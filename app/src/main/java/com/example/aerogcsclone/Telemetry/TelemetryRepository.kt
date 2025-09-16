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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.absoluteValue

// MAVLink flight modes (ArduPilot values)
object MavMode {
    const val STABILIZE: UInt = 0u
    const val LOITER: UInt = 5u
    const val AUTO: UInt = 3u
    // Add other modes as needed
}

class MavlinkTelemetryRepository(
    private val host: String,
    private val port: Int
) {
    private val gcsSystemId: UByte = 255u
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

        // Collector to log COMMAND_ACK messages for diagnostics
        scope.launch {
            mavFrameStream
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
        // HEARTBEAT for mode, armed, armable
        scope.launch {
            mavFrameStream
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
                    _state.update { it.copy(voltage = vBatt, batteryPercent = pct, armable = armable) }
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
            // Send MissionCount
            val missionCount = MissionCount(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                count = missionItems.size.toUShort()
            )
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
            Log.i("MavlinkRepo", "Sent MISSION_COUNT=${missionItems.size}")

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
                                Log.d("MavlinkRepo", "Received MissionRequestInt from sys=$senderSys comp=$senderComp seq=${msg.seq}")
                                firstRequestReceived = true
                                val seq = msg.seq.toInt()
                                if (seq < 0 || seq >= missionItems.size) {
                                    Log.w("MavlinkRepo", "FC requested invalid seq=$seq (MissionRequestInt)")
                                    return@collect
                                }
                                val item = missionItems[seq]
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
                                Log.d("MavlinkRepo", "Received MissionRequest (float) from sys=$senderSys comp=$senderComp seq=${msg.seq}")
                                firstRequestReceived = true
                                val seq = msg.seq.toInt()
                                if (seq < 0 || seq >= missionItems.size) {
                                    Log.w("MavlinkRepo", "FC requested invalid seq=$seq (MissionRequest)")
                                    return@collect
                                }
                                val itemInt = missionItems[seq]
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
                                Log.i("MavlinkRepo", "Received MISSION_ACK from sys=$senderSys comp=$senderComp: ${msg.type}")
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
                Log.w("MavlinkRepo", "No MissionRequest received within $firstRequestTimeout ms; falling back to send all items")
                for (seq in 0 until missionItems.size) {
                    if (sentSeqs.contains(seq)) continue
                    val item = missionItems[seq]
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
                Log.i("MavlinkRepo", "Mission upload acknowledged by FCU")
                return true
            } else {
                Log.e("MavlinkRepo", "Mission upload timed out waiting for ACK")
                return false
            }
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Mission upload failed", e)
            _lastFailure.value = e
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
     * Sets the current waypoint, sends MISSION_START, and falls back to mode switch if needed.
     */
    suspend fun startMission(first: Int, last: Int): Boolean {
        if (!state.value.fcuDetected) {
            Log.w("MavlinkRepo", "Cannot start mission - FCU not detected")
            return false
        }

        // Set starting waypoint
        try {
            val setCurrent = MissionSetCurrent(targetSystem = fcuSystemId, targetComponent = fcuComponentId, seq = first.toUShort())
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, setCurrent)
            Log.i("MavlinkRepo", "Sent MISSION_SET_CURRENT seq=$first")
            delay(200)
        } catch (e: Exception) {
            Log.w("MavlinkRepo", "Failed to send MISSION_SET_CURRENT", e)
        }

        // Try MISSION_START up to 3 times
        val maxAttempts = 3
        val perAttemptTimeout = 1500L
        for (attempt in 1..maxAttempts) {
            try {
                Log.i("MavlinkRepo", "Attempt $attempt: Sending MISSION_START first=$first last=$last")
                sendCommand(MavCmd.MISSION_START, first.toFloat(), last.toFloat())
            } catch (e: Exception) {
                Log.e("MavlinkRepo", "Failed to send MISSION_START on attempt $attempt", e)
            }

            // Wait for COMMAND_ACK
            val ackDeferred = CompletableDeferred<UInt?>()
            val job = AppScope.launch {
                connection.mavFrame.collect { frame ->
                    val msg = frame.message
                    if (msg is CommandAck && msg.command == MavCmd.MISSION_START.wrap()) {
                        if (!ackDeferred.isCompleted) ackDeferred.complete(msg.result.value)
                    }
                }
            }
            val result = withTimeoutOrNull(perAttemptTimeout) { ackDeferred.await() }
            job.cancel()

            if (result == 0u) { // MAV_RESULT_ACCEPTED
                Log.i("MavlinkRepo", "Mission start acknowledged by FCU")
                return true
            } else if (result != null) {
                Log.e("MavlinkRepo", "Mission start rejected by FCU with result=$result")
                return false
            } else {
                Log.w("MavlinkRepo", "No COMMAND_ACK for MISSION_START on attempt $attempt")
                delay(500)
            }
        }

        // Fallback: set current + change mode to AUTO
        Log.e("MavlinkRepo", "MISSION_START not acknowledged, attempting fallback: set current + mode AUTO")
        try {
            val setCurrent = MissionSetCurrent(targetSystem = fcuSystemId, targetComponent = fcuComponentId, seq = first.toUShort())
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, setCurrent)
            Log.i("MavlinkRepo", "Fallback: Sent MISSION_SET_CURRENT seq=$first")
        } catch (e: Exception) {
            Log.w("MavlinkRepo", "Fallback: Failed to send MISSION_SET_CURRENT", e)
        }
        val modeChanged = changeMode(3u) // 3u = AUTO
        if (modeChanged) {
            Log.i("MavlinkRepo", "Fallback: Vehicle switched to AUTO mode")
            delay(500)
            return true
        }
        Log.e("MavlinkRepo", "Fallback failed: vehicle did not switch to AUTO mode")
        return false
    }
}