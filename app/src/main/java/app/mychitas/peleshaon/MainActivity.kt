package app.mychitas.peleshaon

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.mychitas.peleshaon.alarm.AlarmScheduler
import app.mychitas.peleshaon.live.Live
import app.mychitas.peleshaon.moon.MoonReminder
import app.mychitas.peleshaon.ui.MoonCompass
import app.mychitas.peleshaon.ui.ClockTheme
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import app.mychitas.peleshaon.ui.ClockScreen
import app.mychitas.peleshaon.ui.NightFace
import app.mychitas.peleshaon.ui.Onboarding

/**
 * Пеле Шаон — «Часы» сайта Нифлаот в телефоне: циферблат дня на весь экран, времена по Алтер Ребе,
 * Шабат и два будильника, которые молчат в Шабат и праздники.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        ClockTheme.load(this)
        AlarmScheduler.rescheduleAllFromPrefs(this)
        MoonReminder.schedule(this)
        // компас переводит экран в портрет — активность пересоздаётся, а компас должен остаться открытым
        openMoon.value = savedInstanceState?.getBoolean("moon")
            ?: (intent?.getBooleanExtra(MoonReminder.EXTRA_OPEN, false) == true)
        setContent {
            // тема: днём светлая, ночью тёмная (или как выбрано) — и значки строки состояния под неё
            LaunchedEffect(Unit) {
                while (true) {
                    ClockTheme.refresh(this@MainActivity)
                    delay(30_000L)
                }
            }
            val light = ClockTheme.light
            LaunchedEffect(light) {
                val bars = if (light) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            PeleShaonApp(openMoon)
        }
    }

    // уведомление «Кидуш Левана сегодня» — сразу экран-компас
    private val openMoon = mutableStateOf(false)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("moon", openMoon.value)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MoonReminder.EXTRA_OPEN, false)) openMoon.value = true
    }

    // Город, подписи, «Сочетание Имени» могли поменяться — виджеты и уведомление подхватят
    override fun onStop() {
        super.onStop()
        Live.update(this)
        MoonReminder.schedule(this)
    }
}

@Composable
fun PeleShaonApp(openMoon: androidx.compose.runtime.MutableState<Boolean>) {
    val context = LocalContext.current
    var night by remember { mutableStateOf(false) }
    // Знакомство — при первом запуске (разрешение на уведомления просит его шаг про будильники, с объяснением)
    var intro by remember { mutableStateOf(!Prefs.of(context).getBoolean("onboarded", false)) }

    if (intro) {
        Onboarding(onDone = {
            Prefs.of(context).edit().putBoolean("onboarded", true).apply()
            AlarmScheduler.rescheduleAllFromPrefs(context)
            Live.update(context)
            intro = false
        })
    } else if (openMoon.value) {
        // экран-компас Кидуш Левана
        MoonCompass(onClose = { openMoon.value = false })
    } else if (night) {
        // Ночной экран у кровати: тёмный, экран не гаснет, нажатие — назад
        DisposableEffect(Unit) {
            val window = (context as? ComponentActivity)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val prev = window?.attributes?.screenBrightness ?: -1f
            window?.attributes = window?.attributes?.apply { screenBrightness = 0.02f }
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window?.attributes = window?.attributes?.apply { screenBrightness = prev }
            }
        }
        BackHandler { night = false }
        NightFace(Modifier.fillMaxSize().background(Color.Black).clickable { night = false })
    } else {
        ClockScreen(onNightScreen = { night = true }, onIntro = { intro = true }, onMoon = { openMoon.value = true })
    }
}
