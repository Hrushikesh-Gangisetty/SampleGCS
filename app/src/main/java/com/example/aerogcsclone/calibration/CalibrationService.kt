package com.example.aerogcsclone.calibration

import kotlinx.coroutines.flow.StateFlow

interface CalibrationService {
    val calibrationState: StateFlow<CalibrationState>

    suspend fun startImuCalibration()
    suspend fun advanceImuCalibrationStep(position: ImuPosition)
    suspend fun startCompassCalibration()
    suspend fun cancelCalibration()
}