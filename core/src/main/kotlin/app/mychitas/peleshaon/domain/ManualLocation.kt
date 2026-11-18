package app.mychitas.peleshaon.domain

import java.time.ZoneId

/**
 * Explicit user-provided coordinates. No online geocoder or guessed fallback.
 * Saved custom locations must be validated every time they are loaded.
 */
object ManualLocation {
    const val ID = "user:custom"

    fun parse(name: String?, latitude: String?, longitude: String?, zoneId: String?, isIsrael: Boolean = false): CityOption? {
        val title = name?.trim().orEmpty()
        if (title.length !in 2..80) return null
        val lat = latitude?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val lon = longitude?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val zone = zoneId?.trim()?.let(::resolveZone) ?: return null
        return CityOption(ID, title, title, null, lat, lon, zone.id, isIsrael, nameUk = title)
    }

    /** Explicit zone override for occupied-territory local clocks and other civil-time changes. */
    fun zoneOrNull(input: String?): ZoneId? = input?.trim()?.let(::resolveZone)
}
