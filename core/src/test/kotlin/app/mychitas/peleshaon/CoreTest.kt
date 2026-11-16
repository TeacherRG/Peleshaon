package app.mychitas.peleshaon

import app.mychitas.peleshaon.alarm.AlarmRules
import app.mychitas.peleshaon.alarm.AlarmSpec
import app.mychitas.peleshaon.alarm.ShabbatGuard
import app.mychitas.peleshaon.domain.CITIES
import app.mychitas.peleshaon.domain.DashState
import app.mychitas.peleshaon.domain.Dial
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.cityById
import app.mychitas.peleshaon.domain.cityForZone
import app.mychitas.peleshaon.domain.findCities
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val vienna = cityById("Europe/Vienna")!!
    private val zone = vienna.zone()

    private fun at(d: Int, h: Int, m: Int = 0, month: Int = 11, year: Int = 2026) =
        ZonedDateTime.of(year, month, d, h, m, 0, 0, zone)

    private fun day(t: ZonedDateTime) =
        ZmanimEngine.calculateForMoment(t, vienna.name, vienna.lat, vienna.lon, 0.0, zone, false)

    private fun quiet(t: ZonedDateTime, cholHamoed: Boolean = false) =
        ShabbatGuard.checkMoment(t, vienna.name, vienna.lat, vienna.lon, zone, vienna.isIsrael, cholHamoed).isQuietTime

    @Test
    fun jewishDateFromGregorian() {
        assertEquals("6 Kislev, 5787", day(at(16, 10)).jewishDateTranslit)
        // после заката — уже следующий еврейский день
        assertEquals("7 Kislev, 5787", day(at(16, 18)).jewishDateTranslit)
    }

    @Test
    fun zmanimInOrderAndRoundedAsOnTheSite() {
        val r = day(at(16, 10))
        assertEquals(ZmanType.entries.size, r.items.size)
        r.items.zipWithNext().forEach { (a, b) -> assertTrue(a.exactTime.isBefore(b.exactTime), "${a.type} < ${b.type}") }
        assertEquals(LocalTime.of(9, 18), r.time(ZmanType.SOF_ZMAN_SHMA)!!.roundedTime.toLocalTime())
        // «не позже» и восход — вниз, остальное — вверх
        for (z in r.items) {
            val down = z.roundedTime.isAfter(z.exactTime).not()
            if (z.type.roundDown) assertTrue(down, "${z.type} вниз") else assertTrue(!z.roundedTime.isBefore(z.exactTime), "${z.type} вверх")
        }
    }

    @Test
    fun northernSummerHasNoDawnButHasTheRest() {
        val london = cityById("Europe/London")!!
        val june = ZonedDateTime.of(2027, 6, 21, 10, 0, 0, 0, london.zone())
        val r = ZmanimEngine.calculateForMoment(june, london.name, london.lat, london.lon, 0.0, london.zone(), false)
        assertNull(r.time(ZmanType.ALOT))
        assertNotNull(r.time(ZmanType.SOF_ZMAN_SHMA))
        assertEquals(ZmanType.entries.size - 1, r.items.size)
    }

    @Test
    fun everyDayOfYearForEveryCity() {
        for (city in CITIES) {
            val z = city.zone()
            var d = LocalDate.of(2026, 9, 1)
            while (d.isBefore(LocalDate.of(2027, 9, 1))) {
                val t = ZonedDateTime.of(d, LocalTime.of(10, 0), z)
                val r = ZmanimEngine.calculateForMoment(t, city.name, city.lat, city.lon, 0.0, z, city.isIsrael)
                assertTrue(r.jewishDateTranslit.isNotEmpty(), "${city.id} $t")
                val shown = r.items.associateBy { it.type }
                Dial.ring(t, z, shown[ZmanType.SUNRISE]?.exactTime, shown[ZmanType.SUNSET]?.exactTime, shown[ZmanType.ALOT]?.exactTime, shown[ZmanType.TZAIT]?.exactTime)
                d = d.plusDays(7)
            }
        }
    }

    @Test
    fun citiesAsOnTheSite() {
        assertEquals(196, CITIES.size)
        assertEquals(CITIES.size, CITIES.map { it.id }.toSet().size)
        CITIES.forEach { assertNotNull(app.mychitas.peleshaon.domain.resolveZone(it.zoneId), it.zoneId) }
        assertEquals("Вена", cityForZone(ZoneId.of("Europe/Vienna")).nameRu)
        assertEquals("Europe/Vienna", cityForZone(ZoneId.of("Pacific/Nowhere".let { "UTC" })).id)
        assertEquals("tzfat", findCities("цфат").first().id)
        assertEquals("Europe/Vienna", findCities("wien").first().id)
        assertTrue(cityById("Tel Aviv")!!.isIsrael) // старое имя из настроек
    }

    @Test
    fun ringOfTheDay() {
        val now = at(16, 10)
        val r = day(now)
        val ring = Dial.ring(now, zone, r.time(ZmanType.SUNRISE)!!.exactTime, r.time(ZmanType.SUNSET)!!.exactTime, r.time(ZmanType.ALOT)?.exactTime, r.time(ZmanType.TZAIT)?.exactTime)!!
        assertEquals(96, ring.dashes.size)
        assertEquals(1, ring.dashes.count { it.state == DashState.NOW })
        // 10:00 в ноябре: восход 07:03, закат 16:15 — день ~9,2 ч, прошло ~2,95 ч → галахический час 3 (с нуля)
        assertEquals(3, ring.hour)
        // ночные штрихи — тоньше дневных: зимой ночь длиннее, значит, наоборот — толще
        assertTrue(ring.dashes[60].width > ring.dashes[10].width)
        // полдень — около верха
        assertTrue(ring.noon < 0.02 || ring.noon > 0.98)
        assertEquals(12, Dial.TZIRUFIM.size)
        assertEquals("י-ה-ו-ה", Dial.TZIRUFIM[0])
    }

    @Test
    fun shabbatOfTheWeek() {
        val sh = ZmanimEngine.shabbatOf(at(16, 10), vienna)
        assertEquals(LocalDate.of(2026, 11, 20), sh.friday)
        assertEquals(DayOfWeek.FRIDAY, sh.candles!!.dayOfWeek)
        // в субботу днём — сегодняшний, после исхода — следующий
        assertEquals(LocalDate.of(2026, 11, 20), ZmanimEngine.shabbatOf(at(21, 12), vienna).friday)
        assertEquals(LocalDate.of(2026, 11, 27), ZmanimEngine.shabbatOf(at(21, 20), vienna).friday)
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

    @Test
    fun alarmRings() {
        val study = AlarmSpec(on = true, byShma = false, hour = 20, minute = 0, before = 30, days = AlarmSpec.WEEKDAYS, chm = false)
        // понедельник 21:00 → вторник 20:00
        assertEquals(at(17, 20), AlarmRules.nextRing(study, at(16, 21), vienna))
        // пятница 21:00 → суббота не в днях, воскресенье 20:00
        assertEquals(at(22, 20), AlarmRules.nextRing(study, at(20, 21), vienna))
        // каждый день: в пятницу в 20:00 — Шабат, звонка нет; в субботу в 20:00 Шабат уже вышел (около 17:00)
        val daily = study.copy(days = (0..6).toSet())
        assertEquals(at(21, 20), AlarmRules.nextRing(daily, at(20, 12), vienna))
        // будильник в 10:00 каждый день: суббота пропущена — воскресенье
        assertEquals(at(22, 10), AlarmRules.nextRing(daily.copy(hour = 10), at(20, 12), vienna))
        // за 30 минут до конца Шма (17.11 — 09:19) — 08:49
        val wake = study.copy(byShma = true)
        assertEquals(at(17, 8, 49), AlarmRules.nextRing(wake, at(16, 21), vienna))
        assertNull(AlarmRules.nextRing(study.copy(on = false), at(16, 21), vienna))
        assertNull(AlarmRules.nextRing(study.copy(days = emptySet()), at(16, 21), vienna))
        // Песах выпадает из будильника и показан как пропуск
        val beforePesach = ZonedDateTime.of(2027, 4, 20, 21, 0, 0, 0, zone)
        val skipped = AlarmRules.skipped(daily, beforePesach, vienna)
        assertTrue(skipped.isNotEmpty() && skipped.all { it.reason == "yom_tov" })
    }
}
