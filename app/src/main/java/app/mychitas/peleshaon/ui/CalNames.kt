package app.mychitas.peleshaon.ui

import app.mychitas.peleshaon.domain.CalEvent
import app.mychitas.peleshaon.domain.CalKind
import app.mychitas.peleshaon.domain.JewishDay
import java.util.Locale

/*
 * Названия событий еврейского календаря (`JewishCal`) на языке телефона: ru / en / de / he.
 * Русский — с ґ, как везде в проекте; английский — как chabad.org; немецкий — как de.chabad.org.
 */

private enum class L { RU, EN, DE, HE, UK }

private fun lang(locale: Locale): L = when (locale.language) {
    "uk" -> L.UK
    "ru", "be" -> L.RU
    "de" -> L.DE
    "iw", "he" -> L.HE
    else -> L.EN
}

private val HOLIDAYS: Map<String, List<String>> = mapOf(
    // ключ to listOf(ru, en, de, he)
    "erev_pesach" to listOf("Канун Песаха", "Erev Pesach", "Erew Pessach", "ערב פסח"),
    "pesach" to listOf("Песах", "Pesach", "Pessach", "פסח"),
    "chol_hamoed_pesach" to listOf("Хол ґа-моэд Песаха", "Chol HaMoed Pesach", "Chol HaMoed Pessach", "חול המועד פסח"),
    "pesach_sheni" to listOf("Песах шени", "Pesach Sheni", "Pessach Scheni", "פסח שני"),
    "erev_shavuos" to listOf("Канун Шавуота", "Erev Shavuot", "Erew Schawuot", "ערב שבועות"),
    "shavuos" to listOf("Шавуот", "Shavuot", "Schawuot", "שבועות"),
    "tammuz_17" to listOf("Пост 17 тамуза", "Fast of 17 Tammuz", "Fasten 17. Tammus", "צום י״ז בתמוז"),
    "tisha_beav" to listOf("Тиша бе-Ав", "Tishah B'Av", "Tischa BeAw", "תשעה באב"),
    "tu_beav" to listOf("Ту бе-Ав", "Tu B'Av", "Tu BeAw", "ט״ו באב"),
    "erev_rosh_hashana" to listOf("Канун Рош ґа-Шана", "Erev Rosh Hashanah", "Erew Rosch HaSchana", "ערב ראש השנה"),
    "rosh_hashana" to listOf("Рош ґа-Шана", "Rosh Hashanah", "Rosch HaSchana", "ראש השנה"),
    "tzom_gedalya" to listOf("Пост Гедальи", "Fast of Gedaliah", "Fasten Gedalja", "צום גדליה"),
    "erev_yom_kippur" to listOf("Канун Йом Кипура", "Erev Yom Kippur", "Erew Jom Kippur", "ערב יום כיפור"),
    "yom_kippur" to listOf("Йом Кипур", "Yom Kippur", "Jom Kippur", "יום כיפור"),
    "erev_sukkot" to listOf("Канун Суккота", "Erev Sukkot", "Erew Sukkot", "ערב סוכות"),
    "sukkot" to listOf("Суккот", "Sukkot", "Sukkot", "סוכות"),
    "chol_hamoed_sukkot" to listOf("Хол ґа-моэд Суккота", "Chol HaMoed Sukkot", "Chol HaMoed Sukkot", "חול המועד סוכות"),
    "hoshana_rabba" to listOf("Ґошана раба", "Hoshana Rabbah", "Hoschana Rabba", "הושענא רבה"),
    "shemini_atzeret" to listOf("Шмини Ацерет", "Shemini Atzeret", "Schemini Azeret", "שמיני עצרת"),
    "simchat_torah" to listOf("Симхат Тора", "Simchat Torah", "Simchat Tora", "שמחת תורה"),
    "teves_10" to listOf("Пост 10 тевета", "Fast of 10 Tevet", "Fasten 10. Tewet", "עשרה בטבת"),
    "tu_bishvat" to listOf("Ту би-Шват", "Tu B'Shvat", "Tu BiSchwat", "ט״ו בשבט"),
    "taanit_esther" to listOf("Пост Эстер", "Fast of Esther", "Fasten Esther", "תענית אסתר"),
    "purim" to listOf("Пурим", "Purim", "Purim", "פורים"),
    "shushan_purim" to listOf("Шушан Пурим", "Shushan Purim", "Schuschan Purim", "שושן פורים"),
    "purim_katan" to listOf("Пурим катан", "Purim Katan", "Purim Katan", "פורים קטן"),
    "shushan_purim_katan" to listOf("Шушан Пурим катан", "Shushan Purim Katan", "Schuschan Purim Katan", "שושן פורים קטן"),
    "lag_baomer" to listOf("Лаг ба-Омер", "Lag BaOmer", "Lag BaOmer", "ל״ג בעומר"),
    "rosh_chodesh" to listOf("Рош Ходеш", "Rosh Chodesh", "Rosch Chodesch", "ראש חודש"),
    // даты Хабада
    "chabad_10_kislev" to listOf("Освобождение Мителер Ребе", "Liberation of the Mitteler Rebbe", "Befreiung des Mitteler Rebbe", "חג הגאולה של אדמו״ר האמצעי"),
    "chabad_19_kislev" to listOf("Рош ґа-Шана хасидута — освобождение Алтер Ребе", "Rosh Hashanah of Chassidus — the Alter Rebbe's liberation", "Rosch HaSchana der Chassidut — Befreiung des Alter Rebbe", "ראש השנה לחסידות"),
    "chabad_24_teves" to listOf("Йорцайт Алтер Ребе", "Yahrzeit of the Alter Rebbe", "Jahrzeit des Alter Rebbe", "הילולת אדמו״ר הזקן"),
    "chabad_10_shevat" to listOf("Йорцайт Ребе Раяца, начало руководства Ребе", "Yahrzeit of the Previous Rebbe; the Rebbe's leadership begins", "Jahrzeit des Rebbe Rajaz; Beginn der Führung des Rebbe", "יו״ד שבט"),
    "chabad_2_nissan" to listOf("Йорцайт Ребе Рашаба", "Yahrzeit of the Rebbe Rashab", "Jahrzeit des Rebbe Raschab", "הילולת אדמו״ר הרש״ב"),
    "chabad_11_nissan" to listOf("День рождения Ребе", "The Rebbe's birthday", "Geburtstag des Rebbe", "י״א ניסן — יום הולדת הרבי"),
    "chabad_3_tammuz" to listOf("Гимел Тамуз", "Gimmel Tammuz", "Gimmel Tammus", "ג׳ תמוז"),
    "chabad_12_tammuz" to listOf("Освобождение Ребе Раяца", "Liberation of the Previous Rebbe", "Befreiung des Rebbe Rajaz", "י״ב תמוז — חג הגאולה"),
    "chabad_18_elul" to listOf("Хай Элул — день рождения Бааль Шем Това и Алтер Ребе", "Chai Elul — birthday of the Baal Shem Tov and the Alter Rebbe", "Chai Elul — Geburtstag des Baal Schem Tow und des Alter Rebbe", "ח״י אלול"),
    "chabad_20_cheshvan" to listOf("День рождения Ребе Рашаба", "Birthday of the Rebbe Rashab", "Geburtstag des Rebbe Raschab", "כ׳ חשוון — יום הולדת הרש״ב"),
    // особые Шабаты
    "special_SHKALIM" to listOf("Шабат Шкалим", "Shabbat Shekalim", "Schabbat Schekalim", "שבת שקלים"),
    "special_ZACHOR" to listOf("Шабат Захор", "Shabbat Zachor", "Schabbat Sachor", "שבת זכור"),
    "special_PARA" to listOf("Шабат Пара", "Shabbat Parah", "Schabbat Para", "שבת פרה"),
    "special_HACHODESH" to listOf("Шабат ґа-Ходеш", "Shabbat HaChodesh", "Schabbat HaChodesch", "שבת החודש"),
    "special_SHUVA" to listOf("Шабат Шува", "Shabbat Shuvah", "Schabbat Schuwa", "שבת שובה"),
    "special_SHIRA" to listOf("Шабат Шира", "Shabbat Shirah", "Schabbat Schira", "שבת שירה"),
    "special_HAGADOL" to listOf("Шабат ґа-Гадоль", "Shabbat HaGadol", "Schabbat HaGadol", "שבת הגדול"),
    "special_CHAZON" to listOf("Шабат Хазон", "Shabbat Chazon", "Schabbat Chason", "שבת חזון"),
    "mevarchim" to listOf("Шабат мевархим", "Shabbat Mevarchim", "Schabbat Mewarchim", "שבת מברכים"),
    "special_NACHAMU" to listOf("Шабат Нахаму", "Shabbat Nachamu", "Schabbat Nachamu", "שבת נחמו")
)

