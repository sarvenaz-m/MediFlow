package dev.mediflow.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mediflow.model.*
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AndroidReminderScheduler
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: RoutineRepository,
    private val preferences: PreferenceRepository,
    private val registry: AlarmRegistry,
    private val clock: Clock,
) : ReminderScheduler {
    private val mutex = Mutex()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun capabilities(): ReminderCapabilities {
        val manager = context.getSystemService(NotificationManager::class.java)
        val permission =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        val channelEnabled =
            manager.getNotificationChannel(NotificationPublisher.CHANNEL)?.importance !=
                NotificationManager.IMPORTANCE_NONE
        return ReminderCapabilities(
            permission &&
                channelEnabled &&
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms(),
        )
    }

    override fun cancelNotification(key: String) {
        NotificationManagerCompat.from(context).cancel(key, 0)
    }

    override suspend fun reconcile() = mutex.withLock {
        NotificationPublisher.createChannel(context)
        val prefs = preferences.preferences.first()
        val available = capabilities()
        val now = clock.instant()
        val snapshot = repository.snapshot()
        val records = snapshot.records.mapTo(hashSetOf()) { it.key }
        // A delivered notification outlives its alarm. Clear it only when its entry
        // becomes invalid, is recorded, or reminders are disabled.
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.notification.channelId == NotificationPublisher.CHANNEL }
            .forEach { notification ->
                val key = notification.tag
                val medicine = snapshot.medications.find { it.id == key?.substringBefore('|') }
                if (
                    !prefs.reminders ||
                        !available.notifications ||
                        key == null ||
                        key in records ||
                        medicine == null ||
                        ScheduleEngine.resolve(medicine, key, ZoneId.systemDefault()) == null
                ) {
                    manager.cancel(key, notification.id)
                }
            }
        val desired = linkedMapOf<String, Instant>()
        if (prefs.reminders && available.notifications) {
            ScheduleEngine.next(snapshot, now, ZoneId.systemDefault()).forEach {
                desired[it.key] = it.scheduledAt
            }
            repository.snoozes().forEach { snooze ->
                val medicine =
                    snapshot.medications.find { it.id == snooze.key.substringBefore('|') }
                if (
                    snooze.at.isAfter(now) &&
                        snooze.key !in records &&
                        medicine != null &&
                        ScheduleEngine.resolve(medicine, snooze.key, ZoneId.systemDefault()) != null
                ) {
                    desired[snooze.key] = snooze.at
                } else repository.clearSnooze(snooze.key)
            }
        }
        registry
            .entries()
            .filter { it.key !in desired }
            .forEach {
                alarmManager.cancel(pendingIntent(context, it.key))
                registry.remove(it.key)
            }
        desired.forEach { (key, instant) ->
            val pending = pendingIntent(context, key)
            if (available.exactAlarms) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        instant.toEpochMilli(),
                        pending,
                    )
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        instant.toEpochMilli(),
                        pending,
                    )
                }
            } else
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    instant.toEpochMilli(),
                    pending,
                )
            registry.save(RegisteredAlarm(key, instant.toEpochMilli()))
        }
    }

    companion object {
        const val KEY = "occurrence_key"
        const val NOTIFY = "dev.mediflow.NOTIFY"
        const val TAKEN = "dev.mediflow.TAKEN"
        const val SNOOZE = "dev.mediflow.SNOOZE"

        fun pendingIntent(context: Context, key: String, action: String = NOTIFY): PendingIntent {
            val intent =
                Intent(context, DoseReceiver::class.java)
                    .setAction(action)
                    .setData(
                        Uri.Builder()
                            .scheme("mediflow")
                            .authority("reminder")
                            .appendPath(action)
                            .appendPath(key)
                            .build()
                    )
                    .putExtra(KEY, key)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds
    @Singleton
    abstract fun scheduler(implementation: AndroidReminderScheduler): ReminderScheduler

    companion object {
        @Provides @Singleton fun clock(): Clock = Clock.systemUTC()
    }
}
