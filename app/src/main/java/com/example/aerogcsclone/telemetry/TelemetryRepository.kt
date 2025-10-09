package com.example.aerogcsclone.telemetry

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.adapters.coroutines.tryConnect
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.connection.StreamState
import com.divpundir.mavlink.connection.tcp.TcpClientMavConnection
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.definitions.minimal.*
import com.example.aerogcsclone.Telemetry.AppScope
import com.example.aerogcsclone.Telemetry.TelemetryState
//import com.example.aerogcsclone.Telemetry.connections.MavConnectionProvider
import com.example.aerogcsclone.telemetry.connections.MavConnectionProvider
//import com.example.aerogcsclone.telemetry.connections.MavConnectionProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.divpundir.mavlink.api.MavFrame
import com.divpundir.mavlink.api.MavMessage
import kotlinx.coroutines.withTimeoutOrNull

// MAVLink flight modes (ArduPilot values)
object MavMode {
    const val STABILIZE: UInt = 0u
    const val LOITER: UInt = 5u
    const val AUTO: UInt = 3u
    const val RTL: UInt = 6u // RTL (Return to Launch) mode
    const val LAND: UInt = 9u // Add LAND mode for explicit landing
    // Add other modes as needed
}

class MavlinkTelemetryRepository(
    private val provider: MavConnectionProvider,
    private val sharedViewModel: SharedViewModel
) {
    val gcsSystemId: UByte = 255u
    val gcsComponentId: UByte = 1u
    private val _state = MutableStateFlow(TelemetryState())
    val state: StateFlow<TelemetryState> = _state.asStateFlow()

    var fcuSystemId: UByte = 0u
    var fcuComponentId: UByte = 0u

    // Diagnostic info
    private val _lastFailure = MutableStateFlow<Throwable?>(null)
    val lastFailure: StateFlow<Throwable?> = _lastFailure.asStateFlow()

    // Connection
    val connection = provider.createConnection()
    lateinit var mavFrame: Flow<MavFrame<out MavMessage<*>>>

    // MAVLink command value for MISSION_CLEAR_ALL
    private val MISSION_CLEAR_ALL_CMD: UInt = 45u

    // For total distance tracking
    private val positionHistory = mutableListOf<Pair<Double, Double>>()
    private var totalDistanceMeters: Float = 0f
    private var lastMissionRunning = false

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
        mavFrame = connection.mavFrame
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)

        // Log raw messages
        scope.launch {
            mavFrame.collect {
                Log.d("MavlinkRepo", "Frame: ${it.message.javaClass.simpleName} (sysId=${it.systemId}, compId=${it.componentId})")
            }
        }

        // Detect FCU
        scope.launch {
            mavFrame
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

        // Collector to log COMMAND_ACK messages for diagnostics
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<CommandAck>()
                .collect { ack ->
                    try {
                        Log.i(
                            "MavlinkRepo",
                            "COMMAND_ACK received: command=${ack.command} result=${ack.result} progress=${ack.progress}"
                        )
                    } catch (t: Throwable) {
                        Log.i("MavlinkRepo", "COMMAND_ACK received (unable to stringify fields)")
                    }
                }
        }

        // VFR_HUD
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<VfrHud>()
                .collect { hud ->
                    _state.update {
                        it.copy(
                            altitudeMsl = hud.alt,
                            airspeed = hud.airspeed.takeIf { v -> v > 0f },
                            groundspeed = hud.groundspeed.takeIf { v -> v > 0f },
                            formattedAirspeed = formatSpeed(hud.airspeed.takeIf { v -> v > 0f }),
                            formattedGroundspeed = formatSpeed(hud.groundspeed.takeIf { v -> v > 0f }),
                            heading = hud.heading.toFloat()
                        )
                    }
                }
        }

        // GLOBAL_POSITION_INT
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<GlobalPositionInt>()
                .collect { gp ->
                    val altAMSLm = gp.alt / 1000f
                    val relAltM = gp.relativeAlt / 1000f
                    val lat = gp.lat.takeIf { it != Int.MIN_VALUE }?.let { it / 10_000_000.0 }
                    val lon = gp.lon.takeIf { it != Int.MIN_VALUE }?.let { it / 10_000_000.0 }

                    val missionRunning = state.value.mode?.equals("Auto", ignoreCase = true) == true && state.value.armed
                    if (missionRunning) {
                        if (!lastMissionRunning) {
                            positionHistory.clear()
                            totalDistanceMeters = 0f
                        }
                        if (lat != null && lon != null) {
                            if (positionHistory.isNotEmpty()) {
                                val (prevLat, prevLon) = positionHistory.last()
                                val dist = haversine(prevLat, prevLon, lat, lon)
                                totalDistanceMeters += dist
                            }
                            positionHistory.add(lat to lon)
                        }
                        _state.update {
                            it.copy(
                                altitudeMsl = altAMSLm,
                                altitudeRelative = relAltM,
                                latitude = lat,
                                longitude = lon,
                                totalDistanceMeters = totalDistanceMeters
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                altitudeMsl = altAMSLm,
                                altitudeRelative = relAltM,
                                latitude = lat,
                                longitude = lon,
                                totalDistanceMeters = if (positionHistory.isNotEmpty()) totalDistanceMeters else null
                            )
                        }
                    }
                    lastMissionRunning = missionRunning
                }
        }

        // BATTERY_STATUS
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<BatteryStatus>()
                .collect { b ->
                    val currentA = if (b.currentBattery.toInt() == -1) null else b.currentBattery / 100f
                    _state.update { it.copy(currentA = currentA) }
                }
        }
        // HEARTBEAT for mode, armed, armable
        var missionTimerJob: kotlinx.coroutines.Job? = null
        var lastMode: String? = null
        var lastArmed: Boolean? = null
        scope.launch {
            mavFrame
                .filter { frame -> state.value.fcuDetected && frame.systemId == fcuSystemId }
                .map { frame -> frame.message }
                .filterIsInstance<Heartbeat>()
                .collect { hb ->
                    val armed = (hb.baseMode.value and MavModeFlag.SAFETY_ARMED.value) != 0u
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
                    _state.update { it.copy(armed = armed, mode = mode) }

                    // Arm/Disarm Notifications
                    if (lastArmed != null && armed != lastArmed) {
                        if (armed) {
                            sharedViewModel.addNotification(Notification("Drone Armed", NotificationType.SUCCESS))
                        } else {
                            sharedViewModel.addNotification(Notification("Drone Disarmed", NotificationType.INFO))
                        }
                    }

                    // Mission timer logic
                    if (lastMode != mode || lastArmed != armed) {
                        if (mode.equals("Auto", ignoreCase = true) && armed && (lastMode != mode || lastArmed != armed)) {
                            missionTimerJob?.cancel()
                            missionTimerJob = scope.launch {
                                var elapsed = 0L
                                _state.update { it.copy(missionElapsedSec = 0L, missionCompleted = false, lastMissionElapsedSec = null) }
                                while (isActive && state.value.mode?.equals("Auto", ignoreCase = true) == true && state.value.armed) {
                                    delay(1000)
                                    elapsed += 1
                                    _state.update { it.copy(missionElapsedSec = elapsed) }
                                }
                                // Mission ended: store last elapsed time
                                val lastElapsed = state.value.missionElapsedSec
                                _state.update { it.copy(missionElapsedSec = null, missionCompleted = true, lastMissionElapsedSec = lastElapsed) }
                            }
                        } else if ((lastMode?.equals("Auto", ignoreCase = true) == true && mode != "Auto") ||
                                   (lastArmed == true && armed == false && mode.equals("Auto", ignoreCase = true))) {
                            // Mission ended (either mode changed from Auto, or drone disarmed in Auto)
                            missionTimerJob?.cancel()
                            missionTimerJob = null
                            val lastElapsed = state.value.missionElapsedSec
                            _state.update { it.copy(missionElapsedSec = null, missionCompleted = true, lastMissionElapsedSec = lastElapsed) }
                        }
                        lastMode = mode
                        lastArmed = armed
                    }
                }
        }
        // SYS_STATUS
        scope.launch {
            mavFrame
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
                    _state.update { it.copy(voltage = vBatt, batteryPercent = pct, armable = armable) }
                }
        }

        // STATUSTEXT for arming failures and other messages
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<Statustext>()
                .collect { status ->
                    val message = status.text.toString()
                    val type = when (status.severity.value) {
                        MavSeverity.EMERGENCY.value, MavSeverity.ALERT.value, MavSeverity.CRITICAL.value, MavSeverity.ERROR.value -> NotificationType.ERROR
                        MavSeverity.WARNING.value -> NotificationType.WARNING
                        else -> NotificationType.INFO
                    }
                    sharedViewModel.addNotification(Notification(message, type))
                }
        }

        // MISSION_CURRENT for mission progress
        var lastMissionSeq = -1
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<MissionCurrent>()
                .collect { missionCurrent ->
                    if (missionCurrent.seq.toInt() != lastMissionSeq) {
                        lastMissionSeq = missionCurrent.seq.toInt()
                        sharedViewModel.addNotification(
                            Notification(
                                "Executing waypoint #${lastMissionSeq}",
                                NotificationType.INFO
                            )
                        )
                    }
                }
        }

        // MISSION_ACK for mission upload status
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<MissionAck>()
                .collect { missionAck ->
                    val message = "Mission upload: ${missionAck.type.entry?.name ?: "UNKNOWN"}"
                    val type = if (missionAck.type.value == MavMissionResult.MAV_MISSION_ACCEPTED.value) NotificationType.SUCCESS else NotificationType.ERROR
                    sharedViewModel.addNotification(Notification(message, type))
                }
        }

        // GPS_RAW_INT
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<GpsRawInt>()
                .collect { gps ->
                    val sats = gps.satellitesVisible.toInt().takeIf { it >= 0 }
                    val hdop = if (gps.eph.toUInt() == 0xFFFFu) null else gps.eph.toFloat() / 100f
                    _state.update { it.copy(sats = sats, hdop = hdop) }
                }
        }

        // Mission progress logging: MISSION_ITEM_REACHED, MISSION_CURRENT, and mode


        // Helper to request mission items from FCU and return as list
        suspend fun requestMissionItemsFromFcu(timeoutMs: Long = 5000): List<MissionItemInt> {
            val items = mutableListOf<MissionItemInt>()
            val expectedCountDeferred = CompletableDeferred<Int?>()
            val perSeqMap = mutableMapOf<Int, CompletableDeferred<Unit>>()
            val job = AppScope.launch {
                connection.mavFrame.collect { frame ->
                    when (val msg = frame.message) {
                        is MissionCount -> {
                            expectedCountDeferred.complete(msg.count.toInt())
                        }
                        is MissionItemInt -> {
                            items.add(msg)
                            perSeqMap[msg.seq.toInt()]?.let { d -> if (!d.isCompleted) d.complete(Unit) }
                        }
                        else -> {}
                    }
                }
            }
            val req = MissionRequestList(targetSystem = fcuSystemId, targetComponent = fcuComponentId)
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, req)
            val expectedCount = withTimeoutOrNull(timeoutMs) { expectedCountDeferred.await() } ?: 0
            for (seq in 0 until expectedCount) {
                val seqDeferred = CompletableDeferred<Unit>()
                perSeqMap[seq] = seqDeferred
                val reqItem = MissionRequestInt(targetSystem = fcuSystemId, targetComponent = fcuComponentId, seq = seq.toUShort())
                connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, reqItem)
                withTimeoutOrNull(1500L) { seqDeferred.await() }
                perSeqMap.remove(seq)
            }
            delay(200)
            job.cancel()
            return items.sortedBy { it.seq.toInt() }
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
                gcsComponentId, commandLong
            )
            Log.d("MavlinkRepo", "Sent COMMAND_LONG: cmd=${command} p1=$param1 p2=$param2 p3=$param3 p4=$param4 p5=$param5 p6=$param6 p7=$param7")
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Failed to send command", e)
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

    /**
     * Change vehicle mode (ArduPilot: param1=1, param2=customMode)
     * Waits for Heartbeat confirmation.
     */
    suspend fun changeMode(customMode: UInt): Boolean {
        sendCommand(
            MavCmd.DO_SET_MODE,
            1f,                   // param1: MAV_MODE_FLAG_CUSTOM_MODE_ENABLED (always 1 for ArduPilot)
            customMode.toFloat(), // param2: custom mode (e.g., 3u for AUTO)
            0f, 0f, 0f, 0f, 0f
        )
        // Wait for Heartbeat to confirm mode change
        val timeoutMs = 5000L
        val start = System.currentTimeMillis()
        val expectedMode = when (customMode) {
            3u -> "Auto"
            0u -> "Stabilize"
            5u -> "Loiter"
            else -> "Unknown"
        }
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (state.value.mode?.contains(expectedMode, ignoreCase = true) == true) {
                Log.i("MavlinkRepo", "Mode changed to ${state.value.mode}")
                return true
            }
            delay(200)
        }
        Log.e("MavlinkRepo", "Mode change to ${customMode} not confirmed in Heartbeat")
        return false
    }

    /**
     * Uploads a mission using the MAVLink mission protocol handshake.
     * Returns true if ACK received, false otherwise.
     */
    @Suppress("DEPRECATION")
    suspend fun uploadMissionWithAck(missionItems: List<MissionItemInt>, timeoutMs: Long = 15000): Boolean {
        if (!state.value.fcuDetected) {
            Log.e("MavlinkRepo", "FCU not detected, cannot upload mission")
            throw IllegalStateException("FCU not detected")
        }
        if (missionItems.isEmpty()) {
            Log.w("MavlinkRepo", "No mission items to upload")
            return false
        }

        try {
            // Step 0: Clear previous mission
            Log.i("MavlinkRepo", "[Mission Upload] Sending MISSION_CLEAR_ALL...")
            val clearAll = MissionClearAll(targetSystem = fcuSystemId, targetComponent = fcuComponentId)
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, clearAll)
            // Wait for COMMAND_ACK for MISSION_CLEAR_ALL
            val clearAckDeferred = CompletableDeferred<Boolean>()
            val clearJob = AppScope.launch {
                connection.mavFrame
                    .filter { it.systemId == fcuSystemId }
                    .map { it.message }
                    .filterIsInstance<CommandAck>()
                    .collect { ack ->
                        if (ack.command.value == MISSION_CLEAR_ALL_CMD && ack.result.value == MavResult.ACCEPTED.value) {
                            Log.i("MavlinkRepo", "[Mission Upload] MISSION_CLEAR_ALL acknowledged by FCU")
                            clearAckDeferred.complete(true)
                        }
                    }
            }
            val clearAck = withTimeoutOrNull(3000L) { clearAckDeferred.await() } ?: false
            clearJob.cancel()
            if (!clearAck) {
                Log.w("MavlinkRepo", "[Mission Upload] No ACK for MISSION_CLEAR_ALL; proceeding anyway")
            }

            Log.i("MavlinkRepo", "[Mission Upload] Starting upload of ${missionItems.size} items...")
            // Send MissionCount
            val missionCount = MissionCount(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                count = missionItems.size.toUShort()
            )
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
            Log.i("MavlinkRepo", "[Mission Upload] Sent MISSION_COUNT=${missionItems.size}")

            val ackDeferred = CompletableDeferred<Boolean>()
            val sentSeqs = mutableSetOf<Int>()
            var firstRequestReceived = false

            // Resend MISSION_COUNT periodically until first request or timeout
            val resendJob = AppScope.launch {
                while (isActive && !firstRequestReceived && !ackDeferred.isCompleted) {
                    try {
                        connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
                        Log.d("MavlinkRepo", "Resent MISSION_COUNT")
                    } catch (e: Exception) {
                        Log.e("MavlinkRepo", "Failed to resend MISSION_COUNT", e)
                    }
                    delay(700)
                }
            }

            // Collector job
            val job = AppScope.launch {
                connection.mavFrame
                    .collect { frame ->
                        val senderSys = frame.systemId
                        val senderComp = frame.componentId
                        when (val msg = frame.message) {
                            is MissionRequestInt -> {
                                Log.d("MavlinkRepo", "[Mission Upload] Received MissionRequestInt seq=${msg.seq}")
                                firstRequestReceived = true
                                val seq = msg.seq.toInt()
                                if (seq < 0 || seq >= missionItems.size) {
                                    Log.w("MavlinkRepo", "FC requested invalid seq=$seq (MissionRequestInt)")
                                    return@collect
                                }
                                val item = missionItems[seq]
                                Log.i("MavlinkRepo", "[Mission Upload] Sending waypoint seq=$seq: ${item.command} lat=${item.x / 1e7} lon=${item.y / 1e7} alt=${item.z}")
                                val missionItem = item.copy(
                                    targetSystem = senderSys.toUByte(),
                                    targetComponent = senderComp.toUByte(),
                                    seq = seq.toUShort()
                                )
                                try {
                                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionItem)
                                    sentSeqs.add(seq)
                                    Log.i("MavlinkRepo", "Sent MISSION_ITEM_INT seq=$seq to sys=$senderSys comp=$senderComp")
                                } catch (e: Exception) {
                                    Log.e("MavlinkRepo", "Failed to send mission item seq=$seq", e)
                                }
                            }
                            is MissionRequest -> {
                                Log.d("MavlinkRepo", "[Mission Upload] Received MissionRequest seq=${msg.seq}")
                                firstRequestReceived = true
                                val seq = msg.seq.toInt()
                                if (seq < 0 || seq >= missionItems.size) {
                                    Log.w("MavlinkRepo", "FC requested invalid seq=$seq (MissionRequest)")
                                    return@collect
                                }
                                val itemInt = missionItems[seq]
                                Log.i("MavlinkRepo", "[Mission Upload] Sending waypoint seq=$seq: ${itemInt.command} lat=${itemInt.x / 1e7} lon=${itemInt.y / 1e7} alt=${itemInt.z}")
                                val missionItemInt = itemInt.copy(
                                    targetSystem = senderSys.toUByte(),
                                    targetComponent = senderComp.toUByte(),
                                    seq = seq.toUShort()
                                )
                                try {
                                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionItemInt)
                                    sentSeqs.add(seq)
                                    Log.i("MavlinkRepo", "Sent MISSION_ITEM_INT seq=$seq to sys=$senderSys comp=$senderComp (responding to MissionRequest)")
                                } catch (e: Exception) {
                                    Log.e("MavlinkRepo", "Failed to send mission item(seq=$seq) as MissionItemInt", e)
                                }
                            }
                            is MissionAck -> {
                                Log.i("MavlinkRepo", "[Mission Upload] Received MISSION_ACK type=${msg.type}")
                                if (!ackDeferred.isCompleted) ackDeferred.complete(true)
                                return@collect
                            }
                            else -> {
                                // ignore other messages
                            }
                        }
                    }
            }

            // Wait a short period for first request; if none, fallback to sending all items
            val firstRequestTimeout = 5000L
            val startWait = System.currentTimeMillis()
            while (!firstRequestReceived && !ackDeferred.isCompleted && System.currentTimeMillis() - startWait < firstRequestTimeout) {
                delay(100)
            }

            if (!firstRequestReceived) {
                Log.w("MavlinkRepo", "[Mission Upload] No MissionRequest received; sending all items directly")
                for (seq in 0 until missionItems.size) {
                    if (sentSeqs.contains(seq)) continue
                    val item = missionItems[seq]
                    Log.i("MavlinkRepo", "[Mission Upload] Fallback sending waypoint seq=$seq: ${item.command} lat=${item.x / 1e7} lon=${item.y / 1e7} alt=${item.z}")
                    val missionItem = item.copy(
                        targetSystem = fcuSystemId,
                        targetComponent = fcuComponentId,
                        seq = seq.toUShort()
                    )
                    try {
                        Log.d("MavlinkRepo", "Sending fallback item seq=$seq cmd=${missionItem.command} frame=${missionItem.frame} x=${missionItem.x} y=${missionItem.y} z=${missionItem.z}")
                        connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionItem)
                        sentSeqs.add(seq)
                        Log.i("MavlinkRepo", "Fallback: Sent MISSION_ITEM_INT seq=$seq")
                        delay(300)
                    } catch (e: Exception) {
                        Log.e("MavlinkRepo", "Fallback: Failed to send mission item seq=$seq", e)
                    }
                }
            }

            val ackReceived = withTimeoutOrNull(timeoutMs) {
                ackDeferred.await()
            } ?: false

            job.cancel()
            resendJob.cancel()

            if (ackReceived) {
                Log.i("MavlinkRepo", "[Mission Upload] Mission upload acknowledged by FCU")
                return true
            } else {
                Log.e("MavlinkRepo", "[Mission Upload] Mission upload timed out waiting for ACK")
                return false
            }
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Upload] Mission upload failed", e)
            return false
        }
    }

    suspend fun requestMissionAndLog(timeoutMs: Long = 5000) {
        if (!state.value.fcuDetected) {
            Log.w("MavlinkRepo", "FCU not detected; cannot request mission")
            return
        }
        try {
            val received = mutableListOf<Pair<Int, String>>()
            val expectedCountDeferred = CompletableDeferred<Int?>()
            val perSeqMap = mutableMapOf<Int, CompletableDeferred<Unit>>()

            val job = AppScope.launch {
                connection.mavFrame.collect { frame ->
                    when (val msg = frame.message) {
                        is MissionCount -> {
                            Log.i("MavlinkRepo", "Readback: MISSION_COUNT=${msg.count} from sys=${frame.systemId}")
                            expectedCountDeferred.complete(msg.count.toInt())
                        }
                        is MissionItemInt -> {
                            val lat = msg.x / 1e7
                            val lon = msg.y / 1e7
                            Log.i("MavlinkRepo", "Readback: MISSION_ITEM_INT seq=${msg.seq} lat=$lat lon=$lon alt=${msg.z}")
                            received.add(msg.seq.toInt() to "INT: lat=$lat lon=$lon alt=${msg.z}")
                            perSeqMap[msg.seq.toInt()]?.let { d -> if (!d.isCompleted) d.complete(Unit) }
                        }
                        is MissionItem -> {
                            Log.i("MavlinkRepo", "Readback: MISSION_ITEM seq=${msg.seq} x=${msg.x} y=${msg.y} z=${msg.z}")
                            received.add(msg.seq.toInt() to "FLT: x=${msg.x} y=${msg.y} z=${msg.z}")
                            perSeqMap[msg.seq.toInt()]?.let { d -> if (!d.isCompleted) d.complete(Unit) }
                        }
                        is MissionAck -> {
                            Log.i("MavlinkRepo", "Readback: MISSION_ACK type=${msg.type}")
                        }
                        else -> {}
                    }
                }
            }

            try {
                val req = MissionRequestList(targetSystem = fcuSystemId, targetComponent = fcuComponentId)
                connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, req)
                Log.i("MavlinkRepo", "Sent MISSION_REQUEST_LIST to FCU")
            } catch (e: Exception) {
                Log.e("MavlinkRepo", "Failed to send MISSION_REQUEST_LIST", e)
            }

            val expectedCount = withTimeoutOrNull(timeoutMs) { expectedCountDeferred.await() } ?: run {
                Log.w("MavlinkRepo", "Did not receive MISSION_COUNT from FCU within timeout")
                job.cancel()
                return
            }

            Log.i("MavlinkRepo", "Expecting $expectedCount mission items - requesting each item")

            for (seq in 0 until expectedCount) {
                val seqDeferred = CompletableDeferred<Unit>()
                perSeqMap[seq] = seqDeferred
                try {
                    val reqItem = MissionRequestInt(targetSystem = fcuSystemId, targetComponent = fcuComponentId, seq = seq.toUShort())
                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, reqItem)
                    Log.d("MavlinkRepo", "Sent MISSION_REQUEST_INT for seq=$seq")
                } catch (e: Exception) {
                    Log.e("MavlinkRepo", "Failed to send MISSION_REQUEST_INT seq=$seq", e)
                }

                val got = withTimeoutOrNull(1500L) {
                    seqDeferred.await()
                    true
                } ?: false

                if (!got) {
                    Log.w("MavlinkRepo", "Did not receive item for seq=$seq within timeout")
                }

                perSeqMap.remove(seq)
            }

            delay(200)
            job.cancel()

            Log.i("MavlinkRepo", "Mission readback complete: expected=$expectedCount items=${received.size}")
            received.sortedBy { it.first }.forEach { (seq, desc) -> Log.i("MavlinkRepo", "Item #$seq -> $desc") }
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Error during mission readback", e)
        }
    }

    /**
     * Start the mission after uploading.
     * Replicates the Dart/Flutter workflow:
     * 1. Arm the vehicle
     * 2. Send MISSION_START as CommandLong
     * 3. Set mode to AUTO
     */
    suspend fun startMission(): Boolean {
        Log.i("MavlinkRepo", "[Mission Start] Initiating mission start workflow...")
        if (!state.value.fcuDetected) {
            Log.w("MavlinkRepo", "[Mission Start] Cannot start mission - FCU not detected")
            return false
        }

        // Step 1: Arm the vehicle
        try {
            Log.i("MavlinkRepo", "[Mission Start] Sending ARM command...")
            arm()
            Log.i("MavlinkRepo", "[Mission Start] ARM command sent")
            delay(500)
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Start] Failed to arm vehicle", e)
            return false
        }

        // Step 2: Send MISSION_START as CommandLong
        try {
            Log.i("MavlinkRepo", "[Mission Start] Sending MISSION_START command...")
            sendMissionStartCommand()
            Log.i("MavlinkRepo", "[Mission Start] MISSION_START command sent")
            delay(500)
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Start] Failed to send MISSION_START command", e)
            return false
        }

        // Step 3: Set mode to AUTO
        try {
            Log.i("MavlinkRepo", "[Mission Start] Setting mode to AUTO...")
            val modeChanged = changeMode(MavMode.AUTO)
            Log.i("MavlinkRepo", "[Mission Start] Set mode to AUTO, result=$modeChanged")
            delay(500)
            if (!modeChanged) {
                Log.e("MavlinkRepo", "[Mission Start] Failed to switch to AUTO mode")
                return false
            }
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Start] Failed to set AUTO mode", e)
            return false
        }

        Log.i("MavlinkRepo", "[Mission Start] Mission start workflow complete. Vehicle should be in AUTO mode.")
        return true
    }

    /**
     * Sends MISSION_START as CommandLong (param1=0, param2=0, ...)
     */
    suspend fun sendMissionStartCommand() {
        val cmd = CommandLong(
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId,
            command = MavCmd.MISSION_START.wrap(),
            confirmation = 0u,
            param1 = 0f,
            param2 = 0f,
            param3 = 0f,
            param4 = 0f,
            param5 = 0f,
            param6 = 0f,
            param7 = 0f
        )
        connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, cmd)
    }

    suspend fun closeConnection() {
        try {
            // Attempt to close the TCP connection gracefully
            connection.close()
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Error closing TCP connection", e)
        }
    }

    // Haversine formula for distance in meters
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (R * c).toFloat()
    }

    // Format speed for human-readable display
    private fun formatSpeed(speed: Float?): String? {
        if (speed == null) return null
        return when {
//            speed >= 1000f -> String.format("%.2f km/s", speed / 1000f)
            speed >= 1f -> String.format("%.2f m/s", speed)
//            speed >= 0.01f -> String.format("%.1f cm/s", speed * 100f)
//            speed > 0f -> String.format("%.1f mm/s", speed * 1000f)
            else -> "0 m/s"
        }
    }

    suspend fun sendCommandLong(command: CommandLong) {
        try {
            connection.trySendUnsignedV2(
                gcsSystemId,
                gcsComponentId,
                command
            )
            Log.d("MavlinkRepo", "Sent COMMAND_LONG (custom): $command")
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Failed to send custom COMMAND_LONG", e)
        }
    }


}
