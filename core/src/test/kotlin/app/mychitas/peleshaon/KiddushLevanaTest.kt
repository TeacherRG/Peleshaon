package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.KiddushLevanaRules
import app.mychitas.peleshaon.domain.MoonTracker
import app.mychitas.peleshaon.domain.cityById
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KiddushLevanaTest {
    private val vienna = cityById("Europe/Vienna")!!
    private val jerusalem = cityById("Asia/Jerusalem")!!

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC)
    private fun near(exp: Double, got: Double, tol: Double, what: String) {
        val diff = abs(((got - exp) % 360 + 540) % 360 - 180)
        assertTrue(diff <= tol, "$what: ожидалось $exp, получилось $got")
    }

    /** Эталон — библиотека ephem (Python), без рефракции. */
    @Test
    fun moonMatchesEphem() {
        data class Ref(val lat: Double, val lon: Double, val at: ZonedDateTime, val az: Double, val alt: Double, val phase: Double)
        val refs = listOf(
            Ref(48.2082, 16.3738, utc(2026, 11, 17, 18, 0), 194.37, 28.11, 52.7),   // Вена
            Ref(31.7683, 35.2137, utc(2026, 11, 20, 17, 30), 164.14, 62.57, 80.9),  // Йерушалаим
            Ref(40.7128, -74.006, utc(2027, 1, 16, 23, 15), 165.82, 68.28, 62.1),   // Нью-Йорк
            Ref(55.7558, 37.6173, utc(2026, 12, 19, 16, 40), 167.98, 48.15, 75.0)   // Москва
        )
        for (r in refs) {
            val m = MoonTracker.position(r.at, r.lat, r.lon)
            near(r.az, m.azimuth, 0.5, "азимут ${r.at}")
            near(r.alt, m.altitude, 0.5, "высота ${r.at}")
            assertTrue(abs(m.illumination * 100 - r.phase) < 1.0, "освещённость ${r.at}: ${m.illumination}")
        }
        // юлианская дата: полдень 1.1.2000 UT
        assertEquals(2451545.0, MoonTracker.julianDate(utc(2000, 1, 1, 12, 0)), 1e-9)
    }

    @Test
    fun kiddushLevanaKislev5787() {
        // 1 кислева 5787 — 11.11.2026; окно — через 7 суток после молада, до середины месяца
        val (start, end) = KiddushLevanaRules.window(LocalDate.of(2026, 11, 18), vienna)
        assertTrue(start.toLocalDate() in LocalDate.of(2026, 11, 16)..LocalDate.of(2026, 11, 18), "начало $start")
        assertTrue(end.toLocalDate() in LocalDate.of(2026, 11, 24)..LocalDate.of(2026, 11, 26), "конец $end")
        // днём — нет
        assertNull(KiddushLevanaRules.now(utc(2026, 11, 19, 11, 0), vienna))
        // вечер вторника 17.11 после выхода звёзд (если окно уже открыто) / среды 18.11 — есть, Луна над горизонтом
        val wed = KiddushLevanaRules.now(utc(2026, 11, 18, 18, 0), vienna)
        assertNotNull(wed)
        assertTrue(wed.moon.aboveHorizon && wed.jewishDay.month == 9 && wed.jewishDay.day == 9, "$wed")
        // вечер пятницы 20.11 — Шабат: нет; суббота до исхода — нет, после исхода — да
        assertNull(KiddushLevanaRules.now(utc(2026, 11, 20, 18, 0), vienna))
        assertNull(KiddushLevanaRules.now(utc(2026, 11, 21, 15, 30), vienna))
        assertNotNull(KiddushLevanaRules.now(utc(2026, 11, 21, 17, 30), vienna))
        // после середины месяца — нет
        assertNull(KiddushLevanaRules.now(utc(2026, 11, 27, 19, 0), vienna))
    }

    @Test
    fun remindersSkipRestDays() {
        // напоминания в кислеве: не в пятницу вечером; в субботу — после исхода Шабата
        var at = KiddushLevanaRules.nextReminder(utc(2026, 11, 15, 12, 0), vienna)
        val got = mutableListOf<ZonedDateTime>()
        while (at != null && got.size < 12 && at.isBefore(utc(2026, 11, 28, 0, 0))) {
            got += at; at = KiddushLevanaRules.nextReminder(at.plusMinutes(1), vienna)
        }
        assertTrue(got.size >= 5, "$got")
        assertTrue(got.none { it.toLocalDate() == LocalDate.of(2026, 11, 20) }, "пятница: $got")
        val sat = got.firstOrNull { it.toLocalDate() == LocalDate.of(2026, 11, 21) }
        assertNotNull(sat)
        assertTrue(KiddushLevanaRules.now(sat, vienna) != null, "в субботу — после исхода: $sat")
        for (g in got) assertNotNull(KiddushLevanaRules.now(g, vienna), "в момент напоминания можно: $g")
        // 9 ава 5787 (вечер 9 ава — 10 ава по еврейскому счёту) — можно; до него в ав — нельзя
        assertTrue(!KiddushLevanaRules.eveningAllowed(LocalDate.of(2027, 8, 10), jerusalem) ||
            KiddushLevanaRules.window(LocalDate.of(2027, 8, 11), jerusalem).first.isAfter(utc(2027, 8, 11, 0, 0)))
        // год вперёд — каждое напоминание в окне и вне Шабата
        var t = utc(2026, 11, 17, 0, 0)
        repeat(13) {
            val r = KiddushLevanaRules.nextReminder(t, jerusalem) ?: return@repeat
            assertNotNull(KiddushLevanaRules.now(r, jerusalem), "Йерушалаим $r")
            t = r.plusDays(20)
        }
    }
}
