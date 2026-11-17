package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.AlarmRules
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class DstAlarmTest {
    private val vienna = ZoneId.of("Europe/Vienna")
    @Test fun nonexistentSpringTimeMovesToStartOfValidHour() {
        val date = LocalDate.of(2027, 3, 28)
        val actual = AlarmRules.resolveWallTime(date, LocalTime.of(2, 30), vienna)
        assertEquals(LocalTime.of(3, 0), actual.toLocalTime())
        assertEquals(ZoneOffset.ofHours(2), actual.offset)
    }
    @Test fun repeatedAutumnTimeRingsAtEarlierOccurrenceOnce() {
        val date = LocalDate.of(2027, 10, 31)
        val actual = AlarmRules.resolveWallTime(date, LocalTime.of(2, 30), vienna)
        assertEquals(ZoneOffset.ofHours(2), actual.offset)
        assertEquals(LocalTime.of(2, 30), actual.toLocalTime())
    }
}
