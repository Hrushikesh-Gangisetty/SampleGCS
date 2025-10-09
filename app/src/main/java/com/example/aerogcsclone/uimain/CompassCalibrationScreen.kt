package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CompassCalibrationScreen() {
    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationStatus by remember { mutableStateOf("Idle") }

    // Move LaunchedEffect here to observe isCalibrating
    LaunchedEffect(isCalibrating) {
        if (isCalibrating) {
            calibrationStatus = "Calibrating..."
            kotlinx.coroutines.delay(3000)
            isCalibrating = false
            calibrationStatus = "Calibration Complete"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Compass Calibration",
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Follow the instructions below to calibrate your compass:",
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "1. Place your device on a flat surface.\n2. Press 'Start Calibration'.\n3. Rotate your device slowly in all directions.",
            fontSize = 14.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                isCalibrating = true
            },
            enabled = !isCalibrating
        ) {
            Text("Start Calibration")
        }
        Spacer(modifier = Modifier.height(24.dp))
        if (isCalibrating) {
            CircularProgressIndicator()
        }
        Text(
            text = "Status: $calibrationStatus",
            fontSize = 16.sp,
            color = if (calibrationStatus == "Calibration Complete") Color.Green else Color.Red
        )
    }
}
