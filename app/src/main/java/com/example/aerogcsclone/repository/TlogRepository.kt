package com.example.aerogcsclone.repository

import android.util.Log
import com.example.aerogcsclone.database.MissionTemplateDatabase
import com.example.aerogcsclone.database.tlog.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing flight logs and telemetry data
 */
@Singleton
class TlogRepository @Inject constructor(
    private val database: MissionTemplateDatabase
) {
    private val TAG = "TlogRepository"
    private val flightDao = database.flightDao()
    private val telemetryDao = database.telemetryDao()
    private val eventDao = database.eventDao()
    private val mapDataDao = database.mapDataDao()

    // Flight operations
    fun getAllFlights(): Flow<List<FlightEntity>> = flightDao.getAllFlights()

    suspend fun getFlightById(flightId: Long): FlightEntity? = flightDao.getFlightById(flightId)

    suspend fun getActiveFlightOrNull(): FlightEntity? = flightDao.getActiveFlightOrNull()

    suspend fun startFlight(): Long {
        Log.d(TAG, "📝 Creating new flight entry...")
        val flight = FlightEntity(
            startTime = System.currentTimeMillis(),
            isCompleted = false
        )
        val flightId = flightDao.insertFlight(flight)
        Log.i(TAG, "✅ Flight entry created in database - ID: $flightId")
        return flightId
    }

    suspend fun completeFlight(flightId: Long, area: Float? = null, consumedLiquid: Float? = null) {
        Log.d(TAG, "📝 Completing flight $flightId...")
        val endTime = System.currentTimeMillis()
        val flight = flightDao.getFlightById(flightId)
        flight?.let {
            val duration = endTime - it.startTime
            flightDao.completeFlight(flightId, endTime, duration, area, consumedLiquid)
            Log.i(TAG, "✅ Flight $flightId completed - Duration: ${duration/1000}s")
        } ?: run {
            Log.e(TAG, "❌ Flight $flightId not found in database!")
        }
    }

    suspend fun deleteFlight(flightId: Long) {
        Log.d(TAG, "🗑️ Deleting flight $flightId")
        flightDao.deleteFlightById(flightId)
    }

    // Telemetry operations
    fun getTelemetryForFlight(flightId: Long): Flow<List<TelemetryEntity>> =
        telemetryDao.getTelemetryForFlight(flightId)

    suspend fun logTelemetry(
        flightId: Long,
        voltage: Float?,
        current: Float?,
        batteryPercent: Int?,
        satCount: Int?,
        hdop: Float?,
        altitude: Float?,
        speed: Float?,
        latitude: Double?,
        longitude: Double?,
        heading: Float? = null,
        pitchAngle: Float? = null,
        rollAngle: Float? = null,
        yawAngle: Float? = null
    ) {
        try {
            val telemetry = TelemetryEntity(
                flightId = flightId,
                timestamp = System.currentTimeMillis(),
                voltage = voltage,
                current = current,
                batteryPercent = batteryPercent,
                satCount = satCount,
                hdop = hdop,
                altitude = altitude,
                speed = speed,
                latitude = latitude,
                longitude = longitude,
                heading = heading,
                pitchAngle = pitchAngle,
                rollAngle = rollAngle,
                yawAngle = yawAngle
            )
            telemetryDao.insertTelemetry(telemetry)
            // Only log occasionally to avoid spam
            if (System.currentTimeMillis() % 30000 < 5000) {
                Log.v(TAG, "📊 Telemetry saved for flight $flightId (alt=$altitude, speed=$speed)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save telemetry for flight $flightId", e)
        }
    }

    // Event operations
    fun getEventsForFlight(flightId: Long): Flow<List<EventEntity>> =
        eventDao.getEventsForFlight(flightId)

    suspend fun logEvent(
        flightId: Long,
        eventType: EventType,
        severity: EventSeverity,
        message: String,
        additionalData: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        altitude: Float? = null
    ) {
        try {
            Log.d(TAG, "📝 Logging event for flight $flightId: $message")
            val event = EventEntity(
                flightId = flightId,
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                severity = severity,
                message = message,
                additionalData = additionalData,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude
            )
            eventDao.insertEvent(event)
            Log.i(TAG, "✅ Event saved: $message")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save event for flight $flightId: $message", e)
        }
    }

    // Map data operations
    fun getMapDataForFlight(flightId: Long): Flow<List<MapDataEntity>> =
        mapDataDao.getMapDataForFlight(flightId)

    suspend fun logMapData(
        flightId: Long,
        latitude: Double,
        longitude: Double,
        altitude: Float,
        heading: Float? = null,
        speed: Float? = null
    ) {
        try {
            val mapData = MapDataEntity(
                flightId = flightId,
                timestamp = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                heading = heading,
                speed = speed
            )
            mapDataDao.insertMapData(mapData)
            // Only log occasionally
            if (System.currentTimeMillis() % 30000 < 5000) {
                Log.v(TAG, "🗺️ Map position saved for flight $flightId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save map data for flight $flightId", e)
        }
    }

    // Statistics
    suspend fun getTotalFlightsCount(): Int = flightDao.getTotalFlightsCount()

    suspend fun getTotalFlightTime(): Long = flightDao.getTotalFlightTime() ?: 0L
}
