package app.mychitas.peleshaon.domain

import com.kosherjava.zmanim.ComprehensiveZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Времена дня — как на странице «Часы» сайта Нифлаот (src/core/clock.ts): по Алтер Ребе (Бааль ґа-Тания).
 * `isDeadline` — «не позже» (конец Шма, молитвы, Минхи — закат): обратный отсчёт «осталось».
 * `roundDown` — округление вниз до минуты (никогда не позже настоящего); остальное — вверх.
 * `defaultPinned` — подписаны на циферблате, пока пользователь не выбрал сам.
 */
enum class ZmanType(val isDeadline: Boolean, val defaultPinned: Boolean, val roundDown: Boolean) {
    // For fast days, never display the dawn threshold after its exact onset.
    ALOT(isDeadline = false, defaultPinned = false, roundDown = true),
    // Earliest tallit and tefillin (10.2 degrees).
    MISHEYAKIR(isDeadline = false, defaultPinned = false, roundDown = false),
    SUNRISE(isDeadline = false, defaultPinned = false, roundDown = false),
    SOF_ZMAN_SHMA(isDeadline = true, defaultPinned = true, roundDown = true),
    SOF_ZMAN_TFILA(isDeadline = true, defaultPinned = true, roundDown = true),
    // Only on Erev Pesach: last time to burn chametz.
    BIUR_CHAMETZ(isDeadline = true, defaultPinned = false, roundDown = true),
    // Chatzot also limits late Shacharit; never show a later minute.
    CHATZOT(isDeadline = false, defaultPinned = false, roundDown = true),
    MINCHA_GEDOLA(isDeadline = false, defaultPinned = false, roundDown = false),
    /** По Алтер Ребе: полчаса после полудня — бо́льшие из «временных» и обычных (владелец, 25.11.2026). */
    MINCHA_GEDOLA_AR(isDeadline = false, defaultPinned = false, roundDown = false),
    MINCHA_KETANA(isDeadline = false, defaultPinned = false, roundDown = false),
    PLAG_HAMINCHA(isDeadline = false, defaultPinned = false, roundDown = false),
    SUNSET(isDeadline = true, defaultPinned = true, roundDown = true),
    TZAIT(isDeadline = false, defaultPinned = false, roundDown = false),
    /** Выход звёзд по Алтер Ребе — солнце на 7° под горизонтом (владелец, 25.11.2026). */
    TZAIT_AR(isDeadline = false, defaultPinned = false, roundDown = false)
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

/**
 * Половина суток, которую показывает циферблат (владелец, 17.11.2026: «12 часов — или дня, или ночи; горит луна или солнце»):
 * день — от восхода до заката, ночь — от заката до восхода; в каждой 12 галахических часов. `alot`, `tzeit` — рассвет и выход
 * звёзд этой ночи (там обод переходит от ночи к дню и обратно). Как `halfOf` на странице «Часы» сайта.
 */
data class Half(
    val day: Boolean,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val alot: ZonedDateTime?,
    val tzeit: ZonedDateTime?
) {
    /** Где момент в половине: 0 — начало, 1 — конец (оба — вверху циферблата). */
    fun frac(at: ZonedDateTime): Double =
        (at.toInstant().toEpochMilli() - start.toInstant().toEpochMilli()).toDouble() /
            (end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli())

    /** Галахический час половины: 1…12. */
    fun hour(now: ZonedDateTime): Int = (kotlin.math.floor(frac(now) * 12).toInt() + 1).coerceIn(1, 12)
}

/** Еврейская дата числами: день, месяц (как в KosherJava: нисан — 1 … адар — 12, адар II — 13), високосный ли год, год. */
data class JewishDay(val day: Int, val month: Int, val leap: Boolean, val year: Int)

/** Шабат этой недели: свечи по местному обычаю до заката и исход по углу 8,5° (профиль Хабада). */
data class ShabbatTimes(
    val friday: LocalDate,
    val candles: ZonedDateTime?,
    val havdalah: ZonedDateTime?
) {
    val saturday: LocalDate get() = friday.plusDays(1)
}


object ZmanimEngine {

    const val CANDLE_MIN = 18L
    @Deprecated("Do not use a fixed Shabbat-end offset")
    const val END_MIN = 50L

    /** null = city default; the user may override the local candle-lighting custom. */
    @Volatile
    var candleOffsetOverride: Long? = null

    fun localCandleMinutesFor(cityName: String): Long =
        ZmanimService.localCandleMinutes(cityName)

    fun candleMinutesFor(cityName: String): Long =
        candleOffsetOverride ?: localCandleMinutesFor(cityName)

    fun candleLightingOn(date: LocalDate, city: CityOption): ZonedDateTime? =
        ZmanimService.calculate(date, ZmanimConfig(city, candleOffsetOverride))
            .candleLighting?.atZone(city.zone())

