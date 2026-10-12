package dev.mediflow.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mediflow.model.*
import java.time.*
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class RoutineUiState(
    val loading: Boolean = true,
    val snapshot: RoutineSnapshot = RoutineSnapshot(),
    val preferences: AppPreferences = AppPreferences(),
    val selectedDate: LocalDate = LocalDate.now(),
    val now: Instant = Instant.now(),
    val capabilities: ReminderCapabilities = ReminderCapabilities(false, false),
)

@HiltViewModel
class RoutineViewModel
@Inject
constructor(
    private val repository: RoutineRepository,
    private val preferences: PreferenceRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    private val date = MutableStateFlow<LocalDate?>(null)
    private val capabilities = MutableStateFlow(scheduler.capabilities())
    private val time = flow {
        while (true) {
            emit(Instant.now())
            delay(30_000)
        }
    }
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    private var operations = 0
    val state =
        combine(repository.observe(), preferences.preferences, date, time, capabilities) {
                snapshot,
                prefs,
                selected,
                now,
                permissions ->
                RoutineUiState(
                    false,
                    snapshot,
                    prefs,
                    selected ?: now.atZone(ZoneId.systemDefault()).toLocalDate(),
                    now,
                    permissions,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineUiState())

    fun selectDate(value: LocalDate) {
        date.value = value
    }

    fun showMessage(value: String) {
        message.value = value
    }

    fun clearMessage(value: String) {
        if (message.value == value) message.value = null
    }

    private fun launchOperation(block: suspend () -> Unit) = viewModelScope.launch {
        operations++
        busy.value = true
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message.value = e.message ?: "Something went wrong. Please try again."
        } finally {
            operations--
            busy.value = operations > 0
        }
    }

    fun refresh() {
        capabilities.value = scheduler.capabilities()
        viewModelScope.launch {
            try {
                scheduler.reconcile()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message.value =
                    "Your routine is saved. Reminder scheduling will retry; check Settings."
            }
        }
    }

    fun begin(sample: Boolean) = launchOperation {
        if (sample) repository.loadSample(Instant.now(), ZoneId.systemDefault())
        preferences.setOnboarded(sample)
    }

    fun save(medication: Medication, done: () -> Unit) = launchOperation {
        repository.save(medication)
        done()
        reconcileAfterChange("Medication saved")
    }

    fun archive(id: String, done: () -> Unit) = launchOperation {
        repository.archive(id)
        done()
        reconcileAfterChange("Medication archived. Your history is retained.")
    }

    fun refill(id: String, amount: String, done: () -> Unit) = launchOperation {
        repository.refill(id, Quantity.parse(amount.trim()))
        done()
        message.value = "Inventory updated"
    }

    fun record(key: String, status: DoseStatus) = launchOperation {
        when (repository.record(key, status, ZoneId.systemDefault(), Instant.now())) {
            RecordResult.InsufficientStock ->
                message.value = "Not enough stock. Update inventory in your cabinet."
            RecordResult.Unavailable ->
                message.value = "This schedule has changed. Please refresh your routine."
            else -> {
                scheduler.cancelNotification(key)
                reconcileAfterChange(
                    if (status == DoseStatus.TAKEN) "Recorded. Inventory updated."
                    else "Skipped. Inventory unchanged."
                )
            }
        }
    }

    fun undo(key: String) = launchOperation {
        repository.undo(key)
        reconcileAfterChange("Entry undone. Inventory restored.")
    }

    fun snooze(key: String) = launchOperation {
        if (!state.value.preferences.reminders || !scheduler.capabilities().notifications) {
            message.value = "Enable reminders and notifications in Settings first."
            return@launchOperation
        }
        repository.snooze(key, Instant.now().plusSeconds(600))
        scheduler.cancelNotification(key)
        scheduler.reconcile()
        message.value = "Reminder moved to 10 minutes from now"
    }

    fun reminders(enabled: Boolean) = launchOperation {
        preferences.setReminders(enabled)
        scheduler.reconcile()
    }

    private suspend fun reconcileAfterChange(success: String) {
        try {
            scheduler.reconcile()
            message.value = success
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            message.value = "$success Check reminder settings; scheduling will retry."
        }
    }

    fun discreet(enabled: Boolean) = launchOperation { preferences.setDiscreet(enabled) }

    fun theme(mode: ThemeMode) = launchOperation { preferences.setTheme(mode) }
}
