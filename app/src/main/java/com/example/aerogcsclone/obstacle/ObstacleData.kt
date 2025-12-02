package com.example.aerogcsclone.obstacle

import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.google.android.gms.maps.model.LatLng

/**
 * Threat level classification for detected obstacles
 */
enum class ThreatLevel {
    NONE,       // No obstacle detected
    LOW,        // 20-50 meters - warning
    MEDIUM,     // 10-20 meters - caution
    HIGH        // <10 meters - emergency (trigger RTL)
}

/**
 * Represents a detected obstacle
 */
data class ObstacleInfo(
    val distance: Float,                    // Distance to obstacle in meters
    val bearing: Float? = null,             // Direction to obstacle (degrees)
    val elevation: Float? = null,           // Elevation angle to obstacle (degrees)
    val location: LatLng? = null,           // Calculated GPS location of obstacle
    val threatLevel: ThreatLevel,           // Classification of threat
    val detectionTime: Long = System.currentTimeMillis(),  // Timestamp
    val consecutiveDetections: Int = 0      // Number of consecutive HIGH detections
)

/**
 * Mission state saved when obstacle is detected
 */
data class SavedMissionState(
    val missionId: String = java.util.UUID.randomUUID().toString(),
    val interruptedWaypointIndex: Int,      // Waypoint where obstacle detected
    val currentDroneLocation: LatLng,       // Drone GPS position at detection
    val homeLocation: LatLng,               // Home GPS coordinates
    val originalWaypoints: List<MissionItemInt>,  // Complete original mission
    val remainingWaypoints: List<MissionItemInt>, // Waypoints not yet visited
    val obstacleInfo: ObstacleInfo,         // Details of detected obstacle
    val missionProgress: Float,             // Percentage complete (0-100)
    val timestamp: Long = System.currentTimeMillis(),
    val surveyPolygon: List<LatLng> = emptyList(),  // Original survey area
    val missionParameters: MissionParameters? = null
)

/**
 * Parameters from original mission to preserve
 */
data class MissionParameters(
    val altitude: Float = 30f,              // Mission altitude in meters
    val speed: Float = 12f,                 // Flight speed in m/s
    val loiterRadius: Float = 10f,          // Loiter radius in meters
    val rtlAltitude: Float = 60f,           // RTL altitude in meters
    val descentRate: Float = 2f             // Landing descent rate in m/s
)

/**
 * Resume options for user to select
 */
data class ResumeOption(
    val waypointIndex: Int,                 // Index in original mission
    val waypoint: MissionItemInt,           // The waypoint to resume from
    val location: LatLng,                   // GPS coordinates
    val distanceFromObstacle: Float,        // Distance from obstacle area (meters)
    val coveragePercentage: Float,          // Survey coverage if selected (0-100)
    val skippedWaypoints: List<Int>,        // Waypoint indices that will be skipped
    val isRecommended: Boolean = false      // Recommended option
)

/**
 * Status of obstacle detection system
 */
enum class ObstacleDetectionStatus {
    INACTIVE,           // System not running
    MONITORING,         // Actively monitoring for obstacles
    OBSTACLE_DETECTED,  // Obstacle found, waiting for RTL
    RTL_IN_PROGRESS,    // Drone returning home
    READY_TO_RESUME,    // Landed, user can resume mission
    RESUMING            // New mission being executed
}

/**
 * Sensor reading from obstacle detection sensor
 */
data class SensorReading(
    val distance: Float,                    // Distance reading in meters
    val bearing: Float? = null,             // Direction (degrees)
    val elevation: Float? = null,           // Elevation angle (degrees)
    val quality: Float = 1.0f,              // Reading quality (0-1)
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Configuration for obstacle detection system
 */
data class ObstacleDetectionConfig(
    val minDetectionRange: Float = 0f,          // Minimum detection range (meters)
    val maxDetectionRange: Float = 50f,         // Maximum detection range (meters)
    val lowThreatThreshold: Float = 20f,        // LOW threat starts at 20m
    val mediumThreatThreshold: Float = 10f,     // MEDIUM threat starts at 10m
    val highThreatThreshold: Float = 10f,       // HIGH threat below 10m
    val detectionIntervalMs: Long = 100,        // Check every 100ms
    val minimumConsecutiveDetections: Int = 3,  // Require 3 consecutive HIGH detections
    val angleToleranceDegrees: Float = 30f,     // ±30° to consider obstacle "in front"
    val sensorType: SensorType = SensorType.PROXIMITY,
    val enableAutoRTL: Boolean = true           // Auto-trigger RTL on HIGH threat
)

/**
 * Types of sensors available
 */
enum class SensorType {
    PROXIMITY,      // Android proximity sensor
    LIDAR,          // LIDAR sensor (USB/serial)
    ULTRASONIC,     // Ultrasonic rangefinder
    SIMULATED       // Simulated sensor for testing
}

/**
 * RTL monitoring state
 */
data class RTLMonitoringState(
    val isActive: Boolean = false,
    val initialDistance: Float? = null,         // Initial distance from home
    val currentDistance: Float? = null,         // Current distance from home
    val consecutiveArrivalChecks: Int = 0,      // Counter for arrival confirmation
    val arrivalThresholdMeters: Float = 5f      // Consider arrived when < 5m
)

/**
 * Mission statistics for logging
 */
data class MissionStatistics(
    val totalFlightTime: Long = 0,              // Total flight time in milliseconds
    val totalDistance: Float = 0f,              // Total distance traveled in meters
    val averageAltitude: Float = 0f,            // Average altitude in meters
    val batteryUsed: Float = 0f,                // Battery percentage used
    val coveragePercentage: Float = 0f,         // Mission coverage achieved
    val obstaclesDetected: Int = 0,             // Number of obstacles detected
    val missionInterrupts: Int = 0,             // Number of interruptions
    val missionResumes: Int = 0,                // Number of resumes
    val finalStatus: MissionStatus = MissionStatus.IN_PROGRESS
)

/**
 * Overall mission status
 */
enum class MissionStatus {
    NOT_STARTED,
    IN_PROGRESS,
    INTERRUPTED,
    COMPLETED,
    PARTIAL_COMPLETE,
    FAILED,
    CANCELLED
}

