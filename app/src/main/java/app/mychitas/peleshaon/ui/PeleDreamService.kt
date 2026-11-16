package app.mychitas.peleshaon.ui

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.mychitas.peleshaon.domain.ZmanimEngine
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class PeleDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PeleDreamService)
            setViewTreeSavedStateRegistryOwner(this@PeleDreamService)
            setContent {
                var now by remember { mutableStateOf(ZonedDateTime.now()) }
                var burnInOffsetX by remember { mutableStateOf(0.dp) }
                var burnInOffsetY by remember { mutableStateOf(0.dp) }

                LaunchedEffect(Unit) {
                    while (true) {
                        now = ZonedDateTime.now()
                        if (now.second == 0) {
                            burnInOffsetX = ((-6..6).random()).dp
                            burnInOffsetY = ((-6..6).random()).dp
                        }
                        delay(1000L)
                    }
                }

                val prefs = getSharedPreferences("pele_prefs", MODE_PRIVATE)
                val city = prefs.getString("city_name", "Vienna") ?: "Vienna"
                val lat = prefs.getFloat("lat", 48.2082f).toDouble()
                val lon = prefs.getFloat("lon", 16.3738f).toDouble()
                val zoneId = ZoneId.of(prefs.getString("zone_id", "Europe/Vienna"))
                val isIsrael = prefs.getBoolean("is_israel", false)

                val dayData = remember(now.toLocalDate(), now.hour) {
                    ZmanimEngine.calculateForMoment(now.withZoneSameInstant(zoneId), city, lat, lon, 0.0, zoneId, isIsrael)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.offset(x = burnInOffsetX, y = burnInOffsetY),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnalogZmanimClock(
                            now = now.withZoneSameInstant(zoneId),
                            pinnedZmanim = dayData.items.filter { it.type.defaultPinned },
                            zmanLabels = emptyMap(),
                            isDarkTheme = true,
                            is24Hour = true,
                            modifier = Modifier.size(280.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = now.withZoneSameInstant(zoneId).format(DateTimeFormatter.ofPattern("HH:mm")),
                            color = GoldAccent,
                            fontSize = 36.sp
                        )
                        Text(
                            text = dayData.jewishDateHebrew,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
        setContentView(composeView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDetachedToWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDetachedToWindow()
    }
}
