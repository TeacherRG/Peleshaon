package app.mychitas.peleshaon.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.ui.GoldAccent
import app.mychitas.peleshaon.ui.NavyDark
import app.mychitas.peleshaon.ui.TecheletBlue
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AlarmRingActivity : ComponentActivity() {
    // язык, выбранный в «Настройках» (до Android 13 — здесь)
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(app.mychitas.peleshaon.AppLang.wrap(newBase))
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: "TORAH"
        val test = intent.getBooleanExtra(AlarmScheduler.EXTRA_TEST, false)
        val wakeModeName = intent.getStringExtra("wake_mode") ?: WakeMode.SOUND_AND_LIGHT.name
        val wakeMode = runCatching { WakeMode.valueOf(wakeModeName) }.getOrDefault(WakeMode.SOUND_AND_LIGHT)
        val useLight = wakeMode != WakeMode.SOUND_ONLY

        if (useLight) {
            window.attributes = window.attributes.apply {
                screenBrightness = 1.0f
            }
        }

        val appLang = when (Locale.getDefault().language) {
            "ru", "uk", "be" -> "ru"
            "de" -> "de"
            "iw", "he" -> "he"
            else -> "en"
        }

        setContent {
            var sunriseStarted by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { sunriseStarted = true }

            val bgColor by animateColorAsState(
                targetValue = if (useLight && sunriseStarted) Color(0xFFFFF4D6) else NavyDark,
                animationSpec = tween(durationMillis = 25_000),
                label = "sunrise"
            )
            val textColor = if (useLight && sunriseStarted) NavyDark else Color.White

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Text(
                        text = alarmTitle(this@AlarmRingActivity, alarmType),
                        fontSize = 24.sp,
                        color = textColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (alarmType == "TORAH") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    stopAlarm()
                                    openUrl("https://niflaot.mychitas.app/?lang=$appLang&ref=app#/neviim")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TecheletBlue)
                            ) {
                                Text(stringResource(R.string.btn_neviim))
                            }
                            Button(
                                onClick = {
                                    stopAlarm()
                                    openUrl("https://niflaot.mychitas.app/?lang=$appLang&ref=app#/ketuvim")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                            ) {
                                Text(stringResource(R.string.btn_ktuvim), color = NavyDark)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // «Проверить звук» — без «Отложить»
                    if (!test) OutlinedButton(
                        onClick = {
                            controlAlarm(AlarmRingService.ACTION_SNOOZE)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(stringResource(R.string.btn_snooze_5), color = textColor, fontSize = 18.sp)
                    }

                    Button(
                        onClick = { stopAlarm(); goHome() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)
                    ) {
                        Text(
                            text = stringResource(R.string.btn_stop),
                            color = NavyDark,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    private fun controlAlarm(action: String) {
        // Direct stopService would allow an outdated Activity to stop a newer alarm.
        startService(
            Intent(this, AlarmRingService::class.java).setAction(action)
                .putExtra(AlarmRingService.EXTRA_SESSION, intent.getStringExtra(AlarmRingService.EXTRA_SESSION))
                .putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE))
        )
        finish()
    }

    private fun stopAlarm() = controlAlarm(AlarmRingService.ACTION_STOP)

    /** После «Стоп» — на главный экран приложения (владелец, 18.11.2026). */
    private fun goHome() {
        startActivity(
            Intent(this, app.mychitas.peleshaon.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
