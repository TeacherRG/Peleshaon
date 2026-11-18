package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanimConfig
import app.mychitas.peleshaon.domain.ZmanimService
import app.mychitas.peleshaon.domain.cityById
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Produces the *actual app engine* timestamps with seconds for an external,
 * human-reviewed comparison against Chabad.org. No third-party numbers or
 * cached proprietary data are embedded into or fetched by the application.
 * The published file is an internal engineering probe, NOT Chabad's output.
 */
class ChabadTimesProbeTest {
    @Test fun exportRepresentativeCitiesAndSeasons() {
        val cityIds = listOf(
            "dnipro", "Europe/Kiev", "odesa", "lviv", "ua:77:chernivci",
            "ua:12:kamianske", "ua:14:kramatorsk", "Europe/Berlin",
            "Europe/Vienna", "Europe/Vilnius", "Europe/London",
            "Asia/Jerusalem", "Asia/Tel_Aviv", "brooklyn", "Europe/Simferopol"
        )
        val dates = listOf(
            LocalDate.of(2026, 3, 27), // spring DST
            LocalDate.of(2026, 6, 19), // summer and long twilight
            LocalDate.of(2026, 9, 25),
            LocalDate.of(2026, 10, 2),
            LocalDate.of(2026, 10, 16),
            LocalDate.of(2026, 10, 23),
            LocalDate.of(2026, 10, 30), // after autumn EU transition
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 13),
            LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 11, 27)
        )
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
        fun local(instant: java.time.Instant?, zone: java.time.ZoneId): String =
            instant?.atZone(zone)?.format(fmt) ?: "UNDEFINED"
        val lines = mutableListOf(
            "id\tdate\tzone\tlat\tlon\tdawn\tsunrise\tshema\tshacharit\tchatzot\tmincha_gedola\tplag\tsunset\tordinary_tzait\tcandle_lighting\thavdalah_next_day"
        )
        for (id in cityIds) {
            val city = requireNotNull(cityById(id)) { "Missing audit city: $id" }
            for (date in dates) {
                val conf = ZmanimConfig(city, Minhag.CHABAD)
                val snap = ZmanimService.calculate(date, conf)
                val nextDay = ZmanimService.calculate(date.plusDays(1), conf)
                val zone = city.zone()
                lines += listOf(
                    id, date.toString(), zone.id, city.lat.toString(), city.lon.toString(),
                    local(snap.dawn, zone), local(snap.sunrise, zone),
                    local(snap.shema, zone), local(snap.tfila, zone),
                    local(snap.chatzot, zone), local(snap.minchaGedola, zone),
                    local(snap.plagHamincha, zone), local(snap.sunset, zone),
                    local(snap.ordinaryTzait, zone), local(snap.candleLighting, zone),
                    local(nextDay.shabbatEnd, zone)
                ).joinToString("\t")
            }
        }
        val out = File("build/reports/chabad-engine-probe.tsv")
        out.parentFile.mkdirs()
        out.writeText(lines.joinToString("\n") + "\n", Charsets.UTF_8)
        assertTrue(out.length() > 1_000L)
        assertTrue(lines.size == cityIds.size * dates.size + 1)
    }
}
