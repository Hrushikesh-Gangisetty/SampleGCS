package com.example.aerogcsclone.calibration

/**
 * State of the compass calibration process.
 */
sealed class CompassCalibrationState {
    object Idle : CompassCalibrationState()
    object Starting : CompassCalibrationState()
    data class InProgress(
        val currentInstruction: String = "Rotate vehicle slowly on all axes"
    ) : CompassCalibrationState()
    data class Success(
        val message: String,
        val reportDetails: String? = null
    ) : CompassCalibrationState()
    data class Failed(val errorMessage: String) : CompassCalibrationState()
    object Cancelled : CompassCalibrationState()
}

/**
 * UI state for the compass calibration screen.
 */
data class CompassCalibrationUiState(
    val calibrationState: CompassCalibrationState = CompassCalibrationState.Idle,
    val statusText: String = "",
    val compassProgress: Map<Int, Int> = emptyMap(), // compass_id -> completion percentage (0-100)
    val isConnected: Boolean = false,
    val showCancelDialog: Boolean = false,
    val overallProgress: Int = 0 // Overall progress percentage
)
