package app.mychitas.peleshaon.reminder

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Форматы файла с напоминаниями. */
enum class CalendarFormat(val extension: String, val mime: String) {
    /** iCalendar 2.0 (RFC 5545) — Google Календарь, Apple, Outlook, Samsung, Thunderbird. */
    ICS("ics", "text/calendar"),
    /** vCalendar 1.0 — старые телефоны и органайзеры. */
    VCS("vcs", "text/x-vcalendar")
}

/** Итог чтения файла: напоминания, пропущенные прошедшие события и события, правило повтора которых упрощено. */
data class ImportResult(
    val reminders: List<Reminder>,
    val skippedPast: Int,
    val simplified: Int
)

/**
 * Чтение и запись напоминаний в стандартных календарных форматах — без сети: файл выбирает пользователь.
 *
 * Google Календарь: «Настройки → Импорт и экспорт → Экспорт» даёт ZIP с файлами .ics — их и читаем
 * (ZIP распаковывает приложение). Обратно — «Импорт» того же меню принимает наш .ics.
 *
 * Повтор по еврейской дате в iCalendar не выразить (RRULE знает только григорианский календарь), поэтому
 * такие напоминания выгружаются отдельными событиями на [HEBREW_EXPORT_YEARS] лет вперёд, с пометкой
 * `X-PELE-REPEAT` — при импорте в Пеле Шаон они снова сворачиваются в одно повторяющееся напоминание.
 */
object CalendarFiles {

    const val HEBREW_EXPORT_YEARS = 10L
    private const val PRODID = "-//Pele Shaon Luach//Calendar reminders//EN"
    private const val X_REPEAT = "X-PELE-REPEAT"
    private const val X_SOURCE = "X-PELE-SOURCE-UID"
    private const val X_INTERVAL = "X-PELE-INTERVAL"
    private const val X_UNTIL = "X-PELE-UNTIL"

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    private val ICAL_DAYS = mapOf(
        DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU"
    )
    private val DAYS_BY_CODE = ICAL_DAYS.entries.associate { it.value to it.key }

    // ------------------------------------------------------------------ export

    fun export(
        reminders: List<Reminder>, zone: ZoneId, format: CalendarFormat,
        calendarName: String = "Pele Shaon Luach", now: Instant = Instant.now()
    ): String = when (format) {
        CalendarFormat.ICS -> exportIcs(reminders, zone, calendarName, now)
        CalendarFormat.VCS -> exportVcs(reminders, zone, now)
    }

    /** Хебрейские повторы — отдельными днями; гражданские — одним событием с RRULE. */
    private fun expandHebrew(r: Reminder, today: LocalDate): List<LocalDate> {
        val from = maxOf(r.date, today.minusYears(1))
        return ReminderRules.occurrences(r, from, today.plusYears(HEBREW_EXPORT_YEARS))
    }

