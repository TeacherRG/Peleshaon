package app.mychitas.peleshaon.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mychitas.peleshaon.R
import app.mychitas.peleshaon.domain.ZmanimEngine
import app.mychitas.peleshaon.reminder.CalendarFiles
import app.mychitas.peleshaon.reminder.CalendarFormat
import app.mychitas.peleshaon.reminder.ImportResult
import app.mychitas.peleshaon.reminder.Reminder
import app.mychitas.peleshaon.reminder.ReminderStore
import app.mychitas.peleshaon.reminder.Repeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.zip.ZipInputStream

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WEEK = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)

/** Напоминания выбранного дня: список (нажатие — редактировать) и «Добавить». */
@Composable
fun DayReminders(day: LocalDate, todays: List<Reminder>, onEdit: (Reminder) -> Unit, onAdd: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ClockColors.Surface,
        border = BorderStroke(1.dp, ClockColors.Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🔔 " + stringResource(R.string.rem_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ClockColors.Ink)
            if (todays.isEmpty()) Text(stringResource(R.string.rem_none), color = ClockColors.Muted, fontSize = 13.sp)
            for (r in todays.sortedBy { it.time ?: LocalTime.MIN }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onEdit(r) }.padding(vertical = 4.dp)
                ) {
                    Text(r.time?.format(HM) ?: stringResource(R.string.rem_all_day_short),
                        color = ClockColors.TcheletDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title.ifBlank { stringResource(R.string.rem_untitled) }, color = ClockColors.Ink,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (r.note.isNotBlank()) Text(r.note, color = ClockColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (r.repeat != Repeat.NONE) Text("🔁", fontSize = 13.sp)
                    if (r.alertMinutes != null) Text(" 🔔", fontSize = 13.sp)
                    Text("  ✎", color = ClockColors.Muted, fontSize = 15.sp)
                }
            }
            TextButton(onClick = onAdd) {
                Text("＋ " + stringResource(R.string.rem_add), color = ClockColors.TcheletDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Новое напоминание на день [day]: на весь день, уведомление в 9:00 в тот же день. */
fun newReminder(day: LocalDate) = Reminder(
    id = ReminderStore.newId(), title = "", date = day, alertMinutes = Reminder.DEFAULT_ALL_DAY_ALERT
)

private fun repeatLabel(context: Context, r: Repeat, date: LocalDate, isIsrael: Boolean, locale: Locale): String = when (r) {
    Repeat.NONE -> context.getString(R.string.rem_repeat_none)
    Repeat.DAILY -> context.getString(R.string.rem_repeat_daily)
    Repeat.WEEKLY -> context.getString(R.string.rem_repeat_weekly)
    Repeat.MONTHLY -> context.getString(R.string.rem_repeat_monthly)
    Repeat.YEARLY -> context.getString(R.string.rem_repeat_yearly)
    Repeat.HEBREW_MONTHLY -> context.getString(R.string.rem_repeat_hebrew_monthly, ZmanimEngine.jewishDay(date, isIsrael).day)
    Repeat.HEBREW_YEARLY -> context.getString(R.string.rem_repeat_hebrew_yearly, jewishDayMonth(ZmanimEngine.jewishDay(date, isIsrael), locale))
}

private fun alertLabel(context: Context, m: Int?, allDay: Boolean): String = when {
    m == null -> context.getString(R.string.rem_alert_none)
    allDay && m == -540 -> context.getString(R.string.rem_alert_day_9)
    allDay && m == 900 -> context.getString(R.string.rem_alert_eve_9)
    allDay && m == 9540 -> context.getString(R.string.rem_alert_week_9)
    m == 0 -> context.getString(R.string.rem_alert_at_start)
    m == 1440 -> context.getString(R.string.rem_alert_day_before)
    m > 0 && m % 60 == 0 -> context.getString(R.string.rem_alert_hours, m / 60)
    m > 0 -> context.getString(R.string.rem_alert_minutes, m)
    else -> context.getString(R.string.rem_alert_after, -m)
}

/** Редактор напоминания: название, заметка, дата, время или весь день, повтор, уведомление; удалить. */
@Composable
fun ReminderEditor(
    initial: Reminder, isNew: Boolean, isIsrael: Boolean,
    onSave: (Reminder) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    var r by remember(initial.id) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var alertMenu by remember { mutableStateOf(false) }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    fun pickDate(start: LocalDate, set: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, y, m, d -> set(LocalDate.of(y, m + 1, d)) },
            start.year, start.monthValue - 1, start.dayOfMonth).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.rem_new else R.string.rem_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = r.title, onValueChange = { r = r.copy(title = it.take(200)) },
                    label = { Text(stringResource(R.string.rem_field_title)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = r.note, onValueChange = { r = r.copy(note = it.take(2000)) },
                    label = { Text(stringResource(R.string.rem_field_note)) },
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth()
                )
                // дата и еврейская дата
                val jd = ZmanimEngine.jewishDay(r.date, isIsrael)
                OutlinedButton(onClick = { pickDate(r.date) { r = r.copy(date = it) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("📅 " + r.date.format(dateFmt) + " · " + jewishDayMonth(jd, locale) + " " + jd.year,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.rem_all_day), modifier = Modifier.weight(1f), color = ClockColors.Ink)
                    Switch(checked = r.allDay, onCheckedChange = { all ->
                        r = if (all) r.copy(time = null, alertMinutes = r.alertMinutes?.let { Reminder.DEFAULT_ALL_DAY_ALERT })
                            else r.copy(time = LocalTime.of(9, 0), alertMinutes = r.alertMinutes?.let { Reminder.DEFAULT_TIMED_ALERT })
                    })
                }
                r.time?.let { t ->
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> r = r.copy(time = LocalTime.of(h, m)) }, t.hour, t.minute, is24).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("🕘 " + t.format(HM)) }
                }

                // повтор
                Text(stringResource(R.string.rem_repeat), color = ClockColors.Muted, fontSize = 12.sp)
                Box {
                    OutlinedButton(onClick = { repeatMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("🔁 " + repeatLabel(context, r.repeat, r.date, isIsrael, locale), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                        for (rep in Repeat.entries) DropdownMenuItem(
                            text = { Text(repeatLabel(context, rep, r.date, isIsrael, locale)) },
                            onClick = {
                                r = r.copy(repeat = rep, byDay = if (rep == Repeat.WEEKLY) r.byDay else emptySet(),
                                    interval = if (rep == Repeat.NONE) 1 else r.interval,
                                    until = if (rep == Repeat.NONE) null else r.until)
                                repeatMenu = false
                            }
                        )
                    }
                }
                if (r.repeat.hebrew) Text(stringResource(R.string.rem_hebrew_note), color = ClockColors.Muted, fontSize = 12.sp)
                if (r.repeat == Repeat.WEEKLY) {
                    val days = r.byDay.ifEmpty { setOf(r.date.dayOfWeek) }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (d in WEEK) {
                            val on = d in days
                            Box(
                                Modifier.weight(1f).heightIn(min = 36.dp)
                                    .background(if (on) ClockColors.Gold else ClockColors.Surface, CircleShape)
                                    .clickable {
                                        val next = if (on) days - d else days + d
                                        if (next.isNotEmpty()) r = r.copy(byDay = next)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d.getDisplayName(TextStyle.NARROW_STANDALONE, locale), fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, color = if (on) ClockColors.SheetBg else ClockColors.Ink)
                            }
                        }
                    }
                }
                if (r.repeat != Repeat.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.rem_every, r.interval), modifier = Modifier.weight(1f), color = ClockColors.Ink)
                        TextButton(onClick = { r = r.copy(interval = (r.interval - 1).coerceAtLeast(1)) }) { Text("−", fontSize = 20.sp) }
                        TextButton(onClick = { r = r.copy(interval = (r.interval + 1).coerceAtMost(99)) }) { Text("+", fontSize = 20.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDate(r.until ?: r.date.plusMonths(1)) { u -> r = r.copy(until = maxOf(u, r.date)) } },
                            modifier = Modifier.weight(1f)) {
                            Text(r.until?.let { stringResource(R.string.rem_until, it.format(dateFmt)) } ?: stringResource(R.string.rem_forever),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (r.until != null) TextButton(onClick = { r = r.copy(until = null) }) { Text("✕") }
                    }
                }

                // уведомление
                Text(stringResource(R.string.rem_alert), color = ClockColors.Muted, fontSize = 12.sp)
                Box {
                    OutlinedButton(onClick = { alertMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("🔔 " + alertLabel(context, r.alertMinutes, r.allDay), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = alertMenu, onDismissRequest = { alertMenu = false }) {
                        val options = listOf<Int?>(null) + (if (r.allDay) Reminder.ALL_DAY_ALERTS else Reminder.TIMED_ALERTS) +
                            listOfNotNull(r.alertMinutes).filter { it !in Reminder.ALL_DAY_ALERTS && it !in Reminder.TIMED_ALERTS }
                        for (m in options) DropdownMenuItem(
                            text = { Text(alertLabel(context, m, r.allDay)) },
                            onClick = { r = r.copy(alertMinutes = m); alertMenu = false }
                        )
                    }
                }
                if (r.alertMinutes != null) Text(stringResource(R.string.rem_shabbat_note), color = ClockColors.Muted, fontSize = 12.sp)
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("🗑 " + stringResource(R.string.rem_delete), color = ClockColors.Bad)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(r.copy(title = r.title.trim(), note = r.note.trim())) }) { Text(stringResource(R.string.rem_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rem_cancel)) } }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.rem_delete_q)) },
            text = { Text(r.title.ifBlank { stringResource(R.string.rem_untitled) } +
                if (r.repeat != Repeat.NONE) "\n\n" + stringResource(R.string.rem_delete_series) else "") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.rem_delete), color = ClockColors.Bad) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.rem_cancel)) } }
        )
    }
}

