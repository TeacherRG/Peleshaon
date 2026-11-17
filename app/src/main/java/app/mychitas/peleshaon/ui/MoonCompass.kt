package app.mychitas.peleshaon.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.domain.KiddushLevanaNow
import app.mychitas.peleshaon.domain.KiddushLevanaRules
import app.mychitas.peleshaon.domain.MoonPosition
import app.mychitas.peleshaon.domain.MoonTracker
import app.mychitas.peleshaon.moon.MoonReminder
import app.mychitas.peleshaon.moon.compassName
import app.mychitas.peleshaon.moon.moonLine
import app.mychitas.peleshaon.moon.untilText
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Серебро лунной стрелки. */
private val Silver = Color(0xFFDCE3F0)
private val SilverDim = Color(0x80DCE3F0)

/**
 * Плашка на главном экране — только когда сейчас можно сказать Кидуш Левана (с 7 суток после молада до середины месяца,
 * ночью, не в вечер Шабата и Йом Това): «🌙 Кидуш Левана · 9 кислева» и «Луна на ЮЗ (224°), высота 28°». Нажатие — компас.
 */
@Composable
fun MoonPill(kl: KiddushLevanaNow, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, Silver.copy(alpha = 0.45f)),
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.kl_title) + " · " + jewishDayMonth(kl.jewishDay),
                    color = Silver, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1
                )
                Text(moonLine(context, kl.moon), color = ClockColors.Ink2, fontSize = 12.sp, maxLines = 1)
            }
            Text("›", color = Silver, fontSize = 22.sp)
        }
    }
}

/** Куда смотрит телефон: компас (истинный север), неточно ли, и держат ли его стоя (тогда — куда смотрит камера). */
private data class Heading(val deg: Float, val low: Boolean)

/** Компас телефона: датчик поворота (без разрешений), поправка на магнитное склонение; null — датчика нет. */
@Composable
private fun rememberHeading(lat: Double, lon: Double): Pair<Boolean, Heading?> {
    val context = LocalContext.current
    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember {
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    }
    var heading by remember { mutableStateOf<Heading?>(null) }
    val declination = remember(lat, lon) {
        GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
    }
    DisposableEffect(sensor, declination) {
        if (sensor == null) return@DisposableEffect onDispose { }
        val r = FloatArray(9)
        val out = FloatArray(9)
        val o = FloatArray(3)
        var sx = 0.0
        var sy = 0.0
        var first = true
        var low = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, ev.values)
                // телефон стоит (экран к лицу) — направление камеры; лежит — направление верха телефона
                val m = if (abs(r[8]) < 0.55f) {
                    SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, out); out
                } else r
                SensorManager.getOrientation(m, o)
                val a = Math.toRadians(Math.toDegrees(o[0].toDouble()) + declination)
                // сглаживание по кругу — стрелка не дрожит и не прыгает через север
                if (first) { sx = cos(a); sy = sin(a); first = false } else {
                    sx = sx * 0.85 + cos(a) * 0.15
                    sy = sy * 0.85 + sin(a) * 0.15
                }
                val deg = ((Math.toDegrees(atan2(sy, sx)) + 360) % 360).toFloat()
                heading = Heading(deg, low || ev.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
                low = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return (sensor != null) to heading
}

/**
 * Экран-компас Кидуш Левана: роза ветров поворачивается за телефоном, серебряная стрелка показывает на Луну,
 * дуга справа — на какой высоте её искать. Датчики — только пока открыт экран; ничего не сохраняется и не уходит.
 */
