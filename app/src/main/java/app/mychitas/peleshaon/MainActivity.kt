package app.mychitas.peleshaon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.mychitas.peleshaon.alarm.*
import app.mychitas.peleshaon.domain.*
import app.mychitas.peleshaon.ui.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CityOption(
    val name: String,
    val lat: Double,
    val lon: Double,
    val zoneId: String,
    val isIsrael: Boolean
)

val DEFAULT_CITIES = listOf(
    CityOption("Vienna", 48.2082, 16.3738, "Europe/Vienna", false),
    CityOption("Jerusalem", 31.7683, 35.2137, "Asia/Jerusalem", true),
    CityOption("Tel Aviv", 32.0853, 34.7818, "Asia/Jerusalem", true),
    CityOption("Tzfat", 32.9646, 35.4960, "Asia/Jerusalem", true),
    CityOption("New York (Crown Heights)", 40.6694, -73.9422, "America/New_York", false),
    CityOption("Moscow", 55.7558, 37.6173, "Europe/Moscow", false),
    CityOption("Berlin", 52.5200, 13.4050, "Europe/Berlin", false),
    CityOption("London", 51.5074, -0.1278, "Europe/London", false)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("pele_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("torah_enabled")) {
            prefs.edit()
                .putBoolean("torah_enabled", true)
                .putInt("torah_hour", 20)
                .putInt("torah_minute", 0)
                .putString("wake_mode_TORAH", WakeMode.SOUND_AND_LIGHT.name)
                .putBoolean("morning_enabled", false)
                .putInt("morning_offset_shma", 30)
                .putString("wake_mode_MORNING", WakeMode.SOUND_AND_LIGHT.name)
                .apply()
        }

        setContent {
            PeleShaonApp()
        }
    }
}

