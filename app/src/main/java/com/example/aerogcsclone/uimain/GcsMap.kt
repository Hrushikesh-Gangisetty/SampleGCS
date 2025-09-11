package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType

@Composable
fun MainPage(
    telemetryViewModel: SharedViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()
    var mapProperties by remember { mutableStateOf(MapProperties(mapType = MapType.NORMAL)) }
    var points by remember { mutableStateOf(listOf<LatLng>()) }
    var polygonClosed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopNavBar(telemetryState = telemetryState, navController = navController)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GcsMap(
                mapProperties = mapProperties,
                points = points,
                onMapClick = { latLng ->
                    if (!polygonClosed) {
                        points = points + latLng
                    }
                },
                onMarkerClick = { point ->
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

            StatusPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                telemetryState = telemetryState
            )

            FloatingButtons(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(12.dp)
            ) {
                mapProperties = mapProperties.copy(
                    mapType = if (mapProperties.mapType == MapType.NORMAL)
                        MapType.SATELLITE
                    else
                        MapType.NORMAL
                )
            }
        }
    }
}

@Composable
fun GcsMap(mapProperties: MapProperties, points: List<LatLng>, onMapClick: Any, onMarkerClick: Any) {

}

@Composable
fun StatusPanel(
    modifier: Modifier = Modifier,
    telemetryState: TelemetryState
) {
    Surface(
        modifier = modifier
            .width(500.dp)
            .height(120.dp),
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Alt: ${telemetryState.altitudeRelative ?: "N/A"}", color = Color.White)
                Text("Speed: ${telemetryState.groundspeed ?: "N/A"}", color = Color.White)
                Text("Area: N/A", color = Color.White)
                Text("Flow: N/A", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Obs Alt: N/A", color = Color.White)
                Text("Time: N/A", color = Color.White)
                Text("Distance: N/A", color = Color.White)
                Text("Consumed: N/A", color = Color.White)
            }
        }
    }
}

@Composable
fun FloatingButtons(modifier: Modifier = Modifier, onMapTypeChange: () -> Unit) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White)
        }
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
        }
        FloatingActionButton(onClick = onMapTypeChange, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Map, contentDescription = "Map Options", tint = Color.White)
        }
    }
}
