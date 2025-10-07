package com.example.aerogcsclone.manager

import com.MAVLink.common.msg_command_long
import com.MAVLink.enums.MAV_CMD

object CalibrationCommands {

    fun createImuCalibrationCommand(
        targetSystem: Int = 1,
        targetComponent: Int = 1
    ): msg_command_long {
        return msg_command_long().apply {
            this.target_system = targetSystem.toUByte()
            this.target_component = targetComponent.toUByte()
            this.command = MAV_CMD.MAV_CMD_PREFLIGHT_CALIBRATION.toUShort()
            this.confirmation = 0u
            this.param1 = 1f // IMU calibration
            this.param2 = 0f
            this.param3 = 0f
            this.param4 = 0f
            this.param5 = 0f
            this.param6 = 0f
            this.param7 = 0f
        }
    }
}