@Composable
fun PeleShaonApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pele_prefs", Context.MODE_PRIVATE) }

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000L)
        }
    }

    var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
    var isBedsideNightMode by remember { mutableStateOf(false) }
    var selectedCity by remember {
        val savedName = prefs.getString("city_name", null)
        val systemTz = ZoneId.systemDefault().id
        val initial = DEFAULT_CITIES.find { it.name == savedName }
            ?: DEFAULT_CITIES.find { it.zoneId == systemTz }
            ?: DEFAULT_CITIES.first()
        mutableStateOf(initial)
    }

    var morningEnabled by remember { mutableStateOf(prefs.getBoolean("morning_enabled", false)) }
    var morningMinutesBeforeShma by remember { mutableIntStateOf(prefs.getInt("morning_offset_shma", 30)) }
    var morningWakeMode by remember {
        mutableStateOf(WakeMode.valueOf(prefs.getString("wake_mode_MORNING", WakeMode.SOUND_AND_LIGHT.name)!!))
    }

    var torahEnabled by remember { mutableStateOf(prefs.getBoolean("torah_enabled", true)) }
    var torahWakeMode by remember {
        mutableStateOf(WakeMode.valueOf(prefs.getString("wake_mode_TORAH", WakeMode.SOUND_AND_LIGHT.name)!!))
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val cityZone = ZoneId.of(selectedCity.zoneId)
    val cityNow = now.withZoneSameInstant(cityZone)

    val dayZmanim = remember(cityNow.toLocalDate(), cityNow.hour, selectedCity) {
        ZmanimEngine.calculateForMoment(
            now = cityNow,
            cityName = selectedCity.name,
            lat = selectedCity.lat,
            lon = selectedCity.lon,
            zoneId = cityZone,
            isIsrael = selectedCity.isIsrael
        )
    }

    LaunchedEffect(torahEnabled, morningEnabled, morningMinutesBeforeShma, selectedCity) {
        prefs.edit()
            .putString("city_name", selectedCity.name)
            .putFloat("lat", selectedCity.lat.toFloat())
            .putFloat("lon", selectedCity.lon.toFloat())
            .putString("zone_id", selectedCity.zoneId)
            .putBoolean("is_israel", selectedCity.isIsrael)
            .apply()

        AlarmScheduler.rescheduleAllFromPrefs(context)
        if (!torahEnabled) AlarmScheduler.cancelAlarm(context, "TORAH")
        if (!morningEnabled) AlarmScheduler.cancelAlarm(context, "MORNING")
    }

    if (isBedsideNightMode) {
        DisposableEffect(Unit) {
            val window = (context as? ComponentActivity)?.window
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val prevBrightness = window?.attributes?.screenBrightness ?: -1f
            window?.attributes = window?.attributes?.apply { screenBrightness = 0.02f }
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window?.attributes = window?.attributes?.apply { screenBrightness = prevBrightness }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { isBedsideNightMode = false },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnalogZmanimClock(
                    now = cityNow,
                    pinnedZmanim = dayZmanim.items.filter { it.type.defaultPinned },
                    zmanLabels = emptyMap(),
                    isDarkTheme = true,
                    is24Hour = true,
                    modifier = Modifier.size(300.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = cityNow.format(DateTimeFormatter.ofPattern("HH:mm")),
                    color = GoldAccent,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dayZmanim.jewishDateHebrew,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp
                )
            }
        }
        return
    }

    val bgColor = if (isDarkTheme) NavyDark else Color(0xFFF5F3EB)
    val textColor = if (isDarkTheme) Color.White else NavyDark

    Scaffold(
        containerColor = bgColor,
        bottomBar = {
            NavigationBar(containerColor = if (isDarkTheme) Color(0xFF09131E) else Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🕒") },
                    label = { Text(stringResource(R.string.app_name)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("⏰") },
                    label = { Text(stringResource(R.string.morning_alarm_title)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("🌍") },
                    label = { Text(selectedCity.name) }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ClockMainTab(
                modifier = Modifier.padding(padding),
                cityNow = cityNow,
                selectedCity = selectedCity,
                dayZmanim = dayZmanim,
                isDarkTheme = isDarkTheme,
                textColor = textColor,
                onToggleTheme = {
                    isDarkTheme = !isDarkTheme
                    prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()
                },
                onEnableNightScreen = { isBedsideNightMode = true }
            )

            1 -> AlarmsTab(
                modifier = Modifier.padding(padding),
                morningEnabled = morningEnabled,
                onToggleMorning = {
                    morningEnabled = it
                    prefs.edit().putBoolean("morning_enabled", it).apply()
                },
                morningOffset = morningMinutesBeforeShma,
                onChangeMorningOffset = {
                    morningMinutesBeforeShma = it
                    prefs.edit().putInt("morning_offset_shma", it).apply()
                },
                morningWakeMode = morningWakeMode,
                onChangeMorningMode = {
                    morningWakeMode = it
                    prefs.edit().putString("wake_mode_MORNING", it.name).apply()
                },
                torahEnabled = torahEnabled,
                onToggleTorah = {
                    torahEnabled = it
                    prefs.edit().putBoolean("torah_enabled", it).apply()
                },
                torahWakeMode = torahWakeMode,
                onChangeTorahMode = {
                    torahWakeMode = it
                    prefs.edit().putString("wake_mode_TORAH", it.name).apply()
                }
            )

            2 -> CitySelectorTab(
                modifier = Modifier.padding(padding),
                selectedCity = selectedCity,
                textColor = textColor,
                onSelectCity = {
                    selectedCity = it
                    selectedTab = 0
                }
            )
        }
    }
}

@Composable
private fun ClockMainTab(
    modifier: Modifier,
    cityNow: ZonedDateTime,
    selectedCity: CityOption,
    dayZmanim: DayZmanimResult,
    isDarkTheme: Boolean,
    textColor: Color,
    onToggleTheme: () -> Unit,
    onEnableNightScreen: () -> Unit
) {
    val labelsMap = dayZmanim.items.associateWith { getShortZmanLabel(it.type) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedCity.name, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row {
                    TextButton(onClick = onEnableNightScreen) { Text("🌙", fontSize = 18.sp) }
                    TextButton(onClick = onToggleTheme) { Text(if (isDarkTheme) "☀️" else "🌑", fontSize = 18.sp) }
                }
            }
        }

        item {
            AnalogZmanimClock(
                now = cityNow,
                pinnedZmanim = dayZmanim.items.filter { it.type.defaultPinned },
                zmanLabels = labelsMap,
                isDarkTheme = isDarkTheme,
                is24Hour = true,
                modifier = Modifier.size(310.dp)
            )
        }

        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = cityNow.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "${dayZmanim.jewishDateHebrew} • ${dayZmanim.jewishDateTranslit}",
                    fontSize = 15.sp,
                    color = GoldAccent
                )
            }
        }

        items(dayZmanim.items) { item ->
            val diffMin = ChronoUnit.MINUTES.between(cityNow, item.roundedTime)
            val countdown = when {
                diffMin < 0 -> ""
                item.type.isDeadline -> "осталось ${formatMinutes(diffMin)}"
                else -> "через ${formatMinutes(diffMin)}"
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkTheme) Color(0xFF13263A) else Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(getFullZmanLabel(item.type), color = textColor, fontWeight = FontWeight.Medium)
                        if (countdown.isNotEmpty()) {
                            Text(countdown, color = TecheletBlue, fontSize = 12.sp)
                        }
                    }
                    Text(
                        text = item.roundedTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        color = if (diffMin < 0) textColor.copy(alpha = 0.4f) else GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmsTab(
    modifier: Modifier,
    morningEnabled: Boolean,
    onToggleMorning: (Boolean) -> Unit,
    morningOffset: Int,
    onChangeMorningOffset: (Int) -> Unit,
    morningWakeMode: WakeMode,
    onChangeMorningMode: (WakeMode) -> Unit,
    torahEnabled: Boolean,
    onToggleTorah: (Boolean) -> Unit,
    torahWakeMode: WakeMode,
    onChangeTorahMode: (WakeMode) -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF13263A), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.morning_alarm_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Switch(checked = morningEnabled, onCheckedChange = onToggleMorning)
                }
                Text("За $morningOffset мин до конца Шма", color = GoldAccent, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { min ->
                        FilterChip(
                            selected = morningOffset == min,
                            onClick = { onChangeMorningOffset(min) },
                            label = { Text("$min м") }
                        )
                    }
                }
                WakeModeSelector(selected = morningWakeMode, onSelect = onChangeMorningMode)
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF13263A), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(stringResource(R.string.torah_alarm_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("20:00 • niflaot.mychitas.app/?lang=en&ref=share#/neviim", color = TecheletBlue, fontSize = 11.sp)
                    }
                    Switch(checked = torahEnabled, onCheckedChange = onToggleTorah)
                }
                WakeModeSelector(selected = torahWakeMode, onSelect = onChangeTorahMode)
            }
        }
    }
}

