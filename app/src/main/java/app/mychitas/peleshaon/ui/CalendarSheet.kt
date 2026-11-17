package app.mychitas.peleshaon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.domain.CalDay
import app.mychitas.peleshaon.domain.CalEvent
import app.mychitas.peleshaon.domain.CalKind
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.JewishCal
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Цвет вида события — точка в клетке и в списке. */
private fun kindColor(k: CalKind): Color = when (k) {
    CalKind.HOLIDAY -> ClockColors.Gold
    CalKind.EREV -> ClockColors.GoldDeep.copy(alpha = 0.7f)
    CalKind.FAST -> ClockColors.Bad
    CalKind.MINOR -> ClockColors.TcheletDeep
    CalKind.ROSH_CHODESH -> ClockColors.Tchelet
    CalKind.CHABAD -> ClockColors.Plum
    CalKind.CHANUKAH -> ClockColors.Gold
    CalKind.OMER -> ClockColors.Muted
    CalKind.PARSHA, CalKind.SPECIAL_SHABBAT -> ClockColors.Ink2
}

/** События, которые стоит отметить точкой в клетке (не глава и не Омер — они есть почти каждый день). */
private fun marked(d: CalDay) = d.events.filter { it.kind != CalKind.PARSHA && it.kind != CalKind.OMER && it.kind != CalKind.SPECIAL_SHABBAT }

/**
 * Еврейский календарь (владелец, 17.11.2026: «эргономичный современный еврейский календарь»): еврейский месяц сеткой — неделя с
 * воскресенья, Шабат справа (на иврите — слева) и золотом; в клетке — еврейский день крупно, гражданский мелко, точки праздников.
 * Нажатие на день — его события, глава недели, времена дня, свечи и исход. Ниже — ближайшие даты.
 */
