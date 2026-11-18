package app.mychitas.peleshaon

import app.mychitas.peleshaon.domain.CITIES
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.ZmanimConfig
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.domain.ZmanimService
import app.mychitas.peleshaon.domain.resolveZone
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * All 337 shipping locations: actual Kotlin time-engine output, not a copy
 * of a third-party calendar. This does NOT independently confirm 337 times
 * against Chabad.org: each external city/date still needs a verified match.
 */
class AllCityZmanimAuditTest {
    @Test fun exportAndValidateEveryShippingCityAcrossSeasonsAndDst() {
        val dates = listOf(
            "2026-01-16", "2026-02-13", "2026-03-08", "2026-03-27",
            "2026-03-29", "2026-04-03", "2026-06-19", "2026-07-17",
            "2026-09-25", "2026-10-23", "2026-10-25", "2026-10-30",
            "2026-11-01", "2026-11-20", "2026-12-18", "2027-01-08"
        ).map(LocalDate::parse)
        val expected = 337
        assertEquals(expected, CITIES.size, "The shipping city catalogue changed")
        assertEquals(expected, CITIES.map { it.id }.toSet().size, "Duplicate city ID")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
        fun stamp(value: Instant?, zone: ZoneId): String =
            value?.atZone(zone)?.format(fmt) ?: "UNDEFINED"
        fun log(valStr: String): String =
            valStr.replace("\t", " ").replace("\n", " ")
        val main = mutableListOf(
            "id\tname_en\tname_uk\tname_ru\tzone\tdate\tutc_offset\tlat\tlon\tis_friday\t" +
            "dawn\tsunrise\tshema\tshacharit\tchatzot\tmincha_gedola\tplag\tsunset\t" +
            "ordinary_tzait\tcandles_if_friday\thavdalah_if_friday\tstatus\tissues"
        )
        val citySummary = mutableListOf(
            "id\tname_en\tname_uk\tname_ru\tzone\tlat\tlon\tchecked_dates\t" +
            "fully_defined_dates\tpolar_or_undefined_dates\tissues\t" +
            "chabad_dot_org_independent_status"
        )
        val problems = mutableListOf<String>()
        var polarOrUndefined = 0
        var complete = 0
        val prevMinhag = ZmanimEngine.minhag
        val prevOverride = ZmanimEngine.candleOffsetOverride
        try {
            ZmanimEngine.minhag = Minhag.CHABAD
            ZmanimEngine.candleOffsetOverride = null
            for (city in CITIES) {
                val cityProblems = mutableListOf<String>()
                if (city.id.isBlank() || city.name.isBlank() || city.nameRu.isBlank())
                    cityProblems += "empty_id_or_name"
                if (!city.lat.isFinite() || city.lat !in -90.0..90.0 ||
                    !city.lon.isFinite() || city.lon !in -180.0..180.0)
                    cityProblems += "invalid_coordinates"
                val zone = resolveZone(city.zoneId)
                if (zone == null) {
                    cityProblems += "invalid_timezone_fallback_to_device"
                    // A null fallback is not safe for religious times; record it
                    // and fail after writing the per-city evidence.
                }
                val tz = zone ?: ZoneId.of("UTC")
                var undefinedDates = 0
                var definedDates = 0
                for (date in dates) {
                    val issues = mutableListOf<String>()
                    val z = ZmanimService.calculate(date, ZmanimConfig(city, Minhag.CHABAD))
                    val fri = date.dayOfWeek == DayOfWeek.FRIDAY
                    val sat = if (fri) ZmanimService.calculate(
                        date.plusDays(1), ZmanimConfig(city, Minhag.CHABAD)
                    ) else null
                    val events = ZmanimEngine.itemsOf(date, city)
                    val dawn = z.dawn
                    val rise = z.sunrise
                    val set = z.sunset
                    val end = sat?.shabbatEnd
                    val expectedDeadline = setOf(ZmanType.ALOT, ZmanType.SOF_ZMAN_SHMA,
                        ZmanType.SOF_ZMAN_TFILA, ZmanType.CHATZOT, ZmanType.SUNSET)
                    val expectedEarliest = setOf(ZmanType.SUNRISE, ZmanType.MINCHA_GEDOLA,
                        ZmanType.PLAG_HAMINCHA, ZmanType.TZAIT)
                    for (e in events) {
                        if (e.type in expectedDeadline &&
                            e.roundedTime.isAfter(e.exactTime))
                            issues += "rounded_deadline_late_${e.type}"
                        if (e.type in expectedEarliest &&
                            e.roundedTime.isBefore(e.exactTime))
                            issues += "rounded_earliest_early_${e.type}"
                    }
                    if (rise != null && set != null && !rise.isBefore(set))
                        issues += "sunrise_not_before_sunset"
                    if (dawn != null && rise != null && !dawn.isBefore(rise))
                        issues += "dawn_not_before_sunrise"
                    if (z.ordinaryTzait != null && set != null &&
                        !z.ordinaryTzait.isAfter(set))
                        issues += "nightfall_not_after_sunset"
                    if (z.shabbatEnd != null && set != null &&
                        !z.shabbatEnd.isAfter(set))
                        issues += "shabbat_end_not_after_sunset"
                    val offset = ZmanimService.candleMinutes(ZmanimConfig(city, Minhag.CHABAD))
                    if (set != null && z.candleLighting != set.minus(Duration.ofMinutes(offset)))
                        issues += "candles_not_sunset_minus_local_offset"
                    if (fri && sat?.sunset != null && end != null &&
                        !end.isAfter(sat.sunset))
                        issues += "havdalah_not_after_saturday_sunset"

                    if (rise == null || set == null || dawn == null ||
                        z.ordinaryTzait == null || (fri && end == null)) {
                        undefinedDates++
                        polarOrUndefined++
                    } else {
                        definedDates++
                        complete++
                    }
                    val rowIssues = (cityProblems + issues).distinct()
                    if (issues.isNotEmpty()) problems +=
                        "${city.id}\t${date}\t${issues.joinToString(";")}"
                    main += listOf(
                        city.id, log(city.name), log(city.nameUk ?: ""), log(city.nameRu),
                        tz.id, date.toString(), tz.rules.getOffset(date.atTime(12, 0).atZone(tz).toInstant()).id,
                        city.lat.toString(), city.lon.toString(), fri.toString(),
                        stamp(dawn, tz), stamp(rise, tz), stamp(z.shema, tz),
                        stamp(z.tfila, tz), stamp(z.chatzot, tz),
                        stamp(z.minchaGedola, tz), stamp(z.plagHamincha, tz),
                        stamp(set, tz), stamp(z.ordinaryTzait, tz),
                        if (fri) stamp(z.candleLighting, tz) else "NOT_FRIDAY",
                        if (fri) stamp(end, tz) else "NOT_FRIDAY",
                        if (rowIssues.isNotEmpty()) "ERROR" else if (rise == null || set == null)
                            "ASTRONOMICAL_UNDEFINED" else "COMPUTED",
                        rowIssues.joinToString(";")
                    ).joinToString("\t")
                }
                if (cityProblems.isNotEmpty())
                    problems += "${city.id}\tall\t${cityProblems.joinToString(";")}"
                citySummary += listOf(
                    city.id, log(city.name), log(city.nameUk ?: ""), log(city.nameRu),
                    tz.id, city.lat.toString(), city.lon.toString(), dates.size.toString(),
                    definedDates.toString(), undefinedDates.toString(),
                    cityProblems.joinToString(";"), "NOT_EXTERNALLY_VERIFIED_IN_THIS_TEST"
                ).joinToString("\t")
            }
        } finally {
            ZmanimEngine.minhag = prevMinhag
            ZmanimEngine.candleOffsetOverride = prevOverride
        }
        val dir = File("build/reports")
        dir.mkdirs()
        File(dir, "all-337-zmanim-by-date.tsv").writeText(
            main.joinToString("\n") + "\n", Charsets.UTF_8)
        File(dir, "all-337-zmanim-city-summary.tsv").writeText(
            citySummary.joinToString("\n") + "\n", Charsets.UTF_8)
        File(dir, "all-337-zmanim-audit-summary.txt").writeText(
            "Catalog cities: ${CITIES.size}\nDates each: ${dates.size}\n" +
            "Calculation rows: ${main.size-1}\nAll key events defined: ${complete}\n" +
            "One or more astronomical times unavailable: ${polarOrUndefined}\n" +
            "Recorded data/logic issues: ${problems.size}\n" +
            problems.joinToString("\n") + "\n", Charsets.UTF_8)
        assertEquals(expected + 1, citySummary.size)
        assertEquals(expected * dates.size + 1, main.size)
        assertTrue(problems.isEmpty(), "See build/reports/all-337-zmanim-audit-summary.txt: ${problems.take(8)}")
    }
}
