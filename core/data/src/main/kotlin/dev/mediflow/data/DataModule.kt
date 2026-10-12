package dev.mediflow.data

import android.content.Context
import androidx.room.Room
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mediflow.model.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun alarms(implementation: RoomAlarmRegistry): AlarmRegistry

    @Binds @Singleton abstract fun routine(implementation: RoomRoutineRepository): RoutineRepository

    @Binds
    @Singleton
    abstract fun preferences(implementation: DataStorePreferences): PreferenceRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MediFlowDatabase =
        Room.databaseBuilder(context, MediFlowDatabase::class.java, "mediflow.db").build()
}
