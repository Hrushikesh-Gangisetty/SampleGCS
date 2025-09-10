package com.example.aerogcsclone.uimain

//package com.example.aerogcsclone.uimain

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun GcsMap() {
    var points by remember { mutableStateOf(listOf<LatLng>()) }
    var polygonClosed by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(17.385, 78.486), 14f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapClick = { latLng ->
            if (!polygonClosed) {
                points = points + latLng
            }
        }
    ) {
        // Markers
        points.forEachIndexed { index, point ->
            Marker(
                state = MarkerState(position = point),
                title = "Marker ${index + 1}",
                onClick = {
                    if (points.size > 1 && !polygonClosed) {
                        val last = points.last()

                        // If clicked marker = first point → close polygon
                        if (point == points.first() && points.size > 2) {
                            points = points + point
                            polygonClosed = true
                        } else if (point != last) {
                            // Otherwise connect to clicked marker
                            points = points + point
                        }
                    }
                    true
                }
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