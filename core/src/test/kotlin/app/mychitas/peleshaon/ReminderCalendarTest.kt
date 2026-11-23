package app.mychitas.peleshaon

import app.mychitas.peleshaon.reminder.CalendarFiles
import app.mychitas.peleshaon.reminder.CalendarFormat
import app.mychitas.peleshaon.reminder.Reminder
import app.mychitas.peleshaon.reminder.ReminderRules
import app.mychitas.peleshaon.reminder.Repeat
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderCalendarTest {

    private val kyiv = ZoneId.of("Europe/Kyiv")
    private val today = LocalDate.of(2026, 11, 23)
    private val now = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, kyiv).toInstant()

    @Test
    fun civilRepeats() {
        val monthly = Reminder("m", "Rent", date = LocalDate.of(2026, 1, 31), repeat = Repeat.MONTHLY)
        assertTrue(ReminderRules.occursOn(monthly, LocalDate.of(2026, 2, 28)))
        assertTrue(ReminderRules.occursOn(monthly, LocalDate.of(2026, 4, 30)))
        assertFalse(ReminderRules.occursOn(monthly, LocalDate.of(2026, 4, 29)))

        val leap = Reminder("y", "Birthday", date = LocalDate.of(2024, 2, 29), repeat = Repeat.YEARLY)
        assertTrue(ReminderRules.occursOn(leap, LocalDate.of(2025, 2, 28)))
        assertTrue(ReminderRules.occursOn(leap, LocalDate.of(2028, 2, 29)))

        val biweekly = Reminder("w", "Shiur", date = LocalDate.of(2026, 11, 22), repeat = Repeat.WEEKLY,
            interval = 2, byDay = setOf(DayOfWeek.SUNDAY, DayOfWeek.TUESDAY))
        assertTrue(ReminderRules.occursOn(biweekly, LocalDate.of(2026, 11, 24)))
        assertFalse(ReminderRules.occursOn(biweekly, LocalDate.of(2026, 11, 29)))
        assertTrue(ReminderRules.occursOn(biweekly, LocalDate.of(2026, 12, 6)))

        val limited = Reminder("d", "Course", date = today, repeat = Repeat.DAILY, until = today.plusDays(2))
        assertEquals(3, ReminderRules.occurrences(limited, today, today.plusDays(10)).size)
        assertEquals(today.plusDays(4), ReminderRules.untilForCount(limited.copy(until = null), 5))
    }

    @Test
    fun hebrewYearlyFollowsJewishDate() {
        // 19 Kislev 5787
        val date = JewishCalendar(5787, JewishCalendar.KISLEV, 19).localDate
        val r = Reminder("h", "Yud-Tes Kislev", date = date, repeat = Repeat.HEBREW_YEARLY)
        val next = JewishCalendar(5788, JewishCalendar.KISLEV, 19).localDate
        assertTrue(ReminderRules.occursOn(r, next))
        assertFalse(ReminderRules.occursOn(r, date.plusYears(1)) && date.plusYears(1) != next)
        val occ = ReminderRules.occurrences(r, date, date.plusDays(1000))
        assertEquals(3, occ.size)
        occ.forEach { val jc = JewishCalendar(it); assertEquals(19, jc.jewishDayOfMonth); assertEquals(JewishCalendar.KISLEV, jc.jewishMonth) }
    }

    @Test
    fun hebrewAdarAndMissingThirtieth() {
        // 5784 — високосный: 14 адара II → в обычном 5785 — 14 адара
        val adar2 = JewishCalendar(5784, JewishCalendar.ADAR_II, 14)
        val in5785 = ReminderRules.hebrewAnniversary(adar2, 5785)
        assertEquals(JewishCalendar.ADAR, JewishCalendar(in5785).jewishMonth)
        assertEquals(14, JewishCalendar(in5785).jewishDayOfMonth)
        // адар обычного года → в високосном 5787 — адар II
        val adar = JewishCalendar(5785, JewishCalendar.ADAR, 7)
        assertEquals(JewishCalendar.ADAR_II, JewishCalendar(ReminderRules.hebrewAnniversary(adar, 5787)).jewishMonth)
        // 30 хешвана: в году, где хешван 29 дней, — 29-го
        val y = (5780..5800).first { JewishCalendar(it, JewishCalendar.CHESHVAN, 1).daysInJewishMonth == 30 }
        val z = (5780..5800).first { JewishCalendar(it, JewishCalendar.CHESHVAN, 1).daysInJewishMonth == 29 }
        val d = ReminderRules.hebrewAnniversary(JewishCalendar(y, JewishCalendar.CHESHVAN, 30), z)
        assertEquals(29, JewishCalendar(d).jewishDayOfMonth)
    }

    @Test
    fun hebrewMonthly() {
        val r = Reminder("rc", "Monthly", date = JewishCalendar(5787, JewishCalendar.TISHREI, 30).localDate,
            repeat = Repeat.HEBREW_MONTHLY)
        val occ = ReminderRules.occurrences(r, r.date, r.date.plusDays(400))
        assertTrue(occ.size in 12..14)
        // каждый месяц ровно один раз
        assertEquals(occ.size, occ.map { JewishCalendar(it).let { j -> j.jewishYear * 100 + j.jewishMonth } }.distinct().size)
    }

    @Test
    fun nextAlertHandlesAllDayAndTimed() {
        val after = ZonedDateTime.ofInstant(now, kyiv)
        val allDay = Reminder("a", "Day", date = today.plusDays(1), alertMinutes = 900) // накануне в 9:00
        val (at, day) = assertNotNull(ReminderRules.nextAlert(allDay, after.minusHours(2), kyiv))
        assertEquals(today.plusDays(1), day)
        assertEquals(LocalTime.of(9, 0), at.toLocalTime())
        assertEquals(today, at.toLocalDate())
        assertNull(ReminderRules.nextAlert(allDay, after, kyiv))

        val weekly = Reminder("t", "Shiur", date = today.minusDays(14), time = LocalTime.of(20, 0),
            repeat = Repeat.WEEKLY, alertMinutes = 15)
        val (wAt, _) = assertNotNull(ReminderRules.nextAlert(weekly, after, kyiv))
        assertEquals(LocalTime.of(19, 45), wAt.toLocalTime())
        assertEquals(today, wAt.toLocalDate())
        assertNull(ReminderRules.nextAlert(weekly.copy(alertMinutes = null), after, kyiv))
    }

    @Test
    fun triggerDurations() {
        assertEquals("-PT15M", CalendarFiles.trigger(15))
        assertEquals("PT9H", CalendarFiles.trigger(-540))
        assertEquals("-PT15H", CalendarFiles.trigger(900))
        assertEquals("-P1D", CalendarFiles.trigger(1440))
        assertEquals("-P6DT15H", CalendarFiles.trigger(9540))
        assertEquals("PT0S", CalendarFiles.trigger(0))
        for (m in listOf(0, 5, 15, 60, 1440, -540, 900, 9540, 10080))
            assertEquals(-m, CalendarFiles.parseDuration(CalendarFiles.trigger(m)))
    }

    @Test
    fun icsRoundTrip() {
        val list = listOf(
            Reminder("one@x", "Doctor; room 5, floor 2", note = "Line 1\nLine 2", date = today.plusDays(3),
                time = LocalTime.of(8, 30), alertMinutes = 30),
            Reminder("two@x", "Yahrzeit — זכרון", date = JewishCalendar(5787, JewishCalendar.SHEVAT, 10).localDate,
                repeat = Repeat.HEBREW_YEARLY, alertMinutes = 900),
            Reminder("three@x", "Shiur", date = LocalDate.of(2026, 1, 4), time = LocalTime.of(20, 0),
                repeat = Repeat.WEEKLY, byDay = setOf(DayOfWeek.SUNDAY, DayOfWeek.WEDNESDAY), interval = 2,
                until = LocalDate.of(2027, 6, 30), alertMinutes = 10),
            Reminder("four@x", "Rent", date = LocalDate.of(2026, 1, 31), repeat = Repeat.MONTHLY, alertMinutes = -540),
            Reminder("five@x", "Long title ".repeat(12).trim(), date = today.plusDays(1))
        )
        val ics = CalendarFiles.export(list, kyiv, CalendarFormat.ICS, now = now)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue("BEGIN:VTIMEZONE" in ics && "TZID:Europe/Kyiv" in ics)
        assertTrue("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=SU,WE;UNTIL=" in ics)
        assertTrue("RRULE:FREQ=MONTHLY;BYMONTHDAY=-1" in ics)
        assertTrue(ics.lines().all { it.toByteArray().size <= 75 })
        val back = CalendarFiles.parse(ics, kyiv, today)
        assertEquals(0, back.simplified)
        assertEquals(list.map { it.id }.toSet(), back.reminders.map { it.id }.toSet())
        for (r in list) {
            val b = back.reminders.first { it.id == r.id }
            assertEquals(r.title, b.title)
            assertEquals(r.note, b.note)
            assertEquals(r.time, b.time)
            assertEquals(r.repeat, b.repeat)
            assertEquals(r.interval, b.interval)
            assertEquals(r.byDay, b.byDay)
            assertEquals(r.until, b.until)
            assertEquals(r.alertMinutes, b.alertMinutes)
            assertEquals(r.date, b.date)
        }
    }

    @Test
    fun vcsRoundTrip() {
        val list = listOf(
            Reminder("v1", "Встреча", date = today.plusDays(2), time = LocalTime.of(18, 15), alertMinutes = 15,
                repeat = Repeat.WEEKLY),
            Reminder("v2", "Yearly", date = LocalDate.of(2020, 5, 3), repeat = Repeat.YEARLY)
        )
        val vcs = CalendarFiles.export(list, kyiv, CalendarFormat.VCS, now = now)
        assertTrue("VERSION:1.0" in vcs)
        assertTrue("RRULE:W1 " in vcs && "RRULE:YM1 5 #0" in vcs)
        val back = CalendarFiles.parse(vcs, kyiv, today).reminders.associateBy { it.id }
        assertEquals("Встреча", back["v1"]!!.title)
        assertEquals(LocalTime.of(18, 15), back["v1"]!!.time)
        assertEquals(15, back["v1"]!!.alertMinutes)
        assertEquals(Repeat.WEEKLY, back["v1"]!!.repeat)
        assertEquals(Repeat.YEARLY, back["v2"]!!.repeat)
    }

    @Test
    fun googleCalendarExport() {
        // Фрагмент реальной выгрузки Google Календаря: перенос строк, TZID, UTC, весь день, BYDAY, COUNT, отмена.
        val ics = """
            BEGIN:VCALENDAR
            PRODID:-//Google Inc//Google Calendar 70.9054//EN
            VERSION:2.0
            CALSCALE:GREGORIAN
            METHOD:PUBLISH
            X-WR-CALNAME:me@example.com
            X-WR-TIMEZONE:Asia/Jerusalem
            BEGIN:VEVENT
            DTSTART;TZID=Asia/Jerusalem:20261201T090000
            DTEND;TZID=Asia/Jerusalem:20261201T100000
            RRULE:FREQ=WEEKLY;WKST=SU;COUNT=4;BYDAY=TU
            DTSTAMP:20261120T101010Z
            UID:abc123@google.com
            DESCRIPTION:Bring the book\, please\nand tea
            SUMMARY:Weekly shiur with a very long title that Google folds over severa
             l lines
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:This is an event reminder
            TRIGGER:-P0DT0H30M0S
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20270315
            DTEND;VALUE=DATE:20270316
            RRULE:FREQ=YEARLY
            UID:bday@google.com
            SUMMARY:Mom's birthday
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20261224T170000Z
            UID:utc@google.com
            SUMMARY:Call
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20200101
            UID:old@google.com
            SUMMARY:Old
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20270101
            UID:cancel@google.com
            STATUS:CANCELLED
            SUMMARY:Cancelled
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;TZID=Asia/Jerusalem:20270105T090000
            RECURRENCE-ID;TZID=Asia/Jerusalem:20261215T090000
            UID:abc123@google.com
            SUMMARY:Moved instance
            END:VEVENT
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20270201
            RRULE:FREQ=MONTHLY;BYDAY=2TU
            UID:second-tuesday@google.com
            SUMMARY:Second Tuesday
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val jerusalem = ZoneId.of("Asia/Jerusalem")
        val res = CalendarFiles.parse(ics, kyiv, today)
        assertEquals(1, res.skippedPast)
        assertEquals(1, res.simplified)
        val m = res.reminders.associateBy { it.id }
        assertEquals(setOf("abc123@google.com", "bday@google.com", "utc@google.com", "second-tuesday@google.com"), m.keys)
        val shiur = m.getValue("abc123@google.com")
        assertEquals("Weekly shiur with a very long title that Google folds over several lines", shiur.title)
        assertEquals("Bring the book, please\nand tea", shiur.note)
        // 9:00 Иерусалима = 9:00 Киева (обе +2 зимой)
        assertEquals(ZonedDateTime.of(2026, 12, 1, 9, 0, 0, 0, jerusalem).withZoneSameInstant(kyiv).toLocalTime(), shiur.time)
        assertEquals(Repeat.WEEKLY, shiur.repeat)
        assertEquals(setOf(DayOfWeek.TUESDAY), shiur.byDay)
        assertEquals(LocalDate.of(2026, 12, 22), shiur.until)
        assertEquals(30, shiur.alertMinutes)
        val bday = m.getValue("bday@google.com")
        assertTrue(bday.allDay)
        assertEquals(Repeat.YEARLY, bday.repeat)
        assertEquals(LocalTime.of(19, 0), m.getValue("utc@google.com").time)
    }

    @Test
    fun exportedTimestampsAreDeterministic() {
        val r = Reminder("x", "T", date = today, time = LocalTime.of(12, 0))
        val a = CalendarFiles.export(listOf(r), kyiv, CalendarFormat.ICS, now = Instant.EPOCH.plusSeconds(1_800_000_000))
        val b = CalendarFiles.export(listOf(r), kyiv, CalendarFormat.ICS, now = Instant.EPOCH.plusSeconds(1_800_000_000))
        assertEquals(a, b)
    }
}
