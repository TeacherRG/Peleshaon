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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmType = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: "MORNING"
        val prefs = getSharedPreferences("pele_prefs", Context.MODE_PRIVATE)
        val wakeModeName = prefs.getString("wake_mode_$alarmType", WakeMode.SOUND_AND_LIGHT.name)
        val wakeMode = WakeMode.valueOf(wakeModeName ?: WakeMode.SOUND_AND_LIGHT.name)
        val useVibration = prefs.getBoolean("vibrate_$alarmType", true)

        createNotificationChannel()

        val fullScreenIntent = Intent(this, AlarmRingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, alarmType)
            putExtra("wake_mode", wakeMode.name)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 2001, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (alarmType == "TORAH") getString(R.string.torah_alarm_title) else getString(R.string.morning_alarm_title)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        ServiceCompat.startForeground(this, 3001, notification, fgsType)
        startActivity(fullScreenIntent)

        if (wakeMode != WakeMode.LIGHT_ONLY) {
            startCrescendoAudio()
        }
        if (useVibration) {
            startVibration()
        }

        scope.launch {
            delay(3 * 60 * 1000L)
            showMissedNotification(title)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCrescendoAudio() {
        // Мелодия будильника может быть не задана или недоступна — пробуем по очереди.
        val uris = listOfNotNull(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in uris) {
            val player = MediaPlayer()
            try {
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
                player.start()
                mediaPlayer = player
                break
            } catch (e: Exception) {
                player.release()
            }
        }
        if (mediaPlayer == null) return

        scope.launch {
            for (step in 1..30) {
                delay(1000L)
                val vol = (step / 30f).coerceIn(0.05f, 1.0f)
                mediaPlayer?.setVolume(vol, vol)
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

    override fun onDestroy() {
        scope.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "pele_alarm_channel"
        const val ACTION_STOP = "app.mychitas.peleshaon.STOP_ALARM"
    }
}
