package app.mychitas.peleshaon.domain

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.LocalDate

/**
 * Еврейский календарь приложения (владелец, 17.11.2026: «эргономичный современный еврейский календарь»): месяц еврейского года сеткой,
 * праздники, посты, Рош Ходеш, глава недели, Омер, свечи Хануки и даты Хабада. Без отображения — его делает приложение.
 *
 * Праздники и посты — KosherJava (`yomTovIndex`, диаспора или Израиль по городу); современные израильские дни не показываем
 * (`isUseModernHolidays` = false, как в календарях Хабада).
 */
enum class CalKind { HOLIDAY, EREV, FAST, MINOR, ROSH_CHODESH, CHABAD, PARSHA, SPECIAL_SHABBAT, OMER, CHANUKAH }

/** Событие дня: вид, ключ названия (`pesach`, `chabad_19_kislev`, `parsha_VAYETZEI`…) и число (день Омера, свеча Хануки). */
data class CalEvent(val kind: CalKind, val key: String, val n: Int = 0, val leap: Boolean = false)

/**
 * Presentation of candle-lighting transitions. Friday Yom-Tov -> Shabbat
 * must not be treated as a generic 'after nightfall' transfer.
 * These labels do not constitute a ruling on transferring flame or eruv tavshilin.
 */
enum class CandleTransition { NONE, BEFORE_SUNSET, AFTER_NIGHTFALL }

/**
 * Шабат мевархим — благословение нового месяца в последний Шабат месяца (23–29-го; перед тишреем — нет).
 * Молад нового месяца — как его объявляют в синагоге: день, часы и минуты по времени Йерушалаима, и «части» (халаким, 1/18 минуты).
 */
data class Mevarchim(
    val shabbat: LocalDate,
    /** новый месяц — как в KosherJava: нисан — 1 … адар II — 13 */
    val month: Int,
    val leap: Boolean,
    val year: Int,
    val moladDate: LocalDate,
    val moladHour: Int,
    val moladMinute: Int,
    val moladChalakim: Int,
    /** дни Рош Ходеш — один или два */
    val roshChodesh: List<LocalDate>
)

data class CalDay(
    val date: LocalDate,
    val jd: JewishDay,
    val events: List<CalEvent>,
    /** Шабат или Йом Тов / Йом Кипур — работать нельзя */
    val holy: Boolean,
    /** вечером зажигают свечи (канун Шабата или Йом Това; и между двумя святыми днями — после выхода звёзд) */
    val candles: Boolean
)

object JewishCal {

    /**
     * Даты Хабада (месяц KosherJava, день, ключ). Только общеизвестные: освобождения, дни рождения и йорцайты Ребе.
     * 18 элула — день рождения Бааль Шем Това и Алтер Ребе; 10 швата — йорцайт Ребе Раяца и начало руководства Ребе.
     */
    private val CHABAD = listOf(
        Triple(JewishCalendar.KISLEV, 10, "chabad_10_kislev"),
        Triple(JewishCalendar.KISLEV, 19, "chabad_19_kislev"),
        Triple(JewishCalendar.TEVES, 24, "chabad_24_teves"),
        Triple(JewishCalendar.SHEVAT, 10, "chabad_10_shevat"),
        Triple(JewishCalendar.NISSAN, 2, "chabad_2_nissan"),
        Triple(JewishCalendar.NISSAN, 11, "chabad_11_nissan"),
        Triple(JewishCalendar.TAMMUZ, 3, "chabad_3_tammuz"),
        Triple(JewishCalendar.TAMMUZ, 12, "chabad_12_tammuz"),
        Triple(JewishCalendar.ELUL, 18, "chabad_18_elul"),
        Triple(JewishCalendar.CHESHVAN, 20, "chabad_20_cheshvan")
    )

    private val FASTS = setOf(
        JewishCalendar.SEVENTEEN_OF_TAMMUZ, JewishCalendar.TISHA_BEAV, JewishCalendar.FAST_OF_GEDALYAH,
        JewishCalendar.YOM_KIPPUR, JewishCalendar.TENTH_OF_TEVES, JewishCalendar.FAST_OF_ESTHER
    )
    private val EREVS = setOf(
        JewishCalendar.EREV_PESACH, JewishCalendar.EREV_SHAVUOS, JewishCalendar.EREV_ROSH_HASHANA,
        JewishCalendar.EREV_YOM_KIPPUR, JewishCalendar.EREV_SUCCOS
    )
    private val MINOR = setOf(
        JewishCalendar.PESACH_SHENI, JewishCalendar.TU_BEAV, JewishCalendar.TU_BESHVAT, JewishCalendar.LAG_BAOMER,
        JewishCalendar.PURIM_KATAN, JewishCalendar.SHUSHAN_PURIM_KATAN
    )

