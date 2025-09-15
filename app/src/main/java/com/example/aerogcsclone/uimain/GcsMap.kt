package com.example.aerogcsclone.uimain

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun GcsMap(
    telemetryState: TelemetryState,
    mapType: MapType
) {
    var points by remember { mutableStateOf(listOf<LatLng>()) }
    var polygonClosed by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState()

    // Update camera when telemetry changes (live location)
    LaunchedEffect(telemetryState.latitude, telemetryState.longitude) {
        val lat = telemetryState.latitude
        val lon = telemetryState.longitude
        if (lat != null && lon != null) {
            val newPosition = LatLng(lat, lon)
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(newPosition, 16f),
                durationMs = 1000
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = mapType), // ✅ Map type applied
        onMapClick = { latLng ->
            if (!polygonClosed) {
                points = points + latLng
            }
        }
    ) {
        // Live drone marker
        val lat = telemetryState.latitude
        val lon = telemetryState.longitude
        if (lat != null && lon != null) {
            Marker(
                state = MarkerState(position = LatLng(lat, lon)),
                title = "Drone Location"
            )
        }

        // User-drawn markers
        points.forEachIndexed { index, point ->
            Marker(
                state = MarkerState(position = point),
                title = "Marker ${index + 1}",
                onClick = {
                    if (points.size > 1 && !polygonClosed) {
                        val last = points.last()

                        if (point == points.first() && points.size > 2) {
                            points = points + point
                            polygonClosed = true
                        } else if (point != last) {
                            points = points + point
                        }
                    }
                    true
                }
            )
        }

        // Draw polyline (open or closed)
        if (points.size > 1) {
            Polyline(
                points = points,
                width = 4f
            )
        }
    }
}