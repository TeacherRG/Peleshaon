package app.mychitas.peleshaon.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.alarm.AlarmRingService
import app.mychitas.peleshaon.alarm.AlarmRules
import app.mychitas.peleshaon.alarm.ShabbatGuard
import app.mychitas.peleshaon.alarm.AlarmScheduler
import app.mychitas.peleshaon.alarm.AlarmSpec
import app.mychitas.peleshaon.alarm.WakeMode
import app.mychitas.peleshaon.domain.CITIES
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.Dial
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.JewishCal
import app.mychitas.peleshaon.domain.Mevarchim
import androidx.compose.ui.res.pluralStringResource
import app.mychitas.peleshaon.AppLang
import androidx.compose.foundation.layout.FlowRow
import app.mychitas.peleshaon.domain.KiddushLevanaRules
import app.mychitas.peleshaon.domain.findCities
import app.mychitas.peleshaon.live.Live
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Окно поверх часов: город, Шабат, два будильника, все времена, «Как считаем». */
private enum class Panel { SETTINGS, CITY, SHABBAT, WAKE, STUDY, CUSTOM, TIMES, INFO, PRIVACY, CALENDAR }

/** «осталось / через N ч M мин» — как на сайте: минуты вверх, больше суток — дни и часы. */
private fun until(context: Context, now: ZonedDateTime, at: ZonedDateTime, deadline: Boolean): String {
    val ms = Duration.between(now, at).toMillis()
    val mins = maxOf(1L, (ms + 59_999) / 60_000).toInt()
    val r = context.resources
    if (mins >= 24 * 60) return r.getString(R.string.in_dh, mins / 1440, (mins % 1440) / 60)
    return if (mins >= 60) r.getString(if (deadline) R.string.left_hm else R.string.in_hm, mins / 60, mins % 60)
    else r.getString(if (deadline) R.string.left_m else R.string.in_m, mins)
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    onNightScreen: () -> Unit,
    onIntro: () -> Unit = {},
    onMoon: () -> Unit = {},
    onCompass: () -> Unit = {},
    requestedAlarm: androidx.compose.runtime.MutableState<String?>? = null,
    requestedCalendar: androidx.compose.runtime.MutableState<Int>? = null
) {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]

    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    // меняется, когда меняют настройки (город, будильник) — перечитать
    var version by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = { version++ }

    val city = remember(version, now.zone) { Prefs.city(context) }
    val auto = remember(version, now.zone) { Prefs.chosenCity(context) == null }
    var marks by remember { mutableStateOf(Prefs.marks(context)) }
    var tziruf by remember { mutableStateOf(Prefs.tziruf(context)) }
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    // свой будильник, открытый в окне (C1, C2…)
    var editAlarm by rememberSaveable { mutableStateOf(Prefs.MORNING) }
    var pop by remember { mutableStateOf<Triple<String, Float, Float>?>(null) }
    LaunchedEffect(requestedAlarm?.value, requestedCalendar?.value) {
        val id = requestedAlarm?.value
        if (id != null) {
            when (id) {
                Prefs.MORNING -> panel = Panel.WAKE
                Prefs.TORAH -> panel = Panel.STUDY
                else -> {
                    if (id in Prefs.customAlarms(context)) {
                        editAlarm = id
                        panel = Panel.CUSTOM
                    } else panel = Panel.WAKE
                }
            }
            requestedAlarm.value = null
        }
        if ((requestedCalendar?.value ?: 0) > 0) {
            panel = Panel.CALENDAR
            requestedCalendar?.value = 0
        }
    }

    // Stop BEFORE invoking any geographical, Shabbat, Jewish-day rollover or moon calculations.
    // Saved invalid/missing city must not be replaced by a timezone-derived religious city.
    if (auto) {
        NoCityClockScreen(now = now, onIntro = onIntro, onNightScreen = onNightScreen, onChanged = bump)
        return
    }
    val zone = city.zone()
    val cityNow = now.withZoneSameInstant(zone)
    val minute = cityNow.truncatedTo(ChronoUnit.MINUTES)
    val day = remember(city, minute, version) {
        ZmanimEngine.calculateForMoment(cityNow, city.name, city.lat, city.lon, 0.0, zone, city.isIsrael)
    }
    val sh = remember(city, minute, version) { ZmanimEngine.shabbatOf(cityNow, city) }
    // циферблат — половина суток: 12 галахических часов дня или ночи (владелец, 17.11.2026)
    val half = remember(city, minute, version) { ZmanimEngine.halfOf(cityNow, city) }
    val halfItems = remember(half, city, version) { half?.let { ZmanimEngine.halfItems(it, city) } ?: emptyList() }
    val ring = Dial.ring(cityNow, half)
    val mainStyle = Prefs.faceStyle(context, "main")
    val mainTime = Prefs.faceTime(context, "main")
    val subVisible = Prefs.subFaceVisible(context)
    val subStyle = Prefs.faceStyle(context, "sub")
    val subTime = Prefs.faceTime(context, "sub")
    val hourLabel = half?.let { stringResource(if (it.day) R.string.hour_day else R.string.hour_night, it.hour(cityNow)) }
    // еврейская дата — день обычным числом, крупно
    val jd = remember(day.date, city) { ZmanimEngine.jewishDay(day.date, city.isIsrael) }

    // точки циферблата: времена этой половины суток; свечи и исход — если они в ней
    val events = remember(half, halfItems, sh, marks, ClockTheme.light) { faceEvents(half, halfItems, sh, marks) }

    val wdTime = { at: ZonedDateTime ->
        "${at.withZoneSameInstant(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${at.withZoneSameInstant(zone).format(HHMM)}"
    }
    val dayText = { d: LocalDate -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)) }
    val shabbatOn = sh.candles != null && !cityNow.isBefore(sh.candles)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF060B1E))) {
        ClockBackground()
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (Prefs.cityUnmatched(context)) {
                Text(stringResource(R.string.city_unmatched_warning), color = ClockColors.Bad,
                    fontSize = 12.sp, modifier = Modifier.clickable { panel = Panel.CITY })
            } else if (auto) {
                Text(stringResource(R.string.city_auto_unconfirmed_warning), color = ClockColors.Bad,
                    fontSize = 12.sp, modifier = Modifier.clickable { panel = Panel.CITY })
            }
            // ---- шапка: город, дата, «Как считаем»
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(onClick = { panel = Panel.CITY }, modifier = Modifier.widthIn(max = 124.dp)) {
                    Text("📍", fontSize = 14.sp)
                    Text(city.title(locale), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ClockColors.Ink)
                    Text("▾", fontSize = 12.sp, color = ClockColors.TcheletDeep)
                }
                // дата — полупрозрачной плашкой, как кнопка города: видно, что она нажимается (открывает календарь)
                Surface(
                    onClick = { panel = Panel.CALENDAR },
                    shape = RoundedCornerShape(16.dp),
                    color = ClockColors.Surface,
                    border = BorderStroke(1.dp, ClockColors.Line),
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // «7 кислева 5787» крупно; ниже — буквами и гражданская дата; нажатие — календарь
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("📅", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp))
                        Text("${jd.day}", color = ClockColors.GoldDeep, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            jewishMonthName(jd, locale),
                            color = ClockColors.GoldDeep, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(bottom = 3.dp)
                        )
                        Text(" ▾", fontSize = 12.sp, color = ClockColors.TcheletDeep, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Text(
                        // год — в еврейской дате буквами; гражданская — коротко, чтобы строка помещалась
                        day.jewishDateHebrew + " · " + cityNow.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
                        color = ClockColors.Ink2, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (day.isNextJewishDayAfterSunset) Text(stringResource(R.string.evening), color = ClockColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                }
                // меню: компас, настройки, помощь (владелец, 18.11.2026)
                Box {
                    var menu by remember { mutableStateOf(false) }
                    Chip(onClick = { menu = true }, round = true) {
                        Text("☰", fontSize = 18.sp, color = ClockColors.TcheletDeep)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = ClockColors.SheetBg) {
                        for ((icon, label, act) in listOf<Triple<String, Int, () -> Unit>>(
                            Triple("🧭", R.string.menu_compass, onCompass),
                            Triple("⚙", R.string.settings, { panel = Panel.SETTINGS }),
                            Triple("?", R.string.help, { panel = Panel.INFO })
                        )) {
                            DropdownMenuItem(
                                leadingIcon = { Text(icon, fontSize = 18.sp, color = ClockColors.TcheletDeep) },
                                text = { Text(stringResource(label), color = ClockColors.Ink, fontSize = 16.sp) },
                                onClick = { menu = false; act() }
                            )
                        }
                    }
                }
            }

            // ---- циферблат на всё место и подсказка у точки
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (day.isPolarAnomaly) {
                    Text(stringResource(R.string.polar), color = ClockColors.Ink2, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
                if (mainStyle == "DIGITAL") {
                    DigitalFaceTime(now = cityNow, halachic = mainTime == "HALACHIC", hourLabel = hourLabel, modifier = Modifier.fillMaxSize().padding(bottom = if (subVisible) 42.dp else 0.dp))
                } else {
                    BigFace(
                        now = cityNow,
                        zone = zone,
                        half = if (mainTime == "HALACHIC") half else null,
                        ring = if (mainTime == "HALACHIC") ring else null,
                        events = if (mainTime == "HALACHIC") events else emptyList(),
                        active = if (mainTime == "HALACHIC") pop?.first else null,
                        tziruf = tziruf && mainTime == "HALACHIC",
                        modifier = Modifier.fillMaxSize(),
                        hourLabel = if (mainTime == "HALACHIC") hourLabel else null,
                        analogStyle = true,
                        onPick = if (mainTime == "HALACHIC") ({ id, fx, fy -> pop = if (id == null || pop?.first == id) null else Triple(id, fx, fy) }) else null
                    )
                }
                if (subVisible && (subTime == "CIVIL" || half != null)) {
                    SubFaceDial(
                        now = cityNow,
                        half = half,
                        halachic = subTime == "HALACHIC",
                        digital = subStyle == "DIGITAL",
                        hourLabel = hourLabel,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(104.dp)
                    )
                }
                val p = pop
                val e = p?.let { pp -> events.find { it.id == pp.first } }
                if (p != null && e != null) {
                    val density = LocalDensity.current
                    val w = with(density) { maxWidth.toPx() }
                    val h = with(density) { maxHeight.toPx() }
                    val sideLen = minOf(w, h)
                    val ax = (w - sideLen) / 2 + p.second * sideLen
                    val ay = (h - sideLen) / 2 + p.third * sideLen
                    val cardW = minOf(with(density) { 290.dp.toPx() }, w - with(density) { 16.dp.toPx() })
                    val m = with(density) { 8.dp.toPx() }
                    val gap = with(density) { 16.dp.toPx() }
                    val left = (ax - cardW / 2).coerceIn(m, w - cardW - m)
                    val top = ay < h / 2
                    val card = Modifier
                        .width(with(density) { cardW.toDp() })
                        .then(
                            if (top) Modifier.offset { IntOffset(left.roundToInt(), (ay + gap).roundToInt()) }
                            else Modifier.align(Alignment.BottomStart).offset { IntOffset(left.roundToInt(), -(h - ay + gap).roundToInt()) }
                        )
                    PopCard(
                        e = e, now = cityNow, zone = zone, marks = marks, modifier = card,
                        onClose = { pop = null },
                        onToggle = { type ->
                            marks = if (type in marks) marks - type else marks + type
                            Prefs.setMarks(context, marks)
                        },
                        onShabbat = { pop = null; panel = Panel.SHABBAT }
                    )
                }
            }
            // Кидуш Левана: в подходящий вечер — плашка с полумесяцем вместо подсказки; нажатие — лунный компас
            val kl = remember(city, minute, version) { runCatching { KiddushLevanaRules.now(cityNow, city) }.getOrNull() }
            // Шабат мевархим — в пятницу и в субботу: благословение месяца и молад
            val mev = remember(city, minute, version) {
                if (cityNow.dayOfWeek == java.time.DayOfWeek.FRIDAY || cityNow.dayOfWeek == java.time.DayOfWeek.SATURDAY)
                    JewishCal.mevarchim(cityNow.toLocalDate(), city.isIsrael) else null
            }
            if (pop == null && kl != null) {
                MoonPill(kl, onClick = onMoon)
            } else if (pop == null && mev != null) {
                MevarchimPill(mev, onClick = { panel = Panel.SHABBAT })
            } else if (pop == null && !day.isPolarAnomaly) {
                Text(stringResource(R.string.hint), color = ClockColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            // ---- кнопки внизу: Шабат · Будильник · Учёба · Времена
            val off = stringResource(R.string.off2)
            // ближайший из будильников (основной и свои); идёт таймер «мясное → молочное» — он
            val nextWake = remember(version, city, minute) {
                val k = Prefs.kashrutAt(context)
                val kashrut = if (k > System.currentTimeMillis()) listOf(java.time.Instant.ofEpochMilli(k).atZone(zone))
                    else emptyList()
                (kashrut + (listOf(Prefs.MORNING) + Prefs.customAlarms(context))
                    .mapNotNull { AlarmRules.nextRing(Prefs.alarm(context, it), cityNow, city, Prefs.earlyShabbatMinutes(context)) }).minOrNull()
            }
            val nextStudy = remember(version, city, minute) { AlarmRules.nextRing(Prefs.alarm(context, Prefs.TORAH), cityNow, city, Prefs.earlyShabbatMinutes(context)) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DockTool("🕯️", stringResource(R.string.dock_shabbat),
                    (if (shabbatOn) sh.havdalah else sh.candles)?.let(wdTime) ?: "—", Modifier.weight(1f), live = shabbatOn) { panel = Panel.SHABBAT }
                DockTool("⏰", stringResource(R.string.dock_alarm), nextWake?.let(wdTime) ?: off, Modifier.weight(1f)) { panel = Panel.WAKE }
                DockTool("📖", stringResource(R.string.dock_study), nextStudy?.let(wdTime) ?: off, Modifier.weight(1f)) { panel = Panel.STUDY }
                DockTool("🕘", stringResource(R.string.dock_times),
                    stringResource(if (day.isNextJewishDayAfterSunset) R.string.tomorrow_word else R.string.today), Modifier.weight(1f)) { panel = Panel.TIMES }
            }
        }
    }

    // ---- окна
    val open = panel
    if (open != null) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { panel = null },
            sheetState = state,
            containerColor = ClockColors.SheetBg,
            contentColor = ClockColors.Ink
        ) {
            when (open) {
                Panel.CITY -> CitySheet(city, auto) { chosen ->
                    Prefs.setCity(context, chosen)
                    ClockTheme.refresh(context)
                    AlarmScheduler.rescheduleAllFromPrefs(context)
                    app.mychitas.peleshaon.moon.MoonReminder.schedule(context)
                    app.mychitas.peleshaon.live.Live.updateAsync(context)
                    pop = null
                    bump()
                    panel = null
                }
                Panel.SHABBAT -> ShabbatSheet(sh, cityNow, city, wdTime, dayText)
                Panel.WAKE, Panel.STUDY, Panel.CUSTOM -> {
                    val t = when (open) { Panel.WAKE -> Prefs.MORNING; Panel.STUDY -> Prefs.TORAH; else -> editAlarm }
                    key(t) { AlarmSheet(
                    type = t,
                    city = city, now = cityNow, wdTime = wdTime, dayText = dayText, onChange = bump,
                    onOpen = { id -> editAlarm = id; panel = Panel.CUSTOM },
                    onDeleted = { bump(); panel = Panel.WAKE }
                ) } }
                Panel.TIMES -> TimesSheet(city, day.items, day.isNextJewishDayAfterSunset, cityNow, zone, marks, tziruf, dayText,
                    onToggle = { type ->
                        marks = if (type in marks) marks - type else marks + type
                        Prefs.setMarks(context, marks)
                        app.mychitas.peleshaon.live.Live.updateAsync(context)
                    },
                    onTziruf = {
                        tziruf = it
                        Prefs.setTziruf(context, it)
                        app.mychitas.peleshaon.live.Live.updateAsync(context)
                    })
                Panel.INFO -> InfoSheet(onNightScreen = { panel = null; onNightScreen() }, onIntro = { panel = null; onIntro() }, onPrivacy = { panel = Panel.PRIVACY })
                Panel.PRIVACY -> PrivacySheet()
                Panel.CALENDAR -> CalendarSheet(city, cityNow)
                Panel.SETTINGS -> SettingsSheet(onChange = bump)
            }
        }
    }
}


