package app.mychitas.peleshaon.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import app.mychitas.peleshaon.domain.DashState
import app.mychitas.peleshaon.domain.Dial
import app.mychitas.peleshaon.domain.Half
import app.mychitas.peleshaon.domain.Ring
import app.mychitas.peleshaon.domain.ZmanType
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Цвета страницы «Часы» сайта Нифлаот: тёмные — ночное небо (.clk2), светлые — день (владелец, 18.11.2026: «светлую тему —
 * автоматически по времени дня»). Тема — `ClockTheme`; виджеты, ночной экран и заставка — всегда тёмные.
 */
object ClockColors {
    val Ink: Color get() = if (ClockTheme.light) Color(0xFF16233F) else Color(0xFFEEF0F7)
    val Ink2: Color get() = if (ClockTheme.light) Color(0xFF34415F) else Color(0xFFC9CFE3)
    val Muted: Color get() = if (ClockTheme.light) Color(0xFF5D6886) else Color(0xFF9AA4C4)
    val Surface: Color get() = if (ClockTheme.light) Color(0xC7FFFFFF) else Color(0x9E0C1430)
    val SheetBg: Color get() = if (ClockTheme.light) Color(0xFFF8F3E8) else Color(0xFF0B1532)
    val Line: Color get() = if (ClockTheme.light) Color(0x4D9C7A2E) else Color(0x38D6BE82)
    val Tchelet: Color get() = if (ClockTheme.light) Color(0xFF2C5BA0) else Color(0xFF8FB3E8)
    val TcheletDeep: Color get() = if (ClockTheme.light) Color(0xFF24508F) else Color(0xFF9CC0F0)
    val Gold: Color get() = if (ClockTheme.light) Color(0xFFB0832A) else Color(0xFFD9B66A)
    val GoldDeep: Color get() = if (ClockTheme.light) Color(0xFF8E6A1C) else Color(0xFFE2BF72)
    val Ok: Color get() = if (ClockTheme.light) Color(0xFF23804F) else Color(0xFF6BCB94)
    val Bad: Color get() = if (ClockTheme.light) Color(0xFFC2412F) else Color(0xFFF2867D)
    val Plum: Color get() = if (ClockTheme.light) Color(0xFF6A4AA8) else Color(0xFF9C7BD6)
    val Hand: Color get() = if (ClockTheme.light) Color(0xFFD23F2A) else Color(0xFFFF7A6E)
    val BigTime: Color get() = if (ClockTheme.light) Color(0xFF16233F) else Color(0xFFF4F1E8)
    val Seconds: Color get() = if (ClockTheme.light) Color(0xFF5D6886) else Color(0xFFB9C2DE)
    val TzirufC: Color get() = if (ClockTheme.light) Color(0xFF9A6E18) else Color(0xFFE8C97E)
    val RimFill: Color get() = if (ClockTheme.light) Color(0xA6FFFFFF) else Color(0x8C080E24)
    val RimStroke: Color get() = if (ClockTheme.light) Color(0x809C7A2E) else Color(0x59D6BE82)
    val DotStroke: Color get() = if (ClockTheme.light) Color(0xFFFFFFFF) else Color(0xFF0B1532)
    val WidgetFace: Color get() = if (ClockTheme.light) Color(0xDCF8FAFC) else Color(0xD612151C)
    val WidgetFaceStroke: Color get() = if (ClockTheme.light) Color(0x1F0F172A) else Color(0x24FFFFFF)
    val WidgetTick: Color get() = if (ClockTheme.light) Color(0xFF0F172A) else Color.White
    val WidgetDotStroke = Color(0xFF12151C)
    val WidgetNow = Color(0xFFF0715E)
}

/** Цвет точки времени дня — как `COLOR` на сайте. */
fun zmanColor(type: ZmanType): Color = when (type) {
    ZmanType.ALOT -> ClockColors.TcheletDeep
    ZmanType.SUNRISE -> ClockColors.Gold
    ZmanType.SOF_ZMAN_SHMA -> ClockColors.Tchelet
    ZmanType.SOF_ZMAN_TFILA -> ClockColors.Ok
    ZmanType.CHATZOT -> ClockColors.Muted
    ZmanType.MINCHA_GEDOLA -> ClockColors.Plum
    ZmanType.PLAG_HAMINCHA -> ClockColors.GoldDeep
    ZmanType.SUNSET -> ClockColors.Bad
    ZmanType.TZAIT -> ClockColors.Ink2
}

