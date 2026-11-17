package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.JewishCal
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoladTest {
    @Test
    fun shabbatMevarchim() {
        // 25 кислева 5787 (Шабат 5.12.2026) — благословляют тевет
        val m = JewishCal.mevarchim(LocalDate.of(2026, 12, 4), false)
        assertNotNull(m)
        assertEquals(LocalDate.of(2026, 12, 5), m.shabbat)
        assertEquals(10, m.month) // тевет
        assertTrue(abs(m.moladDate.toEpochDay() - m.roshChodesh.last().toEpochDay()) <= 2, "$m")
        assertTrue(m.moladHour in 0..23 && m.moladMinute in 0..59 && m.moladChalakim in 0..17)
        // неделя 11 кислева — не мевархим
        assertNull(JewishCal.mevarchim(LocalDate.of(2026, 11, 18), false))
        // в календаре — событие у самой субботы
        assertTrue(JewishCal.day(LocalDate.of(2026, 12, 5), false).events.any { it.key == "mevarchim" && it.n == 10 })
        // год: каждый месяц, кроме тишрея, — один Шабат мевархим, всегда суббота
        var d = LocalDate.of(2026, 9, 13)
        val found = mutableListOf<Int>()
        while (d.isBefore(LocalDate.of(2027, 10, 2))) {
            if (d.dayOfWeek == DayOfWeek.SATURDAY) JewishCal.mevarchim(d, false)?.let {
                assertEquals(d, it.shabbat); found += it.month
            }
            d = d.plusDays(1)
        }
        assertEquals(found.distinct().size, found.size, "по разу: $found")
        assertTrue(7 !in found, "тишрей не благословляют")
        assertTrue(found.size in 11..12, "$found")
    }

    @Test
    fun seventhHourOfShabbatNight() {
        val vienna = app.mychitas.peleshaon.domain.cityById("Europe/Vienna")!!
        val fri = LocalDate.of(2026, 11, 20)
        val (a, b) = app.mychitas.peleshaon.domain.ZmanimEngine.seventhHourOfShabbatNight(fri, vienna)!!
        val set = app.mychitas.peleshaon.domain.ZmanimEngine.sunsetOf(fri, vienna)!!
        // зимой ночь в Вене ≈ 15 ч: 7-й час начинается около полуночи и длится больше часа
        assertTrue(a.isAfter(set.plusHours(6)) && a.isBefore(set.plusHours(9)), "$a")
        val min = java.time.Duration.between(a, b).toMinutes()
        assertTrue(min in 70..85, "$min мин")
    }
}