@Composable
fun MoonCompass(onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    // компас считает «верх экрана» — экран не поворачиваем, пока он открыт
    DisposableEffect(Unit) {
        val act = context as? Activity
        val prev = act?.requestedOrientation
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if (act != null && prev != null) act.requestedOrientation = prev }
    }
    val city = remember { Prefs.city(context) }
    var now by remember { mutableStateOf(ZonedDateTime.now(city.zone())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now(city.zone())
            delay(1000L)
        }
    }
    val moon = remember(now.toEpochSecond() / 5) { MoonTracker.position(now, city.lat, city.lon) }
    val kl = remember(now.toEpochSecond() / 60) { runCatching { KiddushLevanaRules.now(now, city) }.getOrNull() }
    val (hasSensor, heading) = rememberHeading(city.lat, city.lon)
    var remind by remember { mutableStateOf(MoonReminder.isOn(context)) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF060B1E))) {
        Image(
            painter = painterResource(R.drawable.clock_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("‹  " + stringResource(R.string.ob_back), color = ClockColors.Ink) }
                Spacer(Modifier.weight(1f))
                Text("🌙 " + stringResource(R.string.kl_title), color = Silver, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
            }

            // что делать: повернуться, поднять взгляд
            val delta = heading?.let { ((moon.azimuth - it.deg + 540) % 360 - 180).roundToInt() }
            val alt = moon.altitude.roundToInt()
            val big = when {
                !moon.aboveHorizon -> stringResource(R.string.kl_below)
                delta == null -> stringResource(R.string.kl_face, compassName(moon.azimuth), moon.azimuth.roundToInt() % 360)
                abs(delta) <= 8 -> stringResource(R.string.kl_ahead, alt)
                delta < 0 -> stringResource(R.string.kl_left, -delta)
                else -> stringResource(R.string.kl_right, delta)
            }
            Text(
                big, color = if (delta != null && abs(delta) <= 8 && moon.aboveHorizon) ClockColors.GoldDeep else ClockColors.Ink,
                fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )

            val heLabels = Locale.getDefault().language !in setOf("iw", "he")
            Canvas(modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp).aspectRatio(1f)) {
                drawCompass(heading?.deg ?: 0f, moon, heLabels)
            }

            Text(moonLine(context, moon) + " · " + stringResource(R.string.kl_illum, (moon.illumination * 100).roundToInt()),
                color = ClockColors.Ink2, fontSize = 14.sp, textAlign = TextAlign.Center)
            if (kl != null) {
                Text(stringResource(R.string.kl_until, untilText(kl.end)), color = ClockColors.GoldDeep, fontSize = 14.sp, textAlign = TextAlign.Center)
            } else {
                Text(stringResource(R.string.kl_not_tonight), color = ClockColors.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            if (!hasSensor) {
                Text(stringResource(R.string.kl_nosensor), color = ClockColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            } else {
                Text(stringResource(R.string.kl_hold), color = ClockColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (heading?.low == true) {
                    Text(stringResource(R.string.kl_calibrate), color = ClockColors.Bad, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }

            // напоминание — по желанию
            Surface(shape = RoundedCornerShape(16.dp), color = ClockColors.Surface, border = BorderStroke(1.dp, ClockColors.Line),
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        remind = !remind
                        if (remind) askNotifications(context) { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        MoonReminder.setOn(context, remind)
                    }) {
                        Text(stringResource(R.string.kl_remind), color = ClockColors.Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = remind,
                            onCheckedChange = {
                                remind = it
                                if (it) askNotifications(context) { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                MoonReminder.setOn(context, it)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = ClockColors.SheetBg, checkedTrackColor = ClockColors.Gold)
                        )
                    }
                    Text(stringResource(R.string.kl_remind_note), color = ClockColors.Muted, fontSize = 12.sp)
                }
            }
            Text(stringResource(R.string.kl_note), color = ClockColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp).heightIn(min = 0.dp).padding(bottom = 8.dp))
        }
    }
}

/** Android 13+: спросить разрешение на уведомления, если его ещё нет. */
private fun askNotifications(context: Context, ask: () -> Unit) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) ask()
}

/** Стороны света на розе: буквы языка телефона и под ними — на иврите. */
private fun cardinal(i: Int): String = when (Locale.getDefault().language) {
    "ru", "uk", "be" -> listOf("С", "В", "Ю", "З")[i]
    "de" -> listOf("N", "O", "S", "W")[i]
    "iw", "he" -> listOf("צפון", "מזרח", "דרום", "מערב")[i]
    else -> listOf("N", "E", "S", "W")[i]
}
private val HEB = listOf("צפון", "מזרח", "דרום", "מערב")

/**
 * Роза ветров (поворачивается: вверху — куда направлен телефон), серебряная стрелка на Луну с её фазой и дуга высоты справа:
 * от горизонта (вправо) до зенита (вверх).
 */
