package dev.mediflow.routine

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import dev.mediflow.designsystem.*
import dev.mediflow.model.*
import java.time.*
import java.util.UUID

@Composable
internal fun MedicationEditor(
    original: Medication?,
    busy: Boolean,
    dismiss: () -> Unit,
    save: (Medication) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var strength by rememberSaveable { mutableStateOf(original?.strength ?: "") }
    var quantity by rememberSaveable {
        mutableStateOf(original?.quantityMilli?.let(Quantity::format) ?: "1")
    }
    var stock by rememberSaveable {
        mutableStateOf(original?.stockMilli?.let(Quantity::format) ?: "30")
    }
    var threshold by rememberSaveable {
        mutableStateOf(original?.lowStockMilli?.let(Quantity::format) ?: "5")
    }
    var form by rememberSaveable { mutableStateOf(original?.form ?: MedicineForm.TABLET) }
    var times by rememberSaveable {
        mutableStateOf(original?.schedule?.times?.joinToString(", ") ?: "08:00")
    }
    var days by rememberSaveable {
        mutableStateOf(original?.schedule?.weekdays ?: DayOfWeek.entries.toSet())
    }
    var start by rememberSaveable {
        mutableStateOf(original?.schedule?.startDate?.toString() ?: LocalDate.now().toString())
    }
    var end by rememberSaveable { mutableStateOf(original?.schedule?.endDate?.toString() ?: "") }
    var home by rememberSaveable {
        mutableStateOf(original?.schedule?.timePolicy == TimePolicy.HOME_ZONE)
    }
    var zone by rememberSaveable {
        mutableStateOf(original?.schedule?.homeZone ?: ZoneId.systemDefault().id)
    }
    var notes by rememberSaveable { mutableStateOf(original?.notes ?: "") }
    var color by rememberSaveable { mutableIntStateOf(original?.color ?: 0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!busy) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.padding(16.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .fillMaxHeight(.92f)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (original == null) "Add medication" else "Edit medication",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = dismiss, enabled = !busy) {
                        Icon(Icons.Outlined.Close, "Close editor")
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Use the details and schedule from your own care plan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EditorField("Medication name", name, { name = it }, "medicine_name")
                    EditorField(
                        "Strength or label (optional)",
                        strength,
                        { strength = it },
                        "medicine_strength",
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MedicineForm.entries.forEach { entry ->
                            FilterChip(
                                selected = form == entry,
                                onClick = { form = entry },
                                label = { Text(entry.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EditorField(
                            "Units per entry",
                            quantity,
                            { quantity = it },
                            "medicine_quantity",
                            Modifier.weight(1f),
                            true,
                        )
                        EditorField(
                            "Current stock",
                            stock,
                            { stock = it },
                            "medicine_stock",
                            Modifier.weight(1f),
                            true,
                        )
                    }
                    EditorField("Reminder times", times, { times = it }, "medicine_times")
                    Text(
                        "24-hour format, e.g. 08:00, 20:00. Up to 12 times.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Days of the week", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in days,
                                onClick = { days = if (day in days) days - day else days + day },
                                label = { Text(day.name.take(2)) },
                            )
                        }
                    }
                    Text("Color", style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(4) { index ->
                            FilterChip(
                                selected = color == index,
                                onClick = { color = index },
                                label = { Text(listOf("Lilac", "Sage", "Peach", "Blue")[index]) },
                            )
                        }
                    }
                    TextButton(onClick = { details = !details }) {
                        Text(if (details) "Fewer options" else "Dates, travel and notes")
                    }
                    if (details) {
                        EditorField(
                            "Start date (YYYY-MM-DD)",
                            start,
                            { start = it },
                            "medicine_start",
                        )
                        EditorField("End date (optional)", end, { end = it }, "medicine_end")
                        EditorField(
                            "Low stock threshold",
                            threshold,
                            { threshold = it },
                            "medicine_threshold",
                            decimal = true,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Keep home time when travelling",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "Otherwise, times follow your device's local clock.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(home, { home = it })
                        }
                        if (home)
                            EditorField("Home time zone", zone, { zone = it }, "medicine_zone")
                        EditorField(
                            "Personal notes",
                            notes,
                            { notes = it },
                            "medicine_notes",
                            singleLine = false,
                        )
                    }
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("editor_error"),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !busy,
                    modifier =
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_medication"),
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        try {
                            require(name.trim().isNotEmpty()) { "Enter a medication name." }
                            require(days.isNotEmpty()) { "Choose at least one day." }
                            val dose = Quantity.parse(quantity.trim())
                            require(dose > 0) { "Units per entry must be greater than zero." }
                            val parsedTimes = times.split(',').map { LocalTime.parse(it.trim()) }
                            require(parsedTimes.distinct().size == parsedTimes.size) {
                                "Each reminder time must be different."
                            }
                            val schedule =
                                DoseSchedule(
                                    parsedTimes,
                                    days,
                                    LocalDate.parse(start.trim()),
                                    end.trim().takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                                    if (home) TimePolicy.HOME_ZONE else TimePolicy.LOCAL_CLOCK,
                                    zone.trim(),
                                )
                            save(
                                Medication(
                                    original?.id ?: UUID.randomUUID().toString(),
                                    name.trim(),
                                    strength.trim(),
                                    form,
                                    dose,
                                    Quantity.parse(stock.trim()),
                                    Quantity.parse(threshold.trim()),
                                    schedule,
                                    notes.trim(),
                                    color,
                                    revision = original?.revision ?: 0,
                                )
                            )
                        } catch (e: Exception) {
                            error =
                                if (e is java.time.DateTimeException)
                                    "Check the date, time and time zone formats."
                                else e.message ?: "Please check the medication details."
                        }
                    },
                ) {
                    Text(if (busy) "Saving…" else "Save medication")
                }
            }
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    change: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(14.dp),
        singleLine = singleLine,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text
            ),
    )
}

@Composable
internal fun RefillDialog(
    medicine: Medication,
    busy: Boolean,
    dismiss: () -> Unit,
    refill: (String) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Refill ${medicine.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Add the quantity you received to your current stock.")
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Quantity added") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { refill(amount) }, enabled = !busy) { Text("Add to stock") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
