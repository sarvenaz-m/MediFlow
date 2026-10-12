@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.mediflow.routine

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import dev.mediflow.designsystem.*
import dev.mediflow.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlatformActions(
    val notifications: () -> Unit,
    val exactAlarms: () -> Unit,
    val export: () -> Unit,
    val restore: () -> Unit,
)

private enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.WbSunny),
    CABINET("Cabinet", Icons.Outlined.Medication),
    INSIGHTS("Insights", Icons.Outlined.Insights),
    SETTINGS("Settings", Icons.Outlined.Tune),
}

@Composable
fun RoutineApp(actions: PlatformActions, viewModel: RoutineViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var editor by rememberSaveable { mutableStateOf<Medication?>(null) }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var refill by rememberSaveable { mutableStateOf<Medication?>(null) }
    var archive by rememberSaveable { mutableStateOf<Medication?>(null) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage(it)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!wide && state.preferences.onboarded)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        Destination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = tab == destination,
                                onClick = { tab = destination },
                                icon = { Icon(destination.icon, null) },
                                label = { Text(destination.label) },
                                modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                            )
                        }
                    }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide && state.preferences.onboarded) {
                    Column(
                        Modifier.width(224.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Brand()
                        Spacer(Modifier.height(36.dp))
                        Destination.entries.forEach { destination ->
                            NavigationDrawerItem(
                                label = { Text(destination.label) },
                                icon = { Icon(destination.icon, null) },
                                selected = tab == destination,
                                onClick = { tab = destination },
                                modifier = Modifier.testTag("nav_${destination.name.lowercase()}"),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        RoutineFlower(Modifier.size(120.dp).align(Alignment.CenterHorizontally))
                        Text(
                            "Small steps.\nA calmer routine.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        StatusPill("Stored on your device")
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    when {
                        state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        !state.preferences.onboarded ->
                            WelcomeScreen(
                                busy,
                                { viewModel.begin(true) },
                                { viewModel.begin(false) },
                            )
                        else ->
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "destination",
                            ) { destination ->
                                when (destination) {
                                    Destination.TODAY ->
                                        TodayScreen(
                                            state,
                                            busy,
                                            { calendar = true },
                                            { tab = Destination.SETTINGS },
                                            {
                                                editor = null
                                                editorVisible = true
                                            },
                                            viewModel::selectDate,
                                            viewModel::record,
                                            viewModel::undo,
                                            viewModel::snooze,
                                        )
                                    Destination.CABINET ->
                                        CabinetScreen(
                                            state,
                                            {
                                                editor = null
                                                editorVisible = true
                                            },
                                            {
                                                editor = it
                                                editorVisible = true
                                            },
                                            { refill = it },
                                            { archive = it },
                                        )
                                    Destination.INSIGHTS -> InsightsScreen(state, viewModel::undo)
                                    Destination.SETTINGS ->
                                        SettingsScreen(
                                            state,
                                            actions,
                                            viewModel::reminders,
                                            viewModel::discreet,
                                            viewModel::theme,
                                        )
                                }
                            }
                    }
                }
            }
        }
    }
    if (editorVisible)
        MedicationEditor(editor, busy, { editorVisible = false }) { medicine ->
            viewModel.save(medicine) { editorVisible = false }
        }
    refill?.let { medicine ->
        RefillDialog(medicine, busy, { refill = null }) { amount ->
            viewModel.refill(medicine.id, amount) { refill = null }
        }
    }
    archive?.let { medicine ->
        AlertDialog(
            onDismissRequest = { archive = null },
            title = { Text("Archive ${medicine.name}?") },
            text = {
                Text("Upcoming reminders will stop. Your recorded history will remain available.")
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { viewModel.archive(medicine.id) { archive = null } },
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = { archive = null }) { Text("Keep medication") }
            },
        )
    }
    if (calendar) {
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    state.selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let {
                            viewModel.selectDate(
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        calendar = false
                    }
                ) {
                    Text("View day")
                }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancel") } },
        ) {
            DatePicker(picker)
        }
    }
}

