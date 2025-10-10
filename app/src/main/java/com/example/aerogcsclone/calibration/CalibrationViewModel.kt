package com.example.aerogcsclone.calibration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MavCmd
import com.example.aerogcsclone.telemetry.SharedViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job

class CalibrationViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    private var currentPositionIndex = 0
    private var statusTextCollectorJob: Job? = null

    init {
        // Observe connection state from SharedViewModel
        viewModelScope.launch {
            sharedViewModel.isConnected.collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
            }
        }
    }

    /**
     * Start the accelerometer calibration process.
     */
    fun startCalibration() {
        if (!_uiState.value.isConnected) {
            _uiState.update {
                it.copy(
                    calibrationState = CalibrationState.Failed("Not connected to drone"),
                    statusText = "Please connect to the drone first"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    calibrationState = CalibrationState.Initiating,
                    statusText = "Initiating accelerometer calibration...",
                    currentPositionIndex = 0
                )
            }

            currentPositionIndex = 0

            // Start listening to STATUSTEXT messages from the telemetry
            startStatusTextListener()

            // Send MAV_CMD_PREFLIGHT_CALIBRATION with param5 = 1.0 (accelerometer calibration)
            try {
                sharedViewModel.sendCalibrationCommand(
                    command = MavCmd.PREFLIGHT_CALIBRATION,
                    param1 = 0f, // Gyro
                    param2 = 0f, // Magnetometer
                    param3 = 0f, // Barometer
                    param4 = 0f, // Radio
                    param5 = 1f, // Accelerometer - START
                    param6 = 0f, // ESC/Motor
                    param7 = 0f  // Unused
                )

                Log.d("CalibrationVM", "Sent PREFLIGHT_CALIBRATION command to start accel calibration")

                // Wait for drone to acknowledge calibration start
                delay(1000)

                // Now send the LEVEL position command immediately
                Log.d("CalibrationVM", "Sending LEVEL position command (param1=1.0)")

                var ackReceived = false

                // Start listening for COMMAND_ACK for LEVEL position
                val commandAckJob = viewModelScope.launch {
                    sharedViewModel.commandAck
                        .filter { it.command.value == 42429u }
                        .firstOrNull()
                        ?.let { ack ->
                            ackReceived = true
                            Log.d("CalibrationVM", "COMMAND_ACK for LEVEL position: result=${ack.result.entry?.name ?: ack.result.value}")

                            if (ack.result.value == 0u) { // ACCEPTED
                                Log.d("CalibrationVM", "✓ LEVEL position command ACCEPTED")
                                // Move to next position (LEFT)
                                currentPositionIndex++
                                proceedToNextPosition()
                            } else {
                                Log.e("CalibrationVM", "✗ LEVEL position command FAILED: ${ack.result.entry?.name ?: ack.result.value}")
                                _uiState.update {
                                    it.copy(
                                        calibrationState = CalibrationState.Failed("Calibration failed at LEVEL position: ${ack.result.entry?.name ?: "Unknown error"}"),
                                        statusText = "Calibration failed - drone rejected LEVEL position"
                                    )
                                }
                                stopStatusTextListener()
                            }
                        }
                }

                // Send LEVEL position command (param1 = 1.0)
                sharedViewModel.sendCalibrationCommandRaw(
                    commandId = 42429u, // MAV_CMD_ACCELCAL_VEHICLE_POS
                    param1 = 1.0f, // LEVEL position
                    param2 = 0f,
                    param3 = 0f,
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )

                Log.d("CalibrationVM", "✓ LEVEL position command sent, waiting for COMMAND_ACK...")

                // Wait for COMMAND_ACK with timeout
                delay(3000)
                commandAckJob.cancel()

                // If still in Initiating state and no ACK was received
                if (!ackReceived && _uiState.value.calibrationState is CalibrationState.Initiating) {
                    Log.w("CalibrationVM", "No COMMAND_ACK received for LEVEL position within timeout")
                    _uiState.update {
                        it.copy(
                            calibrationState = CalibrationState.Failed("Timeout waiting for drone acknowledgment of LEVEL position"),
                            statusText = "Calibration failed - no response from drone"
                        )
                    }
                    stopStatusTextListener()
                }

            } catch (e: Exception) {
                Log.e("CalibrationVM", "Failed to start calibration", e)
                _uiState.update {
                    it.copy(
                        calibrationState = CalibrationState.Failed("Failed to start calibration: ${e.message}"),
                        statusText = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * User confirms the current position and moves to the next.
     */
    fun onNextPosition() {
        val currentState = _uiState.value.calibrationState

        if (currentState is CalibrationState.AwaitingUserInput) {
            viewModelScope.launch {
                val position = currentState.position

                Log.d("CalibrationVM", "========================================")
                Log.d("CalibrationVM", "USER CONFIRMED POSITION")
                Log.d("CalibrationVM", "Position: ${position.name}")
                Log.d("CalibrationVM", "Position index: $currentPositionIndex")
                Log.d("CalibrationVM", "========================================")

                _uiState.update {
                    it.copy(
                        calibrationState = CalibrationState.ProcessingPosition(position),
                        statusText = "Processing ${position.name} position..."
                    )
                }

                // Send the position acknowledgment using a custom command
                // MAV_CMD_ACCELCAL_VEHICLE_POS is command ID 42429 in ArduPilot
                try {
                    Log.d("CalibrationVM", "SENDING MAV_CMD_ACCELCAL_VEHICLE_POS")
                    Log.d("CalibrationVM", "Command ID: 42429 (MAV_CMD_ACCELCAL_VEHICLE_POS)")
                    Log.d("CalibrationVM", "Parameters:")
                    Log.d("CalibrationVM", "  param1 (Position): ${position.paramValue} (${position.name})")
                    Log.d("CalibrationVM", "  param2-7: 0.0")

                    var ackReceived = false

                    // Start listening for COMMAND_ACK
                    val commandAckJob = viewModelScope.launch {
                        sharedViewModel.commandAck
                            .filter { it.command.value == 42429u }
                            .firstOrNull()
                            ?.let { ack ->
                                ackReceived = true
                                Log.d("CalibrationVM", "COMMAND_ACK for calibration command: result=${ack.result.entry?.name ?: ack.result.value}")

                                // Check if command was successful (ACCEPTED = 0)
                                if (ack.result.value == 0u) {
                                    Log.d("CalibrationVM", "✓ Position command ACCEPTED by drone")
                                    // Move to next position
                                    currentPositionIndex++
                                    Log.d("CalibrationVM", "Moving to next position (index: $currentPositionIndex)")
                                    proceedToNextPosition()
                                } else {
                                    // Command failed
                                    Log.e("CalibrationVM", "✗ Position command FAILED: ${ack.result.entry?.name ?: ack.result.value}")
                                    _uiState.update {
                                        it.copy(
                                            calibrationState = CalibrationState.Failed("Calibration position command failed: ${ack.result.entry?.name ?: "Unknown error"}"),
                                            statusText = "Calibration failed - drone rejected position command"
                                        )
                                    }
                                    stopStatusTextListener()
                                }
                            }
                    }

                    // Use raw command ID for ArduPilot-specific command
                    sharedViewModel.sendCalibrationCommandRaw(
                        commandId = 42429u, // MAV_CMD_ACCELCAL_VEHICLE_POS
                        param1 = position.paramValue,
                        param2 = 0f,
                        param3 = 0f,
                        param4 = 0f,
                        param5 = 0f,
                        param6 = 0f,
                        param7 = 0f
                    )

                    Log.d("CalibrationVM", "✓ Position command sent, waiting for COMMAND_ACK...")

                    // Wait for COMMAND_ACK with timeout
                    delay(3000)
                    commandAckJob.cancel()

                    // If we reach here and still in ProcessingPosition state and no ACK received
                    if (!ackReceived && _uiState.value.calibrationState is CalibrationState.ProcessingPosition) {
                        Log.w("CalibrationVM", "No COMMAND_ACK received within timeout")
                        _uiState.update {
                            it.copy(
                                calibrationState = CalibrationState.Failed("Timeout waiting for drone acknowledgment"),
                                statusText = "Calibration failed - no response from drone"
                            )
                        }
                        stopStatusTextListener()
                    }

                } catch (e: Exception) {
                    Log.e("CalibrationVM", "========================================")
                    Log.e("CalibrationVM", "FAILED TO SEND POSITION COMMAND")
                    Log.e("CalibrationVM", "Position: ${position.name}")
                    Log.e("CalibrationVM", "Error: ${e.message}", e)
                    Log.e("CalibrationVM", "========================================")
                    _uiState.update {
                        it.copy(
                            calibrationState = CalibrationState.Failed("Failed to process position: ${e.message}"),
                            statusText = "Error: ${e.message}"
                        )
                    }
                }
            }
        } else {
            Log.w("CalibrationVM", "onNextPosition called but not in AwaitingUserInput state. Current: $currentState")
        }
    }

    /**
     * Cancel the calibration process.
     */
    fun cancelCalibration() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    calibrationState = CalibrationState.Idle,
                    statusText = "Cancelling calibration...",
                    showCancelDialog = false
                )
            }

            // Send MAV_CMD_PREFLIGHT_CALIBRATION with all params = 0 to cancel
            try {
                sharedViewModel.sendCalibrationCommand(
                    command = MavCmd.PREFLIGHT_CALIBRATION,
                    param1 = 0f,
                    param2 = 0f,
                    param3 = 0f,
                    param4 = 0f,
                    param5 = 0f,
                    param6 = 0f,
                    param7 = 0f
                )

                Log.d("CalibrationVM", "Sent cancel calibration command")

                delay(500)

                _uiState.update {
                    it.copy(
                        calibrationState = CalibrationState.Cancelled,
                        statusText = "Calibration cancelled",
                        currentPositionIndex = 0
                    )
                }

                stopStatusTextListener()

            } catch (e: Exception) {
                Log.e("CalibrationVM", "Failed to cancel calibration", e)
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
        stopStatusTextListener()
        currentPositionIndex = 0
        _uiState.update {
            it.copy(
                calibrationState = CalibrationState.Idle,
                statusText = "",
                currentPositionIndex = 0,
                showCancelDialog = false
            )
        }
    }

    fun showCancelDialog(show: Boolean) {
        _uiState.update { it.copy(showCancelDialog = show) }
    }

    /**
     * Proceed to the next calibration position or complete the process.
     */
    private fun proceedToNextPosition() {
        val nextPosition = AccelCalibrationPosition.entries.getOrNull(currentPositionIndex)

        Log.d("CalibrationVM", "========================================")
        Log.d("CalibrationVM", "PROCEED TO NEXT POSITION")
        Log.d("CalibrationVM", "Current index: $currentPositionIndex")
        Log.d("CalibrationVM", "Next position: ${nextPosition?.name ?: "COMPLETE"}")
        Log.d("CalibrationVM", "========================================")

        if (nextPosition != null) {
            Log.d("CalibrationVM", "Setting position: ${nextPosition.name}")
            Log.d("CalibrationVM", "Instruction: ${nextPosition.instruction}")

            _uiState.update {
                it.copy(
                    calibrationState = CalibrationState.AwaitingUserInput(
                        position = nextPosition,
                        instruction = nextPosition.instruction
                    ),
                    statusText = nextPosition.instruction,
                    currentPositionIndex = currentPositionIndex
                )
            }

            Log.d("CalibrationVM", "✓ Ready for user to position drone as: ${nextPosition.name}")
        } else {
            // All positions completed
            Log.d("CalibrationVM", "========================================")
            Log.d("CalibrationVM", "ALL POSITIONS COMPLETED!")
            Log.d("CalibrationVM", "========================================")

            _uiState.update {
                it.copy(
                    calibrationState = CalibrationState.Success("Calibration completed successfully!"),
                    statusText = "Calibration successful"
                )
            }
            stopStatusTextListener()
            Log.d("CalibrationVM", "Status text listener stopped")
        }
    }

    /**
     * Start listening to STATUSTEXT messages for calibration feedback.
     */
    private fun startStatusTextListener() {
        stopStatusTextListener() // Stop any existing listener
        Log.d("CalibrationVM", "Starting STATUSTEXT listener for calibration feedback")

        statusTextCollectorJob = viewModelScope.launch {
            sharedViewModel.calibrationStatus.collect { statusText ->
                statusText?.let {
                    Log.d("CalibrationVM", "========================================")
                    Log.d("CalibrationVM", "RECEIVED STATUSTEXT FROM DRONE")
                    Log.d("CalibrationVM", "Message: $it")
                    Log.d("CalibrationVM", "========================================")

                    // Update status text in UI
                    _uiState.update { state ->
                        state.copy(statusText = it)
                    }

                    // Check for success or failure messages
                    when {
                        it.contains("Calibration successful", ignoreCase = true) -> {
                            Log.d("CalibrationVM", "✓✓✓ CALIBRATION SUCCESSFUL ✓✓✓")
                            _uiState.update { state ->
                                state.copy(
                                    calibrationState = CalibrationState.Success(it),
                                    statusText = it
                                )
                            }
                            stopStatusTextListener()
                        }
                        it.contains("Calibration FAILED", ignoreCase = true) ||
                        it.contains("failed", ignoreCase = true) -> {
                            Log.e("CalibrationVM", "✗✗✗ CALIBRATION FAILED ✗✗✗")
                            _uiState.update { state ->
                                state.copy(
                                    calibrationState = CalibrationState.Failed(it),
                                    statusText = it
                                )
                            }
                            stopStatusTextListener()
                        }
                        it.contains("Place vehicle", ignoreCase = true) -> {
                            Log.d("CalibrationVM", "ArduPilot prompting for position")
                            // ArduPilot is prompting for next position
                            // The state machine will handle this via user clicking Next
                        }
                        else -> {
                            Log.d("CalibrationVM", "General status message received")
                        }
                    }
                }
            }
        }
        Log.d("CalibrationVM", "STATUSTEXT listener job started")
    }

    /**
     * Stop listening to STATUSTEXT messages.
     */
    private fun stopStatusTextListener() {
        statusTextCollectorJob?.cancel()
        statusTextCollectorJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusTextListener()
    }
}
