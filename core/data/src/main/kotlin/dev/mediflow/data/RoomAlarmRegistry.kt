package dev.mediflow.data

import dev.mediflow.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAlarmRegistry @Inject constructor(private val database: MediFlowDatabase) :
    AlarmRegistry {
    override suspend fun entries() =
        database.routineDao().alarms().map { RegisteredAlarm(it.key, it.at) }

    override suspend fun save(alarm: RegisteredAlarm) =
        database.routineDao().saveAlarm(ScheduledAlarmEntity(alarm.key, alarm.at))

    override suspend fun remove(key: String) = database.routineDao().deleteAlarm(key)
}
