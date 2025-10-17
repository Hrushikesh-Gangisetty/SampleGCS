@file:Suppress("unused")
package com.example.aerogcsclone.telemetry

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MavCmd
import com.divpundir.mavlink.definitions.common.MavResult
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.divpundir.mavlink.definitions.common.Statustext
import com.example.aerogcsclone.Telemetry.TelemetryState
//import com.example.aerogcsclone.Telemetry.connections.BluetoothConnectionProvider
//import com.example.aerogcsclone.Telemetry.connections.MavConnectionProvider
import com.example.aerogcsclone.telemetry.  connections.BluetoothConnectionProvider
import com.example.aerogcsclone.telemetry.connections.MavConnectionProvider
import com.example.aerogcsclone.telemetry.connections.TcpConnectionProvider
import com.example.aerogcsclone.utils.GeofenceUtils
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.aerogcsclone.grid.GridUtils
import kotlinx.coroutines.withTimeoutOrNull

enum class ConnectionType {
    TCP, BLUETOOTH
}

@SuppressLint("MissingPermission")
data class PairedDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice
) {
    constructor(device: BluetoothDevice) : this(
        name = device.name ?: "Unknown Device",
        address = device.address,
        device = device
    )
}

class SharedViewModel : ViewModel() {

    // --- Area (survey / mission) state ---
    // Area of the currently drawn survey polygon (sq meters)
    private val _surveyAreaSqMeters = MutableStateFlow(0.0)
    val surveyAreaSqMeters: StateFlow<Double> = _surveyAreaSqMeters.asStateFlow()

    // Formatted area string for display (reuses GridUtils formatting to match Grid statistics)
    private val _surveyAreaFormatted = MutableStateFlow("0 acres")
    val surveyAreaFormatted: StateFlow<String> = _surveyAreaFormatted.asStateFlow()

    // Area captured at the time of mission upload (so telemetry shows the uploaded mission's area)
    private val _missionAreaSqMeters = MutableStateFlow(0.0)
    val missionAreaSqMeters: StateFlow<Double> = _missionAreaSqMeters.asStateFlow()

    private val _missionAreaFormatted = MutableStateFlow("0 acres")
    val missionAreaFormatted: StateFlow<String> = _missionAreaFormatted.asStateFlow()

    private fun updateSurveyArea() {
        val polygon = _surveyPolygon.value
        if (polygon.size >= 3) {
            val areaMeters = GridUtils.calculatePolygonArea(polygon)
            val formatted = GridUtils.calculateAndFormatPolygonArea(polygon)
            _surveyAreaSqMeters.value = areaMeters
            _surveyAreaFormatted.value = formatted
        } else {
            _surveyAreaSqMeters.value = 0.0
            _surveyAreaFormatted.value = "0 acres"
        }
    }

    // --- Connection State Management ---
    private val _connectionType = mutableStateOf(ConnectionType.TCP)
    val connectionType: State<ConnectionType> = _connectionType

    private val _ipAddress = mutableStateOf("10.0.2.2")
    val ipAddress: State<String> = _ipAddress

    private val _port = mutableStateOf("5762")
    val port: State<String> = _port

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    private val _selectedDevice = mutableStateOf<PairedDevice?>(null)
    val selectedDevice: State<PairedDevice?> = _selectedDevice

    fun onConnectionTypeChange(newType: ConnectionType) {
        _connectionType.value = newType
    }

    fun onIpAddressChange(newValue: String) {
        _ipAddress.value = newValue
    }

    fun onPortChange(newValue: String) {
        _port.value = newValue
    }

    @SuppressLint("MissingPermission")
    fun setPairedDevices(devices: Set<BluetoothDevice>) {
        _pairedDevices.value = devices.map { PairedDevice(it) }
    }

    fun onDeviceSelected(device: PairedDevice) {
        _selectedDevice.value = device
    }

    // --- Telemetry & Repository ---
    private var repo: MavlinkTelemetryRepository? = null

