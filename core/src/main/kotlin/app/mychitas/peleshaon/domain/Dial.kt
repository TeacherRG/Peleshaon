package app.mychitas.peleshaon.domain

import java.time.ZonedDateTime
import kotlin.math.floor
import kotlin.math.min

/**
 * Циферблат страницы «Часы» сайта Нифлаот (src/components/ClockFace.tsx `dial`), без рисования.
 *
 * Обод — половина суток сейчас: 12 галахических часов дня (восход → закат) или ночи (закат → восход), как часы: начало и
 * конец половины — вверху; `turn` — доля оборота по часовой (0…1). Штрих — четверть галахического часа (48 штрихов), первый
 * штрих часа длиннее. Прошедшие горят, будущие бледные, штрих «сейчас» и короткая стрелка — красные. День — золото, ночь —
 * тхелет; от заката до выхода звёзд и от рассвета до восхода цвет переходит плавно (владелец, 17.11.2026).
 */
enum class DashState { OFF, LIT, NOW }

data class Dash(
    val k: Int,
    /** доля оборота (0 — вверху) */
    val turn: Double,
    /** сколько «дня» в цвете: 1 — золото, 0 — тхелет */
    val light: Double,
    /** толщина в единицах циферблата (радиус обода — 99) */
    val width: Double,
    /** первый штрих галахического часа — длиннее */
    val hour: Boolean,
    val state: DashState
)

data class Ring(
    val dashes: List<Dash>,
    /** сколько прошло от половины: 0…1 — здесь стрелка */
    val progress: Double,
    /** день (солнце) или ночь (луна) */
    val day: Boolean,
    /** текущий галахический час (0…11) */
    val hour: Int
)

object Dial {

    /** Двенадцать сочетаний четырёхбуквенного Имени — по часу дня и ночи («Пардес римоним» 13:3), через дефис. */
    val TZIRUFIM: List<String> = listOf(
        "יהוה", "יההו", "יוהה", "ההיו", "ההוי", "היוה", "והיה", "וההי", "ויהה", "הויה", "היהו", "הוהי"
    ).map { w -> w.toList().joinToString("-") }

    /** Место момента на ободе: доля половины (вне её — по кругу). */
    fun turnOf(at: ZonedDateTime, half: Half): Double = ((half.frac(at) % 1.0) + 1.0) % 1.0

    /** Кольцо половины суток; нет половины (полярный день / ночь) — null. */
    fun ring(now: ZonedDateTime, half: Half?): Ring? {
        if (half == null) return null
        val p = half.frac(now).coerceIn(0.0, 0.99999)
        val cur = min(47, floor(p * 48).toInt())
        fun at(d: ZonedDateTime?, fallback: Double): Double {
            if (d == null) return fallback
            val f = half.frac(d)
            return if (f > 0 && f < 1) f else fallback
        }
        val dusk = at(half.tzeit, 0.08) // выход звёзд
        val dawn = at(half.alot, 0.9)   // рассвет
        val dashes = (0 until 48).map { k ->
            val f = (k + 0.5) / 48
            val light = when {
                half.day -> 1.0
                f < dusk -> 1 - f / dusk
                f > dawn -> (f - dawn) / (1 - dawn)
                else -> 0.0
            }
            val state = when {
                k < cur -> DashState.LIT
                k == cur -> DashState.NOW
                else -> DashState.OFF
            }
            Dash(k, f, light, 2 * Math.PI * 92 * 0.55 / 48, k % 4 == 0, state)
        }
        return Ring(dashes, p, half.day, floor(p * 12).toInt().coerceIn(0, 11))
    }
}
