package app.mychitas.peleshaon.live

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.mychitas.peleshaon.MainActivity
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.DayZmanimResult
import app.mychitas.peleshaon.domain.Dial
import app.mychitas.peleshaon.domain.Half
import app.mychitas.peleshaon.domain.Ring
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.ui.FaceEvent
import app.mychitas.peleshaon.ui.faceEvents
import app.mychitas.peleshaon.ui.full
import app.mychitas.peleshaon.ui.isDeadline
import app.mychitas.peleshaon.ui.jewishDayMonth
import app.mychitas.peleshaon.ui.renderFace
import app.mychitas.peleshaon.ui.short
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Что сейчас: город, времена показанного дня, половина суток, кольцо, точки и ближайшее время. */
class LiveState(
    val city: CityOption,
    val now: ZonedDateTime,
    val day: DayZmanimResult,
    val half: Half?,
    val ring: Ring?,
    val events: List<FaceEvent>,
    val next: FaceEvent?
) {
    companion object {
        fun of(context: Context, at: ZonedDateTime = ZonedDateTime.now()): LiveState {
            val city = Prefs.city(context)
            val zone = city.zone()
            val now = at.withZoneSameInstant(zone)
            val day = ZmanimEngine.calculateForMoment(now, city.name, city.lat, city.lon, 0.0, zone, city.isIsrael)
            val sh = ZmanimEngine.shabbatOf(now, city)
            // циферблат — половина суток: 12 часов дня или ночи
            val half = ZmanimEngine.halfOf(now, city)
            val items = half?.let { ZmanimEngine.halfItems(it, city) } ?: emptyList()
            val ring = Dial.ring(now, half)
            val events = faceEvents(half, items, sh, Prefs.marks(context))
            val next = events.filter { it.at.isAfter(now) }.minByOrNull { it.at }
            return LiveState(city, now, day, half, ring, events, next)
        }
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** «7 кислева 5787» — еврейская дата, день числом. */
private fun dateLine(s: LiveState): String {
    val jd = ZmanimEngine.jewishDay(s.day.date, s.city.isIsrael)
    return "${jewishDayMonth(jd)} ${jd.year}"
}

/** «4-й час дня». */
private fun hourLabel(context: Context, s: LiveState): String? =
    s.half?.let { context.getString(if (it.day) R.string.hour_day else R.string.hour_night, it.hour(s.now)) }

/**
 * Виджеты и уведомление «Ближайшее время». Обратный отсчёт и время в них идут сами (Chronometer, TextClock),
 * поэтому обновлять их нужно редко: когда наступает время дня и раз в 5 минут — сдвинуть штрих «сейчас» на ободе.
 * Будильник для этого — не будящий (RTC): пока экран выключен, телефон не просыпается; включили — всё сразу свежее.
 */
object Live {
    private const val CHANNEL = "pele_live"
    private const val NOTIF_ID = 4001
    private const val TICK = "app.mychitas.peleshaon.LIVE_TICK"

    fun widgetIds(context: Context): IntArray {
        val m = AppWidgetManager.getInstance(context)
        return m.getAppWidgetIds(ComponentName(context, FaceWidget::class.java)) +
            m.getAppWidgetIds(ComponentName(context, LineWidget::class.java))
    }

    fun notificationOn(context: Context): Boolean = Prefs.of(context).getBoolean("live_notification", false)

    fun setNotification(context: Context, on: Boolean) {
        Prefs.of(context).edit().putBoolean("live_notification", on).apply()
        if (!on) NotificationManagerCompat.from(context).cancel(NOTIF_ID)
        update(context)
    }

    /** Обновить всё (виджеты, уведомление) и назначить следующее обновление. */
    fun update(context: Context) {
        val ids = widgetIds(context)
        val notif = notificationOn(context)
        if (ids.isEmpty() && !notif) {
            cancelTick(context)
            return
        }
        val s = LiveState.of(context)
        val m = AppWidgetManager.getInstance(context)
        for (id in m.getAppWidgetIds(ComponentName(context, FaceWidget::class.java))) m.updateAppWidget(id, faceViews(context, s, id))
        for (id in m.getAppWidgetIds(ComponentName(context, LineWidget::class.java))) m.updateAppWidget(id, lineViews(context, s))
        if (notif) showNotification(context, s)
        scheduleTick(context, s)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun nextTitle(context: Context, e: FaceEvent, s: LiveState) =
        "${context.getString(full(e.id))} · ${e.at.withZoneSameInstant(s.city.zone()).format(HHMM)}"

    /** «осталось %s» / «через %s» — Chronometer подставит отсчёт на место %s. */
    private fun countFormat(context: Context, e: FaceEvent): String {
        val word = context.getString(if (isDeadline(e.id)) R.string.live_left else R.string.live_in)
        return "$word %s"
    }

    private fun RemoteViews.countdown(id: Int, context: Context, e: FaceEvent?, s: LiveState) {
        if (e == null) {
            setViewVisibility(id, View.GONE)
            return
        }
        setViewVisibility(id, View.VISIBLE)
        val ms = e.at.toInstant().toEpochMilli() - s.now.toInstant().toEpochMilli()
        setChronometer(id, SystemClock.elapsedRealtime() + ms, countFormat(context, e), true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) setChronometerCountDown(id, true)
    }

    private fun RemoteViews.clocks(context: Context, s: LiveState, vararg ids: Int) {
        for (id in ids) setString(id, "setTimeZone", s.city.zone().id)
    }

    /** Большой виджет: циферблат дня, время города, ближайшее время с отсчётом, еврейская дата. */
    private fun faceViews(context: Context, s: LiveState, widgetId: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_face)
        // размер картинки — по виджету, не меньше 360 и не больше 720 пикселей
        val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val dp = maxOf(opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200), opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200))
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt().coerceIn(360, 720)
        v.setImageViewBitmap(R.id.w_face, renderFace(px, s.now, s.city.zone(), s.half, s.ring, s.events, Prefs.tziruf(context)))
        v.clocks(context, s, R.id.w_time)
        val e = s.next
        v.setTextViewText(R.id.w_next, e?.let { context.getString(short(it.id)) + " " + it.at.withZoneSameInstant(s.city.zone()).format(HHMM) } ?: "")
        v.countdown(R.id.w_count, context, e, s)
        v.setTextViewText(R.id.w_date, dateLine(s))
        v.setTextViewText(R.id.w_city, listOfNotNull(hourLabel(context, s), s.city.title(Locale.getDefault())).joinToString(" · "))
        v.setOnClickPendingIntent(R.id.w_root, openApp(context))
        return v
    }

    /** Строка: время города, ближайшее время с отсчётом, еврейская дата. */
    private fun lineViews(context: Context, s: LiveState): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_line)
        v.clocks(context, s, R.id.w_time)
        val e = s.next
        v.setTextViewText(R.id.w_next, e?.let { nextTitle(context, it, s) } ?: context.getString(R.string.polar_short))
        v.countdown(R.id.w_count, context, e, s)
        v.setTextViewText(R.id.w_date, dateLine(s) + " · " + s.city.title(Locale.getDefault()))
        v.setOnClickPendingIntent(R.id.w_root, openApp(context))
        return v
    }

    private fun showNotification(context: Context, s: LiveState) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.live_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
        val e = s.next
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(e?.let { nextTitle(context, it, s) } ?: context.getString(R.string.polar_short))
            .setContentText(dateLine(s) + " · " + s.city.title(Locale.getDefault()))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(e != null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(context))
        if (e != null) {
            b.setWhen(e.at.toInstant().toEpochMilli()).setUsesChronometer(true).setChronometerCountDown(true)
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, b.build())
        } catch (se: SecurityException) {
            // уведомления запрещены — показать нечего
        }
    }

    private fun tickIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 5001, Intent(context, LiveReceiver::class.java).setAction(TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun scheduleTick(context: Context, s: LiveState) {
        val nowMs = System.currentTimeMillis()
        val fiveMin = nowMs + 5 * 60_000L - nowMs % 60_000L
        val atNext = s.next?.at?.toInstant()?.toEpochMilli()?.plus(1_000L)
        val at = if (atNext != null && atNext < fiveMin) atNext else fiveMin
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // RTC без пробуждения: обновится, когда телефон проснётся
        am.setWindow(AlarmManager.RTC, at, 30_000L, tickIntent(context))
    }

    private fun cancelTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(context))
    }
}

/** Плановое обновление, смена времени, часового пояса, перезагрузка. */
class LiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Live.update(context)
    }
}