@Composable
fun CalendarSheet(city: CityOption, now: ZonedDateTime) {
    val locale = Locale.getDefault()
    val zone = city.zone()
    // после заката — уже завтрашний еврейский день
    val today = remember(now.toEpochSecond() / 60, city) {
        val t = now.withZoneSameInstant(zone)
        val set = ZmanimEngine.sunsetOf(t.toLocalDate(), city)
        if (set != null && !t.isBefore(set)) t.toLocalDate().plusDays(1) else t.toLocalDate()
    }
    var anchor by remember { mutableStateOf(today) }
    var selected by remember { mutableStateOf(today) }
    val month = remember(anchor, city) { JewishCal.monthOf(anchor, city.isIsrael) }
    val first = month.first()
    val sel = remember(selected, city) { JewishCal.day(selected, city.isIsrael) }
    val upcoming = remember(today, city) { JewishCal.upcoming(today, city.isIsrael) }
    val inThisMonth = month.any { it.date == today }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- шапка: ‹ Кислев 5787 › · ноябрь — декабрь 2026 · «Сегодня»
        Row(verticalAlignment = Alignment.CenterVertically) {
            NavButton("‹", stringResource(R.string.cal_prev)) {
                anchor = JewishCal.prevMonth(anchor, city.isIsrael)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(jewishMonthTitle(first.jd, locale), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = ClockColors.GoldDeep)
                val a = first.date
                val b = month.last().date
                val fmt = DateTimeFormatter.ofPattern("LLLL", locale)
                val range = when {
                    a.month == b.month && a.year == b.year -> "${a.format(fmt)} ${a.year}"
                    a.year != b.year -> "${a.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))} — ${b.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))}"
                    else -> "${a.format(fmt)} — ${b.format(fmt)} ${b.year}"
                }
                Text(range, fontSize = 13.sp, color = ClockColors.Ink2)
            }
            NavButton("›", stringResource(R.string.cal_next)) {
                anchor = JewishCal.nextMonth(anchor, city.isIsrael)
            }
        }
        if (!inThisMonth || selected != today) {
            TextButton(
                onClick = { anchor = today; selected = today },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("⟲ " + stringResource(R.string.cal_today), color = ClockColors.TcheletDeep, fontWeight = FontWeight.Bold) }
        }

        // ---- дни недели: воскресенье … Шабат
        val week = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        Row(Modifier.fillMaxWidth()) {
            for (d in week) {
                Text(
                    d.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (d == DayOfWeek.SATURDAY) ClockColors.GoldDeep else ClockColors.Muted
                )
            }
        }

        // ---- сетка месяца
        val lead = first.date.dayOfWeek.value % 7 // воскресенье — 0
        val cells: List<CalDay?> = List(lead) { null } + month
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in cells.chunked(7)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0 until 7) {
                        val d = row.getOrNull(i)
                        Box(Modifier.weight(1f)) {
                            if (d != null) DayCell(d, today = d.date == today, selected = d.date == selected, locale = locale) { selected = d.date }
                        }
                    }
                }
            }
        }

        // ---- выбранный день
        DayDetails(sel, city, locale)

        // ---- ближайшие даты
        HorizontalDivider(color = ClockColors.Line)
        Text(stringResource(R.string.cal_upcoming), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ClockColors.Ink)
        for ((date, e) in upcoming) {
            val days = ChronoUnit.DAYS.between(today, date)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { anchor = date; selected = date }.padding(vertical = 6.dp)
            ) {
                Box(Modifier.size(10.dp).background(kindColor(e.kind), CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(eventTitle(e, locale), color = ClockColors.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val jd = ZmanimEngine.jewishDay(date, city.isIsrael)
                    Text(
                        "${jewishDayMonth(jd, locale)} · ${date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))}",
                        color = ClockColors.Muted, fontSize = 12.sp
                    )
                }
                Text(
                    when (days) {
                        0L -> stringResource(R.string.cal_today_word)
                        1L -> stringResource(R.string.cal_tomorrow)
                        else -> stringResource(R.string.cal_in_days, days.toInt())
                    },
                    color = ClockColors.GoldDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NavButton(text: String, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, ClockColors.Line),
        modifier = Modifier.size(44.dp).semantics { contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, fontSize = 24.sp, color = ClockColors.Ink) }
    }
}

@Composable
private fun DayCell(d: CalDay, today: Boolean, selected: Boolean, locale: Locale, onClick: () -> Unit) {
    val shabbat = d.date.dayOfWeek == DayOfWeek.SATURDAY
    val holiday = d.holy && !shabbat
    val bg = when {
        holiday -> ClockColors.Gold.copy(alpha = 0.22f)
        shabbat -> ClockColors.Gold.copy(alpha = 0.10f)
        else -> ClockColors.Surface
    }
    val dots = marked(d).map { kindColor(it.kind) }.distinct().take(3)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .background(bg, RoundedCornerShape(10.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) ClockColors.Gold else ClockColors.Line,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(3.dp)
    ) {
        // еврейский день — крупно; сегодня — в кружке
        Box(
            Modifier.align(Alignment.TopCenter).size(28.dp)
                .background(if (today) ClockColors.Gold else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${d.jd.day}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = when {
                    today -> ClockColors.SheetBg
                    shabbat || holiday -> ClockColors.GoldDeep
                    else -> ClockColors.Ink
                }
            )
        }
        // гражданский день — мелко; 1-е число — с месяцем
        val g = d.date
        Text(
            if (g.dayOfMonth == 1) "${g.dayOfMonth} ${g.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale)}" else "${g.dayOfMonth}",
            modifier = Modifier.align(Alignment.Center).padding(top = 14.dp),
            fontSize = 10.sp,
            color = ClockColors.Muted,
            maxLines = 1
        )
        if (dots.isNotEmpty()) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in dots) Box(Modifier.size(6.dp).background(c, CircleShape))
            }
        }
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Выбранный день: дата крупно, события, глава недели, времена дня, свечи и исход. */
@Composable
private fun DayDetails(d: CalDay, city: CityOption, locale: Locale) {
    val items = remember(d.date, city) { ZmanimEngine.itemsOf(d.date, city).associateBy { it.type } }
    val next = remember(d.date, city) { JewishCal.day(d.date.plusDays(1), city.isIsrael) }
    val hebrew = remember(d.date, city) { ZmanimEngine.jewishDate(d.date, city.isIsrael).first }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, ClockColors.Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${d.jd.day}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = ClockColors.GoldDeep)
                Spacer(Modifier.width(6.dp))
                Text(jewishMonthName(d.jd, locale) + " " + d.jd.year, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ClockColors.GoldDeep, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(
                hebrew + " · " + d.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
                fontSize = 13.sp, color = ClockColors.Ink2
            )
            for (e in d.events) EventRow(e, locale)

            HorizontalDivider(color = ClockColors.Line)
            val zone = city.zone()
            // свечи: канун Шабата или Йом Това — за 18 мин до заката; между двумя святыми днями — после выхода звёзд
            val sunset = items[ZmanType.SUNSET]?.exactTime
            if (d.candles && sunset != null) {
                if (d.holy) {
                    items[ZmanType.TZAIT]?.let { TimeRow("🕯️", stringResource(R.string.cal_candles_after), it.roundedTime.withZoneSameInstant(zone).format(HHMM), ClockColors.GoldDeep) }
                } else {
                    TimeRow("🕯️", stringResource(R.string.z_t_candles), sunset.minusMinutes(ZmanimEngine.CANDLE_MIN).withZoneSameInstant(zone).format(HHMM), ClockColors.GoldDeep)
                }
            }
            if (d.holy && !next.holy && sunset != null) {
                TimeRow("✨", stringResource(R.string.z_t_havdalah), sunset.plusMinutes(ZmanimEngine.END_MIN).withZoneSameInstant(zone).format(HHMM), ClockColors.GoldDeep)
            }
            for (t in listOf(ZmanType.SUNRISE, ZmanType.SOF_ZMAN_SHMA, ZmanType.SOF_ZMAN_TFILA, ZmanType.SUNSET, ZmanType.TZAIT)) {
                val it = items[t] ?: continue
                TimeRow(null, stringResource(full(t.name)), it.roundedTime.withZoneSameInstant(zone).format(HHMM), zmanColor(t))
            }
            if (items.isEmpty()) Text(stringResource(R.string.polar_short), color = ClockColors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EventRow(e: CalEvent, locale: Locale) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(kindColor(e.kind), CircleShape))
        Spacer(Modifier.width(10.dp))
        val text = if (e.kind == CalKind.PARSHA) stringResource(R.string.cal_parsha, eventName(e, locale)) else eventName(e, locale)
        Text(text, color = ClockColors.Ink, fontSize = 14.sp, fontWeight = if (e.kind == CalKind.HOLIDAY || e.kind == CalKind.PARSHA) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun TimeRow(icon: String?, label: String, time: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(28.dp)) {
        if (icon != null) Text(icon, fontSize = 14.sp, modifier = Modifier.width(24.dp))
        else Box(Modifier.width(24.dp)) { Box(Modifier.size(8.dp).background(color, CircleShape)) }
        Text(label, color = ClockColors.Ink2, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(time, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
