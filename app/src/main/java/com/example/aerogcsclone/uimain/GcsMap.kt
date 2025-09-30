package com.example.aerogcsclone.uimain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.aerogcsclone.R
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*

@Composable
fun GcsMap(
    telemetryState: TelemetryState,
    points: List<LatLng> = emptyList(),
    onMapClick: (LatLng) -> Unit = {},
    cameraPositionState: CameraPositionState? = null,
    mapType: MapType = MapType.NORMAL,
    autoCenter: Boolean = true,
    // Grid survey parameters
    surveyPolygon: List<LatLng> = emptyList(),
    gridLines: List<List<LatLng>> = emptyList(),
    gridWaypoints: List<LatLng> = emptyList(),
    heading: Float? = null,
    // Fence parameters
    fenceCenter: LatLng? = null,
    fenceRadius: Float = 500f
) {
    val context = LocalContext.current
    val cameraState = cameraPositionState ?: rememberCameraPositionState()

    val visitedPositions = remember { mutableStateListOf<LatLng>() }

    // Load quadcopter drawable from res/drawable and scale to dp-based size
    val droneIcon = remember {
        runCatching {
            val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.d_image_prev_ui)
            val sizeDp = 64f
            val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
            val scaled = Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
            BitmapDescriptorFactory.fromBitmap(scaled)
        }.getOrNull()
    }

    val lat = telemetryState.latitude
    val lon = telemetryState.longitude
    if (autoCenter && lat != null && lon != null) {
        cameraState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f))
    }

    LaunchedEffect(lat, lon) {
        if (lat != null && lon != null) {
            val pos = LatLng(lat, lon)
            if (visitedPositions.isEmpty() || visitedPositions.last() != pos) {
                visitedPositions.add(pos)
                val maxLen = 2000
                if (visitedPositions.size > maxLen) {
                    val removeCount = visitedPositions.size - maxLen
                    repeat(removeCount) { visitedPositions.removeAt(0) }
                }
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        properties = MapProperties(mapType = mapType),
        onMapClick = { latLng -> onMapClick(latLng) }
    ) {
        // Fence boundary circle overlay
        fenceCenter?.let { center ->
            Circle(
                center = center,
                radius = fenceRadius.toDouble(),
                strokeColor = Color.Red,
                strokeWidth = 4f,
                fillColor = Color.Red.copy(alpha = 0.2f)
            )
        }

        // Drone marker using quadcopter image; centered via anchor Offset(0.5f, 0.5f)
        if (lat != null && lon != null) {
            Marker(
                state = MarkerState(position = LatLng(lat, lon)),
                title = "Drone",
                icon = droneIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                anchor = Offset(0.5f, 0.5f),
                rotation = heading ?: 0f
            )
        }

        // Regular waypoint markers and planned route (blue)
        if (points.isNotEmpty() && surveyPolygon.isEmpty()) {
            points.forEachIndexed { index, point ->
                Marker(state = MarkerState(position = point), title = "WP ${index + 1}")
            }
            if (points.size > 1) {
                key(points) {
                    Polyline(points = points, width = 4f, color = Color.Blue)
                }
            }
        }

        // Survey polygon outline (purple)
        if (surveyPolygon.isNotEmpty()) {
            surveyPolygon.forEachIndexed { index, point ->
                Marker(
                    state = MarkerState(position = point),
                    title = "P${index + 1}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                )
            }

            if (surveyPolygon.size > 2) {
                // Close the polygon by connecting last point to first
                val closedPolygon = surveyPolygon + surveyPolygon.first()
                Polyline(points = closedPolygon, width = 3f, color = Color.Magenta)
            } else if (surveyPolygon.size == 2) {
                Polyline(points = surveyPolygon, width = 3f, color = Color.Magenta)
            }
        }

        // Grid lines (green)
        gridLines.forEach { line ->
            if (line.size >= 2) {
                Polyline(
                    points = line,
                    width = 2f,
                    color = Color.Green
                )
            }
        }

        // Grid waypoints (first: S/green, last: E/red, others: orange)
        if (gridWaypoints.isNotEmpty()) {
            gridWaypoints.forEachIndexed { index, waypoint ->
                val isFirst = index == 0
                val isLast = index == gridWaypoints.lastIndex
                val (title, color) = when {
                    isFirst -> "S" to BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    isLast -> "E" to BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    else -> "G${index + 1}" to BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                }
                Marker(
                    state = MarkerState(position = waypoint),
                    title = title,
                    icon = color,
                    anchor = Offset(0.5f, 0.5f)
                )
            }
        }

        // Red polyline showing the drone's traveled path
        if (visitedPositions.size > 1) {
            Polyline(points = visitedPositions.toList(), width = 6f, color = Color.Red)
        }

        // Optional grid overlay for regular waypoints
        if (points.size >= 4 && surveyPolygon.isEmpty()) {
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
}
