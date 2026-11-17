package app.mychitas.peleshaon.ui

import app.mychitas.peleshaon.domain.JewishDay
import java.util.Locale

/*
 * Еврейская дата с днём обычным числом (владелец, 17.11.2026: «не все знают гематрию — день обычным числом»):
 * «7 кислева», «7 Kislev», «7. Kislew», «7 בכסלו». Названия — как на сайте (`hebrewDayMonth` в src/core/hebcal.ts).
 * Месяцы — как в KosherJava: нисан — 1 … адар — 12, адар II — 13; в високосный год 12 — адар I.
 */
private val RU = listOf("нисана", "ияра", "сивана", "тамуза", "ава", "элула", "тишрея", "хешвана", "кислева", "тевета", "швата", "адара", "адара II")
private val EN = listOf("Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul", "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar", "Adar II")
private val DE = listOf("Nissan", "Ijar", "Siwan", "Tammus", "Aw", "Elul", "Tischri", "Cheschwan", "Kislew", "Tewet", "Schwat", "Adar", "Adar II")
private val HE = listOf("בניסן", "באייר", "בסיון", "בתמוז", "באב", "באלול", "בתשרי", "בחשוון", "בכסלו", "בטבת", "בשבט", "באדר", "באדר ב׳")

fun jewishMonthName(d: JewishDay, locale: Locale = Locale.getDefault()): String {
    val lang = locale.language
    val list = when (lang) {
        "ru", "uk", "be" -> RU
        "de" -> DE
        "iw", "he" -> HE
        else -> EN
    }
    val name = list[(d.month - 1).coerceIn(0, 12)]
    // в високосный год — адар I
    if (d.leap && d.month == 12) return when (lang) {
        "ru", "uk", "be" -> "адара I"
        "iw", "he" -> "באדר א׳"
        else -> "Adar I"
    }
    return name
}

/** «7 кислева» / «7. Kislew» — без года. */
fun jewishDayMonth(d: JewishDay, locale: Locale = Locale.getDefault()): String =
    if (locale.language == "de") "${d.day}. ${jewishMonthName(d, locale)}" else "${d.day} ${jewishMonthName(d, locale)}"
