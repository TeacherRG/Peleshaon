package app.mychitas.peleshaon.alarm

import app.mychitas.peleshaon.domain.ZmanimEngine
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
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
        muteCholHamoed: Boolean
    ): QuietCheckResult {
        val localMoment = moment.withZoneSameInstant(zoneId)
        val today = localMoment.toLocalDate()
        val yesterday = today.minusDays(1)

        val yesterdaySacredReason = getSacredDayReason(yesterday, isIsrael, muteCholHamoed)
        if (yesterdaySacredReason != null) {
            val endOfToday = getQuietEnd(today, cityName, lat, lon, zoneId)
            if (localMoment.isBefore(endOfToday)) {
                return QuietCheckResult(true, yesterdaySacredReason)
            }
        }

        val todaySacredReason = getSacredDayReason(today, isIsrael, muteCholHamoed)
        if (todaySacredReason != null) {
            val startOfToday = getQuietStart(today, cityName, lat, lon, zoneId)
            if (!localMoment.isBefore(startOfToday)) {
                return QuietCheckResult(true, todaySacredReason)
            }
        }

        return QuietCheckResult(false, null)
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

    private fun getQuietStart(date: LocalDate, cityName: String, lat: Double, lon: Double, zoneId: ZoneId): ZonedDateTime {
        val noon = ZonedDateTime.of(date, LocalTime.NOON, zoneId)
        val res = ZmanimEngine.calculateForMoment(noon, cityName, lat, lon, 0.0, zoneId, false)
        return res.candleLighting ?: ZonedDateTime.of(date, LocalTime.of(14, 0), zoneId)
    }

    private fun getQuietEnd(date: LocalDate, cityName: String, lat: Double, lon: Double, zoneId: ZoneId): ZonedDateTime {
        val noon = ZonedDateTime.of(date, LocalTime.NOON, zoneId)
        val res = ZmanimEngine.calculateForMoment(noon, cityName, lat, lon, 0.0, zoneId, false)
        return res.tzeitShabbat ?: ZonedDateTime.of(date, LocalTime.of(23, 0), zoneId)
    }
}
