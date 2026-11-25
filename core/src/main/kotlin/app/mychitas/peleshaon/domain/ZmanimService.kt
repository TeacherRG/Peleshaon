package app.mychitas.peleshaon.domain

import com.kosherjava.zmanim.ComprehensiveZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** An immutable, fully specified request: never read UI or process-global preferences in a calculation.
 * Все времена считаются только по Алтер Ребе (Бааль ґа-Тания); выбор общины убран (владелец, 25.11.2026). */
data class ZmanimConfig(
    val city: CityOption,
    val candleOffsetMinutes: Long? = null
)

/**
 * Exact instants, not rounded wall-clock labels. null means the specified astronomical
 * calculation could not produce the event; it is NOT permission to use a fixed offset.
 */
data class ZmanimSnapshot(
    val dawn: Instant?,
    val sunrise: Instant?,
    val shema: Instant?,
    val tfila: Instant?,
    val chatzot: Instant?,
    /** Ordinary mincha gedola: half a proportional hour after chatzot. */
    val minchaGedola: Instant?,
    /**
     * Mincha gedola per the Alter Rebbe: half an hour after chatzot, taking whichever half hour
     * is LONGER — half a proportional hour (summer) or 30 clock minutes (winter).
     */
    val minchaGedolaAlterRebbe: Instant?,
    val plagHamincha: Instant?,
    val sunset: Instant?,
    val ordinaryTzait: Instant?,
    val shabbatEnd: Instant?,
    val candleLighting: Instant?,
    val methodIds: Map<String, String>,
    /** Chabad.org: earliest tallis / tefillin, solar depression 10.2 degrees. */
    val misheyakir: Instant?,
    /** 9.5 proportional hours into the true Baal Hatanya day. */
    val minchaKetana: Instant?,
    /** Only on Erev Pesach; null on ordinary days. */
    val sofZmanBiurChametz: Instant?
)

/**
 * Canonical KosherJava 3.1.0 API adapter, Alter Rebbe (Baal Hatanya) only. In particular,
 * ordinary Chabad tzait (6 degrees) is not the Shabbat/Yom Tov exit (8.5 degrees).
 */
object ZmanimService {
    private val overrides = setOf(18L, 20L, 22L, 30L, 40L)
    private val HALF_HOUR: Duration = Duration.ofMinutes(30)

    fun candleMinutes(config: ZmanimConfig): Long =
        config.candleOffsetMinutes?.takeIf { it in overrides }
            ?: localCandleMinutes(config.city.name)

    fun localCandleMinutes(cityName: String): Long = when (cityName.lowercase(Locale.ROOT)) {
        "jerusalem" -> 40L
        "haifa", "zikhron yaakov", "zichron yaakov" -> 30L
        "tel aviv" -> 22L
        else -> 18L
    }

    fun calculate(date: LocalDate, config: ZmanimConfig): ZmanimSnapshot {
        val c = config.city
        val calendar = buildCalendar(date, c.name, c.lat, c.lon, 0.0, c.zone())
        return fromCalendar(calendar, candleMinutes(config))
    }

    fun buildCalendar(
        date: LocalDate,
        cityName: String,
        latitude: Double,
        longitude: Double,
        elevation: Double,
        zone: ZoneId
    ): ComprehensiveZmanimCalendar =
        ComprehensiveZmanimCalendar(
            GeoLocation(cityName, latitude, longitude, elevation, zone)
        ).apply { localDate = date }

    /**
     * Alter Rebbe rule: mincha gedola is half an hour after chatzot, and the LONGER half hour is used —
     * half of a proportional hour in summer, 30 ordinary minutes in winter.
     */
    fun minchaGedolaAlterRebbe(chatzot: Instant?, trueRise: Instant?, trueSet: Instant?): Instant? {
        if (chatzot == null || trueRise == null || trueSet == null || !trueSet.isAfter(trueRise)) return null
        val halfZmanit = Duration.ofNanos(Duration.between(trueRise, trueSet).toNanos() / 24L)
        return chatzot.plus(if (halfZmanit > HALF_HOUR) halfZmanit else HALF_HOUR)
    }

    /** Used by the public ZmanimEngine. */
    internal fun fromCalendar(
        calendar: ComprehensiveZmanimCalendar,
        candleMinutes: Long
    ): ZmanimSnapshot {
        // Chabad.org: when summer twilight never reaches -16.9 degrees,
        // alos begins at the lowest point of the *previous* night.
        // SolarMidnight is calculated at the END of its calendar date, so
        // use the previous civil date. Never turn polar night into false dawn.
        val chabadDawnByDegrees = calendar.alosBaalHatanya
        val solarMidnightDawn = if (chabadDawnByDegrees == null &&
            calendar.sunrise != null && calendar.sunset != null) {
            val yesterday = ComprehensiveZmanimCalendar(calendar.geoLocation).apply {
                localDate = calendar.localDate.minusDays(1)
            }
            // Solar midnight can occur on the previous civil date where the
            // legal zone diverges from solar time (e.g., Kirov and Kazan).
            // Validate the physical night, not its local civil date.
            yesterday.solarMidnight?.takeIf { candidate ->
                val lastSunset = yesterday.sunset
                val nextSunrise = calendar.sunrise
                lastSunset != null && nextSunrise != null &&
                    candidate.isAfter(lastSunset) && candidate.isBefore(nextSunrise)
            }
        } else null

        // Chabad.org's chatzos is half-way between the *true* Baal Hatanya
        // sunrise and sunset (solar center -1.583 degrees), not solar transit.
        // The hidden true boundaries are not the published practical hanetz/shkiah.
        val trueRise = calendar.getSunriseOffsetByDegrees(91.583)
        val trueSet = calendar.getSunsetOffsetByDegrees(91.583)
        val chabadMidday = if (trueRise != null && trueSet != null && trueSet.isAfter(trueRise))
            trueRise.plusNanos(Duration.between(trueRise, trueSet).toNanos() / 2L)
        else null

        return ZmanimSnapshot(
            dawn = chabadDawnByDegrees ?: solarMidnightDawn,
            sunrise = calendar.sunrise,
            shema = calendar.sofZmanShmaBaalHatanya,
            tfila = calendar.sofZmanTfilaBaalHatanya,
            chatzot = chabadMidday,
            minchaGedola = calendar.minchaGedolaBaalHatanya,
            minchaGedolaAlterRebbe = minchaGedolaAlterRebbe(chabadMidday, trueRise, trueSet),
            plagHamincha = calendar.plagHaminchaBaalHatanya,
            sunset = calendar.sunset,
            ordinaryTzait = calendar.tzaisBaalHatanya,
            shabbatEnd = calendar.tzaisGeonim8Point5Degrees,
            candleLighting = if (candleMinutes in overrides) {
                calendar.candleLightingOffset = candleMinutes.toDouble()
                calendar.candleLighting
            } else null,
            methodIds = mapOf(
                "dawn" to (if (solarMidnightDawn != null)
                    "chabad.alos.solar_midnight_when_16_9deg_unreachable"
                    else "chabad.alos.16_9deg"),
                "ordinaryTzait" to "chabad.tzait.6deg",
                "shabbatEnd" to "chabad.havdalah.8_5deg",
                "shema" to "baal_hatanya.shema",
                "minchaGedolaAlterRebbe" to "baal_hatanya.mincha_gedola.greater_of_half_zmanit_or_30min"
            ),
            misheyakir = calendar.misheyakir10Point2Degrees,
            minchaKetana = calendar.minchaKetanaBaalHatanya,
            sofZmanBiurChametz = calendar.sofZmanBiurChametzBaalHatanya
        )
    }
}
