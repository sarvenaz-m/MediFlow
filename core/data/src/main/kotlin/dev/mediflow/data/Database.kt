package dev.mediflow.data

import androidx.room.*
import dev.mediflow.model.*
import java.time.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val strength: String,
    val form: String,
    val quantityMilli: Long,
    val stockMilli: Long,
    val lowStockMilli: Long,
    val times: String,
    val weekdays: String,
    val startDate: String,
    val endDate: String?,
    val timePolicy: String,
    val homeZone: String,
    val notes: String,
    val color: Int,
    val archived: Boolean,
    val revision: Long,
)

@Serializable
@Entity(
    tableName = "dose_records",
    foreignKeys =
        [
            ForeignKey(
                entity = MedicationEntity::class,
                parentColumns = ["id"],
                childColumns = ["medicationId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("medicationId"), Index("scheduledAt")],
)
data class DoseRecordEntity(
    @PrimaryKey val key: String,
    val medicationId: String,
    val medicationName: String,
    val scheduledAt: Long,
    val recordedAt: Long,
    val quantityMilli: Long,
    val status: String,
)

@Entity(tableName = "snoozes") data class SnoozeEntity(@PrimaryKey val key: String, val at: Long)

@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarmEntity(@PrimaryKey val key: String, val at: Long)

data class MedicationBundle(
    @Embedded val medication: MedicationEntity,
    @Relation(parentColumn = "id", entityColumn = "medicationId")
    val records: List<DoseRecordEntity>,
)

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    fun observeBundles(): Flow<List<MedicationBundle>>

    @Transaction
    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    suspend fun bundles(): List<MedicationBundle>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun medication(id: String): MedicationEntity?

    @Query("SELECT * FROM dose_records WHERE `key` = :key")
    suspend fun record(key: String): DoseRecordEntity?

    @Upsert suspend fun save(medication: MedicationEntity)

    @Upsert suspend fun saveRecord(record: DoseRecordEntity)

    @Query("DELETE FROM dose_records WHERE `key` = :key") suspend fun deleteRecord(key: String)

    @Upsert suspend fun saveSnooze(snooze: SnoozeEntity)

    @Query("SELECT * FROM snoozes") suspend fun snoozes(): List<SnoozeEntity>

    @Query("DELETE FROM snoozes WHERE `key` = :key") suspend fun deleteSnooze(key: String)

    @Query("DELETE FROM snoozes") suspend fun clearSnoozes()

    @Query("DELETE FROM dose_records") suspend fun clearRecords()

    @Query("DELETE FROM medications") suspend fun clearMedications()

    @Query("SELECT * FROM scheduled_alarms") suspend fun alarms(): List<ScheduledAlarmEntity>

    @Upsert suspend fun saveAlarm(alarm: ScheduledAlarmEntity)

    @Query("DELETE FROM scheduled_alarms WHERE `key` = :key") suspend fun deleteAlarm(key: String)
}

@Database(
    entities =
        [
            MedicationEntity::class,
            DoseRecordEntity::class,
            SnoozeEntity::class,
            ScheduledAlarmEntity::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class MediFlowDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
}

fun MedicationEntity.toDomain() =
    Medication(
        id,
        name,
        strength,
        MedicineForm.valueOf(form),
        quantityMilli,
        stockMilli,
        lowStockMilli,
        DoseSchedule(
            times.split(',').map(LocalTime::parse),
            weekdays.split(',').map { DayOfWeek.of(it.toInt()) }.toSet(),
            LocalDate.parse(startDate),
            endDate?.let(LocalDate::parse),
            TimePolicy.valueOf(timePolicy),
            homeZone,
        ),
        notes,
        color,
        archived,
        revision,
    )

fun Medication.toEntity() =
    MedicationEntity(
        id,
        name,
        strength,
        form.name,
        quantityMilli,
        stockMilli,
        lowStockMilli,
        schedule.times.sorted().joinToString(","),
        schedule.weekdays.sorted().joinToString(",") { it.value.toString() },
        schedule.startDate.toString(),
        schedule.endDate?.toString(),
        schedule.timePolicy.name,
        schedule.homeZone,
        notes,
        color,
        archived,
        revision,
    )

fun DoseRecordEntity.toDomain() =
    DoseRecord(
        key,
        medicationId,
        medicationName,
        Instant.ofEpochMilli(scheduledAt),
        Instant.ofEpochMilli(recordedAt),
        quantityMilli,
        DoseStatus.valueOf(status),
    )

fun List<MedicationBundle>.toSnapshot() =
    RoutineSnapshot(map { it.medication.toDomain() }, flatMap { it.records }.map { it.toDomain() })
