package com.example.aerogcsclone.export

/**
 * Export format options for flight logs
 */
enum class ExportFormat(val displayName: String, val description: String) {
    JSON("JSON", "Complete flight data in JSON format"),
    CSV("CSV", "Telemetry data in spreadsheet format"),
    TLOG("TLOG", "MAVLink-style log format")
}
