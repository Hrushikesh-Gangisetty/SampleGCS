package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.aerogcsclone.Telemetry.TelemetryState
import com.example.aerogcsclone.authentication.AuthViewModel
import com.example.aerogcsclone.navigation.Screen

@Composable
fun TopNavBar(
    telemetryState: TelemetryState,
    authViewModel: AuthViewModel,
    navController: NavHostController,
    onToggleNotificationPanel: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var kebabMenuExpanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<String?>(null) } // null by default

    // Set nav bar gradient colors based on connection status
    val navBarColors = if (telemetryState.connected) {
        listOf(
            Color(0xFF87CEEB).copy(alpha = 0.6f), // 40% transparent
            Color(0xFF4A90E2).copy(alpha = 0.6f)
        )
    } else {
        listOf(
            Color(0xFFfd5c63).copy(alpha = 0.6f),
            Color(0xFFFF320A).copy(alpha = 0.6f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(IntrinsicSize.Min) // Let the height be determined by content
            .background(
                brush = Brush.horizontalGradient(
                    colors = navBarColors
                )
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hamburger menu
            Box {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White,
                    modifier = Modifier.clickable { menuExpanded = true }
                )
                if (menuExpanded) {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .width(140.dp)
                            .background(Color(0xFF23232B).copy(alpha = 0.85f))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Plan Mission", color = Color.White) },
                            onClick = {
                                selectedMode = "Plan Mission"
                                menuExpanded = false
                                navController.navigate(Screen.Plan.route)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Plot Templates", color = Color.White) },
                            onClick = {
                                selectedMode = "Templates"
                                menuExpanded = false
                                navController.navigate(Screen.PlotTemplates.route)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Home icon
            Icon(
                Icons.Default.Home,
                contentDescription = "Home",
                tint = Color.White,
                modifier = Modifier.clickable {
                    navController.navigate(Screen.Connection.route)
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Title & Mode
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
                // Show selected mode only if not null
                selectedMode?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Status & telemetry
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                InfoBlockGroup(
                    Icons.Default.Sync,
                    listOf("${telemetryState.mode}", if (telemetryState.armed) "Armed" else "Disarmed")
                )
                DividerBlock()

                // Notification Icon
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color.White,
                    modifier = Modifier.clickable { onToggleNotificationPanel() }
                )
                Spacer(modifier = Modifier.width(16.dp))

                // Kebab menu
                Box {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.clickable { kebabMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = kebabMenuExpanded,
                        onDismissRequest = { kebabMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Logs") },
                            onClick = {
                                kebabMenuExpanded = false
                                navController.navigate(Screen.Logs.route)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                kebabMenuExpanded = false
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About App") },
                            onClick = { kebabMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                kebabMenuExpanded = false
                                authViewModel.signout()
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0)
                                }
                            }
                        )
                    }
                }
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
                .background(statusColor, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(statusText, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun DividerBlock() {
    Box(
        modifier = Modifier
            .padding(horizontal = 7.dp)
            .width(1.dp)
            .height(22.dp) // slightly smaller height
            .background(Color.White.copy(alpha = 0.7f))
    )
}

@Composable
fun InfoBlock(icon: ImageVector, value: String) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp), // slightly less padding
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp)) // just a little smaller
        Spacer(modifier = Modifier.height(1.dp))
        Text(value, color = Color.White, fontSize = 9.sp) // just a little smaller
    }
}

@Composable
fun InfoBlockGroup(icon: ImageVector, values: List<String>) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp), // slightly less padding
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp)) // just a little smaller
        Spacer(modifier = Modifier.height(1.dp))
        values.forEach { value ->
            Text(value, color = Color.White, fontSize = 9.sp) // just a little smaller
        }
    }
}
