package app.mychitas.peleshaon.domain

import java.time.ZoneId

data class CityOption(
    val name: String,
    val lat: Double,
    val lon: Double,
    val zoneId: String,
    val isIsrael: Boolean
)

val DEFAULT_CITIES = listOf(
    CityOption("Vienna", 48.2082, 16.3738, "Europe/Vienna", false),
    CityOption("Jerusalem", 31.7683, 35.2137, "Asia/Jerusalem", true),
    CityOption("Tel Aviv", 32.0853, 34.7818, "Asia/Jerusalem", true),
    CityOption("Tzfat", 32.9646, 35.4960, "Asia/Jerusalem", true),
    CityOption("New York (Crown Heights)", 40.6694, -73.9422, "America/New_York", false),
    CityOption("Moscow", 55.7558, 37.6173, "Europe/Moscow", false),
    CityOption("Berlin", 52.5200, 13.4050, "Europe/Berlin", false),
    CityOption("London", 51.5074, -0.1278, "Europe/London", false)
)

/** Город по часовому поясу устройства; нет такого — первый в списке. */
fun cityForZone(zone: ZoneId = ZoneId.systemDefault()): CityOption =
    DEFAULT_CITIES.find { it.zoneId == zone.id } ?: DEFAULT_CITIES.first()
