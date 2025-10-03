package com.example.aerogcsclone.repository

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
    private val flightDao = database.flightDao()
    private val telemetryDao = database.telemetryDao()
    private val eventDao = database.eventDao()
    private val mapDataDao = database.mapDataDao()

    // Flight operations
    fun getAllFlights(): Flow<List<FlightEntity>> = flightDao.getAllFlights()

    suspend fun getFlightById(flightId: Long): FlightEntity? = flightDao.getFlightById(flightId)

    suspend fun getActiveFlightOrNull(): FlightEntity? = flightDao.getActiveFlightOrNull()

    suspend fun startFlight(): Long {
        val flight = FlightEntity(
            startTime = System.currentTimeMillis(),
            isCompleted = false
        )
        return flightDao.insertFlight(flight)
    }

    suspend fun completeFlight(flightId: Long, area: Float? = null, consumedLiquid: Float? = null) {
        val endTime = System.currentTimeMillis()
        val flight = flightDao.getFlightById(flightId)
        flight?.let {
            val duration = endTime - it.startTime
            flightDao.completeFlight(flightId, endTime, duration, area, consumedLiquid)
        }
    }

    suspend fun deleteFlight(flightId: Long) {
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
        speed: Float? = null,
        isWaypoint: Boolean = false,
        waypointIndex: Int? = null
    ) {
        val mapData = MapDataEntity(
            flightId = flightId,
            timestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            heading = heading,
            speed = speed,
            isWaypoint = isWaypoint,
            waypointIndex = waypointIndex
        )
        mapDataDao.insertMapData(mapData)
    }

    // Statistics
    suspend fun getTotalFlightsCount(): Int = flightDao.getTotalFlightsCount()
    suspend fun getTotalFlightTime(): Long = flightDao.getTotalFlightTime() ?: 0L
}