    /** When sunrise is missing, a known sunset still fixes the transition of the Jewish date. */
    fun dateAfterKnownSunset(date: LocalDate, moment: ZonedDateTime, sunset: ZonedDateTime?): LocalDate =
        if (sunset != null && !moment.isBefore(sunset)) date.plusDays(1) else date

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
            val targetDate = dateAfterKnownSunset(today, now, todaySunset)
            val targetCal = if (targetDate != today)
                buildCalendar(targetDate, cityName, lat, lon, elevation, zoneId) else todayCal
            val (hebStr, translitStr) = getJewishDateStrings(targetDate, isIsrael)
            // Some phenomena are still computable in polar twilight, even when
            // sunrise or sunset is undefined. Do not hide surviving valid values.
            return DayZmanimResult(
                date = targetDate,
                isNextJewishDayAfterSunset = targetDate != today,
                jewishDateHebrew = hebStr,
                jewishDateTranslit = translitStr,
                isPolarAnomaly = true,
                items = extractZmanim(targetCal, zoneId),
                candleLighting = ZmanimService.fromCalendar(todayCal,
                    candleMinutesFor(cityName)).candleLighting?.atZone(zoneId),
                tzeitShabbat = havdalahOf(todayCal, zoneId)
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
            isPolarAnomaly = items.none { it.type == ZmanType.SUNRISE } ||
                items.none { it.type == ZmanType.SUNSET },
            items = items,
            candleLighting = ZmanimService.fromCalendar(todayCal,
                candleMinutesFor(cityName)).candleLighting?.atZone(zoneId),
            tzeitShabbat = havdalahOf(todayCal, zoneId)
        )
    }

    /** Закат календарного дня в городе (точно, без округления); нет заката — null. */
    fun sunsetOf(date: LocalDate, city: CityOption): ZonedDateTime? =
        buildCalendar(date, city.name, city.lat, city.lon, 0.0, city.zone()).sunset?.toZoned(city.zone())

    /** Конец времени Шма по Алтер Ребе (округлён вниз). */
    fun shmaOf(date: LocalDate, city: CityOption): ZonedDateTime? =
        shmaFrom(cal(date, city))?.toZoned(city.zone())?.truncatedTo(ChronoUnit.MINUTES)

