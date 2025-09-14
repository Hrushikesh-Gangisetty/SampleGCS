package com.example.aerogcsclone.Telemetry

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.adapters.coroutines.tryConnect
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.connection.StreamState
import com.divpundir.mavlink.connection.tcp.TcpClientMavConnection
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.definitions.minimal.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MavlinkTelemetryRepository(
    private val host: String,
    private val port: Int
) {
    private var mission by mutableStateOf<List<LatLng>>(emptyList())
    private val gcsSystemId: UByte = 200u
    private val gcsComponentId: UByte = 1u
    private val _state = MutableStateFlow(TelemetryState())
    val state: StateFlow<TelemetryState> = _state.asStateFlow()

    private var fcuSystemId: UByte = 0u
    private var fcuComponentId: UByte = 0u

    // Diagnostic info
    private val _lastFailure = MutableStateFlow<Throwable?>(null)
    val lastFailure: StateFlow<Throwable?> = _lastFailure.asStateFlow()

    // Connection
    private val connection = TcpClientMavConnection(host, port, CommonDialect).asCoroutine()

    fun start() {
        val scope = AppScope

        suspend fun reconnect(scope: kotlinx.coroutines.CoroutineScope) {
            while (scope.isActive) {
                try {
                    if (connection.tryConnect(scope)) {
                        return // Exit on successful connection
                    }
                } catch (e: Exception) {
                    Log.e("MavlinkRepo", "Connection attempt failed", e)
                    _lastFailure.value = e
                }
                delay(1000)
            }
        }

        // Manage connection state + reconnects
        scope.launch {
            reconnect(this) // Initial connection attempt
            connection.streamState.collect { st ->
                when (st) {
                    is StreamState.Active -> {
                        if (!state.value.connected) {
                            Log.i("MavlinkRepo", "Connection Active")
                            _state.update { it.copy(connected = true) }
                        }
                    }
                    is StreamState.Inactive -> {
                        if (state.value.connected) {
                            Log.i("MavlinkRepo", "Connection Inactive, reconnecting...")
                            _state.update { it.copy(connected = false, fcuDetected = false) }
                            reconnect(this)
                        }
                    }
                }
            }
        }

        // Send GCS heartbeat
        scope.launch {
            val heartbeat = Heartbeat(
                type = MavType.GCS.wrap(),
                autopilot = MavAutopilot.INVALID.wrap(),
                baseMode = emptyList<MavModeFlag>().wrap(),
                customMode = 0u,
                mavlinkVersion = 3u
            )
            while (isActive) {
                if (state.value.connected) {
                    try {
                        connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, heartbeat)
                    } catch (e: Exception) {
                        Log.e("MavlinkRepo", "Failed to send heartbeat", e)
                        _lastFailure.value = e
                    }
                }
                delay(1000)
            }
        }

        // Shared message stream
        val mavFrameStream = connection.mavFrame
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)

        // Log raw messages
        scope.launch {
            mavFrameStream.collect {
                Log.d("MavlinkRepo", "Frame: ${it.message.javaClass.simpleName} (sysId=${it.systemId}, compId=${it.componentId})")
            }
        }

        // Detect FCU
        scope.launch {
            mavFrameStream
                .filter { it.message is Heartbeat && (it.message as Heartbeat).type != MavType.GCS.wrap() }
                .collect {
                    if (!state.value.fcuDetected) {
                        fcuSystemId = it.systemId
                        fcuComponentId = it.componentId
                        Log.i("MavlinkRepo", "FCU detected sysId=$fcuSystemId compId=$fcuComponentId")
                        _state.update { it.copy(fcuDetected = true) }

                        // Set message intervals
                        launch {
                            suspend fun setMessageRate(messageId: UInt, hz: Float) {
                                val intervalUsec = if (hz <= 0f) 0f else (1_000_000f / hz)
                                val cmd = CommandLong(
                                    targetSystem = fcuSystemId,
                                    targetComponent = fcuComponentId,
                                    command = MavCmd.SET_MESSAGE_INTERVAL.wrap(),
                                    confirmation = 0u,
                                    param1 = messageId.toFloat(),
                                    param2 = intervalUsec,
                                    param3 = 0f,
                                    param4 = 0f,
                                    param5 = 0f,
                                    param6 = 0f,
                                    param7 = 0f
                                )
                                try {
                                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, cmd)
                                } catch (e: Exception) {
                                    Log.e("MavlinkRepo", "Failed to send SET_MESSAGE_INTERVAL", e)
                                    _lastFailure.value = e
                                }
                            }

                            setMessageRate(1u, 1f)   // SYS_STATUS
                            setMessageRate(24u, 1f)  // GPS_RAW_INT
                            setMessageRate(33u, 5f)  // GLOBAL_POSITION_INT
                            setMessageRate(74u, 5f)  // VFR_HUD
                            setMessageRate(147u, 1f) // BATTERY_STATUS
                        }
                    }
                }
        }

        // Collectors

        // VFR_HUD
        scope.launch {
            mavFrameStream
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<VfrHud>()
                .collect { hud ->
                    _state.update {
                        it.copy(
                            altitudeMsl = hud.alt,
                            airspeed = hud.airspeed.takeIf { v -> v > 0f },
                            groundspeed = hud.groundspeed.takeIf { v -> v > 0f }
                        )
                    }
                }
        }

        // GLOBAL_POSITION_INT
        scope.launch {
            mavFrameStream
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<GlobalPositionInt>()
                .collect { gp ->
                    val altAMSLm = gp.alt / 1000f
                    val relAltM = gp.relativeAlt / 1000f
                    val lat = gp.lat.takeIf { it != Int.MIN_VALUE }?.let { it / 10_000_000.0 }
                    val lon = gp.lon.takeIf { it != Int.MIN_VALUE }?.let { it / 10_000_000.0 }
                    _state.update {
                        it.copy(
                            altitudeMsl = altAMSLm,
                            altitudeRelative = relAltM,
                            latitude = lat,
                            longitude = lon
                        )
                    }
                }
        }

        // BATTERY_STATUS
        scope.launch {
            mavFrameStream
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<BatteryStatus>()
                .collect { b ->
                    val currentA = if (b.currentBattery.toInt() == -1) null else b.currentBattery / 100f
                    _state.update { it.copy(currentA = currentA) }
                }
        }
        //HEARTBEAT for mode, armed, armable
        scope.launch {
            mavFrameStream
                .filter{ frame-> state.value.fcuDetected && frame.systemId == fcuSystemId }
                .map{frame -> frame.message}
                .filterIsInstance<Heartbeat>()
                .collect{ hb->
                    val armed = (hb.baseMode.value and MavModeFlag.SAFETY_ARMED.value )!= 0u
                    val mode = when (hb.customMode) {
                        0u -> "Stabilize"
                        1u -> "Acro"
                        2u -> "Alt Hold"
                        3u -> "Auto"
                        4u -> "Guided"
                        5u -> "Loiter"
                        6u -> "RTL"
                        7u -> "Circle"
                        9u -> "Land"
                        11u -> "Drift"
                        13u -> "Sport"
                        14u -> "Flip"
                        15u -> "AutoTune"
                        16u -> "Pos Hold"
                        17u -> "Brake"
                        18u -> "Throw"
                        19u -> "Avoid_ADSB"
                        20u -> "Guided_NoGPS"
                        21u -> "Smart_RTL"
                        22u -> "FlowHold"
                        23u -> "Follow"
                        24u -> "ZigZag"
                        25u -> "SystemID"
                        26u -> "AutoRotate"
                        27u -> "Auto_RTL"
                        else -> "Unknown"
                    }
                    _state.update { it.copy(armed=armed , mode = mode)}
                }
        }
        // SYS_STATUS
        scope.launch {
            mavFrameStream
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<SysStatus>()
                .collect { s ->
                    val vBatt = if (s.voltageBattery.toUInt() == 0xFFFFu) null else s.voltageBattery.toFloat() / 1000f
                    val pct = if (s.batteryRemaining.toInt() == -1) null else s.batteryRemaining.toInt()
                    val SENSOR_3D_GYRO = 1u
                    val present = (s.onboardControlSensorsPresent.value and SENSOR_3D_GYRO) != 0u
                    val enabled = (s.onboardControlSensorsEnabled.value and SENSOR_3D_GYRO) != 0u
                    val healthy = (s.onboardControlSensorsHealth.value and SENSOR_3D_GYRO) != 0u
                    val armable = present && enabled && healthy
                    _state.update { it.copy(voltage = vBatt, batteryPercent = pct , armable = armable) }
                }
        }

        // GPS_RAW_INT
        scope.launch {
            mavFrameStream
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<GpsRawInt>()
                .collect { gps ->
                    val sats = gps.satellitesVisible.toInt().takeIf { it >= 0 }
                    val hdop = if (gps.eph.toUInt() == 0xFFFFu) null else gps.eph.toFloat() / 100f
                    _state.update { it.copy(sats = sats, hdop = hdop) }
                }
        }

        // Mission handling
        scope.launch {
            mavFrameStream
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .collect { message ->
                    when (message) {
                        is MissionRequest -> {
                            val seq = message.seq.toInt()
                            if (seq < mission.size) {
                                val waypoint = mission[seq]
                                val missionItem = MissionItemInt(
                                    targetSystem = fcuSystemId,
                                    targetComponent = fcuComponentId,
                                    seq = seq.toUShort(),
                                    frame = MavFrame.GLOBAL_RELATIVE_ALT.wrap(),
                                    command = MavCmd.NAV_WAYPOINT.wrap(),
                                    current = if (seq == 0) 1u else 0u,
                                    autocontinue = 1u,
                                    param1 = 0f,
                                    param2 = 0f,
                                    param3 = 0f,
                                    param4 = 0f,
                                    x = (waypoint.latitude * 1e7).toInt(),
                                    y = (waypoint.longitude * 1e7).toInt(),
                                    z = 100f, // Default altitude
                                    missionType = MavMissionType.MISSION.wrap()
                                )
                                try {
                                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionItem)
                                } catch (e: Exception) {
                                    Log.e("MavlinkRepo", "Failed to send mission item", e)
                                    _lastFailure.value = e
                                }
                            }
                        }
                        is MissionAck -> {
                            Log.i("MavlinkRepo", "Mission upload acknowledged with result: ${message.type.value}")
                            if (message.type == MavMissionResult.MAV_MISSION_ACCEPTED.wrap()) {
                                _state.update { it.copy(missionLoaded = true) }
                            }
                        }
                    }
                }
        }
    }

    suspend fun sendCommand(command: MavCmd, param1: Float = 0f, param2: Float = 0f, param3: Float = 0f, param4: Float = 0f, param5: Float = 0f, param6: Float = 0f, param7: Float = 0f) {
        val commandLong = CommandLong(
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId,
            command = command.wrap(),
            confirmation = 0u,
            param1 = param1,
            param2 = param2,
            param3 = param3,
            param4 = param4,
            param5 = param5,
            param6 = param6,
            param7 = param7
        )
        try {
            connection.trySendUnsignedV2(
               gcsSystemId,
                gcsComponentId, commandLong)
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Failed to send command", e)
            _lastFailure.value = e
        }
    }

    suspend fun arm() {
        if (state.value.armable) {
            sendCommand(
                MavCmd.COMPONENT_ARM_DISARM,
                1f
            )
        } else {
            Log.w("MavlinkRepo", "Arm command rejected, vehicle not armable")
        }
    }

    suspend fun disarm() {
        sendCommand(
            MavCmd.COMPONENT_ARM_DISARM,
            0f
        )
    }

    suspend fun changeMode(mode: MavMode) {
        sendCommand(
            MavCmd.DO_SET_MODE,
            mode.value.toFloat(),
            0f
        )
    }

    suspend fun takeoff(altitude: Float) {
        sendCommand(
            MavCmd.NAV_TAKEOFF,
            -1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            altitude
        )
    }

    suspend fun land() {
        sendCommand(MavCmd.NAV_LAND)
    }

    suspend fun loadMission(waypoints: List<LatLng>) {
        mission = waypoints
        val missionCount = MissionCount(
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId,
            count = mission.size.toUShort(),
            missionType = MavMissionType.MISSION.wrap()
        )
        try {
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Failed to send mission count", e)
            _lastFailure.value = e
        }
    }

    suspend fun startMission() {
        sendCommand(MavCmd.MISSION_START)
    }
}
