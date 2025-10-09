package com.example.aerogcsclone.calibration

import android.util.Log
import com.divpundir.mavlink.definitions.ardupilotmega.AccelcalVehiclePos
import com.divpundir.mavlink.definitions.common.MavCmd
import com.divpundir.mavlink.definitions.common.Statustext
import com.example.aerogcsclone.telemetry.MavlinkTelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MavlinkCalibrationService @Inject constructor(
    private val telemetryRepository: MavlinkTelemetryRepository,
    private val externalScope: CoroutineScope
) : CalibrationService {

    private val _calibrationState = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    override val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    private var calibrationJob: Job? = null

    override suspend fun startImuCalibration() {
        cancelOngoingCalibration()
        _calibrationState.value = CalibrationState.InProgress("Starting IMU calibration...")

        calibrationJob = externalScope.launch {
            telemetryRepository.mavFrame
                .filterIsInstance<Statustext>()
                .onCompletion {
                    if (_calibrationState.value is CalibrationState.InProgress) {
                        _calibrationState.value = CalibrationState.Error("Calibration stopped unexpectedly.")
                    }
                }
                .collect { statustext ->
                    handleStatusText(statustext)
                }
        }

        telemetryRepository.sendCommandLong(
            MavCmd.PREFLIGHT_CALIBRATION,
            param5 = 1f // 1.0f for accelerometer
        )
    }

    private fun handleStatusText(statustext: Statustext) {
        val message = statustext.text.toString()
        Log.d("CalibrationService", "STATUSTEXT: $message")

        when {
            message.contains("Place vehicle") -> {
                val position = ImuPosition.fromInstruction(message)
                if (position != null) {
                    _calibrationState.value = CalibrationState.AwaitingUserInput(message, position)
                }
            }
            message.contains("Calibration successful") -> {
                _calibrationState.value = CalibrationState.Success("IMU Calibration Successful!")
                cancelOngoingCalibration()
            }
            message.contains("Calibration FAILED") -> {
                _calibrationState.value = CalibrationState.Error("IMU Calibration Failed.")
                cancelOngoingCalibration()
            }
        }
    }

    override suspend fun advanceImuCalibrationStep(position: ImuPosition) {
        val accelcalVehiclePos = AccelcalVehiclePos.values().first { it.value == position.mavlinkValue }
        _calibrationState.value = CalibrationState.InProgress("Acknowledged ${position.name}. Capturing data...")
        telemetryRepository.sendCommandLong(
            MavCmd.ACCELCAL_VEHICLE_POS,
            param1 = accelcalVehiclePos.value.toFloat()
        )
    }

    override suspend fun startCompassCalibration() {
        _calibrationState.value = CalibrationState.Error("Compass calibration not implemented yet.")
    }

    override suspend fun cancelCalibration() {
        cancelOngoingCalibration()
        telemetryRepository.sendCommandLong(MavCmd.PREFLIGHT_CALIBRATION) // All params 0 cancels
        _calibrationState.value = CalibrationState.Idle
    }

    private fun cancelOngoingCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
    }
}