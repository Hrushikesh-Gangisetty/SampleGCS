// Kotlin
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
import com.example.aerogcsclone.authentication.AuthViewModel
import com.google.maps.android.compose.MapType
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng

@Composable
fun MainPage(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()
    val context = LocalContext.current

    // Collect uploaded waypoints for display
    val uploadedWaypoints by telemetryViewModel.uploadedWaypoints.collectAsState()

    // Map camera state controlled from parent so refresh can move it
    val cameraPositionState = rememberCameraPositionState()

    // Map type state
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

    // Ensure we center the map once when MainPage opens if we have telemetry
    var centeredOnce by remember { mutableStateOf(false) }
    LaunchedEffect(telemetryState.latitude, telemetryState.longitude) {
        val lat = telemetryState.latitude
        val lon = telemetryState.longitude
        if (!centeredOnce && lat != null && lon != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f))
            centeredOnce = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopNavBar(
            telemetryState = telemetryState,
            authViewModel = authViewModel,
            navController = navController
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Pass uploadedWaypoints to GcsMap for blue markers/lines
            GcsMap(
                telemetryState = telemetryState,
                mapType = mapType,
                cameraPositionState = cameraPositionState,
                autoCenter = false
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
                    .padding(12.dp),
                onToggleMapType = {
                    mapType = if (mapType == MapType.SATELLITE) MapType.NORMAL else MapType.SATELLITE
                },
                onStartMission = {
                    telemetryViewModel.startMission { success, error ->
                        if (success) {
                            Toast.makeText(context, "Mission start sent", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, error ?: "Failed to start mission", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onRefresh = {
                    val lat = telemetryState.latitude
                    val lon = telemetryState.longitude
                    if (lat != null && lon != null) {
                        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f))
                    } else {
                        Toast.makeText(context, "No GPS location available", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
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
                Text("Speed: ${telemetryState.groundspeed}", color = Color.White)
                Text("Area: N/A", color = Color.White)
                Text("Flow: N/A", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Obs Alt: N/A", color = Color.White)
                // Format mission timer
                val timeStr = telemetryState.missionElapsedSec?.let { sec ->
                    val h = sec / 3600
                    val m = (sec % 3600) / 60
                    val s = sec % 60
                    if (h > 0) "%02d:%02d".format(h, m) else "%02d:%02d".format(m, s)
                } ?: "N/A"
                Text("Time: $timeStr", color = Color.White)
                // Format total distance
                val distStr = telemetryState.totalDistanceMeters?.let { dist ->
                    if (dist < 1000f) "%.0f m".format(dist)
                    else "%.2f km".format(dist / 1000f)
                } ?: "N/A"
                Text("Distance: $distStr", color = Color.White)
                Text("Consumed: N/A", color = Color.White)
            }
        }
    }
}

@Composable
fun FloatingButtons(
    modifier: Modifier = Modifier,
    onToggleMapType: () -> Unit,
    onStartMission: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingActionButton(onClick = { onStartMission() }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White)
        }
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
        FloatingActionButton(onClick = { onRefresh() }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
        }
        FloatingActionButton(
            onClick = { onToggleMapType() },
            containerColor = Color.Black.copy(alpha = 0.7f)
        ) {
            Icon(Icons.Default.Map, contentDescription = "Map Options", tint = Color.White)
        }
    }
}
