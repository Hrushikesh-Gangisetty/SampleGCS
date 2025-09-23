package com.example.aerogcsclone.Telemetry

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.google.android.gms.maps.model.LatLng
//import com.example.aerogcsclone.Telemetry.MissionItemInt
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import androidx.compose.runtime.State

class SharedViewModel : ViewModel() {

    private val _ipAddress = mutableStateOf("10.0.2.2")
    val ipAddress: State<String> = _ipAddress

    private val _port = mutableStateOf("5762")
    val port: State<String> = _port

    fun onIpAddressChange(newValue: String) {
        _ipAddress.value = newValue
    }

    fun onPortChange(newValue: String) {
        _port.value = newValue
    }

    private var repo: MavlinkTelemetryRepository? = null

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    val isConnected: StateFlow<Boolean> = telemetryState
        .map { it.connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var missionUploaded by mutableStateOf(false)
    var lastUploadedCount by mutableStateOf(0)

    // Store uploaded waypoints for display on main screen
    private val _uploadedWaypoints = MutableStateFlow<List<LatLng>>(emptyList())
    val uploadedWaypoints: StateFlow<List<LatLng>> = _uploadedWaypoints.asStateFlow()

    // Store survey polygon for grid missions
    private val _surveyPolygon = MutableStateFlow<List<LatLng>>(emptyList())
    val surveyPolygon: StateFlow<List<LatLng>> = _surveyPolygon.asStateFlow()

    // Store grid lines for grid missions
    private val _gridLines = MutableStateFlow<List<Pair<LatLng, LatLng>>>(emptyList())
    val gridLines: StateFlow<List<Pair<LatLng, LatLng>>> = _gridLines.asStateFlow()

    // Store grid waypoints for grid missions
    private val _gridWaypoints = MutableStateFlow<List<LatLng>>(emptyList())
    val gridWaypoints: StateFlow<List<LatLng>> = _gridWaypoints.asStateFlow()

    // Setters for plan screen to update these
    fun setSurveyPolygon(polygon: List<LatLng>) { _surveyPolygon.value = polygon }
    fun setGridLines(lines: List<Pair<LatLng, LatLng>>) { _gridLines.value = lines }
    fun setGridWaypoints(waypoints: List<LatLng>) { _gridWaypoints.value = waypoints }

    fun connect() {
        viewModelScope.launch {
            val portInt = port.value.toIntOrNull()
            if (portInt != null) {
                val newRepo = MavlinkTelemetryRepository(ipAddress.value, portInt)
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

                // Always clear previous mission in FCU before uploading new one (handled in repo)
                Log.i("SharedVM", "Starting mission upload to FCU...")
                val success = repo?.uploadMissionWithAck(missionItems) ?: false
                missionUploaded = success
                if (success) {
                    lastUploadedCount = missionItems.size

                    // Convert MissionItemInt to LatLng for display
                    val waypoints = missionItems.map { item ->
                        LatLng(item.x / 1E7, item.y / 1E7)
                    }
                    _uploadedWaypoints.value = waypoints

                    Log.i("SharedVM", "Mission upload succeeded (${missionItems.size})")
                    onResult(true, null)
                } else {
                    lastUploadedCount = 0
                    _uploadedWaypoints.value = emptyList()
                    Log.e("SharedVM", "Mission upload failed or timed out")
                    onResult(false, "Mission upload failed or timed out")
                }
            } catch (e: Exception) {
                missionUploaded = false
                lastUploadedCount = 0
                _uploadedWaypoints.value = emptyList()
                Log.e("SharedVM", "Exception during mission upload", e)
                onResult(false, e.message)
            }
        }
    }

    fun startMission(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                Log.i("SharedVM", "Starting mission start sequence...")

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

                // Step 1: Check for acknowledgment of the mission
                if (!missionUploaded || lastUploadedCount == 0) {
                    Log.w("SharedVM", "No mission uploaded or acknowledged, cannot start")
                    onResult(false, "No mission uploaded. Please upload a mission first.")
                    return@launch
                }
                Log.i("SharedVM", "✓ Mission upload acknowledged (${lastUploadedCount} items)")

                // Check basic prerequisites
                if (!_telemetryState.value.armable) {
                    Log.w("SharedVM", "Vehicle not armable, cannot start mission")
                    onResult(false, "Vehicle not armable. Check sensors and GPS.")
                    return@launch
                }

                val sats = _telemetryState.value.sats ?: 0
                if (sats < 6) {
                    Log.w("SharedVM", "Insufficient GPS satellites ($sats), minimum 6 required")
                    onResult(false, "Insufficient GPS satellites ($sats). Need at least 6 for mission.")
                    return@launch
                }

                // Step 2: See if the drone is either in Stabilize or Loiter to arm the drone
                val currentMode = _telemetryState.value.mode
                val isInArmableMode = currentMode?.equals("Stabilize", ignoreCase = true) == true ||
                        currentMode?.equals("Loiter", ignoreCase = true) == true

                if (!isInArmableMode) {
                    Log.i("SharedVM", "Current mode '$currentMode' not suitable for arming, switching to Stabilize")
                    repo?.changeMode(MavMode.STABILIZE)

                    // Wait for mode change to Stabilize
                    val modeTimeout = 5000L
                    val modeStart = System.currentTimeMillis()
                    while (System.currentTimeMillis() - modeStart < modeTimeout) {
                        val mode = _telemetryState.value.mode
                        if (mode?.equals("Stabilize", ignoreCase = true) == true) {
                            Log.i("SharedVM", "✓ Successfully switched to Stabilize mode")
                            break
                        }
                        delay(500)
                    }

                    if (!(_telemetryState.value.mode?.equals("Stabilize", ignoreCase = true) == true)) {
                        Log.w("SharedVM", "Failed to switch to Stabilize mode within timeout")
                        onResult(false, "Failed to switch to suitable mode for arming. Current mode: ${_telemetryState.value.mode}")
                        return@launch
                    }
                } else {
                    Log.i("SharedVM", "✓ Already in suitable mode for arming: $currentMode")
                }

                // Step 3: Arm the drone
                if (!_telemetryState.value.armed) {
                    Log.i("SharedVM", "Vehicle not armed - attempting to arm")
                    repo?.arm()

                    // Wait for arming with increased timeout
                    val armTimeout = 10000L
                    val armStart = System.currentTimeMillis()
                    while (!_telemetryState.value.armed && System.currentTimeMillis() - armStart < armTimeout) {
                        delay(500)
                    }

                    if (!_telemetryState.value.armed) {
                        Log.w("SharedVM", "Vehicle did not arm within timeout")
                        onResult(false, "Vehicle failed to arm. Check pre-arm conditions.")
                        return@launch
                    }
                    Log.i("SharedVM", "✓ Vehicle armed successfully")
                } else {
                    Log.i("SharedVM", "✓ Vehicle already armed")
                }

                // Step 4: Change mode to Auto
                if (_telemetryState.value.mode?.contains("Auto", ignoreCase = true) != true) {
                    Log.i("SharedVM", "Switching vehicle mode to AUTO")
                    repo?.changeMode(MavMode.AUTO)

                    // Wait for mode change to AUTO with increased timeout
                    val autoModeTimeout = 8000L
                    val autoModeStart = System.currentTimeMillis()
                    while (_telemetryState.value.mode?.contains("Auto", ignoreCase = true) != true &&
                        System.currentTimeMillis() - autoModeStart < autoModeTimeout) {
                        delay(500)
                    }

                    if (_telemetryState.value.mode?.contains("Auto", ignoreCase = true) != true) {
                        Log.w("SharedVM", "Vehicle did not switch to AUTO mode within timeout")
                        onResult(false, "Failed to switch to AUTO mode. Current mode: ${_telemetryState.value.mode}")
                        return@launch
                    }
                    Log.i("SharedVM", "✓ Vehicle mode is now AUTO")
                } else {
                    Log.i("SharedVM", "✓ Vehicle already in AUTO mode")
                }

                // Give a small delay to ensure all mode changes are processed
                delay(1000)

                // Step 5: Start the mission
                Log.i("SharedVM", "Sending start mission command")
                val result = repo?.startMission() ?: false

                if (result) {
                    Log.i("SharedVM", "✓ Mission start acknowledged by FCU")
                    onResult(true, null)
                } else {
                    Log.e("SharedVM", "Mission start failed or not acknowledged")
                    onResult(false, "Mission start failed. Check vehicle status and try again.")
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

    suspend fun cancelConnection() {
        // Cancel any ongoing connection coroutine (handled by ConnectionPage)
        // Attempt to close/flush the repo's socket/connection if possible
        repo?.let {
            try {
                it.closeConnection()
            } catch (e: Exception) {
                Log.e("SharedVM", "Error closing connection", e)
            }
        }
        repo = null
        _telemetryState.value = TelemetryState() // Reset state
    }


}