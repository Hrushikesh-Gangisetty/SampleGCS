package com.example.aerogcsclone.calibration

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ImuCalibrationScreen(
    viewModel: ImuCalibrationViewModel = hiltViewModel(),
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val state = uiState) {
            is CalibrationState.Idle -> {
                Text("IMU Calibration")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.onStartClicked() }) {
                    Text("Start Calibration")
                }
            }
            is CalibrationState.AwaitingUserInput -> {
                Text(text = state.instruction, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(32.dp))
                ImuPositionVisual(position = state.visualHint)
                Spacer(modifier = Modifier.height(32.dp))
                Row {
                    Button(onClick = {
                        viewModel.onCancelClicked()
                        onCancel()
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { viewModel.onPositionAcknowledged() }) {
                        Text("Next")
                    }
                }
            }
            is CalibrationState.InProgress -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(state.message)
            }
            is CalibrationState.Success -> {
                Text(state.summary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onComplete) {
                    Text("Done")
                }
            }
            is CalibrationState.Error -> {
                Text(state.message)
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(onClick = {
                        viewModel.onCancelClicked()
                        onCancel()
                    }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { viewModel.onStartClicked() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}