package com.example.aerogcsclone.database.tlog

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a complete flight session
 */
@Entity(tableName = "flights")
data class FlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val flightDuration: Long? = null, // in milliseconds
    val area: Float? = null, // calculated area covered
    val consumedLiquid: Float? = null, // liquid consumed during flight
    val maxAltitude: Float? = null,
    val maxSpeed: Float? = null,
    val totalDistance: Float? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
