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

@Composable
fun MainPage(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()
    val uploadedWaypoints by telemetryViewModel.uploadedWaypoints.collectAsState()
    val context = LocalContext.current

    // 🔑 Map type state
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

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
            if (!telemetryState.connected) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text("Waiting for connection to drone...", modifier = Modifier.padding(top = 16.dp))
                }
            } else {
                // ✅ Pass telemetryState, mapType, and uploaded waypoints to GcsMap
                GcsMap(
                    telemetryState = telemetryState,
                    points = uploadedWaypoints,
                    mapType = mapType
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
                    }
                )
            }
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
fun FloatingButtons(
    modifier: Modifier = Modifier,
    onToggleMapType: () -> Unit,
    onStartMission: () -> Unit
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
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
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