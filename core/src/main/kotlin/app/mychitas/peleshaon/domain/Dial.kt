package app.mychitas.peleshaon.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor
import kotlin.math.min

/**
 * Циферблат страницы «Часы» сайта Нифлаот (src/components/ClockFace.tsx `dial`), без рисования:
 * обод — сутки (24 часа: полдень вверху, полночь внизу), `turn` — доля оборота от полудня (0…1, по часовой).
 *
 * Обод — штрихи, как на часах, но штрих — четверть галахического часа: 48 от восхода до заката и 48 от заката
 * до восхода, поэтому у короткой половины суток штрихи тоньше. Прошедшие в текущей половине горят, штрих «сейчас»
 * выделен; от рассвета до восхода и от заката до выхода звёзд цвет плавно переходит между ночью и днём.
 */
enum class DashState { OFF, LIT, NOW }

data class Dash(
    val k: Int,
    /** доля оборота (0 — полдень вверху) */
    val turn: Double,
    /** сколько «дня» в цвете: 1 — золото, 0 — тхелет */
    val light: Double,
    /** толщина в единицах циферблата (радиус обода — 99) */
    val width: Double,
    /** начало галахического часа — штрих длиннее */
    val hour: Boolean,
    val state: DashState
)

data class Ring(
    val dashes: List<Dash>,
    /** полдень (середина между восходом и закатом) — там солнце, напротив — луна */
    val noon: Double,
    /** текущий галахический час (0…11) — днём или ночью */
    val hour: Int
)

object Dial {

    /** Двенадцать сочетаний четырёхбуквенного Имени — по часу дня и ночи («Пардес римоним» 13:3), через дефис. */
    val TZIRUFIM: List<String> = listOf(
        "יהוה", "יההו", "יוהה", "ההיו", "ההוי", "היוה", "והיה", "וההי", "ויהה", "הויה", "היהו", "הוהי"
    ).map { w -> w.toList().joinToString("-") }

    /** Доля оборота момента по часам города: полдень — 0 (вверху), полночь — 0,5 (внизу). */
    fun turnOf(at: ZonedDateTime, zone: ZoneId): Double {
        val t = at.withZoneSameInstant(zone)
        return ((t.hour + t.minute / 60.0 + t.second / 3600.0) / 24.0 + 0.5) % 1.0
    }

    private fun mod1(x: Double) = ((x % 1.0) + 1.0) % 1.0

    /**
     * Кольцо дня. `sunrise`, `sunset` — показанного дня (после заката — завтрашнего, как и все времена);
     * без восхода или заката (полярный день / ночь) — null, обода нет.
     */
    fun ring(
        now: ZonedDateTime,
        zone: ZoneId,
        sunrise: ZonedDateTime?,
        sunset: ZonedDateTime?,
        alot: ZonedDateTime?,
        tzeit: ZonedDateTime?
    ): Ring? {
        if (sunrise == null || sunset == null) return null
        val a = turnOf(sunrise, zone)
        val dayLen = mod1(turnOf(sunset, zone) - a)
        if (dayLen < 0.05 || dayLen > 0.95) return null
        val nightLen = 1 - dayLen

        fun off(at: ZonedDateTime?, from: Double, len: Double, fallback: Double): Double {
            if (at == null) return fallback
            val d = mod1(turnOf(at, zone) - from)
            return if (d > len) fallback else d / len
        }
        val dusk = off(tzeit, a + dayLen, nightLen, 0.08) // выход звёзд — часть ночи
        val dawn = off(alot, a + dayLen, nightLen, 0.9)   // рассвет

        val p = mod1(turnOf(now, zone) - a)
        val isDay = p < dayLen
        val cur = min(47, floor((if (isDay) p / dayLen else (p - dayLen) / nightLen) * 48).toInt())

        val dashes = (0 until 96).map { k ->
            val day = k < 48
            val i = k % 48
            val f = (i + 0.5) / 48
            val turn = mod1(a + if (day) dayLen * f else dayLen + nightLen * f)
            val light = when {
                day -> 1.0
                f < dusk -> 1 - f / dusk
                f > dawn -> (f - dawn) / (1 - dawn)
                else -> 0.0
            }
            val slot = (if (day) dayLen else nightLen) / 48
            val state = when {
                day != isDay -> DashState.OFF
                i < cur -> DashState.LIT
                i == cur -> DashState.NOW
                else -> DashState.OFF
            }
            Dash(k, turn, light, slot * 2 * Math.PI * 92 * 0.5, i % 4 == 0, state)
        }
        return Ring(dashes, mod1(a + dayLen / 2), cur / 4)
    }
}
