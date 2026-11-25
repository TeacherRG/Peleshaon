package app.mychitas.peleshaon.reminder

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Повтор напоминания. Кроме гражданских (как в Google Календаре) — по еврейской дате:
 * дни рождения, йорцайты и годовщины по еврейскому календарю.
 */
enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, HEBREW_MONTHLY, HEBREW_YEARLY;
    val hebrew: Boolean get() = this == HEBREW_MONTHLY || this == HEBREW_YEARLY
}

/**
 * Напоминание пользователя в календаре.
 *
 * @param id постоянный идентификатор; при экспорте — UID события iCalendar, при импорте — UID из файла
 *   (повторный импорт того же файла обновляет, а не дублирует).
 * @param time null — на весь день.
 * @param alertMinutes за сколько минут до начала уведомить (для события на весь день начало — 00:00,
 *   поэтому «в 9:00 в тот же день» — это −540); null — без уведомления.
 * @param byDay для еженедельного повтора — дни недели; пусто — день недели самой даты.
 * @param until последний день повторов включительно; null — без конца.
 */
data class Reminder(
    val id: String,
    val title: String,
    val note: String = "",
    val date: LocalDate,
    val time: LocalTime? = null,
    val repeat: Repeat = Repeat.NONE,
    val interval: Int = 1,
    val byDay: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
    val alertMinutes: Int? = null
) {
    val allDay: Boolean get() = time == null

    companion object {
        /** Варианты уведомления для события со временем: минуты до начала. */
        val TIMED_ALERTS = listOf(0, 5, 15, 30, 60, 120, 1440)
        /** Для события на весь день: в 9:00 в тот же день, накануне в 9:00, за неделю в 9:00. */
        val ALL_DAY_ALERTS = listOf(-540, 900, 9540)
        const val DEFAULT_TIMED_ALERT = 15
        const val DEFAULT_ALL_DAY_ALERT = -540
    }
}

object ReminderRules {

    /** Сколько дней вперёд искать следующий повтор: еврейский год бывает 385 дней, плюс уведомление «за неделю». */
    private const val HORIZON_DAYS = 400L

