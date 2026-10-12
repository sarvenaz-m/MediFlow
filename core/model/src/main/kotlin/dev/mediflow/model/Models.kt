package dev.mediflow.model

import java.math.BigDecimal
import java.time.*
import kotlinx.coroutines.flow.Flow

enum class MedicineForm(val label: String, val unit: String) {
    TABLET("Tablet", "tablet"),
    CAPSULE("Capsule", "capsule"),
    SOFTGEL("Softgel", "softgel"),
    LIQUID("Liquid", "mL"),
}

enum class TimePolicy {
    LOCAL_CLOCK,
    HOME_ZONE,
}

enum class DoseStatus {
    TAKEN,
    SKIPPED,
}

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/** Quantities use 1/1000 of a unit. Floating-point arithmetic never touches inventory. */
object Quantity {
    const val MAX = 1_000_000_000L

    fun parse(text: String): Long {
        require(text.matches(Regex("[0-9]{1,7}(\\.[0-9]{1,3})?"))) {
            "Use a positive number with up to 3 decimal places."
        }
        return BigDecimal(text).movePointRight(3).longValueExact().also {
            require(it in 0..MAX) { "Quantity is outside the supported range." }
        }
    }

    fun format(value: Long): String =
        BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString()
}

data class DoseSchedule(
    val times: List<LocalTime>,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val timePolicy: TimePolicy = TimePolicy.LOCAL_CLOCK,
    val homeZone: String = "Europe/Lisbon",
) : java.io.Serializable {
    init {
        require(times.isNotEmpty() && times.size <= 12 && times.distinct().size == times.size)
        require(times.all { it.second == 0 && it.nano == 0 })
        require(weekdays.isNotEmpty())
        require(endDate == null || !endDate.isBefore(startDate))
        ZoneId.of(homeZone)
    }

    fun zone(deviceZone: ZoneId): ZoneId =
        if (timePolicy == TimePolicy.LOCAL_CLOCK) deviceZone else ZoneId.of(homeZone)

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(startDate) &&
            (endDate == null || !date.isAfter(endDate)) &&
            date.dayOfWeek in weekdays
}

data class Medication(
    val id: String,
    val name: String,
    val strength: String,
    val form: MedicineForm,
    val quantityMilli: Long,
    val stockMilli: Long,
    val lowStockMilli: Long,
    val schedule: DoseSchedule,
    val notes: String = "",
    val color: Int = 0,
    val archived: Boolean = false,
    val revision: Long = 0,
) : java.io.Serializable {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(name.isNotBlank() && name.length <= 100)
        require(strength.length <= 100 && notes.length <= 1000)
        require(
            quantityMilli in 1..Quantity.MAX &&
                stockMilli in 0..Quantity.MAX &&
                lowStockMilli in 0..Quantity.MAX
        )
        require(color in 0..3)
        require(revision >= 0)
    }
}

data class DoseOccurrence(
    val key: String,
    val medication: Medication,
    val scheduledAt: Instant,
    val localDate: LocalDate,
    val localTime: LocalTime,
)

data class DoseRecord(
    val key: String,
    val medicationId: String,
    val medicationName: String,
    val scheduledAt: Instant,
    val recordedAt: Instant,
    val quantityMilli: Long,
    val status: DoseStatus,
)

data class RoutineSnapshot(
    val medications: List<Medication> = emptyList(),
    val records: List<DoseRecord> = emptyList(),
)

data class AppPreferences(
    val onboarded: Boolean = false,
    val sampleData: Boolean = false,
    val reminders: Boolean = false,
    val theme: ThemeMode = ThemeMode.LIGHT,
    val discreetNotifications: Boolean = true,
)

data class Snooze(val key: String, val at: Instant)

data class ReminderCapabilities(val notifications: Boolean, val exactAlarms: Boolean)

data class RegisteredAlarm(val key: String, val at: Long)

interface AlarmRegistry {
    suspend fun entries(): List<RegisteredAlarm>

    suspend fun save(alarm: RegisteredAlarm)

    suspend fun remove(key: String)
}

sealed interface RecordResult {
    data object Changed : RecordResult

    data object Unchanged : RecordResult

    data object InsufficientStock : RecordResult

    data object Unavailable : RecordResult
}

interface RoutineRepository {
    fun observe(): Flow<RoutineSnapshot>

    suspend fun snapshot(): RoutineSnapshot

    suspend fun save(medication: Medication)

    suspend fun archive(id: String)

    suspend fun refill(id: String, amountMilli: Long)

    suspend fun record(
        key: String,
        status: DoseStatus,
        deviceZone: ZoneId,
        now: Instant,
    ): RecordResult

    suspend fun undo(key: String)

    suspend fun snooze(key: String, at: Instant)

    suspend fun snoozes(): List<Snooze>

    suspend fun clearSnooze(key: String)

    suspend fun loadSample(now: Instant, zone: ZoneId)

    suspend fun exportBackup(): String

    suspend fun restoreBackup(json: String)
}

interface PreferenceRepository {
    val preferences: Flow<AppPreferences>

    suspend fun setOnboarded(sample: Boolean)

    suspend fun setReminders(enabled: Boolean)

    suspend fun setTheme(theme: ThemeMode)

    suspend fun setDiscreet(enabled: Boolean)
}

interface ReminderScheduler {
    suspend fun reconcile()

    fun capabilities(): ReminderCapabilities

    fun cancelNotification(key: String)
}