/** Главы Торы — по `JewishCalendar.Parsha`. */
private val PARSHIOT: Map<String, List<String>> = mapOf(
    "BERESHIS" to listOf("Берешит", "Bereishit", "Bereschit", "בראשית"),
    "NOACH" to listOf("Ноах", "Noach", "Noach", "נח"),
    "LECH_LECHA" to listOf("Лех леха", "Lech Lecha", "Lech Lecha", "לך לך"),
    "VAYERA" to listOf("Вайера", "Vayeira", "Wajera", "וירא"),
    "CHAYEI_SARA" to listOf("Хаей Сара", "Chayei Sarah", "Chaje Sara", "חיי שרה"),
    "TOLDOS" to listOf("Толдот", "Toldot", "Toldot", "תולדות"),
    "VAYETZEI" to listOf("Ваеце", "Vayeitzei", "Wajeze", "ויצא"),
    "VAYISHLACH" to listOf("Ваишлах", "Vayishlach", "Wajischlach", "וישלח"),
    "VAYESHEV" to listOf("Ваешев", "Vayeishev", "Wajeschew", "וישב"),
    "MIKETZ" to listOf("Микец", "Mikeitz", "Mikez", "מקץ"),
    "VAYIGASH" to listOf("Ваигаш", "Vayigash", "Wajigasch", "ויגש"),
    "VAYECHI" to listOf("Ваехи", "Vayechi", "Wajechi", "ויחי"),
    "SHEMOS" to listOf("Шмот", "Shemot", "Schemot", "שמות"),
    "VAERA" to listOf("Ваэра", "Va'eira", "Waera", "וארא"),
    "BO" to listOf("Бо", "Bo", "Bo", "בא"),
    "BESHALACH" to listOf("Бешалах", "Beshalach", "Beschalach", "בשלח"),
    "YISRO" to listOf("Итро", "Yitro", "Jitro", "יתרו"),
    "MISHPATIM" to listOf("Мишпатим", "Mishpatim", "Mischpatim", "משפטים"),
    "TERUMAH" to listOf("Трума", "Terumah", "Teruma", "תרומה"),
    "TETZAVEH" to listOf("Тецаве", "Tetzaveh", "Tezawe", "תצוה"),
    "KI_SISA" to listOf("Ки тиса", "Ki Tisa", "Ki Tissa", "כי תשא"),
    "VAYAKHEL" to listOf("Ваякґель", "Vayakhel", "Wajakhel", "ויקהל"),
    "PEKUDEI" to listOf("Пкудей", "Pekudei", "Pekude", "פקודי"),
    "VAYIKRA" to listOf("Ваикра", "Vayikra", "Wajikra", "ויקרא"),
    "TZAV" to listOf("Цав", "Tzav", "Zaw", "צו"),
    "SHMINI" to listOf("Шмини", "Shemini", "Schemini", "שמיני"),
    "TAZRIA" to listOf("Тазриа", "Tazria", "Tasria", "תזריע"),
    "METZORA" to listOf("Мецора", "Metzora", "Mezora", "מצורע"),
    "ACHREI_MOS" to listOf("Ахарей мот", "Acharei", "Acharej Mot", "אחרי מות"),
    "KEDOSHIM" to listOf("Кдошим", "Kedoshim", "Kedoschim", "קדושים"),
    "EMOR" to listOf("Эмор", "Emor", "Emor", "אמור"),
    "BEHAR" to listOf("Беґар", "Behar", "Behar", "בהר"),
    "BECHUKOSAI" to listOf("Бехукотай", "Bechukotai", "Bechukotai", "בחוקותי"),
    "BAMIDBAR" to listOf("Бемидбар", "Bamidbar", "Bemidbar", "במדבר"),
    "NASSO" to listOf("Насо", "Naso", "Nasso", "נשא"),
    "BEHAALOSCHA" to listOf("Беґаалотха", "Behaalotecha", "Behaalotcha", "בהעלותך"),
    "SHLACH" to listOf("Шлах", "Shelach", "Schelach", "שלח"),
    "KORACH" to listOf("Корах", "Korach", "Korach", "קרח"),
    "CHUKAS" to listOf("Хукат", "Chukat", "Chukat", "חוקת"),
    "BALAK" to listOf("Балак", "Balak", "Balak", "בלק"),
    "PINCHAS" to listOf("Пинхас", "Pinchas", "Pinchas", "פינחס"),
    "MATOS" to listOf("Матот", "Matot", "Matot", "מטות"),
    "MASEI" to listOf("Масэй", "Masei", "Massej", "מסעי"),
    "DEVARIM" to listOf("Дварим", "Devarim", "Dewarim", "דברים"),
    "VAESCHANAN" to listOf("Ваэтханан", "Va'etchanan", "Waetchanan", "ואתחנן"),
    "EIKEV" to listOf("Экев", "Eikev", "Ekew", "עקב"),
    "REEH" to listOf("Рээ", "Re'eh", "Re'eh", "ראה"),
    "SHOFTIM" to listOf("Шофтим", "Shoftim", "Schoftim", "שופטים"),
    "KI_SEITZEI" to listOf("Ки теце", "Ki Teitzei", "Ki Teze", "כי תצא"),
    "KI_SAVO" to listOf("Ки таво", "Ki Tavo", "Ki Tawo", "כי תבוא"),
    "NITZAVIM" to listOf("Ницавим", "Nitzavim", "Nizawim", "נצבים"),
    "VAYEILECH" to listOf("Ваелех", "Vayeilech", "Wajelech", "וילך"),
    "HAAZINU" to listOf("Ґаазину", "Haazinu", "Haasinu", "האזינו"),
    "VZOS_HABERACHA" to listOf("Ве-зот ґа-браха", "Vezot Haberachah", "We'Sot HaBracha", "וזאת הברכה")
)

