package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanimConfig
import app.mychitas.peleshaon.domain.ZmanimService
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.cityById
import com.kosherjava.zmanim.ComprehensiveZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test-first contract. Written before implementing the 3.1.0 adapter.
 * The reference list deliberately includes BERLIN.
 */
class Zmanim31MigrationTest {
    private val berlin = requireNotNull(cityById("Europe/Berlin"))
    private val vienna = requireNotNull(cityById("Europe/Vienna"))

    @Test fun newApiAndImmutableAdapterAgreeForBerlin() {
        val date = LocalDate.of(2026, 11, 21)
        val raw = ComprehensiveZmanimCalendar(
            GeoLocation("Berlin", berlin.lat, berlin.lon, 0.0, berlin.zone())
        ).apply { localDate = date }
        val actual = ZmanimService.calculate(date, ZmanimConfig(berlin, Minhag.CHABAD))
        assertEquals(raw.alosBaalHatanya, actual.dawn)
        assertEquals(raw.tzaisBaalHatanya, actual.ordinaryTzait)
        assertEquals(raw.tzaisGeonim8Point5Degrees, actual.shabbatEnd)
        assertEquals("chabad.tzait.6deg", actual.methodIds["ordinaryTzait"])
        assertEquals("chabad.havdalah.8_5deg", actual.methodIds["shabbatEnd"])
    }

    @Test fun berlinOrdinaryNightfallPrecedesShabbatEndAcrossSeasons() {
        for (date in listOf(
            LocalDate.of(2026, 1, 3),
            LocalDate.of(2026, 4, 4),
            LocalDate.of(2026, 7, 4),
            LocalDate.of(2026, 10, 3)
        )) {
            val z = ZmanimService.calculate(date, ZmanimConfig(berlin, Minhag.CHABAD))
            assertTrue(assertNotNull(z.ordinaryTzait).isBefore(assertNotNull(z.shabbatEnd)),
                "Berlin ${date}: ordinary tzait and Shabbat end must remain distinct")
        }
    }

    @Test fun noFixedFiftyMinutesForSummerBerlin() {
        val date = LocalDate.of(2026, 6, 20)
        val z = ZmanimService.calculate(date, ZmanimConfig(berlin, Minhag.CHABAD))
        val interval = Duration.between(assertNotNull(z.sunset), assertNotNull(z.shabbatEnd))
        assertTrue(interval.toMinutes() > 50, "Summer Berlin has a longer 8.5-degree twilight")
    }

    @Test fun cityDefaultsAndOverridesAreExplicit() {
        val jerusalem = requireNotNull(cityById("Asia/Jerusalem"))
        val telAviv = requireNotNull(cityById("Asia/Tel_Aviv"))
        val date = LocalDate.of(2026, 11, 20)
        assertEquals(40L, ZmanimService.candleMinutes(ZmanimConfig(jerusalem, Minhag.CHABAD)))
        assertEquals(22L, ZmanimService.candleMinutes(ZmanimConfig(telAviv, Minhag.CHABAD)))
        assertEquals(18L, ZmanimService.candleMinutes(ZmanimConfig(berlin, Minhag.CHABAD)))
        val selected = ZmanimService.calculate(date, ZmanimConfig(jerusalem, Minhag.CHABAD, 18L))
        assertEquals(assertNotNull(selected.sunset).minus(Duration.ofMinutes(18)),
            selected.candleLighting)
    }

    @Test fun immutableProfilesDoNotBleedIntoOtherCalls() {
        val date = LocalDate.of(2026, 11, 21)
        val before = ZmanimService.calculate(date, ZmanimConfig(vienna, Minhag.CHABAD))
        val other = ZmanimService.calculate(date, ZmanimConfig(vienna, Minhag.SEFARDI))
        val after = ZmanimService.calculate(date, ZmanimConfig(vienna, Minhag.CHABAD))
        assertEquals(before, after)
        assertFalse(before.ordinaryTzait == other.ordinaryTzait)
        assertTrue(assertNotNull(other.shabbatEnd).isAfter(assertNotNull(other.ordinaryTzait)))
    }

    @Test fun sevenReferenceCitiesAcrossAllFourSeasons() {
        val cities = listOf(
            requireNotNull(cityById("Asia/Jerusalem")),
            requireNotNull(cityById("dnipro")),
            vienna,
            berlin,
            requireNotNull(cityById("Europe/Vilnius")),
            requireNotNull(cityById("Europe/London")),
            CityOption("tromso-test", "Tromso", "Тромсё", null,
                69.6492, 18.9553, "Europe/Oslo", false)
        )
        val dates = listOf(
            LocalDate.of(2026, 1, 15),
            LocalDate.of(2026, 4, 15),
            LocalDate.of(2026, 6, 20),
            LocalDate.of(2026, 10, 15)
        )
        assertEquals(7, cities.size, "Berlin must be an explicit reference city")
        for (city in cities) for (date in dates) {
            val z = ZmanimService.calculate(date, ZmanimConfig(city, Minhag.CHABAD))
            if (z.sunrise != null && z.sunset != null)
                assertTrue(z.sunrise.isBefore(z.sunset), "${city.name} $date")
            if (z.ordinaryTzait != null && z.sunset != null)
                assertTrue(z.ordinaryTzait.isAfter(z.sunset), "${city.name} $date")
            if (z.shabbatEnd != null && z.sunset != null)
                assertTrue(z.shabbatEnd.isAfter(z.sunset), "${city.name} $date")
        }
        // Midnight sun: no honest 8.5-degree sunset event exists in Tromso on June 20.
        val tromso = cities.last()
        assertNull(ZmanimService.calculate(LocalDate.of(2026, 6, 20),
            ZmanimConfig(tromso, Minhag.CHABAD)).shabbatEnd)
    }

