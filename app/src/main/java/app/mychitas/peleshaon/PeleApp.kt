package app.mychitas.peleshaon

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import app.mychitas.peleshaon.domain.Minhag
import app.mychitas.peleshaon.domain.ZmanimEngine
import java.util.Locale

/** При запуске — община (по ней считаются времена) и язык приложения: и для экрана, и для будильников, виджетов, уведомлений. */
class PeleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ZmanimEngine.minhag = Prefs.minhag(this)
        AppLang.applyToProcess(this)
    }
}

/**
 * Язык приложения (владелец, 18.11.2026: «отдельно настройки: выбор языка»): «как в телефоне» или один из пяти.
 * Android 13+ — системный язык приложения (LocaleManager); раньше — подменой конфигурации в окнах и у приложения.
 */
object AppLang {
    /** "" — как в телефоне */
    val TAGS = listOf("", "uk", "ru", "en", "de", "he")

    fun tag(context: Context): String = Prefs.of(context).getString("lang", "") ?: ""

    fun set(context: Context, tag: String) {
        Prefs.of(context).edit().putString("lang", tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            applyToProcess(context.applicationContext)
        }
    }

    /** До Android 13 — язык процесса: по умолчанию и в ресурсах приложения (их читают будильники и виджеты). */
    @Suppress("DEPRECATION")
    fun applyToProcess(app: Context) {
        if (Build.VERSION.SDK_INT >= 33) return
        val t = tag(app)
        val loc = if (t.isEmpty()) Resources0.system else Locale.forLanguageTag(t)
        Locale.setDefault(loc)
        val res = app.resources
        val conf = Configuration(res.configuration).apply { setLocale(loc) }
        res.updateConfiguration(conf, res.displayMetrics)
    }

    /** Окно — на выбранном языке (до Android 13). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val t = tag(base)
        if (t.isEmpty()) return base
        val loc = Locale.forLanguageTag(t)
        Locale.setDefault(loc)
        val conf = Configuration(base.resources.configuration).apply { setLocale(loc) }
        return base.createConfigurationContext(conf)
    }

    /** Название языка — на нём самом. */
    fun label(tag: String): String = when (tag) {
        "uk" -> "Українська"; "ru" -> "Русский"; "en" -> "English"; "de" -> "Deutsch"; "he" -> "עברית"; else -> ""
    }
}

private object Resources0 {
    /** язык телефона */
    val system: Locale get() = android.content.res.Resources.getSystem().configuration.locales[0]
}