/** Время дня на циферблате: ZmanType.name, или CANDLES / HAVDALAH. */
data class FaceEvent(
    val id: String,
    val at: ZonedDateTime,
    val color: Color,
    /** время подписано у точки (выбор пользователя) */
    val label: Boolean
)

const val CANDLES = "CANDLES"
const val HAVDALAH = "HAVDALAH"

/** Поле SVG на сайте: -4…204 по обеим осям. */
private const val BOX_MIN = -4f
private const val BOX = 208f

private fun polar(turn: Double, r: Double): Offset =
    Offset((100 + r * sin(turn * 2 * PI)).toFloat(), (100 - r * cos(turn * 2 * PI)).toFloat())

private data class DotPlace(val e: FaceEvent, val turn: Double, val pos: Offset, var hit: Float)

private fun place(events: List<FaceEvent>, half: Half?): List<DotPlace> {
    val dots = events.map { e ->
        val t = if (half != null) Dial.turnOf(e.at, half) else 0.0
        DotPlace(e, t, polar(t, 91.5), 11f)
    }
    // куда достаёт нажатие: не дальше половины пути до соседней точки (закат и звёзды не сливаются)
    for (d in dots) for (o in dots) if (o !== d) {
        d.hit = max(4.5f, min(d.hit, hypot(d.pos.x - o.pos.x, d.pos.y - o.pos.y) / 2))
    }
    return dots
}

private val MOON = PathParser().parsePathString("M2 -7 A7 7 0 1 0 7 2 A5.5 5.5 0 0 1 2 -7Z").toPath()
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Большой циферблат страницы «Часы»: кольцо суток из штрихов, солнце и луна, все времена дня — точками на ободе
 * (нажатие — `onPick`), выбранные подписаны временем; внутри — время города, секунды и, по желанию, сочетание Имени часа.
 * `onPick(id, fx, fy)` — точка и её место в долях стороны квадрата (0…1); нажатие мимо точек — `onPick(null, …)`.
 */
@Composable
fun BigFace(
    now: ZonedDateTime,
    zone: ZoneId,
    half: Half?,
    ring: Ring?,
    events: List<FaceEvent>,
    active: String?,
    tziruf: Boolean,
    modifier: Modifier = Modifier,
    /** «4-й час дня» — под временем */
    hourLabel: String? = null,
    onPick: ((String?, Float, Float) -> Unit)? = null,
    analogStyle: Boolean = true
) {
    val dots = remember(events, half) { place(events, half) }

    val tap = if (onPick != null) Modifier.pointerInput(dots) {
        detectTapGestures { o ->
            val side = min(size.width, size.height).toFloat()
            val k = side / BOX
            val ox = (size.width - side) / 2f
            val oy = (size.height - side) / 2f
            val ux = (o.x - ox) / k + BOX_MIN
            val uy = (o.y - oy) / k + BOX_MIN
            val hit = dots.filter { hypot(it.pos.x - ux, it.pos.y - uy) <= it.hit }
                .minByOrNull { hypot(it.pos.x - ux, it.pos.y - uy) }
            if (hit != null) onPick(hit.e.id, (hit.pos.x - BOX_MIN) / BOX, (hit.pos.y - BOX_MIN) / BOX)
            else onPick(null, 0f, 0f)
        }
    } else Modifier

    Canvas(modifier = modifier.then(tap)) {
        val side = min(size.width, size.height)
        val k = side / BOX
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(k, k, pivot = Offset.Zero) {
                translate(-BOX_MIN, -BOX_MIN) {
                    drawFace(now, zone, ring, dots, labelsOf(dots, now), active, tziruf, k, hourLabel = hourLabel, analogStyle = analogStyle)
                }
            }
        }
    }
}

private fun widgetEventColor(id: String): Color = when (id) {
    CANDLES, ZmanType.SUNSET.name -> Color(0xFFF0715E)
    HAVDALAH, ZmanType.TZAIT.name, ZmanType.ALOT.name, ZmanType.SOF_ZMAN_SHMA.name -> Color(0xFF93C5FD)
    else -> Color(0xFFE2C07D)
}