private fun pick(list: List<String>, l: L) = if (l == L.UK) (UK_NAMES[list[0]] ?: list[0]) else list[l.ordinal]

/** Украинский — по русскому названию (ה — «г», ג — «ґ», как читают по-украински). */
private val UK_NAMES: Map<String, String> = mapOf(
    "Канун Песаха" to "Переддень Песаху", "Песах" to "Песах", "Хол ґа-моэд Песаха" to "Хол га-моед Песаху", "Песах шени" to "Песах шені",
    "Канун Шавуота" to "Переддень Шавуоту", "Шавуот" to "Шавуот", "Пост 17 тамуза" to "Піст 17 тамуза", "Тиша бе-Ав" to "Тіша бе-Ав",
    "Ту бе-Ав" to "Ту бе-Ав", "Канун Рош ґа-Шана" to "Переддень Рош га-Шана", "Рош ґа-Шана" to "Рош га-Шана", "Пост Гедальи" to "Піст Ґедальї",
    "Канун Йом Кипура" to "Переддень Йом Кіпуру", "Йом Кипур" to "Йом Кіпур", "Канун Суккота" to "Переддень Сукоту", "Суккот" to "Сукот",
    "Хол ґа-моэд Суккота" to "Хол га-моед Сукоту", "Ґошана раба" to "Гошана раба", "Шмини Ацерет" to "Шміні Ацерет", "Симхат Тора" to "Сімхат Тора",
    "Пост 10 тевета" to "Піст 10 тевета", "Ту би-Шват" to "Ту бі-Шват", "Пост Эстер" to "Піст Естер", "Пурим" to "Пурім",
    "Шушан Пурим" to "Шушан Пурім", "Пурим катан" to "Пурім катан", "Шушан Пурим катан" to "Шушан Пурім катан", "Лаг ба-Омер" to "Лаг ба-Омер",
    "Рош Ходеш" to "Рош Ходеш",
    "Освобождение Мителер Ребе" to "Звільнення Мітелер Ребе",
    "Рош ґа-Шана хасидута — освобождение Алтер Ребе" to "Рош га-Шана хасидизму — звільнення Алтер Ребе",
    "Йорцайт Алтер Ребе" to "Йорцайт Алтер Ребе", "Йорцайт Ребе Раяца, начало руководства Ребе" to "Йорцайт Ребе Раяца, початок керівництва Ребе",
    "Йорцайт Ребе Рашаба" to "Йорцайт Ребе Рашаба", "День рождения Ребе" to "День народження Ребе", "Гимел Тамуз" to "Ґімел Тамуз",
    "Освобождение Ребе Раяца" to "Звільнення Ребе Раяца",
    "Хай Элул — день рождения Бааль Шем Това и Алтер Ребе" to "Хай Елул — день народження Бааль Шем Това і Алтер Ребе",
    "День рождения Ребе Рашаба" to "День народження Ребе Рашаба",
    "Шабат Шкалим" to "Шабат Шкалім", "Шабат Захор" to "Шабат Захор", "Шабат Пара" to "Шабат Пара", "Шабат ґа-Ходеш" to "Шабат га-Ходеш",
    "Шабат Шува" to "Шабат Шува", "Шабат Шира" to "Шабат Шіра", "Шабат ґа-Гадоль" to "Шабат га-Ґадоль", "Шабат Хазон" to "Шабат Хазон",
    "Шабат Нахаму" to "Шабат Нахаму", "Шабат мевархим" to "Шабат мевархім",
    // главы
    "Берешит" to "Берешит", "Ноах" to "Ноах", "Лех леха" to "Лех леха", "Вайера" to "Ваєра", "Хаей Сара" to "Хаєй Сара", "Толдот" to "Толдот",
    "Ваеце" to "Ваєце", "Ваишлах" to "Ваїшлах", "Ваешев" to "Ваєшев", "Микец" to "Мікец", "Ваигаш" to "Ваїґаш", "Ваехи" to "Ваєхі",
    "Шмот" to "Шмот", "Ваэра" to "Ваера", "Бо" to "Бо", "Бешалах" to "Бешалах", "Итро" to "Ітро", "Мишпатим" to "Мішпатім", "Трума" to "Трума",
    "Тецаве" to "Тецаве", "Ки тиса" to "Кі тіса", "Ваякґель" to "Ваякгель", "Пкудей" to "Пкудей", "Ваикра" to "Ваїкра", "Цав" to "Цав",
    "Шмини" to "Шміні", "Тазриа" to "Тазріа", "Мецора" to "Мецора", "Ахарей мот" to "Ахарей мот", "Кдошим" to "Кдошім", "Эмор" to "Емор",
    "Беґар" to "Бегар", "Бехукотай" to "Бехукотай", "Бемидбар" to "Бемідбар", "Насо" to "Насо", "Беґаалотха" to "Бегаалотха", "Шлах" to "Шлах",
    "Корах" to "Корах", "Хукат" to "Хукат", "Балак" to "Балак", "Пинхас" to "Пінхас", "Матот" to "Матот", "Масэй" to "Масей", "Дварим" to "Дварім",
    "Ваэтханан" to "Ваетханан", "Экев" to "Екев", "Рээ" to "Рее", "Шофтим" to "Шофтім", "Ки теце" to "Кі теце", "Ки таво" to "Кі таво",
    "Ницавим" to "Ніцавім", "Ваелех" to "Ваєлех", "Ґаазину" to "Гаазіну", "Ве-зот ґа-браха" to "Ве-зот га-браха"
)

