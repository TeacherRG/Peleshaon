package app.mychitas.peleshaon.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import app.mychitas.peleshaon.R
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
        DotPlace(e, t, polar(t, 96.0), 11f)
    }
    // куда достаёт нажатие: не дальше половины пути до соседней точки (закат и звёзды не сливаются)
    for (d in dots) for (o in dots) if (o !== d) {
        d.hit = max(4.5f, min(d.hit, hypot(d.pos.x - o.pos.x, d.pos.y - o.pos.y) / 2))
    }
    return dots
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Оформление циферблата (владелец, 24.11.2026: «используй такие стили, убери неудобные и некрасивые»):
 * золотой обод, звезда Давида вверху, дата и еврейская дата, крупное время, восход и закат, малый циферблат.
 * Цвета — ARGB. `image` — картинка неба вместо цвета (сейчас — «Закат»).
 */
enum class FaceSkin(
    val key: String,
    val title: Int,
    val bg0: Long, val bg1: Long,
    val tick: Long, val text: Long, val text2: Long, val accent: Long,
    val time0: Long, val time1: Long,
    val sub: Long,
    /** тёмный ли фон: тень у текста тёмная, иначе её нет */
    val dark: Boolean,
    val image: Int? = null,
    /** тонкий обод и только часовые деления */
    val minimal: Boolean = false,
    /** часы — буквами иврита (א…יב) */
    val letters: Boolean = false
) {
    CLASSIC("classic", R.string.skin_classic, 0xFF1D2C4F, 0xFF0A1226, 0xFFD9B66A, 0xFFEFE6CF, 0xFFC9BF9F, 0xFFE8C46F,
        0xFFFFF1C4, 0xFFE2B85A, 0xFF0D1730, true),
    SUNSET("sunset", R.string.skin_sunset, 0xFF0B1532, 0xFF050B1E, 0xFFE2C07D, 0xFFF4EFE2, 0xFFCFD5E8, 0xFFE8C46F,
        0xFFFFF6DC, 0xFFF0CF86, 0xFF0B1532, true, image = R.drawable.clock_halachic_face),
    MINIMAL("minimal", R.string.skin_minimal, 0xFF132245, 0xFF132245, 0xFFC9A85C, 0xFFE9ECF5, 0xFFA8B1CC, 0xFFD9B66A,
        0xFFFFFFFF, 0xFFE6E9F2, 0xFF0E1A37, true, minimal = true),
    TRADITIONAL("traditional", R.string.skin_traditional, 0xFFF6E6BD, 0xFFD7B26A, 0xFF5A3E16, 0xFF3A2810, 0xFF5B4320, 0xFF7A4F12,
        0xFF4A3210, 0xFF2C1D08, 0xFFEAD39C, false, letters = true),
    LIGHT("light", R.string.skin_light, 0xFFFFFFFF, 0xFFEFE8D8, 0xFFB8913F, 0xFF1C2333, 0xFF4A5266, 0xFFA87A26,
        0xFF1C2333, 0xFF1C2333, 0xFFF8F2E4, false),
    DARK("dark", R.string.skin_dark, 0xFF222224, 0xFF060607, 0xFFCFAE68, 0xFFF2F2F2, 0xFFB9B9BD, 0xFFE0BD6C,
        0xFFFFF4D6, 0xFFE0BB66, 0xFF111113, true),
    /** днём — «Светлая», ночью — «Классика» (по теме приложения / телефона); цвета здесь — только для порядка */
    ADAPTIVE("adaptive", R.string.skin_adaptive, 0xFFFFFFFF, 0xFFEFE8D8, 0xFFB8913F, 0xFF1C2333, 0xFF4A5266, 0xFFA87A26,
        0xFF1C2333, 0xFF1C2333, 0xFFF8F2E4, false);

    /** «Адаптивная» — светлое или тёмное оформление по теме; остальные — сами по себе. */
    fun resolve(light: Boolean): FaceSkin = if (this == ADAPTIVE) (if (light) LIGHT else CLASSIC) else this

    companion object {
        fun of(key: String?): FaceSkin = values().firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Что написано на циферблате, кроме самого времени. Пустое поле — не показывать. */
data class FaceInfo(
    val dateLine: String? = null,
    val jewishLine: String? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    /** малый циферблат: время «04:17» (или «5-й час» в виджете) и подпись; null — малого циферблата нет */
    val subText: String? = null,
    val subLabel: String? = null,
    /** малый циферблат стрелками */
    val subAnalog: Boolean = false
)

/** Всё для циферблата из настроек: дата, восход и закат, малый циферблат — противоположное главному время. */
fun faceInfo(
    context: android.content.Context,
    city: app.mychitas.peleshaon.domain.CityOption,
    now: ZonedDateTime,
    day: app.mychitas.peleshaon.domain.DayZmanimResult,
    half: Half?,
    widget: Boolean = false
): FaceInfo {
    val p = app.mychitas.peleshaon.Prefs
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    val t = now.withZoneSameInstant(city.zone())
    val jd = app.mychitas.peleshaon.domain.ZmanimEngine.jewishDay(day.date, city.isIsrael)
    val showDate = p.showDate(context)
    val showSun = p.showSun(context)
    fun at(type: ZmanType) = day.time(type)?.roundedTime?.withZoneSameInstant(city.zone())?.format(HHMM)
    val mainHalachic = p.mainTimeType(context) == p.TIME_HALACHIC
    // в виджете главное время — всегда часы телефона (TextClock), а галахическое — номером часа: картинка обновляется
    // не каждую минуту, а галахическая минута не равна обычной
    val subHalachic = widget || !mainHalachic
    val sub = when {
        !p.subdialEnabled(context) -> null
        subHalachic && widget -> half?.let { context.getString(if (it.day) R.string.hour_day else R.string.hour_night, it.hour(t)) }
        subHalachic -> halachicText(half, t)
        else -> t.format(HHMM)
    }
    return FaceInfo(
        dateLine = if (showDate) t.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)) else null,
        jewishLine = if (showDate) "${jewishDayMonth(jd, locale)} ${jd.year}" else null,
        sunrise = if (showSun) at(ZmanType.SUNRISE) else null,
        sunset = if (showSun) at(ZmanType.SUNSET) else null,
        subText = sub,
        subLabel = if (sub == null || (widget && subHalachic)) null
            else context.getString(if (subHalachic) R.string.halachic_time_title else R.string.civil_time_title),
        subAnalog = !widget && p.secondaryFaceStyle(context) == p.FACE_ANALOG
    )
}