/** Ordinary local clock; calendar dates only until the user explicitly selects a city. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoCityClockScreen(
    now: ZonedDateTime,
    onIntro: () -> Unit,
    onNightScreen: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    Box(Modifier.fillMaxSize().background(Color(0xFF060B1E))) {
        ClockBackground()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(onClick = { panel = Panel.CITY }, modifier = Modifier.widthIn(max = 165.dp)) {
                    Text("📍")
                    Text(stringResource(R.string.city_choose), color = ClockColors.Ink,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("▾", color = ClockColors.TcheletDeep)
                }
                Surface(
                    onClick = { panel = Panel.CALENDAR },
                    shape = RoundedCornerShape(16.dp),
                    color = ClockColors.Surface,
                    border = BorderStroke(1.dp, ClockColors.Line),
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                ) {
                    Text("📅 " + now.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)),
                        modifier = Modifier.padding(9.dp), textAlign = TextAlign.Center,
                        color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold)
                }
                Chip(onClick = { panel = Panel.SETTINGS }, round = true) {
                    Text("☰", color = ClockColors.TcheletDeep, fontSize = 18.sp)
                }
            }
            Surface(
                color = ClockColors.Surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ClockColors.Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.city_required_for_halacha),
                    color = ClockColors.GoldDeep, textAlign = TextAlign.Center, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 48.sp)
                    Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
                        color = ClockColors.Ink, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (panel != null) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { panel = null }, sheetState = state,
            containerColor = ClockColors.SheetBg, contentColor = ClockColors.Ink) {
            when (panel) {
                Panel.CITY -> CitySheet(Prefs.city(context), true) { chosen ->
                    Prefs.setCity(context, chosen)
                    AlarmScheduler.rescheduleAllFromPrefs(context)
                    app.mychitas.peleshaon.moon.MoonReminder.schedule(context)
                    Live.updateAsync(context)
                    panel = null
                    onChanged()
                }
                Panel.CALENDAR -> CalendarNoCity(now.toLocalDate())
                Panel.SETTINGS -> SettingsSheet(onChange = onChanged)
                Panel.INFO -> InfoSheet(onNightScreen = { panel = null; onNightScreen() },
                    onIntro = { panel = null; onIntro() }, onPrivacy = { panel = Panel.PRIVACY })
                Panel.PRIVACY -> PrivacySheet()
                else -> Text(stringResource(R.string.city_required_for_halacha),
                    modifier = Modifier.padding(24.dp), color = ClockColors.Ink)
            }
        }
    }
}

@Composable
private fun Chip(onClick: () -> Unit, modifier: Modifier = Modifier, round: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = if (round) CircleShape else RoundedCornerShape(50),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, ClockColors.Line),
        modifier = modifier.heightIn(min = 38.dp).then(if (round) Modifier.size(38.dp) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (round) 0.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}

@Composable
private fun DockTool(icon: String, title: String, sub: String, modifier: Modifier, live: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, if (live) ClockColors.GoldDeep else ClockColors.Line),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ClockColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 11.sp, color = ClockColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PopCard(
    e: FaceEvent,
    now: ZonedDateTime,
    zone: java.time.ZoneId,
    marks: Set<ZmanType>,
    modifier: Modifier,
    onClose: () -> Unit,
    onToggle: (ZmanType) -> Unit,
    onShabbat: () -> Unit
) {
    val context = LocalContext.current
    val past = !e.at.isAfter(now)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ClockColors.SheetBg.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, ClockColors.Line),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(4.dp).background(e.color))
            Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(e.color, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(full(e.id)), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ClockColors.Ink, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("✕", color = ClockColors.Muted) }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(e.at.withZoneSameInstant(zone).format(HHMM), fontWeight = FontWeight.Bold, fontSize = 28.sp, color = ClockColors.Ink)
                    Text(
                        if (past) stringResource(R.string.past) else until(context, now, e.at, isDeadline(e.id)),
                        fontWeight = if (past) FontWeight.Normal else FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (past) ClockColors.Muted else ClockColors.GoldDeep,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(stringResource(about(e.id)), fontSize = 13.5.sp, color = ClockColors.Ink2, lineHeight = 19.sp, modifier = Modifier.padding(end = 8.dp))
                if (e.id == CANDLES || e.id == HAVDALAH) {
                    Text(
                        stringResource(R.string.dock_shabbat) + " →",
                        color = ClockColors.TcheletDeep, fontWeight = FontWeight.Bold, fontSize = 13.5.sp,
                        modifier = Modifier.clickable(onClick = onShabbat).padding(vertical = 4.dp)
                    )
                } else {
                    val type = ZmanType.valueOf(e.id)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onToggle(type) }) {
                        Checkbox(checked = type in marks, onCheckedChange = { onToggle(type) }, colors = checkColors())
                        Text(stringResource(R.string.on_face), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = ClockColors.Ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun checkColors() = CheckboxDefaults.colors(
    checkedColor = ClockColors.Gold, uncheckedColor = ClockColors.Muted, checkmarkColor = ClockColors.SheetBg
)

@Composable
private fun SheetTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ClockColors.Ink, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = ClockColors.Muted)
}

@Composable
private fun SheetColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) { content() }
}

// ---------------------------------------------------------------- город

@Composable
internal fun CitySheet(city: CityOption, auto: Boolean, title: Boolean = true, onPick: (CityOption?) -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    var q by remember { mutableStateOf("") }
    var manualOpen by remember { mutableStateOf(false) }
    var zoneOpen by remember { mutableStateOf(false) }
    var manualName by remember(city.id) { mutableStateOf(if (city.id == app.mychitas.peleshaon.domain.ManualLocation.ID) city.name else "") }
    var latText by remember(city.id) { mutableStateOf(if (city.id == app.mychitas.peleshaon.domain.ManualLocation.ID) city.lat.toString() else "") }
    var lonText by remember(city.id) { mutableStateOf(if (city.id == app.mychitas.peleshaon.domain.ManualLocation.ID) city.lon.toString() else "") }
    var manualIsrael by remember(city.id) { mutableStateOf(city.id == app.mychitas.peleshaon.domain.ManualLocation.ID && city.isIsrael) }
    var zoneText by remember(city.id, city.zoneId) { mutableStateOf(city.zoneId) }
    var inputError by remember { mutableStateOf(false) }
    val list = remember(q, locale) { if (q.isBlank()) CITIES.sortedBy { it.title(locale) } else findCities(q, 30) }
    val repeatedLabels = remember(locale) {
        CITIES.groupingBy { it.title(locale) to it.oblastUk }.eachCount()
            .filterValues { it > 1 }.keys
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        if (title) SheetTitle(stringResource(R.string.city))
        Text(
            city.title(locale) + if (auto) " · " + stringResource(R.string.city_auto) else "",
            color = ClockColors.Gold, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.city_search)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ClockColors.Ink, unfocusedTextColor = ClockColors.Ink,
                focusedBorderColor = ClockColors.Gold, unfocusedBorderColor = ClockColors.Line,
                cursorColor = ClockColors.Gold, focusedPlaceholderColor = ClockColors.Muted, unfocusedPlaceholderColor = ClockColors.Muted
            )
        )
        if (!auto) {
            TextButton(onClick = { onPick(null) }) { Text("⟲ " + stringResource(R.string.city_by_zone), color = ClockColors.TcheletDeep) }
        }
        TextButton(onClick = { manualOpen = !manualOpen; zoneOpen = false; inputError = false }) {
            Text(stringResource(R.string.city_manual_toggle), color = ClockColors.TcheletDeep)
        }
        if (manualOpen) {
            OutlinedTextField(
                value = manualName, onValueChange = { manualName = it; inputError = false },
                label = { Text(stringResource(R.string.city_manual_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = latText, onValueChange = { latText = it; inputError = false },
                label = { Text(stringResource(R.string.city_manual_lat)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lonText, onValueChange = { lonText = it; inputError = false },
                label = { Text(stringResource(R.string.city_manual_lon)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = zoneText, onValueChange = { zoneText = it; inputError = false },
                label = { Text(stringResource(R.string.city_manual_zone)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = manualIsrael, onCheckedChange = { manualIsrael = it })
                Text(stringResource(R.string.city_manual_israel), color = ClockColors.Ink)
            }
            TextButton(onClick = {
                val c = app.mychitas.peleshaon.domain.ManualLocation.parse(manualName, latText, lonText, zoneText, manualIsrael)
                if (c == null) inputError = true else onPick(c)
            }) {
                Text(stringResource(R.string.city_manual_apply), color = ClockColors.Gold)
            }
        }
        if (!auto && !manualOpen) {
            TextButton(onClick = { zoneOpen = !zoneOpen; inputError = false }) {
                Text(stringResource(R.string.city_zone_override), color = ClockColors.TcheletDeep)
            }
        }
        if (zoneOpen) {
            OutlinedTextField(
                value = zoneText, onValueChange = { zoneText = it; inputError = false },
                label = { Text(stringResource(R.string.city_manual_zone)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Note(stringResource(R.string.city_timezone_notice))
            TextButton(onClick = {
                val tz = app.mychitas.peleshaon.domain.ManualLocation.zoneOrNull(zoneText)
                if (tz == null) inputError = true else onPick(city.copy(zoneId = tz.id))
            }) {
                Text(stringResource(R.string.city_zone_apply), color = ClockColors.Gold)
            }
        }
        if (inputError) Note(stringResource(R.string.city_manual_invalid))
        if (list.isEmpty() && !manualOpen) Note(stringResource(R.string.city_none))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = if (manualOpen || zoneOpen) 125.dp else 420.dp)) {
            items(list, key = { it.id }) { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(c) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        c.title(locale), modifier = Modifier.weight(1f), fontSize = 16.sp,
                        color = if (c.id == city.id && !auto) ClockColors.Gold else ClockColors.Ink,
                        fontWeight = if (c.id == city.id) FontWeight.Bold else FontWeight.Normal
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.oblastTitle(locale) ?: c.zoneId.substringAfter('/').replace('_', ' '), fontSize = 12.sp, color = ClockColors.Muted)
                        if ((c.title(locale) to c.oblastUk) in repeatedLabels) {
                            Text(String.format(java.util.Locale.US, "%.4f, %.4f", c.lat, c.lon),
                                fontSize = 11.sp, color = ClockColors.Muted)
                        }
                    }
                }
                HorizontalDivider(color = ClockColors.Line)
            }
        }
    }
}

// ---------------------------------------------------------------- Шабат

@Composable
private fun ShabbatSheet(
    sh: app.mychitas.peleshaon.domain.ShabbatTimes,
    now: ZonedDateTime,
    city: CityOption,
    wdTime: (ZonedDateTime) -> String,
    dayText: (LocalDate) -> String
) {
    val context = LocalContext.current
    val on = sh.candles != null && !now.isBefore(sh.candles)
    val target = if (on) sh.havdalah else sh.candles
    SheetColumn {
        SheetTitle(stringResource(R.string.shabbat))
        Text(dayText(sh.saturday), color = ClockColors.Ink2)
        Text(ZmanimEngine.jewishDate(sh.saturday, city.isIsrael).first, color = ClockColors.Ink, fontSize = 17.sp)
        if (target != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0x29D6AA50), border = BorderStroke(1.dp, Color(0x80D6AA50)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (on) R.string.shabbat_on else R.string.shabbat_lead), color = ClockColors.Ink2, fontSize = 13.sp)
                    // «через 2 дн 3 ч» без первого слова — как на сайте
                    Text(until(context, now, target, false).substringAfter(' '), color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                }
            }
        }
        for ((id, at) in listOf(CANDLES to sh.candles, HAVDALAH to sh.havdalah)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (id == CANDLES) "🕯️" else "✨", fontSize = 22.sp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(full(id)), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
                    Text(stringResource(about(id)), fontSize = 12.5.sp, color = ClockColors.Muted, lineHeight = 17.sp)
                }
                Text(at?.let(wdTime) ?: stringResource(R.string.no_sun), fontWeight = FontWeight.Bold, color = ClockColors.Gold)
            }
        }
        if (sh.havdalah == null) {
            Note(stringResource(R.string.polar_shabbat_undefined))
        }
        SeventhHour(sh.friday, city, now)
        Note(stringResource(R.string.shabbat_note))
        JewishCal.mevarchim(sh.friday, city.isIsrael)?.let { m ->
            HorizontalDivider(color = ClockColors.Line)
            MevarchimBlock(m)
        }
    }
}

/** 7-й час ночи Шабата: с … до … — в этот час не делают кидуш. */
@Composable
private fun SeventhHour(friday: LocalDate, city: CityOption, now: ZonedDateTime) {
    if (ZmanimEngine.minhag != Minhag.CHABAD) return
    val h = remember(friday, city) { ZmanimEngine.seventhHourOfShabbatNight(friday, city) }
    if (!now.isBefore(h.second)) return
    val z = city.zone()
    Surface(shape = RoundedCornerShape(12.dp), color = ClockColors.Bad.copy(alpha = 0.10f), border = BorderStroke(1.dp, ClockColors.Bad.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🍷", fontSize = 20.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.h7_t), color = ClockColors.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(stringResource(R.string.h7_note), color = ClockColors.Muted, fontSize = 12.sp)
            }
            Text(h.first.withZoneSameInstant(z).format(HHMM) + "–" + h.second.withZoneSameInstant(z).format(HHMM),
                color = ClockColors.Bad, fontWeight = FontWeight.Bold)
        }
    }
}

