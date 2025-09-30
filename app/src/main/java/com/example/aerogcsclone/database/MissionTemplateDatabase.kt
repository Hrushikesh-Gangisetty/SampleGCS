package com.example.aerogcsclone.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context

/**
 * Room database for Mission Plan Templates
 */
@Database(
    entities = [MissionTemplateEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(MissionTemplateTypeConverters::class)
abstract class MissionTemplateDatabase : RoomDatabase() {

    abstract fun missionTemplateDao(): MissionTemplateDao

    companion object {
        @Volatile
        private var INSTANCE: MissionTemplateDatabase? = null

        fun getDatabase(context: Context): MissionTemplateDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MissionTemplateDatabase::class.java,
                    "mission_template_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