    /** The same canonical Shema for UI and alarm scheduling. */
    private fun shmaFrom(cal: ComprehensiveZmanimCalendar): Instant? = snapshot(cal).shema

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
            candles = candleLightingOn(fri, city),
            havdalah = havdalahOn(fri.plusDays(1), city)
        )
        val sh = span(today.plusDays(back))
        val end = sh.havdalah
        return if (end != null && !now.isBefore(end)) span(sh.friday.plusDays(7)) else sh
    }

    private fun cal(date: LocalDate, city: CityOption) = buildCalendar(date, city.name, city.lat, city.lon, 0.0, city.zone())

    /** Половина суток сейчас; нет восхода или заката (полярный день / ночь) — null. */
    fun halfOf(now: ZonedDateTime, city: CityOption): Half? {
        val zone = city.zone()
        val today = now.withZoneSameInstant(zone).toLocalDate()
        val t = cal(today, city)
        val rise = t.sunrise?.toZoned(zone) ?: return null
        val set = t.sunset?.toZoned(zone) ?: return null
        if (!now.isBefore(rise) && now.isBefore(set)) return Half(true, rise, set, null, null)
        if (now.isBefore(rise)) {
            val y = cal(today.minusDays(1), city)
            val start = y.sunset?.toZoned(zone) ?: return null
            return Half(false, start, rise, alotOf(t)?.toZoned(zone), tzeitOf(y)?.toZoned(zone))
        }
        val n = cal(today.plusDays(1), city)
        val end = n.sunrise?.toZoned(zone) ?: return null
        return Half(false, set, end, alotOf(n)?.toZoned(zone), tzeitOf(t)?.toZoned(zone))
    }

    /**
     * Времена дня, что попадают в эту половину, — из соседних календарных дней (в ночи — выход звёзд одного дня, рассвет и восход
     * следующего). Время начала половины (восход дня, закат ночи) не берётся: это конец прошлой половины, он вверху, где и конец этой.
     */
    fun halfItems(half: Half, city: CityOption): List<ZmanItem> {
        val zone = city.zone()
        val mid = Instant.ofEpochMilli((half.start.toInstant().toEpochMilli() + half.end.toInstant().toEpochMilli()) / 2).atZone(zone).toLocalDate()
        val seen = mutableSetOf<ZmanType>()
        return (-1L..1L).flatMap { n -> extractZmanim(cal(mid.plusDays(n), city), zone) }
            // Membership belongs to exact astronomical events; display rounding may shift
            // sunrise one minute past the boundary and must not move it into daytime.
            .filter { it.exactTime.isAfter(half.start) && !it.exactTime.isAfter(half.end) && seen.add(it.type) }
            .sortedBy { it.roundedTime }
    }

    /** Времена календарного дня (без перехода на завтра после заката) — для дня, выбранного в календаре. */
    fun itemsOf(date: LocalDate, city: CityOption): List<ZmanItem> = extractZmanim(cal(date, city), city.zone())

    /** Еврейская дата числами — для крупной строки «7 кислева 5787». */
    fun jewishDay(date: LocalDate, isIsrael: Boolean): JewishDay {
        val jc = JewishCalendar(date).apply { inIsrael = isIsrael }
        return JewishDay(jc.jewishDayOfMonth, jc.jewishMonth, jc.isJewishLeapYear, jc.jewishYear)
    }

    /** Еврейская дата календарного дня — на иврите и латиницей. */
    fun jewishDate(date: LocalDate, isIsrael: Boolean): Pair<String, String> = getJewishDateStrings(date, isIsrael)

    private fun buildCalendar(
        date: LocalDate, cityName: String, lat: Double, lon: Double,
        elevation: Double, zoneId: ZoneId
    ): ComprehensiveZmanimCalendar =
        ZmanimService.buildCalendar(date, cityName, lat, lon, elevation, zoneId)

    private fun snapshot(cal: ComprehensiveZmanimCalendar): ZmanimSnapshot =
        ZmanimService.fromCalendar(cal, candleOffsetOverride ?: CANDLE_MIN)

    private fun extractZmanim(cal: ComprehensiveZmanimCalendar, zoneId: ZoneId): List<ZmanItem> {
        val z = snapshot(cal)
        val rawMap = listOf(
            ZmanType.ALOT to z.dawn,
            ZmanType.MISHEYAKIR to z.misheyakir,
            ZmanType.SUNRISE to z.sunrise,
            ZmanType.SOF_ZMAN_SHMA to z.shema,
            ZmanType.SOF_ZMAN_TFILA to z.tfila,
            ZmanType.BIUR_CHAMETZ to z.sofZmanBiurChametz,
            ZmanType.CHATZOT to z.chatzot,
            ZmanType.MINCHA_GEDOLA to z.minchaGedola,
            ZmanType.MINCHA_GEDOLA_AR to z.minchaGedolaAlterRebbe,
            ZmanType.MINCHA_KETANA to z.minchaKetana,
            ZmanType.PLAG_HAMINCHA to z.plagHamincha,
            ZmanType.SUNSET to z.sunset,
            ZmanType.TZAIT to z.ordinaryTzait,
            ZmanType.TZAIT_AR to z.tzaitAlterRebbe
        )
        return rawMap.mapNotNull { (type, instant) ->
            val zdt = instant?.atZone(zoneId) ?: return@mapNotNull null
            val truncated = zdt.truncatedTo(ChronoUnit.MINUTES)
            val rounded = if (type.roundDown || !zdt.isAfter(truncated)) truncated
                else truncated.plusMinutes(1)
            ZmanItem(type, zdt, rounded)
        }.sortedBy { it.exactTime }
    }

    private fun alotOf(cal: ComprehensiveZmanimCalendar): Instant? = snapshot(cal).dawn

    private fun tzeitOf(cal: ComprehensiveZmanimCalendar): Instant? = snapshot(cal).ordinaryTzait

    /** Never declare Shabbat ended before the actual 8.5-degree event. */
    private fun havdalahOf(cal: ComprehensiveZmanimCalendar, zone: ZoneId): ZonedDateTime? =
        snapshot(cal).shabbatEnd?.atZone(zone)?.let {
            if (it.second > 0 || it.nano > 0) it.withSecond(0).withNano(0).plusMinutes(1)
            else it
        }

    /**
     * «Мазаль Маадим» — час Марса в ночь Шабата, 7-й час; в него по обычаю не делают кидуш (Шулхан арух Алтер Ребе 271:3).
     * Часы здесь равные: каждую пятницу с 18:00 до 19:00 по местному солнечному времени города (долгота × 4 мин от Гринвича),
     * поэтому в часах пояса он всегда в одно время (летом — на час позже). Как на 7thhour.info: Вена — 17:55–18:55 зимой.
     * Минуты округлены до ближайшей (владелец, 18.11.2026).
     */
    fun seventhHourOfShabbatNight(friday: LocalDate, city: CityOption): Pair<ZonedDateTime, ZonedDateTime> {
        val offSec = (city.lon * 240).toLong() // местное солнечное время = UTC + долгота × 4 мин
        fun at(h: Int): ZonedDateTime {
            val utc = friday.atTime(h, 0).toEpochSecond(java.time.ZoneOffset.UTC) - offSec
            val rounded = (utc + 30) / 60 * 60
            return Instant.ofEpochSecond(rounded).atZone(city.zone())
        }
        return at(18) to at(19)
    }

    /** Исход Шабата / праздника этого календарного дня в городе. */
    fun havdalahOn(date: LocalDate, city: CityOption): ZonedDateTime? = havdalahOf(cal(date, city), city.zone())

    private fun getJewishDateStrings(gregorianDate: LocalDate, isIsrael: Boolean): Pair<String, String> {
        // JewishCalendar(LocalDate): конструктор (год, месяц, день) ждёт ЕВРЕЙСКУЮ дату
        val jc = JewishCalendar(gregorianDate)
        jc.inIsrael = isIsrael
        val hebFormatter = HebrewDateFormatter().apply { isHebrewFormat = true }
        val latFormatter = HebrewDateFormatter().apply { isHebrewFormat = false }
        return hebFormatter.format(jc) to latFormatter.format(jc)
    }

    private fun Instant.toZoned(zoneId: ZoneId): ZonedDateTime = atZone(zoneId)
}
