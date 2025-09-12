package com.example.aerogcsclone.uimain

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.authentication.AuthViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlightTakeoff

@Composable
fun PlanScreen(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: handle create plan */ }) {
                Text("Create Plan")
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
                        .padding(start = 10.dp, top = 50.dp), // 72dp to push below TopNavBar
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
