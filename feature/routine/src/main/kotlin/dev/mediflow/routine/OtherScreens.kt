package dev.mediflow.routine

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.mediflow.designsystem.*
import dev.mediflow.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CabinetScreen(
    state: RoutineUiState,
    add: () -> Unit,
    edit: (Medication) -> Unit,
    refill: (Medication) -> Unit,
    archive: (Medication) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val medicines =
        state.snapshot.medications.filter {
            !it.archived && (it.name.contains(query, true) || it.strength.contains(query, true))
        }
    LazyColumn(
        Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("cabinet_list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SectionLabel("Your cabinet", "Everything you need, in one place.") {
                FilledIconButton(onClick = add, modifier = Modifier.testTag("cabinet_add")) {
                    Icon(Icons.Outlined.Add, "Add medication")
                }
            }
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                modifier = Modifier.fillMaxWidth().testTag("search_medicines"),
                placeholder = { Text("Find a medication") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
        }
        if (medicines.isEmpty())
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (query.isBlank()) "Your cabinet starts here"
                        else "No matching medications",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (query.isBlank())
                            "Add the name, schedule and stock from your own routine."
                        else "Try a different name.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (query.isBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = add) { Text("Add medication") }
                    }
                }
            }
        items(medicines, key = { it.id }) { medicine ->
            var menu by remember { mutableStateOf(false) }
            val low = medicine.stockMilli <= medicine.lowStockMilli
            SoftCard(Modifier.fillMaxWidth().testTag("medicine_${medicine.id}")) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MedicineGlyph(medicine.color, Modifier.size(60.dp))
                    Column(Modifier.weight(1f)) {
                        Text(medicine.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(medicine.strength, medicine.form.label)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreHoriz, "Options for ${medicine.name}")
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit medication") },
                                onClick = {
                                    menu = false
                                    edit(medicine)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Archive medication") },
                                onClick = {
                                    menu = false
                                    archive(medicine)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${Quantity.format(medicine.stockMilli)} ${medicine.form.unit}${if(medicine.form==MedicineForm.LIQUID)"" else "s"} left",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (low) "Time to review your stock" else "Stock looks comfortable",
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (low) MediColors.Peach
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { refill(medicine) }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refill")
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        medicine.schedule.times.sorted().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (low) StatusPill("Low stock", MediColors.Peach, MediColors.PeachSoft)
                }
            }
        }
        item {
            Text(
                "Stock changes when you record or undo an entry. Skipped entries leave it unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun InsightsScreen(state: RoutineUiState, undo: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = state.now.atZone(zone).toLocalDate()
    val days = (6L downTo 0L).map { today.minusDays(it) }
    val week = state.snapshot.records.filter { it.scheduledAt.atZone(zone).toLocalDate() in days }
    val taken = week.count { it.status == DoseStatus.TAKEN }
    val skipped = week.count { it.status == DoseStatus.SKIPPED }
    LazyColumn(
        Modifier.widthIn(max = 900.dp).fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { SectionLabel("A little perspective", "Your recorded routine, at a glance.") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SoftCard(Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer) {
                    Text("$taken", style = MaterialTheme.typography.displaySmall)
                    Text("Recorded as taken", style = MaterialTheme.typography.bodySmall)
                }
                SoftCard(Modifier.weight(1f)) {
                    Text("$skipped", style = MaterialTheme.typography.displaySmall)
                    Text("Recorded as skipped", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                SectionLabel("Your week", "Entries over the last seven days")
                Spacer(Modifier.height(24.dp))
                val counts = days.map { day ->
                    week.count {
                        it.scheduledAt.atZone(zone).toLocalDate() == day &&
                            it.status == DoseStatus.TAKEN
                    }
                }
                val skippedCounts = days.map { day ->
                    week.count {
                        it.scheduledAt.atZone(zone).toLocalDate() == day &&
                            it.status == DoseStatus.SKIPPED
                    }
                }
                val max =
                    counts.zip(skippedCounts).maxOfOrNull { it.first + it.second }?.coerceAtLeast(1)
                        ?: 1
                val accent = MaterialTheme.colorScheme.primary
                val soft = MaterialTheme.colorScheme.primaryContainer
                val muted = MaterialTheme.colorScheme.outlineVariant
                Canvas(
                    Modifier.fillMaxWidth().height(150.dp).semantics {
                        contentDescription =
                            days.indices.joinToString(". ") {
                                "${days[it]}: ${counts[it]} taken, ${skippedCounts[it]} skipped"
                            }
                    }
                ) {
                    val gap = size.width / 7
                    val bar = gap * .46f
                    repeat(4) { n ->
                        val y = size.height * n / 3
                        drawLine(
                            muted.copy(alpha = .45f),
                            Offset(0f, y),
                            Offset(size.width, y),
                            1.dp.toPx(),
                        )
                    }
                    counts.forEachIndexed { index, count ->
                        val x = gap * index + (gap - bar) / 2
                        val takenHeight = (size.height - 8.dp.toPx()) * count / max
                        val skippedHeight = (size.height - 8.dp.toPx()) * skippedCounts[index] / max
                        if (count + skippedCounts[index] > 0)
                            drawRoundRect(
                                soft,
                                Offset(x, size.height - takenHeight - skippedHeight),
                                Size(bar, takenHeight + skippedHeight),
                                CornerRadius(8.dp.toPx()),
                            )
                        if (count > 0)
                            drawRoundRect(
                                accent,
                                Offset(x, size.height - takenHeight),
                                Size(bar, takenHeight),
                                CornerRadius(8.dp.toPx()),
                            )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    days.forEach {
                        Text(
                            it.format(DateTimeFormatter.ofPattern("EE", Locale.ENGLISH)),
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "● Taken   ◌ Skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.preferences.sampleData) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Includes fictional sample history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SectionLabel("Recent entries", "An accurate record of what you logged") }
        if (state.snapshot.records.isEmpty())
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Your history will appear here",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Record an entry from Today to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        items(
            state.snapshot.records.sortedByDescending { it.recordedAt }.take(60),
            key = { it.key },
        ) { record ->
            SoftCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.medicationName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            record.scheduledAt
                                .atZone(zone)
                                .format(
                                    DateTimeFormatter.ofPattern("MMM d · HH:mm", Locale.ENGLISH)
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(if (record.status == DoseStatus.TAKEN) "Taken" else "Skipped")
                    IconButton(onClick = { undo(record.key) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            "Undo entry for ${record.medicationName}",
                            Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    state: RoutineUiState,
    actions: PlatformActions,
    reminders: (Boolean) -> Unit,
    discreet: (Boolean) -> Unit,
    theme: (ThemeMode) -> Unit,
) {
    val prefs = state.preferences
    LazyColumn(
        Modifier.widthIn(max = 900.dp).fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { SectionLabel("Make it yours", "A routine that fits your day.") }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    "Routine reminders",
                    "Receive notifications for the times you enter.",
                    prefs.reminders,
                ) { enabled ->
                    reminders(enabled)
                    if (enabled && !state.capabilities.notifications) actions.notifications()
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    if (state.capabilities.notifications) "Notifications are available"
                    else "Notifications need your permission",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!state.capabilities.notifications)
                    TextButton(onClick = actions.notifications) { Text("Enable notifications") }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (state.capabilities.exactAlarms) "Precise timing is enabled"
                    else "Timing may be approximate",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Android permissions and battery settings affect when reminders arrive. Force-stopping the app pauses them until you reopen it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.capabilities.exactAlarms)
                    TextButton(onClick = actions.exactAlarms) { Text("Allow precise reminders") }
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    "Discreet notifications",
                    "Hide medication names in notification text.",
                    prefs.discreetNotifications,
                    discreet,
                )
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.theme == mode,
                            onClick = { theme(mode) },
                            label = {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            },
                        )
                    }
                }
            }
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Text("Your data stays with you", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    "No account, advertising or cloud sync. Your routine is stored in this app on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = actions.export, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export a backup")
                }
                TextButton(onClick = actions.restore, modifier = Modifier.fillMaxWidth()) {
                    Text("Restore a backup")
                }
                Text(
                    "Exported files contain medication history and are not password protected. Keep them somewhere private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Brand()
                Spacer(Modifier.height(8.dp))
                Text(
                    "Version 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Organizes the schedules you enter. Does not prescribe treatment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
