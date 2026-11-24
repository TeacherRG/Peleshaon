package app.mychitas.peleshaon

import android.content.Context
import android.content.SharedPreferences
import app.mychitas.peleshaon.alarm.AlarmSpec
import app.mychitas.peleshaon.alarm.KashrutPolicy
import app.mychitas.peleshaon.alarm.WakeMode
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.cityById
import app.mychitas.peleshaon.domain.cityForZone
import app.mychitas.peleshaon.domain.cityMatchForZone
import java.time.ZoneId

/**
 * Настройки — только на этом телефоне (`pele_prefs`), как «Часы» на сайте хранят свои в браузере.
 * Будильники: `MORNING` — будильник (в это время или до конца Шма), `TORAH` — будильник учёбы Торы.
 */
object Prefs {
    const val NAME = "pele_prefs"
    const val MORNING = "MORNING"
    const val TORAH = "TORAH"
    /** таймер «мясное → молочное»: один звонок через 6 часов */
    const val KASHRUT = "KASHRUT"
    /** свои будильники — «C1», «C2»… (владелец, 18.11.2026: «возможность добавить будильники») */
    const val MAX_CUSTOM = 10
    const val KASHRUT_HOURS = KashrutPolicy.WAIT_HOURS

    /** The old absolute deadline has no start timestamp; never assume it was six hours. */
    fun migrateKashrut(context: Context) {
        val p = of(context)
        val version = p.getInt("kashrut_policy_version", 0)
        if (version >= KashrutPolicy.POLICY_VERSION) return
        val reset = KashrutPolicy.resetLegacyTimer(version, p.getLong("kashrut_at", 0L))
        p.edit().apply {
            if (reset) {
                putLong("kashrut_at", 0L)
                putBoolean("kashrut_timer_reset", true)
            }
            remove("kashrut_hours")
            putInt("kashrut_policy_version", KashrutPolicy.POLICY_VERSION)
        }.commit()
    }

    fun kashrutTimerWasReset(context: Context): Boolean = of(context).getBoolean("kashrut_timer_reset", false)
    fun clearKashrutReset(context: Context) { of(context).edit().putBoolean("kashrut_timer_reset", false).apply() }
    fun kashrutDeferred(context: Context): Boolean = of(context).getBoolean("kashrut_deferred", false)
    fun setKashrutDeferred(context: Context, on: Boolean) {
        of(context).edit().putBoolean("kashrut_deferred", on).apply()
    }

    /** Extra silence before Friday candle lighting (not a change to calendar or candle time). */
    fun earlyShabbatMinutes(context: Context): Long =
        of(context).getInt("early_shabbat_quiet", 0).takeIf { it in setOf(0, 15, 30, 45, 60) }?.toLong() ?: 0L

    fun setEarlyShabbatMinutes(context: Context, minutes: Long) {
        require(minutes in setOf(0L, 15L, 30L, 45L, 60L))
        of(context).edit().putInt("early_shabbat_quiet", minutes.toInt()).apply()
    }

