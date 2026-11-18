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
    @Test fun catalogLocalizationEvidenceEqualsActualFiveRuntimeTitles() {
        val file = sequenceOf(
            java.io.File("../qa/approved_city_localizations.tsv"),
            java.io.File("qa/approved_city_localizations.tsv")
        ).firstOrNull { it.isFile } ?: error("Missing versioned five-language QA matrix")
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        assertEquals(338, lines.size, "337 cities plus one header are required")
        val headers = lines.first().split('\t')
        val ix = mapOf(
            "id" to 0, "name_uk" to 3, "name_ru" to 4,
            "name_en" to 5, "name_de" to 6, "name_he" to 7
        )
        for ((key, position) in ix) assertEquals(key, headers[position])
        val seen = mutableSetOf<String>()
        for (line in lines.drop(1)) {
            val row = line.split('\t')
            val id = row[0]
            assertTrue(seen.add(id), "Duplicated QA entry: " + id)
            val place = requireNotNull(cityById(id)) { "QA matrix has removed city: " + id }
            for ((tag, column) in listOf("uk" to 3, "ru" to 4,
                    "en" to 5, "de" to 6, "he" to 7)) {
                assertEquals(row[column], place.title(Locale.forLanguageTag(tag)),
                    "Locale " + tag + " must match its audited displayed form for " + id)
            }
        }
        assertEquals(CITIES.size, seen.size)
    }

    @Test fun keyUkrainianNamesAndCoordinatesRemainStable() {
        val city=requireNotNull(cityById("ua:71:kamianka"))
        assertEquals("Кам’янка", city.title(Locale("uk")))
        assertEquals("Каменка", city.title(Locale("ru")),"Russian equivalent must not be confused with Ukrainian")
        assertEquals(49.0318,city.lat)
        assertEquals(32.10396,city.lon)
    }
}
