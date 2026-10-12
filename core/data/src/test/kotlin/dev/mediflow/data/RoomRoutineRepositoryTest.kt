package dev.mediflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mediflow.model.*
import java.time.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class RoomRoutineRepositoryTest {
    private lateinit var db: MediFlowDatabase
    private lateinit var repository: RoomRoutineRepository
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = Instant.parse("2026-10-12T10:00:00Z")
    private val medicine =
        Medication(
            "test",
            "Test medicine",
            "Sample entry",
            MedicineForm.TABLET,
            500,
            10_000,
            1000,
            DoseSchedule(listOf(LocalTime.of(8, 0)), startDate = LocalDate.of(2026, 10, 1)),
        )
    private val key = "test|2026-10-12|08:00"

    @Before
    fun setup() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    MediFlowDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        repository = RoomRoutineRepository(db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun duplicateTapsConsumeInventoryOnce() = runBlocking {
        repository.save(medicine)
        coroutineScope {
            (1..30)
                .map {
                    async(Dispatchers.Default) {
                        repository.record(key, DoseStatus.TAKEN, zone, now)
                    }
                }
                .awaitAll()
        }
        val snapshot = repository.snapshot()
        assertEquals(9500L, snapshot.medications.single().stockMilli)
        assertEquals(1, snapshot.records.size)
    }

    @Test
    fun undoRestoresOriginalQuantityAfterEditingDose() = runBlocking {
        repository.save(medicine)
        repository.record(key, DoseStatus.TAKEN, zone, now)
        repository.save(repository.snapshot().medications.single().copy(quantityMilli = 1000))
        repository.undo(key)
        assertEquals(10_000L, repository.snapshot().medications.single().stockMilli)
    }

    @Test
    fun staleEditorCannotOverwriteNewStock() = runBlocking {
        repository.save(medicine)
        val edit = repository.snapshot().medications.single()
        repository.record(key, DoseStatus.TAKEN, zone, now)
        assertThrows(StaleMedicationException::class.java) {
            runBlocking { repository.save(edit.copy(name = "Changed")) }
        }
        assertEquals(9500L, repository.snapshot().medications.single().stockMilli)
    }

    @Test
    fun insufficientStockLeavesNoRecord() = runBlocking {
        repository.save(medicine.copy(stockMilli = 100))
        assertEquals(
            RecordResult.InsufficientStock,
            repository.record(key, DoseStatus.TAKEN, zone, now),
        )
        assertTrue(repository.snapshot().records.isEmpty())
        assertEquals(100L, repository.snapshot().medications.single().stockMilli)
    }

    @Test
    fun skippedThenTakenConsumesOnce() = runBlocking {
        repository.save(medicine)
        repository.record(key, DoseStatus.SKIPPED, zone, now)
        repository.record(key, DoseStatus.TAKEN, zone, now)
        assertEquals(9500L, repository.snapshot().medications.single().stockMilli)
    }

    @Test
    fun archiveRetainsHistoryButRemovesUpcomingDoses() = runBlocking {
        repository.save(medicine)
        repository.record(key, DoseStatus.TAKEN, zone, now)
        repository.archive(medicine.id)
        val data = repository.snapshot()
        assertEquals(1, data.records.size)
        assertTrue(ScheduleEngine.next(data, now, zone).isEmpty())
    }

    @Test
    fun backupRoundTripPreservesRecordsAndStock() = runBlocking {
        repository.save(medicine)
        repository.record(key, DoseStatus.TAKEN, zone, now)
        val before = repository.snapshot()
        val backup = repository.exportBackup()
        repository.refill(medicine.id, 5000)
        repository.restoreBackup(backup)
        assertEquals(before, repository.snapshot())
    }

    @Test
    fun invalidBackupCannotEraseCurrentRoutine() = runBlocking {
        repository.save(medicine)
        val before = repository.snapshot()
        assertThrows(Exception::class.java) {
            runBlocking { repository.restoreBackup("{\"version\":42}") }
        }
        assertEquals(before, repository.snapshot())
    }

    @Test
    fun sampleDatasetIsExplicitAndCannotOverwriteExistingData() = runBlocking {
        repository.save(medicine)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.loadSample(now, zone) }
        }
        assertEquals(1, repository.snapshot().medications.size)
    }

    @Test
    fun takingClearsSnooze() = runBlocking {
        repository.save(medicine)
        repository.snooze(key, now.plusSeconds(600))
        repository.record(key, DoseStatus.TAKEN, zone, now)
        assertTrue(repository.snoozes().isEmpty())
    }

    @Test
    fun duplicateUndoIsHarmless() = runBlocking {
        repository.save(medicine)
        repository.record(key, DoseStatus.TAKEN, zone, now)
        repository.undo(key)
        repository.undo(key)
        assertEquals(10_000L, repository.snapshot().medications.single().stockMilli)
    }

    @Test
    fun missingMedicationCannotBeRecorded() = runBlocking {
        assertEquals(RecordResult.Unavailable, repository.record(key, DoseStatus.TAKEN, zone, now))
    }
}
