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

    @Test fun deadlinesAreNeverDisplayedAfterTheirExactMoment() {
        val date = LocalDate.of(2026, 11, 20)
        val exact = ZmanimEngine.itemsOf(date, berlin)
            .first { it.type == ZmanType.SOF_ZMAN_SHMA }
        assertTrue(!exact.roundedTime.isAfter(exact.exactTime))
        // Sunrise is the beginning of an allowed interval: do not render before it.
        val start = ZmanimEngine.itemsOf(date, berlin).first { it.type == ZmanType.SUNRISE }
        assertTrue(!start.roundedTime.isBefore(start.exactTime))
    }

    @Test fun preservingCurrentNoInternetModelAndSixHourKashrutPolicy() {
        assertEquals(6L, app.mychitas.peleshaon.alarm.KashrutPolicy.WAIT_HOURS)
    }
}