    private fun exportIcs(reminders: List<Reminder>, zone: ZoneId, name: String, now: Instant): String {
        val out = StringBuilder()
        fun line(s: String) = out.append(fold(s)).append("\r\n")
        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:$PRODID")
        line("CALSCALE:GREGORIAN")
        line("METHOD:PUBLISH")
        line("X-WR-CALNAME:${escape(name)}")
        line("X-WR-TIMEZONE:${zone.id}")
        val needsZone = reminders.any { it.time != null }
        if (needsZone && zone !is ZoneOffset) vtimezone(zone, now, ::line)
        val stamp = UTC.format(now)
        val today = now.atZone(zone).toLocalDate()

        fun event(r: Reminder, date: LocalDate, uid: String, rrule: String?, hebrew: Boolean) {
            line("BEGIN:VEVENT")
            line("UID:${escape(uid)}")
            line("DTSTAMP:$stamp")
            if (r.time == null) {
                line("DTSTART;VALUE=DATE:${DATE.format(date)}")
                line("DTEND;VALUE=DATE:${DATE.format(date.plusDays(1))}")
                line("TRANSP:TRANSPARENT")
            } else {
                val start = LocalDateTime.of(date, r.time)
                line(if (zone is ZoneOffset) "DTSTART:${UTC.format(ReminderRules.startOn(r, date, zone).toInstant())}"
                     else "DTSTART;TZID=${zone.id}:${LOCAL.format(start)}")
                line("DURATION:PT0S")
            }
            line("SUMMARY:${escape(r.title)}")
            if (r.note.isNotBlank()) line("DESCRIPTION:${escape(r.note)}")
            if (rrule != null) line("RRULE:$rrule")
            if (hebrew) {
                line("$X_SOURCE:${escape(r.id)}")
                line("$X_REPEAT:${r.repeat.name}")
                line("$X_INTERVAL:${r.interval}")
                line("X-PELE-DATE:${DATE.format(r.date)}")
                r.until?.let { line("$X_UNTIL:${DATE.format(it)}") }
            }
            r.alertMinutes?.let {
                line("BEGIN:VALARM")
                line("ACTION:DISPLAY")
                line("DESCRIPTION:${escape(r.title.ifBlank { "Reminder" })}")
                line("TRIGGER:${trigger(it)}")
                line("END:VALARM")
            }
            line("END:VEVENT")
        }

        for (r in reminders) {
            if (r.repeat.hebrew) {
                for (d in expandHebrew(r, today)) event(r, d, "${r.id}-${DATE.format(d)}", null, hebrew = true)
            } else {
                event(r, r.date, r.id, rrule(r, zone), hebrew = false)
            }
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    private fun rrule(r: Reminder, zone: ZoneId): String? {
        val freq = when (r.repeat) {
            Repeat.DAILY -> "DAILY"
            Repeat.WEEKLY -> "WEEKLY"
            Repeat.MONTHLY -> "MONTHLY"
            Repeat.YEARLY -> "YEARLY"
            else -> return null
        }
        val parts = mutableListOf("FREQ=$freq")
        if (r.interval > 1) parts += "INTERVAL=${r.interval}"
        if (r.repeat == Repeat.WEEKLY && r.byDay.isNotEmpty())
            parts += "BYDAY=" + r.byDay.sortedBy { it.value % 7 }.joinToString(",") { ICAL_DAYS.getValue(it) }
        // 29–31-е и 29 февраля: по RFC месяцы без такого числа пропускаются, у нас — последний день месяца.
        val day = r.date.dayOfMonth
        if (r.repeat == Repeat.MONTHLY && day > 28) {
            parts += if (day == 31) "BYMONTHDAY=-1" else "BYMONTHDAY=" + (28..day).joinToString(",") + ";BYSETPOS=-1"
        }
        if (r.repeat == Repeat.YEARLY && r.date.monthValue == 2 && day == 29) parts += "BYMONTH=2;BYMONTHDAY=-1"
        r.until?.let {
            // UNTIL обязан быть того же вида, что DTSTART: дата — для весь-день, UTC — для события со временем.
            parts += if (r.time == null) "UNTIL=${DATE.format(it)}"
            else "UNTIL=" + UTC.format(ReminderRules.startOn(r, it, zone).toInstant())
        }
        return parts.joinToString(";")
    }

    /** Минуты до начала → TRIGGER (RFC 5545 duration): 15 → -PT15M, −540 → PT9H, 900 → -PT15H. */
    fun trigger(minutesBefore: Int): String {
        if (minutesBefore == 0) return "PT0S"
        val sign = if (minutesBefore > 0) "-" else ""
        var m = kotlin.math.abs(minutesBefore)
        val sb = StringBuilder("P")
        if (m % (7 * 1440) == 0) return "${sign}P${m / (7 * 1440)}W"
        val days = m / 1440
        m %= 1440
        if (days > 0) sb.append(days).append('D')
        if (m > 0) {
            sb.append('T')
            if (m / 60 > 0) sb.append(m / 60).append('H')
            if (m % 60 > 0) sb.append(m % 60).append('M')
        }
        return sign + sb
    }

    /** VTIMEZONE по правилам Java: переходы на год назад и [HEBREW_EXPORT_YEARS] лет вперёд. */
    private fun vtimezone(zone: ZoneId, now: Instant, line: (String) -> Unit) {
        val rules = zone.rules
        line("BEGIN:VTIMEZONE")
        line("TZID:${zone.id}")
        val from = now.atZone(zone).minusYears(1).toInstant()
        val to = now.atZone(zone).plusYears(HEBREW_EXPORT_YEARS + 1).toInstant()
        var t = rules.previousTransition(from)?.let { rules.nextTransition(it.instant.minusSeconds(1)) }
            ?: rules.nextTransition(from)
        var wrote = false
        while (t != null && !t.instant.isAfter(to)) {
            val daylight = rules.isDaylightSavings(t.instant)
            val kind = if (daylight) "DAYLIGHT" else "STANDARD"
            line("BEGIN:$kind")
            line("DTSTART:${LOCAL.format(t.dateTimeBefore)}")
            line("TZOFFSETFROM:${offset(t.offsetBefore)}")
            line("TZOFFSETTO:${offset(t.offsetAfter)}")
            line("END:$kind")
            wrote = true
            t = rules.nextTransition(t.instant)
        }
        if (!wrote) {
            val off = rules.getOffset(now)
            line("BEGIN:STANDARD")
            line("DTSTART:19700101T000000")
            line("TZOFFSETFROM:${offset(off)}")
            line("TZOFFSETTO:${offset(off)}")
            line("END:STANDARD")
        }
        line("END:VTIMEZONE")
    }

    private fun offset(o: ZoneOffset): String {
        val s = o.totalSeconds
        val sign = if (s < 0) "-" else "+"
        val a = kotlin.math.abs(s)
        return "%s%02d%02d".format(sign, a / 3600, (a % 3600) / 60)
    }

    private fun exportVcs(reminders: List<Reminder>, zone: ZoneId, now: Instant): String {
        val out = StringBuilder()
        fun line(s: String) = out.append(s).append("\r\n")
        line("BEGIN:VCALENDAR")
        line("VERSION:1.0")
        line("PRODID:$PRODID")
        val today = now.atZone(zone).toLocalDate()
        fun event(r: Reminder, date: LocalDate, uid: String, rrule: String?) {
            line("BEGIN:VEVENT")
            line("UID:$uid")
            line(qp("SUMMARY", r.title))
            if (r.note.isNotBlank()) line(qp("DESCRIPTION", r.note))
            // vCalendar 1.0 не знает «весь день»: с 00:00 до 23:59 местного времени — так пишут и Nokia, и Outlook.
            if (r.time == null) {
                line("DTSTART:${LOCAL.format(date.atStartOfDay())}")
                line("DTEND:${LOCAL.format(date.atTime(23, 59))}")
            } else {
                val start = ReminderRules.startOn(r, date, zone)
                line("DTSTART:${UTC.format(start.toInstant())}")
                line("DTEND:${UTC.format(start.toInstant())}")
            }
            if (rrule != null) line("RRULE:$rrule")
            r.alertMinutes?.let {
                val at = ReminderRules.startOn(r, date, zone).minusMinutes(it.toLong())
                line("DALARM:${UTC.format(at.toInstant())}")
                line("AALARM:${UTC.format(at.toInstant())}")
            }
            line("END:VEVENT")
        }
        for (r in reminders) {
            if (r.repeat.hebrew) for (d in expandHebrew(r, today)) event(r, d, "${r.id}-${DATE.format(d)}", null)
            else event(r, r.date, r.id, vcsRule(r, zone))
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    /** RRULE vCalendar 1.0: D1, W1 MO TU, MD1 15, YM1 12; «#0» — без конца, иначе дата конца. */
    private fun vcsRule(r: Reminder, zone: ZoneId): String? {
        val n = r.interval.coerceAtLeast(1)
        val head = when (r.repeat) {
            Repeat.DAILY -> "D$n"
            Repeat.WEEKLY -> "W$n " + r.byDay.ifEmpty { setOf(r.date.dayOfWeek) }
                .sortedBy { it.value % 7 }.joinToString(" ") { ICAL_DAYS.getValue(it) }
            Repeat.MONTHLY -> "MD$n ${r.date.dayOfMonth}"
            Repeat.YEARLY -> "YM$n ${r.date.monthValue}"
            else -> return null
        }
        val end = r.until?.let { UTC.format(ReminderRules.startOn(r, it, zone).toInstant()) } ?: "#0"
        return "$head $end"
    }

    private fun qp(name: String, value: String): String {
        if (value.all { it.code in 32..126 && it != '=' }) return "$name:$value"
        val bytes = value.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if (c in 33..126 && c != '='.code) sb.append(c.toChar()) else sb.append("=%02X".format(c))
        }
        return "$name;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:$sb"
    }

    /** RFC 5545 §3.3.11 */
    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
        .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")

    /** RFC 5545 §3.1: строки длиннее 75 октетов переносятся; не разрываем UTF-8 посреди символа. */
    private fun fold(s: String): String {
        if (s.toByteArray(Charsets.UTF_8).size <= 75) return s
        val sb = StringBuilder()
        var bytes = 0
        var limit = 75
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val len = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + len > limit) {
                sb.append("\r\n ")
                bytes = 0
                limit = 74
            }
            sb.appendCodePoint(cp)
            bytes += len
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ import

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    private class Component(val name: String) {
        val props = mutableListOf<Prop>()
        val children = mutableListOf<Component>()
        fun first(n: String) = props.firstOrNull { it.name == n }
    }

    /**
     * Читает iCalendar 2.0 или vCalendar 1.0. Времена переводятся в [zone] (зона города в приложении).
     * Прошедшие однократные события (и повторы, закончившиеся до [today]) пропускаются — в выгрузке
     * Google Календаря их тысячи. Отменённые события и изменённые экземпляры повторов (RECURRENCE-ID) не берутся.
     */
    fun parse(text: String, zone: ZoneId, today: LocalDate): ImportResult {
        val root = Component("ROOT")
        val stack = ArrayDeque<Component>().apply { addLast(root) }
        for (raw in unfold(text)) {
            val p = parseLine(raw) ?: continue
            when (p.name) {
                "BEGIN" -> Component(p.value.trim().uppercase()).also { stack.last().children += it; stack.addLast(it) }
                "END" -> if (stack.size > 1) stack.removeLast()
                else -> stack.last().props += p
            }
        }
        val events = mutableListOf<Component>()
        fun collect(c: Component) {
            if (c.name == "VEVENT") events += c
            c.children.forEach(::collect)
        }
        collect(root)

        val out = LinkedHashMap<String, Reminder>()
        var past = 0
        var simplified = 0
        for (e in events) {
            if (e.first("STATUS")?.value?.uppercase() == "CANCELLED") continue
            if (e.first("RECURRENCE-ID") != null) continue
            val start = e.first("DTSTART") ?: continue
            val (date, time) = dateTime(start, zone) ?: continue
            val title = unescape(e.first("SUMMARY")?.value ?: "").trim()
            val note = unescape(e.first("DESCRIPTION")?.value ?: "").trim()

            // собственная выгрузка с повтором по еврейской дате — свернуть обратно
            val source = e.first(X_SOURCE)?.value?.let(::unescape)
            if (source != null) {
                if (source in out) continue
                val repeat = runCatching { Repeat.valueOf(e.first(X_REPEAT)!!.value.trim()) }.getOrNull()
                val first = e.first("X-PELE-DATE")?.value?.let { runCatching { LocalDate.parse(it.trim(), DATE) }.getOrNull() } ?: date
                if (repeat != null) {
                    val r = Reminder(
                        id = source, title = title, note = note, date = first, time = time, repeat = repeat,
                        interval = e.first(X_INTERVAL)?.value?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        until = e.first(X_UNTIL)?.value?.let { runCatching { LocalDate.parse(it.trim(), DATE) }.getOrNull() },
                        alertMinutes = alert(e, date, time, zone)
                    )
                    if (r.until != null && r.until.isBefore(today)) past++ else out[source] = r
                    continue
                }
            }

            val uid = e.first("UID")?.value?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString()
            var r = Reminder(id = uid, title = title, note = note, date = date, time = time,
                alertMinutes = alert(e, date, time, zone))
            e.first("RRULE")?.let { rule ->
                val parsed = if (rule.value.contains("FREQ=", ignoreCase = true)) rrule2(rule.value, r, zone)
                             else rrule1(rule.value, r, zone)
                r = parsed.first
                if (parsed.second) simplified++
            }
            val last = when {
                r.repeat == Repeat.NONE -> r.date
                else -> r.until
            }
            if (last != null && last.isBefore(today)) { past++; continue }
            out[uid] = r
        }
        return ImportResult(out.values.toList(), past, simplified)
    }

    /** Разворачивает перенесённые строки (RFC 5545 §3.1) и мягкие переносы quoted-printable (vCalendar). */
    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = mutableListOf<String>()
        for (l in lines) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + l.substring(1)
            } else if (out.isNotEmpty() && out.last().endsWith("=") &&
                out.last().substringBefore(':').contains("QUOTED-PRINTABLE", ignoreCase = true)) {
                out[out.size - 1] = out.last().dropLast(1) + l
            } else out += l
        }
        return out.filter { it.isNotBlank() }
    }