private fun DrawScope.drawFace(
    now: ZonedDateTime,
    zone: ZoneId,
    ring: Ring?,
    dots: List<DotPlace>,
    labelled: List<Pair<DotPlace, Double>>,
    active: String?,
    tziruf: Boolean,
    k: Float,
    showTime: Boolean = true,
    hourLabel: String? = null,
    widgetStyle: Boolean = false,
    analogStyle: Boolean = true
) {
    val c = Offset(100f, 100f)
    val rimFill = if (widgetStyle) ClockColors.WidgetFace else ClockColors.RimFill
    val rimStroke = if (widgetStyle) ClockColors.WidgetFaceStroke else ClockColors.RimStroke
    if (widgetStyle) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            setShadowLayer(32f / k, 0f, 8f / k, 0x3D000000)
        }
        drawContext.canvas.nativeCanvas.drawCircle(100f, 100f, 99f, shadow)
    }
    drawCircle(rimFill, radius = 99f, center = c)
    drawCircle(rimStroke, radius = 99f, center = c, style = Stroke(if (widgetStyle) 1f / k else 0.5f))

    if (ring != null) {
        // 48 штрихов половины суток: прошедшие горят, будущие бледные, «сейчас» — красный (владелец, 17.11.2026)
        val slot = ring.dashes.first().width
        for (d in ring.dashes) {
            val from = polar(d.turn, if (d.hour) 82.0 else 86.0)
            val to = polar(d.turn, if (d.state == DashState.NOW) 99.0 else 96.0)
            val color = when {
                d.state == DashState.NOW && widgetStyle -> ClockColors.WidgetNow
                d.state == DashState.NOW -> ClockColors.Hand
                widgetStyle -> ClockColors.WidgetTick.copy(alpha = 0.22f)
                else -> lerp(ClockColors.Tchelet, ClockColors.Gold, d.light.toFloat())
            }
            drawLine(
                color = if (widgetStyle || d.state == DashState.NOW) color else color.copy(alpha = if (d.state == DashState.OFF) 0.16f else 1f),
                start = from,
                end = to,
                strokeWidth = (slot * when (d.state) { DashState.NOW -> 1.4; DashState.LIT -> 1.0; DashState.OFF -> 0.7 }).toFloat(),
                cap = StrokeCap.Butt
            )
        }
        // короткая стрелка «сейчас» — у самого обода
        drawLine(
            if (widgetStyle) ClockColors.WidgetNow else ClockColors.Hand,
            polar(ring.progress, 72.0), polar(ring.progress, 85.0),
            strokeWidth = 2.6f, cap = StrokeCap.Round
        )
        // внутри вверху — солнце днём, луна ночью
        // в виджете время — поверх картинки и крупнее: солнце / луна выше и чуть меньше, под подписью у верхней точки
        translate(100f, if (showTime) 56f else if (widgetStyle) 68f else 47f) {
            val sc = if (showTime) 1.15f else 0.95f
            scale(sc, sc, pivot = Offset.Zero) {
                if (ring.day) {
                    drawCircle(ClockColors.Gold, radius = 4.5f, center = Offset.Zero)
                    for (i in 0 until 8) rotate(i * 45f, pivot = Offset.Zero) {
                        drawLine(ClockColors.Gold, Offset(0f, -7f), Offset(0f, -9.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                    }
                } else {
                    drawPath(MOON, if (widgetStyle) Color(0xFFF8FAFC) else ClockColors.Tchelet)
                }
            }
        }
    }

    val canvas = drawContext.canvas.nativeCanvas
    // Текст рисуется в пикселях: единицы циферблата × k (иначе мелкий шрифт размыт)
    fun text(s: String, x: Float, y: Float, sizeUnits: Float, color: Color, bold: Boolean = true) {
        canvas.save()
        canvas.scale(1f / k, 1f / k, 0f, 0f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = sizeUnits * k
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            fontFeatureSettings = "tnum"
        }
        val fm = p.fontMetrics
        canvas.drawText(s, x * k, y * k - (fm.ascent + fm.descent) / 2, p)
        canvas.restore()
    }

    for ((d, turn) in labelled) {
        val pos = polar(turn, 68.0)
        text(
            d.e.at.withZoneSameInstant(zone).format(HHMM), pos.x, pos.y, 8.4f,
            if (widgetStyle) Color(0x99A0AEC0) else d.e.color
        )
    }

    for (d in dots) {
        val past = !d.e.at.isAfter(now)
        val on = d.e.id == active
        val eventColor = if (widgetStyle) widgetEventColor(d.e.id) else d.e.color
        if (on) drawCircle(eventColor.copy(alpha = 0.8f), radius = 8.5f, center = d.pos, style = Stroke(1.4f))
        val r = if (on) 6.2f else if (d.e.label) 5f else 3.6f
        drawCircle(eventColor.copy(alpha = if (past) 0.45f else 1f), radius = r, center = d.pos)
        val dotStroke = if (widgetStyle) ClockColors.WidgetDotStroke else ClockColors.DotStroke
        drawCircle(dotStroke, radius = r, center = d.pos, style = Stroke(if (widgetStyle) 1f / k else 1.8f))
    }

    if (tziruf && ring != null && !widgetStyle) {
        text(Dial.TZIRUFIM[ring.hour], 100f, 154f, 22f, ClockColors.TzirufC)
    }
    if (showTime) {
        val t = now.withZoneSameInstant(zone)
        if (analogStyle) {
            val hourAngle = ((t.hour % 12) + t.minute / 60.0) * 30.0
            val minuteAngle = (t.minute + t.second / 60.0) * 6.0
            val secondAngle = t.second * 6.0
            drawLine(ClockColors.BigTime, c, polar(hourAngle / 360.0, 25.0), strokeWidth = 3.6f, cap = StrokeCap.Round)
            drawLine(ClockColors.BigTime, c, polar(minuteAngle / 360.0, 39.0), strokeWidth = 2.2f, cap = StrokeCap.Round)
            drawLine(ClockColors.Hand, c, polar(secondAngle / 360.0, 47.0), strokeWidth = 1.1f, cap = StrokeCap.Round)
            drawCircle(ClockColors.Gold, radius = 3.2f, center = c)
        } else {
            text(String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute), 100f, 91f, 34f, ClockColors.BigTime)
            text(String.format(Locale.ROOT, "%02d", t.second), 100f, 114f, 14f, ClockColors.Seconds)
        }
    }
    if (hourLabel != null) text(hourLabel, 100f, if (showTime) 131f else 124f, 9f, ClockColors.Muted)
}

