package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CITIES
import app.mychitas.peleshaon.domain.cityById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeoCatalogIntegrityRegressionTest {
    @Test fun selected337RecordsHaveUniqueIdsAndSanePositions() {
        assertEquals(337, CITIES.size, "The approved catalog has 197 previous places and 140 selected Ukrainian places")
        assertEquals(CITIES.size, CITIES.map { it.id }.toSet().size)
        assertEquals(CITIES.size, CITIES.map { String.format(java.util.Locale.US, "%.4f,%.4f", it.lat, it.lon) }.toSet().size)
        CITIES.forEach { city ->
            assertTrue(city.lat.isFinite() && city.lon.isFinite(), "Nonfinite position: ${city.id}")
            assertTrue(city.lat in -90.0..90.0 && city.lon in -180.0..180.0, "Bad coordinates: ${city.id}")
            if (city.id.startsWith("ua:")) {
                assertTrue(city.lat in 44.0..53.0 && city.lon in 22.0..41.0, "UA source outlier: ${city.id}")
            }
        }
    }
    @Test fun sameNameSameRegionRecordsRemainVisibleAndDistinguishable() {
        for ((a,b) in listOf(
            "ua:32:rokytne" to "ua:56:rokytne",
            "ua:68:horodok" to "ua:46:horodok"
        )) {
            val first = assertNotNull(cityById(a))
            val second = assertNotNull(cityById(b))
            assertEquals(first.nameUk, second.nameUk)
            assertTrue(first.oblastUk != second.oblastUk)
            assertTrue(first.lat != second.lat || first.lon != second.lon)
        }
    }
}