/** Название главы (и сдвоенной — через дефис). */
fun parshaName(name: String, locale: Locale = Locale.getDefault()): String {
    val l = lang(locale)
    PARSHIOT[name]?.let { return pick(it, l) }
    val parts = name.split('_').let { p ->
        // сдвоенные: VAYAKHEL_PEKUDEI, ACHREI_MOS_KEDOSHIM, NITZAVIM_VAYEILECH…
        (1 until p.size).map { i -> p.take(i).joinToString("_") to p.drop(i).joinToString("_") }
            .firstOrNull { (a, b) -> a in PARSHIOT && b in PARSHIOT }
    } ?: return name
    return pick(PARSHIOT.getValue(parts.first), l) + "–" + pick(PARSHIOT.getValue(parts.second), l)
}

/** Название события дня. */
fun eventName(e: CalEvent, locale: Locale = Locale.getDefault()): String {
    val l = lang(locale)
    return when (e.kind) {
        CalKind.PARSHA -> parshaName(e.key.removePrefix("parsha_"), locale)
        CalKind.CHANUKAH -> when (l) {
            L.RU -> "Ханука · ${e.n}-я свеча"
            L.EN -> "Chanukah · candle ${e.n}"
            L.DE -> "Chanukka · ${e.n}. Kerze"
            L.HE -> "חנוכה · נר ${e.n}"
            L.UK -> "Ханука · ${e.n}-а свічка"
        }
        CalKind.OMER -> when (l) {
            L.RU -> "${e.n}-й день Омера"
            L.EN -> "Day ${e.n} of the Omer"
            L.DE -> "${e.n}. Tag des Omer"
            L.HE -> "יום ${e.n} לעומר"
            L.UK -> "${e.n}-й день Омеру"
        }
        // Шабат мевархим — с месяцем, который благословляют
        else -> (HOLIDAYS[e.key]?.let { pick(it, l) } ?: e.key) +
            if (e.key == "mevarchim") " · " + jewishMonthOnly(e.n, false, locale) else ""
    }
}

