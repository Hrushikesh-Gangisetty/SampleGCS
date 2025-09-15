package com.example.aerogcsclone.uimain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.aerogcsclone.Telemetry.TelemetryState

@Composable
fun PlanScreen(
    telemetryState: TelemetryState
) {
    // Map toggle state
    var mapToggle by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        GcsMap(
            telemetryState = telemetryState,
            isSatellite = mapToggle
        )

        // Map toggle button
        FloatingActionButton(
            onClick = { mapToggle = !mapToggle },
            containerColor = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier // optionally, position it
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "Toggle Map Type",
                tint = Color.White
            )
        }
    }
}
