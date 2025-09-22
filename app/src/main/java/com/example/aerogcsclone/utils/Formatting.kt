package com.example.aerogcsclone.utils

fun formatSpeed(speed: Float?): String {
    return if (speed != null) {
        String.format("%.1f m/s", speed)
    } else {
        "N/A"
    }
}
