package com.example.aerogcsclone.telemetry

import java.text.SimpleDateFormat
import java.util.*

enum class NotificationType {
    ERROR,
    WARNING,
    SUCCESS,
    INFO
}

data class Notification(
    val message: String,
    val type: NotificationType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)