    private fun yomTovKey(i: Int): String? = when (i) {
        JewishCalendar.EREV_PESACH -> "erev_pesach"
        JewishCalendar.PESACH -> "pesach"
        JewishCalendar.CHOL_HAMOED_PESACH -> "chol_hamoed_pesach"
        JewishCalendar.PESACH_SHENI -> "pesach_sheni"
        JewishCalendar.EREV_SHAVUOS -> "erev_shavuos"
        JewishCalendar.SHAVUOS -> "shavuos"
        JewishCalendar.SEVENTEEN_OF_TAMMUZ -> "tammuz_17"
        JewishCalendar.TISHA_BEAV -> "tisha_beav"
        JewishCalendar.TU_BEAV -> "tu_beav"
        JewishCalendar.EREV_ROSH_HASHANA -> "erev_rosh_hashana"
        JewishCalendar.ROSH_HASHANA -> "rosh_hashana"
        JewishCalendar.FAST_OF_GEDALYAH -> "tzom_gedalya"
        JewishCalendar.EREV_YOM_KIPPUR -> "erev_yom_kippur"
        JewishCalendar.YOM_KIPPUR -> "yom_kippur"
        JewishCalendar.EREV_SUCCOS -> "erev_sukkot"
        JewishCalendar.SUCCOS -> "sukkot"
        JewishCalendar.CHOL_HAMOED_SUCCOS -> "chol_hamoed_sukkot"
        JewishCalendar.HOSHANA_RABBA -> "hoshana_rabba"
        JewishCalendar.SHEMINI_ATZERES -> "shemini_atzeret"
        JewishCalendar.SIMCHAS_TORAH -> "simchat_torah"
        JewishCalendar.TENTH_OF_TEVES -> "teves_10"
        JewishCalendar.TU_BESHVAT -> "tu_bishvat"
        JewishCalendar.FAST_OF_ESTHER -> "taanit_esther"
        JewishCalendar.PURIM -> "purim"
        JewishCalendar.SHUSHAN_PURIM -> "shushan_purim"
        JewishCalendar.PURIM_KATAN -> "purim_katan"
        JewishCalendar.SHUSHAN_PURIM_KATAN -> "shushan_purim_katan"
        JewishCalendar.LAG_BAOMER -> "lag_baomer"
        else -> null // Ханука, Рош Ходеш, Омер — отдельно; современные дни и «исру хаг» — не показываем
    }

    private fun jcOf(date: LocalDate, isIsrael: Boolean) = JewishCalendar(date).apply {
        inIsrael = isIsrael
        isUseModernHolidays = false
    }

    fun day(date: LocalDate, isIsrael: Boolean): CalDay {
        val jc = jcOf(date, isIsrael)
        val ev = mutableListOf<CalEvent>()
        val idx = jc.yomTovIndex
        yomTovKey(idx)?.let { key ->
            val kind = when (idx) {
                in FASTS -> if (idx == JewishCalendar.YOM_KIPPUR) CalKind.HOLIDAY else CalKind.FAST
                in EREVS -> CalKind.EREV
                in MINOR -> CalKind.MINOR
                else -> CalKind.HOLIDAY
            }
            ev += CalEvent(kind, key)
        }
        if (jc.isChanukah) ev += CalEvent(CalKind.CHANUKAH, "chanukah", jc.dayOfChanukah)
        if (jc.isRoshChodesh) ev += CalEvent(CalKind.ROSH_CHODESH, "rosh_chodesh")
        val omer = jc.dayOfOmer
        if (omer > 0) ev += CalEvent(CalKind.OMER, "omer", omer)
        if (ZmanimEngine.minhag == Minhag.CHABAD) for ((m, d, key) in CHABAD) if (jc.jewishMonth == m && jc.jewishDayOfMonth == d) ev += CalEvent(CalKind.CHABAD, key)
        if (date.dayOfWeek == java.time.DayOfWeek.SATURDAY) {
            val p = jc.parshah
            if (p != JewishCalendar.Parsha.NONE) ev += CalEvent(CalKind.PARSHA, "parsha_${p.name}")
            val sp = jc.specialShabbos
            if (sp != JewishCalendar.Parsha.NONE) ev += CalEvent(CalKind.SPECIAL_SHABBAT, "special_${sp.name}")
            mevarchim(date, isIsrael)?.let { ev += CalEvent(CalKind.SPECIAL_SHABBAT, "mevarchim", it.month, it.leap) }
        }
        val jd = JewishDay(jc.jewishDayOfMonth, jc.jewishMonth, jc.isJewishLeapYear, jc.jewishYear)
        return CalDay(date, jd, ev, jc.isAssurBemelacha, jc.hasCandleLighting())
    }

