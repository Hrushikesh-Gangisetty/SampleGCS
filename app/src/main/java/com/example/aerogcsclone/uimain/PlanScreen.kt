package com.example.aerogcsclone.uimain

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.authentication.AuthViewModel
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
import com.example.aerogcsclone.grid.*
import java.util.Locale

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
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

    // State management
    var showPlanActions by remember { mutableStateOf(false) }
    var isGridSurveyMode by remember { mutableStateOf(false) }
    var showGridControls by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

    // Grid survey parameters
    var lineSpacing by remember { mutableStateOf(30f) }
    var gridAngle by remember { mutableStateOf(0f) }
    var surveySpeed by remember { mutableStateOf(10f) }
    var surveyAltitude by remember { mutableStateOf(60f) }

    // Grid state
    var surveyPolygon by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var gridResult by remember { mutableStateOf<GridSurveyResult?>(null) }
    val gridGenerator = remember { GridGenerator() }

    // Camera and waypoint state
    val cameraPositionState = rememberCameraPositionState()
    val points = remember { mutableStateListOf<LatLng>() }
    val waypoints = remember { mutableStateListOf<MissionItemInt>() }
    val coroutineScope = rememberCoroutineScope()

    // Center map once when screen opens
    var centeredOnce by remember { mutableStateOf(false) }
    LaunchedEffect(telemetryState.latitude, telemetryState.longitude) {
        val lat = telemetryState.latitude
        val lon = telemetryState.longitude
        if (!centeredOnce && lat != null && lon != null) {
            cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f))
            centeredOnce = true
        }
    }

    // Helper functions
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
            command = if (isTakeoff) MavEnumValue.of(MavCmd.NAV_TAKEOFF) else MavEnumValue.of(MavCmd.NAV_WAYPOINT),
            current = 0u,
            autocontinue = 1u,
            param1 = 0f, param2 = 0f, param3 = 0f, param4 = 0f,
            x = (latLng.latitude * 1E7).toInt(),
            y = (latLng.longitude * 1E7).toInt(),
            z = alt
        )
    }

    fun regenerateGrid() {
        if (surveyPolygon.size >= 3) {
            val params = GridSurveyParams(
                lineSpacing = lineSpacing,
                gridAngle = gridAngle,
                speed = surveySpeed,
                altitude = surveyAltitude,
                includeSpeedCommands = true
            )
            gridResult = gridGenerator.generateGridSurvey(surveyPolygon, params)
        }
    }

    // Map click handler
    val onMapClick: (LatLng) -> Unit = { latLng ->
        if (isGridSurveyMode) {
            surveyPolygon = surveyPolygon + latLng
            if (surveyPolygon.size >= 3) {
                regenerateGrid()
            }
        } else {
            val seq = waypoints.size
            val isTakeoff = seq == 0
            val item = buildMissionItemFromLatLng(latLng, seq, isTakeoff)
            points.add(latLng)
            waypoints.add(item)
            Toast.makeText(context, "Waypoints: ${points.joinToString { "(${it.latitude},${it.longitude})" }}", Toast.LENGTH_SHORT).show()
        }
    }

    // Update grid when parameters change
    LaunchedEffect(lineSpacing, gridAngle, surveySpeed, surveyAltitude, surveyPolygon) {
        if (isGridSurveyMode) {
            regenerateGrid()
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showPlanActions) {
                    FloatingActionButton(
                        onClick = {
                            val center = cameraPositionState.position.target
                            if (isGridSurveyMode) {
                                surveyPolygon = surveyPolygon + center
                                if (surveyPolygon.size >= 3) regenerateGrid()
                            } else {
                                val seq = waypoints.size
                                val item = buildMissionItemFromLatLng(center, seq, seq == 0)
                                points.add(center)
                                waypoints.add(item)
                            }
                        },
                        modifier = Modifier.padding(bottom = 12.dp).size(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add at Crosshair")
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isGridSurveyMode) {
                                if (surveyPolygon.isNotEmpty()) {
                                    surveyPolygon = surveyPolygon.dropLast(1)
                                    if (surveyPolygon.size >= 3) regenerateGrid() else gridResult = null
                                }
                            } else {
                                if (waypoints.isNotEmpty()) {
                                    waypoints.removeAt(waypoints.lastIndex)
                                    points.removeAt(points.lastIndex)
                                }
                            }
                        },
                        modifier = Modifier.padding(bottom = 12.dp).size(56.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Last")
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isGridSurveyMode) {
                                surveyPolygon = emptyList()
                                gridResult = null
                            } else {
                                waypoints.clear()
                                points.clear()
                            }
                        },
                        modifier = Modifier.padding(bottom = 12.dp).size(56.dp)
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear All")
                    }
                }

                FloatingActionButton(
                    onClick = { showPlanActions = !showPlanActions },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Plan Menu")
                }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Map
            GcsMap(
                telemetryState = telemetryState,
                points = if (isGridSurveyMode) emptyList() else points,
                onMapClick = onMapClick,
                cameraPositionState = cameraPositionState,
                mapType = mapType,
                autoCenter = false,
                surveyPolygon = if (isGridSurveyMode) surveyPolygon else emptyList(),
                gridLines = gridResult?.gridLines ?: emptyList(),
                gridWaypoints = gridResult?.waypoints?.map { it.position } ?: emptyList()
            )

            // Status indicator
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
                Text(
                    "Mode: ${if (isGridSurveyMode) "Grid Survey" else "Waypoints"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGridSurveyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isGridSurveyMode) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Crosshair
            Box(
                modifier = Modifier.size(36.dp).align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Crosshair",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Left sidebar buttons
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 72.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        isGridSurveyMode = !isGridSurveyMode
                        showGridControls = isGridSurveyMode
                        if (isGridSurveyMode) {
                            points.clear()
                            waypoints.clear()
                        } else {
                            surveyPolygon = emptyList()
                            gridResult = null
                        }
                    },
                    containerColor = if (isGridSurveyMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.GridOn,
                        contentDescription = "Grid Survey Mode",
                        tint = if (isGridSurveyMode) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                FloatingActionButton(
                    onClick = {
                        val lat = telemetryState.latitude
                        val lon = telemetryState.longitude
                        if (lat != null && lon != null) {
                            cameraPositionState.move(
                                CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 16f)
                            )
                        } else {
                            Toast.makeText(context, "No GPS location available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Recenter")
                }

                FloatingActionButton(
                    onClick = { telemetryViewModel.arm() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.FlightTakeoff, contentDescription = "Arm")
                }

                FloatingActionButton(
                    onClick = { mapType = if (mapType == MapType.SATELLITE) MapType.NORMAL else MapType.SATELLITE },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = "Toggle Map")
                }
            }

            // Grid controls panel
            if (showGridControls) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .width(320.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Header with title and close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Grid Survey Parameters",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { showGridControls = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Panel",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Line Spacing
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Line Spacing", color = Color.White, modifier = Modifier.weight(1f))
                                Text("${lineSpacing.toInt()} m", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = lineSpacing,
                                onValueChange = { lineSpacing = it },
                                valueRange = 10f..100f,
                                steps = 17,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }

                        // Grid Angle
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Grid Angle", color = Color.White, modifier = Modifier.weight(1f))
                                Text("${gridAngle.toInt()}°", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = gridAngle,
                                onValueChange = { gridAngle = it },
                                valueRange = 0f..180f,
                                steps = 35,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }

                        // Survey Speed
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Speed", color = Color.White, modifier = Modifier.weight(1f))
                                Text("${surveySpeed.toInt()} m/s", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = surveySpeed,
                                onValueChange = { surveySpeed = it },
                                valueRange = 1f..20f,
                                steps = 18,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }

                        // Survey Altitude
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Altitude", color = Color.White, modifier = Modifier.weight(1f))
                                Text("${surveyAltitude.toInt()} m", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = surveyAltitude,
                                onValueChange = { surveyAltitude = it },
                                valueRange = 10f..120f,
                                steps = 21,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }

                        gridResult?.let { result ->
                            HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Grid Statistics:", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Waypoints: ${result.waypoints.size}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                Text("Lines: ${result.numLines}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                Text("Distance: ${String.format(Locale.US, "%.1f", result.totalDistance / 1000)}km", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                Text("Time: ${String.format(Locale.US, "%.1f", result.estimatedTime / 60)}min", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (surveyPolygon.size < 3) {
                            Text(
                                "Tap map to add polygon vertices (need ${3 - surveyPolygon.size} more)",
                                color = Color.Yellow,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { surveyPolygon = emptyList(); gridResult = null },
                                modifier = Modifier.weight(1f),
                                enabled = surveyPolygon.isNotEmpty()
                            ) {
                                Text("Clear")
                            }

                            Button(
                                onClick = {
                                    if (surveyPolygon.size >= 3) {
                                        // Set grid angle perpendicular to longest side for agricultural spraying
                                        val longestSideAngle = gridGenerator.calculateOptimalGridAngle(surveyPolygon)
                                        gridAngle = (longestSideAngle + 90f) % 360f
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = surveyPolygon.size >= 3
                            ) {
                                Text("Auto Angle")
                            }
                        }
                    }
                }
            }

            // Upload button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            ) {
                if (isGridSurveyMode) {
                    Button(
                        onClick = {
                            gridResult?.let { result ->
                                val homeLat = telemetryState.latitude ?: 0.0
                                val homeLon = telemetryState.longitude ?: 0.0
                                val homePosition = LatLng(homeLat, homeLon)
                                val builtMission = GridMissionConverter.convertToMissionItems(result, homePosition)

                                telemetryViewModel.uploadMission(builtMission) { success, error ->
                                    if (success) {
                                        Toast.makeText(context, "Grid survey uploaded", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch { telemetryViewModel.readMissionFromFcu() }
                                        navController.navigate(Screen.Main.route) {
                                            popUpTo(Screen.Plan.route) { inclusive = true }
                                        }
                                    } else {
                                        Toast.makeText(context, error ?: "Upload failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = gridResult?.waypoints?.isNotEmpty() == true,
                        modifier = Modifier.widthIn(min = 180.dp, max = 340.dp).align(Alignment.CenterHorizontally)
                    ) {
                        val count = gridResult?.waypoints?.size ?: 0
                        Text("Upload Grid Survey ($count waypoints)")
                    }
                } else {
                    Button(
                        onClick = {
                            val builtMission = mutableListOf<MissionItemInt>()
                            val homeLat = telemetryState.latitude ?: 0.0
                            val homeLon = telemetryState.longitude ?: 0.0
                            val homeAlt = telemetryState.altitudeMsl ?: 10f

                            builtMission.add(
                                MissionItemInt(
                                    targetSystem = 0u, targetComponent = 0u, seq = 0u,
                                    frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                                    command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
                                    current = 1u, autocontinue = 1u,
                                    param1 = 0f, param2 = 0f, param3 = 0f, param4 = 0f,
                                    x = (homeLat * 1E7).toInt(), y = (homeLon * 1E7).toInt(), z = homeAlt
                                )
                            )

                            builtMission.add(
                                MissionItemInt(
                                    targetSystem = 0u, targetComponent = 0u, seq = 1u,
                                    frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                                    command = MavEnumValue.of(MavCmd.NAV_TAKEOFF),
                                    current = 0u, autocontinue = 1u,
                                    param1 = 0f, param2 = 0f, param3 = 0f, param4 = 0f,
                                    x = (homeLat * 1E7).toInt(), y = (homeLon * 1E7).toInt(), z = 10f
                                )
                            )

                            points.forEachIndexed { idx, latLng ->
                                val seq = idx + 2
                                val isLast = idx == points.lastIndex
                                builtMission.add(
                                    MissionItemInt(
                                        targetSystem = 0u, targetComponent = 0u, seq = seq.toUShort(),
                                        frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
                                        command = if (isLast) MavEnumValue.of(MavCmd.NAV_LAND) else MavEnumValue.of(MavCmd.NAV_WAYPOINT),
                                        current = 0u, autocontinue = 1u,
                                        param1 = 0f, param2 = 0f, param3 = 0f, param4 = 0f,
                                        x = (latLng.latitude * 1E7).toInt(),
                                        y = (latLng.longitude * 1E7).toInt(), z = 10f
                                    )
                                )
                            }

                            telemetryViewModel.uploadMission(builtMission) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Mission uploaded", Toast.LENGTH_SHORT).show()
                                    coroutineScope.launch { telemetryViewModel.readMissionFromFcu() }
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.Plan.route) { inclusive = true }
                                    }
                                } else {
                                    Toast.makeText(context, error ?: "Upload failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = points.isNotEmpty(),
                        modifier = Modifier.widthIn(min = 180.dp, max = 340.dp).align(Alignment.CenterHorizontally)
                    ) {
                        Text("Upload Mission (${points.size})")
                    }
                }
            }
        }
    }
}
