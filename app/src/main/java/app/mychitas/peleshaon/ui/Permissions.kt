package app.mychitas.peleshaon.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.alarm.AlarmScheduler

/** Что мешает будильнику: точное время, окно на весь экран, уведомления. */
private data class PermissionState(val exact: Boolean, val fullScreen: Boolean, val notifications: Boolean)

private fun readPermissions(context: Context): PermissionState {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm.canUseFullScreenIntent()
    return PermissionState(exact, fullScreen, nm.areNotificationsEnabled())
}

private fun openSettings(context: Context, action: String) {
    val pkg = context.packageName
    val intent = when (action) {
        Settings.ACTION_APP_NOTIFICATION_SETTINGS -> Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        else -> Intent(action, Uri.parse("package:$pkg"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Видна, только когда будильнику чего-то не хватает; после возврата из настроек обновляется сама. */
@Composable
fun PermissionsCard() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readPermissions(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val fresh = readPermissions(context)
                if (fresh.exact && !state.exact) AlarmScheduler.rescheduleAllFromPrefs(context)
                state = fresh
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (state.exact && state.fullScreen && state.notifications) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0x29D6AA50),
        border = BorderStroke(1.dp, Color(0x80D6AA50)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.perm_title), color = ClockColors.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(stringResource(R.string.perm_note), color = ClockColors.Ink2, fontSize = 13.sp)
            PermissionRow(R.string.perm_exact, state.exact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) openSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            }
            PermissionRow(R.string.perm_fullscreen, state.fullScreen) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) openSettings(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            }
            PermissionRow(R.string.perm_notif, state.notifications) {
                openSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }
        }
    }
}

@Composable
private fun PermissionRow(label: Int, granted: Boolean, onFix: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), color = ClockColors.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (granted) {
            Text("✓ " + stringResource(R.string.perm_ok), color = ClockColors.Ok, fontSize = 13.sp)
        } else {
            Button(onClick = onFix, colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold)) {
                Text(stringResource(R.string.perm_fix), color = ClockColors.SheetBg)
            }
        }
    }
}
