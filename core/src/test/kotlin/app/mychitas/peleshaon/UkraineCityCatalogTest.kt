package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.*
import java.time.ZoneId
import java.time.LocalDate
import kotlin.test.*

class UkraineCityCatalogTest {
    private val photo = listOf("Балта", "Бар", "Баранівка", "Баришівка", "Бахмач", "Берегове", "Бершадь", "Бобринець", "Богуслав", "Болград", "Борислав", "Бориспіль", "Бородянка", "Боярка", "Брацлав", "Бровари", "Буча", "Васильків", "Вижниця", "Вишгород", "Вишневе", "Вознесенськ", "Ворзель", "Гайсин", "Глухів", "Гнівань", "Горішні Плавні", "Городня", "Городок", "Гостомель", "Дрогобич", "Дубно", "Дунаївці", "Жашків", "Жмеринка", "Звенигородка", "Звягель", "Знам’янка", "Золотоноша", "Іванків", "Ізмаїл", "Ізяслав", "Ірпінь", "Калуш", "Кам’янець-Подільський", "Кам’янка", "Канів", "Кварцитне", "Коломия", "Конотоп", "Коростень", "Коростишів", "Корсунь-Шевченківський", "Костянтинівка", "Краматорськ", "Кролевець", "Лохвиця", "Лубни", "Луцьк", "Макарів", "Марганець", "Миргород", "Мукачево", "Немирів", "Нетішин", "Ніжин", "Нікополь", "Новомиргород", "Ободівка", "Овруч", "Олевськ", "Олександрія", "Павлоград", "Переяслав", "Пирятин", "Подільськ", "Покров", "Полонне", "Прилуки", "Радомишль", "Рахів", "Рокитне", "Ромни", "Самар", "Сарни", "Сквира", "Славута", "Слов’янськ", "Сміла", "Старокостянтинів", "Стрий", "Тальне", "Тараща", "Тернівка", "Тернопіль", "Тиврів", "Тростянець", "Трускавець", "Тульчин", "Тячів", "Умань", "Фастів", "Хмільник", "Хотин", "Хуст", "Черняхів", "Чортків", "Шепетівка", "Шостка", "Шпола", "Яготин")
    private val capitals = listOf("Вінниця", "Луцьк", "Дніпро", "Донецьк", "Житомир", "Ужгород", "Запоріжжя", "Івано-Франківськ", "Київ", "Кропивницький", "Луганськ", "Львів", "Миколаїв", "Одеса", "Полтава", "Рівне", "Суми", "Тернопіль", "Харків", "Херсон", "Хмельницький", "Черкаси", "Чернівці", "Чернігів", "Сімферополь", "Севастополь")
    private val centers = listOf("Кам'янське", "Умань", "Гадяч", "Бердичів", "Меджибіж", "Біла Церква", "Кременчук", "Кривий Ріг", "Маріуполь", "Мелітополь", "Макіївка", "Лисичанськ", "Бахмут", "Слов'янськ", "Мукачево", "Ізмаїл", "Ніжин", "Краматорськ", "Хуст", "Конотоп", "Ромни", "Нікополь", "Самар", "Павлоград", "Дрогобич", "Коростень", "Овруч", "Глухів", "Кропивницький", "Сімферополь", "Севастополь")

