package com.example.aerogcsclone.database.obstacle

import androidx.room.*
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Entity for storing interrupted mission state
 */
@Entity(tableName = "saved_mission_states")
data class SavedMissionStateEntity(
    @PrimaryKey
    val missionId: String,
    val interruptedWaypointIndex: Int,
    val currentDroneLat: Double,
    val currentDroneLng: Double,
    val homeLat: Double,
    val homeLng: Double,
    val originalWaypointsJson: String,      // Serialized MissionItemInt list
    val remainingWaypointsJson: String,     // Serialized MissionItemInt list
    val obstacleLat: Double?,
    val obstacleLng: Double?,
    val obstacleDistance: Float,
    val obstacleBearing: Float?,
    val obstacleThreatLevel: String,
    val missionProgress: Float,
    val timestamp: Long,
    val surveyPolygonJson: String?,         // Serialized LatLng list
    val altitude: Float?,
    val speed: Float?,
    val loiterRadius: Float?,
    val rtlAltitude: Float?,
    val descentRate: Float?,
    val isResolved: Boolean = false,        // Has mission been resumed?
    val resumedAt: Long? = null,
    val notes: String? = null
)

/**
 * DAO for mission state operations
 */
@Dao
interface SavedMissionStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissionState(state: SavedMissionStateEntity)

    @Update
    suspend fun updateMissionState(state: SavedMissionStateEntity)

    @Query("SELECT * FROM saved_mission_states WHERE missionId = :missionId")
    suspend fun getMissionState(missionId: String): SavedMissionStateEntity?

    @Query("SELECT * FROM saved_mission_states WHERE isResolved = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestUnresolvedMission(): SavedMissionStateEntity?

    @Query("SELECT * FROM saved_mission_states ORDER BY timestamp DESC")
    suspend fun getAllMissionStates(): List<SavedMissionStateEntity>

    @Query("SELECT * FROM saved_mission_states WHERE isResolved = 0 ORDER BY timestamp DESC")
    suspend fun getUnresolvedMissions(): List<SavedMissionStateEntity>

    @Query("UPDATE saved_mission_states SET isResolved = 1, resumedAt = :resumedAt WHERE missionId = :missionId")
    suspend fun markAsResolved(missionId: String, resumedAt: Long)

    @Query("DELETE FROM saved_mission_states WHERE missionId = :missionId")
    suspend fun deleteMissionState(missionId: String)

    @Query("DELETE FROM saved_mission_states WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMissions(beforeTimestamp: Long)
}

/**
 * Type converters for mission state serialization
 */
class MissionStateTypeConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromLatLngList(value: List<LatLng>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toLatLngList(value: String?): List<LatLng>? {
        return value?.let {
            val type = object : TypeToken<List<LatLng>>() {}.type
            gson.fromJson(it, type)
        }
    }
}

