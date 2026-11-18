package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanimConfig
import app.mychitas.peleshaon.domain.ZmanimService
import app.mychitas.peleshaon.domain.cityById
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks Chabad.org's explicitly defined halachic boundaries, not civil sunrise/sunset:
 * https://www.chabad.org/library/article_cdo/aid/3209349/jewish/About-Our-Zmanim-Calculations.htm
 * Independent numeric Chabad.org city/date comparisons remain a separate acceptance gate.
 */
class ChabadCompleteDayRegressionTest {
    private val cities = listOf("Asia/Jerusalem", "dnipro", "Europe/Berlin",
        "Europe/Vilnius", "Europe/London", "Europe/Simferopol")
    private val dates = listOf(
        LocalDate.of(2026, 3, 20), LocalDate.of(2026, 6, 19),
        LocalDate.of(2026, 9, 18), LocalDate.of(2026, 12, 18)
    )

    @Test fun noonAndNightMidpointUseTrueBaalHatanyaBoundaries() {
        for (id in cities) {
            val city = requireNotNull(cityById(id))
            for (date in dates) {
                val calendar = ZmanimService.buildCalendar(date, city.name,
                    city.lat, city.lon, 0.0, city.zone())
                val trueRise = calendar.getSunriseOffsetByDegrees(91.583) ?: continue
                val trueSet = calendar.getSunsetOffsetByDegrees(91.583) ?: continue
                val result = ZmanimService.calculate(date, ZmanimConfig(city, Minhag.CHABAD))
                val noon = assertNotNull(result.chatzot, "$id $date chatzot")
                val midpoint = trueRise.plusNanos(Duration.between(trueRise, trueSet).toNanos() / 2)
                assertTrue(kotlin.math.abs(Duration.between(midpoint, noon).toMillis()) <= 1000,
                    "$id $date: chatzot must be the midpoint of TRUE sunrise and sunset")
            }
        }
    }

    @Test fun additionalChabadZmanimUseCanonicalKosherJavaMethods() {
        for (id in cities) {
            val city = requireNotNull(cityById(id))
            for (date in dates) {
                val c = ZmanimService.buildCalendar(date, city.name, city.lat, city.lon, 0.0, city.zone())
                val z = ZmanimService.calculate(date, ZmanimConfig(city, Minhag.CHABAD))
                assertEquals(c.misheyakir10Point2Degrees, z.misheyakir, "$id $date misheyakir")
                assertEquals(c.minchaKetanaBaalHatanya, z.minchaKetana, "$id $date mincha ketana")
                assertEquals(c.sofZmanBiurChametzBaalHatanya, z.sofZmanBiurChametz,
                    "$id $date biur chametz (must be null except Erev Pesach)")
            }
        }
    }

    @Test fun chametzDeadlineIsOnlyPublishedForErevPesach() {
        val city = requireNotNull(cityById("Asia/Jerusalem"))
        val erevPesach = LocalDate.of(2026, 4, 1)
        val ordinary = LocalDate.of(2026, 4, 2)
        assertNotNull(ZmanimService.calculate(erevPesach, ZmanimConfig(city, Minhag.CHABAD)).sofZmanBiurChametz)
        assertNull(ZmanimService.calculate(ordinary, ZmanimConfig(city, Minhag.CHABAD)).sofZmanBiurChametz)
    }
}
