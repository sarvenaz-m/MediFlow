package dev.mediflow.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PendingIntentIdentityTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun identitiesDoNotCollideAcrossMedications() {
        assertNotEquals(
            AndroidReminderScheduler.pendingIntent(context, "first|2026-10-12|08:00"),
            AndroidReminderScheduler.pendingIntent(context, "second|2026-10-12|08:00"),
        )
    }

    @Test
    fun notificationAndActionHaveSeparateIdentities() {
        val key = "first|2026-10-12|08:00"
        assertNotEquals(
            AndroidReminderScheduler.pendingIntent(context, key),
            AndroidReminderScheduler.pendingIntent(context, key, AndroidReminderScheduler.TAKEN),
        )
    }

    @Test
    fun sameOccurrenceReusesPendingIntent() {
        val key = "first|2026-10-12|08:00"
        assertEquals(
            AndroidReminderScheduler.pendingIntent(context, key),
            AndroidReminderScheduler.pendingIntent(context, key),
        )
    }
}