/** Запрос разрешения на уведомления (Android 13+) — при первом напоминании с уведомлением. */
@Composable
fun rememberNotificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Импорт (.ics, .vcs, ZIP из Google Календаря) и экспорт (.ics, .vcs) — файлом, без сети. */
@Composable
fun ReminderFiles(reminders: List<Reminder>, onImported: (List<Reminder>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMenu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun export(uri: Uri?, format: CalendarFormat) {
        if (uri == null) return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = CalendarFiles.export(ReminderStore.load(context), ReminderStore.zone(context), format,
                        calendarName = context.getString(R.string.app_name))
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) R.string.rem_export_done else R.string.rem_file_error, Toast.LENGTH_LONG).show()
        }
    }
    val exportIcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CalendarFormat.ICS.mime)) { export(it, CalendarFormat.ICS) }
    val exportVcs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CalendarFormat.VCS.mime)) { export(it, CalendarFormat.VCS) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { readLimited(it) }
                    val parsed = readCalendarBytes(bytes, context)
                    val (added, updated) = ReminderStore.merge(context, parsed.reminders)
                    Triple(parsed, added, updated)
                }.getOrNull()
            }
            busy = false
            if (result == null) {
                report = context.getString(R.string.rem_file_error)
            } else {
                val (parsed, added, updated) = result
                report = buildString {
                    append(context.getString(R.string.rem_import_done, added, updated))
                    if (parsed.skippedPast > 0) append("\n").append(context.getString(R.string.rem_import_past, parsed.skippedPast))
                    if (parsed.simplified > 0) append("\n").append(context.getString(R.string.rem_import_simplified, parsed.simplified))
                }
                onImported(ReminderStore.load(context))
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { importer.launch(arrayOf("text/calendar", "text/x-vcalendar", "application/zip",
            "application/octet-stream", "application/ics", "text/plain")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text("⤓ " + stringResource(R.string.rem_import), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = {
                if (reminders.isEmpty()) Toast.makeText(context, R.string.rem_export_empty, Toast.LENGTH_SHORT).show()
                else exportMenu = true
            }, modifier = Modifier.fillMaxWidth()) {
                Text("⤒ " + stringResource(R.string.rem_export), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rem_export_ics)) },
                    onClick = { exportMenu = false; exportIcs.launch(EXPORT_NAME + ".ics") })
                DropdownMenuItem(text = { Text(stringResource(R.string.rem_export_vcs)) },
                    onClick = { exportMenu = false; exportVcs.launch(EXPORT_NAME + ".vcs") })
            }
        }
    }
    Text(stringResource(R.string.rem_files_note), color = ClockColors.Muted, fontSize = 12.sp)

    report?.let { msg ->
        AlertDialog(
            onDismissRequest = { report = null },
            title = { Text(stringResource(R.string.rem_import)) },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { report = null }) { Text("OK") } }
        )
    }
}

