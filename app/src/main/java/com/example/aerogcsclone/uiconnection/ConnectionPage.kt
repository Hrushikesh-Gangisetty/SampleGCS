package com.example.aerogcsclone.uiconnection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.navigation.Screen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun ConnectionPage(navController: NavController, viewModel: SharedViewModel) {
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.isConnected.collectLatest { isConnected ->
            if (isConnected) {
                navController.navigate(Screen.Main.route) {
                    popUpTo(Screen.Connection.route) { inclusive = true }
                }
            }
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

            Button(
                onClick = {
                    isConnecting = true
                    errorMessage = ""
                    coroutineScope.launch {
                        try {
                            viewModel.connect()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Connection failed"
                            isConnecting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnecting
            ) {
                Text(if (isConnecting) "Connecting..." else "Connect")
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
