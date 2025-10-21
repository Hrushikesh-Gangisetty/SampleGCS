package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun SettingsScreen(navController: NavHostController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D21)) // Darker background for better contrast
            .padding(20.dp),
        contentAlignment = Alignment.TopStart
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Header Section
                Column {
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Configure your drone settings",
                        color = Color(0xFF9CA3AF),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    HorizontalDivider(
                        color = Color(0xFF3B82F6), // Modern blue accent
                        thickness = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )
                }
            }

            // IMU Calibrations
            item {
                SettingsButton(
                    icon = Icons.Filled.Speed,
                    title = "IMU Calibrations",
                    description = "Accelerometer & Gyroscope",
                    onClick = { navController.navigate("accelerometer_calibration") }
                )
            }

            // Compass Calibration
            item {
                SettingsButton(
                    icon = Icons.Filled.Explore,
                    title = "Compass Calibration",
                    description = "Magnetometer settings",
                    onClick = { navController.navigate("compass_calibration") }
                )
            }

            // Barometer Calibration
            item {
                SettingsButton(
                    icon = Icons.Filled.Thermostat,
                    title = "Barometer Calibration",
                    description = "Altitude & pressure",
                    onClick = { navController.navigate("barometer_calibration") }
                )
            }

            // Spraying System
            item {
                SettingsButton(
                    icon = Icons.Filled.Opacity,
                    title = "Spraying System",
                    description = "Spray controls & parameters",
                    onClick = { navController.navigate("spraying_system") }
                )
            }

            // Remote Controller
            item {
                SettingsButton(
                    icon = Icons.Filled.Gamepad,
                    title = "Remote Controller",
                    description = "RC configuration",
                    onClick = { navController.navigate("remote_controller") }
                )
            }

            // Aircraft
            item {
                SettingsButton(
                    icon = Icons.Filled.Flight,
                    title = "Aircraft",
                    description = "Vehicle parameters",
                    onClick = { navController.navigate("aircraft") }
                )
            }

            // RangeFinder Settings
            item {
                SettingsButton(
                    icon = Icons.Filled.GpsFixed,
                    title = "RangeFinder Settings",
                    description = "Distance measurement",
                    onClick = { navController.navigate("rangefinder_settings") }
                )
            }

            // About App
            item {
                SettingsButton(
                    icon = Icons.Filled.Info,
                    title = "About App",
                    description = "Version & information",
                    onClick = { navController.navigate("about_app") }
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // Remove green background
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon without background circle
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp, // Reduced from 16.sp
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = Color(0xFFE2E8F0),
                    fontSize = 10.sp, // Reduced from 12.sp
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Arrow indicator
            Icon(
                imageVector = Icons.Filled.Flight,
                contentDescription = "Navigate",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(14.dp) // Reduced from 16.dp
                    .padding(start = 8.dp)
            )
        }
    }
}
