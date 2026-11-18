package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CalKind
import app.mychitas.peleshaon.domain.JewishCal
import app.mychitas.peleshaon.domain.KiddushLevanaRules
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.cityById
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MinhagTest {
    private val vienna = cityById("Europe/Vienna")!!

    @AfterTest
    fun back() { ZmanimEngine.minhag = Minhag.CHABAD }

    private fun times(m: Minhag, d: LocalDate): Map<ZmanType, ZonedDateTime> {
        ZmanimEngine.minhag = m
        return ZmanimEngine.itemsOf(d, vienna).associate { it.type to it.exactTime }
    }

    @Test
    fun zmanimFollowTheCommunity() {
        val d = LocalDate.of(2026, 11, 18)
        val ch = times(Minhag.CHABAD, d)
        val li = times(Minhag.LITVISH, d)
        val se = times(Minhag.SEFARDI, d)
        for (t in listOf(ch, li, se)) assertEquals(ZmanType.entries.toSet(), t.keys, "все времена есть")
        // по Маген Авраґаму «Шма» раньше, чем по ГРА; восход и закат у всех одни
        assertTrue(se.getValue(ZmanType.SOF_ZMAN_SHMA).isBefore(li.getValue(ZmanType.SOF_ZMAN_SHMA)))
        assertEquals(ch.getValue(ZmanType.SUNSET), li.getValue(ZmanType.SUNSET))
        // По Chabad.org и ГРА выход звёзд — при 8,5°.
        assertEquals(li.getValue(ZmanType.TZAIT), ch.getValue(ZmanType.TZAIT))
        // порядок времён дня сохраняется у всех
        for (t in listOf(ch, li, se)) {
            val order = ZmanType.entries.map { t.getValue(it) }
            assertEquals(order.sorted(), order, "порядок $t")
        }
    }

    @Test
    fun chabadTimesMatchChabadOrgForBerlinAndVienna() {
        ZmanimEngine.minhag = Minhag.CHABAD
        val berlin = cityById("Europe/Berlin")!!
        val friday = LocalDate.of(2026, 11, 20)
        val saturday = friday.plusDays(1)

        assertEquals(LocalTime.of(15, 49), ZmanimEngine.candleLightingOn(friday, berlin)!!
            .toLocalTime().truncatedTo(ChronoUnit.MINUTES))
        assertEquals(LocalTime.of(17, 2), ZmanimEngine.havdalahOn(saturday, berlin)!!
            .toLocalTime().truncatedTo(ChronoUnit.MINUTES))
        assertEquals(LocalTime.of(15, 53), ZmanimEngine.candleLightingOn(friday, vienna)!!
            .toLocalTime().truncatedTo(ChronoUnit.MINUTES))
        assertEquals(LocalTime.of(17, 1), ZmanimEngine.havdalahOn(saturday, vienna)!!
            .toLocalTime().truncatedTo(ChronoUnit.MINUTES))
    }

    @Test
    fun chabadHavdalahTracksAstronomicalNightfallAcrossSeasons() {
        ZmanimEngine.minhag = Minhag.CHABAD
        val cities = listOf(vienna, cityById("Europe/Berlin")!!)
        val saturdays = listOf(
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 4, 4),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 10, 3)
        )

        for (city in cities) {
            for (date in saturdays) {
                val tzeit = ZmanimEngine.itemsOf(date, city)
                    .single { it.type == ZmanType.TZAIT }.roundedTime
                assertEquals(tzeit, ZmanimEngine.havdalahOn(date, city),
                    "Havdalah must follow local astronomical nightfall: ${city.name} $date")
            }
        }
    }

    @Test
    fun havdalahAndDatesFollowTheCommunity() {
        val sat = LocalDate.of(2026, 11, 21)
        ZmanimEngine.minhag = Minhag.CHABAD
        val ch = ZmanimEngine.havdalahOn(sat, vienna)!!
        val chabadTzeit = ZmanimEngine.itemsOf(sat, vienna)
            .single { it.type == ZmanType.TZAIT }.roundedTime
        assertEquals(chabadTzeit, ch, "Исход Хабада совпадает с астрономическим выходом звёзд")
        ZmanimEngine.minhag = Minhag.SEFARDI
        assertTrue(ZmanimEngine.havdalahOn(sat, vienna)!!.isAfter(ch), "Рабейну Там позже")
        // даты Хабада — только у Хабада
        ZmanimEngine.minhag = Minhag.LITVISH
        assertTrue(JewishCal.day(LocalDate.of(2026, 11, 20), false).events.none { it.kind == CalKind.CHABAD })
        ZmanimEngine.minhag = Minhag.CHABAD
        assertTrue(JewishCal.day(LocalDate.of(2026, 11, 20), false).events.any { it.kind == CalKind.CHABAD })
        // Кидуш Левана: у литовских — с 3 суток, раньше, чем с 7
        val d = LocalDate.of(2026, 11, 18)
        val chStart = KiddushLevanaRules.window(d, vienna).first
        ZmanimEngine.minhag = Minhag.LITVISH
        assertTrue(KiddushLevanaRules.window(d, vienna).first.isBefore(chStart))
        ZmanimEngine.minhag = Minhag.SEFARDI
        assertTrue(KiddushLevanaRules.window(d, vienna).second.isAfter(ZonedDateTime.of(2026, 11, 24, 0, 0, 0, 0, ZoneOffset.UTC)))
    }
}
