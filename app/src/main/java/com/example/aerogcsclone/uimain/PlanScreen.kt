// Kotlin
package com.example.aerogcsclone.uimain

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.LatLng
import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.definitions.common.MavFrame
import com.divpundir.mavlink.definitions.common.MavCmd
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MapType
import com.example.aerogcsclone.navigation.Screen
import kotlinx.coroutines.launch
import com.google.android.gms.maps.CameraUpdateFactory

@Composable
fun PlanScreen(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()
    val context = LocalContext.current

    // Top navigation bar
    TopNavBar(
        telemetryState = telemetryState,
        authViewModel = authViewModel,
        navController = navController
    )

    // State to toggle plan action buttons
    var showPlanActions by remember { mutableStateOf(false) }

    // Map type state
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

    // Camera state for map center / crosshair
    val cameraPositionState = rememberCameraPositionState()

    // Ensure we center the map once when Plan screen opens if we have telemetry
    var centeredOnce by remember { mutableStateOf(false) }
    LaunchedEffect(telemetryState.latitude, telemetryState.longitude) {
        val lat = telemetryState.latitude
        val lon = telemetryState.longitude
        if (!centeredOnce && lat != null && lon != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f))
            centeredOnce = true
        }
    }

    // Waypoint storage: geographic points and mission items
    val points = remember { mutableStateListOf<LatLng>() }
    val waypoints = remember { mutableStateListOf<MissionItemInt>() }

    // Helper to build MissionItemInt from LatLng
    fun buildMissionItemFromLatLng(
        latLng: LatLng,
        seq: Int,
        isTakeoff: Boolean = false,
        alt: Float = 10f
    ): MissionItemInt {
        return MissionItemInt(
            targetSystem = 0u,
            targetComponent = 0u,
            seq = seq.toUShort(),
            frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
            command = if (isTakeoff) MavEnumValue.of(MavCmd.NAV_TAKEOFF) else MavEnumValue.of(
                MavCmd.NAV_WAYPOINT
            ),
            current = 0u, // ensure 0 for compatibility
            autocontinue = 1u,
            param1 = 0f,
            param2 = 0f,
            param3 = 0f,
            param4 = 0f,
            x = (latLng.latitude * 1E7).toInt(),
            y = (latLng.longitude * 1E7).toInt(),
            z = alt
        )
    }

    // Handler when user taps on map: add marker and mission item
    val onMapClick: (LatLng) -> Unit = { latLng ->
        val seq = waypoints.size
        val isTakeoff = seq == 0
        val item = buildMissionItemFromLatLng(latLng, seq, isTakeoff)
        points.add(latLng)
        waypoints.add(item)
    }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Extra buttons shown above "Create Plan"
                if (showPlanActions) {
                    // Add at crosshair
                    FloatingActionButton(
                        onClick = {
                            // get map center
                            val center = cameraPositionState.position.target
                            val seq = waypoints.size
                            val isTakeoff = seq == 0
                            val item = buildMissionItemFromLatLng(center, seq, isTakeoff)
                            points.add(center)
                            waypoints.add(item)
                        },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Waypoint at Crosshair")
                    }

                    FloatingActionButton(
                        onClick = {
                            if (waypoints.isNotEmpty()) {
                                waypoints.removeAt(waypoints.lastIndex)
                                points.removeAt(points.lastIndex)
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Waypoints")
                    }

                    FloatingActionButton(
                        onClick = { waypoints.clear(); points.clear() },
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Map background
            GcsMap(
                telemetryState = telemetryState,
                points = points,
                onMapClick = onMapClick,
                cameraPositionState = cameraPositionState,
                mapType = mapType,
                autoCenter = false // do not force camera while planning (user pans)
            )

            // Small connection / FCU status indicator
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text(
                    "Connected: ${telemetryState.connected}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "FCU detected: ${telemetryState.fcuDetected}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Crosshair overlay
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Crosshair",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Left-side floating buttons (below TopNavBar)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 72.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Refresh button (moved here above Arm)
                FloatingActionButton(
                    onClick = {
                        val lat = telemetryState.latitude
                        val lon = telemetryState.longitude
                        if (lat != null && lon != null) {
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(lat, lon),
                                    16f
                                )
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "No GPS location available",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recenter Map")
                }

                // Arm button
                FloatingActionButton(
                    onClick = { telemetryViewModel.arm() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.FlightTakeoff, contentDescription = "Arm")
                }

                FloatingActionButton(
                    onClick = {
                        mapType =
                            if (mapType == MapType.SATELLITE) MapType.NORMAL else MapType.SATELLITE
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = "Toggle Map Type")
                }

                FloatingActionButton(
                    onClick = { /* TODO: handle Change Mode action */ },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Change Mode")
                }
            }

            // Bottom panel: upload and list
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            ) {
                Button(
                    onClick = {
                        telemetryViewModel.uploadMission(waypoints) { success, error ->
                            if (success) {
                                Toast.makeText(context, "Mission uploaded", Toast.LENGTH_SHORT)
                                    .show()
                                coroutineScope.launch { telemetryViewModel.readMissionFromFcu() }
                                navController.navigate(Screen.Main.route) {
                                    popUpTo(Screen.Plan.route) { inclusive = true }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    error ?: "Mission upload failed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = waypoints.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload Mission (${waypoints.size})")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        telemetryViewModel.readMissionFromFcu()
                        Toast.makeText(
                            context,
                            "Requested mission readback (check logs)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Text("Read Mission (debug)")
                    }
                    Button(onClick = {
                        telemetryViewModel.startMission { s, e ->
                            Toast.makeText(
                                context,
                                if (s) "Start sent" else (e ?: "Start failed"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Text("Start Mission")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Waypoint list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    Text("Waypoints:", style = MaterialTheme.typography.titleSmall)
                    waypoints.forEachIndexed { idx, wp ->
                        Text("#${idx + 1}: Lat=${wp.x / 1e7}, Lon=${wp.y / 1e7}, Alt=${wp.z}")
                    }
                }
            }
        }
    }
}