/** Галахическое время половины суток «чч:мм»: сколько галахических часов и минут прошло от восхода / заката. */
fun halachicText(half: Half?, now: ZonedDateTime): String = half?.let {
    val elapsed = (it.frac(now).coerceIn(0.0, 0.99999) * 12.0 * 60.0).toInt()
    String.format(Locale.ROOT, "%02d:%02d", elapsed / 60, elapsed % 60)
} ?: "--:--"

/** Compact drawn symbol for the two time systems; no text or emoji. */
@Composable
internal fun TimeSystemMark(halachic: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)
        val color = if (halachic) ClockColors.GoldDeep else ClockColors.TcheletDeep
        drawStar(c, side * 0.4f, color, side * 0.06f)
    }
}

private val RIM = listOf(Color(0xFFFFF0B8), Color(0xFFC8963C), Color(0xFFF7DC8E), Color(0xFF9A6B22), Color(0xFFF0CF7C))
private fun rimBrush(from: Offset, to: Offset) = Brush.linearGradient(RIM, start = from, end = to)
private val HEB_HOURS = listOf("יב", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט", "י", "יא")

/** Звезда Давида — два треугольника контуром. */
private fun DrawScope.drawStar(c: Offset, s: Float, color: Color, w: Float) {
    for (a0 in listOf(0.0, 60.0)) {
        val path = Path()
        for (i in 0 until 3) {
            val a = (a0 + i * 120) * PI / 180
            val x = c.x + s * sin(a).toFloat()
            val y = c.y - s * cos(a).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color, style = Stroke(w, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** Солнце на горизонте: полукруг, линия и лучи. */
private fun DrawScope.drawSun(c: Offset, color: Color, s: Float = 1f) {
    val w = 1.3f * s
    drawArc(color, 180f, 180f, false, topLeft = Offset(c.x - 6f * s, c.y - 6f * s), size = Size(12f * s, 12f * s), style = Stroke(w))
    drawLine(color, Offset(c.x - 10f * s, c.y), Offset(c.x + 10f * s, c.y), w, cap = StrokeCap.Round)
    for (a in listOf(-60.0, -30.0, 0.0, 30.0, 60.0)) {
        val r = a * PI / 180
        drawLine(color,
            Offset(c.x + 8.5f * s * sin(r).toFloat(), c.y - 8.5f * s * cos(r).toFloat()),
            Offset(c.x + 11f * s * sin(r).toFloat(), c.y - 11f * s * cos(r).toFloat()), w, cap = StrokeCap.Round)
    }
}

/** Текст в пикселях (единицы × k), по центру точки; тень на тёмном фоне, по желанию — золотой градиент сверху вниз. */
private class Texts(val canvas: android.graphics.Canvas, val k: Float, val shadow: Boolean) {
    fun draw(s: String, x: Float, y: Float, size: Float, color: Color, weight: Int = 700,
             gradient: Pair<Color, Color>? = null, serif: Boolean = false, spacing: Float = 0f) {
        canvas.save()
        canvas.scale(1f / k, 1f / k, 0f, 0f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = size * k
            textAlign = Paint.Align.CENTER
            typeface = when {
                serif -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
                weight >= 700 -> Typeface.DEFAULT_BOLD
                else -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            fontFeatureSettings = "tnum"
            letterSpacing = spacing
            if (shadow) setShadowLayer(1.6f * k, 0f, 0.6f * k, 0xB3000000.toInt())
        }
        val fm = p.fontMetrics
        val base = y * k - (fm.ascent + fm.descent) / 2
        if (gradient != null) {
            p.shader = android.graphics.LinearGradient(0f, base + fm.ascent * 0.8f, 0f, base,
                gradient.first.toArgb(), gradient.second.toArgb(), android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawText(s, x * k, base, p)
        canvas.restore()
    }
}

/**
 * Большой циферблат страницы «Часы»: золотой обод, звезда Давида, дата, крупное время (или стрелки), восход и закат,
 * малый циферблат; по желанию — основные времена дня точками на ободе (нажатие — `onPick`).
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
    /** не используется: номер часа — в малом циферблате */
    hourLabel: String? = null,
    timeText: String? = null,
    analog: Boolean = false,
    timeSystemHalachic: Boolean = false,
    skin: FaceSkin = FaceSkin.CLASSIC,
    info: FaceInfo? = null,
    onPick: ((String?, Float, Float) -> Unit)? = null
) {
    val dots = remember(events, half) { place(events, half) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val artwork = remember(skin) { skin.image?.let { BitmapFactory.decodeResource(context.resources, it).asImageBitmap() } }

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
                    val t = now.withZoneSameInstant(zone)
                    drawFace(skin, artwork, k, now, dots, active, info,
                        timeText = timeText ?: String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute),
                        analog = analog,
                        tzirufText = if (tziruf && ring != null && !analog) Dial.TZIRUFIM[ring.hour] else null)
                }
            }
        }
    }
}

private fun DrawScope.drawFace(
    skin: FaceSkin,
    artwork: ImageBitmap?,
    k: Float,
    now: ZonedDateTime,
    dots: List<DotPlace>,
    active: String?,
    info: FaceInfo?,
    /** null — время не рисуется (в виджете его показывает TextClock) */
    timeText: String?,
    analog: Boolean = false,
    tzirufText: String? = null
) {
    val c = Offset(100f, 100f)
    val tick = Color(skin.tick); val text = Color(skin.text); val text2 = Color(skin.text2); val accent = Color(skin.accent)
    val rimIn = if (skin.minimal) 96f else 93f
    val nc = drawContext.canvas.nativeCanvas

    // мягкое золотое свечение вокруг обода
    nc.drawCircle(100f, 100f, 98.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6B95C.toInt()
        setShadowLayer(4f, 0f, 0f, 0x8CF2C76A.toInt())
    })
    drawCircle(rimBrush(Offset(1f, 1f), Offset(199f, 199f)), radius = 99f, center = c)
    drawCircle(Brush.radialGradient(listOf(Color(skin.bg0), Color(skin.bg1)), center = Offset(100f, 84f), radius = 112f), radius = rimIn, center = c)
    if (artwork != null) {
        clipPath(Path().apply { addOval(Rect(c.x - rimIn, c.y - rimIn, c.x + rimIn, c.y + rimIn)) }) {
            val d = rimIn.toInt()
            drawImage(artwork, dstOffset = IntOffset(100 - d, 100 - d), dstSize = IntSize(2 * d, 2 * d))
        }
    }
    drawCircle(Color(0x802A1A05), radius = rimIn, center = c, style = Stroke(0.8f))

    // деления: 60 минут, 12 часов; у «Традиционного» часы — буквами
    val tx = Texts(nc, k, skin.dark)
    for (i in 0 until 60) {
        val hour = i % 5 == 0
        if (skin.minimal && !hour) continue
        if (skin.letters && hour) continue
        val turn = i / 60.0
        drawLine(
            tick.copy(alpha = if (hour) 1f else 0.7f),
            polar(turn, (rimIn - if (hour) 9f else 5f).toDouble()), polar(turn, (rimIn - 2f).toDouble()),
            strokeWidth = if (hour) 2.2f else 0.7f
        )
    }
    if (skin.letters) for (i in 1 until 12) {
        val p = polar(i / 12.0, (rimIn - 10f).toDouble())
        tx.draw(HEB_HOURS[i], p.x, p.y, 10f, tick, serif = true)
    }

    // звезда Давида и дата
    drawStar(Offset(100f, 25f), 7f, accent, 1.3f)
    info?.dateLine?.let { tx.draw(it, 100f, 43f, 8.6f, text2, weight = 500) }
    info?.jewishLine?.let { tx.draw(it, 100f, 54.5f, 9.4f, accent) }

    if (!analog && timeText != null) {
        tx.draw(timeText, 100f, 80f, 40f, text, gradient = Color(skin.time0) to Color(skin.time1), spacing = -0.02f)
    }
    tzirufText?.let { tx.draw(it, 100f, 106.5f, 10f, accent, serif = true) }

    // восход и закат
    info?.sunrise?.let { drawSun(Offset(52f, 108f), accent); tx.draw(it, 52f, 120f, 9f, text, weight = 600) }
    info?.sunset?.let { drawSun(Offset(148f, 108f), accent); tx.draw(it, 148f, 120f, 9f, text, weight = 600) }

    // стрелки главного времени — до малого циферблата: он лежит сверху, как на настоящих часах
    if (analog && timeText != null) drawHands(timeText, c, 38f, 58f, 4.2f, 2.4f, accent, Color(skin.sub))

    // малый циферблат
    info?.subText?.let { sub ->
        val sc = Offset(100f, 151f)
        drawCircle(Color(skin.sub).copy(alpha = if (artwork != null) 0.85f else 1f), radius = 29f, center = sc)
        drawCircle(rimBrush(Offset(71f, 122f), Offset(129f, 180f)), radius = 29f, center = sc, style = Stroke(2.2f))
        drawStar(Offset(100f, 133f), 4f, accent, 0.9f)
        if (info.subAnalog) {
            for (i in 0 until 12) drawLine(tick, polarAt(sc, i / 12.0, 23.0), polarAt(sc, i / 12.0, 26.0), 1f)
            drawHands(sub, sc, 11f, 18f, 2f, 1.2f, text, Color(skin.sub))
        } else {
            val label = info.subLabel
            if (label == null) tx.draw(sub, 100f, 152f, if (sub.length > 6) 8.5f else 15f, text)
            else {
                tx.draw(sub, 100f, 149f, 15f, text)
                val parts = label.split(' ', limit = 2)
                tx.draw(parts[0], 100f, 161.5f, 5.4f, text2, weight = 500)
                if (parts.size > 1) tx.draw(parts[1], 100f, 168f, 5.4f, text2, weight = 500)
            }
        }
    }

    // основные зманим — точками на ободе
    for (d in dots) {
        val past = !d.e.at.isAfter(now)
        val on = d.e.id == active
        if (on) drawCircle(d.e.color.copy(alpha = 0.9f), radius = 7f, center = d.pos, style = Stroke(1.4f))
        val r = if (on) 4.6f else 3.2f
        drawCircle(d.e.color.copy(alpha = if (past) 0.5f else 1f), radius = r, center = d.pos)
        drawCircle(Color(0xFF1A1204), radius = r, center = d.pos, style = Stroke(1f))
    }
}

private fun polarAt(c: Offset, turn: Double, r: Double): Offset =
    Offset((c.x + r * sin(turn * 2 * PI)).toFloat(), (c.y - r * cos(turn * 2 * PI)).toFloat())

/** Стрелки по «чч:мм» (обычное или галахическое время — оба по 12 часов на круг). */
private fun DrawScope.drawHands(hhmm: String, c: Offset, hourLen: Float, minLen: Float, hourW: Float, minW: Float, color: Color, pin: Color) {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return
    drawLine(color, c, polarAt(c, ((h % 12) + m / 60.0) / 12.0, hourLen.toDouble()), hourW, cap = StrokeCap.Round)
    drawLine(color, c, polarAt(c, m / 60.0, minLen.toDouble()), minW, cap = StrokeCap.Round)
    drawCircle(color, radius = hourW * 0.85f, center = c)
    drawCircle(pin, radius = hourW * 0.33f, center = c)
}

/**
 * Тот же циферблат картинкой — для круглого виджета (там нет Compose).
 * Время в центре не рисуется: его показывает TextClock поверх, он идёт сам.
 */
fun renderFace(
    context: android.content.Context,
    sizePx: Int,
    now: ZonedDateTime,
    zone: ZoneId,
    half: Half?,
    ring: Ring?,
    events: List<FaceEvent>,
    tziruf: Boolean,
    hourLabel: String? = null,
    light: Boolean = false,
    timeSystemHalachic: Boolean = false,
    skin: FaceSkin = FaceSkin.CLASSIC,
    info: FaceInfo? = null
): android.graphics.Bitmap = ClockTheme.render(false) {
    val bmp = ImageBitmap(sizePx, sizePx)
    val insetPx = sizePx * 0.03f
    val k = (sizePx - 2f * insetPx) / BOX
    val dots = place(events, half)
    val artwork = skin.image?.let { BitmapFactory.decodeResource(context.resources, it).asImageBitmap() }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp), Size(sizePx.toFloat(), sizePx.toFloat())) {
        translate(insetPx, insetPx) {
            scale(k, k, pivot = Offset.Zero) {
                translate(-BOX_MIN, -BOX_MIN) {
                    drawFace(skin, artwork, k, now, dots, null, info, timeText = null)
                }
            }
        }
    }
    bmp.asAndroidBitmap()
}

