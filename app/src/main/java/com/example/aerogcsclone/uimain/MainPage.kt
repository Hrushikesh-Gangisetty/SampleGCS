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
//import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.example.aerogcsclone.authentication.AuthViewModel
import com.google.maps.android.compose.MapType
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.sp
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import androidx.compose.ui.text.font.FontWeight
import com.example.aerogcsclone.telemetry.SharedViewModel
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.style.TextOverflow


@Composable
fun MainPage(
    telemetryViewModel: SharedViewModel,
    authViewModel: AuthViewModel,
    navController: NavHostController
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()
    val context = LocalContext.current

    // Collect area values from ViewModel
    val missionAreaFormatted by telemetryViewModel.missionAreaFormatted.collectAsState()
    val surveyAreaFormatted by telemetryViewModel.surveyAreaFormatted.collectAsState()
    val missionUploaded by telemetryViewModel.missionUploaded.collectAsState()

    // Decide which area string to show in the status panel
    val areaToDisplay = if (missionUploaded) {
        if (missionAreaFormatted.isNotBlank()) missionAreaFormatted else "N/A"
    } else {
        if (surveyAreaFormatted.isNotBlank()) surveyAreaFormatted else "N/A"
    }

    // Collect uploaded waypoints for display
    val uploadedWaypoints by telemetryViewModel.uploadedWaypoints.collectAsState()
    val surveyPolygon by telemetryViewModel.surveyPolygon.collectAsState()
    val gridLines by telemetryViewModel.gridLines.collectAsState()
    val gridWaypoints by telemetryViewModel.gridWaypoints.collectAsState()
    val geofenceEnabled by telemetryViewModel.geofenceEnabled.collectAsState()
    val geofencePolygon by telemetryViewModel.geofencePolygon.collectAsState()

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

    val notifications by telemetryViewModel.notifications.collectAsState()
    val isNotificationPanelVisible by telemetryViewModel.isNotificationPanelVisible.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Pass uploadedWaypoints to GcsMap for blue markers/lines
            GcsMap(
                telemetryState = telemetryState,
                points = uploadedWaypoints,
                surveyPolygon = surveyPolygon,
                gridLines = gridLines.map { listOf(it.first, it.second) },
                gridWaypoints = gridWaypoints,
                mapType = mapType,
                cameraPositionState = cameraPositionState,
                autoCenter = false,
                heading = telemetryState.heading,
                geofencePolygon = geofencePolygon,
                geofenceEnabled = geofenceEnabled
            )

            StatusPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                telemetryState = telemetryState,
                areaFormatted = areaToDisplay
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
                })

            if (isNotificationPanelVisible) {
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    NotificationPanel(notifications = notifications)
                }
            }

            // Place the TopNavBar inside the same Box so it overlays the map
            TopNavBar(
                telemetryState = telemetryState,
                authViewModel = authViewModel,
                navController = navController,
                onToggleNotificationPanel = { telemetryViewModel.toggleNotificationPanel() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
            )
        }

        // Mission Complete Popup (must be inside the composable)
        var lastMissionTime by remember { mutableStateOf<Long?>(null) }
        var lastMissionDistance by remember { mutableStateOf<Float?>(null) }
        var prevMissionCompleted by remember { mutableStateOf(false) }
        var prevMissionElapsedSec by remember { mutableStateOf<Long?>(null) }
        var missionJustCompleted by remember { mutableStateOf(false) }
        LaunchedEffect(telemetryState.missionCompleted, telemetryState.missionElapsedSec) {
            // Only show dialog if missionCompleted just transitioned to true AND timer is not running (i.e., mission just ended)
            val completedNow = !prevMissionCompleted && telemetryState.missionCompleted && telemetryState.missionElapsedSec == null && prevMissionElapsedSec != null
            if (completedNow) {
                lastMissionTime = telemetryState.lastMissionElapsedSec
                lastMissionDistance = telemetryState.totalDistanceMeters
                missionJustCompleted = true
            }
            prevMissionCompleted = telemetryState.missionCompleted
            prevMissionElapsedSec = telemetryState.missionElapsedSec
        }
        if (missionJustCompleted) {
            AlertDialog(
                onDismissRequest = {
                    missionJustCompleted = false
                },
                confirmButton = {
                    Button(
                        onClick = {
                            missionJustCompleted = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OK", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                title = {
                    Text("Mission completed!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val timeStr = lastMissionTime?.let { sec ->
                            val h = sec / 3600
                            val m = (sec % 3600) / 60
                            val s = sec % 60
                            if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
                        } ?: "N/A"
                        val distStr = lastMissionDistance?.let { dist ->
                            if (dist < 1000f) "%.0f m".format(dist)
                            else "%.2f km".format(dist / 1000f)
                        } ?: "N/A"
                        Text("Total time taken: $timeStr", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total distance covered: $distStr", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            )
        }
    }
}


@Composable
fun StatusPanel(
    modifier: Modifier = Modifier,
    telemetryState: TelemetryState,
    areaFormatted: String
) {
    Surface(
        modifier = modifier
            // Slightly larger but still compact: increase min/max width and height a bit
            .widthIn(min = 140.dp, max = 380.dp)
            .heightIn(min = 48.dp, max = 74.dp),
        // Set 78% transparency (i.e., 78% transparent => 22% opacity)
        color = Color.Black.copy(alpha = 0.22f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Alt: ${telemetryState.altitudeRelative ?: "N/A"}",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Speed: ${telemetryState.formattedGroundspeed ?: "N/A"}",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Area: ${areaFormatted}",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1.05f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Flow: N/A",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Obs Alt: N/A",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Format mission timer
                val timeStr = telemetryState.missionElapsedSec?.let { sec ->
                    val m = (sec % 3600) / 60
                    val s = sec % 60
                    "%02d:%02d".format(m, s)
                } ?: "N/A"
                Text(
                    "Time: $timeStr",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Format total distance
                val distStr = telemetryState.totalDistanceMeters?.let { dist ->
                    if (dist < 1000f) "%.0f m".format(dist)
                    else "%.2f km".format(dist / 1000f)
                } ?: "N/A"
                Text(
                    "Distance: $distStr",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1.05f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Consumed: N/A",
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
        verticalArrangement = Arrangement.spacedBy(8.dp), // reduced spacing
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingActionButton(
            onClick = { onStartMission() },
            containerColor = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp) // reduced button size
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Start",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        FloatingActionButton(
            onClick = { },
            containerColor = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        FloatingActionButton(
            onClick = { onRefresh() },
            containerColor = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        FloatingActionButton(
            onClick = { onToggleMapType() },
            containerColor = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.Map,
                contentDescription = "Map Options",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
