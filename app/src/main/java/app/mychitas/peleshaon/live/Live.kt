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
import app.mychitas.peleshaon.ui.ClockTheme
import app.mychitas.peleshaon.ui.isDeadline
import app.mychitas.peleshaon.ui.jewishMonthName
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
            val city = requireNotNull(Prefs.chosenCity(context)) {
                "Religious live state requires an explicitly chosen city"
            }
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
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private const val CHANNEL = "pele_live"
    private const val NOTIF_ID = 4001
    private const val TICK = "app.mychitas.peleshaon.LIVE_TICK"

    fun widgetIds(context: Context): IntArray {
        val m = AppWidgetManager.getInstance(context)
        return m.getAppWidgetIds(ComponentName(context, FaceWidget::class.java)) +
            m.getAppWidgetIds(ComponentName(context, DateWidget::class.java))
    }

    fun notificationOn(context: Context): Boolean = Prefs.of(context).getBoolean("live_notification", false)

    fun setNotification(context: Context, on: Boolean) {
        Prefs.of(context).edit().putBoolean("live_notification", on).apply()
        if (!on) NotificationManagerCompat.from(context).cancel(NOTIF_ID)
        update(context)
    }

    /** Keep bitmap rendering off the BroadcastReceiver/main thread. */
    fun updateAsync(context: Context, pending: BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        worker.execute {
            try { update(app) }
            catch (e: Exception) { android.util.Log.e("PeleShaon.Live", "Widget refresh failed", e) }
            finally { pending?.finish() }
        }
    }

    /** Runs on the single shared background worker. */
    fun update(source: Context) {
        val context = app.mychitas.peleshaon.AppLang.wrap(source)
        val ids = widgetIds(context)
        val notif = notificationOn(context)
        if (ids.isEmpty() && !notif) {
            cancelTick(context)
            return
        }
        if (Prefs.chosenCity(context) == null) {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID)
            val manager = AppWidgetManager.getInstance(context)
            val light = ClockTheme.widgetLight(context)
            for (id in manager.getAppWidgetIds(ComponentName(context, FaceWidget::class.java)))
                manager.updateAppWidget(id, ordinaryFaceViews(context, light, id))
            for (id in manager.getAppWidgetIds(ComponentName(context, DateWidget::class.java)))
                manager.updateAppWidget(id, ordinaryDateViews(context, light))
            if (ids.isEmpty()) cancelTick(context) else scheduleOrdinaryTick(context)
            return
        }
        val light = ClockTheme.widgetLight(context)
        val s = ClockTheme.render(light) { LiveState.of(context) }
        val m = AppWidgetManager.getInstance(context)
        for (id in m.getAppWidgetIds(ComponentName(context, FaceWidget::class.java))) m.updateAppWidget(id, faceViews(context, s, id, light))
        for (id in m.getAppWidgetIds(ComponentName(context, DateWidget::class.java))) m.updateAppWidget(id, dateViews(context, s, light))
        if (notif) showNotification(context, s)
        scheduleTick(context, s)
    }

    private fun openApp(context: Context, openCalendar: Boolean = false): PendingIntent =
        PendingIntent.getActivity(
            context, if (openCalendar) 1 else 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_CALENDAR, openCalendar),
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

    /** Цвета виджета: тёмный (ночь) или светлый (день) фон и текст. */
    private const val INK = 0; private const val INK2 = 1; private const val GOLD = 2; private const val MUTED = 3; private const val CREAM = 4
    private val DARK_TEXT = intArrayOf(0xFFF8FAFC.toInt(), 0xFFC9CFE3.toInt(), 0xFFE2C07D.toInt(), 0xFF9AA4C4.toInt(), 0xFFF3E5C8.toInt())
    private val LIGHT_TEXT = intArrayOf(0xFF0F172A.toInt(), 0xFF34415F.toInt(), 0xFF8E6A1C.toInt(), 0xFF5D6886.toInt(), 0xFF0F172A.toInt())

    private fun RemoteViews.paint(light: Boolean, vararg views: Pair<Int, Int>) {
        val c = if (light) LIGHT_TEXT else DARK_TEXT
        for ((id, role) in views) setTextColor(id, c[role])
    }

    private fun RemoteViews.clocks(context: Context, s: LiveState, vararg ids: Int) {
        for (id in ids) setString(id, "setTimeZone", s.city.zone().id)
    }

    /** No city: no stale geographical ring; TextClock follows the device zone. */
    private fun ordinaryFaceViews(context: Context, light: Boolean, widgetId: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_face)
        v.setImageViewBitmap(R.id.w_face,
            android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888))
        v.setInt(R.id.w_date, "setBackgroundResource",
            if (light) R.drawable.widget_date_pill_light else R.drawable.widget_date_pill_dark)
        v.paint(light, R.id.w_time to INK, R.id.w_date to CREAM)
        v.setViewVisibility(R.id.w_tziruf, View.GONE)
        v.setString(R.id.w_time, "setTimeZone", java.time.ZoneId.systemDefault().id)
        v.setTextViewText(R.id.w_date, context.getString(R.string.city_choose))
        v.setOnClickPendingIntent(R.id.w_root, openApp(context))
        fitFace(context, v, widgetId)
        return v
    }

    private fun ordinaryDateViews(context: Context, light: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_date)
        val today = java.time.LocalDate.now()
        val loc = Locale.getDefault()
        v.setInt(R.id.w_root, "setBackgroundResource",
            if (light) R.drawable.widget_date_pill_light else R.drawable.widget_date_pill_dark)
        v.paint(light, R.id.w_day to GOLD, R.id.w_month to INK, R.id.w_weekday to INK2)
        v.setTextViewText(R.id.w_day, today.dayOfMonth.toString())
        v.setTextViewText(R.id.w_month, today.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, loc))
        v.setTextViewText(R.id.w_weekday, today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, loc))
        v.setOnClickPendingIntent(R.id.w_root, openApp(context))
        return v
    }

    private fun scheduleOrdinaryTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setWindow(AlarmManager.RTC, System.currentTimeMillis() + 5 * 60_000L,
            10 * 60_000L, tickIntent(context))
    }

    /**
     * Подгоняет большой виджет под его размер: круг — квадрат по меньшей стороне,
     * плашка с датой сразу под ним (Android 12+), цифры времени — по размеру круга,
     * чтобы не налезали на деления.
     */
    private fun fitFace(context: Context, v: RemoteViews, widgetId: Int) {
        val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (w <= 0 || h <= 0) return
        val font = context.resources.configuration.fontScale
        val pill = 17f * font * 1.3f + 12f + 6f   // текст, внутренние отступы, зазор
        val side = minOf(w - 16f, h - 16f - pill).coerceAtLeast(96f)
        val time = (side * 0.2f).coerceIn(22f, 44f)
        v.setTextViewTextSize(R.id.w_time, TypedValue.COMPLEX_UNIT_DIP, time)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setViewLayoutWidth(R.id.w_face_box, side, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutHeight(R.id.w_face_box, side, TypedValue.COMPLEX_UNIT_DIP)
            v.setViewLayoutMargin(R.id.w_tziruf, RemoteViews.MARGIN_TOP, time * 0.6f + 14f, TypedValue.COMPLEX_UNIT_DIP)
        }
    }

    /** Большой виджет: циферблат дня со скримом, время города и дата в плашке. */
    private fun faceViews(context: Context, s: LiveState, widgetId: Int, light: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_face)
        // размер картинки — по виджету, не меньше 360 и не больше 720 пикселей
        val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val dp = maxOf(opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 200), opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 200))
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt().coerceIn(360, 720)
        v.setImageViewBitmap(R.id.w_face, renderFace(px, s.now, s.city.zone(), s.half, s.ring, s.events, Prefs.tziruf(context), light = light))
        v.setInt(
            R.id.w_date,
            "setBackgroundResource",
            if (light) R.drawable.widget_date_pill_light else R.drawable.widget_date_pill_dark
        )
        v.paint(light, R.id.w_time to INK, R.id.w_date to CREAM)
        val ring = s.ring
        val showTziruf = Prefs.tziruf(context) && ring != null
        v.setViewVisibility(R.id.w_tziruf, if (showTziruf) View.VISIBLE else View.GONE)
        if (showTziruf && ring != null) {
            v.setTextViewText(R.id.w_tziruf, Dial.TZIRUFIM[ring.hour])
            v.paint(light, R.id.w_tziruf to GOLD)
        }
        v.clocks(context, s, R.id.w_time)
        v.setTextViewText(R.id.w_date, dateLine(s))
        v.setOnClickPendingIntent(R.id.w_root, openApp(context))
        fitFace(context, v, widgetId)
        return v
    }

    /** Маленький виджет: еврейская дата — день числом, месяц и день недели. После заката — уже следующий день. */
    private fun dateViews(context: Context, s: LiveState, light: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_date)
        v.setInt(
            R.id.w_root,
            "setBackgroundResource",
            if (light) R.drawable.widget_date_pill_light else R.drawable.widget_date_pill_dark
        )
        v.paint(light, R.id.w_day to GOLD, R.id.w_month to INK, R.id.w_weekday to INK2)
        val jd = ZmanimEngine.jewishDay(s.day.date, s.city.isIsrael)
        v.setTextViewText(R.id.w_day, jd.day.toString())
        // на иврите в списке месяцев — «בכסלו» (для «7 בכסלו»); отдельной строкой — без ב
        val month = jewishMonthName(jd).let { if (Locale.getDefault().language in setOf("iw", "he") && it.startsWith("ב")) it.drop(1) else it }
        v.setTextViewText(R.id.w_month, month)
        // день недели еврейского дня: после заката — уже следующий
        v.setTextViewText(R.id.w_weekday, s.day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.getDefault()))
        v.setOnClickPendingIntent(R.id.w_root, openApp(context, openCalendar = true))
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
        // Android 12+ guarantees no sub-10-minute exactness for inexact alarms.
        am.setWindow(AlarmManager.RTC, at, 10 * 60_000L, tickIntent(context))
    }

    private fun cancelTick(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickIntent(context))
    }
}

/** Плановое обновление, смена времени, часового пояса, перезагрузка. */
class LiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Live.updateAsync(context, goAsync())
    }
}