    fun of(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- город: religious calculations require an explicit valid city selection

    /** Saved user selection, with optional explicit civil-time zone. */
    fun chosenCity(context: Context): CityOption? {
        val p = of(context)
        val id = p.getString("city_id", null) ?: return null
        if (id == app.mychitas.peleshaon.domain.ManualLocation.ID) {
            return app.mychitas.peleshaon.domain.ManualLocation.parse(
                p.getString("custom_city_name", null), p.getString("custom_city_lat", null),
                p.getString("custom_city_lon", null), p.getString("custom_city_zone", null),
                p.getBoolean("custom_city_is_israel", false)
            )
        }
        val base = cityById(id) ?: return null
        val selectedZone = app.mychitas.peleshaon.domain.ManualLocation.zoneOrNull(
            p.getString("city_zone_override", null)
        ) ?: return base
        return base.copy(zoneId = selectedZone.id)
    }

    fun city(context: Context): CityOption = chosenCity(context) ?: cityForZone(ZoneId.systemDefault())

    /** Automatic matching by a time zone is approximate until a city is confirmed. */
    fun cityUnconfirmed(context: Context): Boolean = chosenCity(context) == null

    /** If neither a manual city nor a timezone match exists, do not schedule religious alarms. */
    /** A no-longer-supported saved city must NEVER turn into another city silently.
     * Keep its stored ID until the user explicitly chooses a supported city or manual point.
     * In this state all geographic religious alarms are held and the UI warns the user.
     */
    fun citySelectionObsolete(context: Context): Boolean =
        of(context).getString("city_id", null) != null && chosenCity(context) == null

    // Timezone can display ordinary clock time only; never authorize religious events.
    fun cityUnmatched(context: Context): Boolean = chosenCity(context) == null

    fun setCity(context: Context, city: CityOption?) {
        of(context).edit().apply {
            if (city == null) {
                remove("city_id")
                remove("city_zone_override")
            } else if (city.id == app.mychitas.peleshaon.domain.ManualLocation.ID) {
                requireNotNull(app.mychitas.peleshaon.domain.ManualLocation.parse(
                    city.name, city.lat.toString(), city.lon.toString(), city.zoneId, city.isIsrael
                )) { "Invalid manual city coordinates or time zone" }
                putString("city_id", city.id)
                putString("custom_city_name", city.name)
                putString("custom_city_lat", city.lat.toString())
                putString("custom_city_lon", city.lon.toString())
                putString("custom_city_zone", city.zoneId)
                putBoolean("custom_city_is_israel", city.isIsrael)
                remove("city_zone_override")
            } else {
                requireNotNull(cityById(city.id)) { "Only catalog or explicit manual coordinates allowed" }
                putString("city_id", city.id)
                val original = cityById(city.id)!!
                if (original.zoneId == city.zoneId) remove("city_zone_override")
                else {
                    requireNotNull(app.mychitas.peleshaon.domain.ManualLocation.zoneOrNull(city.zoneId))
                    putString("city_zone_override", city.zoneId)
                }
            }
        }.apply()
    }

    // ---- отметки на циферблате и «Сочетание Имени часа»

    fun marks(context: Context): Set<ZmanType> {
        val saved = of(context).getString("clock_marks", null) ?: return ZmanType.entries.filter { it.defaultPinned }.toSet()
        return saved.split(',').mapNotNull { n -> ZmanType.entries.find { it.name == n } }.toSet()
    }

    fun setMarks(context: Context, marks: Set<ZmanType>) {
        of(context).edit().putString("clock_marks", ZmanType.entries.filter { it in marks }.joinToString(",") { it.name }).apply()
    }

    fun tziruf(context: Context): Boolean = of(context).getBoolean("clock_tziruf", false)
    fun setTziruf(context: Context, on: Boolean) = of(context).edit().putBoolean("clock_tziruf", on).apply()

    // ---- dual clock presentation (civil + halachic), independent of zmanim calculations

    const val TIME_CIVIL = "CIVIL"
    const val TIME_HALACHIC = "HALACHIC"
    const val FACE_DIGITAL = "DIGITAL"
    const val FACE_ANALOG = "ANALOG"

    fun mainTimeType(context: Context): String =
        of(context).getString("main_time_type", TIME_CIVIL)
            ?.takeIf { it == TIME_CIVIL || it == TIME_HALACHIC } ?: TIME_CIVIL

    fun setMainTimeType(context: Context, type: String) {
        require(type == TIME_CIVIL || type == TIME_HALACHIC)
        of(context).edit().putString("main_time_type", type).apply()
    }

    /** The secondary dial is always the opposite time system; there is no independent duplicate choice. */
    fun secondaryTimeType(context: Context): String =
        if (mainTimeType(context) == TIME_CIVIL) TIME_HALACHIC else TIME_CIVIL

    fun faceStyle(context: Context): String =
        of(context).getString("face_style", FACE_DIGITAL)
            ?.takeIf { it == FACE_DIGITAL || it == FACE_ANALOG } ?: FACE_DIGITAL

    fun setFaceStyle(context: Context, style: String) {
        require(style == FACE_DIGITAL || style == FACE_ANALOG)
        of(context).edit().putString("face_style", style).apply()
    }

    fun secondaryFaceStyle(context: Context): String =
        of(context).getString("secondary_face_style", FACE_DIGITAL)
            ?.takeIf { it == FACE_DIGITAL || it == FACE_ANALOG } ?: FACE_DIGITAL

    fun setSecondaryFaceStyle(context: Context, style: String) {
        require(style == FACE_DIGITAL || style == FACE_ANALOG)
        of(context).edit().putString("secondary_face_style", style).apply()
    }

    /** Оформление циферблата (`FaceSkin.key`), владелец, 24.11.2026. */
    fun faceSkin(context: Context): String = of(context).getString("face_skin", "classic") ?: "classic"
    fun setFaceSkin(context: Context, key: String) = of(context).edit().putString("face_skin", key).apply()
    /** На циферблате: дата, восход и закат, основные зманим точками на ободе. */
    fun showDate(context: Context): Boolean = of(context).getBoolean("face_show_date", true)
    fun setShowDate(context: Context, on: Boolean) = of(context).edit().putBoolean("face_show_date", on).apply()
    fun showSun(context: Context): Boolean = of(context).getBoolean("face_show_sun", true)
    fun setShowSun(context: Context, on: Boolean) = of(context).edit().putBoolean("face_show_sun", on).apply()
    fun showZmanim(context: Context): Boolean = of(context).getBoolean("face_show_zmanim", true)
    fun setShowZmanim(context: Context, on: Boolean) = of(context).edit().putBoolean("face_show_zmanim", on).apply()

    fun subdialEnabled(context: Context): Boolean =
        of(context).getBoolean("subdial_enabled", true)

    fun setSubdialEnabled(context: Context, enabled: Boolean) {
        of(context).edit().putBoolean("subdial_enabled", enabled).apply()
    }

    // ---- будильники

    private fun prefix(type: String) = when (type) { MORNING -> "morning"; TORAH -> "torah"; else -> type.lowercase() }

    fun alarm(context: Context, type: String): AlarmSpec {
        val p = of(context)
        val k = prefix(type)
        val morning = type != TORAH
        val days = p.getString("${k}_days", null)
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 0..6 }?.toSet()
            ?: if (morning) (0..6).toSet() else AlarmSpec.WEEKDAYS
        return AlarmSpec(
            on = p.getBoolean("${k}_enabled", false),
            byShma = morning && p.getBoolean("${k}_by_shma", type == MORNING),
            hour = p.getInt("${k}_hour", if (type == MORNING) 6 else if (morning) 7 else 20),
            minute = p.getInt("${k}_minute", if (type == MORNING) 30 else 0),
            before = p.getInt("${k}_offset_shma", 30),
            days = days,
            chm = p.getBoolean("chm_$type", p.getBoolean("mute_chol_hamoed", false))
        )
    }

