package com.example.aerogcsclone.uimain

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.maps.android.compose.MapType

@Composable
fun GcsMap(
    telemetryState: TelemetryState,
    points: List<LatLng> = emptyList(),
    onMapClick: (LatLng) -> Unit = {},
    cameraPositionState: CameraPositionState? = null,
    mapType: MapType = MapType.NORMAL,
    autoCenter: Boolean = true // new flag to control automatic recentering
) {
    val cameraState = cameraPositionState ?: rememberCameraPositionState()

    // Defensive: Use default position if telemetry is missing
    val lat = telemetryState.latitude
    val lon = telemetryState.longitude
    val hasTelemetry = lat != null && lon != null
    val defaultLatLng = LatLng(19.0760, 72.8777) // Mumbai as fallback

    Log.d("GcsMap", "Rendering map: lat=$lat lon=$lon points=${points.size} connected=${telemetryState.connected}")

    if (autoCenter) {
        if (hasTelemetry) {
            cameraState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat!!, lon!!), 16f))
        } else {
            cameraState.move(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 10f))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapType = mapType),
            onMapClick = { latLng -> onMapClick(latLng) }
        ) {
            // Live drone marker
            if (hasTelemetry) {
                Marker(state = MarkerState(position = LatLng(lat!!, lon!!)), title = "Drone Location")
            }
            // User-drawn markers
            points.forEachIndexed { index, point ->
                Marker(state = MarkerState(position = point), title = "WP ${index + 1}")
            }
            // Draw polyline connecting waypoints
            if (points.size > 1) {
                Polyline(points = points, width = 4f)
            }
            // If 4 or more points, draw a simple grid over bounding box
            if (points.size >= 4) {
                val lats = points.map { it.latitude }
                val lons = points.map { it.longitude }
                val minLat = lats.minOrNull() ?: 0.0
                val maxLat = lats.maxOrNull() ?: 0.0
                val minLon = lons.minOrNull() ?: 0.0
                val maxLon = lons.maxOrNull() ?: 0.0
                val latSteps = listOf(minLat, (minLat + maxLat) / 2.0, maxLat)
                val lonSteps = listOf(minLon, (minLon + maxLon) / 2.0, maxLon)
                lonSteps.forEach { lonVal ->
                    val line = listOf(LatLng(minLat, lonVal), LatLng(maxLat, lonVal))
                    Polyline(points = line, width = 2f, color = Color.Gray)
                }
                latSteps.forEach { latVal ->
                    val line = listOf(LatLng(latVal, minLon), LatLng(latVal, maxLon))
                    Polyline(points = line, width = 2f, color = Color.Gray)
                }
            }
        }
        if (!hasTelemetry) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No telemetry data. Waiting for drone position...", fontSize = 18.sp, color = Color.Red)
            }
        }
    }
}