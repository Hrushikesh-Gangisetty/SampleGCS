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
import com.example.aerogcsclone.navigation.Screen

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
                        fontSize = 32.sp,
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
                        color = Color(0xFF87CEEB), // light blue
                        thickness = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )
                }
            }

            // Light blue horizontal line separating the title from the rest of the content
            item {
                // IMU Calibrations | Compass Calibration
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { navController.navigate(Screen.Calibrations.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Speedometer",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "IMU Calibrations", color = Color.White, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = { navController.navigate(Screen.CompassCalibration.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Explore,
                            contentDescription = "Compass",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Compass Calibration", color = Color.White, fontSize = 18.sp)
                    }
                }
            }

            item {
                // Barometer Calibration | (Optional placeholder)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { navController.navigate(Screen.BarometerCalibration.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Thermostat,
                            contentDescription = "Barometer",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Barometer Calibration", color = Color.White, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Spacer(modifier = Modifier.weight(1f)) // Empty space or add another button if needed
                }
            }

            item {
                // Spraying system | Remote controller
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { navController.navigate(Screen.SprayingSystem.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Opacity,
                            contentDescription = "Spraying System",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Spraying system", color = Color.White, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = { navController.navigate(Screen.RemoteController.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Gamepad,
                            contentDescription = "Remote Controller",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Remote controller", color = Color.White, fontSize = 18.sp)
                    }
                }
            }

            item {
                // Aircraft | RangeFinder settings
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { navController.navigate(Screen.Aircraft.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Flight,
                            contentDescription = "Aircraft",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Aircraft", color = Color.White, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = { navController.navigate(Screen.RangeFinderSettings.route) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GpsFixed,
                            contentDescription = "RangeFinder",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "RangeFinder settings", color = Color.White, fontSize = 18.sp)
                    }
                }
            }

            // About App button centered below the grid
            item {
                Button(
                    onClick = { navController.navigate(Screen.AboutApp.route) },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(56.dp)
                        .align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "About App",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "About App", color = Color.White, fontSize = 18.sp)
                }
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
