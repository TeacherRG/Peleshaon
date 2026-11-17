package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.ShabbatGuard
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.JewishCal
import app.mychitas.peleshaon.domain.KiddushLevanaRules
import app.mychitas.peleshaon.domain.MoonTracker
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.cityById
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoonAndPolarRegressionTest {
    private val vienna = requireNotNull(cityById("Europe/Vienna"))
    private val jerusalem = requireNotNull(cityById("Asia/Jerusalem"))

    @AfterTest fun cleanOffset() { ZmanimEngine.candleOffsetOverride = null }

    @Test fun moonBlessingNotPermittedJustAfterSunset() {
        val date = LocalDate.of(2026, 11, 19)
        val sunset = assertNotNull(ZmanimEngine.sunsetOf(date, vienna))
        val tzeit = assertNotNull(ZmanimEngine.itemsOf(date, vienna)
            .firstOrNull { it.type == ZmanType.TZAIT }?.exactTime)
        val moment = sunset.plusMinutes(1)
        assertTrue(moment.isBefore(tzeit), "test must be in twilight before tzeit")
        assertNull(KiddushLevanaRules.now(moment, vienna))
    }

    @Test fun futureMoonRemindersUseTzeitAndFiveDegreeThreshold() {
        val start = LocalDate.of(2026, 11, 17)
        var count = 0
        for (n in 0L..44L) {
            val date = start.plusDays(n)
            val at = KiddushLevanaRules.reminderOn(date, vienna) ?: continue
            val tzeit = assertNotNull(ZmanimEngine.itemsOf(date, vienna)
                .firstOrNull { it.type == ZmanType.TZAIT }?.exactTime)
            assertTrue(!at.isBefore(tzeit), "reminder before tzeit: $date")
            assertTrue(MoonTracker.position(at, vienna.lat, vienna.lon).altitude >= 5.0,
                "Moon below selected 5-degree threshold: $at")
            count++
        }
        assertTrue(count > 0, "Expected a suitable night in two lunar cycles")
    }

    @Test fun polarDayRetainsEveryAvailableAstronomicalEvent() {
        val city = CityOption("polar-test", "Longyearbyen", "Лонгйир", null,
            78.2232, 15.6267, "Arctic/Longyearbyen", false)
        val date = LocalDate.of(2027, 6, 25)
        val result = ZmanimEngine.calculateForMoment(date.atTime(12, 0).atZone(city.zone()),
            city.name, city.lat, city.lon, 0.0, city.zone(), false)
        assertTrue(result.isPolarAnomaly)
        assertEquals(ZmanimEngine.itemsOf(date, city), result.items)
    }

    @Test fun remainingNightIsSearchedWithoutASixHourSunsetCutoff() {
        val start = LocalDate.of(2026, 11, 1)
        var checked = 0
        for (n in 0L..60L) {
            val day = start.plusDays(n)
            val sunset = ZmanimEngine.sunsetOf(day, vienna) ?: continue
            val later = sunset.plusHours(7)
            val sunrise = ZmanimEngine.itemsOf(day.plusDays(1), vienna)
                .firstOrNull { it.type == ZmanType.SUNRISE }?.exactTime ?: continue
            if (!later.isBefore(sunrise)) continue
            val result = KiddushLevanaRules.reminderOn(day, vienna)
            assertTrue(result == null || (result.isAfter(sunset) && result.isBefore(sunrise)))
            checked++
        }
        assertTrue(checked > 0)
    }

    @Test fun jerusalemCustomCandleOffsetAffectsQuietWindow() {
        val friday = LocalDate.of(2026, 11, 20)
        val sunset = assertNotNull(ZmanimEngine.sunsetOf(friday, jerusalem))
        val at = sunset.minusMinutes(30)
        fun quiet() = ShabbatGuard.checkMoment(at, jerusalem.name, jerusalem.lat, jerusalem.lon,
            jerusalem.zone(), jerusalem.isIsrael, false).isQuietTime
        assertTrue(quiet(), "Jerusalem default 40 minutes should already be quiet")
        ZmanimEngine.candleOffsetOverride = 18L
        assertTrue(!quiet(), "User-chosen 18-minute practice must affect the alarm guard")
    }
}