/** «Молад: вторник, 8 декабря, 3:47 и 5 частей». */
@Composable
private fun moladText(m: Mevarchim): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val day = m.moladDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + ", " +
        m.moladDate.format(DateTimeFormatter.ofPattern("d MMMM", locale))
    val time = String.format(Locale.ROOT, "%d:%02d", m.moladHour, m.moladMinute)
    val parts = pluralStringResource(R.plurals.chalakim, m.moladChalakim, m.moladChalakim)
    return stringResource(R.string.mev_molad, day, time, parts)
}

/** Плашка на главном экране в пятницу и субботу перед новым месяцем. */
@Composable
private fun MevarchimPill(m: Mevarchim, onClick: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, ClockColors.Gold.copy(alpha = 0.6f)),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌒", fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mev_title) + " · " + jewishMonthOnly(m.month, m.leap, locale),
                    color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(moladText(m), color = ClockColors.Ink2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", color = ClockColors.GoldDeep, fontSize = 22.sp)
        }
    }
}

/** В окне «Шабат»: благословение месяца, молад, дни Рош Ходеш (у Хабада — и весь Теґилим). */
@Composable
private fun MevarchimBlock(m: Mevarchim) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Text("🌒  " + stringResource(R.string.mev_title), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
    Text(stringResource(R.string.mev_bless, jewishMonthOnly(m.month, m.leap, locale)), color = ClockColors.Ink2)
    Text(moladText(m), color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold)
    val rc = m.roshChodesh.joinToString(" · ") { it.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)) }
    Text(stringResource(R.string.mev_rc, rc), color = ClockColors.Ink2)
    Note(stringResource(R.string.mev_note))
    if (ZmanimEngine.minhag == Minhag.CHABAD) Note(stringResource(R.string.mev_tehillim))
}