@Composable
internal fun Brand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MediMark()
        Text("mediflow", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WelcomeScreen(busy: Boolean, sample: () -> Unit, start: () -> Unit) {
    Column(
        Modifier.widthIn(max = 540.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Brand()
        Spacer(Modifier.height(34.dp))
        RoutineFlower(Modifier.size(190.dp))
        Spacer(Modifier.height(26.dp))
        Text(
            "A little routine.\nA little more calm.",
            style = MaterialTheme.typography.displaySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Make space for your daily care. Keep your schedule, reminders and medication cabinet together.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        StatusPill("Private by design · No account needed")
        Spacer(Modifier.height(30.dp))
        Button(
            onClick = start,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start_routine"),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Start my routine")
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
        }
        TextButton(
            onClick = sample,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("explore_sample"),
        ) {
            Text("Explore a sample routine")
        }
        Text(
            "Sample entries are fictional and can be explored without enabling reminders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun TodayScreen(
    state: RoutineUiState,
    busy: Boolean,
    calendar: () -> Unit,
    settings: () -> Unit,
    add: () -> Unit,
    select: (LocalDate) -> Unit,
    record: (String, DoseStatus) -> Unit,
    undo: (String) -> Unit,
    snooze: (String) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val doses =
        remember(state.snapshot, state.selectedDate, zone) {
            ScheduleEngine.day(state.snapshot, state.selectedDate, zone)
        }
    val records = state.snapshot.records.associateBy { it.key }
    val taken = doses.count { records[it.key]?.status == DoseStatus.TAKEN }
    val today = state.now.atZone(zone).toLocalDate()
    LazyColumn(
        Modifier.widthIn(max = 900.dp).fillMaxSize().testTag("today_list"),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.selectedDate
                            .format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
                            .uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.selectedDate == today) "Your day, in balance."
                        else "Your daily routine.",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                IconButton(onClick = settings) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        "Reminder settings",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            SoftCard(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ONE STEP AT A TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Small moments.\nSteady care.",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "$taken of ${doses.size} scheduled entries recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    RoutineFlower(Modifier.size(100.dp))
                }
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { if (doses.isEmpty()) 0f else taken.toFloat() / doses.size },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .65f),
                )
                if (state.preferences.sampleData) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SAMPLE ROUTINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item {
            val monday =
                state.selectedDate.minusDays((state.selectedDate.dayOfWeek.value - 1).toLong())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(7) { offset ->
                    val date = monday.plusDays(offset.toLong())
                    val selected = date == state.selectedDate
                    Surface(
                        onClick = { select(date) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color =
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                date
                                    .format(DateTimeFormatter.ofPattern("EE", Locale.ENGLISH))
                                    .take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                    if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionLabel("Your schedule", zone.id.replace('_', ' ')) {
                IconButton(onClick = calendar) {
                    Icon(Icons.Outlined.CalendarMonth, "Choose a day")
                }
            }
        }
        if (doses.isEmpty())
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(
                        "A little room for your routine",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No medication is scheduled for this day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = add) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add medication")
                    }
                }
            }
        items(doses, key = { it.key }) { dose ->
            DoseCard(dose, records[dose.key], state.now, busy, record, undo, snooze)
        }
        item {
            OutlinedButton(
                onClick = add,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add_medication"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add medication")
            }
        }
        if (!state.preferences.reminders || !state.capabilities.notifications)
            item {
                TextButton(onClick = settings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.NotificationsOff, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Set up your reminders", style = MaterialTheme.typography.labelMedium)
                }
            }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun DoseCard(
    dose: DoseOccurrence,
    record: DoseRecord?,
    now: Instant,
    busy: Boolean,
    recordAction: (String, DoseStatus) -> Unit,
    undo: (String) -> Unit,
    snooze: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val futureDay =
        dose.scheduledAt.atZone(zone).toLocalDate().isAfter(now.atZone(zone).toLocalDate())
    SoftCard(Modifier.fillMaxWidth().testTag("dose_${dose.medication.id}")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MedicineGlyph(dose.medication.color)
            Column(Modifier.weight(1f)) {
                Text(dose.medication.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Quantity.format(dose.medication.quantityMilli)} ${dose.medication.form.unit} · ${dose.medication.strength}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                dose.scheduledAt.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (record != null) {
                StatusPill(
                    if (record.status == DoseStatus.TAKEN) "Taken" else "Skipped",
                    if (record.status == DoseStatus.TAKEN) MediColors.Sage else MediColors.Muted,
                    if (record.status == DoseStatus.TAKEN) MediColors.SageSoft
                    else MediColors.Lavender,
                )
                Spacer(Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = { undo(dose.key) }) { Text("Undo") }
            } else {
                Text(
                    if (futureDay) "Upcoming"
                    else if (dose.scheduledAt.isAfter(now)) "Scheduled" else "Ready to record",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!futureDay) {
                    FilledTonalButton(
                        enabled = !busy,
                        onClick = { recordAction(dose.key, DoseStatus.TAKEN) },
                        modifier = Modifier.testTag("take_${dose.medication.id}"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mark taken")
                    }
                    Box {
                        IconButton(enabled = !busy, onClick = { menu = true }) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                "More actions for ${dose.medication.name}",
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Remind me in 10 minutes") },
                                onClick = {
                                    menu = false
                                    snooze(dose.key)
                                },
                                leadingIcon = { Icon(Icons.Outlined.Snooze, null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Skip this entry") },
                                onClick = {
                                    menu = false
                                    recordAction(dose.key, DoseStatus.SKIPPED)
                                },
                                leadingIcon = { Icon(Icons.Outlined.SkipNext, null) },
                            )
                        }
                    }
                }
            }
        }
    }
}
