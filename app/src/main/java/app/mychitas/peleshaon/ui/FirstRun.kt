package app.mychitas.peleshaon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.alarm.AlarmScheduler
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.cityMatchForZone
import app.mychitas.peleshaon.live.Live
import app.mychitas.peleshaon.moon.MoonReminder
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Первый запуск (владелец, 23.11.2026):
 *  1. обязательное окно «Где вы находитесь?» — без города дальше не пройти;
 *  2. всплывающие подсказки поверх главного экрана — что где находится, с кнопкой «Пропустить».
 */

// ---------------------------------------------------------------- 1. обязательный выбор города

/** Полноэкранный выбор города. Кнопки «Пропустить» нет: без города нельзя считать времена. */
@Composable
fun CityGate(onChosen: () -> Unit) {
    val context = LocalContext.current
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    // подсказка по часовому поясу телефона — только предложение, выбрать всё равно нужно явно
    val guess = remember { cityMatchForZone() }
    val pick: (CityOption?) -> Unit = { chosen ->
        if (chosen != null) {
            Prefs.setCity(context, chosen)
            ClockTheme.refresh(context)
            AlarmScheduler.rescheduleAllFromPrefs(context)
            MoonReminder.schedule(context)
            app.mychitas.peleshaon.reminder.ReminderNotifier.schedule(context)
            Live.updateAsync(context)
            onChosen()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF060B1E))) {
        ClockBackground()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("📍", fontSize = 40.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(stringResource(R.string.gate_t), fontWeight = FontWeight.Bold, fontSize = 24.sp,
                color = ClockColors.Ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.gate_b), fontSize = 14.sp, lineHeight = 20.sp,
                color = ClockColors.Ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (guess != null) {
                Button(
                    onClick = { pick(guess) },
                    colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.gate_guess, guess.title(locale)),
                        color = ClockColors.SheetBg, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Text(stringResource(R.string.gate_or), fontSize = 13.sp, color = ClockColors.Muted,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ClockColors.SheetBg,
                border = BorderStroke(1.dp, ClockColors.Line),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Box(Modifier.padding(top = 12.dp)) {
                    CitySheet(Prefs.city(context), auto = true, title = false, showCurrent = false, onPick = pick)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 2. подсказки на главном экране

/** Одна подсказка: какой элемент подсветить и что о нём сказать. */
data class TourStep(val key: String, val title: Int, val text: Int, val circle: Boolean = false)

object TourKeys {
    const val CITY = "city"
    const val DATE = "date"
    const val MENU = "menu"
    const val DIAL = "dial"
    const val SHABBAT = "shabbat"
    const val ALARM = "alarm"
    const val STUDY = "study"
    const val TIMES = "times"
}

val MAIN_TOUR = listOf(
    TourStep(TourKeys.CITY, R.string.tour_city_t, R.string.tour_city),
    TourStep(TourKeys.DATE, R.string.tour_date_t, R.string.tour_date),
    TourStep(TourKeys.MENU, R.string.tour_menu_t, R.string.tour_menu),
    TourStep(TourKeys.DIAL, R.string.tour_dial_t, R.string.tour_dial, circle = true),
    TourStep(TourKeys.SHABBAT, R.string.tour_shabbat_t, R.string.tour_shabbat),
    TourStep(TourKeys.ALARM, R.string.tour_alarm_t, R.string.tour_alarm),
    TourStep(TourKeys.STUDY, R.string.tour_study_t, R.string.tour_study),
    TourStep(TourKeys.TIMES, R.string.tour_times_t, R.string.tour_times),
)

/** Состояние подсказок: шаг и где на экране лежат подсвечиваемые элементы. */
@Stable
class TourState(val steps: List<TourStep>, private val onFinish: () -> Unit) {
    var index by mutableIntStateOf(0)
    val bounds = mutableStateMapOf<String, Rect>()
    val step: TourStep? get() = steps.getOrNull(index)
    fun next() { if (index < steps.size - 1) index++ else onFinish() }
    fun back() { if (index > 0) index-- }
    fun finish() = onFinish()
}

/** Отметить элемент экрана, к которому относится подсказка. */
fun Modifier.tourTarget(tour: TourState?, key: String): Modifier =
    if (tour == null) this else this.onGloballyPositioned { tour.bounds[key] = it.boundsInRoot() }

/** Затемнение с «окном» над элементом и карточка с объяснением. Нажатие мимо карточки — следующая подсказка. */
@Composable
fun TourOverlay(tour: TourState) {
    val step = tour.step ?: return
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = tour.bounds[step.key]?.translate(-origin)
    BackHandler { tour.finish() }

    val pulse by rememberInfiniteTransition(label = "tour").animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )
    val gold = ClockColors.Gold

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tour.next() }
    ) {
        val pad = with(density) { 6.dp.toPx() }
        val stroke = with(density) { 2.dp.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color(0xFF02050F).copy(alpha = 0.78f))
            if (target != null) {
                if (step.circle) {
                    val r = min(target.width, target.height) / 2f
                    drawCircle(Color.Black, radius = r, center = target.center, blendMode = BlendMode.Clear)
                    drawCircle(gold.copy(alpha = pulse), radius = r, center = target.center, style = Stroke(stroke))
                } else {
                    val hole = target.inflate(pad)
                    val corner = CornerRadius(min(hole.height / 2f, with(density) { 20.dp.toPx() }))
                    drawRoundRect(Color.Black, topLeft = hole.topLeft, size = hole.size, cornerRadius = corner, blendMode = BlendMode.Clear)
                    drawRoundRect(gold.copy(alpha = pulse), topLeft = hole.topLeft, size = hole.size, cornerRadius = corner, style = Stroke(stroke))
                }
            }
        }

        // карточка — над или под элементом, где больше места; не помещается — внизу экрана
        val h = constraints.maxHeight.toFloat()
        val gap = with(density) { 14.dp.toPx() }
        val need = with(density) { 210.dp.toPx() }
        val above = target?.let { if (step.circle) it.center.y - min(it.width, it.height) / 2f else it.top - pad } ?: 0f
        val below = target?.let { h - (if (step.circle) it.center.y + min(it.width, it.height) / 2f else it.bottom + pad) } ?: 0f
        val base = Modifier.padding(horizontal = 16.dp).widthIn(max = 480.dp).fillMaxWidth()
        val cardModifier = when {
            target == null -> base.align(Alignment.Center)
            below >= need && below >= above -> base.align(Alignment.TopCenter)
                .offset { IntOffset(0, (h - below + gap).roundToInt()) }
            above >= need -> base.align(Alignment.BottomCenter)
                .offset { IntOffset(0, -(h - above + gap).roundToInt()) }
            else -> Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp).then(base)
        }
        TourCard(tour, step, cardModifier)
    }
}

