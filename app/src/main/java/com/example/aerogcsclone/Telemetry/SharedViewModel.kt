package com.example.aerogcsclone.Telemetry

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MissionItemInt
//import com.example.aerogcsclone.Telemetry.MissionItemInt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
                Log.i("SharedVM", "Request to upload mission with ${missionItems.size} items")

                if (repo == null) {
                    Log.w("SharedVM", "No repo available, cannot upload mission")
                    missionUploaded = false
                    lastUploadedCount = 0
                    onResult(false, "Not connected to vehicle")
                    return@launch
                }

                if (!_telemetryState.value.fcuDetected) {
                    Log.w("SharedVM", "FCU not detected, aborting mission upload")
                    missionUploaded = false
                    lastUploadedCount = 0
                    onResult(false, "FCU not detected, please connect to vehicle first")
                    return@launch
                }

                Log.i("SharedVM", "Starting mission upload to FCU...")
                val success = repo?.uploadMissionWithAck(missionItems) ?: false
                missionUploaded = success
                if (success) {
                    lastUploadedCount = missionItems.size
                    Log.i("SharedVM", "Mission upload succeeded (${missionItems.size})")
                    onResult(true, null)
                } else {
                    lastUploadedCount = 0
                    Log.e("SharedVM", "Mission upload failed or timed out")
                    onResult(false, "Mission upload failed or timed out")
                }
            } catch (e: Exception) {
                missionUploaded = false
                lastUploadedCount = 0
                Log.e("SharedVM", "Exception during mission upload", e)
                onResult(false, e.message)
            }
        }
    }

    fun startMission(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                if (repo == null) {
                    Log.w("SharedVM", "No repo available, cannot start mission")
                    onResult(false, "Not connected to vehicle")
                    return@launch
                }

                if (!_telemetryState.value.fcuDetected) {
                    Log.w("SharedVM", "FCU not detected, cannot start mission")
                    onResult(false, "FCU not detected")
                    return@launch
                }

                // Ensure vehicle is armed; if not, attempt to arm and wait briefly
                if (!_telemetryState.value.armed) {
                    Log.i("SharedVM", "Vehicle not armed - attempting to arm")
                    repo?.arm()
                    // wait up to 5s for telemetry to report armed
                    val armTimeout = 5000L
                    val start = System.currentTimeMillis()
                    while (!_telemetryState.value.armed && System.currentTimeMillis() - start < armTimeout) {
                        delay(250)
                    }
                    if (!_telemetryState.value.armed) {
                        Log.w("SharedVM", "Vehicle did not arm within timeout")
                        onResult(false, "Vehicle did not arm")
                        return@launch
                    }
                    Log.i("SharedVM", "Vehicle armed")
                }

                // Ensure vehicle mode is AUTO (some FCs require AUTO mode to execute mission)
                val desiredModeLabel = "Auto"
                if (_telemetryState.value.mode?.contains("Auto", ignoreCase = true) != true) {
                    Log.i("SharedVM", "Switching vehicle mode to AUTO")
                    // attempt to set mode; changeMode expects numeric mode value
                    repo?.changeMode(MavMode.AUTO)
                    // wait up to 4s for mode change
                    val modeTimeout = 4000L
                    val mstart = System.currentTimeMillis()
                    while (_telemetryState.value.mode?.contains("Auto", ignoreCase = true) != true && System.currentTimeMillis() - mstart < modeTimeout) {
                        delay(250)
                    }
                    if (_telemetryState.value.mode?.contains("Auto", ignoreCase = true) != true) {
                        Log.w("SharedVM", "Vehicle did not switch to AUTO mode within timeout")
                        // not a hard failure; proceed to send mission start but warn
                    } else {
                        Log.i("SharedVM", "Vehicle mode is now AUTO")
                    }
                }

                val last = if (lastUploadedCount > 0) lastUploadedCount - 1 else 0
                Log.i("SharedVM", "Sending start mission with first=0 last=$last")
                val result = repo?.startMission(0, last) ?: false
                if (result) {
                    Log.i("SharedVM", "Mission start acknowledged by FCU")
                    onResult(true, null)
                } else {
                    Log.e("SharedVM", "Mission start failed or not acknowledged")
                    onResult(false, "Mission start failed or not acknowledged")
                }
            } catch (e: Exception) {
                Log.e("SharedVM", "Failed to start mission", e)
                onResult(false, e.message)
            }
        }
    }

    // New helper to request mission from FCU and log its items for debugging
    fun readMissionFromFcu() {
        viewModelScope.launch {
            if (repo == null) {
                Log.w("SharedVM", "No repo available, cannot request mission readback")
                return@launch
            }
            try {
                repo?.requestMissionAndLog()
            } catch (e: Exception) {
                Log.e("SharedVM", "Exception during mission readback", e)
            }
        }
    }
}
