package app.mychitas.peleshaon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import android.content.Context
import app.mychitas.peleshaon.Prefs
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.domain.ZmanimEngine
import java.time.ZonedDateTime

/**
 * Тема «Пеле Шаон» (владелец, 18.11.2026): «Авто» — днём (от восхода до заката в городе) светлая, ночью тёмная;
 * или всегда светлая / всегда тёмная. Выбор — `theme` в настройках телефона. Ночной экран, заставка и виджеты — всегда тёмные.
 */
object ClockTheme {
    const val AUTO = "auto"
    const val LIGHT = "light"
    const val DARK = "dark"

    var mode by mutableStateOf(AUTO)
    var dayNow by mutableStateOf(false)
    /** ночной экран / заставка открыты — темно, что бы ни выбрали */
    var darkHolds by mutableIntStateOf(0)
    /** картинка виджета рисуется в этом потоке — тёмная */
    val renderDark = ThreadLocal<Boolean>()

    val light: Boolean
        get() = renderDark.get() != true && darkHolds == 0 && (mode == LIGHT || (mode == AUTO && dayNow))

    fun load(context: Context) {
        mode = Prefs.of(context).getString("theme", AUTO) ?: AUTO
        refresh(context)
    }

    fun set(context: Context, m: String) {
        mode = m
        Prefs.of(context).edit().putString("theme", m).apply()
    }

    /** День ли сейчас в городе: от восхода до заката; нет восхода / заката — 7:00–19:00. */
    fun refresh(context: Context) {
        val city = Prefs.city(context)
        val now = ZonedDateTime.now(city.zone())
        dayNow = runCatching { ZmanimEngine.halfOf(now, city)?.day }.getOrNull() ?: (now.hour in 7..18)
    }

    inline fun <T> dark(block: () -> T): T {
        val prev = renderDark.get()
        renderDark.set(true)
        try { return block() } finally { renderDark.set(prev) }
    }
}

/** Фон экранов: ночью — звёздное небо, днём — светлое небо. */
@Composable
fun ClockBackground() {
    if (ClockTheme.light) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFD7E7F8), Color(0xFFEFF3F8), Color(0xFFF9F2E3)))))
    } else {
        Box(Modifier.fillMaxSize().background(Color(0xFF060B1E))) {
            Image(
                painter = painterResource(R.drawable.clock_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
