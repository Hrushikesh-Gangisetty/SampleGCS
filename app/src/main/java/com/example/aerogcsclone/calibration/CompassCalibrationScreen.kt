package com.example.aerogcsclone.calibration

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun CompassCalibrationScreen(
    viewModel: CompassCalibrationViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show cancel confirmation dialog
    if (uiState.showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showCancelDialog(false) },
            title = { Text("Cancel Calibration?") },
            text = { Text("Are you sure you want to cancel the compass calibration process?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelCalibration()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Yes, Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showCancelDialog(false) }) {
                    Text("Continue Calibration")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF535350))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF535350))
                .padding(16.dp)
        ) {
            CompassCalibrationHeader(
                onBackClick = {
                    if (uiState.calibrationState is CompassCalibrationState.Idle ||
                        uiState.calibrationState is CompassCalibrationState.Success ||
                        uiState.calibrationState is CompassCalibrationState.Failed ||
                        uiState.calibrationState is CompassCalibrationState.Cancelled
                    ) {
                        navController.popBackStack()
                    } else {
                        viewModel.showCancelDialog(true)
                    }
                }
            )
        }

        // Progress indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            CompassCalibrationProgress(
                overallProgress = uiState.overallProgress,
                calibrationState = uiState.calibrationState
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            CompassCalibrationContent(
                calibrationState = uiState.calibrationState,
                statusText = uiState.statusText,
                isConnected = uiState.isConnected,
                compassProgress = uiState.compassProgress,
                overallProgress = uiState.overallProgress
            )
        }

        // Action buttons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF535350))
                .padding(16.dp)
        ) {
            CompassCalibrationActions(
                calibrationState = uiState.calibrationState,
                isConnected = uiState.isConnected,
                onStart = { viewModel.startCalibration() },
                onCancel = { viewModel.showCancelDialog(true) },
                onReset = { viewModel.resetCalibration() }
            )
        }
    }
}

@Composable
private fun CompassCalibrationHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Compass Calibration",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompassCalibrationProgress(
    overallProgress: Int,
    calibrationState: CompassCalibrationState
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Progress text
        if (calibrationState is CompassCalibrationState.InProgress) {
            Text(
                text = "Progress: $overallProgress%",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Progress bar with green color
        LinearProgressIndicator(
            progress = overallProgress.toFloat() / 100f,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(8.dp),
            color = when (calibrationState) {
                is CompassCalibrationState.Success -> Color(0xFF4CAF50) // Green
                is CompassCalibrationState.Failed -> Color.Red
                is CompassCalibrationState.InProgress -> Color(0xFF4CAF50) // Green
                else -> Color.Gray
            },
            trackColor = Color.Gray.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun CompassCalibrationContent(
    calibrationState: CompassCalibrationState,
    statusText: String,
    isConnected: Boolean,
    compassProgress: Map<Int, Int>,
    overallProgress: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A38)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (calibrationState) {
                    is CompassCalibrationState.Idle -> IdleContent(isConnected)
                    is CompassCalibrationState.Starting -> StartingContent()
                    is CompassCalibrationState.InProgress -> InProgressContent(
                        instruction = calibrationState.currentInstruction,
                        compassProgress = compassProgress,
                        overallProgress = overallProgress
                    )
                    is CompassCalibrationState.Success -> SuccessContent(calibrationState.message, calibrationState.reportDetails)
                    is CompassCalibrationState.Failed -> FailedContent(calibrationState.errorMessage)
                    is CompassCalibrationState.Cancelled -> CancelledContent()
                }
            }
        }

        // Status text
        if (statusText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun IdleContent(isConnected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Connection status indicator
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) Color(0xFF1B5E20) else Color(0xFF5D4037)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConnected) "✓ Connected" else "⚠ Not Connected",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Compass Calibration",
            color = Color.White,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isConnected) {
                "Ready to calibrate magnetometers"
            } else {
                "Connect to drone to start"
            },
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A28)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Calibration Instructions:",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Hold vehicle in the air\n" +
                            "2. Rotate slowly - point each side down\n" +
                            "3. Complete 6 full rotations\n" +
                            "4. Listen for 3 rising tones when done\n" +
                            "5. Reboot autopilot after success",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun StartingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        )

        Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier
                .size(80.dp)
                .rotate(angle)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Starting Calibration",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Initializing magnetometer calibration...",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InProgressContent(
    instruction: String,
    compassProgress: Map<Int, Int>,
    overallProgress: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "angle"
        )

        Icon(
            imageVector = Icons.Default.Explore,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier
                .size(80.dp)
                .rotate(angle)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Calibrating...",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = instruction,
            color = Color(0xFF4CAF50),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Individual compass progress indicators
        if (compassProgress.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A28)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Compass Progress:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    compassProgress.entries.sortedBy { it.key }.forEach { (compassId, progress) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Compass $compassId:",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.width(80.dp)
                            )

                            LinearProgressIndicator(
                                progress = progress.toFloat() / 100f,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp),
                                color = Color(0xFF4CAF50),
                                trackColor = Color.Gray.copy(alpha = 0.3f)
                            )

                            Text(
                                text = "$progress%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                modifier = Modifier.width(50.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        if (compassId != compassProgress.keys.max()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessContent(message: String, reportDetails: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Success!",
            color = Color(0xFF4CAF50),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        if (reportDetails != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A28)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Calibration Details:",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = reportDetails,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A5E20)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Please reboot the autopilot",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FailedContent(errorMessage: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calibration Failed",
            color = Color.Red,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CancelledContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Cancel,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Calibration Cancelled",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The calibration process was cancelled",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompassCalibrationActions(
    calibrationState: CompassCalibrationState,
    isConnected: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        when (calibrationState) {
            is CompassCalibrationState.Idle -> {
                Button(
                    onClick = onStart,
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Calibration")
                }
            }
            is CompassCalibrationState.Starting,
            is CompassCalibrationState.InProgress -> {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Calibration")
                }
            }
            is CompassCalibrationState.Success,
            is CompassCalibrationState.Failed,
            is CompassCalibrationState.Cancelled -> {
                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset")
                }
            }
        }
    }
}

