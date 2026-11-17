package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.ShabbatGuard
import app.mychitas.peleshaon.alarm.AlarmRules
import app.mychitas.peleshaon.alarm.AlarmSpec
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.cityById
import app.mychitas.peleshaon.domain.cityForZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.ZoneId

class ForkRegressionTest {
    @AfterTest fun resetCommunity() { ZmanimEngine.minhag = Minhag.CHABAD }
    @Test fun shmaAlarmUsesCommunityFormula() {
        val date = LocalDate.of(2026, 11, 17)
        val city = requireNotNull(cityById("Europe/Vienna"))
        val alarm = AlarmSpec(true, true, 7, 0, 30, setOf(0, 1, 2, 3, 4, 5), false)
        val distinct = mutableSetOf<Long>()
        for (community in Minhag.entries) {
            ZmanimEngine.minhag = community
            val shown = ZmanimEngine.itemsOf(date, city).first { it.type == ZmanType.SOF_ZMAN_SHMA }.roundedTime
            val scheduled = assertNotNull(AlarmRules.ringOn(alarm, date, city))
            assertEquals(shown.minusMinutes(30), scheduled, "Community $community")
            distinct += shown.toEpochSecond()
        }
        assertTrue(distinct.size > 1, "Selected community should change the Shma deadline")
    }
    @Test fun polarHolyDayDoesNotInventClockTimeBoundaries() {
        val city = app.mychitas.peleshaon.domain.CityOption(
            "polar-test", "Longyearbyen", "Лонгйир", null,
            78.2232, 15.6267, "Arctic/Longyearbyen", false
        )
        val noonFriday = LocalDate.of(2027, 6, 25).atTime(12, 0).atZone(city.zone())
        val quiet = ShabbatGuard.checkMoment(noonFriday, city.name, city.lat, city.lon, city.zone(), false, false)
        assertTrue(quiet.isQuietTime)
        assertEquals("unknown", quiet.reasonKey)
    }

    @Test fun selectedCitiesAndTimeZoneAreConsistent() {
        assertEquals("Europe/Kiev", cityForZone(ZoneId.of("Europe/Kyiv")).id)
        assertEquals("Mumbai", cityById("Asia/Kolkata")?.name)
        assertEquals("Kolkata", cityById("kolkata")?.name)
        assertEquals("Be'er Sheva", cityById("beer-sheva")?.name)
        assertEquals(22.5726, cityById("kolkata")!!.lat, 0.0001)
    }
}
