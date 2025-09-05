package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp


@Composable
fun MainPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 🔹 Top NavBar
        TopNavBar()

        // 🔹 Map + Overlays
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray), // Map placeholder
            contentAlignment = Alignment.Center
        ) {
            Text("Map Placeholder", color = Color.White)

            // 🔹 Bottom Left Status Panel
            StatusPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )

            // 🔹 Floating Buttons (Right Side)
            FloatingButtons(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(12.dp)
            )
        }
    }
}


@Composable
fun StatusPanel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .width(500.dp)
            .height(120.dp),
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Alt: 2.5 m", color = Color.White)
                Text("Speed: 15.9 m/s", color = Color.White)
                Text("Area: 10 acre", color = Color.White)
                Text("Flow: 0 L/min", color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Obs Alt: 10", color = Color.White)
                Text("Time: 00:00:00", color = Color.White)
                Text("Distance: 20 m", color = Color.White)
                Text("Consumed: 2.0 ml", color = Color.White)
            }
        }
    }
}

@Composable
fun FloatingButtons(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White)
        }
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
        }
        FloatingActionButton(onClick = { }, containerColor = Color.Black.copy(alpha = 0.7f)) {
            Icon(Icons.Default.Map, contentDescription = "Map Options", tint = Color.White)
        }
    }
}