    @Test fun all111NamesFromPhotoAreSearchable() {
        assertEquals(111, photo.size)
        photo.forEach { assertTrue(findCities(it, 250).isNotEmpty(), "Missing photo: $it") }
    }
    @Test fun allAdministrativeCentersIncludingOccupiedAreSearchable() {
        capitals.forEach { assertTrue(findCities(it, 250).isNotEmpty(), "Missing center: $it") }
    }
    @Test fun keyJewishCentersAreSearchable() {
        centers.forEach { assertTrue(findCities(it, 250).isNotEmpty(), "Missing Jewish point: $it") }
    }
    @Test fun kamianskeIsIndependentFromDnipro() {
        val kam = findCities("Кам'янське", 100).single { it.oblastUk == "Дніпропетровська область" }
        val dni = requireNotNull(cityById("dnipro"))
        assertEquals(kam.id, findCities("Днепродзержинск", 100).first().id)
        assertTrue(kotlin.math.abs(kam.lat - 48.51134) < 0.02)
        assertTrue(kotlin.math.abs(kam.lon - 34.6021) < 0.02)
        assertTrue(kotlin.math.abs(kam.lon - dni.lon) > 0.35)
        assertNotEquals(ZmanimEngine.sunsetOf(LocalDate.of(2026,11,20), kam)?.toInstant(),
            ZmanimEngine.sunsetOf(LocalDate.of(2026,11,20), dni)?.toInstant())
    }
    @Test fun kamiankaAndZnamiankaMustBeDistinctCitiesWithVerifiedRegions() {
        val kamianka = assertNotNull(cityById("ua:71:kamianka"))
        val znamianka = assertNotNull(cityById("ua:35:znamianka"))
        assertEquals("Кам’янка", kamianka.nameUk)
        assertEquals("Черкаська область", kamianka.oblastUk)
        assertEquals("Знам’янка", znamianka.nameUk)
        assertEquals("Кіровоградська область", znamianka.oblastUk)
        assertTrue(kotlin.math.abs(kamianka.lat - 49.03235) < 0.02)
        assertTrue(kotlin.math.abs(kamianka.lon - 32.10395) < 0.02)
        assertTrue(kotlin.math.abs(znamianka.lat - 48.71702) < 0.02)
        assertTrue(kotlin.math.abs(znamianka.lon - 32.65411) < 0.02)
        assertTrue(findCities("Кам’янка", 250).any { it.id == kamianka.id })
        assertTrue(findCities("Знам’янка", 250).any { it.id == znamianka.id })
        assertTrue(findCities("Знаменка", 250).any { it.id == znamianka.id })
        assertTrue(findCities("Знам’янка", 250).none { it.id == kamianka.id })
    }
    @Test fun renamedCitiesRemainSearchable() {
        for ((modern,old) in mapOf(
            "Самар" to "Новомосковськ",
            "Звягель" to "Новоград-Волинський",
            "Переяслав" to "Переяслав-Хмельницький",
            "Кварцитне" to "Першотравневе",
            "Кропивницький" to "Кировоград",
            "Покров" to "Орджонікідзе"
        )) {
            val newIds = findCities(modern,250).map { it.id }.toSet()
            assertTrue(findCities(old,250).any { it.id in newIds }, "$old != $modern")
        }
    }
    @Test fun everyRequestedNameHasItsOwnDisplayedUkrainianPlace() {
        val allRequested = (photo + capitals + centers).distinct()
        assertEquals(147, allRequested.size)
        fun normalized(s: String) = s.lowercase()
            .replace("’", "").replace("'", "").replace("ʼ", "")
            .replace("-", "").replace(" ", "")
        for (requested in allRequested) {
            assertTrue(CITIES.any { normalized(it.title(java.util.Locale("uk"))) == normalized(requested) },
                "No exact Ukrainian city for requested place: $requested")
        }
    }
    @Test fun neverImportObsoleteOrSameNameWrongTown() {
        assertNull(cityById("ua:09:syevyerodoneck"), "Sievierodonetsk was explicitly removed by the user")
        assertTrue(findCities("Сіверськодонецьк", 400).isEmpty())
        assertNull(cityById("ua:40:ternovka"), "Wrong Ternivka near Sevastopol must not survive")
        val city = assertNotNull(cityById("ua:12:ternivka-city"))
        assertEquals("Тернівка", city.nameUk)
        assertEquals("Дніпропетровська область", city.oblastUk)
        assertTrue(kotlin.math.abs(city.lat - 48.5244825) < 0.002)
        assertTrue(kotlin.math.abs(city.lon - 36.081723) < 0.002)
        assertEquals(140, UKRAINE_IMPORTED_CITIES.size)
    }
    @Test fun allCityIdsAndTimezonesValid() {
        assertEquals(337, CITIES.size, "Catalog is intentionally limited to 197 legacy + 140 selected Ukrainian places")
        assertEquals(CITIES.size, CITIES.map{it.id}.distinct().size)
        CITIES.forEach {
            assertTrue(it.lat in -90.0..90.0 && it.lon in -180.0..180.0)
            assertNotNull(resolveZone(it.zoneId))
        }
        assertEquals("Europe/Kiev", cityById("Europe/Kiev")?.id)
        assertNotNull(cityById("Europe/Berlin"))
    }
    @Test fun duplicatesRequireRegionDisambiguation() {
        val r = findCities("Рокитне",250).mapNotNull {it.oblastUk}.distinct()
        assertTrue(r.size>=2)
        assertTrue(findCities("Городок",250).mapNotNull{it.oblastUk}.distinct().size>=2)
    }
}
