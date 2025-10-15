package com.example.aerogcsclone.calibration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aerogcsclone.telemetry.SharedViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel for ArduPilot Compass (Magnetometer) Calibration.
 *
 * Implements the ArduPilot-specific protocol:
 * 1. GCS sends MAV_CMD_DO_START_MAG_CAL (42424)
 * 2. Autopilot responds with COMMAND_ACK (MAV_RESULT_ACCEPTED)
 * 3. Autopilot sends STATUSTEXT messages with progress updates (e.g., "progress <5%>")
 * 4. User rotates vehicle on all axes to fill the magnetic field sphere
 * 5. Autopilot sends final STATUSTEXT (success/failure)
 * 6. Reboot required to apply new offsets
 */
class CompassCalibrationViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val _uiState = MutableStateFlow(CompassCalibrationUiState())
    val uiState: StateFlow<CompassCalibrationUiState> = _uiState.asStateFlow()

    private var statusTextListenerJob: Job? = null
    private var progressListenerJob: Job? = null
    private var reportListenerJob: Job? = null

    // Tuning parameters
    private val ackTimeoutMs = 5000L

    init {
        // Observe connection state
        viewModelScope.launch {
            sharedViewModel.isConnected.collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
            }
        }
    }

    /**
     * Start compass calibration using ArduPilot's MAV_CMD_DO_START_MAG_CAL.
     */
    fun startCalibration() {
        if (!_uiState.value.isConnected) {
            _uiState.update {
                it.copy(
                    calibrationState = CompassCalibrationState.Failed("Not connected to drone"),
                    statusText = "Please connect to the drone first"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    calibrationState = CompassCalibrationState.Starting,
                    statusText = "Starting compass calibration...",
                    compassProgress = emptyMap(),
                    overallProgress = 0
                )
            }

            // Start listening to STATUSTEXT messages for progress
            startStatusTextListener()

            // Also listen to MAG_CAL_PROGRESS and MAG_CAL_REPORT if available
            startProgressListener()
            startReportListener()

            try {
                Log.d("CompassCalVM", "Sending MAV_CMD_DO_START_MAG_CAL (42424)")

                // Send MAV_CMD_DO_START_MAG_CAL (ArduPilot-specific command)
                sharedViewModel.sendCalibrationCommandRaw(
                    commandId = 42424u, // MAV_CMD_DO_START_MAG_CAL
                    param1 = 0f, // Bitmask of magnetometers (0 = all)
                    param2 = 0f, // Retry on failure (0 = no retry)
                    param3 = 1f, // Autosave (1 = save automatically)
                    param4 = 0f, // Delay (0 = start immediately)
                    param5 = 0f, // Autoreboot (0 = no auto reboot, user must reboot)
                    param6 = 0f,
                    param7 = 0f
                )

                // Wait for COMMAND_ACK
                val ack = sharedViewModel.awaitCommandAck(42424u, ackTimeoutMs)
                val ackResult = ack?.result?.value
                val ackName = ack?.result?.entry?.name ?: "NO_ACK"

                Log.d("CompassCalVM", "Received ACK: result=$ackName (value=$ackResult)")

                // Check if accepted or in progress
                if (ackResult == 0u || ackResult == 5u) { // ACCEPTED or IN_PROGRESS
                    _uiState.update {
                        it.copy(
                            calibrationState = CompassCalibrationState.InProgress(
                                currentInstruction = "Rotate vehicle slowly - point each side down towards earth"
                            ),
                            statusText = "Calibrating... Rotate vehicle on all axes"
                        )
                    }
                    Log.d("CompassCalVM", "✓ Compass calibration started")
                } else {
                    _uiState.update {
                        it.copy(
                            calibrationState = CompassCalibrationState.Failed("Calibration rejected: $ackName"),
                            statusText = "Failed to start calibration"
                        )
                    }
                    stopAllListeners()
                }

            } catch (e: Exception) {
                Log.e("CompassCalVM", "Failed to start compass calibration", e)
                _uiState.update {
                    it.copy(
                        calibrationState = CompassCalibrationState.Failed("Error: ${e.message}"),
                        statusText = "Error: ${e.message}"
                    )
                }
                stopAllListeners()
            }
        }
    }

    /**
     * Cancel compass calibration using MAV_CMD_DO_CANCEL_MAG_CAL.
     */
    fun cancelCalibration() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    statusText = "Cancelling calibration...",
                    showCancelDialog = false
                )
            }

            try {
                Log.d("CompassCalVM", "Sending MAV_CMD_DO_CANCEL_MAG_CAL (42426)")

                // Send MAV_CMD_DO_CANCEL_MAG_CAL
                sharedViewModel.sendCalibrationCommandRaw(
                    commandId = 42426u, // MAV_CMD_DO_CANCEL_MAG_CAL
                    param1 = 0f, // Bitmask (0 = cancel all)
                    param2 = 0f,
                    param3 = 0f,
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )

                delay(500)

                _uiState.update {
                    it.copy(
                        calibrationState = CompassCalibrationState.Cancelled,
                        statusText = "Calibration cancelled",
                        compassProgress = emptyMap(),
                        overallProgress = 0
                    )
                }

                stopAllListeners()

            } catch (e: Exception) {
                Log.e("CompassCalVM", "Failed to cancel compass calibration", e)
                _uiState.update {
                    it.copy(
                        statusText = "Error cancelling: ${e.message}"
                    )
                }
            }
        }
    }

    fun resetCalibration() {
        stopAllListeners()
        _uiState.update {
            it.copy(
                calibrationState = CompassCalibrationState.Idle,
                statusText = "",
                compassProgress = emptyMap(),
                overallProgress = 0,
                showCancelDialog = false
            )
        }
    }

    fun showCancelDialog(show: Boolean) {
        _uiState.update { it.copy(showCancelDialog = show) }
    }

    /**
     * Start listening to STATUSTEXT messages for progress updates.
     * ArduPilot sends progress as "progress <XX%>" in STATUSTEXT.
     */
    private fun startStatusTextListener() {
        statusTextListenerJob?.cancel()
        statusTextListenerJob = viewModelScope.launch {
            sharedViewModel.calibrationStatus.collect { statusText ->
                statusText?.let { text ->
                    Log.d("CompassCalVM", "STATUSTEXT: $text")

                    // Update status text in UI
                    _uiState.update { state ->
                        state.copy(statusText = text)
                    }

                    val lower = text.lowercase()

                    // Parse progress percentage from STATUSTEXT (e.g., "progress <45%>")
                    val progressRegex = """progress\s*<(\d+)%>""".toRegex()
                    val progressMatch = progressRegex.find(lower)
                    if (progressMatch != null) {
                        val progress = progressMatch.groupValues[1].toIntOrNull() ?: 0
                        _uiState.update {
                            it.copy(
                                overallProgress = progress,
                                statusText = "Calibrating... $progress%"
                            )
                        }
                        Log.d("CompassCalVM", "Progress: $progress%")
                    }

                    // Check for success
                    if (lower.contains("calibration successful") ||
                        lower.contains("mag calibration successful")) {
                        _uiState.update {
                            it.copy(
                                calibrationState = CompassCalibrationState.Success(
                                    message = "Compass calibration completed successfully!",
                                    reportDetails = text
                                ),
                                statusText = "Success! Please reboot the autopilot.",
                                overallProgress = 100
                            )
                        }
                        stopAllListeners()
                    }

                    // Check for failure
                    if (lower.contains("calibration failed") ||
                        lower.contains("mag cal failed")) {
                        _uiState.update {
                            it.copy(
                                calibrationState = CompassCalibrationState.Failed(text),
                                statusText = "Calibration failed - please retry"
                            )
                        }
                        stopAllListeners()
                    }
                }
            }
        }
    }

    /**
     * Listen to MAG_CAL_PROGRESS messages (if autopilot sends them).
     */
    private fun startProgressListener() {
        progressListenerJob?.cancel()
        progressListenerJob = viewModelScope.launch {
            sharedViewModel.magCalProgress.collect { progress ->
                Log.d("CompassCalVM", "MAG_CAL_PROGRESS: compass=${progress.compassId} pct=${progress.completionPct}")

                // Update progress map
                val updatedProgress = _uiState.value.compassProgress.toMutableMap()
                updatedProgress[progress.compassId.toInt()] = progress.completionPct.toInt()

                // Calculate overall progress
                val overallPct = if (updatedProgress.isNotEmpty()) {
                    updatedProgress.values.average().toInt()
                } else {
                    0
                }

                _uiState.update {
                    it.copy(
                        compassProgress = updatedProgress,
                        overallProgress = overallPct,
                        statusText = "Calibrating... ${overallPct}%"
                    )
                }
            }
        }
    }

    /**
     * Listen to MAG_CAL_REPORT messages for final result.
     */
    private fun startReportListener() {
        reportListenerJob?.cancel()
        reportListenerJob = viewModelScope.launch {
            sharedViewModel.magCalReport.collect { report ->
                Log.d("CompassCalVM", "MAG_CAL_REPORT: compass=${report.compassId} status=${report.calStatus.entry?.name}")

                val statusName = report.calStatus.entry?.name ?: "UNKNOWN"

                when {
                    statusName.contains("SUCCESS", ignoreCase = true) -> {
                        val details = "Compass ${report.compassId}: Fitness=${report.fitness}, " +
                                "Offsets=(${report.ofsX}, ${report.ofsY}, ${report.ofsZ})"

                        _uiState.update {
                            it.copy(
                                calibrationState = CompassCalibrationState.Success(
                                    message = "Compass calibration completed successfully!",
                                    reportDetails = details
                                ),
                                statusText = "Success! Please reboot the autopilot.",
                                overallProgress = 100
                            )
                        }
                        stopAllListeners()
                    }
                    statusName.contains("FAIL", ignoreCase = true) -> {
                        _uiState.update {
                            it.copy(
                                calibrationState = CompassCalibrationState.Failed(
                                    "Calibration failed: $statusName"
                                ),
                                statusText = "Calibration failed - please retry"
                            )
                        }
                        stopAllListeners()
                    }
                }
            }
        }
    }

    private fun stopAllListeners() {
        statusTextListenerJob?.cancel()
        statusTextListenerJob = null
        progressListenerJob?.cancel()
        progressListenerJob = null
        reportListenerJob?.cancel()
        reportListenerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAllListeners()
    }
}
