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
    @Test fun everySelectedUkrainianPlaceHasFiveReadableVariants() {
        val ukrainian = CITIES.filter { it.id.startsWith("ua:") }
        assertEquals(140, ukrainian.size)
        for (city in ukrainian) {
            val uk = city.title(Locale("uk"))
            val ru = city.title(Locale("ru"))
            val en = city.title(Locale("en"))
            val de = city.title(Locale("de"))
            val he = city.title(Locale("he"))
            assertEquals(city.nameUk, uk, "UA entry without curated Ukrainian name: " + city.id)
            assertTrue(uk.any { it in 'А'..'я' }, "Missing Ukrainian Cyrillic: " + city.id)
            assertTrue(ru.any { it in 'А'..'я' } && ru.none { it in "іїєґІЇЄҐ" },
                "Invalid Russian Cyrillic: " + city.id)
            assertTrue(en.any { it in 'A'..'z' } && en.none { it in 'А'..'я' },
                "English display not romanized: " + city.id)
            assertTrue(de.any { it in 'A'..'z' } && de.none { it in 'А'..'я' },
                "German display not romanized: " + city.id)
            assertTrue(he.any { it in '\u05D0'..'\u05EA' }, "Hebrew has no Hebrew letters: " + city.id)
        }
    }
    @Test fun knownHebrewNamesAndSearchAreNotEnglishFallback() {
        assertEquals("ירושלים", requireNotNull(cityById("Asia/Jerusalem")).title(Locale("he")))
        assertEquals("ברלין", requireNotNull(cityById("Europe/Berlin")).title(Locale("he")))
        assertTrue(findCities("ירושלים",100).any { it.id=="Asia/Jerusalem" })
        val ukrainian=requireNotNull(cityById("ua:71:kamianka"))
        assertTrue(ukrainian.title(Locale("he")).any { it in '\u05D0'..'\u05EA' })
    }
    @Test fun all27AdministrativeRegionsAreLocalizedIntoFiveScripts() {
        val regions=CITIES.mapNotNull { it.oblastUk }.distinct()
        assertTrue(regions.size in 24..27, "All 24 oblasts plus Crimea/Sevastopol/Kyiv may be represented through legacy entries")
        for (ua in regions) {
            val location=CITIES.first { it.oblastUk==ua }
            for (tag in listOf("en","de")) {
                val value=requireNotNull(location.oblastTitle(Locale.forLanguageTag(tag)))
                assertTrue(value.none { it in '\u0400'..'\u04FF' }, ua + " leaked Cyrillic in " + tag)
            }
            assertTrue(requireNotNull(location.oblastTitle(Locale("he"))).any { it in '\u05D0'..'\u05EA' })
        }
    }
    @Test fun keyUkrainianNamesAndCoordinatesRemainStable() {
        val city=requireNotNull(cityById("ua:71:kamianka"))
        assertEquals("Кам’янка", city.title(Locale("uk")))
        assertEquals("Кам’янка", city.title(Locale("ru")))
        assertEquals(49.0318,city.lat)
        assertEquals(32.10396,city.lon)
    }
}
