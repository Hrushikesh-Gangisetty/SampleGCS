package com.example.aerogcsclone.uiconnection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.navigation.Screen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ConnectionPage(navController: NavController, viewModel: SharedViewModel) {
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var connectionJob by remember { mutableStateOf<Job?>(null) }
    var showPopup by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.isConnected.collectLatest { isConnected ->
            if (isConnected) {
                isConnecting = false
                connectionJob?.cancel()
                navController.navigate(Screen.Main.route) {
                    popUpTo(Screen.Connection.route) { inclusive = true }
                }
            }
        }
    }

    fun startConnection(autoRetry: Boolean = false) {
        isConnecting = true
        errorMessage = ""
        connectionJob?.cancel()
        connectionJob = coroutineScope.launch {
            var attempts = 0
            val maxAttempts = if (autoRetry) 3 else 1
            while (attempts < maxAttempts && !viewModel.isConnected.value) {
                try {
                    viewModel.connect()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Connection failed"
                }
                attempts++
                if (!viewModel.isConnected.value && autoRetry && attempts < maxAttempts) {
                    delay(5000)
                }
            }
            if (!viewModel.isConnected.value) {
                isConnecting = false
                showPopup = true
            }
        }
    }

    fun cancelConnection() {
        connectionJob?.cancel()
        isConnecting = false
        errorMessage = ""
        coroutineScope.launch {
            viewModel.cancelConnection()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF535350))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Connect to Drone",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            val ipAddress by viewModel.ipAddress
            val port by viewModel.port

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { viewModel.onIpAddressChange(it) },
                label = { Text("IP Address", color = Color.White) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(color = Color.White)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = port,
                onValueChange = { viewModel.onPortChange(it) },
                label = { Text("Port", color = Color.White) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(color = Color.White)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { startConnection(autoRetry = true) },
                    modifier = Modifier.weight(1f),
                    enabled = !isConnecting
                ) {
                    Text(if (isConnecting) "Connecting..." else "Connect")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { startConnection(autoRetry = false) },
                    modifier = Modifier.weight(1f),
                    enabled = !isConnecting
                ) {
                    Text("Retry")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { cancelConnection() },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnecting
            ) {
                Text("Cancel")
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
        if (showPopup) {
            AlertDialog(
                onDismissRequest = { showPopup = false },
                title = { Text("Connection Failed") },
                text = { Text("Unable to connect to the drone after multiple attempts.") },
                confirmButton = {
                    Button(onClick = { showPopup = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
