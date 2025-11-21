package com.example.aerogcsclone.manager

import android.content.Context
//import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.example.aerogcsclone.database.tlog.EventType
import com.example.aerogcsclone.database.tlog.EventSeverity
import com.example.aerogcsclone.service.FlightLoggingService
import com.example.aerogcsclone.telemetry.SharedViewModel
import com.example.aerogcsclone.viewmodel.TlogViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Manager class to handle automatic flight logging integration with telemetry
 */
class FlightManager(
    private val context: Context,
    private val tlogViewModel: TlogViewModel,
    private val telemetryViewModel: SharedViewModel
) {
    private var loggingService: FlightLoggingService? = null
    private var previousArmedState: Boolean = false
    private var monitoringJob: Job? = null

    // Manual mission tracking variables (same as TelemetryRepository)
    private var isFlightActive = false
    private var groundLevelAltitude: Float = 0f
    private var hasStartedLogging = false

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.Main).launch {
            telemetryViewModel.telemetryState.collect { telemetryState ->
                handleFlightStateChange(telemetryState)
                handleConnectionStateChange(telemetryState)
                handleLowBatteryWarning(telemetryState)
            }
        }
    }

    private suspend fun handleFlightStateChange(telemetryState: TelemetryState) {
        val currentArmedState = telemetryState.armed
        val relAltM = telemetryState.altitudeRelative ?: 0f
        val currentSpeed = telemetryState.groundspeed ?: 0f

        // Capture ground level when drone arms
        if (currentArmedState && !previousArmedState) {
            groundLevelAltitude = relAltM
            android.util.Log.i("FlightManager", "[Manual Mission] Drone armed - ground level: ${groundLevelAltitude}m")
        }

        // Start logging: armed AND altitude > ground + 1m
        val takeoffThreshold = groundLevelAltitude + 1f
        if (currentArmedState && !isFlightActive && relAltM > takeoffThreshold && !hasStartedLogging) {
            android.util.Log.i("FlightManager", "[Manual Mission] ✅ Starting logging - altitude ${relAltM}m > threshold ${takeoffThreshold}m")
            isFlightActive = true
            hasStartedLogging = true
            startFlight()
        }

        // Stop logging: altitude near ground OR speed = 0 OR disarmed
        val landingAltitudeThreshold = groundLevelAltitude + 0.5f
        val hasLanded = relAltM <= landingAltitudeThreshold
        val hasStoppedMoving = currentSpeed < 0.1f

        if (isFlightActive && (hasLanded || !currentArmedState || hasStoppedMoving)) {
            val reason = when {
                !currentArmedState -> "Disarmed"
                hasLanded -> "Landed (altitude: ${relAltM}m ≤ ${landingAltitudeThreshold}m)"
                hasStoppedMoving -> "Stopped (speed: ${currentSpeed}m/s)"
                else -> "Unknown"
            }
            android.util.Log.i("FlightManager", "[Manual Mission] ✅ Stopping logging - Reason: $reason")
            isFlightActive = false
            hasStartedLogging = false
            groundLevelAltitude = 0f
            endFlight()
        }

        // Update previous armed state
        previousArmedState = currentArmedState
    }

    private suspend fun handleConnectionStateChange(telemetryState: TelemetryState) {
        if (!telemetryState.connected && isFlightActive) {
            // Log connection loss event if there's an active flight
            tlogViewModel.logEvent(
                eventType = EventType.CONNECTION_LOSS,
                severity = EventSeverity.WARNING,
                message = "Connection to drone lost"
            )
        }
    }

    private suspend fun handleLowBatteryWarning(telemetryState: TelemetryState) {
        // Only log battery warnings during active flight
        if (!isFlightActive) return

        telemetryState.batteryPercent?.let { batteryPercent ->
            if (batteryPercent <= 20 && batteryPercent > 15) {
                tlogViewModel.logEvent(
                    eventType = EventType.LOW_BATTERY,
                    severity = EventSeverity.WARNING,
                    message = "Low battery warning: ${batteryPercent}%"
                )
            } else if (batteryPercent <= 15) {
                tlogViewModel.logEvent(
                    eventType = EventType.LOW_BATTERY,
                    severity = EventSeverity.CRITICAL,
                    message = "Critical battery level: ${batteryPercent}%"
                )
            }
        }
    }

    private suspend fun startFlight() {
        try {
            tlogViewModel.startFlight()

            // Start the logging service for telemetry data every 5 seconds
            loggingService = FlightLoggingService(tlogViewModel)
            loggingService?.startLogging(telemetryViewModel.telemetryState)

            android.util.Log.i("FlightManager", "Flight logging started")

        } catch (e: Exception) {
            android.util.Log.e("FlightManager", "Error starting flight", e)
        }
    }

    private suspend fun endFlight() {
        try {
            // Stop logging service
            loggingService?.stopLogging()
            loggingService = null

            // End the flight with calculated area (you can implement area calculation later)
            tlogViewModel.endFlight()

            android.util.Log.i("FlightManager", "Flight logging stopped")

        } catch (e: Exception) {
            android.util.Log.e("FlightManager", "Error ending flight", e)
        }
    }

    fun destroy() {
        monitoringJob?.cancel()
        loggingService?.stopLogging()
    }
}
