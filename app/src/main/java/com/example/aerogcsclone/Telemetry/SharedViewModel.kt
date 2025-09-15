package com.example.aerogcsclone.Telemetry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MissionItemInt
//import com.example.aerogcsclone.Telemetry.MissionItemInt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SharedViewModel : ViewModel() {

    var ipAddress by mutableStateOf("10.0.2.2")
    var port by mutableStateOf("5762")

    private var repo: MavlinkTelemetryRepository? = null

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    val isConnected: StateFlow<Boolean> = telemetryState
        .map { it.connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var missionUploaded by mutableStateOf(false)
    var lastUploadedCount by mutableStateOf(0)

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

    fun uploadMission(missionItems: List<MissionItemInt>, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val success = repo?.uploadMissionWithAck(missionItems) ?: false
                missionUploaded = success
                if (success) {
                    lastUploadedCount = missionItems.size
                    onResult(true, null)
                } else {
                    lastUploadedCount = 0
                    onResult(false, "Mission upload failed or timed out")
                }
            } catch (e: Exception) {
                missionUploaded = false
                lastUploadedCount = 0
                onResult(false, e.message)
            }
        }
    }

    fun startMission(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val last = if (lastUploadedCount > 0) lastUploadedCount - 1 else 0
                repo?.startMission(0, last)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }
}