@Composable
private fun WakeModeSelector(selected: WakeMode, onSelect: (WakeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selected == WakeMode.SOUND_AND_LIGHT,
            onClick = { onSelect(WakeMode.SOUND_AND_LIGHT) },
            label = { Text(stringResource(R.string.mode_sound_light), fontSize = 11.sp) }
        )
        FilterChip(
            selected = selected == WakeMode.SOUND_ONLY,
            onClick = { onSelect(WakeMode.SOUND_ONLY) },
            label = { Text(stringResource(R.string.mode_sound_only), fontSize = 11.sp) }
        )
        FilterChip(
            selected = selected == WakeMode.LIGHT_ONLY,
            onClick = { onSelect(WakeMode.LIGHT_ONLY) },
            label = { Text(stringResource(R.string.mode_light_only), fontSize = 11.sp) }
        )
    }
}

@Composable
private fun CitySelectorTab(
    modifier: Modifier,
    selectedCity: CityOption,
    textColor: Color,
    onSelectCity: (CityOption) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        items(DEFAULT_CITIES) { city ->
            TextButton(
                onClick = { onSelectCity(city) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(city.name, color = if (city == selectedCity) GoldAccent else textColor, fontSize = 18.sp)
                    Text(city.zoneId, color = textColor.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatMinutes(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h} ч ${m} мин" else "${m} мин"
}

private fun getShortZmanLabel(type: ZmanType): String = when (type) {
    ZmanType.SUNRISE -> "Восход"
    ZmanType.SOF_ZMAN_SHMA -> "Шма"
    ZmanType.SOF_ZMAN_TFILA -> "Тфила"
    ZmanType.CHATZOT -> "Полдень"
    ZmanType.MINCHA_GEDOLA -> "Минха гд."
    ZmanType.PLAG_HAMINCHA -> "Плаг"
    ZmanType.SUNSET -> "Закат"
    ZmanType.TZAIT -> "Звёзды"
}

private fun getFullZmanLabel(type: ZmanType): String = when (type) {
    ZmanType.SUNRISE -> "Восход солнца (Нец)"
    ZmanType.SOF_ZMAN_SHMA -> "Конец времени Шма"
    ZmanType.SOF_ZMAN_TFILA -> "Конец времени молитвы"
    ZmanType.CHATZOT -> "Полдень (Хацот)"
    ZmanType.MINCHA_GEDOLA -> "Минха гдола"
    ZmanType.PLAG_HAMINCHA -> "Плаг ґа-минха"
    ZmanType.SUNSET -> "Закат (конец Минхи)"
    ZmanType.TZAIT -> "Выход звёзд (Цет)"
}
