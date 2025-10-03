package com.example.aerogcsclone.manager

import android.content.Context
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.example.aerogcsclone.database.tlog.EventType
import com.example.aerogcsclone.database.tlog.EventSeverity
import com.example.aerogcsclone.service.FlightLoggingService
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

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        monitoringJob = CoroutineScope(Dispatchers.Main).launch {
            telemetryViewModel.telemetryState.collect { telemetryState ->
                handleArmedStateChange(telemetryState)
                handleConnectionStateChange(telemetryState)
                handleLowBatteryWarning(telemetryState)
            }
        }
    }

    private suspend fun handleArmedStateChange(telemetryState: TelemetryState) {
        val currentArmedState = telemetryState.armed

        // Check for armed state change
        if (currentArmedState != previousArmedState) {
            if (currentArmedState) {
                // Drone was armed - start flight
                startFlight()
            } else {
                // Drone was disarmed - end flight
                endFlight()
            }
            previousArmedState = currentArmedState
        }
    }

    private suspend fun handleConnectionStateChange(telemetryState: TelemetryState) {
        if (!telemetryState.connected) {
            // Log connection loss event if there's an active flight
            tlogViewModel.logEvent(
                eventType = EventType.CONNECTION_LOSS,
                severity = EventSeverity.WARNING,
                message = "Connection to drone lost"
            )
        }
    }

    private suspend fun handleLowBatteryWarning(telemetryState: TelemetryState) {
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

        } catch (e: Exception) {
            // Handle error starting flight
        }
    }

    private suspend fun endFlight() {
        try {
            // Stop logging service
            loggingService?.stopLogging()
            loggingService = null

            // End the flight with calculated area (you can implement area calculation later)
            tlogViewModel.endFlight()

        } catch (e: Exception) {
            // Handle error ending flight
        }
    }

    fun destroy() {
        monitoringJob?.cancel()
        loggingService?.stopLogging()
    }
}
