package app.mychitas.peleshaon.domain

import com.kosherjava.zmanim.ComprehensiveZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** An immutable, fully specified request: never read UI or process-global preferences in a calculation. */
data class ZmanimConfig(
    val city: CityOption,
    val minhag: Minhag,
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
    val minchaGedola: Instant?,
    val plagHamincha: Instant?,
    val sunset: Instant?,
    val ordinaryTzait: Instant?,
    val shabbatEnd: Instant?,
    val candleLighting: Instant?,
    val methodIds: Map<String, String>
)

/**
 * Canonical KosherJava 3.1.0 API adapter. In particular, ordinary Chabad tzait
 * (6 degrees) is not the Shabbat/Yom Tov exit (8.5 degrees).
 *
 * The Sephardi profile deliberately preserves its old calculation choices pending
 * qualified rabbinic review. These choices are not universal halakhic rulings.
 */
object ZmanimService {
    private val overrides = setOf(18L, 20L, 22L, 30L, 40L)

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
        return fromCalendar(calendar, config.minhag, candleMinutes(config))
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

    /** Used by the legacy public ZmanimEngine while UI consumers migrate to explicit configs. */
    internal fun fromCalendar(
        calendar: ComprehensiveZmanimCalendar,
        community: Minhag,
        candleMinutes: Long
    ): ZmanimSnapshot {
        // Chabad.org: when summer twilight never reaches -16.9 degrees,
        // alos begins at the lowest point of the *previous* night.
        // SolarMidnight is calculated at the END of its calendar date, so
        // use the previous civil date. Never turn polar night into false dawn.
        val chabadDawnByDegrees = if (community == Minhag.CHABAD) calendar.alosBaalHatanya else null
        val solarMidnightDawn = if (community == Minhag.CHABAD &&
            chabadDawnByDegrees == null &&
            calendar.sunrise != null && calendar.sunset != null) {
            ComprehensiveZmanimCalendar(calendar.geoLocation).apply {
                localDate = calendar.localDate.minusDays(1)
            }.solarMidnight?.takeIf {
                it.atZone(calendar.geoLocation.zoneId).toLocalDate() == calendar.localDate
            }
        } else null
        val actualDawn = chabadDawnByDegrees ?: solarMidnightDawn

        val ordinary = when (community) {
            Minhag.CHABAD -> calendar.tzaisBaalHatanya
            Minhag.LITVISH -> calendar.tzaisGeonim8Point5Degrees
            Minhag.SEFARDI -> calendar.getZmanisBasedOffset(13.5 / 60.0)
        }
        val exit = when (community) {
            Minhag.CHABAD, Minhag.LITVISH -> calendar.tzaisGeonim8Point5Degrees
            Minhag.SEFARDI -> calendar.tzais72Zmanis
        }
        val methods = when (community) {
            Minhag.CHABAD -> mapOf(
                "dawn" to (if (solarMidnightDawn != null)
                    "chabad.alos.solar_midnight_when_16_9deg_unreachable"
                    else "chabad.alos.16_9deg"),
                "ordinaryTzait" to "chabad.tzait.6deg",
                "shabbatEnd" to "chabad.havdalah.8_5deg",
                "shema" to "baal_hatanya.shema"
            )
            Minhag.LITVISH -> mapOf(
                "dawn" to "gra.alos.16_1deg",
                "ordinaryTzait" to "selected.gra.tzait.8_5deg",
                "shabbatEnd" to "selected.gra.havdalah.8_5deg",
                "shema" to "gra.shema"
            )
            Minhag.SEFARDI -> mapOf(
                "dawn" to "selected.sefardi.alos.72zmanis",
                "ordinaryTzait" to "selected.sefardi.tzait.13_5zmanis",
                "shabbatEnd" to "selected.sefardi.rabbeinu_tam.72zmanis",
                "shema" to "selected.sefardi.mga72zmanis"
            )
        }
        return ZmanimSnapshot(
            dawn = when (community) {
                Minhag.CHABAD -> actualDawn
                Minhag.LITVISH -> calendar.alos16Point1Degrees
                Minhag.SEFARDI -> calendar.alos72Zmanis
            },
            sunrise = calendar.sunrise,
            shema = when (community) {
                Minhag.CHABAD -> calendar.sofZmanShmaBaalHatanya
                Minhag.LITVISH -> calendar.sofZmanShmaGRA
                Minhag.SEFARDI -> calendar.sofZmanShmaMGA72MinutesZmanis
            },
            tfila = if (community == Minhag.CHABAD) calendar.sofZmanTfilaBaalHatanya else calendar.sofZmanTfilaGRA,
            chatzot = calendar.chatzosHayom,
            // Revisit the 30-minute threshold with an approved community policy; do not silently decide it.
            minchaGedola = if (community == Minhag.CHABAD) calendar.minchaGedolaBaalHatanya else calendar.minchaGedolaGRA,
            plagHamincha = if (community == Minhag.CHABAD) calendar.plagHaminchaBaalHatanya else calendar.plagHaminchaGRA,
            sunset = calendar.sunset,
            ordinaryTzait = ordinary,
            shabbatEnd = exit,
            candleLighting = if (candleMinutes in setOf(18L, 20L, 22L, 30L, 40L)) {
                calendar.candleLightingOffset = candleMinutes.toDouble()
                calendar.candleLighting
            } else null,
            methodIds = methods
        )
    }
}
