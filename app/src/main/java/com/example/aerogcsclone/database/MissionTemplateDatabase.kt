package com.example.aerogcsclone.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.example.aerogcsclone.database.tlog.*

/**
 * Room database for Mission Plan Templates and Flight Logs
 */
@Database(
    entities = [
        MissionTemplateEntity::class,
        FlightEntity::class,
        TelemetryEntity::class,
        EventEntity::class,
        MapDataEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(MissionTemplateTypeConverters::class, TlogTypeConverters::class)
abstract class MissionTemplateDatabase : RoomDatabase() {

    abstract fun missionTemplateDao(): MissionTemplateDao
    abstract fun flightDao(): FlightDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun eventDao(): EventDao
    abstract fun mapDataDao(): MapDataDao

    companion object {
        @Volatile
        private var INSTANCE: MissionTemplateDatabase? = null

        fun getDatabase(context: Context): MissionTemplateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MissionTemplateDatabase::class.java,
                    "mission_template_database"
                )
                .fallbackToDestructiveMigration() // For now, will recreate on schema change
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
