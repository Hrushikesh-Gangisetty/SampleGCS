package com.example.aerogcsclone.uimain

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*

// Helper function to create medium-sized marker icons for waypoints
private fun createMediumMarker(hue: Float): BitmapDescriptor {
    // Create a medium-sized bitmap (50% of original marker size)
    val scale = 0.5f
    val width = (64 * scale).toInt() // Default marker is ~64px
    val height = (64 * scale).toInt()

    // Create a medium-sized colored circle as marker
    val mediumBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(mediumBitmap)

    // Draw a medium-sized colored circle
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = when (hue) {
            BitmapDescriptorFactory.HUE_AZURE -> android.graphics.Color.CYAN
            BitmapDescriptorFactory.HUE_VIOLET -> android.graphics.Color.MAGENTA
            BitmapDescriptorFactory.HUE_GREEN -> android.graphics.Color.GREEN
            BitmapDescriptorFactory.HUE_RED -> android.graphics.Color.RED
            BitmapDescriptorFactory.HUE_ORANGE -> android.graphics.Color.rgb(255, 165, 0)
            else -> android.graphics.Color.BLUE
        }
        style = android.graphics.Paint.Style.FILL
    }

    canvas.drawCircle(width / 2f, height / 2f, width / 2f - 1, paint)

    // Add a border for visibility
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(width / 2f, height / 2f, width / 2f - 2, paint)

    return BitmapDescriptorFactory.fromBitmap(mediumBitmap)
}

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
    // Geofence parameters - now using polygon instead of circle
    geofencePolygon: List<LatLng> = emptyList(),
    geofenceEnabled: Boolean = false
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

    // Create medium-sized marker icons for waypoints (50% of default size)
    val mediumBlueMarker = remember { createMediumMarker(BitmapDescriptorFactory.HUE_AZURE) }
    val mediumVioletMarker = remember { createMediumMarker(BitmapDescriptorFactory.HUE_VIOLET) }
    val mediumGreenMarker = remember { createMediumMarker(BitmapDescriptorFactory.HUE_GREEN) }
    val mediumRedMarker = remember { createMediumMarker(BitmapDescriptorFactory.HUE_RED) }
    val mediumOrangeMarker = remember { createMediumMarker(BitmapDescriptorFactory.HUE_ORANGE) }

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
        uiSettings = MapUiSettings(zoomControlsEnabled = false), // Disable zoom controls
        onMapClick = { latLng -> onMapClick(latLng) }
    ) {
        // Polygon geofence boundary overlay (replaces circular fence)
        if (geofenceEnabled && geofencePolygon.isNotEmpty()) {
            // Draw the polygon boundary
            if (geofencePolygon.size >= 3) {
                // Close the polygon by connecting last point to first
                val closedPolygon = geofencePolygon + geofencePolygon.first()
                Polyline(
                    points = closedPolygon,
                    width = 4f,
                    color = Color.Red
                )

                // Fill the polygon area with semi-transparent red
                Polygon(
                    points = geofencePolygon,
                    fillColor = Color.Red.copy(alpha = 0.2f),
                    strokeColor = Color.Red,
                    strokeWidth = 4f
                )
            }
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
                Marker(
                    state = MarkerState(position = point),
                    title = "WP ${index + 1}",
                    icon = mediumBlueMarker,
                    anchor = Offset(0.5f, 0.5f)
                )
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
                    icon = mediumVioletMarker,
                    anchor = Offset(0.5f, 0.5f)
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
                    isFirst -> "S" to mediumGreenMarker
                    isLast -> "E" to mediumRedMarker
                    else -> "G${index + 1}" to mediumOrangeMarker
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
