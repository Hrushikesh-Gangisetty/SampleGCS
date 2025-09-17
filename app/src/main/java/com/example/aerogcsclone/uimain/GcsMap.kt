package com.example.aerogcsclone.uimain

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.aerogcsclone.R
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.maps.android.compose.MapType

@Composable
fun GcsMap(
    telemetryState: TelemetryState,
    points: List<LatLng> = emptyList(),
    dronePath: List<LatLng> = emptyList(),
    onMapClick: (LatLng) -> Unit = {},
    cameraPositionState: CameraPositionState? = null,
    mapType: MapType = MapType.NORMAL,
    autoCenter: Boolean = true // new flag to control automatic recentering
) {
    val cameraState = cameraPositionState ?: rememberCameraPositionState()
    val context = LocalContext.current

    // Update camera when telemetry changes (live location) only if autoCenter is true
    val lat = telemetryState.latitude
    val lon = telemetryState.longitude
    if (autoCenter && lat != null && lon != null) {
        // animate only when autoCenter is requested
        cameraState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f))
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(mapType = mapType),
        onMapClick = { latLng -> onMapClick(latLng) }
    ) {
        // Live drone marker
        if (lat != null && lon != null) {
            val droneIcon = bitmapDescriptorFromVector(context, R.drawable.ic_drone)
            Marker(state = MarkerState(position = LatLng(lat, lon)), title = "Drone Location", icon = droneIcon)
        }

        // User-drawn markers
        points.forEachIndexed { index, point ->
            Marker(state = MarkerState(position = point), title = "WP ${index + 1}")
        }

        // Draw polyline connecting waypoints
        if (points.size > 1) {
            Polyline(points = points, width = 4f)
        }

        // Draw drone path
        if (dronePath.size > 1) {
            Polyline(points = dronePath, width = 4f, color = Color.Red)
        }

        // If 4 or more points, draw a simple grid over bounding box
        if (points.size >= 4) {
            val lats = points.map { it.latitude }
            val lons = points.map { it.longitude }
            val minLat = lats.minOrNull() ?: 0.0
            val maxLat = lats.maxOrNull() ?: 0.0
            val minLon = lons.minOrNull() ?: 0.0
            val maxLon = lons.maxOrNull() ?: 0.0

            // draw two vertical and two horizontal lines (3x3 grid)
            val latSteps = listOf(minLat, (minLat + maxLat) / 2.0, maxLat)
            val lonSteps = listOf(minLon, (minLon + maxLon) / 2.0, maxLon)

            // vertical lines
            lonSteps.forEach { lonVal ->
                val line = listOf(LatLng(minLat, lonVal), LatLng(maxLat, lonVal))
                Polyline(points = line, width = 2f, color = Color.Gray)
            }
            // horizontal lines
            latSteps.forEach { latVal ->
                val line = listOf(LatLng(latVal, minLon), LatLng(latVal, maxLon))
                Polyline(points = line, width = 2f, color = Color.Gray)
            }
        }
    }
}

// Helper function to convert a vector drawable to a BitmapDescriptor
fun bitmapDescriptorFromVector(context: Context, @DrawableRes vectorResId: Int): BitmapDescriptor? {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId)
    vectorDrawable?.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
    val bitmap = Bitmap.createBitmap(vectorDrawable?.intrinsicWidth ?: 0, vectorDrawable?.intrinsicHeight ?: 0, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    vectorDrawable?.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}