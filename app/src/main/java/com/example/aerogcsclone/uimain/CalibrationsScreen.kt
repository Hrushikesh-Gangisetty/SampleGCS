package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aerogcsclone.telemetry.SharedViewModel

@Composable
fun CalibrationsScreen(
    viewModel: SharedViewModel
) {
    val calibrationStatus by viewModel.calibrationStatus.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF23272A)) // dark grey background
            .padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Calibrations",
                color = Color.White,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "1. IMU Calibrations",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.startImuCalibration() }) {
                    Text("Start IMU Calibration")
                }
            }
            calibrationStatus?.let {
                Text(
                    text = "Status: $it",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
