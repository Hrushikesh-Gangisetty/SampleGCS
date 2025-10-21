package com.example.aerogcsclone.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun RCCalibrationScreen(
    viewModel: RCCalibrationViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showInstructionDialog by remember { mutableStateOf(false) }
    var safetyWarningRead by remember { mutableStateOf(false) }

    // Announce safety warning when dialog is shown
    LaunchedEffect(showSafetyDialog) {
        if (showSafetyDialog) {
            safetyWarningRead = false
            val safetyMessage = "Safety Warning. Before proceeding, ensure: Your transmitter is ON and receiver is powered and connected. Your motor does NOT have power, NO PROPS attached!"
            viewModel.announceSafetyWarning(safetyMessage)
            // Enable button after announcement (approximately 8 seconds for the message)
            kotlinx.coroutines.delay(8000)
            safetyWarningRead = true
        }
    }

    // Safety Warning Dialog
    if (showSafetyDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Safety Warning",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "⚠️ Before proceeding, ensure:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• Your transmitter is ON and receiver is powered and connected",
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Your motor does NOT have power/NO PROPS attached!!!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red,
                        lineHeight = 22.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSafetyDialog = false
                        showInstructionDialog = true
                        safetyWarningRead = false
                    },
                    enabled = safetyWarningRead,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        if (safetyWarningRead) "I Understand" else "Please wait...",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Instruction Dialog
    if (showInstructionDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Calibration Instructions",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Click OK and move all RC sticks and switches to their extreme positions so the red bars hit the limits.",
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInstructionDialog = false
                        viewModel.startCalibration()
                    }
                ) {
                    Text("OK - Start Calibration", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstructionDialog = false }) {
                    Text("Cancel")
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
            RCCalibrationHeader(
                onBackClick = {
                    when (uiState.calibrationState) {
                        is RCCalibrationState.Idle,
                        is RCCalibrationState.Ready,
                        is RCCalibrationState.Success,
                        is RCCalibrationState.Failed -> navController.popBackStack()
                        else -> {
                            // Show confirmation dialog if calibration in progress
                        }
                    }
                }
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Connection Status
            ConnectionStatusCard(isConnected = uiState.isConnected)

            Spacer(modifier = Modifier.height(24.dp))

            // Instruction Card
            InstructionCard(
                state = uiState.calibrationState,
                statusText = uiState.statusText
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main RC Channels Display (Roll, Pitch, Throttle, Yaw)
            MainControlsCard(
                channels = uiState.channels,
                rollChannel = uiState.rollChannel,
                pitchChannel = uiState.pitchChannel,
                throttleChannel = uiState.throttleChannel,
                yawChannel = uiState.yawChannel,
                calibrationState = uiState.calibrationState
            )

            Spacer(modifier = Modifier.height(24.dp))

            // All Channels Display
            AllChannelsCard(
                channels = uiState.channels,
                calibrationState = uiState.calibrationState
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            CalibrationButton(
                buttonText = uiState.buttonText,
                enabled = uiState.isConnected &&
                    (uiState.calibrationState is RCCalibrationState.Ready ||
                     uiState.calibrationState is RCCalibrationState.CapturingMinMax ||
                     uiState.calibrationState is RCCalibrationState.CapturingCenter),
                isLoading = uiState.calibrationState is RCCalibrationState.Saving ||
                           uiState.calibrationState is RCCalibrationState.LoadingParameters,
                onClick = {
                    // Show safety dialog only when starting calibration (Ready state)
                    if (uiState.calibrationState is RCCalibrationState.Ready) {
                        showSafetyDialog = true
                    } else {
                        viewModel.onButtonClick()
                    }
                }
            )

            // Success/Failure Result
            when (val state = uiState.calibrationState) {
                is RCCalibrationState.Success -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    ResultCard(
                        message = state.summary,
                        isSuccess = true
                    )
                }
                is RCCalibrationState.Failed -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    ResultCard(
                        message = state.errorMessage,
                        isSuccess = false
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RCCalibrationHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "RC Calibration",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ConnectionStatusCard(isConnected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFF2E7D32) else Color(0xFF9E9E9E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isConnected) "Connected to Vehicle" else "Not Connected",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun InstructionCard(
    state: RCCalibrationState,
    statusText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3A3A38)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Instructions",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when (state) {
                    is RCCalibrationState.Idle -> "Initializing..."
                    is RCCalibrationState.LoadingParameters -> "Loading RC channel configuration from vehicle..."
                    is RCCalibrationState.Ready -> state.instruction
                    is RCCalibrationState.CapturingMinMax -> state.instruction
                    is RCCalibrationState.CapturingCenter -> state.instruction
                    is RCCalibrationState.Saving -> "Saving calibration to vehicle. Please wait..."
                    is RCCalibrationState.Success -> "Calibration complete! Your RC is now calibrated."
                    is RCCalibrationState.Failed -> "Calibration failed. Please try again."
                },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            if (statusText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = statusText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun MainControlsCard(
    channels: List<RCChannelData>,
    rollChannel: Int,
    pitchChannel: Int,
    throttleChannel: Int,
    yawChannel: Int,
    calibrationState: RCCalibrationState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3A3A38)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Main Flight Controls",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Roll
            RCChannelBar(
                label = "Roll (CH$rollChannel)",
                channel = channels.getOrNull(rollChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pitch
            RCChannelBar(
                label = "Pitch (CH$pitchChannel)",
                channel = channels.getOrNull(pitchChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Throttle
            RCChannelBar(
                label = "Throttle (CH$throttleChannel)",
                channel = channels.getOrNull(throttleChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Yaw
            RCChannelBar(
                label = "Yaw (CH$yawChannel)",
                channel = channels.getOrNull(yawChannel - 1),
                isCapturing = calibrationState is RCCalibrationState.CapturingMinMax
            )
        }
    }
}

@Composable
private fun AllChannelsCard(
    channels: List<RCChannelData>,
    calibrationState: RCCalibrationState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3A3A38)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "All RC Channels (1-16)",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            channels.forEachIndexed { index, channel ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                RCChannelBar(
                    label = "CH${channel.channelNumber}",
                    channel = channel,
                    isCapturing = calibrationState is RCCalibrationState.CapturingMinMax,
                    compact = true
                )
            }
        }
    }
}

@Composable
private fun RCChannelBar(
    label: String,
    channel: RCChannelData?,
    isCapturing: Boolean,
    compact: Boolean = false
) {
    if (channel == null) return

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = if (compact) FontWeight.Normal else FontWeight.Medium
            )
            Text(
                text = channel.currentValue.toString(),
                color = MaterialTheme.colorScheme.primary,
                fontSize = if (compact) 13.sp else 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 20.dp else 32.dp)
        ) {
            // Background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2A28), RoundedCornerShape(4.dp))
            )

            // Current value bar (drawn first, underneath)
            val progress = ((channel.currentValue - 800f) / (2200f - 800f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(4.dp)
                    )
            )

            // Min marker (red line) - drawn on top
            if (isCapturing && channel.minValue < 2000) {
                val minPosition = ((channel.minValue - 800f) / (2200f - 800f)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(minPosition)
                        .background(Color.Red)
                )
            }

            // Max marker (red line) - drawn on top
            if (isCapturing && channel.maxValue > 1000) {
                val maxPosition = ((channel.maxValue - 800f) / (2200f - 800f)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(maxPosition)
                        .background(Color.Red)
                )
            }
        }
    }
}

@Composable
private fun CalibrationButton(
    buttonText: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = Color.Gray
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = buttonText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.LightGray
        )
    }
}

@Composable
private fun ResultCard(
    message: String,
    isSuccess: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFD32F2F)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isSuccess) "Success!" else "Failed",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}
