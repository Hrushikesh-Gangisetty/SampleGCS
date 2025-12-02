package com.example.aerogcsclone.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.aerogcsclone.database.obstacle.*

/**
 * Database for storing obstacle detection mission states
 */
@Database(
    entities = [SavedMissionStateEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(MissionStateTypeConverters::class)
abstract class ObstacleDatabase : RoomDatabase() {

    abstract fun savedMissionStateDao(): SavedMissionStateDao

    companion object {
        @Volatile
        private var INSTANCE: ObstacleDatabase? = null

        fun getDatabase(context: Context): ObstacleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ObstacleDatabase::class.java,
                    "obstacle_detection_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
