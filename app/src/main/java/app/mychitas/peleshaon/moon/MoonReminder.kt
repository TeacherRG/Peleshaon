package app.mychitas.peleshaon.moon

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
import app.mychitas.peleshaon.domain.KiddushLevanaRules
import app.mychitas.peleshaon.domain.MoonPosition
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Сторона света по азимуту — на языке телефона (иврит и украинский — 8 сторон, остальные — 16). */
fun compassName(az: Double, locale: Locale = Locale.getDefault()): String {
    val i16 = (((az + 11.25) % 360) / 22.5).toInt() % 16
    return when (locale.language) {
        "uk" -> listOf("Пн", "ПнСх", "Сх", "ПдСх", "Пд", "ПдЗх", "Зх", "ПнЗх")[(((az + 22.5) % 360) / 45).toInt() % 8]
        "ru", "be" -> listOf("С", "ССВ", "СВ", "ВСВ", "В", "ВЮВ", "ЮВ", "ЮЮВ", "Ю", "ЮЮЗ", "ЮЗ", "ЗЮЗ", "З", "ЗСЗ", "СЗ", "ССЗ")[i16]
        "de" -> listOf("N", "NNO", "NO", "ONO", "O", "OSO", "SO", "SSO", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")[i16]
        "iw", "he" -> listOf("צפון", "צפון־מזרח", "מזרח", "דרום־מזרח", "דרום", "דרום־מערב", "מערב", "צפון־מערב")[(((az + 22.5) % 360) / 45).toInt() % 8]
        else -> listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")[i16]
    }
}

/** «Луна на ЮЗ (224°), высота 28°» или «Луна под горизонтом». */
fun moonLine(context: Context, m: MoonPosition): String =
    if (!m.aboveHorizon) context.getString(R.string.kl_below)
    else context.getString(R.string.kl_moon, compassName(m.azimuth), m.azimuth.roundToInt() % 360, m.altitude.roundToInt())

/** «вт 23:41» — когда кончается время Кидуш Левана. */
fun untilText(at: ZonedDateTime, locale: Locale = Locale.getDefault()): String =
    at.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + at.format(DateTimeFormatter.ofPattern("d.MM HH:mm"))

/**
 * Напоминание о Кидуш Левана (по желанию, выключено, пока не включат на экране-компасе): одно тихое уведомление в вечер,
 * когда можно, — после выхода звёзд (после Шабата и праздника — после их исхода), если Луна выше 5°. Будильник не будящий
 * точно: окно 10 минут, разрешения на точное время не нужно.
 */
object MoonReminder {
    private const val CHANNEL = "pele_moon"
    private const val NOTIF_ID = 4101
    const val KEY = "kl_reminder"
    const val EXTRA_OPEN = "open_moon"

    fun isOn(context: Context): Boolean = Prefs.of(context).getBoolean(KEY, false)

    fun setOn(context: Context, on: Boolean) {
        Prefs.of(context).edit().putBoolean(KEY, on).apply()
        schedule(context)
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 6001, Intent(context, MoonReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Поставить ближайшее напоминание (или снять, если выключено). */
    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
        if (!isOn(context)) return
        val at = runCatching { KiddushLevanaRules.nextReminder(ZonedDateTime.now(), Prefs.city(context)) }.getOrNull() ?: return
        am.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), 10 * 60_000L, pending(context))
    }

    fun openIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 6002,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Пришло время: если сейчас можно и Луну видно — одно тихое уведомление; потом — следующее напоминание. */
    fun fire(context: Context) {
        val city = Prefs.city(context)
        val now = ZonedDateTime.now(city.zone())
        val kl = runCatching { KiddushLevanaRules.now(now, city) }.getOrNull()
        if (isOn(context) && kl != null && kl.moon.altitude >= 5.0) notify(context, kl.moon, kl.end)
        schedule(context)
    }

    private fun notify(context: Context, m: MoonPosition, end: ZonedDateTime) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.kl_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        val text = moonLine(context, m) + " · " + context.getString(R.string.kl_until, untilText(end))
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(context.getString(R.string.kl_notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context))
            .setTimeoutAfter(end.toInstant().toEpochMilli() - System.currentTimeMillis())
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, n)
        } catch (se: SecurityException) {
            // уведомления запрещены — показать нечего
        }
    }
}

class MoonReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = MoonReminder.fire(context)
}
