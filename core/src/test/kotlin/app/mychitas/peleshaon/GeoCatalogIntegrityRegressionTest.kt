package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CITIES
import app.mychitas.peleshaon.domain.cityById
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeoCatalogIntegrityRegressionTest {
    @Test fun all1694RecordsHaveUniqueIdsAndSanePositions() {
        assertEquals(1694, CITIES.size, "Renew the full coordinate inventory whenever changing the catalog")
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
            "ua:12:novomoskovsk" to "ua:12:novomoskovsk:2",
            "ua:51:podilsk" to "ua:51:podilsk:2"
        )) {
            val first = assertNotNull(cityById(a))
            val second = assertNotNull(cityById(b))
            assertEquals(first.nameUk, second.nameUk)
            assertEquals(first.oblastUk, second.oblastUk)
            assertTrue(first.lat != second.lat || first.lon != second.lon)
        }
    }
}