    @Test fun chabadSummerAlosFallsBackToPreviousSolarMidnight() {
        val summer = LocalDate.of(2026, 6, 19)
        for (id in listOf("Europe/Berlin", "Europe/Vilnius", "Europe/London")) {
            val city = requireNotNull(cityById(id))
            val raw = ZmanimService.buildCalendar(summer, city.name,
                city.lat, city.lon, 0.0, city.zone())
            assertNull(raw.alosBaalHatanya, "$id must exercise the missing-angle branch")
            val expected = ZmanimService.buildCalendar(summer.minusDays(1),
                city.name, city.lat, city.lon, 0.0, city.zone()).solarMidnight
            val snap = ZmanimService.calculate(summer, ZmanimConfig(city, Minhag.CHABAD))
            assertNotNull(expected)
            assertEquals(expected, snap.dawn, "$id: previous night lower solar transit")
            assertEquals("chabad.alos.solar_midnight_when_16_9deg_unreachable",
                snap.methodIds["dawn"])
            assertTrue(snap.dawn!!.isBefore(requireNotNull(snap.sunrise)))
        }
    }

    @Test fun normalWinterAlosStillUses16Point9Degrees() {
        val winter = LocalDate.of(2026, 11, 20)
        for (id in listOf("Europe/Berlin", "Europe/Vilnius", "Europe/London")) {
            val city = requireNotNull(cityById(id))
            val cal = ZmanimService.buildCalendar(winter, city.name, city.lat,
                city.lon, 0.0, city.zone())
            val snap = ZmanimService.calculate(winter, ZmanimConfig(city, Minhag.CHABAD))
            assertEquals(cal.alosBaalHatanya, snap.dawn)
            assertEquals("chabad.alos.16_9deg", snap.methodIds["dawn"])
        }
    }

    @Test fun missingNightfallAndShabbatEndAreNotInvented() {
        val yakutsk = requireNotNull(cityById("Asia/Yakutsk"))
        val june = LocalDate.of(2026, 6, 19)
        val snap = ZmanimService.calculate(june, ZmanimConfig(yakutsk, Minhag.CHABAD))
        assertNull(snap.ordinaryTzait, "No fabricated 6-degree nightfall")
        // An absent 8.5-degree event remains absent; no fixed-minute substitute.
        val saturday = ZmanimService.calculate(june.plusDays(1),
            ZmanimConfig(yakutsk, Minhag.CHABAD))
        assertNull(saturday.shabbatEnd)
    }

    @Test fun deadlinesAreNeverDisplayedAfterTheirExactMoment() {
        val date = LocalDate.of(2026, 11, 20)
        val exact = ZmanimEngine.itemsOf(date, berlin)
            .first { it.type == ZmanType.SOF_ZMAN_SHMA }
        assertTrue(!exact.roundedTime.isAfter(exact.exactTime))
        // Sunrise is the beginning of an allowed interval: do not render before it.
        val start = ZmanimEngine.itemsOf(date, berlin).first { it.type == ZmanType.SUNRISE }
        assertTrue(!start.roundedTime.isBefore(start.exactTime))
    }

    @Test fun chabadDawnAndMiddayNeverRoundLate() {
        val simferopol = requireNotNull(cityById("Europe/Simferopol"))
        val events = ZmanimEngine.itemsOf(LocalDate.of(2026, 11, 13), simferopol)
        val dawn = events.first { it.type == ZmanType.ALOT }
        val noon = events.first { it.type == ZmanType.CHATZOT }
        assertTrue(!dawn.roundedTime.isAfter(dawn.exactTime))
        assertTrue(!noon.roundedTime.isAfter(noon.exactTime))
        assertEquals(java.time.LocalTime.of(6, 2), dawn.roundedTime.toLocalTime())
        assertEquals(java.time.LocalTime.of(12, 27), noon.roundedTime.toLocalTime())
        val sunrise = events.first { it.type == ZmanType.SUNRISE }
        val firstMincha = events.first { it.type == ZmanType.MINCHA_GEDOLA }
        assertTrue(!sunrise.roundedTime.isBefore(sunrise.exactTime))
        assertTrue(!firstMincha.roundedTime.isBefore(firstMincha.exactTime))
    }

    @Test fun deadlineAndEarliestTimeRoundingAcrossSeasons() {
        val ids = listOf("dnipro", "Europe/Berlin", "Asia/Jerusalem",
            "Europe/Kiev", "odesa", "Europe/Simferopol")
        val dates = listOf(LocalDate.of(2026, 3, 27), LocalDate.of(2026, 6, 19),
            LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 20))
        val deadlines = setOf(ZmanType.ALOT, ZmanType.SOF_ZMAN_SHMA,
            ZmanType.SOF_ZMAN_TFILA, ZmanType.CHATZOT, ZmanType.SUNSET)
        val earliest = setOf(ZmanType.SUNRISE, ZmanType.MINCHA_GEDOLA,
            ZmanType.PLAG_HAMINCHA, ZmanType.TZAIT)
        for (id in ids) for (date in dates) {
            val city = requireNotNull(cityById(id))
            for (event in ZmanimEngine.itemsOf(date, city)) {
                if (event.type in deadlines)
                    assertTrue(!event.roundedTime.isAfter(event.exactTime),
                        "${city.id} ${date} ${event.type} rounded after deadline")
                if (event.type in earliest)
                    assertTrue(!event.roundedTime.isBefore(event.exactTime),
                        "${city.id} ${date} ${event.type} rounded before earliest event")
            }
        }
    }

    @Test fun preservingCurrentNoInternetModelAndSixHourKashrutPolicy() {
        assertEquals(6L, app.mychitas.peleshaon.alarm.KashrutPolicy.WAIT_HOURS)
    }
}
