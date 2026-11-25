package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimConfig
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.ZmanimService
import app.mychitas.peleshaon.domain.cityById
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Alter Rebbe: mincha gedola is half an hour after chatzot, using the LONGER half hour —
 * half a proportional hour in summer, 30 ordinary minutes in winter.
 */
class MinchaGedolaAlterRebbeTest {
    private val vienna = requireNotNull(cityById("Europe/Vienna"))

    @Test fun winterUsesThirtyClockMinutes() {
        val z = ZmanimService.calculate(LocalDate.of(2026, 12, 21), ZmanimConfig(vienna))
        val chatzot = assertNotNull(z.chatzot)
        assertEquals(chatzot.plus(Duration.ofMinutes(30)), z.minchaGedolaAlterRebbe)
        // the ordinary (proportional) half hour is shorter in winter
        assertTrue(assertNotNull(z.minchaGedola).isBefore(assertNotNull(z.minchaGedolaAlterRebbe)))
    }

    @Test fun summerUsesHalfProportionalHour() {
        val z = ZmanimService.calculate(LocalDate.of(2026, 6, 21), ZmanimConfig(vienna))
        val chatzot = assertNotNull(z.chatzot)
        val ar = assertNotNull(z.minchaGedolaAlterRebbe)
        assertTrue(ar.isAfter(chatzot.plus(Duration.ofMinutes(30))))
        // equals the ordinary Baal Hatanya mincha gedola (within KosherJava's millisecond rounding)
        val diff = Duration.between(assertNotNull(z.minchaGedola), ar).abs()
        assertTrue(diff < Duration.ofSeconds(1), "diff $diff")
    }

    @Test fun neverEarlierThanOrdinaryAllYear() {
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            val z = ZmanimService.calculate(d, ZmanimConfig(vienna))
            val ar = assertNotNull(z.minchaGedolaAlterRebbe, "$d")
            assertFalse(ar.isBefore(assertNotNull(z.minchaGedola).minusSeconds(1)), "$d")
            assertFalse(ar.isBefore(assertNotNull(z.chatzot).plus(Duration.ofMinutes(30))), "$d")
            d = d.plusDays(1)
        }
    }

    @Test fun calendarAndTimesListShowBothMinchaGedolaAndCalendarExtras() {
        val types = ZmanimEngine.itemsOf(LocalDate.of(2026, 12, 21), vienna).map { it.type }
        for (t in listOf(ZmanType.MINCHA_GEDOLA, ZmanType.MINCHA_GEDOLA_AR, ZmanType.MISHEYAKIR, ZmanType.MINCHA_KETANA))
            assertTrue(t in types, "$t missing")
        assertFalse(ZmanType.BIUR_CHAMETZ in types)
        // Erev Pesach 5787 = 2027-04-21
        assertTrue(ZmanType.BIUR_CHAMETZ in ZmanimEngine.itemsOf(LocalDate.of(2027, 4, 21), vienna).map { it.type })
    }

    @Test fun itemsAreInChronologicalOrder() {
        val items = ZmanimEngine.itemsOf(LocalDate.of(2026, 6, 21), vienna)
        assertEquals(items.sortedBy { it.exactTime }, items)
    }
}
