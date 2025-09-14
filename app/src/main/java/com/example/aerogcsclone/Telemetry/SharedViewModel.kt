package com.example.aerogcsclone.Telemetry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aerogcsclone.uimain.Geometry
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SharedViewModel : ViewModel() {

    var waypoints by mutableStateOf(listOf<LatLng>())
        private set

    fun addWaypoint(latLng: LatLng) {
        waypoints = waypoints + latLng
    }

    var ipAddress by mutableStateOf("10.0.2.2")
    var port by mutableStateOf("5762")

    private var repo: MavlinkTelemetryRepository? = null

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    val isConnected: StateFlow<Boolean> = telemetryState
        .map { it.connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun connect() {
        viewModelScope.launch {
            val portInt = port.toIntOrNull()
            if (portInt != null) {
                val newRepo = MavlinkTelemetryRepository(ipAddress, portInt)
                repo = newRepo
                newRepo.start()
                newRepo.state.collect {
                    _telemetryState.value = it
                }
            }
        }
    }

    fun arm() {
        viewModelScope.launch {
            repo?.arm()
        }
    }

    fun loadMission() {
        viewModelScope.launch {
            repo?.loadMission(waypoints)
        }
    }

    fun startMission() {
        viewModelScope.launch {
            repo?.startMission()
        }
    }

    fun resetMissionLoaded() {
        // This is not ideal, we should be updating the state in the repo
        // and have it flow up, but for now this will do.
        _telemetryState.update { it.copy(missionLoaded = false) }
    }

    fun deleteLastWaypoint() {
        if (waypoints.isNotEmpty()) {
            waypoints = waypoints.dropLast(1)
        }
    }

    fun clearAllWaypoints() {
        waypoints = emptyList()
    }
}
