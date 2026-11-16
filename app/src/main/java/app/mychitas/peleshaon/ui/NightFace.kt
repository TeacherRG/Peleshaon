package app.mychitas.peleshaon.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.domain.Dial
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Циферблат для ночи — у кровати и заставкой Android: тот же, что на главном экране, без нажатий.
 * Раз в минуту чуть сдвигается, чтобы не выжечь экран.
 */
@Composable
fun NightFace(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var shift by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            if (now.second == 0) shift = (-6..6).random() to (-6..6).random()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val city = remember { Prefs.city(context) }
    val marks = remember { Prefs.marks(context) }
    val tziruf = remember { Prefs.tziruf(context) }
    val zone = city.zone()
    val cityNow = now.withZoneSameInstant(zone)
    val minute = cityNow.truncatedTo(ChronoUnit.MINUTES)
    val day = remember(minute) {
        ZmanimEngine.calculateForMoment(cityNow, city.name, city.lat, city.lon, 0.0, zone, city.isIsrael)
    }
    val events = remember(day) {
        day.items.map { FaceEvent(it.type.name, it.roundedTime, zmanColor(it.type), it.type in marks) }
    }
    val ring = Dial.ring(
        cityNow, zone,
        day.time(ZmanType.SUNRISE)?.exactTime, day.time(ZmanType.SUNSET)?.exactTime,
        day.time(ZmanType.ALOT)?.exactTime, day.time(ZmanType.TZAIT)?.exactTime
    )
    Box(modifier = modifier) {
        BigFace(
            now = cityNow, zone = zone, ring = ring, events = events, active = null, tziruf = tziruf,
            modifier = Modifier.fillMaxSize().padding(24.dp).offset(shift.first.dp, shift.second.dp)
        )
    }
}
