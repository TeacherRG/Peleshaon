package app.mychitas.peleshaon.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.Prefs
import java.time.ZonedDateTime
import kotlinx.coroutines.*

enum class WakeMode {
    SOUND_AND_LIGHT,
    SOUND_ONLY,
    LIGHT_ONLY
}

class AlarmRingService : Service() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(app.mychitas.peleshaon.AppLang.wrap(newBase))
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    // Only one alarm may control the foreground service; stale notification actions are ignored.
    private var activeSession: String? = null
    private var activeType: String? = null
    private val pendingAlarms = PendingAlarmQueue()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (matchesSession(intent)) advanceOrStop()
            else if (activeSession == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SNOOZE) {
            if (matchesSession(intent)) {
                val type = activeType ?: return START_NOT_STICKY
                if (type != app.mychitas.peleshaon.Prefs.KASHRUT) {
                    AlarmScheduler.scheduleExactAlarm(
                        this, java.time.ZonedDateTime.now().plusMinutes(5), type, isSnooze = true
                    )
                }
                advanceOrStop()
            } else if (activeSession == null) stopSelf()
            return START_NOT_STICKY
        }

        val alarmType = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: "MORNING"
        val test = intent?.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false) == true
        if (activeSession != null) {
            // A test cannot replace a real alarm. Real simultaneous events wait in FIFO order.
            if (!test) pendingAlarms.enqueue(alarmType, activeType)
            hasQueuedAlarms = !pendingAlarms.isEmpty()
            return START_NOT_STICKY
        }
        beginRing(alarmType, test)
        return START_NOT_STICKY
    }

    private fun isQuietNow(alarmType: String): Boolean {
        if (Prefs.cityUnmatched(this)) return true
        val city = Prefs.city(this)
        return ShabbatGuard.checkMoment(ZonedDateTime.now(city.zone()), city.name, city.lat,
            city.lon, city.zone(), city.isIsrael, Prefs.alarm(this, alarmType).chm,
            Prefs.earlyShabbatMinutes(this)).isQuietTime
    }

    private fun beginRing(alarmType: String, test: Boolean) {
        if (isQuietNow(alarmType)) {
            val next = pendingAlarms.poll()
            hasQueuedAlarms = !pendingAlarms.isEmpty()
            if (next == null) stopSelf() else beginRing(next, false)
            return
        }
        val token = java.util.UUID.randomUUID().toString()
        currentSessionId = token
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
            .setFullScreenIntent(fullScreenPendingIntent, wakeMode != WakeMode.SOUND_ONLY)
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
            requestAlarmAudioFocus()
            startCrescendoAudio(alarmType)
        }
        if (useVibration && wakeMode != WakeMode.LIGHT_ONLY) {
            startVibration()
        }

        // A ringing or queued alarm must also stop when Friday quiet time begins.
        val city = Prefs.city(this)
        val quietStart = ShabbatGuard.nextQuietStart(ZonedDateTime.now(city.zone()), city.name,
            city.lat, city.lon, city.zone(), city.isIsrael, Prefs.alarm(this, alarmType).chm,
            Prefs.earlyShabbatMinutes(this))
        val untilQuiet = quietStart?.toInstant()?.toEpochMilli()?.minus(System.currentTimeMillis())
        if (untilQuiet != null && untilQuiet in 1L..180_000L) {
            scope.launch {
                // Stop slightly early rather than risk playing across a religious boundary.
                delay((untilQuiet - 500L).coerceAtLeast(0L))
                if (activeSession == token) advanceOrStop()
            }
        }

        scope.launch {
            delay(3 * 60 * 1000L)
            if (activeSession == token) {
                if (!test) showMissedNotification(title, alarmType)
                advanceOrStop()
            }
        }

    }

    private fun requestAlarmAudioFocus() {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setOnAudioFocusChangeListener { /* alarm ends only after user action or timeout */ }
                .build()
            focusRequest = req
            manager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun releaseAlarmAudioFocus() {
        audioManager?.let { manager ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let(manager::abandonAudioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(null)
            }
        }
        focusRequest = null
        audioManager = null
    }

    private fun startCrescendoAudio(alarmType: String) {
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
                    if (isQuietNow(alarmType)) {
                        player.release()
                        if (activeType == alarmType) advanceOrStop()
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

    private fun showMissedNotification(title: String, type: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val missed = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.alarm_missed))
            .setAutoCancel(true)
            .build()
        nm.notify(3100 + AlarmScheduler.requestCode(type, false), missed)
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

    /**
     * Complete only this session. Keep another simultaneous real alarm pending;
     * never mislabel a displaced alarm as missed without waiting for user action.
     */
    private fun advanceOrStop() {
        activeSession?.let(::announceFinished)
        stopRing()
        activeSession = null
        activeType = null
        currentSessionId = null
        val next = pendingAlarms.poll()
        hasQueuedAlarms = !pendingAlarms.isEmpty()
        if (next == null) stopSelf() else beginRing(next, false)
    }

    private fun announceFinished(token: String) {
        sendBroadcast(Intent(ACTION_SESSION_FINISHED).setPackage(packageName)
            .putExtra(EXTRA_SESSION, token))
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
        releaseAlarmAudioFocus()
    }

    override fun onDestroy() {
        activeSession?.let(::announceFinished)
        stopRing()
        pendingAlarms.clear()
        scope.cancel()
        activeSession = null
        activeType = null
        currentSessionId = null
        hasQueuedAlarms = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "pele_alarm_channel"
        const val EXTRA_SESSION = "alarm_session"
        const val ACTION_SESSION_FINISHED = "app.mychitas.peleshaon.ALARM_SESSION_FINISHED"
        @Volatile var currentSessionId: String? = null
            private set
        @Volatile var hasQueuedAlarms: Boolean = false
            private set
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
