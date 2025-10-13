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
import kotlinx.coroutines.withTimeoutOrNull

class CalibrationViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    private var currentPositionIndex = 0
    private var statusTextCollectorJob: Job? = null

    // Tuning knobs
    private val ackTimeoutMs = 4000L
    private val startPromptTimeoutMs = 8000L
    private val nextPromptTimeoutMs = 8000L
    private val finalOutcomeTimeoutMs = 10000L
    private val maxRetries = 1 // retry count for no-ACK cases

    // Prompt flow for "Place vehicle ..." STATUSTEXT parsing
    private var accelPromptFlow = MutableSharedFlow<AccelCalibrationPosition>(extraBufferCapacity = 1)

    init {
        // Observe connection state from SharedViewModel
        viewModelScope.launch {
            sharedViewModel.isConnected.collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
            }
        }
    }

    /**
     * Start the accelerometer calibration process (ArduPilot).
     * Requires either an ACK (ACCEPTED/IN_PROGRESS) + a prompt, or a prompt alone, before showing step 1.
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

            // Reset prompt flow to avoid stale emissions from previous runs
            accelPromptFlow = MutableSharedFlow(extraBufferCapacity = 1)

            // Start listening to STATUSTEXT messages from the telemetry
            startStatusTextListener()

            // Send MAV_CMD_PREFLIGHT_CALIBRATION with param5 = 1.0 (accelerometer calibration)
            var startedAckOk = false
            var lastAckDesc: String? = null
            try {
                repeat(maxRetries + 1) { attempt ->
                    Log.d("CalibrationVM", "Sending PREFLIGHT_CALIBRATION (accel) attempt=${attempt + 1}")
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

                    val ack = sharedViewModel.awaitCommandAck(241u, ackTimeoutMs)
                    lastAckDesc = ack?.result?.entry?.name ?: ack?.result?.value?.toString()
                    val result = ack?.result?.value
                    val ok = (result == 0u /* ACCEPTED */) || (result == 5u /* IN_PROGRESS */)
                    if (ok) {
                        startedAckOk = true
                        return@repeat
                    } else if (ack != null) {
                        // Negative ACK -> fail immediately
                        return@repeat
                    }
                    // ack == null -> rely on prompt
                }

                // Wait for the first prompt (LEVEL expected). If prompt doesn't arrive, fail.
                val firstPrompt = awaitNextPrompt(startPromptTimeoutMs)
                if (firstPrompt != null) {
                    // Align index to the prompt given by autopilot
                    currentPositionIndex = AccelCalibrationPosition.entries.indexOf(firstPrompt).coerceAtLeast(0)
                    _uiState.update {
                        it.copy(
                            calibrationState = CalibrationState.AwaitingUserInput(
                                position = firstPrompt,
                                instruction = firstPrompt.instruction
                            ),
                            statusText = firstPrompt.instruction,
                            currentPositionIndex = currentPositionIndex
                        )
                    }
                    Log.d("CalibrationVM", "Start prompt received: ${firstPrompt.name}")
                } else {
                    val reason = if (startedAckOk) "No start prompt received" else "No ACK and no start prompt"
                    Log.e("CalibrationVM", "✗ Calibration start failed: $reason (ack=$lastAckDesc)")
                    _uiState.update {
                        it.copy(
                            calibrationState = CalibrationState.Failed("Calibration start failed: $reason"),
                            statusText = "Calibration failed to start"
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
     * Requires either an ACCEPTED/IN_PROGRESS ACK or the next prompt to proceed.
     */
    fun onNextPosition() {
        val currentState = _uiState.value.calibrationState

        if (currentState is CalibrationState.AwaitingUserInput) {
            viewModelScope.launch {
                val position = currentState.position

                _uiState.update {
                    it.copy(
                        calibrationState = CalibrationState.ProcessingPosition(position),
                        statusText = "Processing ${position.name} position..."
                    )
                }

                try {
                    // Reset prompt flow before sending this step to avoid consuming old prompts
                    accelPromptFlow = MutableSharedFlow(extraBufferCapacity = 1)

                    // Send and await ACK for this position
                    var ackOk = false
                    var lastAck: String? = null
                    repeat(maxRetries + 1) { attempt ->
                        Log.d("CalibrationVM", "Sending ACCELCAL_VEHICLE_POS=${position.name} attempt=${attempt + 1}")
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
                        val ack = sharedViewModel.awaitCommandAck(42429u, ackTimeoutMs)
                        val result = ack?.result?.value
                        lastAck = ack?.result?.entry?.name ?: result?.toString()
                        val ok = (result == 0u /* ACCEPTED */) || (result == 5u /* IN_PROGRESS */)
                        if (ok) {
                            ackOk = true
                            return@repeat
                        } else if (ack != null) {
                            // Negative ACK -> fail immediately
                            ackOk = false
                            return@repeat
                        }
                        // ack == null -> rely on prompt
                    }

                    // If this was the last position, wait for final outcome explicitly
                    val isLast = position == AccelCalibrationPosition.BACK
                    if (isLast) {
                        val outcome = awaitFinalOutcome(finalOutcomeTimeoutMs)
                        when (outcome) {
                            true -> {
                                // Success will also be set by listener, but ensure state
                                _uiState.update {
                                    it.copy(
                                        calibrationState = CalibrationState.Success("Calibration completed successfully!"),
                                        statusText = "Calibration successful"
                                    )
                                }
                                stopStatusTextListener()
                            }
                            false -> {
                                _uiState.update {
                                    it.copy(
                                        calibrationState = CalibrationState.Failed("Calibration failed at final step"),
                                        statusText = "Calibration failed"
                                    )
                                }
                                stopStatusTextListener()
                            }
                            null -> {
                                _uiState.update {
                                    it.copy(
                                        calibrationState = CalibrationState.Failed("Timeout waiting for completion"),
                                        statusText = "Calibration timeout"
                                    )
                                }
                                stopStatusTextListener()
                            }
                        }
                        return@launch
                    }

                    // For intermediate positions: wait for the next prompt and only then advance
                    val nextPrompt = awaitNextPrompt(nextPromptTimeoutMs)
                    if (nextPrompt == null) {
                        // If ACK was explicitly negative or no prompt came, fail fast
                        val reason = if (ackOk) "No next prompt from vehicle" else "No ACK and no next prompt"
                        Log.e("CalibrationVM", "✗ Step failed: ${position.name} -> $reason (lastAck=$lastAck)")
                        _uiState.update {
                            it.copy(
                                calibrationState = CalibrationState.Failed("Step failed: $reason"),
                                statusText = "Calibration failed - $reason"
                            )
                        }
                        stopStatusTextListener()
                        return@launch
                    }

                    // If vehicle asks for same position again, treat as not accepted
                    if (nextPrompt == position) {
                        Log.e("CalibrationVM", "✗ Vehicle still asking for ${position.name} -> not accepted")
                        _uiState.update {
                            it.copy(
                                calibrationState = CalibrationState.Failed("Position not accepted by vehicle: ${position.name}"),
                                statusText = "Please retry calibration"
                            )
                        }
                        stopStatusTextListener()
                        return@launch
                    }

                    // Update to vehicle-requested next position
                    currentPositionIndex = AccelCalibrationPosition.entries.indexOf(nextPrompt).coerceAtLeast(currentPositionIndex + 1)
                    _uiState.update {
                        it.copy(
                            calibrationState = CalibrationState.AwaitingUserInput(
                                position = nextPrompt,
                                instruction = nextPrompt.instruction
                            ),
                            statusText = nextPrompt.instruction,
                            currentPositionIndex = currentPositionIndex
                        )
                    }

                } catch (e: Exception) {
                    Log.e("CalibrationVM", "Failed to send position command ${position.name}", e)
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

            // There is no dedicated cancel for accel; try clearing by sending PREFLIGHT_CALIBRATION with zeros
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
     * Start listening to STATUSTEXT messages for calibration feedback.
     * Emits parsed position prompts to accelPromptFlow and auto-resolves success/failure text.
     */
    private fun startStatusTextListener() {
        stopStatusTextListener() // Stop any existing listener
        Log.d("CalibrationVM", "Starting STATUSTEXT listener for calibration feedback")

        statusTextCollectorJob = viewModelScope.launch {
            sharedViewModel.calibrationStatus.collect { statusText ->
                statusText?.let { text ->
                    Log.d("CalibrationVM", "STATUSTEXT: $text")

                    // Update status text in UI
                    _uiState.update { state ->
                        state.copy(statusText = text)
                    }

                    // Parse known prompts and emit
                    parseAccelPrompt(text)?.let { pos ->
                        accelPromptFlow.tryEmit(pos)
                    }

                    // Check for success or failure messages
                    when {
                        text.contains("Calibration successful", ignoreCase = true) -> {
                            _uiState.update { state ->
                                state.copy(
                                    calibrationState = CalibrationState.Success(text),
                                    statusText = text
                                )
                            }
                            stopStatusTextListener()
                        }
                        text.contains("Calibration FAILED", ignoreCase = true) ||
                        text.contains("failed", ignoreCase = true) -> {
                            _uiState.update { state ->
                                state.copy(
                                    calibrationState = CalibrationState.Failed(text),
                                    statusText = text
                                )
                            }
                            stopStatusTextListener()
                        }
                        else -> Unit
                    }
                }
            }
        }
        Log.d("CalibrationVM", "STATUSTEXT listener job started")
    }

    /** Stop listening to STATUSTEXT messages. */
    private fun stopStatusTextListener() {
        statusTextCollectorJob?.cancel()
        statusTextCollectorJob = null
    }

    /** Parse ArduPilot accel calibration prompts into positions. */
    private fun parseAccelPrompt(text: String): AccelCalibrationPosition? {
        val lower = text.lowercase()
        return when {
            lower.contains("place") && lower.contains("level") -> AccelCalibrationPosition.LEVEL
            lower.contains("place") && lower.contains("left") -> AccelCalibrationPosition.LEFT
            lower.contains("place") && lower.contains("right") -> AccelCalibrationPosition.RIGHT
            lower.contains("place") && (lower.contains("nose down") || (lower.contains("nose") && lower.contains("down"))) -> AccelCalibrationPosition.NOSEDOWN
            lower.contains("place") && (lower.contains("nose up") || (lower.contains("nose") && lower.contains("up"))) -> AccelCalibrationPosition.NOSEUP
            lower.contains("place") && lower.contains("back") -> AccelCalibrationPosition.BACK
            else -> null
        }
    }

    /** Await next "Place vehicle ..." prompt. */
    private suspend fun awaitNextPrompt(timeoutMs: Long): AccelCalibrationPosition? = withTimeoutOrNull(timeoutMs) {
        accelPromptFlow.first()
    }

    /** Await a final outcome from STATUSTEXT: success/failed. Returns true=success, false=failed, null=timeout. */
    private suspend fun awaitFinalOutcome(timeoutMs: Long): Boolean? = withTimeoutOrNull(timeoutMs) {
        sharedViewModel.calibrationStatus
            .mapNotNull { it }
            .first { txt ->
                val l = txt.lowercase()
                l.contains("calibration successful") || l.contains("failed")
            }
            .contains("successful", ignoreCase = true)
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusTextListener()
    }
}
