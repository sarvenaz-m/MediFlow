package dev.mediflow.reminders

import android.content.*
import android.util.Log
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.mediflow.model.*
import java.time.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class DoseReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var preferences: PreferenceRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var publisher: NotificationPublisher

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(AndroidReminderScheduler.KEY) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000) {
                    val snapshot = repository.snapshot()
                    val medicine =
                        snapshot.medications.find { it.id == key.substringBefore('|') }
                            ?: return@withTimeoutOrNull
                    val dose =
                        ScheduleEngine.resolve(medicine, key, ZoneId.systemDefault())
                            ?: return@withTimeoutOrNull
                    val prefs = preferences.preferences.first()
                    when (intent.action) {
                        AndroidReminderScheduler.TAKEN -> {
                            when (
                                repository.record(
                                    key,
                                    DoseStatus.TAKEN,
                                    ZoneId.systemDefault(),
                                    Instant.now(),
                                )
                            ) {
                                RecordResult.InsufficientStock ->
                                    publisher.show(
                                        dose,
                                        prefs.discreetNotifications,
                                        "Review inventory in MediFlow before recording this entry.",
                                    )
                                else -> scheduler.cancelNotification(key)
                            }
                        }
                        AndroidReminderScheduler.SNOOZE -> {
                            if (snapshot.records.none { it.key == key })
                                repository.snooze(key, Instant.now().plusSeconds(600))
                            scheduler.cancelNotification(key)
                        }
                        AndroidReminderScheduler.NOTIFY -> {
                            if (
                                prefs.reminders &&
                                    scheduler.capabilities().notifications &&
                                    snapshot.records.none { it.key == key } &&
                                    Duration.between(dose.scheduledAt, Instant.now()).toHours() < 24
                            ) {
                                publisher.show(dose, prefs.discreetNotifications)
                            }
                            repository.clearSnooze(key)
                        }
                    }
                    scheduler.reconcile()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w(
                    "MediFlowReminders",
                    "Reminder action deferred; maintenance will reconcile the schedule.",
                )
            } finally {
                runCatching { ReconcileWorker.enqueue(context) }
                pending.finish()
            }
        }
    }
}

@AndroidEntryPoint
class ReconcileReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000) { scheduler.reconcile() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w("MediFlowReminders", "Schedule reconciliation deferred to maintenance.")
            } finally {
                runCatching {
                    ReconcileWorker.enqueue(context)
                    ReconcileWorker.periodic(context)
                }
                pending.finish()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderWorkerDependencies {
    fun scheduler(): ReminderScheduler
}

class ReconcileWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            EntryPointAccessors.fromApplication(
                    applicationContext,
                    ReminderWorkerDependencies::class.java,
                )
                .scheduler()
                .reconcile()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "reminder-reconcile",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ReconcileWorker>().build(),
                )
        }

        fun periodic(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "reminder-maintenance",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<ReconcileWorker>(15, TimeUnit.MINUTES).build(),
                )
        }
    }
}
