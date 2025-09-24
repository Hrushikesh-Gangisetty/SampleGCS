package com.example.aerogcsclone.Telemetry

data class TelemetryState(

    val connected : Boolean = false,
    val fcuDetected : Boolean = false,
    //Altitude
    val altitudeMsl: Float? = null,
    val altitudeRelative: Float? = null,
    //Speeds
    val airspeed: Float? = null,
    val groundspeed: Float? = null,
    //Battery
    val voltage: Float? = null,
    val batteryPercent: Int? = null,
    val currentA : Float? = null,
    //Sat count and HDOP
    val sats : Int? = null,
    val hdop : Float? = null,
    //Latitude and Longitude
    val latitude : Double?= null,
    val longitude : Double? = null,

    val mode: String? = null,
    val armed: Boolean = false,
    val armable: Boolean = false,
    // Mission timer (seconds elapsed since mission start, null if not running)
    val missionElapsedSec: Long? = null,
    val lastMissionElapsedSec: Long? = null,
    val missionCompleted: Boolean = false,
    val totalDistanceMeters: Float? = null,
    // Formatted speed values for UI
    val formattedAirspeed: String? = null,
    val formattedGroundspeed: String? = null,
    val heading: Float? = null
)