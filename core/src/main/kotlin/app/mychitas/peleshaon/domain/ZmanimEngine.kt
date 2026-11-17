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

/** Шабат этой недели: зажигание свечей (пятница, закат − 18 мин) и исход (суббота, закат + 50 мин). */
data class ShabbatTimes(
    val friday: LocalDate,
    val candles: ZonedDateTime?,
    val havdalah: ZonedDateTime?
) {
    val saturday: LocalDate get() = friday.plusDays(1)
}

/**
 * Община — по ней считаются времена дня, исход Шабата, Кидуш Левана и даты календаря (владелец, 18.11.2026:
 * «выбор общины: Хабад, литовская, сефардская — даты и зманим автоматически подстроятся»). Раввином не проверено.
 * - `CHABAD` — по Алтер Ребе (Бааль ґа-Тания), как в календарях Хабада; исход Шабата — закат + 50 мин.
 * - `LITVISH` — по Виленскому Гаону (ГРА): «Шма» и молитва — от восхода до заката; выход звёзд и исход Шабата — солнце на 8,5° под горизонтом.
 * - `SEFARDI` — по раву Овадье Йосефу («Ялкут Йосеф»): рассвет — 72 «временные» минуты, «Шма» — по Маген Авраґаму, молитва — по ГРА,
 *   выход звёзд — 13,5 «временной» минуты после заката, исход Шабата — по Рабейну Таму (72 «временные» минуты).
 */
enum class Minhag { CHABAD, LITVISH, SEFARDI }

object ZmanimEngine {

    const val CANDLE_MIN = 18L
    const val END_MIN = 50L

    /** Община приложения; ставится при запуске и в «Настройках». */
    @Volatile
    var minhag: Minhag = Minhag.CHABAD

    /** null = city default; a user's minhag may override local practice. */
    @Volatile
    var candleOffsetOverride: Long? = null

    fun localCandleMinutesFor(cityName: String): Long = when (cityName.lowercase(java.util.Locale.ROOT)) {
        "jerusalem" -> 40L
        "haifa", "zikhron yaakov", "zichron yaakov" -> 30L
        else -> CANDLE_MIN
    }

    fun candleMinutesFor(cityName: String): Long =
        candleOffsetOverride ?: localCandleMinutesFor(cityName)

    fun candleLightingOn(date: LocalDate, city: CityOption): ZonedDateTime? =
        sunsetOf(date, city)?.minusMinutes(candleMinutesFor(city.name))

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
            // Some phenomena are still computable in polar twilight, even when
            // sunrise or sunset is undefined. Do not hide surviving valid values.
            return DayZmanimResult(
                date = today,
                isNextJewishDayAfterSunset = false,
                jewishDateHebrew = hebStr,
                jewishDateTranslit = translitStr,
                isPolarAnomaly = true,
                items = extractZmanim(todayCal, zoneId),
                candleLighting = todaySunset?.minusMinutes(candleMinutesFor(cityName)),
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
            candleLighting = todaySunset.minusMinutes(candleMinutesFor(cityName)),
            tzeitShabbat = havdalahOf(todayCal, zoneId)
        )
    }

    /** Закат календарного дня в городе (точно, без округления); нет заката — null. */
    fun sunsetOf(date: LocalDate, city: CityOption): ZonedDateTime? =
        buildCalendar(date, city.name, city.lat, city.lon, 0.0, city.zone()).sunset?.toZoned(city.zone())

    /** Конец времени Шма по выбранной общине (округлён вниз). */
    fun shmaOf(date: LocalDate, city: CityOption): ZonedDateTime? =
        shmaFrom(cal(date, city))?.toZoned(city.zone())?.truncatedTo(ChronoUnit.MINUTES)

    /** Общая формула для будильников и отображаемых времён. */
    private fun shmaFrom(cal: ComplexZmanimCalendar): Date? = when (minhag) {
        Minhag.CHABAD -> cal.sofZmanShmaBaalHatanya
        Minhag.LITVISH -> cal.sofZmanShmaGRA
        Minhag.SEFARDI -> cal.sofZmanShmaMGA72MinutesZmanis
    }

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
            .filter { it.roundedTime.isAfter(half.start) && !it.roundedTime.isAfter(half.end) && seen.add(it.type) }
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
        val chabad = minhag == Minhag.CHABAD
        val rawMap = listOf(
            ZmanType.ALOT to alotOf(cal),
            ZmanType.SUNRISE to cal.sunrise,
            ZmanType.SOF_ZMAN_SHMA to shmaFrom(cal),
            ZmanType.SOF_ZMAN_TFILA to if (chabad) cal.sofZmanTfilaBaalHatanya else cal.sofZmanTfilaGRA,
            ZmanType.CHATZOT to cal.chatzos,
            ZmanType.MINCHA_GEDOLA to if (chabad) cal.minchaGedolaBaalHatanya else cal.minchaGedola,
            ZmanType.PLAG_HAMINCHA to if (chabad) cal.plagHaminchaBaalHatanya else cal.plagHamincha,
            ZmanType.SUNSET to cal.sunset,
            ZmanType.TZAIT to tzeitOf(cal)
        )

        // Сохраняем доступные времена даже при отсутствии отдельного астрономического события.
        return rawMap.mapNotNull { (type, dateObj) ->
            val zdt = dateObj?.toZoned(zoneId) ?: return@mapNotNull null
            val truncated = zdt.truncatedTo(ChronoUnit.MINUTES)
            val rounded = if (type.roundDown || !zdt.isAfter(truncated)) truncated else truncated.plusMinutes(1)
            ZmanItem(type = type, exactTime = zdt, roundedTime = rounded)
        }
    }

    /** Рассвет по общине. */
    private fun alotOf(cal: ComplexZmanimCalendar): Date? =
        if (minhag == Minhag.SEFARDI) cal.alos72Zmanis else cal.alos16Point1Degrees

    /** Выход звёзд по общине. */
    private fun tzeitOf(cal: ComplexZmanimCalendar): Date? = when (minhag) {
        Minhag.CHABAD -> cal.tzaisBaalHatanya
        Minhag.LITVISH -> cal.tzaisGeonim8Point5Degrees
        Minhag.SEFARDI -> {
            // 13,5 «временной» минуты после заката (рав Овадья Йосеф)
            val set = cal.sunset
            val hour = cal.shaahZmanisGra
            if (set == null || hour == Long.MIN_VALUE) null else Date(set.time + (hour * 13.5 / 60).toLong())
        }
    }

    /** Исход Шабата и праздника по общине: Хабад — закат + 50 мин, литовская — 8,5°, сефардская — Рабейну Там (72 «временные» мин). */
    private fun havdalahOf(cal: ComplexZmanimCalendar, zone: ZoneId): ZonedDateTime? = when (minhag) {
        Minhag.CHABAD -> cal.sunset?.toZoned(zone)?.plusMinutes(END_MIN)
        Minhag.LITVISH -> cal.tzaisGeonim8Point5Degrees?.toZoned(zone)
        Minhag.SEFARDI -> cal.tzais72Zmanis?.toZoned(zone)
    }?.let { if (it.second > 0 || it.nano > 0) it.withSecond(0).withNano(0).plusMinutes(1) else it }

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

    private fun Date.toZoned(zoneId: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(this.time).atZone(zoneId)
}