/**
 * Подписанные времена — только ещё не наступившие (владелец, 18.11.2026: «цифры прошедших зманим не нужны, нужны следующих»):
 * выбранные на циферблате и всегда ближайшее; раздвинуты по ободу, чтобы не налезали.
 */
private fun labelsOf(dots: List<DotPlace>, now: ZonedDateTime): List<Pair<DotPlace, Double>> {
    val next = dots.filter { it.e.at.isAfter(now) }.minByOrNull { it.e.at }
    val l = dots.filter { it.e.at.isAfter(now) && (it.e.label || it === next) }.sortedBy { it.turn }
    val turns = l.map { it.turn }.toMutableList()
    for (i in 1 until turns.size) turns[i] = max(turns[i], turns[i - 1] + 0.045)
    return l.zip(turns)
}

/**
 * Тот же циферблат картинкой — для виджета на рабочем столе (там нет Compose).
 * Время в центре не рисуется: его показывает TextClock поверх, он идёт сам.
 */
fun renderFace(
    sizePx: Int,
    now: ZonedDateTime,
    zone: ZoneId,
    half: Half?,
    ring: Ring?,
    events: List<FaceEvent>,
    tziruf: Boolean,
    hourLabel: String? = null,
    light: Boolean = false
): android.graphics.Bitmap = ClockTheme.render(light) {
    val bmp = ImageBitmap(sizePx, sizePx)
    val insetPx = 34f
    val k = (sizePx - 2f * insetPx) / BOX
    val dots = place(events, half)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp), Size(sizePx.toFloat(), sizePx.toFloat())) {
        translate(insetPx, insetPx) {
            scale(k, k, pivot = Offset.Zero) {
                translate(-BOX_MIN, -BOX_MIN) {
                    drawFace(
                        now, zone, ring, dots, labelsOf(dots, now), null, tziruf, k,
                        showTime = false, hourLabel = hourLabel, widgetStyle = true
                    )
                }
            }
        }
    }
    bmp.asAndroidBitmap()
}

/**
 * Точки циферблата: времена дня, что попадают в эту половину суток (выбранные — с подписью); свечи и исход Шабата — если они в ней.
 */
fun faceEvents(
    half: Half?,
    items: List<app.mychitas.peleshaon.domain.ZmanItem>,
    sh: app.mychitas.peleshaon.domain.ShabbatTimes,
    marks: Set<ZmanType>
): List<FaceEvent> {
    if (half == null) return emptyList()
    val list = items.map { FaceEvent(it.type.name, it.roundedTime, zmanColor(it.type), it.type in marks) }.toMutableList()
    for ((id, at) in listOf(CANDLES to sh.candles, HAVDALAH to sh.havdalah)) {
        if (at != null && at.isAfter(half.start) && !at.isAfter(half.end)) list += FaceEvent(id, at, ClockColors.GoldDeep, true)
    }
    return list
}
