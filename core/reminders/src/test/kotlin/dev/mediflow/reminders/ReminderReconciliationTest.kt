package dev.mediflow.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.mediflow.model.*
import java.time.*
import java.util.TimeZone
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Exercises Android scheduling decisions; does not simulate real Doze delivery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderReconciliationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val clock = Clock.fixed(Instant.parse("2026-03-02T07:00:00Z"), ZoneOffset.UTC)
    private val medicine =
        Medication(
            "sample",
            "Sample",
            "",
            MedicineForm.TABLET,
            1000,
            30000,
            5000,
            DoseSchedule(listOf(LocalTime.of(8, 0)), startDate = LocalDate.of(2026, 3, 1)),
        )
    private val repository = FakeRoutineRepository(RoutineSnapshot(listOf(medicine)))
    private val preferences = FakePreferences()
    private val registry = FakeRegistry()
    private val scheduler =
        AndroidReminderScheduler(context, repository, preferences, registry, clock)
    private lateinit var previousZone: TimeZone

    @Before
    fun setup() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        ShadowAlarmManager.setAutoSchedule(false)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(context as android.app.Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationPublisher.createChannel(context)
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(previousZone)
    }

    @Test
    fun reconciliationIsIdempotentAndUsesExactIdleAlarmsWhenAllowed() = runTest {
        scheduler.reconcile()
        scheduler.reconcile()
        val alarms = shadowOf(alarmManager).scheduledAlarms
        assertEquals(8, alarms.size)
        assertEquals(8, registry.entries().size)
        assertEquals(
            Instant.parse("2026-03-02T08:00:00Z").toEpochMilli(),
            alarms.minOf { it.triggerAtMs },
        )
        assertTrue(
            alarms.all {
                it.windowLengthMs == ShadowAlarmManager.WINDOW_EXACT && it.isAllowWhileIdle
            }
        )
    }

    @Test
    fun exactPermissionDenialKeepsApproximateReminders() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        scheduler.reconcile()
        assertFalse(scheduler.capabilities().exactAlarms)
        assertEquals(8, shadowOf(alarmManager).scheduledAlarms.size)
        assertTrue(
            shadowOf(alarmManager).scheduledAlarms.all {
                it.windowLengthMs != ShadowAlarmManager.WINDOW_EXACT
            }
        )
    }

    @Test
    fun disablingRemindersCancelsAlarmsAndPostedNotifications() = runTest {
        scheduler.reconcile()
        postNotification("sample|2026-03-02|08:00")
        preferences.setReminders(false)
        scheduler.reconcile()
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertTrue(registry.entries().isEmpty())
        assertEquals(0, notificationManager.activeNotifications.size)
    }

    @Test
    fun deniedNotificationsCancelPreviouslyScheduledAlarms() = runTest {
        scheduler.reconcile()
        shadowOf(context as android.app.Application)
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        scheduler.reconcile()
        assertFalse(scheduler.capabilities().notifications)
        assertTrue(registry.entries().isEmpty())
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun deliveredEntryIsRetainedUntilArchivedOrRecorded() = runTest {
        val key = "sample|2026-03-01|08:00"
        postNotification(key)
        scheduler.reconcile()
        assertEquals(1, notificationManager.activeNotifications.size)
        repository.value = RoutineSnapshot(listOf(medicine.copy(archived = true)))
        scheduler.reconcile()
        assertEquals(0, notificationManager.activeNotifications.size)
        assertTrue(registry.entries().isEmpty())
    }

    @Test
    fun snoozeReplacesOriginalAlarmAndInvalidSnoozeIsRemoved() = runTest {
        val key = "sample|2026-03-02|08:00"
        val at = clock.instant().plusSeconds(600)
        repository.pendingSnoozes += Snooze(key, at)
        repository.pendingSnoozes += Snooze("missing|2026-03-02|08:00", at)
        scheduler.reconcile()
        assertEquals(at.toEpochMilli(), registry.entries().single { it.key == key }.at)
        assertEquals(listOf(Snooze(key, at)), repository.snoozes())
    }

    private fun postNotification(key: String) {
        notificationManager.notify(
            key,
            0,
            Notification.Builder(context, NotificationPublisher.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Sample reminder")
                .build(),
        )
    }

    private class FakeRegistry : AlarmRegistry {
        val values = linkedMapOf<String, RegisteredAlarm>()

        override suspend fun entries() = values.values.toList()

        override suspend fun save(alarm: RegisteredAlarm) {
            values[alarm.key] = alarm
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }

    private class FakePreferences : PreferenceRepository {
        override val preferences = MutableStateFlow(AppPreferences(reminders = true))

        override suspend fun setReminders(enabled: Boolean) {
            preferences.value = preferences.value.copy(reminders = enabled)
        }

        override suspend fun setOnboarded(sample: Boolean) = Unit

        override suspend fun setTheme(theme: ThemeMode) = Unit

        override suspend fun setDiscreet(enabled: Boolean) = Unit
    }

    private class FakeRoutineRepository(var value: RoutineSnapshot) : RoutineRepository {
        val pendingSnoozes = mutableListOf<Snooze>()

        override suspend fun snapshot() = value

        override fun observe(): Flow<RoutineSnapshot> = flowOf(value)

        override suspend fun snoozes() = pendingSnoozes.toList()

        override suspend fun clearSnooze(key: String) {
            pendingSnoozes.removeAll { it.key == key }
        }

        override suspend fun save(medication: Medication) = error("Not used by scheduler")

        override suspend fun archive(id: String) = error("Not used by scheduler")

        override suspend fun refill(id: String, amountMilli: Long) = error("Not used by scheduler")

        override suspend fun record(
            key: String,
            status: DoseStatus,
            deviceZone: ZoneId,
            now: Instant,
        ): RecordResult = error("Not used by scheduler")

        override suspend fun undo(key: String) = error("Not used by scheduler")

        override suspend fun snooze(key: String, at: Instant) = error("Not used by scheduler")

        override suspend fun loadSample(now: Instant, zone: ZoneId) = error("Not used by scheduler")

        override suspend fun exportBackup(): String = error("Not used by scheduler")

        override suspend fun restoreBackup(json: String) = error("Not used by scheduler")
    }
}
