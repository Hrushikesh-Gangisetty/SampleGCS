package com.example.aerogcsclone.database.tlog

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

/**
 * Entity for storing GPS coordinates for flight path replay and analysis
 */
@Entity(
    tableName = "map_data",
    foreignKeys = [
        ForeignKey(
            entity = FlightEntity::class,
            parentColumns = ["id"],
            childColumns = ["flightId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MapDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val flightId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Float,
    val heading: Float? = null,
    val speed: Float? = null,
    val isWaypoint: Boolean = false, // true if this is a planned waypoint
    val waypointIndex: Int? = null // index of waypoint if applicable
)
