package app.mychitas.peleshaon.alarm

import app.mychitas.peleshaon.domain.ZmanimEngine
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class QuietCheckResult(
    val isQuietTime: Boolean,
    val reasonKey: String? = null
)

object ShabbatGuard {

    fun checkMoment(
        moment: ZonedDateTime,
        cityName: String,
        lat: Double,
        lon: Double,
        zoneId: ZoneId,
        isIsrael: Boolean,
        muteCholHamoed: Boolean,
        earlyShabbatMinutes: Long = 0L
    ): QuietCheckResult {
        val localMoment = moment.withZoneSameInstant(zoneId)
        val today = localMoment.toLocalDate()
        val yesterday = today.minusDays(1)

        val yesterdaySacredReason = getSacredDayReason(yesterday, isIsrael, muteCholHamoed)
        if (yesterdaySacredReason != null) {
            val endOfToday = getQuietEnd(today, cityName, lat, lon, zoneId)
            if (endOfToday == null) return QuietCheckResult(true, "unknown")
            if (localMoment.isBefore(endOfToday)) {
                return QuietCheckResult(true, yesterdaySacredReason)
            }
        }

        val todaySacredReason = getSacredDayReason(today, isIsrael, muteCholHamoed)
        if (todaySacredReason != null) {
            val startOfToday = getQuietStart(today, cityName, lat, lon, zoneId, earlyShabbatMinutes)
            if (startOfToday == null) return QuietCheckResult(true, "unknown")
            if (!localMoment.isBefore(startOfToday)) {
                return QuietCheckResult(true, todaySacredReason)
            }
        }

        return QuietCheckResult(false, null)
    }

    /** Next permitted day may start a minute later than the raw astronomical event. */
    fun nextQuietStart(
        moment: ZonedDateTime, cityName: String, lat: Double, lon: Double,
        zoneId: ZoneId, isIsrael: Boolean, muteCholHamoed: Boolean,
        earlyShabbatMinutes: Long = 0L
    ): ZonedDateTime? {
        val local = moment.withZoneSameInstant(zoneId)
        for (n in 0L..2L) {
            val date = local.toLocalDate().plusDays(n)
            if (getSacredDayReason(date, isIsrael, muteCholHamoed) == null) continue
            val start = getQuietStart(date, cityName, lat, lon, zoneId, earlyShabbatMinutes)
            if (start != null && start.isAfter(local)) return start
        }
        return null
    }

    private fun getSacredDayReason(
        erevGregorianDate: LocalDate,
        isIsrael: Boolean,
        muteCholHamoed: Boolean
    ): String? {
        val nextDay = erevGregorianDate.plusDays(1)
        val jc = JewishCalendar(nextDay).apply {
            inIsrael = isIsrael
        }

        if (jc.yomTovIndex == JewishCalendar.YOM_KIPPUR) return "yom_kippur"
        if (jc.isYomTovAssurBemelacha) return "yom_tov"
        if (nextDay.dayOfWeek == DayOfWeek.SATURDAY) return "shabbat"
        if (muteCholHamoed && jc.isCholHamoed) return "chol_hamoed"

        return null
    }

    // No invented 14:00/23:00 cutoffs. If astronomy is unavailable, suppress
    // alarm sounds conservatively on an affected holy day and report unknown.
    private fun getQuietStart(date: LocalDate, cityName: String, lat: Double, lon: Double, zoneId: ZoneId, earlyShabbatMinutes: Long): ZonedDateTime? {
        val noon = date.atTime(12, 0).atZone(zoneId)
        val candles = ZmanimEngine.calculateForMoment(noon, cityName, lat, lon, 0.0, zoneId, false).candleLighting
        // Only an explicitly selected earlier Friday acceptance moves Shabbat silence earlier.
        return candles?.minusMinutes(if (date.dayOfWeek == DayOfWeek.FRIDAY) earlyShabbatMinutes.coerceIn(0, 60) else 0L)
    }

    private fun getQuietEnd(date: LocalDate, cityName: String, lat: Double, lon: Double, zoneId: ZoneId): ZonedDateTime? {
        val noon = date.atTime(12, 0).atZone(zoneId)
        return ZmanimEngine.calculateForMoment(noon, cityName, lat, lon, 0.0, zoneId, false).tzeitShabbat
    }
}