// ---------------------------------------------------------------- будильники

@Composable
private fun AlarmSheet(
    type: String,
    city: CityOption,
    now: ZonedDateTime,
    wdTime: (ZonedDateTime) -> String,
    dayText: (LocalDate) -> String,
    onChange: () -> Unit,
    onOpen: (String) -> Unit = {},
    onDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val study = type == Prefs.TORAH
    val custom = type.startsWith("C")
    var spec by remember { mutableStateOf(Prefs.alarm(context, type)) }
    var mode by remember { mutableStateOf(Prefs.wakeMode(context, type)) }
    val change = { s: AlarmSpec ->
        spec = s
        Prefs.setAlarm(context, type, s)
        AlarmScheduler.rescheduleAllFromPrefs(context)
        onChange()
    }
    val pickTime = {
        TimePickerDialog(context, { _, h, m -> change(spec.copy(hour = h, minute = m, byShma = false)) }, spec.hour, spec.minute, true).show()
    }
    val next = AlarmRules.nextRing(spec, now, city, Prefs.earlyShabbatMinutes(context))
    val skips = AlarmRules.skipped(spec, now, city, earlyShabbatMinutes = Prefs.earlyShabbatMinutes(context))
    val timeText = String.format(Locale.ROOT, "%02d:%02d", spec.hour, spec.minute)

    SheetColumn {
        if (spec.on) PermissionsCard()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (custom) stringResource(R.string.alarm_n, Prefs.customNumber(type)) else stringResource(if (study) R.string.study else R.string.alarm),
                fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ClockColors.Ink, modifier = Modifier.weight(1f)
            )
            Text(stringResource(if (spec.on) R.string.alarm_on else R.string.alarm_off), color = if (spec.on) ClockColors.Ok else ClockColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Switch(checked = spec.on, onCheckedChange = { change(spec.copy(on = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = ClockColors.Gold, checkedThumbColor = ClockColors.SheetBg))
        }

        if (study) {
            Text(stringResource(R.string.study_lead), color = ClockColors.Ink2, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkButton(stringResource(R.string.btn_neviim), "neviim", Modifier.weight(1f))
                LinkButton(stringResource(R.string.btn_ktuvim), "ketuvim", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.study_when), color = ClockColors.Ink, modifier = Modifier.weight(1f))
                TimeButton(timeText, pickTime)
            }
        } else {
            Text(stringResource(R.string.alarm_when), color = ClockColors.Muted, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { change(spec.copy(byShma = false)) }) {
                RadioButton(selected = !spec.byShma, onClick = { change(spec.copy(byShma = false)) }, colors = radioColors())
                Text(stringResource(R.string.alarm_mode_clock), color = ClockColors.Ink, modifier = Modifier.weight(1f))
                TimeButton(timeText, pickTime)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { change(spec.copy(byShma = true)) }) {
                RadioButton(selected = spec.byShma, onClick = { change(spec.copy(byShma = true)) }, colors = radioColors())
                Text(stringResource(R.string.alarm_mode_shma), color = ClockColors.Ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 12.dp)) {
                AlarmSpec.BEFORE.forEach { n ->
                    FilterChip(
                        selected = spec.byShma && spec.before == n,
                        onClick = { change(spec.copy(before = n, byShma = true)) },
                        label = { Text(stringResource(R.string.before_min, n), fontSize = 12.sp) },
                        colors = chipColors()
                    )
                }
            }
        }

        Text(stringResource(R.string.alarm_days), color = ClockColors.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            for (d in 0..6) {
                val on = d in spec.days
                val name = DayOfWeek.of(if (d == 0) 7 else d).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                Surface(
                    onClick = { change(spec.copy(days = if (on) spec.days - d else spec.days + d)) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (on) ClockColors.Gold else ClockColors.Surface,
                    border = BorderStroke(1.dp, if (on) ClockColors.Gold else ClockColors.Line),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) ClockColors.SheetBg else ClockColors.Ink, maxLines = 1)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { change(spec.copy(chm = !spec.chm)) }) {
            Checkbox(checked = spec.chm, onCheckedChange = { change(spec.copy(chm = it)) }, colors = checkColors())
            Text(stringResource(R.string.alarm_chm), color = ClockColors.Ink)
        }

        Text(stringResource(R.string.alarm_how), color = ClockColors.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((m, label) in listOf(
                WakeMode.SOUND_AND_LIGHT to R.string.mode_sound_light,
                WakeMode.SOUND_ONLY to R.string.mode_sound_only,
                WakeMode.LIGHT_ONLY to R.string.mode_light_only
            )) {
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m; Prefs.setWakeMode(context, type, m) },
                    label = { Text(stringResource(label), fontSize = 12.sp) },
                    colors = chipColors()
                )
            }
        }
        Note(stringResource(R.string.alarm_how_note))

        if (spec.on) {
            Surface(shape = RoundedCornerShape(12.dp), color = ClockColors.Surface, border = BorderStroke(1.dp, ClockColors.Line), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (next != null) {
                        Text(stringResource(R.string.alarm_next), color = ClockColors.Muted, fontSize = 13.sp)
                        Text("${dayText(next.toLocalDate())}, ${next.format(HHMM)}", color = ClockColors.Ink, fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(if (spec.days.isEmpty()) R.string.alarm_no_days else R.string.alarm_no_next), color = ClockColors.Ink2)
                    }
                    for (s in skips) {
                        val why = when (s.reason) {
                            "yom_kippur" -> R.string.why_yom_kippur
                            "chol_hamoed" -> R.string.why_chol_hamoed
                            "shabbat" -> R.string.why_shabbat
                            "unknown" -> R.string.why_unknown
                            else -> R.string.why_yom_tov
                        }
                        Text(stringResource(R.string.alarm_skip, dayText(s.at.toLocalDate()), stringResource(why)), color = ClockColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                val i = Intent(context, AlarmRingService::class.java).putExtra(AlarmScheduler.EXTRA_ALARM_TYPE, type)
                    .putExtra(AlarmScheduler.EXTRA_TEST, true)
                androidx.core.content.ContextCompat.startForegroundService(context, i)
            },
            border = BorderStroke(1.dp, ClockColors.Line)
        ) { Text(stringResource(R.string.alarm_test), color = ClockColors.Ink) }
        Note(stringResource(R.string.alarm_note))

        if (custom) {
            OutlinedButton(
                onClick = { AlarmScheduler.removeCustom(context, type); onDeleted() },
                border = BorderStroke(1.dp, ClockColors.Bad)
            ) { Text(stringResource(R.string.alarm_delete), color = ClockColors.Bad) }
        }
        if (type == Prefs.MORNING) {
            HorizontalDivider(color = ClockColors.Line)
            MoreAlarms(city, now, onOpen, onChange)
            HorizontalDivider(color = ClockColors.Line)
            KashrutCard(onChange)
        }
    }
}

/** Свои будильники: список (время, дни, вкл.) и «＋ Добавить будильник». */
@Composable
private fun MoreAlarms(city: CityOption, now: ZonedDateTime, onOpen: (String) -> Unit, onChange: () -> Unit) {
    val context = LocalContext.current
    var ids by remember { mutableStateOf(Prefs.customAlarms(context)) }
    Text(stringResource(R.string.alarms_more), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
    for (id in ids) key(id) {
        var spec by remember { mutableStateOf(Prefs.alarm(context, id)) }
        val time = if (spec.byShma) stringResource(R.string.before_min, spec.before) else String.format(Locale.ROOT, "%02d:%02d", spec.hour, spec.minute)
        Surface(
            onClick = { onOpen(id) },
            shape = RoundedCornerShape(12.dp), color = ClockColors.Surface, border = BorderStroke(1.dp, ClockColors.Line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.alarm_n, Prefs.customNumber(id)), color = ClockColors.Muted, fontSize = 12.sp)
                    Text(time, color = ClockColors.Ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    val next = if (spec.on) AlarmRules.nextRing(spec, now, city, Prefs.earlyShabbatMinutes(context)) else null
                    if (next != null) Text(next.format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())), color = ClockColors.Ink2, fontSize = 12.sp)
                }
                Switch(
                    checked = spec.on,
                    onCheckedChange = {
                        spec = spec.copy(on = it)
                        Prefs.setAlarm(context, id, spec)
                        AlarmScheduler.rescheduleAllFromPrefs(context)
                        onChange()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = ClockColors.Gold, checkedThumbColor = ClockColors.SheetBg)
                )
            }
        }
    }
    if (ids.size < Prefs.MAX_CUSTOM) {
        OutlinedButton(
            onClick = {
                val id = Prefs.addCustomAlarm(context) ?: return@OutlinedButton
                ids = Prefs.customAlarms(context)
                AlarmScheduler.rescheduleAllFromPrefs(context)
                onChange()
                onOpen(id)
            },
            border = BorderStroke(1.dp, ClockColors.Gold)
        ) { Text("＋  " + stringResource(R.string.alarm_add), color = ClockColors.Ink) }
    }
}

