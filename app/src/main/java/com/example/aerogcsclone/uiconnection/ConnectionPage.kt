package com.example.aerogcsclone.uiconnection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.example.aerogcsclone.navigation.Screen

@Composable
fun ConnectionPage(navController: NavController) {
    var ipAddress by remember { mutableStateOf("10.0.2.2") }
    var port by remember { mutableStateOf("5760") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // Full dark background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // custom dark background
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Connect to Drone",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP Address", color = Color.White) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port", color = Color.White) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        isConnecting = true
                        errorMessage = ""

                        if (ipAddress.isNotEmpty() && port.isNotEmpty()) {
                            navController.navigate(Screen.Main.route)
                        } else {
                            errorMessage = "Invalid IP or Port"
                        }

                        isConnecting = false
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
}
