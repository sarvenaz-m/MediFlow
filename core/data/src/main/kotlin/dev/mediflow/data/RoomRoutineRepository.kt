package dev.mediflow.data

import androidx.room.withTransaction
import dev.mediflow.model.*
import java.time.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StaleMedicationException :
    IllegalStateException(
        "This medication changed. Close the editor and reopen it to keep the latest stock."
    )

@Serializable
data class RoutineBackup(
    val version: Int = 1,
    val exportedAt: String,
    val medications: List<MedicationEntity>,
    val records: List<DoseRecordEntity>,
)

@Singleton
class RoomRoutineRepository @Inject constructor(private val database: MediFlowDatabase) :
    RoutineRepository {
    private val dao = database.routineDao()
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    override fun observe() = dao.observeBundles().map { it.toSnapshot() }

    override suspend fun snapshot() = dao.bundles().toSnapshot()

    override suspend fun save(medication: Medication) = database.withTransaction {
        val previous = dao.medication(medication.id)
        if (previous != null && previous.revision != medication.revision)
            throw StaleMedicationException()
        dao.save(medication.copy(revision = (previous?.revision ?: 0) + 1).toEntity())
    }

    override suspend fun archive(id: String) = database.withTransaction {
        dao.medication(id)?.let { dao.save(it.copy(archived = true, revision = it.revision + 1)) }
        dao.snoozes()
            .filter { it.key.substringBefore('|') == id }
            .forEach { dao.deleteSnooze(it.key) }
    }

    override suspend fun refill(id: String, amountMilli: Long) = database.withTransaction {
        require(amountMilli in 1..Quantity.MAX) { "Enter a refill greater than zero." }
        val medicine = requireNotNull(dao.medication(id)) { "Medication no longer exists." }
        val stock = medicine.stockMilli + amountMilli
        require(stock <= Quantity.MAX) { "The resulting stock is too large." }
        dao.save(medicine.copy(stockMilli = stock, revision = medicine.revision + 1))
    }

    override suspend fun record(
        key: String,
        status: DoseStatus,
        deviceZone: ZoneId,
        now: Instant,
    ): RecordResult = database.withTransaction {
        val medicine =
            dao.medication(key.substringBefore('|'))?.toDomain()
                ?: return@withTransaction RecordResult.Unavailable
        val occurrence =
            ScheduleEngine.resolve(medicine, key, deviceZone)
                ?: return@withTransaction RecordResult.Unavailable
        val previous = dao.record(key)?.toDomain()
        if (previous?.status == status) return@withTransaction RecordResult.Unchanged
        val newStock =
            InventoryPolicy.stockAfter(
                medicine.stockMilli,
                previous,
                status,
                medicine.quantityMilli,
            ) ?: return@withTransaction RecordResult.InsufficientStock
        dao.save(medicine.copy(stockMilli = newStock, revision = medicine.revision + 1).toEntity())
        dao.saveRecord(
            DoseRecordEntity(
                key,
                medicine.id,
                medicine.name,
                occurrence.scheduledAt.toEpochMilli(),
                now.toEpochMilli(),
                medicine.quantityMilli,
                status.name,
            )
        )
        dao.deleteSnooze(key)
        RecordResult.Changed
    }

    override suspend fun undo(key: String) = database.withTransaction {
        val record = dao.record(key)?.toDomain() ?: return@withTransaction
        val medicine = dao.medication(record.medicationId) ?: return@withTransaction
        val stock =
            InventoryPolicy.stockAfter(medicine.stockMilli, record, null, medicine.quantityMilli)
        requireNotNull(stock) { "Update stock before undoing this entry." }
        dao.save(medicine.copy(stockMilli = stock, revision = medicine.revision + 1))
        dao.deleteRecord(key)
    }

    override suspend fun snooze(key: String, at: Instant) {
        dao.saveSnooze(SnoozeEntity(key, at.toEpochMilli()))
    }

    override suspend fun snoozes() =
        dao.snoozes().map { Snooze(it.key, Instant.ofEpochMilli(it.at)) }

    override suspend fun clearSnooze(key: String) = dao.deleteSnooze(key)

    override suspend fun loadSample(now: Instant, zone: ZoneId) = database.withTransaction {
        require(dao.bundles().isEmpty()) { "Sample data is available only for an empty routine." }
        val today = now.atZone(zone).toLocalDate()
        val samples =
            listOf(
                Medication(
                    "demo-vitamin",
                    "Vitamin D3",
                    "Sample entry",
                    MedicineForm.SOFTGEL,
                    1000,
                    30_000,
                    5000,
                    DoseSchedule(listOf(LocalTime.of(8, 0)), startDate = today.minusDays(6)),
                    "Illustrative routine",
                    0,
                ),
                Medication(
                    "demo-omega",
                    "Omega-3",
                    "Sample entry",
                    MedicineForm.CAPSULE,
                    1000,
                    24_000,
                    5000,
                    DoseSchedule(listOf(LocalTime.of(12, 30)), startDate = today.minusDays(6)),
                    "Illustrative routine",
                    1,
                ),
                Medication(
                    "demo-magnesium",
                    "Magnesium",
                    "Sample entry",
                    MedicineForm.TABLET,
                    1000,
                    9000,
                    5000,
                    DoseSchedule(listOf(LocalTime.of(20, 0)), startDate = today.minusDays(6)),
                    "Illustrative routine",
                    2,
                ),
            )
        samples.forEach { dao.save(it.toEntity()) }
        for (daysAgo in 6L downTo 1L) {
            samples.forEachIndexed { index, med ->
                if (!(daysAgo == 3L && index == 1)) {
                    val dose =
                        ScheduleEngine.occurrence(
                            med,
                            today.minusDays(daysAgo),
                            med.schedule.times.single(),
                            zone,
                        )!!
                    val status =
                        if (daysAgo == 4L && index == 2) DoseStatus.SKIPPED else DoseStatus.TAKEN
                    record(dose.key, status, zone, dose.scheduledAt.plusSeconds(300))
                }
            }
        }
    }

    override suspend fun exportBackup(): String = database.withTransaction {
        val bundles = dao.bundles()
        json.encodeToString(
            RoutineBackup(
                exportedAt = Instant.now().toString(),
                medications = bundles.map { it.medication },
                records = bundles.flatMap { it.records },
            )
        )
    }

    override suspend fun restoreBackup(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= 2_097_152) {
            "Backup must be smaller than 2 MB."
        }
        val backup = this.json.decodeFromString<RoutineBackup>(json)
        require(backup.version == 1) { "This backup version is not supported." }
        Instant.parse(backup.exportedAt)
        require(backup.medications.size <= 500 && backup.records.size <= 20_000) {
            "Backup exceeds the supported size."
        }
        val meds = backup.medications.map { it.toDomain() }
        val ids = meds.map { it.id }.toSet()
        require(ids.size == meds.size) { "Duplicate medication identifiers." }
        require(backup.records.map { it.key }.distinct().size == backup.records.size) {
            "Duplicate dose identifiers."
        }
        backup.records.forEach {
            require(it.medicationId in ids && it.key.substringBefore('|') == it.medicationId) {
                "A history entry has no matching medication."
            }
            val parts = it.key.split('|')
            require(parts.size == 3)
            LocalDate.parse(parts[1])
            LocalTime.parse(parts[2])
            require(
                it.quantityMilli in 1..Quantity.MAX &&
                    it.medicationName.isNotBlank() &&
                    it.medicationName.length <= 100
            )
            require(
                it.scheduledAt in 946684800000..7258118400000 &&
                    it.recordedAt in 946684800000..7258118400000
            )
            DoseStatus.valueOf(it.status)
        }
        // Validate the entire document before opening the replacement transaction.
        database.withTransaction {
            dao.clearSnoozes()
            dao.clearRecords()
            dao.clearMedications()
            backup.medications.forEach { dao.save(it) }
            backup.records.forEach { dao.saveRecord(it) }
            // Keep the alarm registry until reconciliation can cancel old PendingIntents.
        }
    }
}
