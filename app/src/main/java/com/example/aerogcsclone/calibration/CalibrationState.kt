package com.example.aerogcsclone.calibration

import kotlinx.coroutines.flow.StateFlow

enum class ImuPosition {
    LEVEL,
    RIGHT_SIDE,
    LEFT_SIDE,
    NOSE_DOWN,
    NOSE_UP,
    BACK
}

sealed class CalibrationState {
    object Idle : CalibrationState()
    data class InProgress(
        val message: String,
        val progress: Float? = null,
        val multiProgress: Map<Int, Float>? = null
    ) : CalibrationState()
    data class AwaitingUserInput(
        val instruction: String,
        val visualHint: ImuPosition
    ) : CalibrationState()
    data class Success(val summary: String) : CalibrationState()
    data class Error(val message: String) : CalibrationState()
}