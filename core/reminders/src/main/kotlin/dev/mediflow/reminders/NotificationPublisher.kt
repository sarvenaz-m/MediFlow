package dev.mediflow.reminders

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mediflow.model.*
import javax.inject.Inject

class NotificationPublisher
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    @SuppressLint(
        "MissingPermission"
    ) // Checked immediately before notify; revocation is also caught.
    fun show(dose: DoseOccurrence, discreet: Boolean, problem: String? = null) {
        createChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val open =
            PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val title = if (discreet) "Your routine is ready" else dose.medication.name
        val description =
            problem
                ?: if (discreet) "Open MediFlow to view your scheduled reminder."
                else
                    "${Quantity.format(dose.medication.quantityMilli)} ${dose.medication.form.unit} · ${dose.medication.strength}"
        val notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(description)
                .setStyle(NotificationCompat.BigTextStyle().bigText(description))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(
                    0,
                    "Taken",
                    AndroidReminderScheduler.pendingIntent(
                        context,
                        dose.key,
                        AndroidReminderScheduler.TAKEN,
                    ),
                )
                .addAction(
                    0,
                    "In 10 minutes",
                    AndroidReminderScheduler.pendingIntent(
                        context,
                        dose.key,
                        AndroidReminderScheduler.SNOOZE,
                    ),
                )
                .build()
        try {
            NotificationManagerCompat.from(context).notify(dose.key, 0, notification)
        } catch (_: SecurityException) {
            /* The next app resume shows the updated permission state. */
        }
    }

    companion object {
        const val CHANNEL = "routine_reminders"

        fun createChannel(context: Context) {
            val channel =
                NotificationChannel(
                        CHANNEL,
                        "Routine reminders",
                        NotificationManager.IMPORTANCE_HIGH,
                    )
                    .apply {
                        description = "Reminders for the schedules you enter."
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    }
            context
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