    fun setAlarm(context: Context, type: String, spec: AlarmSpec) {
        val k = prefix(type)
        of(context).edit()
            .putBoolean("${k}_enabled", spec.on)
            .putBoolean("${k}_by_shma", spec.byShma)
            .putInt("${k}_hour", spec.hour)
            .putInt("${k}_minute", spec.minute)
            .putInt("${k}_offset_shma", spec.before)
            .putString("${k}_days", spec.days.sorted().joinToString(","))
            .putBoolean("chm_$type", spec.chm)
            .apply()
    }

    fun wakeMode(context: Context, type: String): WakeMode =
        runCatching { WakeMode.valueOf(of(context).getString("wake_mode_$type", null) ?: "") }
            .getOrDefault(WakeMode.SOUND_AND_LIGHT)

    fun setWakeMode(context: Context, type: String, mode: WakeMode) =
        of(context).edit().putString("wake_mode_$type", mode.name).apply()

    // ---- свои будильники

    fun customAlarms(context: Context): List<String> =
        of(context).getString("custom_alarms", "")!!.split(',').filter { it.matches(Regex("C\\d+")) }

    /** Новый будильник: по умолчанию включён, 7:00, все дни. Больше `MAX_CUSTOM` — null. */
    fun addCustomAlarm(context: Context): String? {
        val list = customAlarms(context)
        if (list.size >= MAX_CUSTOM) return null
        val n = (1..99).first { "C$it" !in list }
        val id = "C$n"
        of(context).edit().putString("custom_alarms", (list + id).joinToString(",")).apply()
        // An ID can be reused: initialize every field rather than inheriting deleted values.
        setAlarm(context, id, AlarmSpec(on = true, byShma = false, hour = 7, minute = 0,
            before = 30, days = (0..6).toSet(), chm = false))
        setWakeMode(context, id, WakeMode.SOUND_AND_LIGHT)
        return id
    }

    fun removeCustomAlarm(context: Context, id: String) {
        val key = prefix(id)
        val editor = of(context).edit()
            .putString("custom_alarms", customAlarms(context).filter { it != id }.joinToString(","))
            .remove("chm_$id").remove("wake_mode_$id").remove("vibrate_$id")
        for (suffix in listOf("enabled", "by_shma", "hour", "minute", "offset_shma", "days"))
            editor.remove("${key}_$suffix")
        editor.apply()
    }

    /** Номер будильника для подписи: «Будильник 2» (основной — первый). */
    fun customNumber(id: String): Int = (id.removePrefix("C").toIntOrNull() ?: 0) + 1

    // ---- мясное → молочное: когда можно молочное (мс), 0 — таймер не идёт

    fun kashrutAt(context: Context): Long = of(context).getLong("kashrut_at", 0L)
    fun setKashrutAt(context: Context, at: Long) = of(context).edit().putLong("kashrut_at", at).apply()

    // ---- община: по ней считаются времена дня, исход Шабата, Кидуш Левана и даты

    fun minhag(context: Context): app.mychitas.peleshaon.domain.Minhag =
        runCatching { app.mychitas.peleshaon.domain.Minhag.valueOf(of(context).getString("minhag", null) ?: "") }
            .getOrDefault(app.mychitas.peleshaon.domain.Minhag.CHABAD)

    /** null = city default (Jerusalem 40m, Haifa 30m, Tel Aviv 22m, others 18m). */
    fun candleOffset(context: Context): Long? =
        of(context).getInt("candle_offset", -1).takeIf { it in setOf(18, 20, 22, 30, 40) }?.toLong()

    fun setCandleOffset(context: Context, minutes: Long?) {
        val value = minutes?.toInt()?.takeIf { it in setOf(18, 20, 22, 30, 40) } ?: -1
        of(context).edit().putInt("candle_offset", value).apply()
    }

    fun setMinhag(context: Context, m: app.mychitas.peleshaon.domain.Minhag) =
        of(context).edit().putString("minhag", m.name).apply()
}
