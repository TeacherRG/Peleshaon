package app.mychitas.peleshaon.alarm

import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.ZmanimEngine
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Будильник — как на странице «Часы» сайта Нифлаот (src/core/clock.ts):
 * `byShma` — за `before` минут до конца времени Шма, иначе — в `hour:minute`;
 * `days` — дни недели (0 — воскресенье … 6 — суббота); `chm` — молчать и в Хол ґа-моэд.
 * В Шабат, Йом Тов и Йом Кипур молчит всегда — от зажигания свечей до исхода (ShabbatGuard).
 */
data class AlarmSpec(
    val on: Boolean,
    val byShma: Boolean,
    val hour: Int,
    val minute: Int,
    val before: Int,
    val days: Set<Int>,
    val chm: Boolean
) {
    companion object {
        /** «за N минут до конца Шма» — варианты, как на сайте */
        val BEFORE = listOf(15, 30, 45, 60, 90)
        val WEEKDAYS = setOf(0, 1, 2, 3, 4, 5)
    }
}

/** Звонок, который отменил день отдыха: когда был бы и почему молчит. */
data class SkippedRing(val at: ZonedDateTime, val reason: String)

object AlarmRules {

    /** 0 — воскресенье … 6 — суббота */
    fun dayIndex(date: LocalDate): Int = date.dayOfWeek.value % 7

    /** DST: in a spring-forward gap choose the first valid instant after the gap;
     *  in an autumn overlap use the earlier occurrence only (one ring). */
    fun resolveWallTime(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime {
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        return if (offsets.isEmpty()) {
            zone.rules.getTransition(local).dateTimeAfter.atZone(zone)
        } else {
            ZonedDateTime.ofLocal(local, zone, offsets.first())
        }
    }

    /** Когда звонил бы будильник в этот календарный день (без учёта дней отдыха). */
    fun ringOn(spec: AlarmSpec, date: LocalDate, city: CityOption): ZonedDateTime? =
        if (spec.byShma) ZmanimEngine.shmaOf(date, city)?.minusMinutes(spec.before.toLong())
        else resolveWallTime(date, LocalTime.of(spec.hour, spec.minute), city.zone())

    private fun quiet(at: ZonedDateTime, spec: AlarmSpec, city: CityOption): String? =
        ShabbatGuard.checkMoment(at, city.name, city.lat, city.lon, city.zone(), city.isIsrael, spec.chm)
            .takeIf { it.isQuietTime }?.reasonKey

    /** Следующий звонок после `now` в ближайшие три недели; нет — null. */
    fun nextRing(spec: AlarmSpec, now: ZonedDateTime, city: CityOption): ZonedDateTime? {
        if (!spec.on || spec.days.isEmpty()) return null
        val today = now.withZoneSameInstant(city.zone()).toLocalDate()
        for (n in 0..21L) {
            val date = today.plusDays(n)
            if (dayIndex(date) !in spec.days) continue
            val at = ringOn(spec, date, city) ?: continue
            if (!at.isAfter(now)) continue
            if (quiet(at, spec, city) != null) continue
            return at
        }
        return null
    }

    /** Звонки ближайших 8 дней, которые отменили праздники (без Шабата — о нём и так понятно). */
    fun skipped(spec: AlarmSpec, now: ZonedDateTime, city: CityOption, max: Int = 2): List<SkippedRing> {
        if (!spec.on) return emptyList()
        val today = now.withZoneSameInstant(city.zone()).toLocalDate()
        val out = mutableListOf<SkippedRing>()
        for (n in 0..8L) {
            if (out.size >= max) break
            val date = today.plusDays(n)
            if (dayIndex(date) !in spec.days) continue
            val at = ringOn(spec, date, city) ?: continue
            if (at.isBefore(now)) continue
            val why = quiet(at, spec, city) ?: continue
            if (why != "shabbat") out += SkippedRing(at, why)
        }
        return out
    }
}
