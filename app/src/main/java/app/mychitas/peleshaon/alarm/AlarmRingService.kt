package app.mychitas.peleshaon.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.mychitas.peleshaon.R
import kotlinx.coroutines.*

enum class WakeMode {
    SOUND_AND_LIGHT,
    SOUND_ONLY,
    LIGHT_ONLY
}

class AlarmRingService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    // Only one alarm may control the foreground service; stale notification actions are ignored.
    private var activeSession: String? = null
    private var activeType: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (activeSession == null || matchesSession(intent)) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SNOOZE) {
            if (matchesSession(intent)) {
                val type = activeType ?: return START_NOT_STICKY
                // Kashrut is a one-shot timer. Its original expiry clears state,
                // so snoozing it would silently disappear on the next re-schedule.
                if (type != app.mychitas.peleshaon.Prefs.KASHRUT) {
                    AlarmScheduler.scheduleExactAlarm(this, java.time.ZonedDateTime.now().plusMinutes(5), type, isSnooze = true)
                }
                stopSelf()
            } else if (activeSession == null) {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val alarmType = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: "MORNING"
        val test = intent?.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false) == true
        // A user-triggered sound test cannot interrupt a real ringing alarm.
        if (test && activeSession != null) return START_NOT_STICKY
        if (activeSession != null) {
            activeType?.let { showMissedNotification(alarmTitle(this, it)) }
            stopRing()
        }
        val token = java.util.UUID.randomUUID().toString()
        activeSession = token
        activeType = alarmType
        val prefs = getSharedPreferences("pele_prefs", Context.MODE_PRIVATE)
        val wakeModeName = prefs.getString("wake_mode_$alarmType", WakeMode.SOUND_AND_LIGHT.name)
        val wakeMode = runCatching { WakeMode.valueOf(wakeModeName ?: "") }
            .getOrDefault(WakeMode.SOUND_AND_LIGHT)
        val useVibration = prefs.getBoolean("vibrate_$alarmType", true)

        createNotificationChannel()

        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
            putExtra("wake_mode", wakeMode.name)
            putExtra(AlarmScheduler.EXTRA_TEST, test)
            putExtra(EXTRA_SESSION, token)
            action = "app.mychitas.peleshaon.RING_$token"
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 2001, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = alarmTitle(this, alarmType)
        // окно звонка смахнули — выключить можно и из уведомления
        fun action(act: String, code: Int) = PendingIntent.getService(
            this, code,
            Intent(this, AlarmRingService::class.java)
                .setAction(act)
                .setData(android.net.Uri.parse("peleshaon://alarm/$token/$code"))
                .putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
                .putExtra(EXTRA_SESSION, token),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.btn_stop), action(ACTION_STOP, 2002))
            .apply { if (!test && alarmType != app.mychitas.peleshaon.Prefs.KASHRUT)
                addAction(0, getString(R.string.btn_snooze_5), action(ACTION_SNOOZE, 2003)) }
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, 3001, notification, fgsType)
        // Android 10+ blocks arbitrary background Activity launches; full-screen notification owns the launch.

        if (wakeMode != WakeMode.LIGHT_ONLY) {
            startCrescendoAudio()
        }
        if (useVibration && wakeMode != WakeMode.LIGHT_ONLY) {
            startVibration()
        }

        scope.launch {
            delay(3 * 60 * 1000L)
            if (activeSession == token) {
                showMissedNotification(title)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startCrescendoAudio() {
        // prepare() may read a slow ringtone provider; never block the service main thread.
        val uris = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        scope.launch {
            for (uri in uris) {
                val player = MediaPlayer()
                try {
                    withContext(Dispatchers.IO) {
                        player.setDataSource(applicationContext, uri)
                        player.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        player.isLooping = true
                        player.setVolume(0.05f, 0.05f)
                        player.prepare()
                    }
                    if (!isActive) {
                        player.release()
                        return@launch
                    }
                    mediaPlayer = player
                    player.start()
                    for (step in 1..30) {
                        delay(1000L)
                        if (mediaPlayer !== player) break
                        val vol = (step / 30f).coerceIn(0.05f, 1.0f)
                        player.setVolume(vol, vol)
                    }
                    return@launch
                } catch (cancelled: CancellationException) {
                    runCatching { player.release() }
                    throw cancelled
                } catch (_: Exception) {
                    if (mediaPlayer === player) mediaPlayer = null
                    runCatching { player.release() }
                }
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 600, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun showMissedNotification(title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val missed = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.alarm_missed))
            .setAutoCancel(true)
            .build()
        nm.notify(3002, missed)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_alarms),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /** Окно звонка смахнули из недавних — музыка не должна играть дальше без способа её выключить. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun matchesSession(intent: Intent): Boolean =
        activeSession != null && intent.getStringExtra(EXTRA_SESSION) == activeSession

    private fun stopRing() {
        scope.coroutineContext.cancelChildren()
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        stopRing()
        scope.cancel()
        activeSession = null
        activeType = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "pele_alarm_channel"
        const val EXTRA_SESSION = "alarm_session"
        const val ACTION_STOP = "app.mychitas.peleshaon.STOP_ALARM"
        const val ACTION_SNOOZE = "app.mychitas.peleshaon.SNOOZE_ALARM"
    }
}

/** Заголовок звонка: будильник, будильник N, учёба, «можно молочное». */
fun alarmTitle(context: Context, type: String): String = when {
    type == "TORAH" -> context.getString(R.string.torah_alarm_title)
    type == "KASHRUT" -> context.getString(R.string.kashrut_title)
    type.startsWith("C") -> context.getString(R.string.alarm_n, app.mychitas.peleshaon.Prefs.customNumber(type))
    else -> context.getString(R.string.morning_alarm_title)
}
