package com.example.aerogcsclone.manager

import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.definitions.common.CommandLong
import com.divpundir.mavlink.definitions.common.MavCmd

object CalibrationCommands {

    fun createImuCalibrationCommand(
        targetSystem: UByte = 1u,
        targetComponent: UByte = 1u
    ): CommandLong {
        return CommandLong(
            targetSystem = targetSystem,
            targetComponent = targetComponent,
            command = MavEnumValue.of(MavCmd.PREFLIGHT_CALIBRATION),
            confirmation = 0u,
            param1 = 1f, // IMU calibration
            param2 = 0f,
            param3 = 0f,
            param4 = 0f,
            param5 = 0f,
            param6 = 0f,
            param7 = 0f
        )
    }

    fun createBarometerCalibrationCommand(
        targetSystem: UByte = 1u,
        targetComponent: UByte = 1u
    ): CommandLong {
        return CommandLong(
            targetSystem = targetSystem,
            targetComponent = targetComponent,
            command = MavEnumValue.of(MavCmd.PREFLIGHT_CALIBRATION),
            confirmation = 0u,
            param1 = 0f, // IMU
            param2 = 0f, // Magnetometer
            param3 = 1f, // Barometer calibration
            param4 = 0f,
            param5 = 0f,
            param6 = 0f,
            param7 = 0f
        )
    }
}