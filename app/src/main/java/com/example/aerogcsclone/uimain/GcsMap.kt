package com.example.aerogcsclone.uimain

//package com.example.aerogcsclone.uimain

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun GcsMap(
    mapProperties: MapProperties,
    points: List<LatLng>,
    onMapClick: (LatLng) -> Unit,
    onMarkerClick: (LatLng) -> Boolean
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(17.385, 78.486), 14f)
    }

    val uiSettings = remember { MapUISettings(zoomControlsEnabled = true) }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        onMapClick = onMapClick
    ) {
        // Markers
        points.forEachIndexed { index, point ->
            Marker(
                state = MarkerState(position = point),
                title = "Marker ${index + 1}",
                onClick = { onMarkerClick(point) }
            )
        }

        // Always draw polyline (whether open or closed)
        if (points.size > 1) {
            Polyline(
                points = points,
//                color = 0xFF000000.toInt(), // black outline
                width = 4f
            )
        }
    }
}