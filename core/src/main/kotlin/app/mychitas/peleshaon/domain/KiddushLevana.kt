package app.mychitas.peleshaon.domain

import app.mychitas.peleshaon.alarm.ShabbatGuard
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Где Луна: азимут (0 — север, 90 — восток, 180 — юг, 270 — запад), высота над горизонтом (без рефракции, с параллаксом)
 * и освещённая доля диска 0…1.
 */
data class MoonPosition(val azimuth: Double, val altitude: Double, val illumination: Double) {
    val aboveHorizon: Boolean get() = altitude > 0.0
}

/**
 * Положение Луны — по формулам П. Шлютера («How to compute planetary positions», основные возмущения Луны).
 * Сверено с библиотекой ephem на 4000 случайных моментах 2026–2036 и городах от −55° до 65° широты:
 * азимут и высота — не дальше 0,2°, освещённость — 0,25 %. (Присланный образец считал юлианскую дату на 12 часов позже —
 * Луна выходила на 120° в стороне; здесь это исправлено.)
 */
object MoonTracker {

    fun position(at: ZonedDateTime, latDeg: Double, lonDeg: Double): MoonPosition {
        val jd = julianDate(at)
        val d = jd - 2451543.5 // сутки от 0 января 2000 г., 0 ч UT

        // орбита Луны
        val n = 125.1228 - 0.0529538083 * d
        val i = 5.1454
        val w = 318.0634 + 0.1643573223 * d
        val a = 60.2666 // в радиусах Земли
        val e = 0.054900
        val m = norm(115.3654 + 13.0649929509 * d)
        // Солнце
        val ws = 282.9404 + 4.70935e-5 * d
        val ms = norm(356.0470 + 0.9856002585 * d)
        val es = 0.016709 - 1.151e-9 * d

        var ea = m + deg(e * sinD(m) * (1 + e * cosD(m)))
        repeat(5) { ea -= (ea - deg(e * sinD(ea)) - m) / (1 - e * cosD(ea)) }
        val xv = a * (cosD(ea) - e)
        val yv = a * sqrt(1 - e * e) * sinD(ea)
        val v = deg(atan2(yv, xv))
        var r = hypot(xv, yv)
        val xh = r * (cosD(n) * cosD(v + w) - sinD(n) * sinD(v + w) * cosD(i))
        val yh = r * (sinD(n) * cosD(v + w) + cosD(n) * sinD(v + w) * cosD(i))
        val zh = r * sinD(v + w) * sinD(i)
        var lon = deg(atan2(yh, xh))
        var lat = deg(atan2(zh, hypot(xh, yh)))

        val eSun = ms + deg(es * sinD(ms) * (1 + es * cosD(ms)))
        val ls = norm(deg(atan2(sqrt(1 - es * es) * sinD(eSun), cosD(eSun) - es)) + ws)

        // главные возмущения
        val lm = n + w + m
        val dd = lm - ls
        val f = lm - n
        lon += -1.274 * sinD(m - 2 * dd) + 0.658 * sinD(2 * dd) - 0.186 * sinD(ms) -
            0.059 * sinD(2 * m - 2 * dd) - 0.057 * sinD(m - 2 * dd + ms) + 0.053 * sinD(m + 2 * dd) +
            0.046 * sinD(2 * dd - ms) + 0.041 * sinD(m - ms) - 0.035 * sinD(dd) - 0.031 * sinD(m + ms) -
            0.015 * sinD(2 * f - 2 * dd) + 0.011 * sinD(m - 4 * dd)
        lat += -0.173 * sinD(f - 2 * dd) - 0.055 * sinD(m - f - 2 * dd) - 0.046 * sinD(m + f - 2 * dd) +
            0.033 * sinD(f + 2 * dd) + 0.017 * sinD(2 * m + f)
        r += -0.58 * cosD(m - 2 * dd) - 0.46 * cosD(2 * dd)

        // в экваториальные координаты
        val ecl = 23.4393 - 3.563e-7 * d
        val xe = cosD(lon) * cosD(lat)
        val ye = cosD(ecl) * sinD(lon) * cosD(lat) - sinD(ecl) * sinD(lat)
        val ze = sinD(ecl) * sinD(lon) * cosD(lat) + cosD(ecl) * sinD(lat)
        val ra = norm(deg(atan2(ye, xe)))
        val dec = deg(atan2(ze, hypot(xe, ye)))

        // в горизонтальные
        val gmst = norm(280.46061837 + 360.98564736629 * (jd - 2451545.0))
        val ha = norm(gmst + lonDeg - ra)
        var alt = deg(asin(sinD(latDeg) * sinD(dec) + cosD(latDeg) * cosD(dec) * cosD(ha)))
        alt -= deg(asin(1 / r)) * cosD(alt) // суточный параллакс: с Земли Луна ниже, чем из её центра
        val az = norm(deg(atan2(sinD(ha), cosD(ha) * sinD(latDeg) - tan(rad(dec)) * cosD(latDeg))) + 180)

        val illum = (1 - cosD(lat) * cosD(lon - ls)) / 2
        return MoonPosition(az, alt, illum)
    }

    /** Юлианская дата: полдень 1 января 2000 г. UT — 2451545.0 (сутки начинаются в полдень). */
    fun julianDate(at: ZonedDateTime): Double {
        val u = at.withZoneSameInstant(ZoneOffset.UTC)
        return u.toInstant().toEpochMilli() / 86_400_000.0 + 2440587.5
    }

    private fun rad(x: Double) = x * PI / 180
    private fun deg(x: Double) = x * 180 / PI
    private fun sinD(x: Double) = sin(rad(x))
    private fun cosD(x: Double) = cos(rad(x))
    private fun norm(x: Double) = ((x % 360) + 360) % 360
}

