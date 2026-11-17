package app.mychitas.peleshaon.domain

import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale

/**
 * Город: имя по-английски (`name`, им же считает KosherJava), по-русски и по-немецки, координаты, часовой пояс.
 * Список — тот же, что на сайте Нифлаот (src/core/places.ts): города часовых поясов и общины.
 */
data class CityOption(
    val id: String,
    val name: String,
    val nameRu: String,
    val nameDe: String?,
    val lat: Double,
    val lon: Double,
    val zoneId: String,
    val isIsrael: Boolean
) {
    /** Имя на языке интерфейса (иврита в списке нет — английское). */
    fun title(locale: Locale = Locale.getDefault()): String = when (locale.language) {
        "uk" -> UK_CITY[nameRu] ?: nameRu
        "ru", "be" -> nameRu
        "de" -> nameDe ?: name
        else -> name
    }

    /** Часовой пояс; имя, которого нет в базе устройства, — пояс с тем же временем, в крайнем случае — устройства. */
    fun zone(): ZoneId = resolveZone(zoneId) ?: ZoneId.systemDefault()
}

val CITIES: List<CityOption> = listOf(
    CityOption("Asia/Jerusalem", "Jerusalem", "Иерусалим", null, 31.78, 35.22, "Asia/Jerusalem", true),
    CityOption("Asia/Tel_Aviv", "Tel Aviv", "Тель-Авив", null, 32.08, 34.78, "Asia/Tel_Aviv", true),
    CityOption("Asia/Hebron", "Hebron", "Хеврон", null, 31.53, 35.1, "Asia/Hebron", true),
    CityOption("Europe/Moscow", "Moscow", "Москва", "Moskau", 55.76, 37.62, "Europe/Moscow", false),
    CityOption("Europe/Kaliningrad", "Kaliningrad", "Калининград", null, 54.71, 20.51, "Europe/Kaliningrad", false),
    CityOption("Europe/Samara", "Samara", "Самара", null, 53.2, 50.15, "Europe/Samara", false),
    CityOption("Europe/Volgograd", "Volgograd", "Волгоград", "Wolgograd", 48.71, 44.51, "Europe/Volgograd", false),
    CityOption("Europe/Saratov", "Saratov", "Саратов", "Saratow", 51.53, 46.03, "Europe/Saratov", false),
    CityOption("Europe/Ulyanovsk", "Ulyanovsk", "Ульяновск", "Uljanowsk", 54.32, 48.4, "Europe/Ulyanovsk", false),
    CityOption("Europe/Astrakhan", "Astrakhan", "Астрахань", "Astrachan", 46.35, 48.04, "Europe/Astrakhan", false),
    CityOption("Europe/Kirov", "Kirov", "Киров", "Kirow", 58.6, 49.66, "Europe/Kirov", false),
    CityOption("Asia/Yekaterinburg", "Yekaterinburg", "Екатеринбург", "Jekaterinburg", 56.84, 60.6, "Asia/Yekaterinburg", false),
    CityOption("Asia/Omsk", "Omsk", "Омск", null, 54.99, 73.37, "Asia/Omsk", false),
    CityOption("Asia/Novosibirsk", "Novosibirsk", "Новосибирск", "Nowosibirsk", 55.03, 82.92, "Asia/Novosibirsk", false),
    CityOption("Asia/Barnaul", "Barnaul", "Барнаул", null, 53.35, 83.78, "Asia/Barnaul", false),
    CityOption("Asia/Tomsk", "Tomsk", "Томск", null, 56.49, 84.95, "Asia/Tomsk", false),
    CityOption("Asia/Novokuznetsk", "Novokuznetsk", "Новокузнецк", "Nowokusnezk", 53.76, 87.12, "Asia/Novokuznetsk", false),
    CityOption("Asia/Krasnoyarsk", "Krasnoyarsk", "Красноярск", "Krasnojarsk", 56.01, 92.85, "Asia/Krasnoyarsk", false),
    CityOption("Asia/Irkutsk", "Irkutsk", "Иркутск", null, 52.29, 104.3, "Asia/Irkutsk", false),
    CityOption("Asia/Chita", "Chita", "Чита", "Tschita", 52.03, 113.5, "Asia/Chita", false),
    CityOption("Asia/Yakutsk", "Yakutsk", "Якутск", "Jakutsk", 62.03, 129.73, "Asia/Yakutsk", false),
    CityOption("Asia/Vladivostok", "Vladivostok", "Владивосток", "Wladiwostok", 43.12, 131.89, "Asia/Vladivostok", false),
    CityOption("Asia/Khabarovsk", "Khabarovsk", "Хабаровск", "Chabarowsk", 48.48, 135.08, "Asia/Khabarovsk", false),
    CityOption("Asia/Sakhalin", "Sakhalin", "Южно-Сахалинск", "Juschno-Sachalinsk", 46.96, 142.73, "Asia/Sakhalin", false),
    CityOption("Asia/Magadan", "Magadan", "Магадан", null, 59.56, 150.8, "Asia/Magadan", false),
    CityOption("Asia/Kamchatka", "Kamchatka", "Петропавловск-Камчатский", "Petropawlowsk-Kamtschatski", 53.02, 158.65, "Asia/Kamchatka", false),
    CityOption("Europe/Kiev", "Kiev", "Киев", "Kiew", 50.45, 30.52, "Europe/Kiev", false),
    CityOption("Europe/Simferopol", "Simferopol", "Симферополь", "Simferopol", 44.95, 34.1, "Europe/Simferopol", false),
    CityOption("Europe/Minsk", "Minsk", "Минск", null, 53.9, 27.57, "Europe/Minsk", false),
    CityOption("Europe/Chisinau", "Chisinau", "Кишинёв", "Chișinău", 47.01, 28.86, "Europe/Chisinau", false),
    CityOption("Europe/Riga", "Riga", "Рига", null, 56.95, 24.11, "Europe/Riga", false),
    CityOption("Europe/Vilnius", "Vilnius", "Вильнюс", "Vilnius", 54.69, 25.28, "Europe/Vilnius", false),
    CityOption("Europe/Tallinn", "Tallinn", "Таллин", null, 59.44, 24.75, "Europe/Tallinn", false),
    CityOption("Asia/Tbilisi", "Tbilisi", "Тбилиси", "Tiflis", 41.72, 44.79, "Asia/Tbilisi", false),
    CityOption("Asia/Baku", "Baku", "Баку", null, 40.41, 49.87, "Asia/Baku", false),
    CityOption("Asia/Yerevan", "Yerevan", "Ереван", "Jerewan", 40.18, 44.51, "Asia/Yerevan", false),
    CityOption("Asia/Almaty", "Almaty", "Алматы", null, 43.24, 76.89, "Asia/Almaty", false),
    CityOption("Asia/Tashkent", "Tashkent", "Ташкент", "Taschkent", 41.3, 69.24, "Asia/Tashkent", false),
    CityOption("Asia/Bishkek", "Bishkek", "Бишкек", "Bischkek", 42.87, 74.59, "Asia/Bishkek", false),
    CityOption("Asia/Dushanbe", "Dushanbe", "Душанбе", "Duschanbe", 38.56, 68.77, "Asia/Dushanbe", false),
    CityOption("Europe/Berlin", "Berlin", "Берлин", null, 52.52, 13.4, "Europe/Berlin", false),
    CityOption("Europe/Vienna", "Vienna", "Вена", "Wien", 48.21, 16.37, "Europe/Vienna", false),
    CityOption("Europe/Zurich", "Zurich", "Цюрих", "Zürich", 47.38, 8.54, "Europe/Zurich", false),
    CityOption("Europe/Busingen", "Busingen", "Бюзинген", "Büsingen", 47.7, 8.69, "Europe/Busingen", false),
    CityOption("Europe/Prague", "Prague", "Прага", "Prag", 50.08, 14.44, "Europe/Prague", false),
    CityOption("Europe/Warsaw", "Warsaw", "Варшава", "Warschau", 52.23, 21.01, "Europe/Warsaw", false),
    CityOption("Europe/Budapest", "Budapest", "Будапешт", null, 47.5, 19.04, "Europe/Budapest", false),
    CityOption("Europe/Bucharest", "Bucharest", "Бухарест", "Bukarest", 44.43, 26.1, "Europe/Bucharest", false),
    CityOption("Europe/Sofia", "Sofia", "София", "Sofia", 42.7, 23.32, "Europe/Sofia", false),
    CityOption("Europe/Athens", "Athens", "Афины", "Athen", 37.98, 23.73, "Europe/Athens", false),
    CityOption("Europe/Istanbul", "Istanbul", "Стамбул", null, 41.01, 28.98, "Europe/Istanbul", false),
    CityOption("Asia/Nicosia", "Nicosia", "Никосия", "Nikosia", 35.17, 33.36, "Asia/Nicosia", false),
    CityOption("Europe/Rome", "Rome", "Рим", "Rom", 41.9, 12.5, "Europe/Rome", false),
    CityOption("Europe/Paris", "Paris", "Париж", null, 48.86, 2.35, "Europe/Paris", false),
    CityOption("Europe/Brussels", "Brussels", "Брюссель", "Brüssel", 50.85, 4.35, "Europe/Brussels", false),
    CityOption("Europe/Amsterdam", "Amsterdam", "Амстердам", null, 52.37, 4.9, "Europe/Amsterdam", false),
    CityOption("Europe/Luxembourg", "Luxembourg", "Люксембург", "Luxemburg", 49.61, 6.13, "Europe/Luxembourg", false),
    CityOption("Europe/Copenhagen", "Copenhagen", "Копенгаген", "Kopenhagen", 55.68, 12.57, "Europe/Copenhagen", false),
    CityOption("Europe/Stockholm", "Stockholm", "Стокгольм", null, 59.33, 18.07, "Europe/Stockholm", false),
    CityOption("Europe/Oslo", "Oslo", "Осло", null, 59.91, 10.75, "Europe/Oslo", false),
    CityOption("Europe/Helsinki", "Helsinki", "Хельсинки", null, 60.17, 24.94, "Europe/Helsinki", false),
    CityOption("Europe/London", "London", "Лондон", null, 51.51, -0.13, "Europe/London", false),
    CityOption("Europe/Dublin", "Dublin", "Дублин", null, 53.35, -6.26, "Europe/Dublin", false),
    CityOption("Europe/Madrid", "Madrid", "Мадрид", null, 40.42, -3.7, "Europe/Madrid", false),
    CityOption("Europe/Lisbon", "Lisbon", "Лиссабон", "Lissabon", 38.72, -9.14, "Europe/Lisbon", false),
    CityOption("Europe/Belgrade", "Belgrade", "Белград", null, 44.79, 20.45, "Europe/Belgrade", false),
    CityOption("Europe/Zagreb", "Zagreb", "Загреб", null, 45.81, 15.98, "Europe/Zagreb", false),
    CityOption("Europe/Bratislava", "Bratislava", "Братислава", null, 48.15, 17.11, "Europe/Bratislava", false),
    CityOption("Europe/Ljubljana", "Ljubljana", "Любляна", "Ljubljana", 46.06, 14.51, "Europe/Ljubljana", false),
    CityOption("America/New_York", "New York", "Нью-Йорк", null, 40.71, -74.01, "America/New_York", false),
    CityOption("America/Toronto", "Toronto", "Торонто", null, 43.65, -79.38, "America/Toronto", false),
    CityOption("America/Montreal", "Montreal", "Монреаль", null, 45.5, -73.57, "America/Montreal", false),
    CityOption("America/Chicago", "Chicago", "Чикаго", null, 41.88, -87.63, "America/Chicago", false),
    CityOption("America/Detroit", "Detroit", "Детройт", null, 42.33, -83.05, "America/Detroit", false),
    CityOption("America/Denver", "Denver", "Денвер", null, 39.74, -104.99, "America/Denver", false),
    CityOption("America/Phoenix", "Phoenix", "Финикс", null, 33.45, -112.07, "America/Phoenix", false),
    CityOption("America/Los_Angeles", "Los Angeles", "Лос-Анджелес", null, 34.05, -118.24, "America/Los_Angeles", false),
    CityOption("America/Vancouver", "Vancouver", "Ванкувер", null, 49.28, -123.12, "America/Vancouver", false),
    CityOption("America/Mexico_City", "Mexico City", "Мехико", "Mexiko-Stadt", 19.43, -99.13, "America/Mexico_City", false),
    CityOption("America/Sao_Paulo", "Sao Paulo", "Сан-Паулу", null, -23.55, -46.63, "America/Sao_Paulo", false),
    CityOption("America/Argentina/Buenos_Aires", "Buenos Aires", "Буэнос-Айрес", null, -34.6, -58.38, "America/Argentina/Buenos_Aires", false),
    CityOption("America/Santiago", "Santiago", "Сантьяго", null, -33.45, -70.67, "America/Santiago", false),
    CityOption("America/Panama", "Panama", "Панама", "Panama-Stadt", 8.98, -79.52, "America/Panama", false),
    CityOption("Africa/Johannesburg", "Johannesburg", "Йоханнесбург", null, -26.2, 28.05, "Africa/Johannesburg", false),
    CityOption("Africa/Casablanca", "Casablanca", "Касабланка", "Casablanca", 33.57, -7.59, "Africa/Casablanca", false),
    CityOption("Australia/Sydney", "Sydney", "Сидней", null, -33.87, 151.21, "Australia/Sydney", false),
    CityOption("Australia/Melbourne", "Melbourne", "Мельбурн", null, -37.81, 144.96, "Australia/Melbourne", false),
    CityOption("Australia/Brisbane", "Brisbane", "Брисбен", null, -27.47, 153.03, "Australia/Brisbane", false),
    CityOption("Australia/Perth", "Perth", "Перт", null, -31.95, 115.86, "Australia/Perth", false),
    CityOption("Pacific/Auckland", "Auckland", "Окленд", null, -36.85, 174.76, "Pacific/Auckland", false),
    CityOption("Asia/Dubai", "Dubai", "Дубай", "Dubai", 25.2, 55.27, "Asia/Dubai", false),
    CityOption("Asia/Bangkok", "Bangkok", "Бангкок", "Bangkok", 13.76, 100.5, "Asia/Bangkok", false),
    CityOption("Asia/Hong_Kong", "Hong Kong", "Гонконг", "Hongkong", 22.32, 114.17, "Asia/Hong_Kong", false),
    CityOption("Asia/Shanghai", "Shanghai", "Шанхай", "Schanghai", 31.23, 121.47, "Asia/Shanghai", false),
    CityOption("Asia/Tokyo", "Tokyo", "Токио", "Tokio", 35.68, 139.69, "Asia/Tokyo", false),
    CityOption("Asia/Singapore", "Singapore", "Сингапур", "Singapur", 1.35, 103.82, "Asia/Singapore", false),
    // Исторический ID сохранён для совместимости с ранее выбранным городом.
    CityOption("Asia/Kolkata", "Mumbai", "Мумбаи", "Mumbai", 19.08, 72.88, "Asia/Kolkata", false),
    CityOption("kolkata", "Kolkata", "Калькутта", "Kalkutta", 22.5726, 88.3639, "Asia/Kolkata", false),
    CityOption("haifa", "Haifa", "Хайфа", null, 32.79, 34.99, "Asia/Jerusalem", true),
    CityOption("beer-sheva", "Be'er Sheva", "Беэр-Шева", "Be'er Scheva", 31.25, 34.79, "Asia/Jerusalem", true),
    CityOption("bnei-brak", "Bnei Brak", "Бней-Брак", null, 32.08, 34.83, "Asia/Jerusalem", true),
    CityOption("tzfat", "Tzfat (Safed)", "Цфат", "Zfat (Safed)", 32.96, 35.5, "Asia/Jerusalem", true),
    CityOption("tiberias", "Tiberias", "Тверия", "Tiberias", 32.79, 35.53, "Asia/Jerusalem", true),
    CityOption("netanya", "Netanya", "Нетания", "Netanja", 32.33, 34.86, "Asia/Jerusalem", true),
    CityOption("ashdod", "Ashdod", "Ашдод", "Aschdod", 31.8, 34.65, "Asia/Jerusalem", true),
    CityOption("ashkelon", "Ashkelon", "Ашкелон", "Aschkelon", 31.67, 34.57, "Asia/Jerusalem", true),
    CityOption("rishon", "Rishon LeZion", "Ришон-ле-Цион", "Rischon LeZion", 31.97, 34.79, "Asia/Jerusalem", true),
    CityOption("petah-tikva", "Petah Tikva", "Петах-Тиква", "Petach Tikwa", 32.09, 34.89, "Asia/Jerusalem", true),
    CityOption("kfar-chabad", "Kfar Chabad", "Кфар-Хабад", "Kfar Chabad", 31.99, 34.85, "Asia/Jerusalem", true),
    CityOption("eilat", "Eilat", "Эйлат", null, 29.56, 34.95, "Asia/Jerusalem", true),
    CityOption("nazareth-illit", "Nof HaGalil", "Ноф-ха-Галиль", null, 32.72, 35.33, "Asia/Jerusalem", true),
    CityOption("karmiel", "Karmiel", "Кармиэль", null, 32.92, 35.3, "Asia/Jerusalem", true),
    CityOption("saint-petersburg", "Saint Petersburg", "Санкт-Петербург", "Sankt Petersburg", 59.94, 30.31, "Europe/Moscow", false),
    CityOption("kazan", "Kazan", "Казань", "Kasan", 55.79, 49.12, "Europe/Moscow", false),
    CityOption("nizhny-novgorod", "Nizhny Novgorod", "Нижний Новгород", "Nischni Nowgorod", 56.33, 44.0, "Europe/Moscow", false),
    CityOption("rostov", "Rostov-on-Don", "Ростов-на-Дону", "Rostow am Don", 47.24, 39.71, "Europe/Moscow", false),
    CityOption("krasnodar", "Krasnodar", "Краснодар", null, 45.04, 38.98, "Europe/Moscow", false),
    CityOption("sochi", "Sochi", "Сочи", "Sotschi", 43.6, 39.73, "Europe/Moscow", false),
    CityOption("voronezh", "Voronezh", "Воронеж", "Woronesch", 51.66, 39.2, "Europe/Moscow", false),
    CityOption("perm", "Perm", "Пермь", null, 58.01, 56.25, "Asia/Yekaterinburg", false),
    CityOption("chelyabinsk", "Chelyabinsk", "Челябинск", "Tscheljabinsk", 55.16, 61.4, "Asia/Yekaterinburg", false),
    CityOption("ufa", "Ufa", "Уфа", null, 54.74, 55.97, "Asia/Yekaterinburg", false),
    CityOption("odesa", "Odesa", "Одесса", "Odessa", 46.48, 30.72, "Europe/Kyiv", false),
    CityOption("dnipro", "Dnipro", "Днепр", "Dnipro", 48.46, 35.05, "Europe/Kyiv", false),
    CityOption("kharkiv", "Kharkiv", "Харьков", "Charkiw", 49.99, 36.23, "Europe/Kyiv", false),
    CityOption("zaporizhzhia", "Zaporizhzhia", "Запорожье", "Saporischschja", 47.84, 35.14, "Europe/Kyiv", false),
    CityOption("lviv", "Lviv", "Львов", "Lwiw", 49.84, 24.03, "Europe/Kyiv", false),
    CityOption("uman", "Uman", "Умань", null, 48.75, 30.22, "Europe/Kyiv", false),
    CityOption("vinnytsia", "Vinnytsia", "Винница", "Winnyzja", 49.23, 28.47, "Europe/Kyiv", false),
    CityOption("gomel", "Gomel", "Гомель", null, 52.44, 30.98, "Europe/Minsk", false),
    CityOption("vitebsk", "Vitebsk", "Витебск", "Witebsk", 55.19, 30.2, "Europe/Minsk", false),
    CityOption("daugavpils", "Daugavpils", "Даугавпилс", null, 55.87, 26.54, "Europe/Riga", false),
    CityOption("batumi", "Batumi", "Батуми", null, 41.64, 41.63, "Asia/Tbilisi", false),
    CityOption("astana", "Astana", "Астана", null, 51.17, 71.45, "Asia/Almaty", false),
    CityOption("samarkand", "Samarkand", "Самарканд", null, 39.65, 66.96, "Asia/Samarkand", false),
    CityOption("bukhara", "Bukhara", "Бухара", "Buchara", 39.77, 64.42, "Asia/Samarkand", false),
    CityOption("graz", "Graz", "Грац", null, 47.07, 15.44, "Europe/Vienna", false),
    CityOption("salzburg", "Salzburg", "Зальцбург", null, 47.81, 13.06, "Europe/Vienna", false),
    CityOption("innsbruck", "Innsbruck", "Инсбрук", null, 47.27, 11.4, "Europe/Vienna", false),
    CityOption("linz", "Linz", "Линц", null, 48.31, 14.29, "Europe/Vienna", false),
    CityOption("munich", "Munich", "Мюнхен", "München", 48.14, 11.58, "Europe/Berlin", false),
    CityOption("frankfurt", "Frankfurt", "Франкфурт-на-Майне", "Frankfurt am Main", 50.11, 8.68, "Europe/Berlin", false),
    CityOption("hamburg", "Hamburg", "Гамбург", null, 53.55, 9.99, "Europe/Berlin", false),
    CityOption("cologne", "Cologne", "Кёльн", "Köln", 50.94, 6.96, "Europe/Berlin", false),
    CityOption("dusseldorf", "Düsseldorf", "Дюссельдорф", "Düsseldorf", 51.23, 6.77, "Europe/Berlin", false),
    CityOption("stuttgart", "Stuttgart", "Штутгарт", null, 48.78, 9.18, "Europe/Berlin", false),
    CityOption("leipzig", "Leipzig", "Лейпциг", null, 51.34, 12.37, "Europe/Berlin", false),
    CityOption("dresden", "Dresden", "Дрезден", null, 51.05, 13.74, "Europe/Berlin", false),
    CityOption("hannover", "Hanover", "Ганновер", "Hannover", 52.37, 9.73, "Europe/Berlin", false),
    CityOption("nuremberg", "Nuremberg", "Нюрнберг", "Nürnberg", 49.45, 11.08, "Europe/Berlin", false),
    CityOption("bremen", "Bremen", "Бремен", null, 53.08, 8.8, "Europe/Berlin", false),
    CityOption("dortmund", "Dortmund", "Дортмунд", null, 51.51, 7.47, "Europe/Berlin", false),
    CityOption("essen", "Essen", "Эссен", null, 51.46, 7.01, "Europe/Berlin", false),
    CityOption("mannheim", "Mannheim", "Мангейм", null, 49.49, 8.47, "Europe/Berlin", false),
    CityOption("karlsruhe", "Karlsruhe", "Карлсруэ", null, 49.01, 8.4, "Europe/Berlin", false),
    CityOption("basel", "Basel", "Базель", null, 47.56, 7.59, "Europe/Zurich", false),
    CityOption("geneva", "Geneva", "Женева", "Genf", 46.2, 6.14, "Europe/Zurich", false),
    CityOption("bern", "Bern", "Берн", null, 46.95, 7.45, "Europe/Zurich", false),
    CityOption("lugano", "Lugano", "Лугано", null, 46.0, 8.95, "Europe/Zurich", false),
    CityOption("antwerp", "Antwerp", "Антверпен", "Antwerpen", 51.22, 4.4, "Europe/Brussels", false),
    CityOption("rotterdam", "Rotterdam", "Роттердам", null, 51.92, 4.48, "Europe/Amsterdam", false),
    CityOption("manchester", "Manchester", "Манчестер", null, 53.48, -2.24, "Europe/London", false),
    CityOption("gateshead", "Gateshead", "Гейтсхед", null, 54.95, -1.6, "Europe/London", false),
    CityOption("leeds", "Leeds", "Лидс", null, 53.8, -1.55, "Europe/London", false),
    CityOption("milan", "Milan", "Милан", "Mailand", 45.46, 9.19, "Europe/Rome", false),
    CityOption("venice", "Venice", "Венеция", "Venedig", 45.44, 12.33, "Europe/Rome", false),
    CityOption("nice", "Nice", "Ницца", "Nizza", 43.7, 7.27, "Europe/Paris", false),
    CityOption("marseille", "Marseille", "Марсель", null, 43.3, 5.37, "Europe/Paris", false),
    CityOption("lyon", "Lyon", "Лион", null, 45.76, 4.84, "Europe/Paris", false),
    CityOption("strasbourg", "Strasbourg", "Страсбург", "Straßburg", 48.57, 7.75, "Europe/Paris", false),
    CityOption("barcelona", "Barcelona", "Барселона", null, 41.39, 2.17, "Europe/Madrid", false),
    CityOption("krakow", "Kraków", "Краков", "Krakau", 50.06, 19.94, "Europe/Warsaw", false),
    CityOption("lodz", "Łódź", "Лодзь", "Lodz", 51.76, 19.46, "Europe/Warsaw", false),
    CityOption("thessaloniki", "Thessaloniki", "Салоники", "Thessaloniki", 40.64, 22.94, "Europe/Athens", false),
    CityOption("cyprus-larnaca", "Larnaca", "Ларнака", "Larnaka", 34.92, 33.62, "Asia/Nicosia", false),
    CityOption("limassol", "Limassol", "Лимасол", null, 34.68, 33.04, "Asia/Nicosia", false),
    CityOption("antalya", "Antalya", "Анталья", "Antalya", 36.9, 30.7, "Europe/Istanbul", false),
    CityOption("brooklyn", "Brooklyn (Crown Heights)", "Бруклин (Краун-Хайтс)", "Brooklyn (Crown Heights)", 40.67, -73.94, "America/New_York", false),
    CityOption("lakewood", "Lakewood", "Лейквуд", null, 40.08, -74.21, "America/New_York", false),
    CityOption("monsey", "Monsey", "Монси", null, 41.11, -74.07, "America/New_York", false),
    CityOption("boston", "Boston", "Бостон", null, 42.36, -71.06, "America/New_York", false),
    CityOption("philadelphia", "Philadelphia", "Филадельфия", "Philadelphia", 39.95, -75.17, "America/New_York", false),
    CityOption("baltimore", "Baltimore", "Балтимор", null, 39.29, -76.61, "America/New_York", false),
    CityOption("washington", "Washington", "Вашингтон", null, 38.91, -77.04, "America/New_York", false),
    CityOption("miami", "Miami", "Майами", null, 25.76, -80.19, "America/New_York", false),
    CityOption("atlanta", "Atlanta", "Атланта", null, 33.75, -84.39, "America/New_York", false),
    CityOption("cleveland", "Cleveland", "Кливленд", null, 41.5, -81.69, "America/New_York", false),
    CityOption("pittsburgh", "Pittsburgh", "Питтсбург", null, 40.44, -80.0, "America/New_York", false),
    CityOption("houston", "Houston", "Хьюстон", null, 29.76, -95.37, "America/Chicago", false),
    CityOption("dallas", "Dallas", "Даллас", null, 32.78, -96.8, "America/Chicago", false),
    CityOption("san-francisco", "San Francisco", "Сан-Франциско", null, 37.77, -122.42, "America/Los_Angeles", false),
    CityOption("san-diego", "San Diego", "Сан-Диего", null, 32.72, -117.16, "America/Los_Angeles", false),
    CityOption("las-vegas", "Las Vegas", "Лас-Вегас", null, 36.17, -115.14, "America/Los_Angeles", false),
    CityOption("seattle", "Seattle", "Сиэтл", null, 47.61, -122.33, "America/Los_Angeles", false),
    CityOption("ottawa", "Ottawa", "Оттава", null, 45.42, -75.7, "America/Toronto", false),
    CityOption("cape-town", "Cape Town", "Кейптаун", "Kapstadt", -33.92, 18.42, "Africa/Johannesburg", false),
    CityOption("rio", "Rio de Janeiro", "Рио-де-Жанейро", null, -22.91, -43.17, "America/Sao_Paulo", false),
)

