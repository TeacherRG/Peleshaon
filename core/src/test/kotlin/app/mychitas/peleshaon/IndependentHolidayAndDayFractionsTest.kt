package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.ShabbatGuard
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanimConfig
import app.mychitas.peleshaon.domain.ZmanimService
import app.mychitas.peleshaon.domain.cityById
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Independent published Jewish-calendar dates (Chabad.org/Hebcal 2026):
 * Oct 3 = Shemini Atzeret/Simchat Torah in Israel; Oct 4 = Diaspora Simchat Torah.
 * Sep 21 = Yom Kippur in both regions. Do not equate a time zone with jurisdiction.
 */
class IndependentHolidayAndDayFractionsTest {
    private val jerusalem = requireNotNull(cityById("Asia/Jerusalem"))
    private val berlin = requireNotNull(cityById("Europe/Berlin"))

    private fun isQuiet(date: LocalDate, hour: Int, cityId: String): Boolean {
        val city = requireNotNull(cityById(cityId))
        val whenLocal = date.atTime(hour, 0).atZone(city.zone())
        return ShabbatGuard.checkMoment(whenLocal, city.name, city.lat, city.lon, city.zone(),
            city.isIsrael, false).isQuietTime
    }

    @Test fun october2026FestivalOneDayIsraelTwoDaysDiaspora() {
        // Published calendar: 3 Oct Shmini Atzeret/Simchat Torah (Israel);
        // 4 Oct Simchat Torah (diaspora). Both also observe the Sabbath on the 3rd.
        assertTrue(isQuiet(LocalDate.of(2026, 10, 3), 12, "Asia/Jerusalem"))
        assertTrue(isQuiet(LocalDate.of(2026, 10, 3), 12, "Europe/Berlin"))
        assertFalse(isQuiet(LocalDate.of(2026, 10, 4), 12, "Asia/Jerusalem"),
            "October 4 is no longer Yom Tov in Israel")
        assertTrue(isQuiet(LocalDate.of(2026, 10, 4), 12, "Europe/Berlin"),
            "October 4 remains diaspora Simchat Torah")
    }

    @Test fun yomKippur2026IsQuietInBothJurisdictions() {
        val day = LocalDate.of(2026, 9, 21)
        assertTrue(isQuiet(day, 12, "Asia/Jerusalem"))
        assertTrue(isQuiet(day, 12, "Europe/Berlin"))
    }

    @Test fun chabadProportionalHoursMatchSolarDayFractions() {
        for (city in listOf(jerusalem, berlin)) {
            for (date in listOf(
                LocalDate.of(2026, 3, 20), LocalDate.of(2026, 6, 19),
                LocalDate.of(2026, 9, 18), LocalDate.of(2026, 12, 18)
            )) {
                val snap = ZmanimService.calculate(date, ZmanimConfig(city, Minhag.CHABAD))
                val raw = ZmanimService.buildCalendar(date, city.name, city.lat,
                    city.lon, 0.0, city.zone())
                // The Alter Rebbe's proportional day uses hidden TRUE sunrise and
                // sunset at -1.583 degrees, NOT published civil sunrise/sunset.
                val sunrise = assertNotNull(raw.getSunriseOffsetByDegrees(91.583))
                val sunset = assertNotNull(raw.getSunsetOffsetByDegrees(91.583))
                val dayMillis = Duration.between(sunrise, sunset).toMillis()
                for ((name, fraction, actual) in listOf(
                    Triple("shema", 3.0/12, snap.shema),
                    Triple("tfila", 4.0/12, snap.tfila),
                    Triple("chatzot", 6.0/12, snap.chatzot),
                    Triple("minchaGedola", 6.5/12, snap.minchaGedola),
                    Triple("plagHamincha", 10.75/12, snap.plagHamincha)
                )) {
                    val expected = sunrise.plusMillis((dayMillis * fraction).toLong())
                    val found = assertNotNull(actual, "${city.name} $date $name")
                    val deviationMillis = kotlin.math.abs(Duration.between(expected, found).toMillis())
                    assertTrue(deviationMillis <= 1000,
                        "${city.name} $date $name deviates from true Baal Hatanya day by $deviationMillis ms")
                }
            }
        }
    }
}