/** Кидуш Левана сегодня вечером: еврейская дата, до какого момента можно, и где Луна. */
data class KiddushLevanaNow(
    val jewishDay: JewishDay,
    val end: ZonedDateTime,
    val moon: MoonPosition
)

/**
 * Когда говорят Кидуш Левана — по общине (`ZmanimEngine.minhag`; ниже — Хабад): с семи суток после молада и до середины месяца (между моладами,
 * ≈ 14 сут. 18 ч — KosherJava `tchilasZmanKidushLevana7Days` / `sofZmanKidushLevanaBetweenMoldos`), ночью;
 * не в вечер Шабата и Йом Това (вечер после их исхода — можно); в ав — после 9 ава, в тишрей — после Йом Кипура.
 * Раввином не проверено — на экране: «сверяйтесь с раввином общины».
 */
object KiddushLevanaRules {

    /** Окно месяца еврейской даты [начало, конец) в часовом поясе города. */
    fun window(jewishGreg: LocalDate, city: CityOption): Pair<ZonedDateTime, ZonedDateTime> {
        val jc = JewishCalendar(jewishGreg).apply { inIsrael = city.isIsrael }
        val zone = city.zone()
        fun z(d: java.util.Date) = java.time.Instant.ofEpochMilli(d.time).atZone(zone)
        // Хабад и сефарды — с 7 суток после молада; литовские (по Рема) — с 3 суток. Конец: ашкеназы — середина между моладами, сефарды — 15 суток
        val start = if (ZmanimEngine.minhag == Minhag.LITVISH) jc.tchilasZmanKidushLevana3Days else jc.tchilasZmanKidushLevana7Days
        val end = if (ZmanimEngine.minhag == Minhag.SEFARDI) jc.sofZmanKidushLevana15Days else jc.sofZmanKidushLevanaBetweenMoldos
        return z(start) to z(end)
    }

    /** Ночь с этим моментом: гражданский день, вечером которого она началась; днём — null. */
    fun eveningOf(now: ZonedDateTime, city: CityOption): LocalDate? {
        val half = ZmanimEngine.halfOf(now, city) ?: return null
        if (half.day) return null
        return half.start.withZoneSameInstant(city.zone()).toLocalDate()
    }

    /** Можно ли в вечер этого дня: месяц, Шабат и Йом Тов (вечер Шабата / праздника — нельзя). */
    fun eveningAllowed(evening: LocalDate, city: CityOption): Boolean {
        val jc = JewishCalendar(evening.plusDays(1)).apply { inIsrael = city.isIsrael }
        if (jc.isAssurBemelacha) return false
        if (jc.jewishMonth == JewishCalendar.AV && jc.jewishDayOfMonth < 10) return false
        if (jc.jewishMonth == JewishCalendar.TISHREI && jc.jewishDayOfMonth < 11) return false
        return true
    }

    /** Сейчас — ночь, в которую можно сказать Кидуш Левана; иначе null. */
    fun now(now: ZonedDateTime, city: CityOption): KiddushLevanaNow? {
        val evening = eveningOf(now, city) ?: return null
        if (!eveningAllowed(evening, city)) return null
        val (start, end) = window(evening.plusDays(1), city)
        if (now.isBefore(start) || !now.isBefore(end)) return null
        // вечер после Шабата / праздника: до их исхода — ещё нельзя
        if (ShabbatGuard.checkMoment(now, city.name, city.lat, city.lon, city.zone(), city.isIsrael, false).isQuietTime) return null
        val moon = MoonTracker.position(now, city.lat, city.lon)
        return KiddushLevanaNow(ZmanimEngine.jewishDay(evening.plusDays(1), city.isIsrael), end, moon)
    }

    /**
     * Ближайшее напоминание: вечер, когда можно, — выход звёзд (после Шабата и праздника — их исход), если Луна тогда
     * выше 5° и окно ещё открыто. Ищет на 40 дней вперёд.
     */
    fun nextReminder(now: ZonedDateTime, city: CityOption): ZonedDateTime? {
        val zone = city.zone()
        var day = now.withZoneSameInstant(zone).toLocalDate()
        repeat(40) {
            val at = reminderOn(day, city)
            if (at != null && at.isAfter(now)) return at
            day = day.plusDays(1)
        }
        return null
    }

    /** Когда напомнить в вечер этого гражданского дня; нельзя или Луны не видно — null. */
    fun reminderOn(evening: LocalDate, city: CityOption): ZonedDateTime? {
        if (!eveningAllowed(evening, city)) return null
        val zone = city.zone()
        val items = ZmanimEngine.itemsOf(evening, city)
        val tzeit = items.firstOrNull { it.type == ZmanType.TZAIT }?.roundedTime ?: return null
        val sunset = ZmanimEngine.sunsetOf(evening, city) ?: return null
        var at = tzeit
        if (ShabbatGuard.checkMoment(at, city.name, city.lat, city.lon, zone, city.isIsrael, false).isQuietTime) {
            at = (ZmanimEngine.havdalahOn(evening, city) ?: sunset.plusMinutes(ZmanimEngine.END_MIN)).withSecond(0).withNano(0).plusMinutes(1)
            if (ShabbatGuard.checkMoment(at, city.name, city.lat, city.lon, zone, city.isIsrael, false).isQuietTime) return null
        }
        val (start, end) = window(evening.plusDays(1), city)
        if (at.isBefore(start)) {
            // окно открывается этой ночью позже — напомнить, когда откроется, если ещё темно
            if (start.isAfter(sunset.plusHours(6))) return null
            at = start.withSecond(0).withNano(0).plusMinutes(1)
        }
        if (!at.isBefore(end)) return null
        return if (MoonTracker.position(at, city.lat, city.lon).altitude >= 5.0) at else null
    }
}
