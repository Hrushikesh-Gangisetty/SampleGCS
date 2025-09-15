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
import androidx.compose.material.icons.filled.*
import com.google.maps.android.compose.MapType

@Composable
fun PlanScreen(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()

    // State to toggle plan action buttons
    var showPlanActions by remember { mutableStateOf(false) }

    // ✅ Map type state (same as in MainPage)
    var mapType by remember { mutableStateOf(MapType.NORMAL) }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Extra buttons shown above "Create Plan"
                if (showPlanActions) {
                    FloatingActionButton(
                        onClick = { /* TODO: Add Waypoints */ },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Waypoints")
                    }

                    FloatingActionButton(
                        onClick = { /* TODO: Delete Waypoints */ },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Waypoints")
                    }

                    FloatingActionButton(
                        onClick = { /* TODO: Clear Plan */ },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear Plan")
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
                // ✅ Pass selected mapType to GcsMap
                GcsMap(telemetryState = telemetryState, mapType = mapType)

                // Left-side floating buttons (below TopNavBar)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 72.dp), // push below TopNavBar
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ✅ Map toggle button ABOVE Arm button
                    FloatingActionButton(
                        onClick = {
                            mapType = if (mapType == MapType.NORMAL) MapType.SATELLITE else MapType.NORMAL
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = "Toggle Map Type")
                    }

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
