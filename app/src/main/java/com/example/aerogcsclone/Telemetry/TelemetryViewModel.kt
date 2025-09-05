package com.example.aerogcsclone.Telemetry

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class TelemetryViewModel : ViewModel() {

    private val repo = MavlinkTelemetryRepository

    // Expose as a StateFlow for Compose to observe
    val telemetry: StateFlow<TelemetryState> = repo.state


    init {
        // Start the MAVLink telemetry collection
        repo.start()
    }
}