/**
 * Прямоугольный виджет (владелец, 24.11.2026): дата и еврейская дата сверху, место для времени (TextClock) посередине,
 * внизу — восход, галахический час, закат. Рисуется в пикселях `w × h`; единица — сотая высоты.
 */
fun renderCard(context: android.content.Context, w: Int, h: Int, skin: FaceSkin, info: FaceInfo): android.graphics.Bitmap {
    val bmp = ImageBitmap(w, h)
    val k = h / 100f
    val W = w / k
    val artwork = skin.image?.let { BitmapFactory.decodeResource(context.resources, it).asImageBitmap() }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
        scale(k, k, pivot = Offset.Zero) {
            val outer = RoundRect(1f, 1f, W - 1f, 99f, CornerRadius(16f))
            val inner = RoundRect(3.5f, 3.5f, W - 3.5f, 96.5f, CornerRadius(13.5f))
            drawPath(Path().apply { addRoundRect(outer) }, rimBrush(Offset(0f, 0f), Offset(W, 100f)))
            val innerPath = Path().apply { addRoundRect(inner) }
            drawPath(innerPath, Brush.radialGradient(listOf(Color(skin.bg0), Color(skin.bg1)), center = Offset(W / 2, 40f), radius = W * 0.7f))
            if (artwork != null) clipPath(innerPath) {
                // картинка — по ширине, горизонт — ниже середины
                val side = W.toInt()
                drawImage(artwork, dstOffset = IntOffset(0, (50 - side * 0.55f).toInt()), dstSize = IntSize(side, side))
                drawRect(Color(0x590B1532), size = Size(W, 100f))
            }
            val tx = Texts(drawContext.canvas.nativeCanvas, k, skin.dark)
            val text = Color(skin.text); val text2 = Color(skin.text2); val accent = Color(skin.accent)
            drawStar(Offset(W / 2, 11f), 3.6f, accent, 0.8f)
            info.dateLine?.let { tx.draw(it, W / 2, 21f, 7f, text2, weight = 500) }
            info.jewishLine?.let { tx.draw(it, W / 2, 30f, 7.6f, accent) }
            val sx = minOf(18f, W * 0.12f)
            info.sunrise?.let { drawSun(Offset(sx, 76f), accent, 0.9f); tx.draw(it, sx, 87f, 8f, text, weight = 600) }
            info.sunset?.let { drawSun(Offset(W - sx, 76f), accent, 0.9f); tx.draw(it, W - sx, 87f, 8f, text, weight = 600) }
            info.subText?.let { sub ->
                val pw = minOf(58f, W - 2 * sx - 24f)
                val pill = RoundRect(W / 2 - pw / 2, 70f, W / 2 + pw / 2, 92f, CornerRadius(8f))
                drawPath(Path().apply { addRoundRect(pill) }, Color(skin.sub).copy(alpha = 0.75f))
                drawPath(Path().apply { addRoundRect(pill) }, rimBrush(Offset(W / 2 - pw / 2, 70f), Offset(W / 2 + pw / 2, 92f)), style = Stroke(1f))
                drawStar(Offset(W / 2 - pw / 2 + 7f, 76f), 2.6f, accent, 0.7f)
                tx.draw(context.getString(R.string.halachic_time_title), W / 2 + 3f, 76f, 5.2f, text2, weight = 500)
                tx.draw(sub, W / 2, 85.5f, 8.5f, text)
            }
        }
    }
    return bmp.asAndroidBitmap()
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
