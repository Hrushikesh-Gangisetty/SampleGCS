package com.example.aerogcsclone.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility object for formatting timestamps consistently across the app
 */
object TimeFormatter {

    /**
     * Format Unix timestamp (milliseconds) to human-readable date and time
     * Example: "Dec 12, 2024 14:35:15"
     */
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Format Unix timestamp to date only
     * Example: "Dec 12, 2024"
     */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Format Unix timestamp to time only
     * Example: "14:35:15"
     */
    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Format Unix timestamp to short time (no seconds)
     * Example: "14:35"
     */
    fun formatShortTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Format duration in milliseconds to human-readable format
     * Example: "02:15:30" for 2 hours, 15 minutes, 30 seconds
     */
    fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    /**
     * Format timestamp for telemetry logs with millisecond precision
     * Example: "2024-12-12 14:35:15.757"
     */
    fun formatTelemetryTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Format timestamp relative to now
     * Example: "2 hours ago", "Just now", "Yesterday"
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            else -> formatDate(timestamp)
        }
    }

    /**
     * Get current timestamp in milliseconds
     */
    fun now(): Long = System.currentTimeMillis()
}

