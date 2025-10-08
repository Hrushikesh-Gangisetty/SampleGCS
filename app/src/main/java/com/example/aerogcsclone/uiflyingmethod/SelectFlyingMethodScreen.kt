package com.example.aerogcsclone.uiflyingmethod

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.aerogcsclone.R
import com.example.aerogcsclone.navigation.Screen

@Composable
fun SelectFlyingMethodScreen(navController: NavController) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF23272A) // dark grey
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Select Flying Method",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FlyingMethodCard(
                    icon = { Image(painter = painterResource(id = R.drawable.autonomous), contentDescription = "Automatic", modifier = Modifier.size(64.dp)) },
                    label = "Automatic",
                    onClick = { navController.navigate(Screen.Main.route) }
                )
                FlyingMethodCard(
                    icon = { Image(painter = painterResource(id = R.drawable.manual), contentDescription = "Manual", modifier = Modifier.size(64.dp)) },
                    label = "Manual",
                    onClick = { navController.navigate(Screen.Main.route) }
                )
            }
        }
    }
}

@Composable
fun FlyingMethodCard(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2F33)) // slightly lighter dark grey
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}