    /**
     * Шабат мевархим этой недели (с воскресенья по субботу — ближайший Шабат) и молад нового месяца; не мевархим — null.
     */
    fun mevarchim(date: LocalDate, isIsrael: Boolean): Mevarchim? {
        val sat = date.plusDays(((java.time.DayOfWeek.SATURDAY.value - date.dayOfWeek.value + 7) % 7).toLong())
        val jc = jcOf(sat, isIsrael)
        if (jc.jewishDayOfMonth !in 23..29 || jc.jewishMonth == JewishCalendar.ELUL) return null
        var first = sat
        while (jcOf(first, isIsrael).jewishDayOfMonth != 1) first = first.plusDays(1)
        val rc = if (jcOf(first.minusDays(1), isIsrael).jewishDayOfMonth == 30) listOf(first.minusDays(1), first) else listOf(first)
        val next = jcOf(first, isIsrael)
        val m = next.molad
        return Mevarchim(sat, next.jewishMonth, next.isJewishLeapYear, next.jewishYear,
            m.localDate, m.moladHours, m.moladMinutes, m.moladChalakim, rc)
    }

    /** Все дни еврейского месяца, в котором стоит `date`. */
    fun monthOf(date: LocalDate, isIsrael: Boolean): List<CalDay> {
        val jc = jcOf(date, isIsrael)
        val first = date.minusDays((jc.jewishDayOfMonth - 1).toLong())
        return (0 until jc.daysInJewishMonth).map { day(first.plusDays(it.toLong()), isIsrael) }
    }

    /** Only a timing category; actual religious practice needs local guidance. */
    fun candleTransition(date: LocalDate, isIsrael: Boolean): CandleTransition {
        val current = day(date, isIsrael)
        if (!current.candles) return CandleTransition.NONE
        val next = day(date.plusDays(1), isIsrael)
        return if (!current.holy ||
            (date.dayOfWeek == java.time.DayOfWeek.FRIDAY && next.holy)
        ) CandleTransition.BEFORE_SUNSET else CandleTransition.AFTER_NIGHTFALL
    }

    /** Первый день следующего / прошлого еврейского месяца. */
    fun nextMonth(date: LocalDate, isIsrael: Boolean): LocalDate {
        val jc = jcOf(date, isIsrael)
        return date.minusDays((jc.jewishDayOfMonth - 1).toLong()).plusDays(jc.daysInJewishMonth.toLong())
    }

    fun prevMonth(date: LocalDate, isIsrael: Boolean): LocalDate {
        val jc = jcOf(date, isIsrael)
        val lastOfPrev = date.minusDays(jc.jewishDayOfMonth.toLong())
        return lastOfPrev.minusDays((jcOf(lastOfPrev, isIsrael).jewishDayOfMonth - 1).toLong())
    }

    /**
     * Ближайшие праздники, посты и даты Хабада (без Рош Ходеша, Омера и глав): многодневный праздник — одной строкой, с первого дня;
     * то, что уже идёт сегодня, — тоже.
     */
    fun upcoming(from: LocalDate, isIsrael: Boolean, count: Int = 8, days: Int = 200): List<Pair<LocalDate, CalEvent>> {
        val out = mutableListOf<Pair<LocalDate, CalEvent>>()
        var prevKeys = day(from.minusDays(1), isIsrael).events.map { it.key }.toSet()
        for (n in 0 until days) {
            val d = from.plusDays(n.toLong())
            val cd = day(d, isIsrael)
            for (e in cd.events) {
                if (e.kind !in setOf(CalKind.HOLIDAY, CalKind.FAST, CalKind.CHABAD, CalKind.CHANUKAH, CalKind.MINOR)) continue
                if (e.key in prevKeys && n > 0) continue
                if (out.any { it.second.key == e.key && it.first.isAfter(d.minusDays(9)) }) continue
                out += d to e
            }
            prevKeys = cd.events.map { it.key }.toSet()
            if (out.size >= count) break
        }
        return out.take(count)
    }
}
