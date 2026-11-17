package app.mychitas.peleshaon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.live.PinIcon
import app.mychitas.peleshaon.live.PinWidget

/** Кнопки «Поставить на главный экран»: циферблат и строка. Рабочий стол не умеет — подсказка, как вручную. */
@Composable
fun AddWidgetButtons() {
    val context = LocalContext.current
    val can = remember { PinWidget.supported(context) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (can) {
            Button(
                onClick = { PinWidget.request(context) },
                colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold),
                modifier = Modifier.fillMaxWidth()
            ) { Text("🕰  " + stringResource(R.string.widget_add_face), color = ClockColors.SheetBg, fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = { PinWidget.request(context, date = true) },
                border = BorderStroke(1.dp, ClockColors.Line),
                modifier = Modifier.fillMaxWidth()
            ) { Text("📅  " + stringResource(R.string.widget_add_date), color = ClockColors.Ink) }
        } else {
            Text(stringResource(R.string.widget_add_manual), color = ClockColors.Muted, fontSize = 14.sp)
        }
    }
}

/** Кнопка «Поставить значок приложения на главный экран». Рабочий стол не умеет — подсказка, как вручную. */
@Composable
fun AddIconButton() {
    val context = LocalContext.current
    val can = remember { PinIcon.supported(context) }
    if (can) {
        Button(
            onClick = { PinIcon.request(context) },
            colors = ButtonDefaults.buttonColors(containerColor = ClockColors.Gold),
            modifier = Modifier.fillMaxWidth()
        ) { Text("📲  " + stringResource(R.string.icon_add), color = ClockColors.SheetBg, fontWeight = FontWeight.Bold) }
    } else {
        Text(stringResource(R.string.icon_add_manual), color = ClockColors.Muted, fontSize = 14.sp)
    }
}

/**
 * «Значок или виджеты не появились?» — что видит телефон (виджетов в системе, рабочий стол, модель) и как разрешить
 * рабочему столу ставить значки: на OPPO / realme / OnePlus, Xiaomi, vivo, Huawei приложению не из Google Play
 * разрешение «Ярлыки на рабочем столе» по умолчанию закрыто — запросы молча отклоняются (владелец, 18.11.2026).
 */
@Composable
fun HomeScreenHelp() {
    val context = LocalContext.current
    val brand = remember { android.os.Build.MANUFACTURER.lowercase() }
    val providers = remember { runCatching { app.mychitas.peleshaon.live.PinDone.providers(context) }.getOrDefault(-1) }
    val launcher = remember { runCatching { app.mychitas.peleshaon.live.PinDone.launcher(context) }.getOrDefault("?") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_t), fontWeight = FontWeight.Bold, color = ClockColors.Ink)
        val steps = when {
            brand in setOf("oppo", "realme", "oneplus") -> R.string.home_oppo
            brand in setOf("xiaomi", "redmi", "poco") -> R.string.home_xiaomi
            brand in setOf("vivo", "iqoo") -> R.string.home_vivo
            brand in setOf("huawei", "honor") -> R.string.home_huawei
            else -> R.string.home_other
        }
        Text(stringResource(steps), color = ClockColors.Ink2, fontSize = 14.sp)
        OutlinedButton(
            onClick = {
                // у Xiaomi — сразу экран разрешений; у остальных — настройки приложения
                val pkg = context.packageName
                val miui = android.content.Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg)
                val details = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
                val ok = brand in setOf("xiaomi", "redmi", "poco") && runCatching { context.startActivity(miui.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!ok) runCatching { context.startActivity(details.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            border = BorderStroke(1.dp, ClockColors.Gold)
        ) { Text(stringResource(R.string.home_open), color = ClockColors.Ink) }
        if (providers == 0) Text(stringResource(R.string.home_zero), color = ClockColors.Bad, fontSize = 13.sp)
        Text(
            stringResource(R.string.home_status, providers, launcher, android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL, android.os.Build.VERSION.RELEASE),
            color = ClockColors.Muted, fontSize = 12.sp
        )
    }
}