private const val EXPORT_NAME = "pele-shaon-reminders"
private const val MAX_FILE = 25 * 1024 * 1024

/** Текст календаря или ZIP (выгрузка Google Календаря — по файлу .ics на каждый календарь). */
private fun readCalendarBytes(bytes: ByteArray, context: Context): ImportResult {
    val zone = ReminderStore.zone(context)
    val today = LocalDate.now(zone)
    val texts = mutableListOf<String>()
    if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.lowercase()
                if (!e.isDirectory && (n.endsWith(".ics") || n.endsWith(".vcs") || n.endsWith(".ical")))
                    texts += decode(readLimited(zip))
            }
        }
    } else texts += decode(bytes)
    val parts = texts.map { CalendarFiles.parse(it, zone, today) }
    if (parts.isEmpty() || texts.none { it.contains("BEGIN:VCALENDAR", ignoreCase = true) }) error("not a calendar file")
    return ImportResult(parts.flatMap { it.reminders }, parts.sumOf { it.skippedPast }, parts.sumOf { it.simplified })
}

/** Не больше [MAX_FILE] байт: InputStream.readNBytes есть только с Android 13. */
private fun readLimited(input: java.io.InputStream): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        if (out.size() > MAX_FILE) error("file too large")
    }
    return out.toByteArray()
}

private fun decode(b: ByteArray): String = String(b, Charsets.UTF_8).removePrefix("﻿")