/** «Ханука» без номера свечи — для списка ближайших дат. */
fun eventTitle(e: CalEvent, locale: Locale = Locale.getDefault()): String =
    if (e.kind == CalKind.CHANUKAH) when (lang(locale)) { L.RU -> "Ханука"; L.EN -> "Chanukah"; L.DE -> "Chanukka"; L.HE -> "חנוכה"; L.UK -> "Ханука" }
    else eventName(e, locale)

private val MONTH_TITLES: Map<L, List<String>> = mapOf(
    L.RU to listOf("Нисан", "Ияр", "Сиван", "Тамуз", "Ав", "Элул", "Тишрей", "Хешван", "Кислев", "Тевет", "Шват", "Адар", "Адар II"),
    L.EN to listOf("Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul", "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar", "Adar II"),
    L.DE to listOf("Nissan", "Ijar", "Siwan", "Tammus", "Aw", "Elul", "Tischri", "Cheschwan", "Kislew", "Tewet", "Schwat", "Adar", "Adar II"),
    L.UK to listOf("Нісан", "Іяр", "Сіван", "Тамуз", "Ав", "Елул", "Тішрей", "Хешван", "Кіслев", "Тевет", "Шват", "Адар", "Адар II"),
    L.HE to listOf("ניסן", "אייר", "סיון", "תמוז", "אב", "אלול", "תשרי", "חשוון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳")
)

/** «Кислев 5787» — заголовок месяца (в високосный год 12-й месяц — Адар I). */
/** «Тевет» — название месяца (именительный); в високосный год 12-й — Адар I. */
fun jewishMonthOnly(month: Int, leap: Boolean, locale: Locale = Locale.getDefault()): String {
    val l = lang(locale)
    return if (leap && month == 12) (if (l == L.HE) "אדר א׳" else if (l == L.RU || l == L.UK) "Адар I" else "Adar I")
    else MONTH_TITLES.getValue(l)[(month - 1).coerceIn(0, 12)]
}

fun jewishMonthTitle(jd: JewishDay, locale: Locale = Locale.getDefault()): String {
    val l = lang(locale)
    val name = if (jd.leap && jd.month == 12) (if (l == L.HE) "אדר א׳" else "Adar I".let { if (l == L.RU || l == L.UK) "Адар I" else it })
    else MONTH_TITLES.getValue(l)[(jd.month - 1).coerceIn(0, 12)]
    return "$name ${jd.year}"
}
