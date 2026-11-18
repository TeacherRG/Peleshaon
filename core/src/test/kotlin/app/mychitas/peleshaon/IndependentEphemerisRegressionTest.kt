package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanimConfig
import app.mychitas.peleshaon.domain.ZmanimService
import app.mychitas.peleshaon.domain.cityById
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EXTERNAL ephemeris baseline, not another call to KosherJava:
 * Swiss Ephemeris solar equatorial coordinates + horizontal altitude root-finding.
 * Center of the Sun -0.8333 degrees for sea-level sunrise/sunset;
 * true solar depression 16.9 degrees (Baal Hatanya dawn), 6 degrees (ordinary
 * Chabad tzait), 8.5 degrees (selected Shabbat exit).
 *
 * Calculated independently for 7 sites x 4 seasons in 2026. Local HH:mm values
 * recorded to the minute. Comparisons tolerate distinct ephemeris/refraction
 * conventions (8 min or 12 min near Arctic circle); no fallback times invented.
 * These are astronomical consistency tests, NOT rabbinic certification.
 * See INDEPENDENT_ZMANIM_AUDIT.md.
 */
class IndependentEphemerisRegressionTest {
    private val locations: Map<String, CityOption> = mapOf(
        "jer" to requireNotNull(cityById("Asia/Jerusalem")),
        "dni" to requireNotNull(cityById("dnipro")),
        "vie" to requireNotNull(cityById("Europe/Vienna")),
        "ber" to requireNotNull(cityById("Europe/Berlin")),
        "vil" to requireNotNull(cityById("Europe/Vilnius")),
        "lon" to requireNotNull(cityById("Europe/London")),
        "tro" to CityOption("test:tromso", "Tromso", "Тромсё", null,
            69.6492, 18.9553, "Europe/Oslo", false)
    )

    // City | date | sunrise | sunset | dawn16.9 | ordinaryTzait6 | ShabbatExit8.5
    private val cases = listOf(
        "jer|2026-03-20|05:43|17:50|04:27|18:14|18:26",
        "jer|2026-06-19|05:33|19:47|04:00|20:15|20:29",
        "jer|2026-09-18|06:24|18:41|05:08|19:05|19:17",
        "jer|2026-12-18|06:33|16:37|05:11|17:04|17:17",
        "dni|2026-03-20|05:43|17:52|04:04|18:23|18:38",
        "dni|2026-06-19|04:37|20:44|01:40|21:26|21:49",
        "dni|2026-09-18|06:20|18:46|04:40|19:17|19:33",
        "dni|2026-12-18|07:26|15:45|05:38|16:22|16:39",
        "vie|2026-03-20|05:57|18:07|04:19|18:38|18:53",
        "vie|2026-06-19|04:53|20:58|02:00|21:39|22:02",
        "vie|2026-09-18|06:35|19:01|04:55|19:32|19:47",
        "vie|2026-12-18|07:40|16:01|05:52|16:38|16:55",
        "ber|2026-03-20|06:09|18:19|04:20|18:53|19:10",
        "ber|2026-06-19|04:42|21:32|--|22:23|22:52",
        "ber|2026-09-18|06:45|19:14|04:55|19:48|20:05",
        "ber|2026-12-18|08:13|15:52|06:12|16:34|16:53",
        "vil|2026-03-20|06:21|18:32|04:26|19:08|19:25",
        "vil|2026-06-19|04:41|21:59|--|22:56|23:32",
        "vil|2026-09-18|06:56|19:28|04:59|20:04|20:22",
        "vil|2026-12-18|08:38|15:52|06:29|16:37|16:57",
        "lon|2026-03-20|06:03|18:13|04:17|18:46|19:03",
        "lon|2026-06-19|04:42|21:21|--|22:08|22:35",
        "lon|2026-09-18|06:40|19:08|04:52|19:41|19:58",
        "lon|2026-12-18|08:01|15:52|06:05|16:32|16:50",
        "tro|2026-03-20|05:43|18:01|02:09|19:02|19:32",
        "tro|2026-06-19|--|--|--|--|--",
        "tro|2026-09-18|06:08|19:05|02:06|20:07|20:38",
        "tro|2026-12-18|--|--|06:39|13:52|14:42"
    )

    @Test fun independentAstronomicalReferenceAcrossSevenCitiesAndFourSeasons() {
        var comparisons = 0
        for (row in cases) {
            val (id, textDate, sunrise, sunset, dawn, ordinary, exit) = row.split("|")
            val city = requireNotNull(locations[id])
            val date = LocalDate.parse(textDate)
            val result = ZmanimService.calculate(date, ZmanimConfig(city, Minhag.CHABAD))
            val reference = listOf(
                "sunrise" to (sunrise to result.sunrise),
                "sunset" to (sunset to result.sunset),
                "dawn16.9" to (dawn to result.dawn),
                "tzait6" to (ordinary to result.ordinaryTzait),
                "shabbat8.5" to (exit to result.shabbatEnd)
            )
            for ((event, value) in reference) {
                val (hhmm, actual) = value
                if (hhmm == "--") {
                    // Explicit missing astronomic events: no fixed-time substitution.
                    if (id == "tro" && textDate == "2026-06-19") {
                        assertNull(actual, "$id $textDate $event must not be guessed during midnight sun")
                    }
                    continue
                }
                val expected = date.atTime(LocalTime.parse(hhmm)).atZone(city.zone()).toInstant()
                val observed = assertNotNull(actual, "$id $textDate $event must exist")
                val errorMinutes = kotlin.math.abs(Duration.between(expected, observed).toMillis()) / 60_000.0
                val toleranceMinutes = if (id == "tro") 12.0 else 8.0
                assertTrue(errorMinutes <= toleranceMinutes,
                    "$id $textDate $event Swiss Ephemeris $hhmm vs KosherJava " +
                        observed.atZone(city.zone()) + " (difference $errorMinutes min)")
                comparisons++
            }
        }
        assertTrue(comparisons >= 120, "Independent matrix must cover most astronomical events")
    }

    @Test fun publishedBerlinCandleTimeAgreesWithIndependentSunset() {
        val berlin = requireNotNull(locations["ber"])
        val date = LocalDate.of(2026, 6, 19)
        val snapshot = ZmanimService.calculate(date, ZmanimConfig(berlin, Minhag.CHABAD))
        // Published Hebcal Berlin calendar 19 June 2026: candles 21:14, 18 min before sunset.
        // URL in INDEPENDENT_ZMANIM_AUDIT.md; not an independent halakhic ruling.
        val published = date.atTime(21, 14).atZone(berlin.zone()).toInstant()
        val actual = assertNotNull(snapshot.candleLighting)
        assertTrue(kotlin.math.abs(Duration.between(published, actual).toMinutes()) <= 4,
            "Berlin 2026-06-19: candle lighting should match published sunset-derived 21:14")
    }
}
