package com.example.aerogcsclone.calibration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aerogcsclone.telemetry.SharedViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

class CompassCalibrationViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val _uiState = MutableStateFlow(CompassCalibrationUiState())
    val uiState: StateFlow<CompassCalibrationUiState> = _uiState.asStateFlow()

    private var progressListenerJob: Job? = null
    private var reportListenerJob: Job? = null

    init {
        // Observe connection state from SharedViewModel
        viewModelScope.launch {
            sharedViewModel.isConnected.collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
            }
        }
    }

    /**
     * Start the compass calibration process.
     * Sends MAV_CMD_DO_START_MAG_CAL command and starts monitoring progress.
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

            // Start listening to MAG_CAL_PROGRESS and MAG_CAL_REPORT messages
            startProgressListener()
            startReportListener()

            try {
                // Send MAV_CMD_DO_START_MAG_CAL (command ID 42424)
                // param1: MagMask (0 = all compasses)
                // param2: Retry (0 = no retry)
                // param3: Autosave (1 = save parameters automatically)
                // param4: Delay (0 = start immediately)
                // param5: AutoReboot (0 = no auto reboot)
                // param6: Fitness (0 = default fitness level)
                sharedViewModel.sendCalibrationCommandRaw(
                    commandId = 42424u, // MAV_CMD_DO_START_MAG_CAL
                    param1 = 0f, // Calibrate all compasses
                    param2 = 0f, // No retry
                    param3 = 1f, // Autosave
                    param4 = 0f, // No delay
                    param5 = 0f, // No auto-reboot
                    param6 = 0f  // Default fitness
                )

                Log.d("CompassCalVM", "Sent MAV_CMD_DO_START_MAG_CAL command")

                // Wait for command acknowledgment
                delay(1000)

                var ackReceived = false
                val commandAckJob = viewModelScope.launch {
                    sharedViewModel.commandAck
                        .filter { it.command.value == 42424u }
                        .firstOrNull()
                        ?.let { ack ->
                            ackReceived = true
                            Log.d("CompassCalVM", "COMMAND_ACK for MAG_CAL_START: result=${ack.result.entry?.name ?: ack.result.value}")

                            if (ack.result.value == 0u) { // ACCEPTED
                                Log.d("CompassCalVM", "✓ Compass calibration started successfully")
                                _uiState.update {
                                    it.copy(
                                        calibrationState = CompassCalibrationState.InProgress(
                                            currentInstruction = "Rotate vehicle slowly - point each side down towards earth"
                                        ),
                                        statusText = "Calibration in progress..."
                                    )
                                }
                            } else {
                                Log.e("CompassCalVM", "✗ Compass calibration start FAILED: ${ack.result.entry?.name ?: ack.result.value}")
                                _uiState.update {
                                    it.copy(
                                        calibrationState = CompassCalibrationState.Failed("Calibration start failed: ${ack.result.entry?.name ?: "Unknown error"}"),
                                        statusText = "Failed to start calibration"
                                    )
                                }
                                stopListeners()
                            }
                        }
                }

                delay(3000)
                commandAckJob.cancel()

                if (!ackReceived && _uiState.value.calibrationState is CompassCalibrationState.Starting) {
                    Log.w("CompassCalVM", "No COMMAND_ACK received for MAG_CAL_START within timeout")
                    // Assume success and proceed (some autopilots may not send ACK immediately)
                    _uiState.update {
                        it.copy(
                            calibrationState = CompassCalibrationState.InProgress(
                                currentInstruction = "Rotate vehicle slowly - point each side down towards earth"
                            ),
                            statusText = "Calibration in progress..."
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("CompassCalVM", "Failed to start compass calibration", e)
                _uiState.update {
                    it.copy(
                        calibrationState = CompassCalibrationState.Failed("Failed to start calibration: ${e.message}"),
                        statusText = "Error: ${e.message}"
                    )
                }
                stopListeners()
            }
        }
    }

    /**
     * Cancel the compass calibration process.
     * Sends MAV_CMD_DO_CANCEL_MAG_CAL command.
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
                // Send MAV_CMD_DO_CANCEL_MAG_CAL (command ID 42425)
                sharedViewModel.sendCalibrationCommandRaw(
                    commandId = 42425u, // MAV_CMD_DO_CANCEL_MAG_CAL
                    param1 = 0f,
                    param2 = 0f,
                    param3 = 0f,
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )

                Log.d("CompassCalVM", "Sent MAV_CMD_DO_CANCEL_MAG_CAL command")

                delay(500)

                _uiState.update {
                    it.copy(
                        calibrationState = CompassCalibrationState.Cancelled,
                        statusText = "Calibration cancelled",
                        compassProgress = emptyMap(),
                        overallProgress = 0
                    )
                }

                stopListeners()

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

    /**
     * Reset the calibration state to idle.
     */
    fun resetCalibration() {
        stopListeners()
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
     * Start listening for MAG_CAL_PROGRESS messages.
     */
    private fun startProgressListener() {
        progressListenerJob?.cancel()
        progressListenerJob = viewModelScope.launch {
            sharedViewModel.magCalProgress.collect { progress ->
                Log.d("CompassCalVM", "MAG_CAL_PROGRESS: compass=${progress.compassId} status=${progress.calStatus.entry?.name} pct=${progress.completionPct}")

                // Update progress map with latest values
                val updatedProgress = _uiState.value.compassProgress.toMutableMap()
                updatedProgress[progress.compassId.toInt()] = progress.completionPct.toInt()

                // Calculate overall progress (average of all compasses)
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
     * Start listening for MAG_CAL_REPORT messages (final result).
     */
    private fun startReportListener() {
        reportListenerJob?.cancel()
        reportListenerJob = viewModelScope.launch {
            sharedViewModel.magCalReport.collect { report ->
                Log.d("CompassCalVM", "MAG_CAL_REPORT: compass=${report.compassId} status=${report.calStatus.entry?.name} fitness=${report.fitness}")

                // Check calibration status
                val statusName = report.calStatus.entry?.name ?: "UNKNOWN"

                when {
                    statusName.contains("SUCCESS", ignoreCase = true) || statusName == "MAG_CAL_SUCCESS" -> {
                        val details = "Compass ${report.compassId}: Fitness=${report.fitness}, " +
                                "Offsets=(${report.ofsX}, ${report.ofsY}, ${report.ofsZ})"

                        _uiState.update {
                            it.copy(
                                calibrationState = CompassCalibrationState.Success(
                                    message = "Compass calibration completed successfully!",
                                    reportDetails = details
                                ),
                                statusText = "Success - Please reboot the autopilot",
                                overallProgress = 100
                            )
                        }
                        stopListeners()
                    }
                    statusName.contains("FAIL", ignoreCase = true) || statusName == "MAG_CAL_FAILED" -> {
                        _uiState.update {
                            it.copy(
                                calibrationState = CompassCalibrationState.Failed(
                                    "Calibration failed: $statusName"
                                ),
                                statusText = "Calibration failed - please retry"
                            )
                        }
                        stopListeners()
                    }
                    else -> {
                        Log.d("CompassCalVM", "MAG_CAL_REPORT with status: $statusName (continuing)")
                    }
                }
            }
        }
    }

    /**
     * Stop all listeners.
     */
    private fun stopListeners() {
        progressListenerJob?.cancel()
        progressListenerJob = null
        reportListenerJob?.cancel()
        reportListenerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListeners()
    }
}

