package app.mychitas.peleshaon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.domain.ZmanimEngine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendar without a selected location: civil and arithmetic Hebrew dates only.
 * Never infer Israel/diaspora holiday rules or sunset rollover from the phone zone.
 */
@Composable
fun CalendarNoCity(today: LocalDate) {
    val locale = Locale.getDefault()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    val lead = month.atDay(1).dayOfWeek.value % 7
    val days = List<LocalDate?>(lead) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = { month = month.minusMonths(1) }, color = ClockColors.Surface,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("‹", fontSize = 26.sp) }
            }
            Text(month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Surface(onClick = { month = month.plusMonths(1) }, color = ClockColors.Surface,
                shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("›", fontSize = 26.sp) }
            }
        }
        Row {
            for (n in 0..6) {
                val d = DayOfWeek.of(if (n == 0) 7 else n)
                Text(d.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 12.sp, color = ClockColors.Ink)
            }
        }
        for (week in days.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..6) {
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f)) {
                        if (d != null) {
                            val hebrew = ZmanimEngine.jewishDay(d, false)
                            Surface(onClick = { selected = d },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(if (selected == d) 2.dp else 1.dp,
                                    if (selected == d) ClockColors.Gold else ClockColors.Line),
                                color = ClockColors.Surface,
                                modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(d.dayOfMonth.toString(), fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold, color = ClockColors.Ink)
                                    Text(hebrew.day.toString(), fontSize = 12.sp,
                                        color = ClockColors.GoldDeep)
                                }
                            }
                        }
                    }
                }
            }
        }
        val jd = ZmanimEngine.jewishDay(selected, false)
        Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)),
            color = ClockColors.Ink, fontWeight = FontWeight.Bold)
        Text("${jd.day} " + jewishMonthName(jd, locale) + " ${jd.year}",
            color = ClockColors.GoldDeep, fontSize = 18.sp)
        Text(stringResource(R.string.city_required_for_halacha),
            fontSize = 13.sp, color = ClockColors.Muted)
    }
}
