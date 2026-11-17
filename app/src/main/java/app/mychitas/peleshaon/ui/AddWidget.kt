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