/** Прежнее имя — для старых настроек и кода, который его ждёт. */
val DEFAULT_CITIES: List<CityOption> get() = CITIES

/** Пояса, которых может не быть в базе часовых поясов (новой или старой), — пояс с тем же временем. */
private val ZONE_ALIASES = mapOf(
    "Asia/Khabarovsk" to "Asia/Vladivostok",
    "Europe/Kyiv" to "Europe/Kiev",
    "Europe/Kiev" to "Europe/Kyiv",
    "Asia/Tel_Aviv" to "Asia/Jerusalem"
)

fun resolveZone(id: String): ZoneId? =
    try {
        ZoneId.of(id)
    } catch (e: Exception) {
        ZONE_ALIASES[id]?.let { alias -> try { ZoneId.of(alias) } catch (e2: Exception) { null } }
    }

private val IL_ZONES = setOf("Asia/Jerusalem", "Asia/Tel_Aviv", "Asia/Hebron", "Asia/Gaza", "Israel")
fun inIsrael(zone: ZoneId): Boolean = zone.id in IL_ZONES

/** Город по часовому поясу устройства; нет такого — Вена. */
fun cityForZone(zone: ZoneId = ZoneId.systemDefault()): CityOption =
    CITIES.find { it.id == (if (zone.id == "Europe/Kyiv") "Europe/Kiev" else zone.id) }
        ?: CITIES.find { it.zoneId == zone.id }
        ?: CITIES.first { it.id == "Europe/Vienna" }