    /** Бывает ли напоминание в этот гражданский день. */
    fun occursOn(r: Reminder, date: LocalDate): Boolean {
        if (date.isBefore(r.date)) return false
        if (r.until != null && date.isAfter(r.until)) return false
        val step = r.interval.coerceAtLeast(1).toLong()
        return when (r.repeat) {
            Repeat.NONE -> date == r.date
            Repeat.DAILY -> ChronoUnit.DAYS.between(r.date, date) % step == 0L
            Repeat.WEEKLY -> {
                val days = r.byDay.ifEmpty { setOf(r.date.dayOfWeek) }
                if (date.dayOfWeek !in days) return false
                // Номер недели считаем от воскресенья недели первой даты (как WKST=SU в Google).
                val w0 = weekStart(r.date)
                ChronoUnit.WEEKS.between(w0, weekStart(date)) % step == 0L
            }
            Repeat.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(r.date.withDayOfMonth(1), date.withDayOfMonth(1))
                months % step == 0L && date.dayOfMonth == minOf(r.date.dayOfMonth, date.lengthOfMonth())
            }
            Repeat.YEARLY -> {
                if ((date.year - r.date.year) % step != 0L) return false
                if (date.monthValue != r.date.monthValue) return false
                // 29 февраля — в невисокосный год 28-го
                date.dayOfMonth == minOf(r.date.dayOfMonth, date.lengthOfMonth())
            }
            Repeat.HEBREW_MONTHLY -> {
                val o = JewishCalendar(r.date)
                val d = JewishCalendar(date)
                val months = hebrewMonthIndex(d) - hebrewMonthIndex(o)
                months % step == 0L && d.jewishDayOfMonth == minOf(o.jewishDayOfMonth, d.daysInJewishMonth)
            }
            Repeat.HEBREW_YEARLY -> {
                val o = JewishCalendar(r.date)
                val d = JewishCalendar(date)
                if ((d.jewishYear - o.jewishYear) % step != 0L) return false
                hebrewAnniversary(o, d.jewishYear) == date
            }
        }
    }

    private fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value % 7).toLong())

    /** Порядковый номер еврейского месяца от сотворения — чтобы считать «каждые N месяцев». */
    private fun hebrewMonthIndex(jc: JewishDate): Long {
        // Год начинается с тишрея (7); в KosherJava нисан — 1. Месяцы до года считаем по 19-летнему циклу.
        val y = jc.jewishYear.toLong() - 1
        val before = 235L * (y / 19) + 12L * (y % 19) + (7L * (y % 19) + 1) / 19
        val leap = jc.isJewishLeapYear
        val last = if (leap) 13 else 12
        val m = jc.jewishMonth
        val inYear = if (m >= 7) m - 7 else m + (last - 7)
        return before + inYear
    }

    /**
     * Годовщина еврейской даты в еврейском году [year]: адар I/II в обычный год — адар,
     * адар обычного года в високосный — адар II; 30-го, которого в этом году нет, — 29-го.
     */
    fun hebrewAnniversary(original: JewishDate, year: Int): LocalDate {
        val leap = JewishDate(year, JewishDate.TISHREI, 1).isJewishLeapYear
        var month = original.jewishMonth
        if (!leap && month == JewishDate.ADAR_II) month = JewishDate.ADAR
        else if (leap && month == JewishDate.ADAR && !original.isJewishLeapYear) month = JewishDate.ADAR_II
        val days = JewishDate(year, month, 1).daysInJewishMonth
        return JewishDate(year, month, minOf(original.jewishDayOfMonth, days)).localDate
    }

    /** Дни, в которые бывает напоминание, в промежутке [from, to] включительно. */
    fun occurrences(r: Reminder, from: LocalDate, to: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = maxOf(from, r.date)
        val end = if (r.until != null) minOf(to, r.until) else to
        while (!d.isAfter(end)) {
            if (occursOn(r, d)) out += d
            d = d.plusDays(1)
        }
        return out
    }

    /** Начало события в этот день: время или полночь (на весь день); DST — как у будильников. */
    fun startOn(r: Reminder, date: LocalDate, zone: ZoneId): ZonedDateTime {
        val local = LocalDateTime.of(date, r.time ?: LocalTime.MIDNIGHT)
        val offsets = zone.rules.getValidOffsets(local)
        return if (offsets.isEmpty()) zone.rules.getTransition(local).dateTimeAfter.atZone(zone)
        else ZonedDateTime.ofLocal(local, zone, offsets.first())
    }

    fun alertOn(r: Reminder, date: LocalDate, zone: ZoneId): ZonedDateTime? =
        r.alertMinutes?.let { startOn(r, date, zone).minusMinutes(it.toLong()) }

    /** Ближайшее уведомление строго после [after] (и день события, к которому оно относится); нет — null. */
    fun nextAlert(r: Reminder, after: ZonedDateTime, zone: ZoneId): Pair<ZonedDateTime, LocalDate>? {
        val minutes = r.alertMinutes ?: return null
        // уведомление может быть до дня события («за неделю») или после полуночи («в 9:00») — смотрим с запасом
        val lead = (minutes / 1440L) + 2
        val today = after.withZoneSameInstant(zone).toLocalDate()
        var d = maxOf(r.date, today.minusDays(2))
        val end = today.plusDays(HORIZON_DAYS + lead)
        while (!d.isAfter(end)) {
            if (r.until != null && d.isAfter(r.until)) return null
            if (occursOn(r, d)) {
                val at = startOn(r, d, zone).minusMinutes(minutes.toLong())
                if (at.isAfter(after)) return at to d
            }
            d = d.plusDays(1)
        }
        return null
    }

    /** Повтор, заданный как «N раз» (COUNT), — в последний день. */
    fun untilForCount(r: Reminder, count: Int): LocalDate? {
        if (count <= 0) return null
        if (r.repeat == Repeat.NONE) return r.date
        var n = 0
        var d = r.date
        val limit = r.date.plusYears(60)
        while (!d.isAfter(limit)) {
            if (occursOn(r, d) && ++n == count) return d
            d = d.plusDays(1)
        }
        return null
    }
}
