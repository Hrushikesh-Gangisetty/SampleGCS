package com.example.aerogcsclone.calibration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aerogcsclone.telemetry.SharedViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.max
import kotlin.math.min

/**
 * ViewModel for ArduPilot RC (Radio Control) Calibration.
 *
 * Implements the MissionPlanner RC calibration protocol:
 *
 * STEP 1: Load Parameters
 *   - Request RCMAP_ROLL, RCMAP_PITCH, RCMAP_THROTTLE, RCMAP_YAW parameters
 *   - Determine which physical RC channels map to flight controls
 *   - Request RC_CHANNELS message streaming at 10Hz
 *
 * STEP 2: Monitor Current Values
 *   - Display real-time RC channel values (1-16)
 *   - Show current min/max/trim values from parameters
 *   - User can see live stick movements
 *
 * STEP 3: Calibrate - Capture Min/Max
 *   - User clicks "Calibrate Radio"
 *   - Instruction: "Move all sticks and switches to extreme positions"
 *   - Track minimum and maximum values for each channel
 *   - Display red lines showing captured extremes
 *
 * STEP 4: Calibrate - Capture Center/Trim
 *   - User clicks "Click when Done"
 *   - Instruction: "Center all sticks and set throttle to minimum"
 *   - Capture current values as trim/center values
 *
 * STEP 5: Save to Vehicle
 *   - For each channel (1-16):
 *     - Send RC{n}_MIN parameter
 *     - Send RC{n}_MAX parameter
 *     - Send RC{n}_TRIM parameter
 *   - Wait for PARAM_VALUE confirmations
 *
 * STEP 6: Display Summary
 *   - Show final min|max values for all channels
 *   - Highlight channels that appear disconnected (1500 ±2)
 */
class RCCalibrationViewModel(private val sharedViewModel: SharedViewModel) : ViewModel() {

    private val _uiState = MutableStateFlow(RCCalibrationUiState())
    val uiState: StateFlow<RCCalibrationUiState> = _uiState.asStateFlow()

    private var rcChannelsListenerJob: Job? = null
    private var paramValueListenerJob: Job? = null

    // Calibration tracking
    private val capturedMin = IntArray(16) { 1500 }
    private val capturedMax = IntArray(16) { 1500 }
    private val capturedTrim = IntArray(16) { 1500 }

    private var oldRcRate: Float = 0f

    init {
        // Observe connection state
        viewModelScope.launch {
            sharedViewModel.isConnected.collect { isConnected ->
                _uiState.update { it.copy(isConnected = isConnected) }
                if (isConnected && _uiState.value.calibrationState is RCCalibrationState.Idle) {
                    loadParameters()
                }
            }
        }
    }

    /**
     * STEP 1: Load RC mapping parameters and start RC_CHANNELS streaming.
     */
    private fun loadParameters() {
        viewModelScope.launch {
            Log.d("RCCalVM", "========== LOADING RC PARAMETERS ==========")
            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.LoadingParameters,
                    statusText = "Loading RC channel mapping..."
                )
            }