/** «Мясное → молочное»: один звонок через 6 часов. */
@Composable
private fun KashrutCard(onChange: () -> Unit) {
    val context = LocalContext.current
    var at by remember { mutableStateOf(Prefs.kashrutAt(context)) }
    val running = at > System.currentTimeMillis()
    Text("🍖 → 🥛  " + stringResource(R.string.kashrut_t), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
    Text(stringResource(R.string.kashrut_interval), color = ClockColors.Muted, fontSize = 13.sp)
    if (Prefs.kashrutTimerWasReset(context)) Note(stringResource(R.string.kashrut_timer_reset))
    if (Prefs.kashrutDeferred(context)) {
        val city = Prefs.city(context)
        val silent = ShabbatGuard.checkMoment(java.time.ZonedDateTime.now(city.zone()), city.name,
            city.lat, city.lon, city.zone(), city.isIsrael, false,
            Prefs.earlyShabbatMinutes(context)).isQuietTime
        if (!silent) Note(stringResource(R.string.kashrut_deferred))
    }
    if (running) {
        PermissionsCard()
        val t = java.time.Instant.ofEpochMilli(at).atZone(Prefs.city(context).zone())
        Text(stringResource(R.string.kashrut_until, t.format(DateTimeFormatter.ofPattern("HH:mm"))), color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = { AlarmScheduler.stopKashrut(context); at = 0L; onChange() },
            border = BorderStroke(1.dp, ClockColors.Line)
        ) { Text(stringResource(R.string.kashrut_cancel), color = ClockColors.Ink) }
    } else {
        Button(
            onClick = { AlarmScheduler.startKashrut(context); at = Prefs.kashrutAt(context); onChange() },
            colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.kashrut_start), color = ClockColors.SheetBg, fontWeight = FontWeight.Bold) }
    }
    Note(stringResource(R.string.kashrut_note))
}