    private val _telemetryState = MutableStateFlow(TelemetryState())
    val telemetryState: StateFlow<TelemetryState> = _telemetryState.asStateFlow()

    val isConnected: StateFlow<Boolean> = telemetryState
        .map { it.connected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _calibrationStatus = MutableStateFlow<String?>(null)
    val calibrationStatus: StateFlow<String?> = _calibrationStatus.asStateFlow()

    private val _imuCalibrationStartResult = MutableStateFlow<Boolean?>(null)
    val imuCalibrationStartResult: StateFlow<Boolean?> = _imuCalibrationStartResult

    // Expose COMMAND_ACK flow for calibration and other commands
    val commandAck: SharedFlow<com.divpundir.mavlink.definitions.common.CommandAck>
        get() = repo?.commandAck ?: MutableSharedFlow()

    // Expose COMMAND_LONG flow for incoming commands from FC (e.g., ACCELCAL_VEHICLE_POS)
    val commandLong: SharedFlow<com.divpundir.mavlink.definitions.common.CommandLong>
        get() = repo?.commandLong ?: MutableSharedFlow()

    // Expose MAG_CAL_PROGRESS flow for compass calibration progress
    val magCalProgress: SharedFlow<com.divpundir.mavlink.definitions.ardupilotmega.MagCalProgress>
        get() = repo?.magCalProgress ?: MutableSharedFlow()

    // Expose MAG_CAL_REPORT flow for compass calibration final report
    val magCalReport: SharedFlow<com.divpundir.mavlink.definitions.common.MagCalReport>
        get() = repo?.magCalReport ?: MutableSharedFlow()

    // --- Calibration helpers ---
    /**
     * Request MAG_CAL_PROGRESS and MAG_CAL_REPORT messages from the autopilot.
     * This is needed because these messages are not sent by default.
     */
    suspend fun requestMagCalMessages(hz: Float = 10f) {
        Log.d("SharedVM", "Requesting MAG_CAL_PROGRESS and MAG_CAL_REPORT messages at $hz Hz")
        repo?.sendCommand(
            MavCmd.SET_MESSAGE_INTERVAL,
            param1 = 191f, // MAG_CAL_PROGRESS message ID
            param2 = if (hz <= 0f) 0f else (1_000_000f / hz) // interval in microseconds
        )
        repo?.sendCommand(
            MavCmd.SET_MESSAGE_INTERVAL,
            param1 = 192f, // MAG_CAL_REPORT message ID
            param2 = if (hz <= 0f) 0f else (1_000_000f / hz) // interval in microseconds
        )
    }

    /**
     * Await a COMMAND_ACK for the given command id within the timeout.
     * Returns the ack if received, or null if the timeout elapses.
     */
    suspend fun awaitCommandAck(commandId: UInt, timeoutMs: Long = 5000L): com.divpundir.mavlink.definitions.common.CommandAck? {
        return try {
            withTimeoutOrNull(timeoutMs) {
                commandAck
                    .filter { it.command.value == commandId }
                    .first()
            }
        } catch (e: Exception) {
            Log.e("SharedVM", "Error while awaiting COMMAND_ACK for $commandId", e)
            null
        }
    }

    fun connect() {
        viewModelScope.launch {
            val provider: MavConnectionProvider? = when (_connectionType.value) {
                ConnectionType.TCP -> {
                    val portInt = port.value.toIntOrNull()
                    if (portInt != null) {
                        TcpConnectionProvider(ipAddress.value, portInt)
                    } else {
                        Log.e("SharedVM", "Invalid port number.")
                        null
                    }
                }
                ConnectionType.BLUETOOTH -> {
                    selectedDevice.value?.device?.let {
                        BluetoothConnectionProvider(it)
                    } ?: run {
                        Log.e("SharedVM", "No Bluetooth device selected.")
                        null
                    }
                }
            }

            if (provider == null) {
                Log.e("SharedVM", "Failed to create connection provider.")
                return@launch
            }

            // If there's an old repo, close its connection first
            repo?.closeConnection()

            val newRepo = MavlinkTelemetryRepository(provider, this@SharedViewModel)
            repo = newRepo
            newRepo.start()
            viewModelScope.launch {
                newRepo.state.collect {
                    _telemetryState.value = it
                }
            }

            viewModelScope.launch {
                newRepo.mavFrame
                    .map { it.message }
                    .filterIsInstance<Statustext>()
                    .collect {
                        val statusText = it.text
                        // Surface common calibration-related prompts, including accel/compass/barometer keywords
                        val lower = statusText.lowercase()
                        val keys = listOf(
                            // generic
                            "calib", "progress",
                            // accel prompts
                            "place", "position", "level", "nose", "left", "right", "back",
                            // barometer
                            "baro", "barometer", "pressure"
                        )
                        if (keys.any { key -> lower.contains(key) }) {
                            _calibrationStatus.value = statusText
                        }
                    }
            }
        }
    }

    // --- Mission State ---
    // Expose mission uploaded state as StateFlow so UI can observe it reliably
    private val _missionUploaded = MutableStateFlow(false)
    val missionUploaded: StateFlow<Boolean> = _missionUploaded.asStateFlow()
    var lastUploadedCount by mutableStateOf(0)

    private val _uploadedWaypoints = MutableStateFlow<List<LatLng>>(emptyList())
    val uploadedWaypoints: StateFlow<List<LatLng>> = _uploadedWaypoints.asStateFlow()

    private val _surveyPolygon = MutableStateFlow<List<LatLng>>(emptyList())
    val surveyPolygon: StateFlow<List<LatLng>> = _surveyPolygon.asStateFlow()

    private val _gridLines = MutableStateFlow<List<Pair<LatLng, LatLng>>>(emptyList())
    val gridLines: StateFlow<List<Pair<LatLng, LatLng>>> = _gridLines.asStateFlow()

    private val _gridWaypoints = MutableStateFlow<List<LatLng>>(emptyList())
    val gridWaypoints: StateFlow<List<LatLng>> = _gridWaypoints.asStateFlow()

    private val _planningWaypoints = MutableStateFlow<List<LatLng>>(emptyList())
    val planningWaypoints: StateFlow<List<LatLng>> = _planningWaypoints.asStateFlow()

    private val _fenceRadius = MutableStateFlow(5f)
    val fenceRadius: StateFlow<Float> = _fenceRadius.asStateFlow()

    private val _geofenceEnabled = MutableStateFlow(false)
    val geofenceEnabled: StateFlow<Boolean> = _geofenceEnabled.asStateFlow()

    private val _geofencePolygon = MutableStateFlow<List<LatLng>>(emptyList())
    val geofencePolygon: StateFlow<List<LatLng>> = _geofencePolygon.asStateFlow()

    fun setSurveyPolygon(polygon: List<LatLng>) {
        _surveyPolygon.value = polygon
        updateGeofencePolygon()
        updateSurveyArea()
    }
    fun setGridLines(lines: List<Pair<LatLng, LatLng>>) { _gridLines.value = lines }
    fun setGridWaypoints(waypoints: List<LatLng>) {
        _gridWaypoints.value = waypoints
        updateGeofencePolygon()
        // Grid waypoints may be derived from survey polygon - ensure survey area is recalculated
        updateSurveyArea()
    }

    fun setPlanningWaypoints(waypoints: List<LatLng>) {
        _planningWaypoints.value = waypoints
        updateGeofencePolygon()
        updateSurveyArea()
    }

    fun setFenceRadius(radius: Float) {
        _fenceRadius.value = radius
        updateGeofencePolygon()
    }

    fun setGeofenceEnabled(enabled: Boolean) {
        _geofenceEnabled.value = enabled
        if (enabled) {
            updateGeofencePolygon()
        } else {
            _geofencePolygon.value = emptyList()
        }
    }

    private fun updateGeofencePolygon() {
        if (!_geofenceEnabled.value) {
            _geofencePolygon.value = emptyList()
            return
        }

        val allWaypoints = mutableListOf<LatLng>()

        if (_uploadedWaypoints.value.isNotEmpty()) {
            allWaypoints.addAll(_uploadedWaypoints.value)
        } else {
            allWaypoints.addAll(_planningWaypoints.value)
        }
        allWaypoints.addAll(_surveyPolygon.value)
        allWaypoints.addAll(_gridWaypoints.value)

        if (allWaypoints.isNotEmpty()) {
            val bufferDistance = _fenceRadius.value.toDouble()
            val polygonBuffer = GeofenceUtils.generatePolygonBuffer(allWaypoints, bufferDistance)
            _geofencePolygon.value = polygonBuffer
        } else {
            _geofencePolygon.value = emptyList()
        }
    }

    // --- MAVLink Actions ---

    fun arm() {
        viewModelScope.launch {
            repo?.arm()
        }
    }

    // --- Notification State ---
    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _isNotificationPanelVisible = MutableStateFlow(false)
    val isNotificationPanelVisible: StateFlow<Boolean> = _isNotificationPanelVisible.asStateFlow()

    fun addNotification(notification: Notification) {
        _notifications.value = listOf(notification) + _notifications.value
    }

    fun toggleNotificationPanel() {
        _isNotificationPanelVisible.value = !_isNotificationPanelVisible.value
    }

    fun startImuCalibration() {
        viewModelScope.launch {
            repo?.let {
                try {
                    val command = com.example.aerogcsclone.manager.CalibrationCommands.createImuCalibrationCommand(
                        targetSystem = it.fcuSystemId,
                        targetComponent = it.fcuComponentId
                    )
                    it.sendCommandLong(command)

                    // Wait briefly for ACK of PREFLIGHT_CALIBRATION (241)
                    val ack = awaitCommandAck(241u, timeoutMs = 4000)
                    val resultVal = ack?.result?.value
                    val accepted = resultVal == 0u || resultVal == 5u // ACCEPTED or IN_PROGRESS
                    _imuCalibrationStartResult.value = accepted || ack == null // treat no-ACK as unknown, but allow UI flow
                } catch (e: Exception) {
                    Log.e("SharedVM", "IMU calibration start failed", e)
                    _imuCalibrationStartResult.value = false
                }
            } ?: run {
                _imuCalibrationStartResult.value = false
            }
        }
    }

    fun resetImuCalibrationStartResult() {
        _imuCalibrationStartResult.value = null
    }

    /**
     * Send a calibration command to the vehicle.
     * This method is used for accelerometer calibration and other calibration procedures.
     */
    suspend fun sendCalibrationCommand(
        command: MavCmd,
        param1: Float = 0f,
        param2: Float = 0f,
        param3: Float = 0f,
        param4: Float = 0f,
        param5: Float = 0f,
        param6: Float = 0f,
        param7: Float = 0f
    ) {
        repo?.sendCommand(
            command = command,
            param1 = param1,
            param2 = param2,
            param3 = param3,
            param4 = param4,
            param5 = param5,
            param6 = param6,
            param7 = param7
        )
    }

    /**
     * Send a raw calibration command using command ID (for ArduPilot-specific commands).
     */
    suspend fun sendCalibrationCommandRaw(
        commandId: UInt,
        param1: Float = 0f,
        param2: Float = 0f,
        param3: Float = 0f,
        param4: Float = 0f,
        param5: Float = 0f,
        param6: Float = 0f,
        param7: Float = 0f
    ) {
        repo?.sendCommandRaw(
            commandId = commandId,
            param1 = param1,
            param2 = param2,
            param3 = param3,
            param4 = param4,
            param5 = param5,
            param6 = param6,
            param7 = param7
        )
    }

    /**
     * Send COMMAND_ACK back to autopilot (for ArduPilot conversational calibration protocol).
     * This is used during IMU calibration where GCS sends ACK to autopilot to confirm position.
     */
    suspend fun sendCommandAck(
        commandId: UInt,
        result: MavResult,
        progress: UByte = 0u,
        resultParam2: Int = 0
    ) {
        repo?.sendCommandAck(
            commandId = commandId,
            result = result,
            progress = progress,
            resultParam2 = resultParam2
        )
    }

    fun uploadMission(missionItems: List<MissionItemInt>, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                Log.i("SharedVM", "Request to upload mission with ${missionItems.size} items")

                if (repo == null) {
                    Log.w("SharedVM", "No repo available, cannot upload mission")
                    _missionUploaded.value = false
                    lastUploadedCount = 0
                    onResult(false, "Not connected to vehicle")
                    return@launch
                }

                if (!_telemetryState.value.fcuDetected) {
                    Log.w("SharedVM", "FCU not detected, aborting mission upload")
                    _missionUploaded.value = false
                    lastUploadedCount = 0
                    onResult(false, "FCU not detected, please connect to vehicle first")
                    return@launch
                }

                Log.i("SharedVM", "Starting mission upload to FCU...")
                val success = repo?.uploadMissionWithAck(missionItems) ?: false
                _missionUploaded.value = success
                if (success) {
                    lastUploadedCount = missionItems.size
                    val waypoints = missionItems.filter { item ->
                        item.command.value != 20u && !(item.x == 0 && item.y == 0)
                    }.map { item ->
                        LatLng(item.x / 1E7, item.y / 1E7)
                    }
                    _uploadedWaypoints.value = waypoints
                    updateGeofencePolygon()
                    // Capture area at time of upload. Prefer explicit survey polygon if available,
                    // otherwise attempt to use uploaded waypoints to compute a polygon area (if possible).
                    if (_surveyPolygon.value.size >= 3) {
                        val areaMeters = GridUtils.calculatePolygonArea(_surveyPolygon.value)
                        val formatted = GridUtils.calculateAndFormatPolygonArea(_surveyPolygon.value)
                        _missionAreaSqMeters.value = areaMeters
                        _missionAreaFormatted.value = formatted
                    } else if (waypoints.size >= 3) {
                        val formatted = GridUtils.calculateAndFormatPolygonArea(waypoints)
                        val areaMeters = GridUtils.calculatePolygonArea(waypoints)
                        _missionAreaSqMeters.value = areaMeters
                        _missionAreaFormatted.value = formatted
                    } else {
                        _missionAreaSqMeters.value = 0.0
                        _missionAreaFormatted.value = "0 acres"
                    }
                    Log.i("SharedVM", "Mission upload succeeded (${missionItems.size})")
                    onResult(true, null)
                } else {
                    lastUploadedCount = 0
                    _uploadedWaypoints.value = emptyList()
                    // clear mission area on failed upload
                    _missionAreaSqMeters.value = 0.0
                    _missionAreaFormatted.value = "0 acres"
                    _missionUploaded.value = false
                    Log.e("SharedVM", "Mission upload failed or timed out")
                    onResult(false, "Mission upload failed or timed out")
                }
            } catch (e: Exception) {
                _missionUploaded.value = false
                lastUploadedCount = 0
                _uploadedWaypoints.value = emptyList()
                Log.e("SharedVM", "Exception during mission upload", e)
                onResult(false, e.message)
            }
        }
    }

    fun startMission(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _telemetryState.value = _telemetryState.value.copy(missionCompleted = false, missionElapsedSec = null)
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

                if (!_missionUploaded.value || lastUploadedCount == 0) {
                    Log.w("SharedVM", "No mission uploaded or acknowledged, cannot start")
                    onResult(false, "No mission uploaded. Please upload a mission first.")
                    return@launch
                }
                Log.i("SharedVM", "✓ Mission upload acknowledged (${lastUploadedCount} items)")

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

                val currentMode = _telemetryState.value.mode
                val isInArmableMode = currentMode?.equals("Stabilize", ignoreCase = true) == true ||
                        currentMode?.equals("Loiter", ignoreCase = true) == true

                if (!isInArmableMode) {
                    Log.i("SharedVM", "Current mode '$currentMode' not suitable for arming, switching to Stabilize")
                    repo?.changeMode(MavMode.STABILIZE)
                    val modeTimeout = 5000L
                    val modeStart = System.currentTimeMillis()
                    while (System.currentTimeMillis() - modeStart < modeTimeout) {
                        if (_telemetryState.value.mode?.equals("Stabilize", ignoreCase = true) == true) {
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

                if (!_telemetryState.value.armed) {
                    Log.i("SharedVM", "Vehicle not armed - attempting to arm")
                    repo?.arm()
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

                if (_telemetryState.value.mode?.contains("Auto", ignoreCase = true) != true) {
                    Log.i("SharedVM", "Switching vehicle mode to AUTO")
                    repo?.changeMode(MavMode.AUTO)
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

                delay(1000)

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
        repo?.let {
            try {
                it.closeConnection()
            } catch (e: Exception) {
                Log.e("SharedVM", "Error closing connection", e)
            }
        }
        repo = null
        _telemetryState.value = TelemetryState()
    }

    // --- Geofence Violation Detection ---
    private val _geofenceViolationDetected = MutableStateFlow(false)
    val geofenceViolationDetected: StateFlow<Boolean> = _geofenceViolationDetected.asStateFlow()

    private var lastGeofenceCheck = 0L
    private val geofenceCheckInterval = 1000L // Check every 1 second

    init {
        // Monitor drone position and check geofence violations
        viewModelScope.launch {
            telemetryState.collect { state ->
                checkGeofenceViolation(state)
            }
        }
    }

    private fun checkGeofenceViolation(state: TelemetryState) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGeofenceCheck < geofenceCheckInterval) return
        lastGeofenceCheck = currentTime

        if (!_geofenceEnabled.value || _geofencePolygon.value.isEmpty()) return

        val droneLat = state.latitude
        val droneLon = state.longitude

        if (droneLat == null || droneLon == null) return

        val dronePosition = LatLng(droneLat, droneLon)
        val isInsideGeofence = isPointInPolygon(dronePosition, _geofencePolygon.value)

        if (!isInsideGeofence && !_geofenceViolationDetected.value) {
            // Geofence violation detected!
            _geofenceViolationDetected.value = true
            Log.w("Geofence", "GEOFENCE VIOLATION DETECTED! Switching to RTL mode")

            // Add notification
            addNotification(
                Notification(
                    message = "GEOFENCE VIOLATION: Drone crossed boundary! Switching to RTL mode",
                    type = NotificationType.WARNING
                )
            )

            // Automatically switch to RTL mode
            switchToRTL()
        } else if (isInsideGeofence && _geofenceViolationDetected.value) {
            // Drone returned to safe zone
            _geofenceViolationDetected.value = false
            Log.i("Geofence", "Drone returned to safe zone")

            addNotification(
                Notification(
                    message = "GEOFENCE CLEAR: Drone returned to safe zone",
                    type = NotificationType.INFO
                )
            )
        }
    }

    private fun isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean {
        if (polygon.size < 3) return true // No valid polygon

        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            val xi = polygon[i].longitude
            val yi = polygon[i].latitude
            val xj = polygon[j].longitude
            val yj = polygon[j].latitude

            if (((yi > point.latitude) != (yj > point.latitude)) &&
                (point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    private fun switchToRTL() {
        viewModelScope.launch {
            repo?.let { repository ->
                try {
                    Log.i("Geofence", "Sending RTL command to drone")
                    repository.changeMode(MavMode.RTL)

                    addNotification(
                        Notification(
                            message = "RTL ACTIVATED: Return to Launch mode activated due to geofence violation",
                            type = NotificationType.WARNING
                        )
                    )
                } catch (e: Exception) {
                    Log.e("Geofence", "Failed to switch to RTL mode: ${e.message}")

                    addNotification(
                        Notification(
                            message = "RTL FAILED: Failed to activate RTL mode - ${e.message}",
                            type = NotificationType.ERROR
                        )
                    )
                }
            } ?: run {
                Log.e("Geofence", "Cannot switch to RTL - no connection to drone")
            }
        }
    }
}
