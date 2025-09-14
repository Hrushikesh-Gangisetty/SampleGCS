package com.example.aerogcsclone.uimain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun GcsMap(
    telemetryState: TelemetryState,
    cameraPositionState: CameraPositionState,
    waypoints: List<LatLng>,
    showCrosshair: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
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

            // Waypoint markers
            waypoints.forEachIndexed { index, waypoint ->
                Marker(
                    state = MarkerState(position = waypoint),
                    title = "Waypoint ${index + 1}"
                )
            }

            // Draw polyline between waypoints
            if (waypoints.size > 1) {
                val polylinePoints = if (waypoints.size >= 3) {
                    waypoints + waypoints.first()
                } else {
                    waypoints
                }
                Polyline(
                    points = polylinePoints,
                    width = 4f
                )
            }
        }

        if (showCrosshair) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Crosshair",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