@Composable
private fun radioColors() = RadioButtonDefaults.colors(selectedColor = ClockColors.Gold, unselectedColor = ClockColors.Muted)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = ClockColors.Surface,
    labelColor = ClockColors.Ink,
    selectedContainerColor = ClockColors.Gold,
    selectedLabelColor = ClockColors.SheetBg
)

@Composable
private fun TimeButton(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = ClockColors.Surface, border = BorderStroke(1.dp, ClockColors.Gold)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ClockColors.Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

/** Ссылка на отрывок дня на сайте Нифлаот — на языке телефона. */
@Composable
private fun LinkButton(text: String, route: String, modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        onClick = {
            val lang = when (Locale.getDefault().language) {
                "ru", "uk", "be" -> "ru"; "de" -> "de"; "iw", "he" -> "he"; else -> "en"
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://niflaot.mychitas.app/?lang=$lang&ref=app#/$route")))
        },
        shape = RoundedCornerShape(12.dp),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, ClockColors.Line),
        modifier = modifier
    ) {
        Text(text, color = ClockColors.TcheletDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp))
    }
}

// ---------------------------------------------------------------- времена дня

@Composable
private fun TimesSheet(
    city: CityOption,
    items: List<app.mychitas.peleshaon.domain.ZmanItem>,
    tomorrow: Boolean,
    now: ZonedDateTime,
    zone: java.time.ZoneId,
    marks: Set<ZmanType>,
    tziruf: Boolean,
    dayText: (LocalDate) -> String,
    onToggle: (ZmanType) -> Unit,
    onTziruf: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val next = items.firstOrNull { it.roundedTime.isAfter(now) }
    var showTzirufWarning by remember { mutableStateOf(false) }
    val requestTzirufChange: (Boolean) -> Unit = { enabled ->
        if (enabled) showTzirufWarning = true else onTziruf(false)
    }
    SheetColumn {
        SheetTitle(stringResource(R.string.times_title))
        if (tomorrow) Text(stringResource(R.string.tomorrow, dayText(now.toLocalDate().plusDays(1))), color = ClockColors.GoldDeep, fontSize = 13.sp)
        // в пятницу (и ночью Шабата до восхода) — 7-й час ночи, когда не делают кидуш
        val fri = when (now.dayOfWeek) {
            java.time.DayOfWeek.FRIDAY -> now.toLocalDate()
            java.time.DayOfWeek.SATURDAY -> now.toLocalDate().minusDays(1).takeIf { now.hour < 12 }
            else -> null
        }
        fri?.let { SeventhHour(it, city, now) }
        Column {
            for (z in items) {
                val past = !z.roundedTime.isAfter(now)
                val on = z.type in marks
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .background(if (z == next) Color(0x1FD6AA50) else Color.Transparent, RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    Box(Modifier.size(10.dp).background(zmanColor(z.type).copy(alpha = if (past) 0.45f else 1f), CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(full(z.type.name)), color = if (past) ClockColors.Muted else ClockColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (past) stringResource(R.string.past) else until(context, now, z.roundedTime, z.type.isDeadline),
                            color = if (past) ClockColors.Muted else ClockColors.TcheletDeep, fontSize = 12.sp
                        )
                    }
                    Text(z.roundedTime.withZoneSameInstant(zone).format(HHMM), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (past) ClockColors.Muted else ClockColors.Gold)
                    Checkbox(checked = on, onCheckedChange = { onToggle(z.type) }, colors = checkColors())
                }
            }
        }
        if (items.none { it.type == ZmanType.TZAIT }) {
            Note(stringResource(R.string.polar_nightfall_undefined))
        }
        Text(stringResource(R.string.on_face) + " — ✓", color = ClockColors.Muted, fontSize = 12.sp)
        HorizontalDivider(color = ClockColors.Line)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { requestTzirufChange(!tziruf) }) {
            Checkbox(checked = tziruf, onCheckedChange = requestTzirufChange, colors = checkColors())
            Text(stringResource(R.string.tziruf), color = ClockColors.Ink, fontWeight = FontWeight.SemiBold)
        }
        if (tziruf) Note(stringResource(R.string.tziruf_note))
        TzirufMore()
        HorizontalDivider(color = ClockColors.Line)
        var live by remember { mutableStateOf(Live.notificationOn(context)) }
        val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                live = true
                Live.setNotification(context, true)
            }
        }
        val toggleLive: (Boolean) -> Unit = { enabled ->
            if (enabled && android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                live = enabled
                Live.setNotification(context, enabled)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { toggleLive(!live) }) {
            Checkbox(checked = live, onCheckedChange = toggleLive, colors = checkColors())
            Text(stringResource(R.string.live_notif), color = ClockColors.Ink, fontWeight = FontWeight.SemiBold)
        }
        Note(stringResource(R.string.live_notif_note))
    }
    if (showTzirufWarning) {
        AlertDialog(
            onDismissRequest = { showTzirufWarning = false },
            title = { Text(stringResource(R.string.tziruf_warning_title)) },
            text = { Text(stringResource(R.string.tziruf_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showTzirufWarning = false
                    onTziruf(true)
                }) {
                    Text(stringResource(R.string.tziruf_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTzirufWarning = false }) {
                    Text(stringResource(R.string.tziruf_cancel))
                }
            }
        )
    }
}

// ---------------------------------------------------------------- как считаем

