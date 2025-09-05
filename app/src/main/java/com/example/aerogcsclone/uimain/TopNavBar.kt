package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TopNavBar() {
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
            // Left section
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
            Spacer(modifier = Modifier.width(16.dp))

            // Title block
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier= Modifier.height(10.dp))
                Text(
                    text = "Pavaman Aviation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 25.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Manual",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right section (info blocks)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoBlock(Icons.Default.Flight, "13%")
                DividerBlock()
                InfoBlock(Icons.Default.BatteryFull, "12.600 V")
                DividerBlock()
                InfoBlock(Icons.Default.Gamepad, "100%")
                DividerBlock()
                InfoBlockGroup(Icons.Default.Bolt, listOf("561 mAh", "28.12 A"))
                DividerBlock()
                InfoBlockGroup(Icons.Default.SatelliteAlt, listOf("10 sats", "1.2 hdop"))
                DividerBlock()
                InfoBlockGroup(Icons.Default.Sync, listOf("Stabilize", "Arm"))
                DividerBlock()
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
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
