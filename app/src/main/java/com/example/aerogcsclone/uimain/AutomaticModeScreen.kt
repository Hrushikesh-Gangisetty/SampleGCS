package com.example.aerogcsclone.uimain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.aerogcsclone.Telemetry.SharedViewModel

@Composable
fun AutomaticModeScreen(navController: NavController, telemetryViewModel: SharedViewModel) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopNavBar(
            telemetryState = telemetryState,
            onAutomaticModeClick = { /* Do nothing, already on Automatic page */ },
            onManualModeClick = { navController.popBackStack() }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GcsMap()
        }
    }
}
