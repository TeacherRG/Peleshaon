package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CITIES
import app.mychitas.peleshaon.domain.cityById
import app.mychitas.peleshaon.domain.findCities
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceLanguageRegressionTest {
    @Test fun allFiveLanguagesDisplayNonblankCityAndRegionLabels() {
        val languages=listOf("uk","ru","en","de","he")
        for (city in CITIES) {
            for (language in languages) {
                assertTrue(city.title(Locale.forLanguageTag(language)).isNotBlank(),
                    "Missing " + language + " for " + city.id)
                if (city.oblastUk != null) {
                    assertTrue(city.oblastTitle(Locale.forLanguageTag(language))?.isNotBlank() == true)
                }
            }
        }
    }
    @Test fun knownHebrewNamesAndSearchAreNotEnglishFallback() {
        assertEquals("ירושלים", requireNotNull(cityById("Asia/Jerusalem")).title(Locale("he")))
        assertEquals("ברלין", requireNotNull(cityById("Europe/Berlin")).title(Locale("he")))
        assertTrue(findCities("ירושלים",100).any { it.id=="Asia/Jerusalem" })
        val ukrainian=requireNotNull(cityById("ua:71:kamianka"))
        assertTrue(ukrainian.title(Locale("he")).any { it in '\u05D0'..'\u05EA' })
    }
    @Test fun keyUkrainianNamesAndCoordinatesRemainStable() {
        val city=requireNotNull(cityById("ua:71:kamianka"))
        assertEquals("Кам’янка", city.title(Locale("uk")))
        assertEquals("Кам’янка", city.title(Locale("ru")))
        assertEquals(49.0318,city.lat)
        assertEquals(32.10396,city.lon)
    }
}
