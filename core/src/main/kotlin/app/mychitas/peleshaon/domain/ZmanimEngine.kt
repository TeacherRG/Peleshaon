package app.mychitas.peleshaon.domain

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

enum class ZmanType(val isDeadline: Boolean, val defaultPinned: Boolean) {
    SUNRISE(isDeadline = false, defaultPinned = false),
    SOF_ZMAN_SHMA(isDeadline = true, defaultPinned = true),
    SOF_ZMAN_TFILA(isDeadline = true, defaultPinned = true),
    CHATZOT(isDeadline = false, defaultPinned = false),
    MINCHA_GEDOLA(isDeadline = false, defaultPinned = false),
    PLAG_HAMINCHA(isDeadline = false, defaultPinned = false),
    SUNSET(isDeadline = true, defaultPinned = true),
    TZAIT(isDeadline = false, defaultPinned = false)
}

data class ZmanItem(
    val type: ZmanType,
    val exactTime: ZonedDateTime,
    val roundedTime: ZonedDateTime
)

data class DayZmanimResult(
    val date: LocalDate,
    val isNextJewishDayAfterSunset: Boolean,
    val jewishDateHebrew: String,
    val jewishDateTranslit: String,
    val isPolarAnomaly: Boolean,
    val items: List<ZmanItem>,
    val candleLighting: ZonedDateTime?,
    val tzeitShabbat: ZonedDateTime?
)

object ZmanimEngine {

    fun calculateForMoment(
        now: ZonedDateTime,
        cityName: String,
        lat: Double,
        lon: Double,
        elevation: Double = 0.0,
        zoneId: ZoneId,
        isIsrael: Boolean
    ): DayZmanimResult {
        val today = now.withZoneSameInstant(zoneId).toLocalDate()
        val todayCal = buildCalendar(today, cityName, lat, lon, elevation, zoneId)
        val todaySunset = todayCal.sunset?.toZoned(zoneId)
        val todaySunrise = todayCal.sunrise?.toZoned(zoneId)

        if (todaySunrise == null || todaySunset == null) {
            return DayZmanimResult(
                date = today,
                isNextJewishDayAfterSunset = false,
                jewishDateHebrew = "",
                jewishDateTranslit = "",
                isPolarAnomaly = true,
                items = emptyList(),
                candleLighting = null,
                tzeitShabbat = null
            )
        }

        val isAfterSunset = !now.isBefore(todaySunset)
        val targetDate = if (isAfterSunset) today.plusDays(1) else today
        val targetCal = if (isAfterSunset) {
            buildCalendar(targetDate, cityName, lat, lon, elevation, zoneId)
        } else {
            todayCal
        }

        val items = extractZmanim(targetCal, zoneId)
        val (hebStr, translitStr) = getJewishDateStrings(targetDate, isIsrael)

        return DayZmanimResult(
            date = targetDate,
            isNextJewishDayAfterSunset = isAfterSunset,
            jewishDateHebrew = hebStr,
            jewishDateTranslit = translitStr,
            isPolarAnomaly = items.isEmpty(),
            items = items,
            candleLighting = todaySunset.minusMinutes(18),
            tzeitShabbat = todaySunset.plusMinutes(50)
        )
    }

    private fun buildCalendar(
        date: LocalDate,
        cityName: String,
        lat: Double,
        lon: Double,
        elevation: Double,
        zoneId: ZoneId
    ): ComplexZmanimCalendar {
        val tz = TimeZone.getTimeZone(zoneId)
        val geo = GeoLocation(cityName, lat, lon, elevation, tz)
        val cal = ComplexZmanimCalendar(geo)
        val javaCal = Calendar.getInstance(tz).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.calendar = javaCal
        return cal
    }

    private fun extractZmanim(cal: ComplexZmanimCalendar, zoneId: ZoneId): List<ZmanItem> {
        val rawMap = listOf(
            ZmanType.SUNRISE to cal.sunrise,
            ZmanType.SOF_ZMAN_SHMA to cal.sofZmanShmaBaalHatanya,
            ZmanType.SOF_ZMAN_TFILA to cal.sofZmanTfilaBaalHatanya,
            ZmanType.CHATZOT to cal.chatzos,
            ZmanType.MINCHA_GEDOLA to cal.minchaGedolaBaalHatanya,
            ZmanType.PLAG_HAMINCHA to cal.plagHaminchaBaalHatanya,
            ZmanType.SUNSET to cal.sunset,
            ZmanType.TZAIT to cal.tzaisBaalHatanya
        )

        if (rawMap.any { it.second == null }) return emptyList()

        return rawMap.map { (type, dateObj) ->
            val zdt = dateObj!!.toZoned(zoneId)
            val rounded = if (type.isDeadline) {
                zdt.truncatedTo(ChronoUnit.MINUTES)
            } else {
                val truncated = zdt.truncatedTo(ChronoUnit.MINUTES)
                if (zdt.isAfter(truncated)) truncated.plusMinutes(1) else truncated
            }
            ZmanItem(type = type, exactTime = zdt, roundedTime = rounded)
        }
    }

    private fun getJewishDateStrings(gregorianDate: LocalDate, isIsrael: Boolean): Pair<String, String> {
        val jc = JewishCalendar(gregorianDate)
        jc.inIsrael = isIsrael
        val hebFormatter = HebrewDateFormatter().apply { isHebrewFormat = true }
        val latFormatter = HebrewDateFormatter().apply { isHebrewFormat = false }
        return hebFormatter.format(jc) to latFormatter.format(jc)
    }

    private fun Date.toZoned(zoneId: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(this.time).atZone(zoneId)
}
