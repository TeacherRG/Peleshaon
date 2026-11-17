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
        ZmanimEngine.candleOffsetOverride = Prefs.candleOffset(this)
        AppLang.migratePlatformLanguage(this)
        AppLang.applyToProcess(this)
    }
}

/**
 * Язык приложения (владелец, 18.11.2026: «отдельно настройки: выбор языка»): «как в телефоне» или один из пяти.
 * Язык ставится сам, подменой конфигурации в окнах и у приложения — на всех версиях: системный язык приложения
 * (LocaleManager, Android 13+) на некоторых телефонах (ColorOS) не применяется — владелец, 18.11.2026: «язык нигде не меняется».
 * LocaleManager тоже ставится — чтобы выбор был виден в настройках телефона.
 */
object AppLang {
    /** "" — как в телефоне */
    val TAGS = listOf("", "uk", "ru", "en", "de", "he")

    private const val MIGRATED = "lang_platform_migrated"

    /** Android 13+: systemic per-app language is authoritative, including changes made in system Settings. */
    fun tag(context: Context): String {
        val prefs = Prefs.of(context)
        if (Build.VERSION.SDK_INT >= 33) {
            val systemTags = runCatching {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            }.getOrNull()
            if (systemTags != null) {
                val raw = systemTags.substringBefore(',').substringBefore('-')
                val selected = when (raw) { "iw" -> "he"; else -> raw }.takeIf { it in TAGS } ?: ""
                if (prefs.getString("lang", "") != selected) prefs.edit().putString("lang", selected).apply()
                return selected
            }
        }
        return prefs.getString("lang", "") ?: ""
    }

    /** Migrate existing in-app selection once, without overriding later external system changes. */
    fun migratePlatformLanguage(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = Prefs.of(context)
        if (prefs.getBoolean(MIGRATED, false)) return
        val stored = prefs.getString("lang", "") ?: ""
        runCatching {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.toLanguageTags().isEmpty() && stored.isNotEmpty()) {
                manager.applicationLocales = LocaleList.forLanguageTags(stored)
            }
            prefs.edit().putBoolean(MIGRATED, true).apply()
        }
    }

    fun set(context: Context, tag: String) {
        val selected = tag.takeIf { it in TAGS } ?: ""
        Prefs.of(context).edit().putString("lang", selected).putBoolean(MIGRATED, true).apply()
        if (Build.VERSION.SDK_INT >= 33) runCatching {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (selected.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(selected)
        }
        applyToProcess(context.applicationContext)
    }

    /** Язык процесса: по умолчанию и в ресурсах приложения (их читают будильники и виджеты). */
    @Suppress("DEPRECATION")
    fun applyToProcess(app: Context) {
        val t = tag(app)
        val loc = if (t.isEmpty()) Resources0.system else Locale.forLanguageTag(t)
        Locale.setDefault(loc)
        val res = app.resources
        val conf = Configuration(res.configuration).apply { setLocale(loc) }
        // On Android 13+ LocaleManager updates the app's resources and recreates Activity.
        // Keep legacy app-context resources synchronized only on older systems.
        if (Build.VERSION.SDK_INT < 33) res.updateConfiguration(conf, res.displayMetrics)
    }

    /** Окно — на выбранном языке. */
    fun wrap(base: Context): Context {
        val t = tag(base)
        val loc = if (t.isEmpty()) Resources0.system else Locale.forLanguageTag(t)
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
