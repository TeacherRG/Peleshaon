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
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.mychitas.peleshaon.ui.CityGate
import app.mychitas.peleshaon.ui.MAIN_TOUR
import app.mychitas.peleshaon.ui.TourState

/**
 * Пеле Шаон — «Часы» сайта Нифлаот в телефоне: циферблат дня на весь экран, времена по Алтер Ребе,
 * Шабат и два будильника, которые молчат в Шабат и праздники.
 */
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_CALENDAR = "app.mychitas.peleshaon.OPEN_CALENDAR"
    }

    // язык, выбранный в «Настройках»
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(app.mychitas.peleshaon.AppLang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // язык из «Настроек»: и для текстов, и для дат, названий месяцев, городов (они берут язык по умолчанию)
        app.mychitas.peleshaon.AppLang.applyToProcess(applicationContext)
        java.util.Locale.setDefault(resources.configuration.locales[0])
        ClockTheme.load(this)
        AlarmScheduler.rescheduleAllFromPrefs(this)
        MoonReminder.schedule(this)
        // компас переводит экран в портрет — активность пересоздаётся, а компас должен остаться открытым
        openMoon.value = savedInstanceState?.getString("compass")
            ?: if (intent?.getBooleanExtra(MoonReminder.EXTRA_OPEN, false) == true) "moon" else null
        openAlarm.value = savedInstanceState?.getString("alarm_panel")
            ?: intent?.getStringExtra(AlarmScheduler.EXTRA_SHOW_ALARM_TYPE)
        openCalendar.value = if (intent?.getBooleanExtra(EXTRA_OPEN_CALENDAR, false) == true) 1 else 0
        if (openAlarm.value != null) openMoon.value = null
        if (openCalendar.value > 0) {
            openAlarm.value = null
            openMoon.value = null
        }
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
            PeleShaonApp(openMoon, openAlarm, openCalendar)
        }
    }

    // уведомление «Кидуш Левана сегодня» — сразу экран-компас
    // компас: "moon" — Луна, "jer" — Йерушалаим, null — закрыт
    private val openMoon = mutableStateOf<String?>(null)
    private val openAlarm = mutableStateOf<String?>(null)
    private val openCalendar = mutableStateOf(0)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("compass", openMoon.value)
        outState.putString("alarm_panel", openAlarm.value)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MoonReminder.EXTRA_OPEN, false)) {
            openAlarm.value = null
            openMoon.value = "moon"
        }
        intent.getStringExtra(AlarmScheduler.EXTRA_SHOW_ALARM_TYPE)?.let {
            openMoon.value = null
            openAlarm.value = it
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_CALENDAR, false)) {
            openMoon.value = null
            openAlarm.value = null
            openCalendar.value++
        }
    }

    // Город, подписи, «Сочетание Имени» могли поменяться — виджеты и уведомление подхватят
    override fun onStop() {
        super.onStop()
        Live.updateAsync(this)
        MoonReminder.schedule(this)
    }
}

@Composable
fun PeleShaonApp(
    openMoon: androidx.compose.runtime.MutableState<String?>,
    openAlarm: androidx.compose.runtime.MutableState<String?>,
    openCalendar: androidx.compose.runtime.MutableState<Int>
) {
    val context = LocalContext.current
    var night by rememberSaveable { mutableStateOf(false) }
    // Первый запуск (владелец, 23.11.2026): сначала обязательный выбор города, затем подсказки
    // поверх главного экрана — что где находится; подсказки можно пропустить.
    val firstRun = !Prefs.of(context).getBoolean("onboarded", false)
    var needCity by rememberSaveable { mutableStateOf(firstRun && Prefs.chosenCity(context) == null) }
    var showTour by rememberSaveable { mutableStateOf(firstRun) }
    // пролистываемое «Знакомство» — теперь только по кнопке в справке «?»
    var intro by rememberSaveable { mutableStateOf(false) }
    val finishTour: () -> Unit = {
        Prefs.of(context).edit().putBoolean("onboarded", true).apply()
        showTour = false
    }
    val tour = remember { TourState(MAIN_TOUR, onFinish = { finishTour() }) }
    LaunchedEffect(openAlarm.value, openCalendar.value) {
        if (openAlarm.value != null || openCalendar.value > 0) {
            night = false
            intro = false
            // открыли окно будильника или календаря — подсказки не закрывают его
            if (showTour && !needCity) finishTour()
        }
    }

    if (needCity) {
        // без города нельзя считать времена — окно обязательно, пропустить нельзя
        CityGate(onChosen = {
            needCity = false
            tour.index = 0
        })
    } else if (intro) {
        Onboarding(onDone = {
            Prefs.of(context).edit().putBoolean("onboarded", true).apply()
            AlarmScheduler.rescheduleAllFromPrefs(context)
            Live.updateAsync(context)
            intro = false
        })
    } else if (openMoon.value != null) {
        // компас: Йерушалаим или Луна
        MoonCompass(onClose = { openMoon.value = null }, startMoon = openMoon.value == "moon")
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
        ClockScreen(onNightScreen = { night = true }, onIntro = { intro = true },
            onTour = { tour.index = 0; showTour = true },
            onMoon = { openMoon.value = "moon" }, onCompass = { openMoon.value = "jer" },
            requestedAlarm = openAlarm, requestedCalendar = openCalendar,
            tour = if (showTour && Prefs.chosenCity(context) != null) tour else null)
    }
}
