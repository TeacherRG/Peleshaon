package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CalKind
import app.mychitas.peleshaon.domain.JewishCal
import app.mychitas.peleshaon.domain.KiddushLevanaRules
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.cityById
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
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
        // выход звёзд: 8,5° — позже, чем по Алтер Ребе
        assertTrue(li.getValue(ZmanType.TZAIT).isAfter(ch.getValue(ZmanType.TZAIT)))
        // порядок времён дня сохраняется у всех
        for (t in listOf(ch, li, se)) {
            val order = ZmanType.entries.map { t.getValue(it) }
            assertEquals(order.sorted(), order, "порядок $t")
        }
    }

    @Test
    fun havdalahAndDatesFollowTheCommunity() {
        val sat = LocalDate.of(2026, 11, 21)
        ZmanimEngine.minhag = Minhag.CHABAD
        val ch = ZmanimEngine.havdalahOn(sat, vienna)!!
        val tzait = ZmanimEngine.itemsOf(sat, vienna)
            .single { it.type == ZmanType.TZAIT }.exactTime
        assertTrue(ch.isAfter(tzait), "Havdalah is later than ordinary Chabad tzait")
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
