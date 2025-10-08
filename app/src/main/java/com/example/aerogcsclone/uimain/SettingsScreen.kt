package com.example.aerogcsclone.uimain

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
fun SettingsScreen(navController: NavHostController) {
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
                text = "Settings",
                color = Color.White,
                fontSize = 32.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = { navController.navigate(com.example.aerogcsclone.navigation.Screen.Calibrations.route) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text(text = "Calibrations", color = Color.White, fontSize = 22.sp)
            }
        }
    }
}
