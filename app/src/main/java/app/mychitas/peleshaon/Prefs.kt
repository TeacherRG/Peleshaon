package app.mychitas.peleshaon

import android.content.Context
import android.content.SharedPreferences
import app.mychitas.peleshaon.alarm.AlarmSpec
import app.mychitas.peleshaon.alarm.WakeMode
import app.mychitas.peleshaon.domain.CityOption
import app.mychitas.peleshaon.domain.ZmanType
import app.mychitas.peleshaon.domain.cityById
import app.mychitas.peleshaon.domain.cityForZone
import java.time.ZoneId

/**
 * Настройки — только на этом телефоне (`pele_prefs`), как «Часы» на сайте хранят свои в браузере.
 * Будильники: `MORNING` — будильник (в это время или до конца Шма), `TORAH` — будильник учёбы Торы.
 */
object Prefs {
    const val NAME = "pele_prefs"
    const val MORNING = "MORNING"
    const val TORAH = "TORAH"

    fun of(context: Context): SharedPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // ---- город: выбранный вручную, иначе — по часовому поясу телефона

    /** Выбранный город; null — по часовому поясу. */
    fun chosenCity(context: Context): CityOption? = cityById(of(context).getString("city_id", null))

    fun city(context: Context): CityOption = chosenCity(context) ?: cityForZone(ZoneId.systemDefault())

    fun setCity(context: Context, city: CityOption?) {
        of(context).edit().apply {
            if (city == null) remove("city_id") else putString("city_id", city.id)
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

    fun tziruf(context: Context): Boolean = of(context).getBoolean("clock_tziruf", true)
    fun setTziruf(context: Context, on: Boolean) = of(context).edit().putBoolean("clock_tziruf", on).apply()

    // ---- будильники

    private fun prefix(type: String) = if (type == MORNING) "morning" else "torah"

    fun alarm(context: Context, type: String): AlarmSpec {
        val p = of(context)
        val k = prefix(type)
        val morning = type == MORNING
        val days = p.getString("${k}_days", null)
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 0..6 }?.toSet()
            ?: if (morning) (0..6).toSet() else AlarmSpec.WEEKDAYS
        return AlarmSpec(
            on = p.getBoolean("${k}_enabled", false),
            byShma = morning && p.getBoolean("${k}_by_shma", true),
            hour = p.getInt("${k}_hour", if (morning) 6 else 20),
            minute = p.getInt("${k}_minute", if (morning) 30 else 0),
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
}
