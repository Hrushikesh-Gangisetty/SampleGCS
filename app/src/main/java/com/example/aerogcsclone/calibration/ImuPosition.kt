package com.example.aerogcsclone.calibration

enum class ImuPosition(val mavlinkValue: UInt) {
    LEVEL(1u),
    ON_RIGHT_SIDE(3u),
    ON_LEFT_SIDE(2u),
    NOSE_DOWN(4u),
    NOSE_UP(5u),
    ON_ITS_BACK(6u);

    companion object {
        fun fromInstruction(instruction: String): ImuPosition? {
            return when {
                "level" in instruction.lowercase() -> LEVEL
                "left" in instruction.lowercase() -> ON_LEFT_SIDE
                "right" in instruction.lowercase() -> ON_RIGHT_SIDE
                "nose down" in instruction.lowercase() -> NOSE_DOWN
                "nose up" in instruction.lowercase() -> NOSE_UP
                "back" in instruction.lowercase() -> ON_ITS_BACK
                else -> null
            }
        }
    }
}