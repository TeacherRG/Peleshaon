package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.ShabbatGuard
import app.mychitas.peleshaon.domain.DEFAULT_CITIES
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    private val vienna = DEFAULT_CITIES.first { it.name == "Vienna" }
    private val zone = ZoneId.of(vienna.zoneId)

    private fun at(d: Int, h: Int, m: Int = 0, month: Int = 11) =
        ZonedDateTime.of(2026, month, d, h, m, 0, 0, zone)

    private fun quiet(t: ZonedDateTime, cholHamoed: Boolean = false) =
        ShabbatGuard.checkMoment(t, vienna.name, vienna.lat, vienna.lon, zone, vienna.isIsrael, cholHamoed).isQuietTime

    @Test
    fun jewishDateFromGregorian() {
        val r = ZmanimEngine.calculateForMoment(at(16, 10), vienna.name, vienna.lat, vienna.lon, 0.0, zone, false)
        assertEquals("6 Kislev, 5787", r.jewishDateTranslit)
        // после заката — уже следующий еврейский день
        val evening = ZmanimEngine.calculateForMoment(at(16, 18), vienna.name, vienna.lat, vienna.lon, 0.0, zone, false)
        assertEquals("7 Kislev, 5787", evening.jewishDateTranslit)
    }

    @Test
    fun zmanimInOrder() {
        val r = ZmanimEngine.calculateForMoment(at(16, 10), vienna.name, vienna.lat, vienna.lon, 0.0, zone, false)
        assertEquals(ZmanType.entries.size, r.items.size)
        r.items.zipWithNext().forEach { (a, b) -> assertTrue(a.exactTime.isBefore(b.exactTime), "${a.type} < ${b.type}") }
        val shma = r.items.first { it.type == ZmanType.SOF_ZMAN_SHMA }.roundedTime.toLocalTime()
        assertEquals(LocalTime.of(9, 18), shma)
    }

    @Test
    fun everyDayOfYearForAllCities() {
        for (city in DEFAULT_CITIES) {
            val z = ZoneId.of(city.zoneId)
            var d = LocalDate.of(2026, 9, 1)
            while (d.isBefore(LocalDate.of(2027, 9, 1))) {
                for (h in listOf(3, 10, 23)) {
                    val t = ZonedDateTime.of(d, LocalTime.of(h, 0), z)
                    val r = ZmanimEngine.calculateForMoment(t, city.name, city.lat, city.lon, 0.0, z, city.isIsrael)
                    assertTrue(r.jewishDateTranslit.isNotEmpty(), "${city.name} $t")
                    ShabbatGuard.checkMoment(t, city.name, city.lat, city.lon, z, city.isIsrael, true)
                }
                d = d.plusDays(1)
            }
        }
    }

    @Test
    fun shabbatIsQuiet() {
        assertFalse(quiet(at(20, 15)))       // пятница днём
        assertTrue(quiet(at(20, 17)))        // после зажигания свечей
        assertTrue(quiet(at(21, 10)))        // Шабат
        assertFalse(quiet(at(21, 18)))       // после исхода
        assertFalse(quiet(at(22, 10)))       // воскресенье
    }

    @Test
    fun yomTovIsQuiet() {
        // Песах 5787: 15 нисана = 22.04.2027 (четверг), вне Израиля — два дня
        val pesach = ZonedDateTime.of(2027, 4, 22, 10, 0, 0, 0, zone)
        assertTrue(quiet(pesach))
        assertTrue(quiet(pesach.plusDays(1)))
        // Хол ґа-моэд — только по галочке
        val chol = ZonedDateTime.of(2027, 4, 25, 10, 0, 0, 0, zone)
        assertFalse(quiet(chol))
        assertTrue(quiet(chol, cholHamoed = true))
    }
}