/** Город по id или по прежнему английскому имени. */
fun cityById(id: String?): CityOption? =
    if (id == null) null else CITIES.find { it.id == id } ?: CITIES.find { it.name == id }

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}0-9]+"), " ")
        .trim()

/** Города, в имени которых (ru, en, de) есть слово с начала запроса; сначала — с начала имени. Как на сайте. */
fun findCities(query: String, max: Int = 12): List<CityOption> {
    val n = norm(query)
    if (n.isEmpty()) return emptyList()
    return CITIES.mapNotNull { c ->
        val names = listOfNotNull(c.nameRu, c.name, c.nameDe, UK_CITY[c.nameRu]).map(::norm)
        var best = 9
        for (s in names) {
            best = minOf(best, when {
                s.startsWith(n) -> 0
                s.split(' ').any { it.startsWith(n) } -> 1
                n.length >= 3 && s.contains(n) -> 2
                else -> 9
            })
        }
        if (best < 9) best to c else null
    }.sortedBy { it.first }.take(max).map { it.second }
}

/** Украинские названия городов — по русскому (одинаковые не повторяются). */
internal val UK_CITY: Map<String, String> = mapOf(
    "Иерусалим" to "Єрусалим",
    "Тель-Авив" to "Тель-Авів",
    "Калининград" to "Калінінград",
    "Ульяновск" to "Ульяновськ",
    "Киров" to "Кіров",
    "Екатеринбург" to "Єкатеринбург",
    "Омск" to "Омськ",
    "Новосибирск" to "Новосибірськ",
    "Томск" to "Томськ",
    "Новокузнецк" to "Новокузнецьк",
    "Красноярск" to "Красноярськ",
    "Иркутск" to "Іркутськ",
    "Якутск" to "Якутськ",
    "Хабаровск" to "Хабаровськ",
    "Южно-Сахалинск" to "Южно-Сахалінськ",
    "Петропавловск-Камчатский" to "Петропавловськ-Камчатський",
    "Киев" to "Київ",
    "Симферополь" to "Сімферополь",
    "Минск" to "Мінськ",
    "Кишинёв" to "Кишинів",
    "Вильнюс" to "Вільнюс",
    "Таллин" to "Таллінн",
    "Тбилиси" to "Тбілісі",
    "Ереван" to "Єреван",
    "Алматы" to "Алмати",
    "Бишкек" to "Бішкек",
    "Берлин" to "Берлін",
    "Вена" to "Відень",
    "Бюзинген" to "Бюзінген",
    "София" to "Софія",
    "Афины" to "Афіни",
    "Никосия" to "Нікосія",
    "Хельсинки" to "Гельсінкі",
    "Дублин" to "Дублін",
    "Лиссабон" to "Лісабон",
    "Финикс" to "Фінікс",
    "Мехико" to "Мехіко",
    "Буэнос-Айрес" to "Буенос-Айрес",
    "Йоханнесбург" to "Йоганнесбург",
    "Сидней" to "Сідней",
    "Брисбен" to "Брісбен",
    "Токио" to "Токіо",
    "Сингапур" to "Сінгапур",
    "Мумбаи" to "Мумбаї",
    "Тверия" to "Тверія",
    "Нетания" to "Нетанія",
    "Ришон-ле-Цион" to "Рішон-ле-Ціон",
    "Петах-Тиква" to "Петах-Тіква",
    "Эйлат" to "Ейлат",
    "Ноф-ха-Галиль" to "Ноф-га-Ґаліль",
    "Кармиэль" to "Карміель",
    "Нижний Новгород" to "Нижній Новгород",
    "Сочи" to "Сочі",
    "Пермь" to "Перм",
    "Челябинск" to "Челябінськ",
    "Одесса" to "Одеса",
    "Днепр" to "Дніпро",
    "Харьков" to "Харків",
    "Запорожье" to "Запоріжжя",
    "Львов" to "Львів",
    "Винница" to "Вінниця",
    "Витебск" to "Вітебськ",
    "Даугавпилс" to "Даугавпілс",
    "Батуми" to "Батумі",
    "Инсбрук" to "Інсбрук",
    "Линц" to "Лінц",
    "Франкфурт-на-Майне" to "Франкфурт-на-Майні",
    "Кёльн" to "Кельн",
    "Эссен" to "Ессен",
    "Мангейм" to "Мангайм",
    "Карлсруэ" to "Карлсруе",
    "Гейтсхед" to "Гейтсгед",
    "Лидс" to "Лідс",
    "Милан" to "Мілан",
    "Венеция" to "Венеція",
    "Ницца" to "Ніцца",
    "Лион" to "Ліон",
    "Краков" to "Краків",
    "Салоники" to "Салоніки",
    "Лимасол" to "Лімасол",
    "Анталья" to "Анталія",
    "Бруклин (Краун-Хайтс)" to "Бруклін (Краун-Гайтс)",
    "Монси" to "Монсі",
    "Филадельфия" to "Філадельфія",
    "Майами" to "Маямі",
    "Кливленд" to "Клівленд",
    "Питтсбург" to "Піттсбург",
    "Хьюстон" to "Г'юстон",
    "Сан-Диего" to "Сан-Дієго",
    "Сиэтл" to "Сіетл",
    "Рио-де-Жанейро" to "Ріо-де-Жанейро"
)
