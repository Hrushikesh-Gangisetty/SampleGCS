package com.example.aerogcsclone.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun BarometerCalibrationScreen(
    viewModel: BarometerCalibrationViewModel,
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF23272A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Barometer Calibration",
                color = Color.White,
                fontSize = 28.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Place the drone on a flat surface. Ensure no wind or movement. Press Start Calibration.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            if (!uiState.isFlatSurface || !uiState.isWindGood) {
                if (!uiState.isFlatSurface && !uiState.isWindGood) {
                    Text(
                        text = "Place the drone on a flat surface.\nWind condition is not good. It is better to stop flying and calibrating the drone.",
                        color = Color.Red,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else if (!uiState.isFlatSurface) {
                    Text(
                        text = "Place the drone on a flat surface.",
                        color = Color.Red,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else if (!uiState.isWindGood) {
                    Text(
                        text = "Wind condition is not good. It is better to stop flying and calibrating the drone.",
                        color = Color.Red,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
            if (uiState.isCalibrating) {
                LinearProgressIndicator(
                    progress = uiState.progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(8.dp)
                        .padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${uiState.progress}%",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(
                    onClick = { viewModel.stopCalibration() },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Stop Calibration")
                }
            } else {
                Button(
                    onClick = { viewModel.startCalibration() },
                    enabled = uiState.isConnected && uiState.isFlatSurface && uiState.isWindGood && !uiState.isCalibrating,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Start Calibration")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (uiState.statusText.isNotEmpty()) {
                Text(
                    text = uiState.statusText,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
            }
        }
    }
}
