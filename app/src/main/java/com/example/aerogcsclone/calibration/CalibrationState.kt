package com.example.aerogcsclone.calibration

/**
 * Enum representing the different orientations for accelerometer calibration.
 */
enum class AccelCalibrationPosition(val paramValue: Float, val instruction: String) {
    LEVEL(1.0f, "Place vehicle level and press Next"),
    LEFT(2.0f, "Place vehicle on its LEFT side and press Next"),
    RIGHT(3.0f, "Place vehicle on its RIGHT side and press Next"),
    NOSEDOWN(4.0f, "Place vehicle nose DOWN and press Next"),
    NOSEUP(5.0f, "Place vehicle nose UP and press Next"),
    BACK(6.0f, "Place vehicle on its BACK and press Next");

    companion object {
        fun fromIndex(index: Int): AccelCalibrationPosition? = entries.getOrNull(index)
    }
}

/**
 * State of the calibration process.
 */
sealed class CalibrationState {
    object Idle : CalibrationState()
    object Initiating : CalibrationState()
    data class AwaitingUserInput(
        val position: AccelCalibrationPosition,
        val instruction: String
    ) : CalibrationState()
    data class ProcessingPosition(val position: AccelCalibrationPosition) : CalibrationState()
    data class Success(val message: String) : CalibrationState()
    data class Failed(val errorMessage: String) : CalibrationState()
    object Cancelled : CalibrationState()
}

/**
 * UI state for the calibration screen.
 */
data class CalibrationUiState(
    val calibrationState: CalibrationState = CalibrationState.Idle,
    val statusText: String = "",
    val currentPositionIndex: Int = 0,
    val totalPositions: Int = AccelCalibrationPosition.entries.size,
    val isConnected: Boolean = false,
    val showCancelDialog: Boolean = false
)
