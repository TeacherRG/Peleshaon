package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.AlarmRules
import app.mychitas.peleshaon.alarm.AlarmSpec
import app.mychitas.peleshaon.alarm.KashrutPolicy
import app.mychitas.peleshaon.alarm.ShabbatGuard
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.cityById
import java.time.LocalDate
import java.time.ZoneId
import app.mychitas.peleshaon.domain.cityMatchForZone
import kotlin.test.assertNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShabbatSafetyTest {
    private val city = requireNotNull(cityById("Europe/Vienna"))
    private val friday = LocalDate.of(2026, 11, 20)

    @AfterTest fun reset() {
        ZmanimEngine.candleOffsetOverride = null
    }

    private fun quiet(at: java.time.ZonedDateTime, early: Long = 0L): Boolean =
        ShabbatGuard.checkMoment(at, city.name, city.lat, city.lon, city.zone(), city.isIsrael,
            false, early).isQuietTime

    @Test fun exactCandleAndHavdalahBoundariesAreSafe() {
        val candles = assertNotNull(ZmanimEngine.candleLightingOn(friday, city))
        val havdalah = assertNotNull(ZmanimEngine.havdalahOn(friday.plusDays(1), city))
        assertFalse(quiet(candles.minusSeconds(1)))
        assertTrue(quiet(candles))
        assertTrue(quiet(havdalah.minusSeconds(1)))
        assertFalse(quiet(havdalah))
    }

    @Test fun earlyFridaySilenceDoesNotChangeCandleTime() {
        val candles = assertNotNull(ZmanimEngine.candleLightingOn(friday, city))
        val earlyStart = candles.minusMinutes(30)
        assertFalse(quiet(earlyStart.minusSeconds(1), 30))
        assertTrue(quiet(earlyStart, 30))
        assertEquals(candles, ZmanimEngine.candleLightingOn(friday, city))
        val upcoming = ShabbatGuard.nextQuietStart(earlyStart.minusMinutes(10), city.name,
            city.lat, city.lon, city.zone(), city.isIsrael, false, 30)
        assertEquals(earlyStart, upcoming)
    }

    @Test fun alarmPlanningCannotScheduleWithinEarlySilence() {
        val candles = assertNotNull(ZmanimEngine.candleLightingOn(friday, city))
        val withinEarly = candles.minusMinutes(15)
        val spec = AlarmSpec(true, false, withinEarly.hour, withinEarly.minute, 30,
            (0..6).toSet(), false)
        val next = assertNotNull(AlarmRules.nextRing(spec, friday.atStartOfDay(city.zone()), city, 30))
        assertEquals(friday.plusDays(2), next.toLocalDate())
    }

    @Test fun aKnownSunsetRollsTheDateEvenIfSunriseIsMissing() {
        val date = LocalDate.of(2027, 2, 1)
        val sunset = date.atTime(17, 12).atZone(city.zone())
        assertEquals(date, ZmanimEngine.dateAfterKnownSunset(date, sunset.minusSeconds(1), sunset))
        assertEquals(date.plusDays(1), ZmanimEngine.dateAfterKnownSunset(date, sunset, sunset))
        assertEquals(date, ZmanimEngine.dateAfterKnownSunset(date, sunset.plusHours(2), null))
    }

    @Test fun anUnknownSystemTimeZoneHasNoTrustedCityMatch() {
        assertNull(cityMatchForZone(ZoneId.of("Pacific/Chatham")))
    }

    @Test fun kashrutWaitingTimeIsAlwaysSixHoursAndLegacyIsReset() {
        assertEquals(6L, KashrutPolicy.WAIT_HOURS)
        assertEquals(21_600_000L, KashrutPolicy.deadline(0L))
        assertTrue(KashrutPolicy.resetLegacyTimer(0, 100L))
        assertFalse(KashrutPolicy.resetLegacyTimer(1, 100L))
        assertFalse(KashrutPolicy.resetLegacyTimer(0, 0L))
    }
}
