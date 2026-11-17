package app.mychitas.peleshaon.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.live.LiveState
import java.util.Locale

/**
 * Знакомство при первом запуске: что это, город, как читать циферблат, будильники (здесь же — разрешение на
 * уведомления, а не сразу при запуске), часы вне приложения. Повторить — «?» → «Знакомство с приложением».
 */
private const val STEPS = 5

@Composable
fun Onboarding(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val last = step == STEPS - 1
    BackHandler(enabled = step > 0) { step-- }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF060B1E))) {
        Image(
            painter = painterResource(R.drawable.clock_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.ob_skip), color = ClockColors.Muted) }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "step",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { s ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (s) {
                        0 -> StepWelcome()
                        1 -> StepCity(onConfirm = { step++ })
                        2 -> StepDial()
                        3 -> StepAlarms()
                        else -> StepOutside()
                    }
                }
            }
            // точки шагов и кнопки
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                for (i in 0 until STEPS) {
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (i == step) 10.dp else 7.dp)
                            .background(if (i == step) ClockColors.Gold else ClockColors.Muted.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { step-- }, border = BorderStroke(1.dp, ClockColors.Line), modifier = Modifier.weight(1f).height(50.dp)) {
                        Text(stringResource(R.string.ob_back), color = ClockColors.Ink)
                    }
                }
                Button(
                    onClick = { if (last) onDone() else step++ },
                    colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text(stringResource(if (last) R.string.ob_start else R.string.ob_next), color = ClockColors.SheetBg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Title(id: Int) {
    Text(stringResource(id), fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = ClockColors.Ink)
}

@Composable
private fun Body(text: String) {
    Text(text, fontSize = 15.sp, lineHeight = 22.sp, color = ClockColors.Ink2)
}

/** Живой циферблат города — тот же, что на главном экране. */
@Composable
private fun LiveDial(modifier: Modifier) {
    val context = LocalContext.current
    val s = remember { LiveState.of(context) }
    BigFace(now = s.now, zone = s.city.zone(), half = s.half, ring = s.ring, events = s.events, active = null, tziruf = false, modifier = modifier)
}

@Composable
private fun StepWelcome() {
    Spacer(Modifier.height(4.dp))
    LiveDial(Modifier.fillMaxWidth().height(260.dp))
    Title(R.string.ob1_t)
    Body(stringResource(R.string.ob1_b))
}

@Composable
private fun StepCity(onConfirm: () -> Unit) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    val city = remember(version) { Prefs.city(context) }
    val auto = remember(version) { Prefs.chosenCity(context) == null }
    Title(R.string.ob2_t)
    Body(stringResource(R.string.ob2_b))
    Surface(shape = RoundedCornerShape(16.dp), color = ClockColors.Surface, border = BorderStroke(1.dp, ClockColors.Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📍 " + city.title(Locale.getDefault()), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ClockColors.Gold, textAlign = TextAlign.Center)
            if (auto) Text(stringResource(R.string.city_auto), fontSize = 13.sp, color = ClockColors.Muted)
        }
    }
    if (!choosing) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Surface), modifier = Modifier.weight(1f)) {
                Text("✓ " + stringResource(R.string.ob2_ok), color = ClockColors.Ink)
            }
            OutlinedButton(onClick = { choosing = true }, border = BorderStroke(1.dp, ClockColors.Line), modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.ob2_other), color = ClockColors.TcheletDeep, textAlign = TextAlign.Center)
            }
        }
    } else {
        Surface(shape = RoundedCornerShape(16.dp), color = ClockColors.SheetBg, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(top = 12.dp)) {
                CitySheet(city, auto, title = false) { chosen ->
                    Prefs.setCity(context, chosen)
                    version++
                    choosing = false
                }
            }
        }
    }
    Text(stringResource(R.string.ob2_later), fontSize = 13.sp, color = ClockColors.Muted)
}

@Composable
private fun StepDial() {
    Title(R.string.ob3_t)
    LiveDial(Modifier.fillMaxWidth().height(230.dp))
    DialLegend()
    Text("📅 " + stringResource(R.string.cal_hint), fontSize = 14.sp, lineHeight = 20.sp, color = ClockColors.Ink2)
}

/** Что на циферблате — образцы цвета и подписи. Есть и в справке «?». */
@Composable
fun DialLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendRow({ Swatch(listOf(ClockColors.Gold)) ; Swatch(listOf(ClockColors.Tchelet)) }, R.string.ob3_l1)
        LegendRow({ Swatch(listOf(ClockColors.Gold, ClockColors.Gold, ClockColors.Tchelet, ClockColors.Tchelet)) }, R.string.ob3_l2)
        LegendRow({ Swatch(listOf(ClockColors.Gold, ClockColors.Gold, ClockColors.Bad, ClockColors.Gold.copy(alpha = 0.3f))) }, R.string.ob3_l3)
        LegendRow({
            Box(Modifier.size(12.dp).background(ClockColors.Tchelet, CircleShape))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(12.dp).background(ClockColors.Ok, CircleShape))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(12.dp).background(ClockColors.Bad, CircleShape))
        }, R.string.ob3_l4)
        LegendRow({ Text("🕯️⏰📖🕘", fontSize = 13.sp) }, R.string.ob3_l5)
    }
}

@Composable
private fun LegendRow(sample: @Composable () -> Unit, text: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(64.dp), verticalAlignment = Alignment.CenterVertically) { sample() }
        Text(stringResource(text), fontSize = 14.sp, lineHeight = 20.sp, color = ClockColors.Ink2, modifier = Modifier.weight(1f))
    }
}

/** Несколько штрихов обода. */
@Composable
private fun Swatch(colors: List<Color>) {
    for (c in colors) {
        Box(Modifier.padding(end = 3.dp).width(4.dp).height(16.dp).background(c, RoundedCornerShape(1.dp)))
    }
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun StepAlarms() {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = granted() }

    Text("⏰", fontSize = 48.sp)
    Title(R.string.ob4_t)
    Body(stringResource(R.string.ob4_b))
    Body(stringResource(R.string.ob4_perm))
    if (ok) {
        Text("✓ " + stringResource(R.string.ob4_notif_ok), color = ClockColors.Ok, fontWeight = FontWeight.Bold)
    } else {
        Button(
            onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold)
        ) { Text(stringResource(R.string.ob4_notif), color = ClockColors.SheetBg, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun StepOutside() {
    Text("📱", fontSize = 48.sp)
    Title(R.string.ob5_t)
    for (id in listOf(R.string.ob5_l1, R.string.ob5_l2, R.string.ob5_l3)) {
        Row {
            Text("•", color = ClockColors.Gold, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
            Body(stringResource(id))
        }
    }
    Spacer(Modifier.height(8.dp))
    AddWidgetButtons()
}