            try {
                // Request RC channel mapping parameters
                val params = listOf("RCMAP_ROLL", "RCMAP_PITCH", "RCMAP_THROTTLE", "RCMAP_YAW")
                val paramValues = mutableMapOf<String, Int>()

                // Start listening for parameter responses
                val paramJob = viewModelScope.launch {
                    sharedViewModel.paramValue.collect { paramValue ->
                        val paramName = paramValue.paramId
                        if (paramName in params) {
                            paramValues[paramName] = paramValue.paramValue.toInt()
                            Log.d("RCCalVM", "✓ Received $paramName = ${paramValue.paramValue.toInt()}")
                        }
                    }
                }

                // Request each parameter
                params.forEach { param ->
                    sharedViewModel.requestParameter(param)
                    delay(100)
                }

                // Wait for responses (with timeout)
                val startTime = System.currentTimeMillis()
                while (paramValues.size < params.size && System.currentTimeMillis() - startTime < 5000) {
                    delay(100)
                }

                paramJob.cancel()

                // Extract channel mappings (default to standard if not received)
                val rollCh = paramValues["RCMAP_ROLL"] ?: 1
                val pitchCh = paramValues["RCMAP_PITCH"] ?: 2
                val throttleCh = paramValues["RCMAP_THROTTLE"] ?: 3
                val yawCh = paramValues["RCMAP_YAW"] ?: 4

                Log.d("RCCalVM", "✓ RC Mapping: Roll=$rollCh, Pitch=$pitchCh, Throttle=$throttleCh, Yaw=$yawCh")

                // Update channel function assignments
                val updatedChannels = _uiState.value.channels.mapIndexed { index, ch ->
                    val channelNum = index + 1
                    val function = when (channelNum) {
                        rollCh -> "Roll"
                        pitchCh -> "Pitch"
                        throttleCh -> "Throttle"
                        yawCh -> "Yaw"
                        else -> null
                    }
                    ch.copy(isAssignedToFunction = function)
                }

                _uiState.update {
                    it.copy(
                        channels = updatedChannels,
                        rollChannel = rollCh,
                        pitchChannel = pitchCh,
                        throttleChannel = throttleCh,
                        yawChannel = yawCh,
                        calibrationState = RCCalibrationState.Ready(),
                        statusText = "Ready to calibrate",
                        buttonText = "Calibrate Radio"
                    )
                }

                // Start RC_CHANNELS streaming at 10Hz
                sharedViewModel.requestRCChannels(10f)
                startRCChannelsListener()

                Log.d("RCCalVM", "========== RC PARAMETERS LOADED ==========")

            } catch (e: Exception) {
                Log.e("RCCalVM", "Failed to load RC parameters", e)
                _uiState.update {
                    it.copy(
                        calibrationState = RCCalibrationState.Failed("Failed to load RC parameters: ${e.message}"),
                        statusText = "Error loading parameters"
                    )
                }
            }
        }
    }

    /**
     * STEP 2: Start listening to RC_CHANNELS messages and update UI in real-time.
     */
    private fun startRCChannelsListener() {
        rcChannelsListenerJob?.cancel()
        rcChannelsListenerJob = viewModelScope.launch {
            sharedViewModel.rcChannels.collect { rcChannels ->
                // Extract all 16 channels from RC_CHANNELS message
                val channelValues = listOf(
                    rcChannels.chan1Raw.toInt(),
                    rcChannels.chan2Raw.toInt(),
                    rcChannels.chan3Raw.toInt(),
                    rcChannels.chan4Raw.toInt(),
                    rcChannels.chan5Raw.toInt(),
                    rcChannels.chan6Raw.toInt(),
                    rcChannels.chan7Raw.toInt(),
                    rcChannels.chan8Raw.toInt(),
                    rcChannels.chan9Raw.toInt(),
                    rcChannels.chan10Raw.toInt(),
                    rcChannels.chan11Raw.toInt(),
                    rcChannels.chan12Raw.toInt(),
                    rcChannels.chan13Raw.toInt(),
                    rcChannels.chan14Raw.toInt(),
                    rcChannels.chan15Raw.toInt(),
                    rcChannels.chan16Raw.toInt()
                )

                // Update current values and track min/max during calibration
                val state = _uiState.value.calibrationState
                val updatedChannels = _uiState.value.channels.mapIndexed { index, ch ->
                    val currentValue = channelValues[index]

                    // If capturing min/max, update captured values
                    if (state is RCCalibrationState.CapturingMinMax) {
                        capturedMin[index] = min(capturedMin[index], currentValue)
                        capturedMax[index] = max(capturedMax[index], currentValue)

                        ch.copy(
                            currentValue = currentValue,
                            minValue = capturedMin[index],
                            maxValue = capturedMax[index]
                        )
                    } else {
                        ch.copy(currentValue = currentValue)
                    }
                }

                _uiState.update { it.copy(channels = updatedChannels) }
            }
        }
    }

    /**
     * STEP 3: Start calibration - begin capturing min/max values.
     */
    fun startCalibration() {
        if (!_uiState.value.isConnected) {
            Log.e("RCCalVM", "❌ Cannot start calibration - Not connected to drone")
            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Failed("Not connected to drone"),
                    statusText = "Please connect to the drone first"
                )
            }
            return
        }

        viewModelScope.launch {
            Log.d("RCCalVM", "========== STARTING RC CALIBRATION ==========")

            // Initialize min/max tracking
            for (i in 0..15) {
                capturedMin[i] = 2200  // Start high so any real value will be lower
                capturedMax[i] = 800   // Start low so any real value will be higher
            }

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.CapturingMinMax(
                        "Move all RC sticks and switches to their extreme positions"
                    ),
                    statusText = "Move sticks to extremes...",
                    buttonText = "Click when Done"
                )
            }

            Log.d("RCCalVM", "✓ Capturing min/max values - move all controls to extremes")
        }
    }

    /**
     * STEP 4: Move to center capture phase.
     */
    fun captureCenter() {
        viewModelScope.launch {
            Log.d("RCCalVM", "========== CAPTURING CENTER VALUES ==========")

            // Validate that we captured reasonable min/max values
            var validChannels = 0
            for (i in 0..15) {
                if (capturedMin[i] < 2000 && capturedMax[i] > 1000 && capturedMin[i] < capturedMax[i]) {
                    validChannels++
                }
            }

            if (validChannels < 4) {
                Log.e("RCCalVM", "❌ Not enough valid channels captured (only $validChannels)")
                _uiState.update {
                    it.copy(
                        calibrationState = RCCalibrationState.Failed(
                            "Bad channel data. Please ensure transmitter is on and move all sticks."
                        ),
                        statusText = "Calibration failed"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.CapturingCenter(
                        "Center all sticks and set throttle to minimum, then click 'Save Calibration'"
                    ),
                    statusText = "Center sticks and set throttle down...",
                    buttonText = "Save Calibration"
                )
            }

            // Give user time to center sticks
            delay(500)

            Log.d("RCCalVM", "✓ Ready to capture center values")
        }
    }

    /**
     * STEP 5: Capture trim values and save all calibration to vehicle.
     */
    fun saveCalibration() {
        viewModelScope.launch {
            Log.d("RCCalVM", "========== SAVING RC CALIBRATION ==========")

            // Capture current positions as trim/center
            val currentChannels = _uiState.value.channels
            for (i in 0..15) {
                capturedTrim[i] = currentChannels[i].currentValue
            }

            // Validate trim values are within min/max range
            for (i in 0..15) {
                if (capturedMin[i] < capturedMax[i] && capturedMin[i] != 0 && capturedMax[i] != 0) {
                    // Constrain trim to be within min/max
                    if (capturedTrim[i] < capturedMin[i]) capturedTrim[i] = capturedMin[i]
                    if (capturedTrim[i] > capturedMax[i]) capturedTrim[i] = capturedMax[i]
                }
            }

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Saving,
                    statusText = "Saving calibration to vehicle...",
                    buttonText = "Saving..."
                )
            }

            // Save parameters to vehicle
            var successCount = 0
            var failCount = 0

            for (i in 0..15) {
                val channelNum = i + 1

                // Only save channels that have valid data
                if (capturedMin[i] < capturedMax[i] &&
                    capturedMin[i] > 800 && capturedMax[i] < 2200 &&
                    capturedTrim[i] >= capturedMin[i] && capturedTrim[i] <= capturedMax[i]) {

                    Log.d("RCCalVM", "Saving CH$channelNum: MIN=${capturedMin[i]} MAX=${capturedMax[i]} TRIM=${capturedTrim[i]}")

                    try {
                        // Save MIN
                        val minResult = sharedViewModel.setParameter("RC${channelNum}_MIN", capturedMin[i].toFloat())
                        if (minResult != null) successCount++ else failCount++
                        delay(50)

                        // Save MAX
                        val maxResult = sharedViewModel.setParameter("RC${channelNum}_MAX", capturedMax[i].toFloat())
                        if (maxResult != null) successCount++ else failCount++
                        delay(50)

                        // Save TRIM
                        val trimResult = sharedViewModel.setParameter("RC${channelNum}_TRIM", capturedTrim[i].toFloat())
                        if (trimResult != null) successCount++ else failCount++
                        delay(50)

                    } catch (e: Exception) {
                        Log.e("RCCalVM", "Failed to save parameters for CH$channelNum", e)
                        failCount += 3
                    }
                } else {
                    Log.d("RCCalVM", "Skipping CH$channelNum - invalid or disconnected (${capturedMin[i]} | ${capturedMax[i]})")
                }
            }

            // Stop RC_CHANNELS streaming
            sharedViewModel.stopRCChannels()
            rcChannelsListenerJob?.cancel()

            // Generate summary report
            val summary = buildString {
                appendLine("RC Calibration Complete!")
                appendLine()
                appendLine("Detected radio channel values:")
                appendLine("NOTE: Channels showing 1500±2 are likely not connected")
                appendLine("Normal values are around 1100 | 1900")
                appendLine()
                appendLine("Channel : Min  | Max")
                appendLine("─────────────────────────")

                for (i in 0..15) {
                    val channelNum = i + 1
                    val function = _uiState.value.channels[i].isAssignedToFunction
                    val label = if (function != null) "CH$channelNum ($function)" else "CH$channelNum"

                    if (capturedMin[i] < capturedMax[i] && capturedMin[i] > 800) {
                        appendLine("$label : ${capturedMin[i]} | ${capturedMax[i]}")
                    }
                }

                appendLine()
                appendLine("Saved $successCount parameters successfully")
                if (failCount > 0) {
                    appendLine("Failed to save $failCount parameters")
                }
            }

            Log.d("RCCalVM", summary)

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Success(summary),
                    statusText = "Calibration saved successfully",
                    buttonText = "Done"
                )
            }

            Log.d("RCCalVM", "========== RC CALIBRATION COMPLETE ==========")
        }
    }

    /**
     * Handle button click based on current state.
     */
    fun onButtonClick() {
        when (_uiState.value.calibrationState) {
            is RCCalibrationState.Ready -> startCalibration()
            is RCCalibrationState.CapturingMinMax -> captureCenter()
            is RCCalibrationState.CapturingCenter -> saveCalibration()
            else -> {
                // Do nothing for other states
            }
        }
    }

    /**
     * Reset calibration to initial state.
     */
    fun resetCalibration() {
        viewModelScope.launch {
            rcChannelsListenerJob?.cancel()

            _uiState.update {
                it.copy(
                    calibrationState = RCCalibrationState.Idle,
                    statusText = "",
                    buttonText = "Calibrate Radio"
                )
            }

            // Reload parameters if connected
            if (_uiState.value.isConnected) {
                loadParameters()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        rcChannelsListenerJob?.cancel()
        paramValueListenerJob?.cancel()

        // Stop RC_CHANNELS streaming when leaving screen
        viewModelScope.launch {
            sharedViewModel.stopRCChannels()
        }
    }
}

