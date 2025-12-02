package com.example.aerogcsclone.ui.obstacle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aerogcsclone.obstacle.*
import com.example.aerogcsclone.viewmodel.ObstacleDetectionViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * UI Screen for Obstacle Detection and Mission Resume
 * Displays current obstacle status, RTL monitoring, and resume options
 */
@Composable
fun ObstacleDetectionScreen(
    obstacleViewModel: ObstacleDetectionViewModel,
    onResumeSelected: (ResumeOption) -> Unit,
    onDismiss: () -> Unit
) {
    val detectionStatus by obstacleViewModel.detectionStatus.collectAsState()
    val currentObstacle by obstacleViewModel.currentObstacle.collectAsState()
    val resumeOptions by obstacleViewModel.resumeOptions.collectAsState()

    // Create default states for RTL and saved mission
    val rtlMonitoring = remember { RTLMonitoringState() }
    val savedMissionState = remember { mutableStateOf<SavedMissionState?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Obstacle Detection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        StatusCard(detectionStatus, currentObstacle)

        Spacer(modifier = Modifier.height(16.dp))

        // Content based on status
        when (detectionStatus) {
            ObstacleDetectionStatus.MONITORING -> {
                MonitoringContent(currentObstacle)
            }
            ObstacleDetectionStatus.OBSTACLE_DETECTED -> {
                ObstacleDetectedContent(currentObstacle)
            }
            ObstacleDetectionStatus.RTL_IN_PROGRESS -> {
                RTLMonitoringContent(rtlMonitoring)
            }
            ObstacleDetectionStatus.READY_TO_RESUME -> {
                ResumeOptionsContent(
                    savedMissionState = savedMissionState.value,
                    resumeOptions = resumeOptions,
                    onResumeSelected = onResumeSelected
                )
            }
            ObstacleDetectionStatus.RESUMING -> {
                ResumingContent()
            }
            else -> {
                InactiveContent()
            }
        }
    }
}

@Composable
fun StatusCard(status: ObstacleDetectionStatus, obstacle: ObstacleInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ObstacleDetectionStatus.MONITORING -> Color(0xFF1B5E20)
                ObstacleDetectionStatus.OBSTACLE_DETECTED -> Color(0xFFB71C1C)
                ObstacleDetectionStatus.RTL_IN_PROGRESS -> Color(0xFFF57C00)
                ObstacleDetectionStatus.READY_TO_RESUME -> Color(0xFF1565C0)
                ObstacleDetectionStatus.RESUMING -> Color(0xFF4A148C)
                else -> Color(0xFF424242)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = when (status) {
                        ObstacleDetectionStatus.MONITORING -> "✅ Monitoring Active"
                        ObstacleDetectionStatus.OBSTACLE_DETECTED -> "⚠️ Obstacle Detected!"
                        ObstacleDetectionStatus.RTL_IN_PROGRESS -> "🏠 Returning Home"
                        ObstacleDetectionStatus.READY_TO_RESUME -> "✈️ Ready to Resume"
                        ObstacleDetectionStatus.RESUMING -> "🔄 Resuming Mission"
                        else -> "⚪ Inactive"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (obstacle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Distance: ${obstacle.distance.toInt()}m - ${obstacle.threatLevel}",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Icon(
                imageVector = when (status) {
                    ObstacleDetectionStatus.MONITORING -> Icons.Default.Visibility
                    ObstacleDetectionStatus.OBSTACLE_DETECTED -> Icons.Default.Warning
                    ObstacleDetectionStatus.RTL_IN_PROGRESS -> Icons.Default.Home
                    ObstacleDetectionStatus.READY_TO_RESUME -> Icons.Default.PlayArrow
                    ObstacleDetectionStatus.RESUMING -> Icons.Default.Refresh
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun MonitoringContent(obstacle: ObstacleInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Scanning for Obstacles...",
                fontSize = 16.sp,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (obstacle != null) {
                ThreatIndicator(obstacle)
            } else {
                Text(
                    text = "No obstacles detected",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ThreatIndicator(obstacle: ObstacleInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = when (obstacle.threatLevel) {
                    ThreatLevel.LOW -> "🟢 Low Threat"
                    ThreatLevel.MEDIUM -> "🟡 Medium Threat"
                    ThreatLevel.HIGH -> "🔴 HIGH THREAT"
                    else -> "⚪ No Threat"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = when (obstacle.threatLevel) {
                    ThreatLevel.LOW -> Color.Green
                    ThreatLevel.MEDIUM -> Color.Yellow
                    ThreatLevel.HIGH -> Color.Red
                    else -> Color.Gray
                }
            )

            Text(
                text = "${obstacle.distance.toInt()}m away",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        if (obstacle.threatLevel == ThreatLevel.HIGH) {
            Text(
                text = "${obstacle.consecutiveDetections}/3",
                fontSize = 14.sp,
                color = Color.Red
            )
        }
    }
}

@Composable
fun ObstacleDetectedContent(obstacle: ObstacleInfo?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "EMERGENCY RTL TRIGGERED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            obstacle?.let {
                Text(
                    text = "Obstacle detected at ${it.distance.toInt()}m",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Drone returning to home...",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun RTLMonitoringContent(rtlState: RTLMonitoringState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Return to Launch Progress",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (rtlState.currentDistance != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Distance to Home:",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "${rtlState.currentDistance.toInt()}m",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (rtlState.initialDistance != null) {
                    val progress = 1f - (rtlState.currentDistance / rtlState.initialDistance)
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            if (rtlState.consecutiveArrivalChecks > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Confirming arrival... (${rtlState.consecutiveArrivalChecks}/3)",
                    fontSize = 14.sp,
                    color = Color.Yellow
                )
            }
        }
    }
}

@Composable
fun ResumeOptionsContent(
    savedMissionState: SavedMissionState?,
    resumeOptions: List<ResumeOption>,
    onResumeSelected: (ResumeOption) -> Unit
) {
    Column {
        // Mission Info Card
        savedMissionState?.let { state ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mission Interrupted",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    InfoRow("Progress", "${state.missionProgress.toInt()}%")
                    InfoRow("Waypoint", "${state.interruptedWaypointIndex}")
                    InfoRow("Obstacle", "${state.obstacleInfo.distance.toInt()}m")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Resume Options
        Text(
            text = "Select Resume Point",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(resumeOptions) { option ->
                ResumeOptionCard(option, onResumeSelected)
            }
        }
    }
}

@Composable
fun ResumeOptionCard(
    option: ResumeOption,
    onSelected: (ResumeOption) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (option.isRecommended) Color(0xFF1565C0) else Color(0xFF2E2E2E)
        ),
        onClick = { onSelected(option) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Waypoint ${option.waypointIndex}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (option.isRecommended) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⭐ RECOMMENDED",
                            fontSize = 12.sp,
                            color = Color.Yellow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${option.distanceFromObstacle.toInt()}m from obstacle",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Text(
                    text = "${option.coveragePercentage.toInt()}% coverage",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                if (option.skippedWaypoints.isNotEmpty()) {
                    Text(
                        text = "Skips: ${option.skippedWaypoints.size} waypoints",
                        fontSize = 12.sp,
                        color = Color.Red.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
fun ResumingContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color(0xFF4CAF50))

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Uploading New Mission...",
                fontSize = 16.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun InactiveContent() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E2E2E))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Obstacle Detection Inactive",
                fontSize = 16.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start a mission to enable monitoring",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}
