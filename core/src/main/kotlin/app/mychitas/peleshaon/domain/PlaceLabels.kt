package app.mychitas.peleshaon.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Proper nouns are normally transliterated, not translated. Where a Hebrew
 * exonym has been documented we use it. Otherwise the Hebrew form is a
 * readable phonetic rendering, NOT an official or independently verified name.
 * The app and this formatter remain completely offline.
 */
internal object PlaceLabels {
    private val hebrewExonyms = mapOf(
        "Asia/Jerusalem" to "ירושלים", "Asia/Tel_Aviv" to "תל אביב",
        "Asia/Hebron" to "חברון", "Europe/Kiev" to "קייב",
        "dnipro" to "דניפרו", "Europe/Berlin" to "ברלין",
        "Europe/Vienna" to "וינה", "Europe/Vilnius" to "וילנה",
        "Europe/Warsaw" to "ורשה", "Europe/London" to "לונדון",
        "Europe/Moscow" to "מוסקבה", "Europe/Paris" to "פריז",
        "ua:71:kamianka" to "קמיאנקה", "ua:35:znamianka" to "זנמיאנקה"
    )

    fun hebrew(city: CityOption): String =
        hebrewExonyms[city.id] ?: phoneticHebrew(
            city.nameUk ?: city.nameRu.takeIf { s -> s.any(::isCyrillic) } ?: city.name
        )

    private fun isCyrillic(c: Char) =
        Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC

