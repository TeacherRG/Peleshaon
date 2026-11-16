package app.mychitas.peleshaon.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduler {

    const val EXTRA_ALARM_TYPE = "alarm_type"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    fun scheduleExactAlarm(
        context: Context,
        triggerTime: ZonedDateTime,
        alarmType: String,
        isSnooze: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_TYPE, alarmType)
            putExtra(EXTRA_IS_SNOOZE, isSnooze)
        }

        val requestCode = if (alarmType == "MORNING") 1001 else 1002
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerMillis = triggerTime.toInstant().toEpochMilli()
        val clockInfo = AlarmManager.AlarmClockInfo(triggerMillis, pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } else {
            alarmManager.setAlarmClock(clockInfo, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, alarmType: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = if (alarmType == "MORNING") 1001 else 1002
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }

    fun rescheduleAllFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("pele_prefs", Context.MODE_PRIVATE)
        val cityName = prefs.getString("city_name", "Vienna") ?: "Vienna"
        val lat = prefs.getFloat("lat", 48.2082f).toDouble()
        val lon = prefs.getFloat("lon", 16.3738f).toDouble()
        val zoneId = ZoneId.of(prefs.getString("zone_id", "Europe/Vienna"))
        val isIsrael = prefs.getBoolean("is_israel", false)
        val muteCholHamoed = prefs.getBoolean("mute_chol_hamoed", false)
        val now = ZonedDateTime.now(zoneId)

        if (prefs.getBoolean("torah_enabled", true)) {
            for (dayOffset in 0..14) {
                val candidateDate = now.toLocalDate().plusDays(dayOffset.toLong())
                val candidate = ZonedDateTime.of(candidateDate, LocalTime.of(20, 0), zoneId)
                if (!candidate.isAfter(now)) continue
                val quiet = ShabbatGuard.checkMoment(candidate, cityName, lat, lon, zoneId, isIsrael, muteCholHamoed)
                if (!quiet.isQuietTime) {
                    scheduleExactAlarm(context, candidate, "TORAH")
                    break
                }
            }
        }

        if (prefs.getBoolean("morning_enabled", false)) {
            val offsetMin = prefs.getInt("morning_offset_shma", 30).toLong()
            for (dayOffset in 0..14) {
                val checkMoment = now.plusDays(dayOffset.toLong()).withHour(10).withMinute(0)
                val res = ZmanimEngine.calculateForMoment(checkMoment, cityName, lat, lon, 0.0, zoneId, isIsrael)
                val shma = res.items.find { it.type == ZmanType.SOF_ZMAN_SHMA } ?: continue
                val candidate = shma.roundedTime.minusMinutes(offsetMin)
                if (!candidate.isAfter(now)) continue
                val quiet = ShabbatGuard.checkMoment(candidate, cityName, lat, lon, zoneId, isIsrael, muteCholHamoed)
                if (!quiet.isQuietTime) {
                    scheduleExactAlarm(context, candidate, "MORNING")
                    break
                }
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: "MORNING"
        val prefs = context.getSharedPreferences("pele_prefs", Context.MODE_PRIVATE)
        val cityName = prefs.getString("city_name", "Vienna") ?: "Vienna"
        val lat = prefs.getFloat("lat", 48.2082f).toDouble()
        val lon = prefs.getFloat("lon", 16.3738f).toDouble()
        val zoneId = ZoneId.of(prefs.getString("zone_id", "Europe/Vienna"))
        val isIsrael = prefs.getBoolean("is_israel", false)
        val muteCholHamoed = prefs.getBoolean("mute_chol_hamoed", false)

        val quietStatus = ShabbatGuard.checkMoment(
            moment = ZonedDateTime.now(zoneId),
            cityName = cityName,
            lat = lat,
            lon = lon,
            zoneId = zoneId,
            isIsrael = isIsrael,
            muteCholHamoed = muteCholHamoed
        )

        if (!quietStatus.isQuietTime) {
            val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        AlarmScheduler.rescheduleAllFromPrefs(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAllFromPrefs(context)
    }
}
