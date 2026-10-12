package dev.mediflow.app

import android.app.Application
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.mediflow.reminders.NotificationPublisher
import dev.mediflow.reminders.ReconcileWorker

@HiltAndroidApp
class MediFlowApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        NotificationPublisher.createChannel(this)
        ReconcileWorker.periodic(this)
        ReconcileWorker.enqueue(this)
    }
}
