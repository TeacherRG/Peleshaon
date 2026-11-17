package app.mychitas.peleshaon.wear

import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.mychitas.peleshaon.domain.ZmanItem
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.cityForZone
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Gold = Color(0xFFD4AF37)
private val Tchelet = Color(0xFF1E88E5)
private val Face = Color(0xFF0D1B2A)
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }
}

@Composable
fun WearApp() {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000L)
        }
    }

    // Город — по часовому поясу часов (без геолокации)
    val city = remember { cityForZone(ZoneId.systemDefault()) }
    val zone = city.zone()
    val cityNow = now.withZoneSameInstant(zone)
    val day = remember(city, cityNow.truncatedTo(ChronoUnit.MINUTES)) {
        ZmanimEngine.calculateForMoment(cityNow, city.name, city.lat, city.lon, 0.0, zone, city.isIsrael)
    }
    val res = LocalContext.current.resources
    val next = day.items.firstOrNull { it.exactTime.isAfter(cityNow) }
    val candles = day.candleLighting?.takeIf {
        cityNow.dayOfWeek == DayOfWeek.FRIDAY && it.isAfter(cityNow)
    }

    MaterialTheme {
        val listState = rememberScalingLazyListState()
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    WatchRing(cityNow, day.items, next)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(cityNow.format(HHMM), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        if (next != null) {
                            Text(
                                text = "${res.getString(shortLabel(next.type))} ${next.roundedTime.format(HHMM)}",
                                color = if (next.type.isDeadline) Gold else Tchelet,
                                fontSize = 13.sp
                            )
                            Text(
                                text = countdown(res, cityNow, next),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = day.jewishDateHebrew.ifEmpty { res.getString(R.string.polar) },
                    color = Gold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (candles != null) {
                item { ZmanRow(res.getString(R.string.candles), candles.format(HHMM), Gold, passed = false) }
            }
            items(day.items) { z ->
                ZmanRow(
                    label = res.getString(shortLabel(z.type)),
                    time = z.roundedTime.format(HHMM),
                    color = if (z.type.isDeadline) Gold else Tchelet,
                    passed = !z.exactTime.isAfter(cityNow)
                )
            }
        }
    }
}

/** Обод часов: 12 штрихов, точки времён дня, дуга от «сейчас» до ближайшего времени. */
@Composable
private fun WatchRing(now: ZonedDateTime, items: List<ZmanItem>, next: ZmanItem?) {
    Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f * 0.9f
        fun angle(t: ZonedDateTime) = ((t.hour % 12) + t.minute / 60.0) * 30.0 - 90.0
        fun at(deg: Double, rr: Float) = Offset(
            c.x + rr * cos(deg * PI / 180).toFloat(),
            c.y + rr * sin(deg * PI / 180).toFloat()
        )

        drawCircle(Face, radius = r, center = c)
        drawCircle(Gold.copy(alpha = 0.5f), radius = r, center = c, style = Stroke(1.5.dp.toPx()))
        for (i in 0 until 12) {
            val deg = i * 30.0 - 90.0
            drawLine(
                Gold.copy(alpha = 0.6f), at(deg, r * if (i % 3 == 0) 0.86f else 0.91f), at(deg, r * 0.97f),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
            )
        }

        // Дуга «осталось» — если ближайшее время в пределах 12 часов
        if (next != null) {
            val minutes = ChronoUnit.MINUTES.between(now, next.exactTime)
            if (minutes in 1..719) {
                val start = angle(now).toFloat()
                drawArc(
                    color = (if (next.type.isDeadline) Gold else Tchelet).copy(alpha = 0.8f),
                    startAngle = start,
                    sweepAngle = minutes / 2f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Точки времён дня, прошедшие — бледнее, ближайшее — крупнее
        items.forEach { z ->
            val passed = !z.exactTime.isAfter(now)
            val color = (if (z.type.isDeadline) Gold else Tchelet).copy(alpha = if (passed) 0.3f else 1f)
            val big = z == next
            drawCircle(color, radius = (if (big) 5 else 3).dp.toPx(), center = at(angle(z.roundedTime), r))
        }

        // Стрелка «сейчас»
        drawLine(Color.White, at(angle(now), r * 0.78f), at(angle(now), r * 1.0f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun ZmanRow(label: String, time: String, color: Color, passed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = if (passed) 0.4f else 1f), fontSize = 14.sp)
        Text(time, color = color.copy(alpha = if (passed) 0.4f else 1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun countdown(res: Resources, now: ZonedDateTime, z: ZmanItem): String {
    val total = ChronoUnit.MINUTES.between(now, z.roundedTime).coerceAtLeast(0)
    val h = (total / 60).toInt()
    val m = (total % 60).toInt()
    val dur = if (h > 0) res.getString(R.string.dur_hm, h, m) else res.getString(R.string.dur_m, m)
    return res.getString(if (z.type.isDeadline) R.string.left else R.string.in_time, dur)
}

private fun shortLabel(type: ZmanType): Int = when (type) {
    ZmanType.ALOT -> R.string.zs_alot
    ZmanType.SUNRISE -> R.string.zs_sunrise
    ZmanType.SOF_ZMAN_SHMA -> R.string.zs_shma
    ZmanType.SOF_ZMAN_TFILA -> R.string.zs_tfila
    ZmanType.CHATZOT -> R.string.zs_chatzot
    ZmanType.MINCHA_GEDOLA -> R.string.zs_mincha
    ZmanType.PLAG_HAMINCHA -> R.string.zs_plag
    ZmanType.SUNSET -> R.string.zs_sunset
    ZmanType.TZAIT -> R.string.zs_tzait
}