@Composable
private fun InfoSheet(onNightScreen: () -> Unit, onIntro: () -> Unit, onPrivacy: () -> Unit) {
    SheetColumn {
        SheetTitle(stringResource(R.string.help))
        Text(stringResource(R.string.legend_t), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        DialLegend()
        Note("📅 " + stringResource(R.string.cal_hint))
        HorizontalDivider(color = ClockColors.Line)
        Text(stringResource(R.string.info), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        Note(minhagNote())
        Note(stringResource(R.string.shabbat_note))
        Note(stringResource(R.string.alarm_note))
        Note(stringResource(R.string.kl_help))
        TzirufMore()
        Button(onClick = onNightScreen, colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Surface)) {
            Text("🌙  " + stringResource(R.string.night_screen), color = ClockColors.Ink)
        }
        HorizontalDivider(color = ClockColors.Line)
        AddIconButton()
        Note(stringResource(R.string.ob5_l1))
        AddWidgetButtons()
        HomeScreenHelp()
        Note(stringResource(R.string.ob5_l2))
        Note(stringResource(R.string.ob5_l3))
        OutlinedButton(onClick = onIntro, border = BorderStroke(1.dp, ClockColors.Line)) {
            Text(stringResource(R.string.ob_again), color = ClockColors.Ink)
        }
        HorizontalDivider(color = ClockColors.Line)
        AboutBlock(onPrivacy)
    }
}

private const val SITE = "https://niflaot.mychitas.app/"
private const val PRIVACY_URL = "https://niflaot.mychitas.app/peleshaon/privacy/"
private const val MAIL = "Office@mychitas.app"

private fun langParam(): String = when (Locale.getDefault().language) {
    "ru", "uk", "be" -> "ru"; "de" -> "de"; "iw", "he" -> "he"; else -> "en"
}

private fun openUri(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // нет браузера или почты — ничего не открываем
    }
}

/** «О приложении»: версия, сайт Нифлаот, почта, конфиденциальность. */
@Composable
private fun AboutBlock(onPrivacy: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Text(stringResource(R.string.about_t), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
    Note(stringResource(R.string.about_by) + " · " + stringResource(R.string.about_version, version))
    LinkRow(stringResource(R.string.about_site)) { openUri(context, "$SITE?lang=${langParam()}&ref=app") }
    LinkRow(stringResource(R.string.about_mail)) { openUri(context, "mailto:$MAIL") }
    LinkRow(stringResource(R.string.privacy_t), onPrivacy)
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = ClockColors.TcheletDeep,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)
    )
}

