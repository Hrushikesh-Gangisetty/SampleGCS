package com.example.aerogcsclone.calibration

/**
 * Represents the different states during Level Horizon calibration
 */
sealed class LevelCalibrationState {
    object Idle : LevelCalibrationState()
    object Initiating : LevelCalibrationState()
    object InProgress : LevelCalibrationState()
    data class Success(val message: String = "Level calibration completed successfully") : LevelCalibrationState()
    data class Failed(val errorMessage: String) : LevelCalibrationState()
    object Cancelled : LevelCalibrationState()
}

/**
 * UI State for Level Horizon Calibration
 */
data class LevelCalibrationUiState(
    val calibrationState: LevelCalibrationState = LevelCalibrationState.Idle,
    val statusText: String = "",
    val isConnected: Boolean = false,
    val buttonText: String = "Start Level Calibration",
    val showCancelDialog: Boolean = false
)

