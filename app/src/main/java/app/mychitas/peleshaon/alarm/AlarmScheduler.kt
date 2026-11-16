package app.mychitas.peleshaon.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.mychitas.peleshaon.Prefs
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

        // Отложенный звонок — свой PendingIntent: иначе он затирает следующий будильник,
        // а пересчёт расписания (открыли приложение) — отложенный звонок.
        val requestCode = (if (alarmType == "MORNING") 1001 else 1002) + (if (isSnooze) 10 else 0)
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

    /** Следующие звонки обоих будильников — по настройкам и городу; нет звонка — будильник снят. */
    fun rescheduleAllFromPrefs(context: Context) {
        val city = Prefs.city(context)
        val now = ZonedDateTime.now(city.zone())
        for (type in listOf(Prefs.MORNING, Prefs.TORAH)) {
            val next = AlarmRules.nextRing(Prefs.alarm(context, type), now, city)
            if (next != null) scheduleExactAlarm(context, next, type) else cancelAlarm(context, type)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: Prefs.MORNING
        val city = Prefs.city(context)
        // Последняя проверка в момент звонка: Шабат, Йом Тов, Йом Кипур (и Хол ґа-моэд по галочке) — молчим
        val quietStatus = ShabbatGuard.checkMoment(
            moment = ZonedDateTime.now(city.zone()),
            cityName = city.name,
            lat = city.lat,
            lon = city.lon,
            zoneId = city.zone(),
            isIsrael = city.isIsrael,
            muteCholHamoed = Prefs.alarm(context, alarmType).chm
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
