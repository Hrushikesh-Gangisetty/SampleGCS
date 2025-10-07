package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalibrationsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF23272A)) // dark grey background
            .padding(24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Calibrations",
                color = Color.White,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Text(
                text = "1. IMU Calibrations",
                color = Color.White,
                fontSize = 22.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
