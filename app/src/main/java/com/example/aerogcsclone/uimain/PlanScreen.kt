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
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu

@Composable
fun PlanScreen(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()

    // State to toggle plan action buttons
    var showPlanActions by remember { mutableStateOf(false) }

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
                // Map background
                GcsMap(telemetryState = telemetryState)

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
