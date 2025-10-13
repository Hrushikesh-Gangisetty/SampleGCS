package com.example.aerogcsclone.calibration

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.aerogcsclone.manager.CalibrationCommands
import com.divpundir.mavlink.definitions.common.CommandLong

// UI state for the calibration screen
data class BarometerCalibrationUiState(
    val isConnected: Boolean = true, // Set to true for demo; replace with actual connection logic
    val statusText: String = "",
    val isCalibrating: Boolean = false,
    val progress: Int = 0,
    val isStopped: Boolean = false,
    val isFlatSurface: Boolean = true, // Simulate for demo; replace with sensor data
    val isWindGood: Boolean = true // Simulate for demo; replace with sensor data
)

class BarometerCalibrationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BarometerCalibrationUiState())
    val uiState: StateFlow<BarometerCalibrationUiState> = _uiState

    private var calibrationJob: Job? = null

    fun checkConditions(flatSurface: Boolean, windGood: Boolean) {
        _uiState.update {
            it.copy(isFlatSurface = flatSurface, isWindGood = windGood)
        }
    }

    fun startCalibration() {
        val state = _uiState.value
        if (!state.isFlatSurface && !state.isWindGood) {
            _uiState.update {
                it.copy(statusText = "Place the drone on a flat surface. Wind condition is not good. It is better to stop flying and calibrating the drone.")
            }
            return
        } else if (!state.isFlatSurface) {
            _uiState.update {
                it.copy(statusText = "Place the drone on a flat surface.")
            }
            return
        } else if (!state.isWindGood) {
            _uiState.update {
                it.copy(statusText = "Wind condition is not good. It is better to stop flying and calibrating the drone.")
            }
            return
        }
        // Send calibration command
        val command: CommandLong = CalibrationCommands.createBarometerCalibrationCommand()
        // TODO: Send command to drone
        _uiState.update {
            it.copy(
                statusText = "Barometer calibration started...",
                isCalibrating = true,
                progress = 0,
                isStopped = false
            )
        }
        calibrationJob?.cancel()
        calibrationJob = CoroutineScope(Dispatchers.Default).launch {
            for (i in 1..100) {
                delay(50) // Simulate progress, replace with real feedback
                _uiState.update { state ->
                    if (!state.isStopped) {
                        state.copy(progress = i, statusText = "Calibration in progress: $i%")
                    } else {
                        state
                    }
                }
                if (_uiState.value.isStopped) break
            }
            if (!_uiState.value.isStopped) {
                _uiState.update {
                    it.copy(statusText = "Calibration is successful!", isCalibrating = false, progress = 100)
                }
            }
        }
    }

    fun stopCalibration() {
        // TODO: Send stop/cancel command to drone if supported
        _uiState.update {
            it.copy(statusText = "Calibration stopped.", isStopped = true, isCalibrating = false)
        }
        calibrationJob?.cancel()
    }
}
