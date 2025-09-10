package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.window.Popup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.example.aerogcsclone.authentication.AuthViewModel
import com.example.aerogcsclone.navigation.Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup

@Composable
fun TopNavBar(telemetryState: TelemetryState, authViewModel: AuthViewModel, navController: NavHostController) {
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf("Manual") }
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Logout") },
            text = { Text("Do you really want to logout?") },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.signout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Main.route) { inclusive = true }
                        }
                        showLogoutConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4A90E2))
                ) {
                    Text("Yes", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showLogoutConfirmation = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)
                ) {
                    Text("No", color = Color.White)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF87CEEB), Color(0xFF4A90E2))
                )
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.clickable { menuExpanded = true }
                )
                if (menuExpanded) {
                    Popup(
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = "Automatic",
                                color = Color.White,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable {
                                        selectedMode = "Automatic"
                                        menuExpanded = false
                                    }
                            )
                            Text(
                                text = "Manual",
                                color = Color.White,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable {
                                        selectedMode = "Manual"
                                        menuExpanded = false
                                    }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                Icons.Default.Home,
                contentDescription = "Home",
                tint = Color.White,
                modifier = Modifier.clickable { showLogoutConfirmation = true }
            )
            Spacer(modifier = Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Pavaman Aviation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = selectedMode,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionStatusWidget(isConnected = telemetryState.connected)
                DividerBlock()
                InfoBlock(Icons.Default.Flight, "13%")
                DividerBlock()
                InfoBlock(Icons.Default.BatteryFull, "${telemetryState.batteryPercent ?: "N/A"}%")
                DividerBlock()
                InfoBlock(Icons.Default.Gamepad, "100%")
                DividerBlock()
                InfoBlockGroup(
                    Icons.Default.Bolt,
                    listOf(
                        "${telemetryState.voltage ?: "N/A"} V",
                        "${telemetryState.currentA ?: "N/A"} A"
                    )
                )
                DividerBlock()
                InfoBlockGroup(
                    Icons.Default.SatelliteAlt,
                    listOf(
                        "${telemetryState.sats ?: "N/A"} sats",
                        "${telemetryState.hdop ?: "N/A"} hdop"
                    )
                )
                DividerBlock()
                InfoBlockGroup(Icons.Default.Sync, listOf("Stabilize", "Arm"))
                DividerBlock()
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
    }
}

@Composable
fun ConnectionStatusWidget(isConnected: Boolean) {
    val statusColor = if (isConnected) Color.Green else Color.Red
    val statusText = if (isConnected) "Connected" else "Disconnected"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(statusText, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun DividerBlock() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp) // spacing between items
            .width(1.dp)
            .height(30.dp)
            .background(Color.White.copy(alpha = 0.7f))
    )
}

@Composable
fun InfoBlock(icon: ImageVector, value: String) {
    Column(
        modifier = Modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun InfoBlockGroup(icon: ImageVector, values: List<String>) {
    Column(
        modifier = Modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(2.dp))
        values.forEach { value ->
            Text(value, color = Color.White, fontSize = 12.sp)
        }
    }
}