    /** Approximate script transliteration: do not use as a certified exonym. */
    fun phoneticHebrew(input: String): String {
        val source = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        val cyr = mapOf(
            'а' to "א", 'б' to "ב", 'в' to "ו", 'г' to "ה", 'ґ' to "ג",
            'д' to "ד", 'е' to "ה", 'є' to "ייה", 'ж' to "ז׳",
            'з' to "ז", 'и' to "י", 'і' to "י", 'ї' to "יי", 'й' to "י",
            'к' to "ק", 'л' to "ל", 'м' to "מ", 'н' to "נ",
            'о' to "ו", 'п' to "פ", 'р' to "ר", 'с' to "ס",
            'т' to "ט", 'у' to "ו", 'ф' to "פ", 'х' to "ח",
            'ц' to "צ", 'ч' to "צ׳", 'ш' to "ש", 'щ' to "שצ׳",
            'ю' to "יו", 'я' to "יה", 'ы' to "י", 'э' to "א",
            'ё' to "יו", 'ъ' to "", 'ь' to ""
        )
        val lat = mapOf(
            'a' to "א", 'b' to "ב", 'c' to "ק", 'd' to "ד", 'e' to "ה",
            'f' to "פ", 'g' to "ג", 'h' to "ה", 'i' to "י", 'j' to "י",
            'k' to "ק", 'l' to "ל", 'm' to "מ", 'n' to "נ", 'o' to "ו",
            'p' to "פ", 'q' to "ק", 'r' to "ר", 's' to "ס", 't' to "ט",
            'u' to "ו", 'v' to "ו", 'w' to "ו", 'x' to "קס",
            'y' to "י", 'z' to "ז"
        )
        val digraphs = listOf(
            "shch" to "שצ׳", "sh" to "ש", "ch" to "צ׳",
            "kh" to "ח", "zh" to "ז׳", "ts" to "צ",
            "ya" to "יה", "yu" to "יו", "yo" to "יו"
        )
        val out = StringBuilder()
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (isCyrillic(c)) {
                out.append(cyr[c] ?: "")
                i++
                continue
            }
            val digraph = digraphs.firstOrNull { source.startsWith(it.first, i) }
            if (digraph != null) {
                out.append(digraph.second)
                i += digraph.first.length
                continue
            }
            out.append(lat[c] ?: if (c.isWhitespace() || c == '-' || c.isDigit()) c.toString() else "")
            i++
        }
        return out.toString().trim().ifBlank { input }
    }

    // Administrative region exonyms: UK is the authoritative original.
    private val ruRegion = mapOf(
        "Автономна Республіка Крим" to "Автономная Республика Крым",
        "Вінницька область" to "Винницкая область",
        "Волинська область" to "Волынская область",
        "Дніпропетровська область" to "Днепропетровская область",
        "Донецька область" to "Донецкая область",
        "Житомирська область" to "Житомирская область",
        "Закарпатська область" to "Закарпатская область",
        "Запорізька область" to "Запорожская область",
        "Івано-Франківська область" to "Ивано-Франковская область",
        "Київ" to "Киев", "Київська область" to "Киевская область",
        "Кіровоградська область" to "Кировоградская область",
        "Луганська область" to "Луганская область",
        "Львівська область" to "Львовская область",
        "Миколаївська область" to "Николаевская область",
        "Одеська область" to "Одесская область",
        "Полтавська область" to "Полтавская область",
        "Рівненська область" to "Ровенская область",
        "Севастополь" to "Севастополь",
        "Сумська область" to "Сумская область",
        "Тернопільська область" to "Тернопольская область",
        "Харківська область" to "Харьковская область",
        "Херсонська область" to "Херсонская область",
        "Хмельницька область" to "Хмельницкая область",
        "Черкаська область" to "Черкасская область",
        "Чернівецька область" to "Черновицкая область",
        "Чернігівська область" to "Черниговская область"
    )
    // EN and DE use international Latin spelling when no established exonym
    // is known. This is transliteration, not an assertion of official translation.
    private val enRegion = mapOf(
        "Автономна Республіка Крим" to "Autonomous Republic of Crimea",
        "Вінницька область" to "Vinnytsia Oblast",
        "Волинська область" to "Volyn Oblast",
        "Дніпропетровська область" to "Dnipropetrovsk Oblast",
        "Донецька область" to "Donetsk Oblast",
        "Житомирська область" to "Zhytomyr Oblast",
        "Закарпатська область" to "Zakarpattia Oblast",
        "Запорізька область" to "Zaporizhzhia Oblast",
        "Івано-Франківська область" to "Ivano-Frankivsk Oblast",
        "Київ" to "Kyiv",
        "Київська область" to "Kyiv Oblast",
        "Кіровоградська область" to "Kirovohrad Oblast",
        "Луганська область" to "Luhansk Oblast",
        "Львівська область" to "Lviv Oblast",
        "Миколаївська область" to "Mykolaiv Oblast",
        "Одеська область" to "Odesa Oblast",
        "Полтавська область" to "Poltava Oblast",
        "Рівненська область" to "Rivne Oblast",
        "Севастополь" to "Sevastopol",
        "Сумська область" to "Sumy Oblast",
        "Тернопільська область" to "Ternopil Oblast",
        "Харківська область" to "Kharkiv Oblast",
        "Херсонська область" to "Kherson Oblast",
        "Хмельницька область" to "Khmelnytskyi Oblast",
        "Черкаська область" to "Cherkasy Oblast",
        "Чернівецька область" to "Chernivtsi Oblast",
        "Чернігівська область" to "Chernihiv Oblast"
    )
    fun region(uk: String, locale: Locale): String = when (locale.language) {
        "ru", "be" -> ruRegion[uk] ?: uk
        "en" -> enRegion[uk] ?: (uk.removeSuffix(" область") + " Oblast")
        "de" -> when (uk) {
            "Київ" -> "Kiew"
            "Севастополь" -> "Sewastopol"
            "Автономна Республіка Крим" -> "Autonome Republik Krim"
            else -> "Oblast " + (enRegion[uk] ?: uk.removeSuffix(" область"))
                .removeSuffix(" Oblast")
        }
        "he", "iw" -> when (uk) {
            "Київ" -> "קייב"
            "Севастополь" -> "סבסטופול"
            else -> "מחוז " + phoneticHebrew(uk.removeSuffix(" область"))
        }
        else -> uk
    }
}