private fun DrawScope.drawCompass(heading: Float, moon: MoonPosition, heLabels: Boolean) {
    val c = Offset(size.width / 2, size.height / 2)
    val rr = min(size.width, size.height) / 2 * 0.72f
    fun at(angleDeg: Double, r: Float) = Offset(
        c.x + (r * sin(Math.toRadians(angleDeg))).toFloat(),
        c.y - (r * cos(Math.toRadians(angleDeg))).toFloat()
    )

    drawCircle(ClockColors.RimFill, rr, c)
    drawCircle(ClockColors.RimStroke, rr, c, style = Stroke(width = 1.5.dp.toPx()))

    // штрихи через 5°, каждые 30° — длиннее
    for (k in 0 until 72) {
        val a = k * 5.0 - heading
        val long = k % 6 == 0
        drawLine(
            if (long) ClockColors.Gold else ClockColors.Gold.copy(alpha = 0.4f),
            at(a, rr * (if (long) 0.86f else 0.91f)), at(a, rr * 0.97f),
            strokeWidth = (if (long) 2.2f else 1.2f).dp.toPx(), cap = StrokeCap.Round
        )
    }
    // стороны света
    val canvas = drawContext.canvas.nativeCanvas
    val p = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    for (i in 0 until 4) {
        val a = i * 90.0 - heading
        val pos = at(a, rr * 0.72f)
        p.color = (if (i == 0) ClockColors.Hand else ClockColors.Ink).toArgb()
        p.textSize = (if (heLabels) 22 else 17).sp.toPx()
        val fm = p.fontMetrics
        canvas.drawText(cardinal(i), pos.x, pos.y - (fm.ascent + fm.descent) / 2, p)
        if (heLabels) {
            p.color = ClockColors.GoldDeep.copy(alpha = 0.8f).toArgb()
            p.textSize = 12.sp.toPx()
            canvas.drawText(HEB[i], pos.x, pos.y + 18.sp.toPx(), p)
        }
    }
    // север — красный треугольник на ободе
    run {
        val tip = at(-heading.toDouble(), rr * 0.99f)
        val l = at(-heading - 4.0, rr * 0.88f)
        val r = at(-heading + 4.0, rr * 0.88f)
        drawPath(Path().apply { moveTo(tip.x, tip.y); lineTo(l.x, l.y); lineTo(r.x, r.y); close() }, ClockColors.Hand)
    }

    // куда направлен телефон — золотой треугольник над ободом
    run {
        val tip = Offset(c.x, c.y - rr * 1.02f)
        drawPath(Path().apply {
            moveTo(tip.x, tip.y); lineTo(tip.x - 9.dp.toPx(), tip.y - 14.dp.toPx()); lineTo(tip.x + 9.dp.toPx(), tip.y - 14.dp.toPx()); close()
        }, ClockColors.GoldDeep)
    }

    // лунная стрелка
    val a = moon.azimuth - heading
    val up = moon.aboveHorizon
    val col = if (up) Silver else SilverDim
    val tail = at(a + 180, rr * 0.18f)
    val head = at(a, rr * 0.62f)
    drawLine(col, tail, head, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
        pathEffect = if (up) null else PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
    drawCircle(col, 5.dp.toPx(), c)
    // Луна на конце стрелки — с фазой
    val mc = at(a, rr * 0.80f)
    val mr = rr * 0.085f
    drawCircle(col, mr, mc)
    val shadow = Path().apply { addOval(androidx.compose.ui.geometry.Rect(mc, mr)) }
    clipPath(shadow) {
        drawCircle(Color(0xFF0B1532).copy(alpha = 0.92f), mr, Offset(mc.x - 2 * mr * moon.illumination.toFloat(), mc.y))
    }

    // дуга высоты справа: от горизонта (вправо) до зенита (вверх)
    val ar = rr * 1.14f
    drawArc(
        ClockColors.Line, startAngle = -90f, sweepAngle = 90f, useCenter = false,
        topLeft = Offset(c.x - ar, c.y - ar), size = Size(ar * 2, ar * 2), style = Stroke(width = 2.dp.toPx())
    )
    p.textSize = 10.sp.toPx()
    p.color = ClockColors.Muted.toArgb()
    for (deg in listOf(0, 30, 60, 90)) {
        val q = at(90.0 - deg, ar)
        val q2 = at(90.0 - deg, ar + 6.dp.toPx())
        drawLine(ClockColors.Muted, q, q2, strokeWidth = 1.dp.toPx())
        val t = at(90.0 - deg, ar + 16.dp.toPx())
        canvas.drawText("$deg°", t.x, t.y + 4.sp.toPx(), p)
    }
    if (up) {
        val altC = moon.altitude.coerceIn(0.0, 90.0)
        drawArc(
            Silver, startAngle = 0f, sweepAngle = -altC.toFloat(), useCenter = false,
            topLeft = Offset(c.x - ar, c.y - ar), size = Size(ar * 2, ar * 2), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(Silver, 6.dp.toPx(), at(90.0 - altC, ar))
    }
}
