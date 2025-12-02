package com.example.aerogcsclone.obstacle

import android.util.Log
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.example.aerogcsclone.database.obstacle.SavedMissionStateDao
import com.example.aerogcsclone.database.obstacle.SavedMissionStateEntity
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository for saving and retrieving mission states
 * Handles serialization/deserialization of mission data
 */
class MissionStateRepository(
    private val dao: SavedMissionStateDao
) {
    private val tag = "MissionStateRepo"
    private val gson = Gson()

    /**
     * Save mission state when obstacle is detected
     */
    suspend fun saveMissionState(state: SavedMissionState): Boolean {
        return try {
            val entity = SavedMissionStateEntity(
                missionId = state.missionId,
                interruptedWaypointIndex = state.interruptedWaypointIndex,
                currentDroneLat = state.currentDroneLocation.latitude,
                currentDroneLng = state.currentDroneLocation.longitude,
                homeLat = state.homeLocation.latitude,
                homeLng = state.homeLocation.longitude,
                originalWaypointsJson = serializeWaypoints(state.originalWaypoints),
                remainingWaypointsJson = serializeWaypoints(state.remainingWaypoints),
                obstacleLat = state.obstacleInfo.location?.latitude,
                obstacleLng = state.obstacleInfo.location?.longitude,
                obstacleDistance = state.obstacleInfo.distance,
                obstacleBearing = state.obstacleInfo.bearing,
                obstacleThreatLevel = state.obstacleInfo.threatLevel.name,
                missionProgress = state.missionProgress,
                timestamp = state.timestamp,
                surveyPolygonJson = serializeSurveyPolygon(state.surveyPolygon),
                altitude = state.missionParameters?.altitude,
                speed = state.missionParameters?.speed,
                loiterRadius = state.missionParameters?.loiterRadius,
                rtlAltitude = state.missionParameters?.rtlAltitude,
                descentRate = state.missionParameters?.descentRate,
                isResolved = false
            )

            dao.insertMissionState(entity)
            Log.i(tag, "✅ Mission state saved: ${state.missionId}")
            true
        } catch (e: Exception) {
            Log.e(tag, "❌ Failed to save mission state", e)
            false
        }
    }

    /**
     * Retrieve the latest unresolved mission
     */
    suspend fun getLatestUnresolvedMission(): SavedMissionState? {
        return try {
            val entity = dao.getLatestUnresolvedMission()
            entity?.let { toSavedMissionState(it) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to retrieve mission state", e)
            null
        }
    }

    /**
     * Retrieve specific mission by ID
     */
    suspend fun getMissionState(missionId: String): SavedMissionState? {
        return try {
            val entity = dao.getMissionState(missionId)
            entity?.let { toSavedMissionState(it) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to retrieve mission state $missionId", e)
            null
        }
    }

    /**
     * Mark mission as resolved after successful resume
     */
    suspend fun markAsResolved(missionId: String): Boolean {
        return try {
            dao.markAsResolved(missionId, System.currentTimeMillis())
            Log.i(tag, "✅ Mission marked as resolved: $missionId")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to mark mission as resolved", e)
            false
        }
    }

    /**
     * Get all unresolved missions
     */
    suspend fun getUnresolvedMissions(): List<SavedMissionState> {
        return try {
            dao.getUnresolvedMissions().mapNotNull { toSavedMissionState(it) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to retrieve unresolved missions", e)
            emptyList()
        }
    }

    /**
     * Delete old mission states (older than specified timestamp)
     */
    suspend fun cleanupOldMissions(olderThanMs: Long): Boolean {
        return try {
            dao.deleteOldMissions(olderThanMs)
            Log.i(tag, "✅ Old missions cleaned up")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to cleanup old missions", e)
            false
        }
    }

    /**
     * Serialize waypoints to JSON
     */
    private fun serializeWaypoints(waypoints: List<MissionItemInt>): String {
        return try {
            gson.toJson(waypoints)
        } catch (e: Exception) {
            Log.e(tag, "Failed to serialize waypoints", e)
            "[]"
        }
    }

    /**
     * Deserialize waypoints from JSON
     */
    private fun deserializeWaypoints(json: String): List<MissionItemInt> {
        return try {
            val type = object : TypeToken<List<MissionItemInt>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Failed to deserialize waypoints", e)
            emptyList()
        }
    }

    /**
     * Serialize survey polygon to JSON
     */
    private fun serializeSurveyPolygon(polygon: List<LatLng>?): String? {
        return try {
            polygon?.let { gson.toJson(it) }
        } catch (e: Exception) {
            Log.e(tag, "Failed to serialize survey polygon", e)
            null
        }
    }

    /**
     * Deserialize survey polygon from JSON
     */
    private fun deserializeSurveyPolygon(json: String?): List<LatLng> {
        return try {
            json?.let {
                val type = object : TypeToken<List<LatLng>>() {}.type
                gson.fromJson(it, type) ?: emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Failed to deserialize survey polygon", e)
            emptyList()
        }
    }

    /**
     * Convert entity to domain model
     */
    private fun toSavedMissionState(entity: SavedMissionStateEntity): SavedMissionState {
        return SavedMissionState(
            missionId = entity.missionId,
            interruptedWaypointIndex = entity.interruptedWaypointIndex,
            currentDroneLocation = LatLng(entity.currentDroneLat, entity.currentDroneLng),
            homeLocation = LatLng(entity.homeLat, entity.homeLng),
            originalWaypoints = deserializeWaypoints(entity.originalWaypointsJson),
            remainingWaypoints = deserializeWaypoints(entity.remainingWaypointsJson),
            obstacleInfo = ObstacleInfo(
                distance = entity.obstacleDistance,
                bearing = entity.obstacleBearing,
                location = if (entity.obstacleLat != null && entity.obstacleLng != null) {
                    LatLng(entity.obstacleLat, entity.obstacleLng)
                } else null,
                threatLevel = ThreatLevel.valueOf(entity.obstacleThreatLevel),
                detectionTime = entity.timestamp
            ),
            missionProgress = entity.missionProgress,
            timestamp = entity.timestamp,
            surveyPolygon = deserializeSurveyPolygon(entity.surveyPolygonJson),
            missionParameters = if (entity.altitude != null) {
                MissionParameters(
                    altitude = entity.altitude,
                    speed = entity.speed ?: 12f,
                    loiterRadius = entity.loiterRadius ?: 10f,
                    rtlAltitude = entity.rtlAltitude ?: 60f,
                    descentRate = entity.descentRate ?: 2f
                )
            } else null
        )
    }
}