/** Коротко — что приложение делает с данными; полный текст — на сайте (тот же, что в Google Play). */
@Composable
private fun PrivacySheet() {
    val context = LocalContext.current
    SheetColumn {
        SheetTitle(stringResource(R.string.privacy_t))
        for (id in listOf(R.string.privacy_1, R.string.privacy_2, R.string.privacy_3, R.string.privacy_4)) {
            Row {
                Text("•", color = ClockColors.Gold, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
                Text(stringResource(id), fontSize = 14.5.sp, lineHeight = 21.sp, color = ClockColors.Ink2)
            }
        }
        LinkRow(stringResource(R.string.privacy_full)) {
            val anchor = when (langParam()) { "ru" -> "ru"; "de" -> "de"; "he" -> "he"; else -> "en" }
            openUri(context, "$PRIVACY_URL#$anchor")
        }
        Note(stringResource(R.string.about_mail))
    }
}

/** «Что такое сочетание Имени?» — раскрывается нажатием: число сочетаний, источники, порядок на часах, как смотреть. */
@Composable
private fun TzirufMore() {
    var open by remember { mutableStateOf(false) }
    Text(
        (if (open) "▾ " else "▸ ") + stringResource(R.string.tziruf_more),
        color = ClockColors.Gold, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable { open = !open }.padding(vertical = 6.dp)
    )
    if (open) for (p in stringResource(R.string.tziruf_more_body).split("\n\n")) Note(p)
}

/** Как считаем: у Хабада — прежний текст, у других общин — их правило и точность расчёта. */
@Composable
private fun minhagNote(): String = when (ZmanimEngine.minhag) {
    Minhag.CHABAD -> stringResource(R.string.note)
    Minhag.LITVISH -> stringResource(R.string.minhag_d_litvish) + " " + stringResource(R.string.note_acc)
    Minhag.SEFARDI -> stringResource(R.string.minhag_d_sefardi) + " " + stringResource(R.string.note_acc)
}

/**
 * «Настройки» (владелец, 18.11.2026): язык, тема и община — Хабад, литовская, сефардская; по общине сами меняются
 * времена дня, исход Шабата, Кидуш Левана и даты календаря.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

@Composable
private fun FaceSettings(context: Context, face: String, title: Int, onChange: () -> Unit) {
    var style by remember(face) { mutableStateOf(Prefs.faceStyle(context, face)) }
    var time by remember(face) { mutableStateOf(Prefs.faceTime(context, face)) }
    var visible by remember(face) { mutableStateOf(Prefs.subFaceVisible(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(stringResource(title), color = ClockColors.Ink, fontWeight = FontWeight.SemiBold)
        if (face == "sub") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.clock_sub_visible), color = ClockColors.Ink2, modifier = Modifier.weight(1f))
                Switch(checked = visible, onCheckedChange = {
                    visible = it
                    Prefs.setSubFaceVisible(context, it)
                    onChange()
                }, colors = SwitchDefaults.colors(checkedTrackColor = ClockColors.Gold, checkedThumbColor = ClockColors.SheetBg))
            }
        }
        Text(stringResource(R.string.clock_style_title), color = ClockColors.Ink2, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, label) in listOf("DIGITAL" to R.string.clock_digital, "ANALOG" to R.string.clock_analog)) {
                FilterChip(selected = style == value, onClick = {
                    style = value
                    Prefs.setFaceStyle(context, face, value)
                }, label = { Text(stringResource(label), fontSize = 12.sp) }, colors = chipColors())
            }
        }
        Text(stringResource(R.string.clock_time_title), color = ClockColors.Ink2, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((value, label) in listOf("HALACHIC" to R.string.clock_halachic, "CIVIL" to R.string.clock_civil)) {
                FilterChip(selected = time == value, onClick = {
                    time = value
                    Prefs.setFaceTime(context, face, value)
                    app.mychitas.peleshaon.live.Live.updateAsync(context)
                }, label = { Text(stringResource(label), fontSize = 12.sp) }, colors = chipColors())
            }
        }
    }
}

@Composable
private fun SubFaceDial(
    now: ZonedDateTime,
    half: app.mychitas.peleshaon.domain.Half?,
    halachic: Boolean,
    digital: Boolean,
    hourLabel: String?,
    modifier: Modifier = Modifier
) {
    val elapsedSeconds = if (halachic) {
        ((half?.frac(now) ?: 0.0).coerceIn(0.0, 0.999999) * 43_200.0).toInt()
    } else 0
    val hour = if (halachic) (elapsedSeconds / 3600) % 12 + 1 else now.hour
    val minute = if (halachic) (elapsedSeconds / 60) % 60 else now.minute
    val second = if (halachic) elapsedSeconds % 60 else now.second
    val timeText = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    val label = if (halachic) hourLabel ?: stringResource(R.string.clock_halachic)
        else stringResource(R.string.clock_civil)

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = ClockColors.RimFill,
        border = BorderStroke(1.5.dp, ClockColors.Gold.copy(alpha = 0.9f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (digital) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(timeText, color = ClockColors.BigTime, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(label, color = ClockColors.Muted, fontSize = 8.sp, maxLines = 1)
                    Text("•", color = ClockColors.Gold.copy(alpha = 0.7f), fontSize = 8.sp)
                }
            } else {
                Canvas(Modifier.fillMaxSize().padding(13.dp)) {
                    val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val hourAngle = ((hour % 12) + minute / 60.0) * 30.0
                    val minuteAngle = (minute + second / 60.0) * 6.0
                    rotate(hourAngle.toFloat(), pivot = c) {
                        drawLine(ClockColors.BigTime, c, androidx.compose.ui.geometry.Offset(c.x, c.y - size.minDimension * 0.23f),
                            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                    rotate(minuteAngle.toFloat(), pivot = c) {
                        drawLine(ClockColors.BigTime, c, androidx.compose.ui.geometry.Offset(c.x, c.y - size.minDimension * 0.34f),
                            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                    rotate((second * 6).toFloat(), pivot = c) {
                        drawLine(ClockColors.Hand, c, androidx.compose.ui.geometry.Offset(c.x, c.y - size.minDimension * 0.38f),
                            strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                    }
                    drawCircle(ClockColors.Gold, radius = 3.dp.toPx(), center = c)
                }
                Text(label, color = ClockColors.Muted, fontSize = 7.sp, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp))
            }
        }
    }
}

@Composable
private fun DigitalFaceTime(
    now: ZonedDateTime,
    halachic: Boolean,
    hourLabel: String?,
    modifier: Modifier = Modifier
) {
    val timeLabel = if (halachic) (hourLabel ?: stringResource(R.string.clock_halachic))
        else stringResource(R.string.clock_civil)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = ClockColors.RimFill,
            border = BorderStroke(1.5.dp, ClockColors.Gold.copy(alpha = 0.72f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val inset = 1.5.dp.toPx()
                    val diameter = size.minDimension - inset * 2
                    val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
                    val topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f
                    )
                    drawCircle(
                        color = ClockColors.RimStroke.copy(alpha = 0.42f),
                        radius = diameter / 2f,
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx())
                    )
                    drawArc(
                        color = ClockColors.Gold.copy(alpha = 0.78f),
                        startAngle = -90f,
                        sweepAngle = (now.second / 60f) * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(now.format(DateTimeFormatter.ofPattern("HH")), color = ClockColors.BigTime,
                            fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Text(":", color = ClockColors.Gold.copy(alpha = if (now.second % 2 == 0) 0.9f else 0.42f),
                            fontSize = 40.sp, fontWeight = FontWeight.Light)
                        Text(now.format(DateTimeFormatter.ofPattern("mm")), color = ClockColors.BigTime,
                            fontSize = 42.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(timeLabel, color = ClockColors.Muted, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(4.dp)
                            .background(ClockColors.Gold.copy(alpha = 0.72f), CircleShape)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(onChange: () -> Unit) {
    val context = LocalContext.current
    SheetColumn {
        SheetTitle(stringResource(R.string.settings))
        if (Prefs.cityUnconfirmed(context)) {
            Note(stringResource(if (Prefs.cityUnmatched(context)) R.string.city_unmatched_warning
                else R.string.city_unconfirmed_warning))
        }

        // язык
        Text(stringResource(R.string.lang_t), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        var lang by remember { mutableStateOf(AppLang.tag(context)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in AppLang.TAGS) {
                FilterChip(
                    selected = lang == t,
                    onClick = {
                        if (lang == t) return@FilterChip
                        lang = t
                        AppLang.set(context, t)
                        // окно — заново, уже на новом языке
                        // Android 13+ normally recreates Activity via LocaleManager.
                        // Some ColorOS variants need manual recreation for resource refresh.
                        if (android.os.Build.VERSION.SDK_INT < 33 ||
                            android.os.Build.MANUFACTURER.lowercase() in setOf("oppo", "realme", "oneplus")) {
                            var host: Context = context
                            while (host is android.content.ContextWrapper && host !is android.app.Activity) {
                                host = host.baseContext
                            }
                            (host as? android.app.Activity)?.recreate()
                        }
                        app.mychitas.peleshaon.live.Live.updateAsync(context)
                    },
                    label = { Text(if (t.isEmpty()) stringResource(R.string.lang_system) else AppLang.label(t), fontSize = 13.sp) },
                    colors = chipColors()
                )
            }
        }
        HorizontalDivider(color = ClockColors.Line)

        // тема
        Text(stringResource(R.string.theme_t), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((m, label) in listOf(ClockTheme.AUTO to R.string.theme_auto, ClockTheme.LIGHT to R.string.theme_light, ClockTheme.DARK to R.string.theme_dark)) {
                FilterChip(
                    selected = ClockTheme.mode == m,
                    onClick = {
                        ClockTheme.set(context, m)
                        app.mychitas.peleshaon.live.Live.updateAsync(context)
                    },
                    label = { Text(stringResource(label), fontSize = 13.sp) },
                    colors = chipColors()
                )
            }
        }
        Note(stringResource(R.string.theme_note))
        HorizontalDivider(color = ClockColors.Line)
        Text(stringResource(R.string.clock_faces_title), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        FaceSettings(context, "main", R.string.clock_main_face, onChange)
        FaceSettings(context, "sub", R.string.clock_sub_face, onChange)
        HorizontalDivider(color = ClockColors.Line)
        HorizontalDivider(color = ClockColors.Line)
        // There is no valid coordinate context for geographically sensitive settings.
        if (Prefs.chosenCity(context) == null) return@SheetColumn

        // Local candle-lighting practice is independent of the community's zmanim profile.
        Text(stringResource(R.string.candle_offset_title), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        var candleSetting by remember { mutableStateOf(Prefs.candleOffset(context)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (offset in listOf<Long?>(null, 18L, 20L, 22L, 30L, 40L)) {
                FilterChip(
                    selected = candleSetting == offset,
                    onClick = {
                        candleSetting = offset
                        Prefs.setCandleOffset(context, offset)
                        ZmanimEngine.candleOffsetOverride = offset
                        AlarmScheduler.rescheduleAllFromPrefs(context)
                        app.mychitas.peleshaon.live.Live.updateAsync(context)
                        onChange()
                    },
                    label = {
                        Text(if (offset == null) stringResource(R.string.candle_offset_local)
                            else stringResource(R.string.candle_offset_minutes, offset.toInt()), fontSize = 12.sp)
                    },
                    colors = chipColors()
                )
            }
        }
        Note(stringResource(R.string.candle_offset_note))
        val localCandleMinutes = ZmanimEngine.localCandleMinutesFor(Prefs.city(context).name)
        if (candleSetting != null && candleSetting!! < localCandleMinutes) {
            Note(stringResource(R.string.candle_offset_warning))
        }
        HorizontalDivider(color = ClockColors.Line)

        // Earlier silence is separate from actual candle lighting and calendar calculations.
        Text(stringResource(R.string.shabbat_quiet_early_title), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        var earlyQuiet by remember { mutableStateOf(Prefs.earlyShabbatMinutes(context)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (minutes in listOf(0L, 15L, 30L, 45L, 60L)) {
                FilterChip(
                    selected = earlyQuiet == minutes,
                    onClick = {
                        earlyQuiet = minutes
                        Prefs.setEarlyShabbatMinutes(context, minutes)
                        AlarmScheduler.rescheduleAllFromPrefs(context)
                        onChange()
                    },
                    label = { Text(stringResource(R.string.shabbat_quiet_early_choice, minutes.toInt()), fontSize = 12.sp) },
                    colors = chipColors()
                )
            }
        }
        Note(stringResource(R.string.shabbat_quiet_early_note))
        HorizontalDivider(color = ClockColors.Line)

        // община
        Text(stringResource(R.string.minhag_t), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        var minhag by remember { mutableStateOf(ZmanimEngine.minhag) }
        for ((m, title, about) in listOf(
            Triple(Minhag.CHABAD, R.string.minhag_chabad, R.string.minhag_d_chabad),
            Triple(Minhag.LITVISH, R.string.minhag_litvish, R.string.minhag_d_litvish),
            Triple(Minhag.SEFARDI, R.string.minhag_sefardi, R.string.minhag_d_sefardi)
        )) {
            val on = minhag == m
            Surface(
                onClick = {
                    if (on) return@Surface
                    minhag = m
                    ZmanimEngine.minhag = m
                    Prefs.setMinhag(context, m)
                    // будильник «до конца Шма», покой в Шабат, напоминание о Кидуш Левана, виджеты — по новой общине
                    AlarmScheduler.rescheduleAllFromPrefs(context)
                    app.mychitas.peleshaon.moon.MoonReminder.schedule(context)
                    app.mychitas.peleshaon.live.Live.updateAsync(context)
                    onChange()
                },
                shape = RoundedCornerShape(12.dp),
                color = if (on) ClockColors.Gold.copy(alpha = 0.18f) else ClockColors.Surface,
                border = BorderStroke(if (on) 2.dp else 1.dp, if (on) ClockColors.Gold else ClockColors.Line),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    RadioButton(selected = on, onClick = null, colors = radioColors())
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(title), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
                        Text(stringResource(about), color = ClockColors.Ink2, fontSize = 13.sp)
                    }
                }
            }
        }
        Note(stringResource(R.string.minhag_note))
    }
}
