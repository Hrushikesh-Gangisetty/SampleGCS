package com.example.aerogcsclone.database.tlog

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

/**
 * Entity for storing telemetry data every 5 seconds during flight
 */
@Entity(
    tableName = "telemetry_logs",
    foreignKeys = [
        ForeignKey(
            entity = FlightEntity::class,
            parentColumns = ["id"],
            childColumns = ["flightId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val flightId: Long,
    val timestamp: Long,
    val voltage: Float?,
    val current: Float?,
    val batteryPercent: Int?,
    val satCount: Int?,
    val hdop: Float?,
    val altitude: Float?, // in meters
    val speed: Float?, // in m/s
    val latitude: Double?,
    val longitude: Double?,
    val heading: Float?, // in degrees
    val pitchAngle: Float?,
    val rollAngle: Float?,
    val yawAngle: Float?
)
