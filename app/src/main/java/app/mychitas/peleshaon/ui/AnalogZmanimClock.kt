package app.mychitas.peleshaon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.mychitas.peleshaon.domain.ZmanItem
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val NavyDark = Color(0xFF0D1B2A)
val TecheletBlue = Color(0xFF1E88E5)
val GoldAccent = Color(0xFFD4AF37)

@Composable
fun AnalogZmanimClock(
    now: ZonedDateTime,
    pinnedZmanim: List<ZmanItem>,
    zmanLabels: Map<ZmanItem, String>,
    isDarkTheme: Boolean,
    is24Hour: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.36f
        val rimColor = if (isDarkTheme) GoldAccent else NavyDark
        val faceColor = if (isDarkTheme) Color(0xFF13263A) else Color(0xFFFAF8F2)

        drawCircle(color = faceColor, radius = outerRadius, center = center)
        drawCircle(
            color = rimColor,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        val hebrewMarks = mapOf(0 to "י״ב", 3 to "ג", 6 to "ו", 9 to "ט")
        val textPaint = android.graphics.Paint().apply {
            color = if (isDarkTheme) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#0D1B2A")
            textSize = outerRadius * 0.16f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }

        for (i in 0 until 12) {
            val angleRad = (i * 30.0 - 90.0) * PI / 180.0
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()

            val label = hebrewMarks[i]
            if (label != null) {
                val textR = outerRadius * 0.80f
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    center.x + textR * cosA,
                    center.y + textR * sinA + (textPaint.textSize * 0.35f),
                    textPaint
                )
            } else {
                val startR = outerRadius * 0.88f
                val endR = outerRadius * 0.95f
                drawLine(
                    color = rimColor.copy(alpha = 0.6f),
                    start = Offset(center.x + startR * cosA, center.y + startR * sinA),
                    end = Offset(center.x + endR * cosA, center.y + endR * sinA),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        val calloutPaint = android.graphics.Paint().apply {
            textSize = outerRadius * 0.095f
            isAntiAlias = true
        }

        pinnedZmanim.forEach { item ->
            val zTime = item.roundedTime
            val isPassed = now.isAfter(item.exactTime)
            val alpha = if (isPassed) 0.35f else 1.0f

            val hour12 = (zTime.hour % 12) + zTime.minute / 60.0
            val angleDeg = hour12 * 30.0 - 90.0
            val angleRad = angleDeg * PI / 180.0
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()

            val dotCenter = Offset(center.x + outerRadius * cosA, center.y + outerRadius * sinA)
            val dotColor = if (item.type.isDeadline) GoldAccent else TecheletBlue

            val elbowRadius = outerRadius * 1.12f
            val elbow = Offset(center.x + elbowRadius * cosA, center.y + elbowRadius * sinA)
            val isRightSide = cosA >= 0
            val labelEnd = Offset(elbow.x + if (isRightSide) 18.dp.toPx() else -18.dp.toPx(), elbow.y)

            drawLine(color = dotColor.copy(alpha = alpha), start = dotCenter, end = elbow, strokeWidth = 1.5.dp.toPx())
            drawLine(color = dotColor.copy(alpha = alpha), start = elbow, end = labelEnd, strokeWidth = 1.5.dp.toPx())
            drawCircle(color = dotColor.copy(alpha = alpha), radius = 5.dp.toPx(), center = dotCenter)

            val shortName = zmanLabels[item] ?: ""
            val caption = "${zTime.format(timeFormatter)} $shortName"
            calloutPaint.color = if (isDarkTheme) {
                android.graphics.Color.argb((alpha * 255).toInt(), 255, 255, 255)
            } else {
                android.graphics.Color.argb((alpha * 255).toInt(), 13, 27, 42)
            }
            calloutPaint.textAlign = if (isRightSide) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT

            drawContext.canvas.nativeCanvas.drawText(
                caption,
                labelEnd.x + if (isRightSide) 4.dp.toPx() else -4.dp.toPx(),
                labelEnd.y + (calloutPaint.textSize * 0.35f),
                calloutPaint
            )
        }

        val sec = now.second
        val min = now.minute + sec / 60.0
        val hr = (now.hour % 12) + min / 60.0

        fun drawHand(angleDeg: Double, lengthRatio: Float, widthDp: Float, color: Color) {
            val rad = (angleDeg - 90.0) * PI / 180.0
            drawLine(
                color = color,
                start = center,
                end = Offset(
                    center.x + outerRadius * lengthRatio * cos(rad).toFloat(),
                    center.y + outerRadius * lengthRatio * sin(rad).toFloat()
                ),
                strokeWidth = widthDp.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        val handMainColor = if (isDarkTheme) Color.White else NavyDark
        drawHand(hr * 30.0, 0.52f, 4.5f, handMainColor)
        drawHand(min * 6.0, 0.76f, 3.0f, handMainColor)
        drawHand(sec * 6.0, 0.84f, 1.5f, TecheletBlue)
        drawCircle(color = GoldAccent, radius = 5.dp.toPx(), center = center)
    }
}