@Composable
private fun TourCard(tour: TourState, step: TourStep, modifier: Modifier) {
    val last = tour.index == tour.steps.size - 1
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ClockColors.SheetBg,
        border = BorderStroke(1.dp, ClockColors.Gold),
        shadowElevation = 8.dp,
        // нажатие по карточке не должно листать подсказки
        modifier = modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${tour.index + 1} / ${tour.steps.size}", fontSize = 12.sp, color = ClockColors.Muted)
                Spacer(Modifier.weight(1f))
                if (!last) TextButton(onClick = { tour.finish() }) {
                    Text(stringResource(R.string.ob_skip), color = ClockColors.Muted, fontSize = 14.sp)
                }
            }
            Text(stringResource(step.title), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = ClockColors.Ink, modifier = Modifier.padding(end = 8.dp))
            Spacer(Modifier.padding(top = 4.dp))
            Text(stringResource(step.text), fontSize = 14.sp, lineHeight = 20.sp,
                color = ClockColors.Ink2, modifier = Modifier.padding(end = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tour.index > 0) TextButton(onClick = { tour.back() }) {
                    Text(stringResource(R.string.ob_back), color = ClockColors.TcheletDeep)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { tour.next() },
                    colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold)
                ) {
                    Text(stringResource(if (last) R.string.tour_done else R.string.ob_next),
                        color = ClockColors.SheetBg, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
