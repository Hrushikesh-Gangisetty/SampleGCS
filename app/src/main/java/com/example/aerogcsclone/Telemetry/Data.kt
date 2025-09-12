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
    val longitude : Double? = null

    val mode: String? = null,
    val armed: Boolean = false,
    val armable: Boolean = false,
)