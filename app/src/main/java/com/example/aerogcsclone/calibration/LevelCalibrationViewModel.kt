package com.example.aerogcsclone.calibration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MavCmd
import com.example.aerogcsclone.telemetry.SharedViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

/**
 * ViewModel for Level Horizon Calibration - MissionPlanner Protocol
 *
 * Protocol Flow (replicating MissionPlanner):
 *
 * 1. User clicks "Start Level Calibration"
 *    - Send MAV_CMD_PREFLIGHT_CALIBRATION with param5=2 (level horizon calibration)
 *    - Wait for COMMAND_ACK response (timeout: 2000ms, up to 3 retries)
 *
 * 2. Handle ACK response:
 *    - ACCEPTED (0): Continue to monitor STATUSTEXT
 *    - DENIED/UNSUPPORTED: Show error and stop
 *    - Timeout: Retry (up to 3 times)
 *
 * 3. Monitor STATUSTEXT messages for completion:
 *    - "Calibration successful" → Success
 *    - "Calibration failed" → Failed
 *
 * 4. Update UI with final result
 */
class LevelCalibrationViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val _uiState = MutableStateFlow(LevelCalibrationUiState())
    val uiState: StateFlow<LevelCalibrationUiState> = _uiState.asStateFlow()

    private var _inCalibration = false
    private var statusTextCollectorJob: Job? = null

    // MissionPlanner Protocol Constants
    private val ACK_TIMEOUT_MS = 2000L  // 2 seconds as per MissionPlanner
    private val MAX_RETRIES = 3         // Up to 3 retries
    private val STATUSTEXT_TIMEOUT_MS = 10000L  // 10 seconds for final result

    init {
        // Observe connection state
        viewModelScope.launch {
            sharedViewModel.isConnected.collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
            }
        }
    }

    /**
     * Start the Level Horizon calibration process
     */
    fun startCalibration() {
        if (!_uiState.value.isConnected) {
            _uiState.update {
                it.copy(
                    calibrationState = LevelCalibrationState.Failed("Not connected to drone"),
                    statusText = "Please connect to the drone first"
                )
            }
            return
        }

        viewModelScope.launch {
            // Reset any "spoken once" keys so announcements for this calibration run will play
            sharedViewModel.resetTtsSpokenKeys()

            // Announce calibration started via TTS
            sharedViewModel.announceCalibrationStarted()

            _uiState.update {
                it.copy(
                    calibrationState = LevelCalibrationState.Initiating,
                    statusText = "Starting level calibration...",
                    buttonText = "Calibrating..."
                )
            }

            _inCalibration = true

            try {
                // STEP 1: Send PREFLIGHT_CALIBRATION command with retry logic
                var ackReceived = false
                var lastAckResult: UInt? = null
                var attempt = 0

                while (attempt < MAX_RETRIES && !ackReceived) {
                    attempt++
                    Log.d("LevelCalVM", "Sending MAV_CMD_PREFLIGHT_CALIBRATION (param5=2) - Attempt $attempt/$MAX_RETRIES")

                    // Send PREFLIGHT_CALIBRATION command with param5=2 for level horizon
                    sharedViewModel.sendCalibrationCommand(
                        command = MavCmd.PREFLIGHT_CALIBRATION,
                        param1 = 0f, // Gyro
                        param2 = 0f, // Magnetometer
                        param3 = 0f, // Ground pressure
                        param4 = 0f, // Radio
                        param5 = 2f, // Level Horizon - param5=2 ← KEY PARAMETER
                        param6 = 0f, // Compass/Motor
                        param7 = 0f  // Airspeed
                    )

                    // STEP 2: Wait for COMMAND_ACK (as per MissionPlanner protocol)
                    Log.d("LevelCalVM", "Waiting for COMMAND_ACK (timeout: ${ACK_TIMEOUT_MS}ms)...")

                    val ack = sharedViewModel.awaitCommandAck(241u, ACK_TIMEOUT_MS) // 241 = PREFLIGHT_CALIBRATION

                    if (ack != null) {
                        lastAckResult = ack.result.value
                        val resultName = ack.result.entry?.name ?: "UNKNOWN"
                        Log.d("LevelCalVM", "Received COMMAND_ACK: $resultName (${ack.result.value})")

                        when (ack.result.value) {
                            0u -> { // MAV_RESULT_ACCEPTED
                                Log.d("LevelCalVM", "✓ Command ACCEPTED by autopilot")
                                ackReceived = true
                            }
                            1u -> { // MAV_RESULT_TEMPORARILY_REJECTED
                                Log.w("LevelCalVM", "⚠ Command TEMPORARILY_REJECTED, retrying...")
                                if (attempt < MAX_RETRIES) {
                                    delay(500) // Short delay before retry
                                }
                            }
                            2u -> { // MAV_RESULT_DENIED
                                Log.e("LevelCalVM", "✗ Command DENIED by autopilot")
                                stopCalibration()
                                _uiState.update {
                                    it.copy(
                                        calibrationState = LevelCalibrationState.Failed("Command denied by autopilot"),
                                        statusText = "The vehicle denied the calibration command",
                                        buttonText = "Start Level Calibration"
                                    )
                                }
                                return@launch
                            }
                            3u -> { // MAV_RESULT_UNSUPPORTED
                                Log.e("LevelCalVM", "✗ Command UNSUPPORTED by autopilot")
                                stopCalibration()
                                _uiState.update {
                                    it.copy(
                                        calibrationState = LevelCalibrationState.Failed("Level calibration not supported by vehicle"),
                                        statusText = "Your vehicle firmware may not support this feature",
                                        buttonText = "Start Level Calibration"
                                    )
                                }
                                return@launch
                            }
                            4u -> { // MAV_RESULT_IN_PROGRESS
                                Log.d("LevelCalVM", "Command IN_PROGRESS")
                                ackReceived = true
                            }
                            else -> {
                                Log.w("LevelCalVM", "Unexpected ACK result: $resultName (${ack.result.value})")
                            }
                        }
                    } else {
                        Log.w("LevelCalVM", "⏱ ACK timeout on attempt $attempt/$MAX_RETRIES")
                        if (attempt < MAX_RETRIES) {
                            delay(500) // Short delay before retry
                        }
                    }
                }

                // Check if we got ACK after retries
                if (!ackReceived) {
                    Log.e("LevelCalVM", "✗ Failed to receive ACK after $MAX_RETRIES attempts")
                    stopCalibration()
                    _uiState.update {
                        it.copy(
                            calibrationState = LevelCalibrationState.Failed("No response from vehicle after $MAX_RETRIES attempts"),
                            statusText = "Please check connection and try again",
                            buttonText = "Start Level Calibration"
                        )
                    }
                    return@launch
                }

                // STEP 3: ACK received successfully, now monitor STATUSTEXT for completion
                Log.d("LevelCalVM", "Command accepted, monitoring STATUSTEXT for completion...")

                _uiState.update {
                    it.copy(
                        calibrationState = LevelCalibrationState.InProgress,
                        statusText = "Measuring vehicle attitude... Please keep the vehicle level and still."
                    )
                }

                // Start listening to STATUSTEXT messages
                startMessageListener()

                // Set a timeout for final result
                launch {
                    delay(STATUSTEXT_TIMEOUT_MS)
                    if (_inCalibration && _uiState.value.calibrationState is LevelCalibrationState.InProgress) {
                        Log.w("LevelCalVM", "⏱ STATUSTEXT timeout - no completion message received")
                        stopCalibration()
                        _uiState.update {
                            it.copy(
                                calibrationState = LevelCalibrationState.Failed("Calibration timed out waiting for result"),
                                statusText = "No completion message received from vehicle",
                                buttonText = "Start Level Calibration"
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("LevelCalVM", "Failed to send level calibration command", e)
                stopCalibration()
                _uiState.update {
                    it.copy(
                        calibrationState = LevelCalibrationState.Failed("Failed to start calibration: ${e.message}"),
                        statusText = "Communication error",
                        buttonText = "Start Level Calibration"
                    )
                }
            }
        }
    }

    /**
     * Cancel the calibration process
     */
    fun cancelCalibration() {
        viewModelScope.launch {
            Log.d("LevelCalVM", "User cancelled calibration")
            stopCalibration()
            _uiState.update {
                it.copy(
                    calibrationState = LevelCalibrationState.Cancelled,
                    statusText = "Calibration cancelled by user",
                    buttonText = "Start Level Calibration",
                    showCancelDialog = false
                )
            }
        }
    }

    /**
     * Reset calibration to start a new one
     */
    fun resetCalibration() {
        stopCalibration()
        _uiState.update {
            LevelCalibrationUiState(
                isConnected = it.isConnected,
                buttonText = "Start Level Calibration"
            )
        }
    }

    /**
     * Show/hide cancel confirmation dialog
     */
    fun showCancelDialog(show: Boolean) {
        _uiState.update { it.copy(showCancelDialog = show) }
    }

    /**
     * Start listening to STATUSTEXT messages for calibration progress
     * (Following MissionPlanner protocol)
     */
    private fun startMessageListener() {
        statusTextCollectorJob?.cancel()
        statusTextCollectorJob = viewModelScope.launch {
            sharedViewModel.calibrationStatus
                .mapNotNull { it }
                .collect { statusText ->
                    if (!_inCalibration) return@collect

                    val text = statusText.lowercase()
                    Log.d("LevelCalVM", "STATUSTEXT: $text")

                    _uiState.update { it.copy(statusText = statusText) }

                    when {
                        // Success patterns (as per MissionPlanner)
                        text.contains("calibration successful") ||
                        text.contains("level complete") ||
                        text.contains("calibration: success") -> {
                            Log.d("LevelCalVM", "✓ Level calibration completed successfully")
                            stopCalibration()
                            _uiState.update {
                                it.copy(
                                    calibrationState = LevelCalibrationState.Success("Level calibration completed successfully!"),
                                    statusText = "Level horizon has been set",
                                    buttonText = "Start Level Calibration"
                                )
                            }
                            // Announce success
                            sharedViewModel.announceCalibrationFinished(isSuccess = true)
                        }
                        // Failure patterns (as per MissionPlanner)
                        text.contains("calibration failed") ||
                        text.contains("calibration: fail") ||
                        text.contains("failed") && text.contains("level") -> {
                            Log.e("LevelCalVM", "✗ Level calibration failed: $text")
                            stopCalibration()
                            _uiState.update {
                                it.copy(
                                    calibrationState = LevelCalibrationState.Failed(statusText),
                                    statusText = "Calibration failed",
                                    buttonText = "Start Level Calibration"
                                )
                            }
                            // Announce failure
                            sharedViewModel.announceCalibrationFinished(isSuccess = false)
                        }
                        // Instruction messages
                        text.contains("level") && text.contains("place") -> {
                            _uiState.update {
                                it.copy(statusText = statusText)
                            }
                        }
                    }
                }
        }
    }

    /**
     * Stop calibration and clean up listeners
     */
    private fun stopCalibration() {
        _inCalibration = false
        statusTextCollectorJob?.cancel()
        statusTextCollectorJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopCalibration()
    }
}
