package app.mychitas.peleshaon.domain

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Времена дня — как на странице «Часы» сайта Нифлаот (src/core/clock.ts): по Алтер Ребе (Бааль ґа-Тания).
 * `isDeadline` — «не позже» (конец Шма, молитвы, Минхи — закат): обратный отсчёт «осталось».
 * `roundDown` — округление вниз до минуты (никогда не позже настоящего); остальное — вверх.
 * `defaultPinned` — подписаны на циферблате, пока пользователь не выбрал сам.
 */
enum class ZmanType(val isDeadline: Boolean, val defaultPinned: Boolean, val roundDown: Boolean) {
    ALOT(isDeadline = false, defaultPinned = false, roundDown = false),
    SUNRISE(isDeadline = false, defaultPinned = false, roundDown = true),
    SOF_ZMAN_SHMA(isDeadline = true, defaultPinned = true, roundDown = true),
    SOF_ZMAN_TFILA(isDeadline = true, defaultPinned = true, roundDown = true),
    CHATZOT(isDeadline = false, defaultPinned = false, roundDown = false),
    MINCHA_GEDOLA(isDeadline = false, defaultPinned = false, roundDown = false),
    PLAG_HAMINCHA(isDeadline = false, defaultPinned = false, roundDown = false),
    SUNSET(isDeadline = true, defaultPinned = true, roundDown = true),
    TZAIT(isDeadline = false, defaultPinned = false, roundDown = false)
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
) {
    fun time(type: ZmanType): ZmanItem? = items.find { it.type == type }
}

/** Шабат этой недели: зажигание свечей (пятница, закат − 18 мин) и исход (суббота, закат + 50 мин). */
data class ShabbatTimes(
    val friday: LocalDate,
    val candles: ZonedDateTime?,
    val havdalah: ZonedDateTime?
) {
    val saturday: LocalDate get() = friday.plusDays(1)
}

object ZmanimEngine {

    const val CANDLE_MIN = 18L
    const val END_MIN = 50L

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
            val (hebStr, translitStr) = getJewishDateStrings(today, isIsrael)
            return DayZmanimResult(
                date = today,
                isNextJewishDayAfterSunset = false,
                jewishDateHebrew = hebStr,
                jewishDateTranslit = translitStr,
                isPolarAnomaly = true,
                items = emptyList(),
                candleLighting = null,
                tzeitShabbat = null
            )
        }

        // После заката — уже времена завтрашнего дня и завтрашняя еврейская дата
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
            candleLighting = todaySunset.minusMinutes(CANDLE_MIN),
            tzeitShabbat = todaySunset.plusMinutes(END_MIN)
        )
    }

    /** Закат календарного дня в городе (точно, без округления); нет заката — null. */
    fun sunsetOf(date: LocalDate, city: CityOption): ZonedDateTime? =
        buildCalendar(date, city.name, city.lat, city.lon, 0.0, city.zone()).sunset?.toZoned(city.zone())

    /** Конец времени Шма календарного дня (округлён вниз). */
    fun shmaOf(date: LocalDate, city: CityOption): ZonedDateTime? =
        buildCalendar(date, city.name, city.lat, city.lon, 0.0, city.zone()).sofZmanShmaBaalHatanya
            ?.toZoned(city.zone())?.truncatedTo(ChronoUnit.MINUTES)

    /**
     * Шабат этой недели: с воскресенья по пятницу — ближайший; в субботу — сегодняшний, пока он не вышел;
     * после исхода — следующий. Как `shabbatOf` на странице «Часы».
     */
    fun shabbatOf(now: ZonedDateTime, city: CityOption): ShabbatTimes {
        val zone = city.zone()
        val today = now.withZoneSameInstant(zone).toLocalDate()
        val back = if (today.dayOfWeek == DayOfWeek.SATURDAY) -1L
        else ((DayOfWeek.FRIDAY.value - today.dayOfWeek.value + 7) % 7).toLong()
        fun span(fri: LocalDate) = ShabbatTimes(
            friday = fri,
            candles = sunsetOf(fri, city)?.minusMinutes(CANDLE_MIN),
            havdalah = sunsetOf(fri.plusDays(1), city)?.plusMinutes(END_MIN)
        )
        val sh = span(today.plusDays(back))
        val end = sh.havdalah
        return if (end != null && !now.isBefore(end)) span(sh.friday.plusDays(7)) else sh
    }

    /** Еврейская дата календарного дня — на иврите и латиницей. */
    fun jewishDate(date: LocalDate, isIsrael: Boolean): Pair<String, String> = getJewishDateStrings(date, isIsrael)

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
            ZmanType.ALOT to cal.alos16Point1Degrees,
            ZmanType.SUNRISE to cal.sunrise,
            ZmanType.SOF_ZMAN_SHMA to cal.sofZmanShmaBaalHatanya,
            ZmanType.SOF_ZMAN_TFILA to cal.sofZmanTfilaBaalHatanya,
            ZmanType.CHATZOT to cal.chatzos,
            ZmanType.MINCHA_GEDOLA to cal.minchaGedolaBaalHatanya,
            ZmanType.PLAG_HAMINCHA to cal.plagHaminchaBaalHatanya,
            ZmanType.SUNSET to cal.sunset,
            ZmanType.TZAIT to cal.tzaisBaalHatanya
        )

        // Летом на севере солнце не опускается на 16,1° — рассвета нет; остальное без восхода и заката не считается
        if (rawMap.any { it.second == null && it.first != ZmanType.ALOT }) return emptyList()

        return rawMap.mapNotNull { (type, dateObj) ->
            val zdt = dateObj?.toZoned(zoneId) ?: return@mapNotNull null
            val truncated = zdt.truncatedTo(ChronoUnit.MINUTES)
            val rounded = if (type.roundDown || !zdt.isAfter(truncated)) truncated else truncated.plusMinutes(1)
            ZmanItem(type = type, exactTime = zdt, roundedTime = rounded)
        }
    }

    private fun getJewishDateStrings(gregorianDate: LocalDate, isIsrael: Boolean): Pair<String, String> {
        // JewishCalendar(LocalDate): конструктор (год, месяц, день) ждёт ЕВРЕЙСКУЮ дату
        val jc = JewishCalendar(gregorianDate)
        jc.inIsrael = isIsrael
        val hebFormatter = HebrewDateFormatter().apply { isHebrewFormat = true }
        val latFormatter = HebrewDateFormatter().apply { isHebrewFormat = false }
        return hebFormatter.format(jc) to latFormatter.format(jc)
    }

    private fun Date.toZoned(zoneId: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(this.time).atZone(zoneId)
}
