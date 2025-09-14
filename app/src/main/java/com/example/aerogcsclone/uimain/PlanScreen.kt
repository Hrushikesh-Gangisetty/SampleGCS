package com.example.aerogcsclone.uimain

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.authentication.AuthViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun PlanScreen(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()

    // State to toggle plan action buttons
    var showPlanActions by remember { mutableStateOf(false) }
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

    // Navigate back when mission is loaded
    LaunchedEffect(telemetryState.missionLoaded) {
        if (telemetryState.missionLoaded) {
            navController.popBackStack()
            telemetryViewModel.resetMissionLoaded()
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Extra buttons shown above "Create Plan"
                if (showPlanActions) {
                    FloatingActionButton(
                        onClick = {
                            val center = cameraPositionState.position.target
                            telemetryViewModel.addWaypoint(center)
                        },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Waypoints")
                    }

                    FloatingActionButton(
                        onClick = { telemetryViewModel.deleteLastWaypoint() },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Last Waypoint")
                    }

                    FloatingActionButton(
                        onClick = { telemetryViewModel.clearAllWaypoints() },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear Plan")
                    }

                    FloatingActionButton(
                        onClick = { telemetryViewModel.loadMission() },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Publish, contentDescription = "Load Mission")
                    }
                }

                // Main Create Plan button
                FloatingActionButton(
                    onClick = { showPlanActions = !showPlanActions },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Create Plan")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top navigation bar
            TopNavBar(
                telemetryState = telemetryState,
                authViewModel = authViewModel,
                navController = navController
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // Map background
                GcsMap(
                    telemetryState = telemetryState,
                    cameraPositionState = cameraPositionState,
                    waypoints = telemetryViewModel.waypoints,
                    showCrosshair = true
                )

                // Left-side floating buttons (below TopNavBar)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 72.dp), // push below TopNavBar
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = { telemetryViewModel.arm() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.FlightTakeoff, contentDescription = "Arm")
                    }

                    FloatingActionButton(
                        onClick = { /* TODO: handle Change Mode action */ },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Change Mode")
                    }
                }
            }
        }
    }
}
