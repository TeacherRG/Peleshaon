package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.ManualLocation
import app.mychitas.peleshaon.domain.cityById
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManualLocationTest {
    @Test fun manualCoordinatesAreValidatedWithoutInternet() {
        val city = assertNotNull(ManualLocation.parse("My village", "48.51", "34.60", "Europe/Kyiv"))
        assertEquals(ManualLocation.ID, city.id)
        assertEquals("Europe/Kyiv", city.zoneId)
        assertEquals(48.51, city.lat)
        assertEquals(34.60, city.lon)
        assertEquals(city.zone(), assertNotNull(ManualLocation.zoneOrNull("Europe/Kyiv")))
    }
    @Test fun commaDecimalAndOldKyivZoneAliasAreAccepted() {
        val city = assertNotNull(ManualLocation.parse("Кам'янське", "48,51134", "34,60210", "Europe/Kiev"))
        assertTrue(city.lat in 48.50..48.52)
        assertEquals(34.6021, city.lon)
        assertNotNull(city.zone())
    }
    @Test fun dangerousInputsDoNotProduceReligiousTimes() {
        for ((latitude,longitude) in listOf(
            "999" to "35", "NaN" to "35", "Infinity" to "35",
            "48" to "999", "" to "35", "abc" to "34"
        )) assertNull(ManualLocation.parse("A location", latitude, longitude, "Europe/Kyiv"))
        assertNull(ManualLocation.parse("A location","48","34","Invalid/Zone"))
        assertNull(ManualLocation.parse("","48","34","Europe/Kyiv"))
    }
    @Test fun differentTimezonePreservesInstantSemantics() {
        val city = assertNotNull(ManualLocation.parse("Local example","48.5","34.6","Europe/Kyiv"))
        val overridden = city.copy(zoneId = assertNotNull(ManualLocation.zoneOrNull("Europe/Simferopol")).id)
        assertNotEquals(city.zoneId, overridden.zoneId)
        assertEquals(city.lat, overridden.lat)
        assertEquals(city.lon, overridden.lon)
    }
    @Test fun oldSavedCitiesRemainResolvable() {
        assertEquals("dnipro", cityById("dnipro")?.id)
        assertEquals("Europe/Berlin", cityById("Europe/Berlin")?.id)
    }
}
