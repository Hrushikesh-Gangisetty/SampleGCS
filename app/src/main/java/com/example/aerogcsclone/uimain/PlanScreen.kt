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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.authentication.AuthViewModel
import com.example.aerogcsclone.database.GridParameters
import com.example.aerogcsclone.ui.components.SaveMissionDialog
import com.example.aerogcsclone.ui.components.MissionChoiceDialog
import com.example.aerogcsclone.ui.components.TemplateSelectionDialog
import com.example.aerogcsclone.viewmodel.MissionTemplateViewModel
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
    navController: NavHostController,
    missionTemplateViewModel: MissionTemplateViewModel = viewModel()
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()
    val missionTemplateUiState by missionTemplateViewModel.uiState.collectAsState()
    val templates by missionTemplateViewModel.templates.collectAsState(initial = emptyList())
    val fenceRadius by telemetryViewModel.fenceRadius.collectAsState()
    val geofenceEnabled by telemetryViewModel.geofenceEnabled.collectAsState()
    val geofencePolygon by telemetryViewModel.geofencePolygon.collectAsState()
    val context = LocalContext.current

    // State management
    var showPlanActions by remember { mutableStateOf(false) }
    var isGridSurveyMode by remember { mutableStateOf(false) }
    var showGridControls by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }

    // Mission template dialog states
    var showMissionChoiceDialog by remember { mutableStateOf(true) }
    var showSaveMissionDialog by remember { mutableStateOf(false) }
    var showTemplateSelectionDialog by remember { mutableStateOf(false) }
    var hasStartedPlanning by remember { mutableStateOf(false) }

    // Grid survey parameters
    var lineSpacing by remember { mutableStateOf(30f) }
    var gridAngle by remember { mutableStateOf(0f) }
    var surveySpeed by remember { mutableStateOf(10f) }
    var surveyAltitude by remember { mutableStateOf(60f) }

    // Grid state
    var surveyPolygon by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var gridResult by remember { mutableStateOf<GridSurveyResult?>(null) }
    val gridGenerator = remember { GridGenerator() }

    // Calculate fence center (centroid of surveyPolygon)
    val fenceCenter = remember(surveyPolygon) {
        if (surveyPolygon.isNotEmpty()) {
            val lat = surveyPolygon.map { it.latitude }.average()
            val lon = surveyPolygon.map { it.longitude }.average()
            LatLng(lat, lon)
        } else null
    }

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

    // Helper functions - moved before LaunchedEffect that uses them
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

            // Update SharedViewModel with the new survey polygon and grid waypoints
            telemetryViewModel.setSurveyPolygon(surveyPolygon)
            gridResult?.let { result ->
                telemetryViewModel.setGridWaypoints(result.waypoints.map { it.position })
                telemetryViewModel.setGridLines(result.gridLines)
            }
        }
    }

    // Handle mission template UI state changes
    LaunchedEffect(missionTemplateUiState) {
        missionTemplateUiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            missionTemplateViewModel.clearMessages()
        }

        missionTemplateUiState.successMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            missionTemplateViewModel.clearMessages()
            if (showSaveMissionDialog) {
                showSaveMissionDialog = false
            }
        }

        missionTemplateUiState.selectedTemplate?.let { template ->
            // Load template data into current state
            points.clear()
            waypoints.clear()

            if (template.isGridSurvey && template.gridParameters != null) {
                isGridSurveyMode = true
                showGridControls = true

                val gridParams = template.gridParameters
                lineSpacing = gridParams.lineSpacing
                gridAngle = gridParams.gridAngle
                surveySpeed = gridParams.surveySpeed
                surveyAltitude = gridParams.surveyAltitude
                surveyPolygon = gridParams.surveyPolygon

                if (surveyPolygon.size >= 3) {
                    regenerateGrid()
                }
            } else {
                isGridSurveyMode = false
                showGridControls = false
                points.addAll(template.waypointPositions)
                waypoints.addAll(template.waypoints)
            }

            // Center map on first waypoint if available
            if (template.waypointPositions.isNotEmpty()) {
                val firstPoint = template.waypointPositions.first()
                cameraPositionState.move(
                    CameraUpdateFactory.newLatLngZoom(firstPoint, 16f)
                )
            }

            missionTemplateViewModel.clearSelectedTemplate()
            Toast.makeText(context, "Template '${template.plotName}' loaded successfully", Toast.LENGTH_SHORT).show()
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

            // Update SharedViewModel for geofence calculation during planning
            telemetryViewModel.setPlanningWaypoints(points.toList())
        }
    }

    // Update grid when parameters change
    LaunchedEffect(lineSpacing, gridAngle, surveySpeed, surveyAltitude, surveyPolygon) {
        if (isGridSurveyMode) {
            regenerateGrid()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Main content with Scaffold
        Scaffold(
            floatingActionButton = {
                // Bottom right - Delete waypoint button only
                if (hasStartedPlanning) {
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
                                    // Update SharedViewModel for geofence calculation after deletion
                                    telemetryViewModel.setPlanningWaypoints(points.toList())
                                }
                            }
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Last Waypoint")
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
                    gridLines = gridResult?.gridLines?.map { pair -> listOf(pair.first, pair.second) } ?: emptyList(),
                    gridWaypoints = gridResult?.waypoints?.map { it.position } ?: emptyList(),
                    geofencePolygon = geofencePolygon,
                    geofenceEnabled = geofenceEnabled
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

                // Top right button column - new organized layout
                if (hasStartedPlanning) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 16.dp, top = 90.dp), // Moved up from 120dp to 90dp
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Add Point button with text and transparent background
                        ElevatedButton(
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
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.7f), // Keep transparent
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.elevatedButtonElevation(
                                defaultElevation = 6.dp
                            ),
                            modifier = Modifier.widthIn(min = 120.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Point")
                        }

                        // Save Mission button - now dark
                        ElevatedButton(
                            onClick = { showSaveMissionDialog = true },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF2E2E2E), // Dark gray background
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.elevatedButtonElevation(
                                defaultElevation = 6.dp
                            ),
                            modifier = Modifier.widthIn(min = 120.dp)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Mission")
                        }

                        // Upload Mission button - now dark
                        ElevatedButton(
                            onClick = {
                                if (isGridSurveyMode && gridResult != null) {
                                    // Grid survey mission upload
                                    val homeLat = telemetryState.latitude ?: 0.0
                                    val homeLon = telemetryState.longitude ?: 0.0
                                    val homePosition = LatLng(homeLat, homeLon)
                                    val builtMission = GridMissionConverter.convertToMissionItems(gridResult!!, homePosition)

                                    telemetryViewModel.uploadMission(builtMission) { success, error ->
                                        if (success) {
                                            Toast.makeText(context, "Grid mission uploaded", Toast.LENGTH_SHORT).show()
                                            coroutineScope.launch { telemetryViewModel.readMissionFromFcu() }
                                            navController.navigate(Screen.Main.route) {
                                                popUpTo(Screen.Plan.route) { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, error ?: "Failed to upload grid mission", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else if (points.isNotEmpty()) {
                                    // Regular waypoint mission upload
                                    val builtMission = mutableListOf<MissionItemInt>()
                                    val homeLat = telemetryState.latitude ?: 0.0
                                    val homeLon = telemetryState.longitude ?: 0.0
                                    val homeAlt = telemetryState.altitudeMsl ?: 10f

                                    // Add home location as first waypoint
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

                                    // Add takeoff command as second waypoint
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

                                    // Add user-defined waypoints
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
                                            Toast.makeText(context, error ?: "Failed to upload mission", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "No waypoints to upload", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = if (isGridSurveyMode) gridResult?.waypoints?.isNotEmpty() == true else points.isNotEmpty(),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF1A1A1A), // Darker background
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.elevatedButtonElevation(
                                defaultElevation = 6.dp
                            ),
                            modifier = Modifier.widthIn(min = 120.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload Mission")
                        }
                    }
                }

                // Crosshair
                if (hasStartedPlanning) {
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
                }

                // Left sidebar buttons
                if (hasStartedPlanning) {
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
                            Icon(Icons.Default.MyLocation, contentDescription = "Center on Drone")
                        }

                        FloatingActionButton(
                            onClick = {
                                mapType = if (mapType == MapType.SATELLITE) MapType.NORMAL else MapType.SATELLITE
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = "Toggle Map Type")
                        }
                    }
                }

                // Grid controls - restored original design with statistics
                if (showGridControls && hasStartedPlanning) {
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

                            HorizontalDivider(color = Color.Gray, thickness = 1.dp)

                            // Geofence Toggle (moved above buffer distance as requested)
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Enable Geofence",
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Switch(
                                        checked = geofenceEnabled,
                                        onCheckedChange = { telemetryViewModel.setGeofenceEnabled(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color.Green, // Green when ON
                                            uncheckedThumbColor = Color.White,
                                            uncheckedTrackColor = Color.Red // Red when OFF
                                        )
                                    )
                                }
                                if (geofenceEnabled) {
                                    Text(
                                        "Polygon fence active around mission plan",
                                        color = Color.Green,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    Text(
                                        "Geofence disabled",
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            // Buffer Distance Slider (moved below toggle as requested)
                            if (geofenceEnabled) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Buffer Distance", color = Color.White, modifier = Modifier.weight(1f))
                                        Text("${fenceRadius.toInt()} m", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = fenceRadius,
                                        onValueChange = { telemetryViewModel.setFenceRadius(it) },
                                        valueRange = 0f..50f, // Changed from 1-50m to 0-50m for zero start
                                        steps = 50,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.Green,
                                            activeTrackColor = Color.Green,
                                            inactiveTrackColor = Color.Gray
                                        )
                                    )
                                    Text(
                                        "Adjust polygon buffer distance around mission plan",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            gridResult?.let { result ->
                                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Grid Statistics:", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Waypoints: ${result.waypoints.size}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text("Lines: ${result.numLines}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text("Distance: ${String.format(Locale.US, "%.1f", result.totalDistance / 1000)}km", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text("Time: ${String.format(Locale.US, "%.1f", result.estimatedTime / 60)}min", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                    Text("Area: ${result.polygonArea}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Dialogs
                if (showMissionChoiceDialog && !hasStartedPlanning) {
                    MissionChoiceDialog(
                        onDismiss = {
                            showMissionChoiceDialog = false
                            hasStartedPlanning = true
                        },
                        onLoadExisting = {
                            showMissionChoiceDialog = false
                            showTemplateSelectionDialog = true
                        },
                        onCreateNew = {
                            showMissionChoiceDialog = false
                            hasStartedPlanning = true
                        },
                        hasTemplates = templates.isNotEmpty()
                    )
                }

                if (showTemplateSelectionDialog) {
                    TemplateSelectionDialog(
                        templates = templates,
                        onDismiss = {
                            showTemplateSelectionDialog = false
                            hasStartedPlanning = true
                        },
                        onSelectTemplate = { template ->
                            showTemplateSelectionDialog = false
                            hasStartedPlanning = true
                            missionTemplateViewModel.loadTemplate(template.id)
                        },
                        isLoading = missionTemplateUiState.isLoading
                    )
                }

                if (showSaveMissionDialog) {
                    SaveMissionDialog(
                        onDismiss = { showSaveMissionDialog = false },
                        onSave = { projectName, plotName ->
                            val currentGridParams = if (isGridSurveyMode) {
                                GridParameters(
                                    lineSpacing = lineSpacing,
                                    gridAngle = gridAngle,
                                    surveySpeed = surveySpeed,
                                    surveyAltitude = surveyAltitude,
                                    surveyPolygon = surveyPolygon
                                )
                            } else null

                            val waypointsToSave = if (isGridSurveyMode && gridResult != null) {
                                gridResult!!.waypoints.mapIndexed { index, gridWaypoint ->
                                    buildMissionItemFromLatLng(
                                        gridWaypoint.position,
                                        index,
                                        index == 0,
                                        gridWaypoint.altitude
                                    )
                                }
                            } else {
                                waypoints.toList()
                            }

                            val positionsToSave = if (isGridSurveyMode && gridResult != null) {
                                gridResult!!.waypoints.map { it.position }
                            } else {
                                points.toList()
                            }

                            missionTemplateViewModel.saveTemplate(
                                projectName = projectName,
                                plotName = plotName,
                                waypoints = waypointsToSave,
                                waypointPositions = positionsToSave,
                                isGridSurvey = isGridSurveyMode,
                                gridParameters = currentGridParams
                            )
                        },
                        isLoading = missionTemplateUiState.isLoading
                    )
                }
            }
        }
    }

    // Dialogs
    if (showMissionChoiceDialog && !hasStartedPlanning) {
        MissionChoiceDialog(
            onDismiss = {
                showMissionChoiceDialog = false
                hasStartedPlanning = true
            },
            onLoadExisting = {
                showMissionChoiceDialog = false
                showTemplateSelectionDialog = true
            },
            onCreateNew = {
                showMissionChoiceDialog = false
                hasStartedPlanning = true
            },
            hasTemplates = templates.isNotEmpty()
        )
    }

    if (showTemplateSelectionDialog) {
        TemplateSelectionDialog(
            templates = templates,
            onDismiss = {
                showTemplateSelectionDialog = false
                hasStartedPlanning = true
            },
            onSelectTemplate = { template ->
                showTemplateSelectionDialog = false
                hasStartedPlanning = true
                missionTemplateViewModel.loadTemplate(template.id)
            },
            isLoading = missionTemplateUiState.isLoading
        )
    }

    if (showSaveMissionDialog) {
        SaveMissionDialog(
            onDismiss = { showSaveMissionDialog = false },
            onSave = { projectName, plotName ->
                val currentGridParams = if (isGridSurveyMode) {
                    GridParameters(
                        lineSpacing = lineSpacing,
                        gridAngle = gridAngle,
                        surveySpeed = surveySpeed,
                        surveyAltitude = surveyAltitude,
                        surveyPolygon = surveyPolygon
                    )
                } else null

                val waypointsToSave = if (isGridSurveyMode && gridResult != null) {
                    gridResult!!.waypoints.mapIndexed { index, gridWaypoint ->
                        buildMissionItemFromLatLng(
                            gridWaypoint.position,
                            index,
                            index == 0,
                            gridWaypoint.altitude
                        )
                    }
                } else {
                    waypoints.toList()
                }

                val positionsToSave = if (isGridSurveyMode && gridResult != null) {
                    gridResult!!.waypoints.map { it.position }
                } else {
                    points.toList()
                }

                missionTemplateViewModel.saveTemplate(
                    projectName = projectName,
                    plotName = plotName,
                    waypoints = waypointsToSave,
                    waypointPositions = positionsToSave,
                    isGridSurvey = isGridSurveyMode,
                    gridParameters = currentGridParams
                )
            },
            isLoading = missionTemplateUiState.isLoading
        )
    }
}
