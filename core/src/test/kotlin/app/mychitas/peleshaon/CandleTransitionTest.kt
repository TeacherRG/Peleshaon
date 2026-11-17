package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CandleTransition
import app.mychitas.peleshaon.domain.JewishCal
import app.mychitas.peleshaon.domain.ZmanimEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.DayOfWeek
import java.time.LocalDate

class CandleTransitionTest {
    @AfterTest fun clearOffset() { ZmanimEngine.candleOffsetOverride = null }

    @Test fun localCandleOffsetsCanBeOverridden() {
        assertEquals(40L, ZmanimEngine.candleMinutesFor("Jerusalem"))
        assertEquals(30L, ZmanimEngine.candleMinutesFor("Haifa"))
        assertEquals(18L, ZmanimEngine.candleMinutesFor("Vienna"))
        ZmanimEngine.candleOffsetOverride = 20L
        assertEquals(20L, ZmanimEngine.candleMinutesFor("Jerusalem"))
    }

    @Test fun yomTovFridayToShabbatIsBeforeSunsetNotAfterNightfall() {
        val start = LocalDate.of(2026, 1, 1)
        val friday = (0L..2000L).map { start.plusDays(it) }.firstOrNull {
            it.dayOfWeek == DayOfWeek.FRIDAY && JewishCal.day(it, false).holy &&
                JewishCal.day(it, false).candles && JewishCal.day(it.plusDays(1), false).holy
        }
        val found = assertNotNull(friday, "test calendar contains a Yom Tov Friday")
        assertEquals(CandleTransition.BEFORE_SUNSET, JewishCal.candleTransition(found, false))
    }

    @Test fun ordinaryFridayIsBeforeSunset() {
        val friday = LocalDate.of(2026, 11, 20)
        assertEquals(CandleTransition.BEFORE_SUNSET, JewishCal.candleTransition(friday, false))
    }

    @Test fun nightfallTransitionRemainsSeparate() {
        val start = LocalDate.of(2026, 1, 1)
        val holyToHoly = (0L..2000L).map { start.plusDays(it) }.firstOrNull {
            it.dayOfWeek != DayOfWeek.FRIDAY && JewishCal.day(it, false).holy &&
                JewishCal.day(it, false).candles && JewishCal.day(it.plusDays(1), false).holy
        }
        assertTrue(holyToHoly != null, "test calendar contains two consecutive holy days")
        assertEquals(CandleTransition.AFTER_NIGHTFALL, JewishCal.candleTransition(holyToHoly!!, false))
    }
}
