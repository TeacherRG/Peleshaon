package app.mychitas.peleshaon.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mychitas.peleshaon.MainActivity
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.alarm.ShabbatGuard
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

/**
 * Напоминания календаря — только на этом телефоне (`pele_prefs`, ключ `calendar_reminders`, JSON),
 * как и остальные настройки. В сеть не уходят: импорт и экспорт — файлом, который выбирает пользователь.
 */
object ReminderStore {
    private const val KEY = "calendar_reminders"
    const val MAX = 3000

    /** Зона напоминаний — зона выбранного города; город не выбран — зона телефона. */
    fun zone(context: Context): ZoneId = Prefs.chosenCity(context)?.zone() ?: ZoneId.systemDefault()

    fun newId(): String = UUID.randomUUID().toString() + "@peleshaon"

    fun load(context: Context): List<Reminder> {
        val raw = Prefs.of(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i -> runCatching { fromJson(a.getJSONObject(i)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    /** Сохранить весь список и переставить уведомления. Изменение не «догоняет» уведомления, уже прошедшие. */
    fun save(context: Context, list: List<Reminder>) {
        val a = JSONArray()
        for (r in list.take(MAX)) a.put(toJson(r))
        Prefs.of(context).edit().putString(KEY, a.toString()).apply()
        ReminderNotifier.markCheckedNow(context)
        ReminderNotifier.schedule(context)
    }

    fun upsert(context: Context, r: Reminder): List<Reminder> {
        val list = load(context)
        val out = if (list.any { it.id == r.id }) list.map { if (it.id == r.id) r else it } else list + r
        save(context, out)
        return out
    }

    fun delete(context: Context, id: String): List<Reminder> {
        val out = load(context).filter { it.id != id }
        save(context, out)
        return out
    }

    /** Импорт: события с тем же UID обновляются, новые добавляются. Возвращает (новых, обновлено). */
    fun merge(context: Context, incoming: List<Reminder>): Pair<Int, Int> {
        val map = LinkedHashMap<String, Reminder>()
        for (r in load(context)) map[r.id] = r
        var added = 0
        var updated = 0
        for (r in incoming) {
            if (r.id in map) updated++ else added++
            map[r.id] = r
        }
        save(context, map.values.toList())
        return added to updated
    }

    private fun toJson(r: Reminder) = JSONObject().apply {
        put("id", r.id)
        put("title", r.title)
        put("note", r.note)
        put("date", r.date.toString())
        r.time?.let { put("time", it.toString()) }
        put("repeat", r.repeat.name)
        put("interval", r.interval)
        if (r.byDay.isNotEmpty()) put("byDay", r.byDay.sortedBy { it.value }.joinToString(",") { it.name })
        r.until?.let { put("until", it.toString()) }
        r.alertMinutes?.let { put("alert", it) }
    }

    private fun fromJson(o: JSONObject) = Reminder(
        id = o.getString("id"),
        title = o.optString("title", ""),
        note = o.optString("note", ""),
        date = LocalDate.parse(o.getString("date")),
        time = o.optString("time", "").takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
        repeat = runCatching { Repeat.valueOf(o.optString("repeat", "NONE")) }.getOrDefault(Repeat.NONE),
        interval = o.optInt("interval", 1).coerceAtLeast(1),
        byDay = o.optString("byDay", "").split(',').mapNotNull { n -> DayOfWeek.values().find { it.name == n } }.toSet(),
        until = o.optString("until", "").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
        alertMinutes = if (o.has("alert")) o.getInt("alert") else null
    )
}

/**
 * Уведомления о напоминаниях. Один будильник AlarmManager — на ближайшее уведомление из всех.
 * Проверенный момент (`reminders_checked_until`) не даёт уведомить дважды и позволяет показать
 * пропущенное (телефон был выключен) — но не старше трёх суток.
 *
 * В Шабат и Йом Тов (по выбранному городу, от свечей до исхода — как будильники) уведомления не приходят:
 * они откладываются до исхода и приходят тогда.
 */
object ReminderNotifier {
    private const val CHANNEL = "pele_reminders"
    private const val KEY_CHECKED = "reminders_checked_until"
    private const val REQUEST = 7001
    private const val MAX_CATCH_UP_MS = 3L * 24 * 3600 * 1000

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun markCheckedNow(context: Context) {
        Prefs.of(context).edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply()
    }

    private fun checkedSince(context: Context): Long {
        val now = System.currentTimeMillis()
        val saved = Prefs.of(context).getLong(KEY_CHECKED, 0L)
        if (saved <= 0L) { markCheckedNow(context); return now }
        return maxOf(saved, now - MAX_CATCH_UP_MS)
    }

    /** Поставить будильник на ближайшее уведомление (или снять, если уведомлять не о чем). */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
        val list = ReminderStore.load(context).filter { it.alertMinutes != null }
        if (list.isEmpty()) return
        val zone = ReminderStore.zone(context)
        val since = Instant.ofEpochMilli(checkedSince(context)).atZone(zone)
        val next = list.mapNotNull { runCatching { ReminderRules.nextAlert(it, since, zone)?.first }.getOrNull() }
            .minOrNull() ?: return
        arm(context, maxOf(next.toInstant().toEpochMilli(), System.currentTimeMillis() + 1_000L))
    }

    private fun arm(context: Context, at: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
        }
    }

    /** Сейчас Шабат / Йом Тов в выбранном городе? Город не выбран — не знаем, уведомляем без звука. */
    private fun quiet(context: Context, at: ZonedDateTime): Boolean {
        val city = Prefs.chosenCity(context) ?: return false
        return runCatching {
            ShabbatGuard.checkMoment(at, city.name, city.lat, city.lon, city.zone(), city.isIsrael,
                muteCholHamoed = false, earlyShabbatMinutes = Prefs.earlyShabbatMinutes(context)).isQuietTime
        }.getOrDefault(true)
    }

    fun fire(context: Context) {
        val zone = ReminderStore.zone(context)
        val now = ZonedDateTime.now(zone)
        if (quiet(context, now)) {
            // ищем исход с шагом 10 минут (не дольше трёх суток) — тогда и покажем накопившееся
            var t = now
            repeat(3 * 24 * 6) {
                t = t.plusMinutes(10)
                if (!quiet(context, t)) { arm(context, t.toInstant().toEpochMilli()); return }
            }
            arm(context, t.toInstant().toEpochMilli())
            return
        }
        val since = Instant.ofEpochMilli(checkedSince(context)).atZone(zone)
        val due = mutableListOf<Pair<Reminder, LocalDate>>()
        for (r in ReminderStore.load(context)) {
            var from = since
            while (true) {
                val (at, day) = runCatching { ReminderRules.nextAlert(r, from, zone) }.getOrNull() ?: break
                if (at.isAfter(now)) break
                due += r to day
                from = at
            }
        }
        val silent = Prefs.chosenCity(context) == null
        for ((r, day) in due.takeLast(20)) notify(context, r, day, zone, silent)
        Prefs.of(context).edit().putLong(KEY_CHECKED, now.toInstant().toEpochMilli()).apply()
        schedule(context)
    }

    private fun notify(context: Context, r: Reminder, day: LocalDate, zone: ZoneId, silent: Boolean) {
        val localized = app.mychitas.peleshaon.AppLang.wrap(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, localized.getString(R.string.rem_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val locale = localized.resources.configuration.locales[0]
        val whenText = if (r.time == null)
            day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
        else ReminderRules.startOn(r, day, zone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
        val text = if (r.note.isBlank()) whenText else whenText + "\n" + r.note
        val open = PendingIntent.getActivity(
            context, REQUEST,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CALENDAR, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(r.title.ifBlank { localized.getString(R.string.rem_untitled) })
            .setContentText(whenText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSilent(silent)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(("rem:" + r.id + day).hashCode(), n)
        } catch (_: SecurityException) {
            // уведомления запрещены — показать нечего
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        Thread {
            try { ReminderNotifier.fire(context.applicationContext) } finally { result.finish() }
        }.start()
    }
}