    private fun parseLine(line: String): Prop? {
        var quoted = false
        var colon = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') quoted = !quoted
            else if (c == ':' && !quoted) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = mutableMapOf<String, String>()
        for (p in head.drop(1)) {
            val eq = p.indexOf('=')
            // vCalendar 1.0 допускает параметры без имени: «;QUOTED-PRINTABLE»
            if (eq < 0) params[p.uppercase()] = p.uppercase()
            else params[p.substring(0, eq).uppercase()] = p.substring(eq + 1).trim('"')
        }
        var value = line.substring(colon + 1)
        if (params["ENCODING"]?.uppercase() == "QUOTED-PRINTABLE" || "QUOTED-PRINTABLE" in params) {
            value = decodeQp(value, params["CHARSET"])
        }
        // Имя свойства может быть с группой: «item1.SUMMARY»
        return Prop(head[0].substringAfterLast('.').uppercase(), params, value)
    }

    private fun decodeQp(s: String, charset: String?): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val h = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (h != null) { bytes.write(h); i += 3; continue }
            }
            bytes.write(c.code and 0xFF)
            i++
        }
        val cs = runCatching { charset(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes.toByteArray(), cs)
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun zoneOf(tzid: String?, fallback: ZoneId): ZoneId {
        if (tzid == null) return fallback
        val clean = tzid.trim().removePrefix("/")
        runCatching { return ZoneId.of(clean) }
        // «/mozilla.org/20050126_1/Europe/Berlin», Windows-имена Outlook — частые случаи
        Regex("([A-Za-z]+/[A-Za-z_]+(?:/[A-Za-z_]+)?)$").find(clean)?.let { m ->
            runCatching { return ZoneId.of(m.value) }
        }
        WINDOWS_ZONES[clean]?.let { return ZoneId.of(it) }
        return fallback
    }

    private val WINDOWS_ZONES = mapOf(
        "Israel Standard Time" to "Asia/Jerusalem", "FLE Standard Time" to "Europe/Kiev",
        "Russian Standard Time" to "Europe/Moscow", "W. Europe Standard Time" to "Europe/Berlin",
        "GMT Standard Time" to "Europe/London", "Eastern Standard Time" to "America/New_York",
        "Central Standard Time" to "America/Chicago", "Pacific Standard Time" to "America/Los_Angeles",
        "E. Europe Standard Time" to "Europe/Chisinau", "Central European Standard Time" to "Europe/Warsaw",
        "Romance Standard Time" to "Europe/Paris", "UTC" to "UTC"
    )

    /** DTSTART → день и время в [zone]; весь день — время null. */
    private fun dateTime(p: Prop, zone: ZoneId): Pair<LocalDate, LocalTime?>? {
        val v = p.value.trim()
        if (p.params["VALUE"]?.uppercase() == "DATE" || v.length == 8) {
            return runCatching { LocalDate.parse(v.take(8), DATE) to null }.getOrNull()
        }
        val zdt = instantOf(v, p.params["TZID"], zone) ?: return null
        val local = zdt.withZoneSameInstant(zone)
        // vCalendar «весь день» 00:00 — оставляем со временем: в 1.0 это не отличить от события в полночь.
        return local.toLocalDate() to local.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    }

    private fun instantOf(v: String, tzid: String?, zone: ZoneId): ZonedDateTime? = runCatching {
        val s = v.trim()
        if (s.endsWith("Z")) LocalDateTime.parse(s.dropLast(1).take(15), LOCAL).atZone(ZoneOffset.UTC)
        else {
            val local = if (s.length >= 15) LocalDateTime.parse(s.take(15), LOCAL)
                        else LocalDate.parse(s.take(8), DATE).atStartOfDay()
            local.atZone(zoneOf(tzid, zone))
        }
    }.getOrNull()

    /** Первое уведомление события: минуты до начала. */
    private fun alert(e: Component, date: LocalDate, time: LocalTime?, zone: ZoneId): Int? {
        val start = LocalDateTime.of(date, time ?: LocalTime.MIDNIGHT).atZone(zone)
        for (a in e.children.filter { it.name == "VALARM" }) {
            val t = a.first("TRIGGER") ?: continue
            if (t.params["VALUE"]?.uppercase() == "DATE-TIME") {
                val at = instantOf(t.value, null, ZoneOffset.UTC) ?: continue
                return Duration.between(at, start).toMinutes().toInt()
            }
            // У наших событий длительность нулевая, поэтому RELATED=END — то же, что START.
            val d = parseDuration(t.value.trim()) ?: continue
            return -d
        }
        // vCalendar 1.0
        for (n in listOf("DALARM", "AALARM")) {
            val v = e.first(n)?.value?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val at = instantOf(v, null, zone) ?: continue
            return Duration.between(at, start).toMinutes().toInt()
        }
        return null
    }

    /** RFC 5545 duration в минутах со знаком: «-PT15M» → −15. */
    fun parseDuration(s: String): Int? {
        val m = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")
            .matchEntire(s.uppercase()) ?: return null
        val g = m.groupValues
        val total = (g[2].toIntOrNull() ?: 0) * 7 * 1440 + (g[3].toIntOrNull() ?: 0) * 1440 +
            (g[4].toIntOrNull() ?: 0) * 60 + (g[5].toIntOrNull() ?: 0) + (g[6].toIntOrNull() ?: 0) / 60
        return if (g[1] == "-") -total else total
    }

    /** RRULE iCalendar 2.0; второй элемент — правило пришлось упростить. */
    private fun rrule2(value: String, base: Reminder, zone: ZoneId): Pair<Reminder, Boolean> {
        val parts = value.split(';').mapNotNull {
            val i = it.indexOf('='); if (i < 0) null else it.substring(0, i).uppercase() to it.substring(i + 1).uppercase()
        }.toMap()
        var simplified = false
        val repeat = when (parts["FREQ"]) {
            "DAILY" -> Repeat.DAILY
            "WEEKLY" -> Repeat.WEEKLY
            "MONTHLY" -> Repeat.MONTHLY
            "YEARLY" -> Repeat.YEARLY
            else -> { simplified = true; Repeat.NONE }
        }
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        var byDay = emptySet<DayOfWeek>()
        parts["BYDAY"]?.let { list ->
            val codes = list.split(',').map { it.trim() }
            if (repeat == Repeat.WEEKLY && codes.all { it in DAYS_BY_CODE }) byDay = codes.mapNotNull(DAYS_BY_CODE::get).toSet()
            else if (repeat == Repeat.DAILY && codes.all { it in DAYS_BY_CODE }) {
                // «каждый будний день» Google пишет и как DAILY;BYDAY=MO,TU,…
                return rrule2(value.replace(Regex("FREQ=DAILY", RegexOption.IGNORE_CASE), "FREQ=WEEKLY"), base, zone)
            } else simplified = true // «2-й вторник месяца» и т. п.
        }
        for (k in listOf("BYWEEKNO", "BYYEARDAY", "BYHOUR", "BYMINUTE")) if (k in parts) simplified = true
        parts["BYMONTH"]?.let { if (repeat != Repeat.YEARLY || it != base.date.monthValue.toString()) simplified = true }
        parts["BYMONTHDAY"]?.let { days ->
            // наш «последний день, если такого числа нет»: -1 или «28,29,30» с BYSETPOS=-1
            val last = days.split(',').last()
            val ours = days == "-1" || (parts["BYSETPOS"] == "-1" && last == base.date.dayOfMonth.toString())
            if (!ours && days != base.date.dayOfMonth.toString()) simplified = true
        }
        parts["BYSETPOS"]?.let { if (it != "-1" || "BYMONTHDAY" !in parts) simplified = true }
        var r = base.copy(repeat = repeat, interval = interval, byDay = byDay)
        parts["UNTIL"]?.let { u ->
            val until = if (u.length == 8) runCatching { LocalDate.parse(u, DATE) }.getOrNull()
                        else instantOf(u, null, zone)?.withZoneSameInstant(zone)?.toLocalDate()
            r = r.copy(until = until)
        }
        parts["COUNT"]?.toIntOrNull()?.let { r = r.copy(until = ReminderRules.untilForCount(r, it)) }
        return r to simplified
    }

    /** RRULE vCalendar 1.0: «W1 MO WE #10», «MD1 15 #0», «YM1 6 20301231T000000Z». */
    private fun rrule1(value: String, base: Reminder, zone: ZoneId): Pair<Reminder, Boolean> {
        val tokens = value.trim().uppercase().split(Regex("\\s+"))
        val head = tokens.firstOrNull() ?: return base to true
        val m = Regex("^(D|W|MD|MP|YM|YD)(\\d+)$").matchEntire(head) ?: return base to true
        var simplified = false
        val repeat = when (m.groupValues[1]) {
            "D" -> Repeat.DAILY
            "W" -> Repeat.WEEKLY
            "MD" -> Repeat.MONTHLY
            "YM" -> Repeat.YEARLY
            else -> { simplified = true; if (m.groupValues[1] == "MP") Repeat.MONTHLY else Repeat.YEARLY }
        }
        var r = base.copy(repeat = repeat, interval = m.groupValues[2].toInt().coerceAtLeast(1))
        if (repeat == Repeat.WEEKLY) {
            val days = tokens.drop(1).mapNotNull(DAYS_BY_CODE::get).toSet()
            if (days.isNotEmpty()) r = r.copy(byDay = days)
        }
        val last = tokens.last()
        when {
            last.startsWith("#") -> last.drop(1).toIntOrNull()?.takeIf { it > 0 }?.let { r = r.copy(until = ReminderRules.untilForCount(r, it)) }
            last.length >= 8 && last[0].isDigit() && tokens.size > 1 ->
                r = r.copy(until = instantOf(last, null, zone)?.withZoneSameInstant(zone)?.toLocalDate())
        }
        return r to simplified
    }
}
