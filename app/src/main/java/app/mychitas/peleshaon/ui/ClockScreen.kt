package app.mychitas.peleshaon.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.mychitas.peleshaon.alarm.AlarmScheduler
import app.mychitas.peleshaon.alarm.AlarmSpec
import app.mychitas.peleshaon.alarm.WakeMode
import app.mychitas.peleshaon.domain.CITIES
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.Dial
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
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
private enum class Panel { CITY, SHABBAT, WAKE, STUDY, TIMES, INFO, PRIVACY, CALENDAR }

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
fun ClockScreen(onNightScreen: () -> Unit, onIntro: () -> Unit = {}) {
    val context = LocalContext.current
    val locale = Locale.getDefault()

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

    val city = remember(version) { Prefs.city(context) }
    val auto = remember(version) { Prefs.chosenCity(context) == null }
    var marks by remember { mutableStateOf(Prefs.marks(context)) }
    var tziruf by remember { mutableStateOf(Prefs.tziruf(context)) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var pop by remember { mutableStateOf<Triple<String, Float, Float>?>(null) }

    val zone = city.zone()
    val cityNow = now.withZoneSameInstant(zone)
    val minute = cityNow.truncatedTo(ChronoUnit.MINUTES)
    val day = remember(city, minute) {
        ZmanimEngine.calculateForMoment(cityNow, city.name, city.lat, city.lon, 0.0, zone, city.isIsrael)
    }
    val sh = remember(city, minute) { ZmanimEngine.shabbatOf(cityNow, city) }
    // циферблат — половина суток: 12 галахических часов дня или ночи (владелец, 17.11.2026)
    val half = remember(city, minute) { ZmanimEngine.halfOf(cityNow, city) }
    val halfItems = remember(half, city) { half?.let { ZmanimEngine.halfItems(it, city) } ?: emptyList() }
    val ring = Dial.ring(cityNow, half)
    val hourLabel = half?.let { stringResource(if (it.day) R.string.hour_day else R.string.hour_night, it.hour(cityNow)) }
    // еврейская дата — день обычным числом, крупно
    val jd = remember(day.date, city) { ZmanimEngine.jewishDay(day.date, city.isIsrael) }

    // точки циферблата: времена этой половины суток; свечи и исход — если они в ней
    val events = remember(half, halfItems, sh, marks) { faceEvents(half, halfItems, sh, marks) }

    val wdTime = { at: ZonedDateTime ->
        "${at.withZoneSameInstant(zone).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} ${at.withZoneSameInstant(zone).format(HHMM)}"
    }
    val dayText = { d: LocalDate -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)) }
    val shabbatOn = sh.candles != null && !cityNow.isBefore(sh.candles)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF060B1E))) {
        Image(
            painter = painterResource(R.drawable.clock_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ---- шапка: город, дата, «Как считаем»
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(onClick = { panel = Panel.CITY }, modifier = Modifier.widthIn(max = 150.dp)) {
                    Text("📍", fontSize = 14.sp)
                    Text(city.title(locale), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ClockColors.Ink)
                    Text("▾", fontSize = 12.sp, color = ClockColors.TcheletDeep)
                }
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        .clickable(onClickLabel = stringResource(R.string.cal_title)) { panel = Panel.CALENDAR },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // «7 кислева 5787» крупно; ниже — буквами и гражданская дата; нажатие — календарь
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("📅", fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp, bottom = 5.dp))
                        Text("${jd.day}", color = ClockColors.GoldDeep, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            jewishMonthName(jd, locale) + "  " + jd.year,
                            color = ClockColors.GoldDeep, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        day.jewishDateHebrew + " · " + cityNow.format(DateTimeFormatter.ofPattern("EEE, d MMMM", locale)),
                        color = ClockColors.Ink2, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (day.isNextJewishDayAfterSunset) Text(stringResource(R.string.evening), color = ClockColors.Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                Chip(onClick = { panel = Panel.INFO }, round = true) {
                    Text("?", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ClockColors.TcheletDeep)
                }
            }

            // ---- циферблат на всё место и подсказка у точки
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (day.isPolarAnomaly) {
                    Text(stringResource(R.string.polar), color = ClockColors.Ink2, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                }
                BigFace(
                    now = cityNow,
                    zone = zone,
                    half = half,
                    ring = ring,
                    events = events,
                    active = pop?.first,
                    tziruf = tziruf,
                    modifier = Modifier.fillMaxSize(),
                    hourLabel = hourLabel,
                    onPick = { id, fx, fy -> pop = if (id == null || pop?.first == id) null else Triple(id, fx, fy) }
                )
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
            if (pop == null && !day.isPolarAnomaly) {
                Text(stringResource(R.string.hint), color = ClockColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            // ---- кнопки внизу: Шабат · Будильник · Учёба · Времена
            val off = stringResource(R.string.off2)
            val nextWake = remember(version, city, minute) { AlarmRules.nextRing(Prefs.alarm(context, Prefs.MORNING), cityNow, city) }
            val nextStudy = remember(version, city, minute) { AlarmRules.nextRing(Prefs.alarm(context, Prefs.TORAH), cityNow, city) }
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
                    AlarmScheduler.rescheduleAllFromPrefs(context)
                    pop = null
                    bump()
                    panel = null
                }
                Panel.SHABBAT -> ShabbatSheet(sh, cityNow, city, wdTime, dayText)
                Panel.WAKE, Panel.STUDY -> AlarmSheet(
                    type = if (open == Panel.WAKE) Prefs.MORNING else Prefs.TORAH,
                    city = city, now = cityNow, wdTime = wdTime, dayText = dayText, onChange = bump
                )
                Panel.TIMES -> TimesSheet(day.items, day.isNextJewishDayAfterSunset, cityNow, zone, marks, tziruf, dayText,
                    onToggle = { type ->
                        marks = if (type in marks) marks - type else marks + type
                        Prefs.setMarks(context, marks)
                    },
                    onTziruf = { tziruf = it; Prefs.setTziruf(context, it) })
                Panel.INFO -> InfoSheet(onNightScreen = { panel = null; onNightScreen() }, onIntro = { panel = null; onIntro() }, onPrivacy = { panel = Panel.PRIVACY })
                Panel.PRIVACY -> PrivacySheet()
                Panel.CALENDAR -> CalendarSheet(city, cityNow)
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
        color = Color(0xF20C1430),
        border = BorderStroke(1.dp, ClockColors.Line),
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
    val locale = Locale.getDefault()
    var q by remember { mutableStateOf("") }
    val list = remember(q) { if (q.isBlank()) CITIES.sortedBy { it.title(locale) } else findCities(q, 30) }
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
        if (list.isEmpty()) Note(stringResource(R.string.city_none))
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
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
                    Text(c.zoneId.substringAfter('/').replace('_', ' '), fontSize = 12.sp, color = ClockColors.Muted)
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
        Note(stringResource(R.string.shabbat_note))
    }
}

// ---------------------------------------------------------------- будильники

@Composable
private fun AlarmSheet(
    type: String,
    city: CityOption,
    now: ZonedDateTime,
    wdTime: (ZonedDateTime) -> String,
    dayText: (LocalDate) -> String,
    onChange: () -> Unit
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val study = type == Prefs.TORAH
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
    val next = AlarmRules.nextRing(spec, now, city)
    val skips = AlarmRules.skipped(spec, now, city)
    val timeText = String.format(Locale.ROOT, "%02d:%02d", spec.hour, spec.minute)

    SheetColumn {
        PermissionsCard()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (study) R.string.study else R.string.alarm), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ClockColors.Ink, modifier = Modifier.weight(1f))
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
                androidx.core.content.ContextCompat.startForegroundService(context, i)
            },
            border = BorderStroke(1.dp, ClockColors.Line)
        ) { Text(stringResource(R.string.alarm_test), color = ClockColors.Ink) }
        Note(stringResource(R.string.alarm_note))
    }
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
                "ru" -> "ru"; "de" -> "de"; "iw", "he" -> "he"; else -> "en"
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
    SheetColumn {
        SheetTitle(stringResource(R.string.times_title))
        if (tomorrow) Text(stringResource(R.string.tomorrow, dayText(now.toLocalDate().plusDays(1))), color = ClockColors.GoldDeep, fontSize = 13.sp)
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
        Text(stringResource(R.string.on_face) + " — ✓", color = ClockColors.Muted, fontSize = 12.sp)
        HorizontalDivider(color = ClockColors.Line)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onTziruf(!tziruf) }) {
            Checkbox(checked = tziruf, onCheckedChange = onTziruf, colors = checkColors())
            Text(stringResource(R.string.tziruf), color = ClockColors.Ink, fontWeight = FontWeight.SemiBold)
        }
        if (tziruf) Note(stringResource(R.string.tziruf_note))
        HorizontalDivider(color = ClockColors.Line)
        var live by remember { mutableStateOf(Live.notificationOn(context)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { live = !live; Live.setNotification(context, live) }) {
            Checkbox(checked = live, onCheckedChange = { live = it; Live.setNotification(context, it) }, colors = checkColors())
            Text(stringResource(R.string.live_notif), color = ClockColors.Ink, fontWeight = FontWeight.SemiBold)
        }
        Note(stringResource(R.string.live_notif_note))
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
        Note(stringResource(R.string.note))
        Note(stringResource(R.string.shabbat_note))
        Note(stringResource(R.string.alarm_note))
        Button(onClick = onNightScreen, colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Surface)) {
            Text("🌙  " + stringResource(R.string.night_screen), color = ClockColors.Ink)
        }
        HorizontalDivider(color = ClockColors.Line)
        Note(stringResource(R.string.ob5_l1))
        AddWidgetButtons()
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
    "ru" -> "ru"; "de" -> "de"; "iw", "he" -> "he"; else -> "en"
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
