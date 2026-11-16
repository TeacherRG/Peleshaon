package app.mychitas.peleshaon

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val res = LocalContext.current.resources
    val labelsMap = dayZmanim.items.associateWith { res.getString(shortZmanLabel(it.type)) }

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
                item.type.isDeadline -> res.getString(R.string.left, formatMinutes(res, diffMin))
                else -> res.getString(R.string.in_time, formatMinutes(res, diffMin))
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
                        Text(stringResource(fullZmanLabel(item.type)), color = textColor, fontWeight = FontWeight.Medium)
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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PermissionsCard()

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
                Text(stringResource(R.string.morning_offset, morningOffset), color = GoldAccent, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach { min ->
                        FilterChip(
                            selected = morningOffset == min,
                            onClick = { onChangeMorningOffset(min) },
                            label = { Text(stringResource(R.string.chip_min, min)) }
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
                        Text(stringResource(R.string.torah_alarm_sub), color = TecheletBlue, fontSize = 11.sp)
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

private fun formatMinutes(res: Resources, totalMinutes: Long): String {
    val h = (totalMinutes / 60).toInt()
    val m = (totalMinutes % 60).toInt()
    return if (h > 0) res.getString(R.string.dur_hm, h, m) else res.getString(R.string.dur_m, m)
}

private fun shortZmanLabel(type: ZmanType): Int = when (type) {
    ZmanType.SUNRISE -> R.string.zs_sunrise
    ZmanType.SOF_ZMAN_SHMA -> R.string.zs_shma
    ZmanType.SOF_ZMAN_TFILA -> R.string.zs_tfila
    ZmanType.CHATZOT -> R.string.zs_chatzot
    ZmanType.MINCHA_GEDOLA -> R.string.zs_mincha
    ZmanType.PLAG_HAMINCHA -> R.string.zs_plag
    ZmanType.SUNSET -> R.string.zs_sunset
    ZmanType.TZAIT -> R.string.zs_tzait
}

private fun fullZmanLabel(type: ZmanType): Int = when (type) {
    ZmanType.SUNRISE -> R.string.zf_sunrise
    ZmanType.SOF_ZMAN_SHMA -> R.string.zf_shma
    ZmanType.SOF_ZMAN_TFILA -> R.string.zf_tfila
    ZmanType.CHATZOT -> R.string.zf_chatzot
    ZmanType.MINCHA_GEDOLA -> R.string.zf_mincha
    ZmanType.PLAG_HAMINCHA -> R.string.zf_plag
    ZmanType.SUNSET -> R.string.zf_sunset
    ZmanType.TZAIT -> R.string.zf_tzait
}

/** Что мешает будильнику: точное время, окно на весь экран, уведомления. */
private data class PermissionState(val exact: Boolean, val fullScreen: Boolean, val notifications: Boolean)

private fun readPermissions(context: Context): PermissionState {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm.canUseFullScreenIntent()
    val notifications = nm.areNotificationsEnabled()
    return PermissionState(exact, fullScreen, notifications)
}

private fun openSettings(context: Context, action: String) {
    val pkg = context.packageName
    val intent = when (action) {
        Settings.ACTION_APP_NOTIFICATION_SETTINGS ->
            Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        else -> Intent(action, Uri.parse("package:$pkg"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun PermissionsCard() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readPermissions(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val fresh = readPermissions(context)
                if (fresh.exact && !state.exact) AlarmScheduler.rescheduleAllFromPrefs(context)
                state = fresh
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (state.exact && state.fullScreen && state.notifications) return

    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF3A2A13), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.perm_title), color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(stringResource(R.string.perm_note), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            PermissionRow(R.string.perm_exact, state.exact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    openSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                }
            }
            PermissionRow(R.string.perm_fullscreen, state.fullScreen) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    openSettings(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                }
            }
            PermissionRow(R.string.perm_notif, state.notifications) {
                openSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }
        }
    }
}

@Composable
private fun PermissionRow(label: Int, granted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(label), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (granted) {
            Text("✓ " + stringResource(R.string.perm_ok), color = TecheletBlue, fontSize = 13.sp)
        } else {
            Button(onClick = onFix, colors = ButtonDefaults.buttonColors(containerColor = GoldAccent)) {
                Text(stringResource(R.string.perm_fix), color = NavyDark)
            }
        }
    }
}
