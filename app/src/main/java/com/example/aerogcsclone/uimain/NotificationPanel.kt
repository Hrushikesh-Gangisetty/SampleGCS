package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aerogcsclone.telemetry.Notification
import com.example.aerogcsclone.telemetry.NotificationType

@Composable
fun NotificationPanel(notifications: List<Notification>) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.3f)
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        LazyColumn {
            items(notifications) { notification ->
                NotificationItem(notification)
            }
        }
    }
}

@Composable
fun NotificationItem(notification: Notification) {
    val color = when (notification.type) {
        NotificationType.ERROR -> Color.Red
        NotificationType.WARNING -> Color.Yellow
        NotificationType.SUCCESS -> Color.Green
        NotificationType.INFO -> Color.White
    }
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "[${notification.timestamp}] ",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Text(
            text = notification.message,
            color = color,
            fontSize = 12.sp
        )
    }
}