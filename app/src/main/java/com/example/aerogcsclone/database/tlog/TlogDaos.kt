package com.example.aerogcsclone.database.tlog

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Telemetry operations
 */
@Dao
interface TelemetryDao {

    @Query("SELECT * FROM telemetry_logs WHERE flightId = :flightId ORDER BY timestamp ASC")
    fun getTelemetryForFlight(flightId: Long): Flow<List<TelemetryEntity>>

    @Query("SELECT * FROM telemetry_logs WHERE flightId = :flightId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getTelemetryInTimeRange(flightId: Long, startTime: Long, endTime: Long): List<TelemetryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(telemetry: TelemetryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetryBatch(telemetryList: List<TelemetryEntity>)

    @Query("DELETE FROM telemetry_logs WHERE flightId = :flightId")
    suspend fun deleteTelemetryForFlight(flightId: Long)

    @Query("SELECT MAX(altitude) FROM telemetry_logs WHERE flightId = :flightId")
    suspend fun getMaxAltitudeForFlight(flightId: Long): Float?

    @Query("SELECT MAX(speed) FROM telemetry_logs WHERE flightId = :flightId")
    suspend fun getMaxSpeedForFlight(flightId: Long): Float?
}

/**
 * Data Access Object for Event operations
 */
@Dao
interface EventDao {

    @Query("SELECT * FROM flight_events WHERE flightId = :flightId ORDER BY timestamp ASC")
    fun getEventsForFlight(flightId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM flight_events WHERE eventType = :eventType ORDER BY timestamp DESC")
    fun getEventsByType(eventType: EventType): Flow<List<EventEntity>>

    @Query("SELECT * FROM flight_events WHERE severity = :severity ORDER BY timestamp DESC")
    fun getEventsBySeverity(severity: EventSeverity): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Query("DELETE FROM flight_events WHERE flightId = :flightId")
    suspend fun deleteEventsForFlight(flightId: Long)
}

/**
 * Data Access Object for Map Data operations
 */
@Dao
interface MapDataDao {

    @Query("SELECT * FROM map_data WHERE flightId = :flightId ORDER BY timestamp ASC")
    fun getMapDataForFlight(flightId: Long): Flow<List<MapDataEntity>>

    @Query("SELECT * FROM map_data WHERE flightId = :flightId AND isWaypoint = 1 ORDER BY waypointIndex ASC")
    suspend fun getWaypointsForFlight(flightId: Long): List<MapDataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapData(mapData: MapDataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapDataBatch(mapDataList: List<MapDataEntity>)

    @Query("DELETE FROM map_data WHERE flightId = :flightId")
    suspend fun deleteMapDataForFlight(flightId: Long)
}
