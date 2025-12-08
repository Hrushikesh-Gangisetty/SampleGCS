package com.example.aerogcsclone.telemetry

import android.util.Log
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.adapters.coroutines.tryConnect
import com.divpundir.mavlink.adapters.coroutines.trySendUnsignedV2
import com.divpundir.mavlink.api.MavEnumValue
import com.divpundir.mavlink.api.wrap
import com.divpundir.mavlink.connection.StreamState
import com.divpundir.mavlink.connection.tcp.TcpClientMavConnection
import com.divpundir.mavlink.definitions.common.*
import com.divpundir.mavlink.definitions.minimal.*
import com.divpundir.mavlink.definitions.ardupilotmega.MagCalProgress
import com.divpundir.mavlink.definitions.common.MagCalReport
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
import java.util.concurrent.atomic.AtomicLong

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

    // Track if disconnection was intentional (user-initiated)
    private var intentionalDisconnect = false

    // Diagnostic info
    private val _lastFailure = MutableStateFlow<Throwable?>(null)
    val lastFailure: StateFlow<Throwable?> = _lastFailure.asStateFlow()

    // Connection
    val connection = provider.createConnection()
    lateinit var mavFrame: Flow<MavFrame<out MavMessage<*>>>
        private set

    // Track last heartbeat time from FCU (thread-safe using AtomicLong)
    private val lastFcuHeartbeatTime = AtomicLong(0L)
    private val HEARTBEAT_TIMEOUT_MS = 5000L // Increased to 5 seconds for Bluetooth/real hardware reliability

    // For total distance tracking
    private val positionHistory = mutableListOf<Pair<Double, Double>>()
    private var totalDistanceMeters: Float = 0f
    private var lastMissionRunning = false
    private var flightStartTime: Long = 0L  // Track when flight actually started
    private var isFlightActive = false  // Track if flight is in progress

    // Manual mission tracking (for non-AUTO mode flights)
    private var manualFlightActive = false
    private var manualFlightStartTime: Long = 0L
    private var manualFlightDistance: Float = 0f
    private val manualPositionHistory = mutableListOf<Pair<Double, Double>>()
    private var groundLevelAltitude: Float = 0f  // Store the starting ground level altitude
    private var previousArmedState = false  // Track previous armed state to detect transitions
    private var hasShownMissionStarted = false  // Prevent duplicate "Mission started" notifications
    private var isMissionUploadInProgress = false  // Track if mission upload is actively in progress (not just clearing)

    // COMMAND_ACK flow for calibration and other commands
    private val _commandAck = MutableSharedFlow<CommandAck>(replay = 0, extraBufferCapacity = 10)
    val commandAck: SharedFlow<CommandAck> = _commandAck.asSharedFlow()

    // COMMAND_LONG flow for incoming commands from FC (e.g., ACCELCAL_VEHICLE_POS)
    private val _commandLong = MutableSharedFlow<CommandLong>(replay = 0, extraBufferCapacity = 10)
    val commandLong: SharedFlow<CommandLong> = _commandLong.asSharedFlow()

    // MAG_CAL_PROGRESS flow for compass calibration progress
    private val _magCalProgress = MutableSharedFlow<MagCalProgress>(replay = 0, extraBufferCapacity = 10)
    val magCalProgress: SharedFlow<MagCalProgress> = _magCalProgress.asSharedFlow()

    // MAG_CAL_REPORT flow for compass calibration final report
    private val _magCalReport = MutableSharedFlow<MagCalReport>(replay = 0, extraBufferCapacity = 10)
    val magCalReport: SharedFlow<MagCalReport> = _magCalReport.asSharedFlow()

    // RC_CHANNELS flow for radio control calibration
    private val _rcChannels = MutableSharedFlow<RcChannels>(replay = 0, extraBufferCapacity = 10)
    val rcChannels: SharedFlow<RcChannels> = _rcChannels.asSharedFlow()

    // PARAM_VALUE flow for parameter reading
    private val _paramValue = MutableSharedFlow<ParamValue>(replay = 0, extraBufferCapacity = 10)
    val paramValue: SharedFlow<ParamValue> = _paramValue.asSharedFlow()

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
                        // Don't set connected=true here anymore
                        // Connection will be marked as true only when FCU heartbeat is received
                        Log.i("MavlinkRepo", "Stream Active - waiting for FCU heartbeat")
                        // Reset intentional disconnect flag when connection becomes active
                        intentionalDisconnect = false
                    }
                    is StreamState.Inactive -> {
                        Log.i("MavlinkRepo", "Stream Inactive")
                        _state.update { it.copy(connected = false, fcuDetected = false) }
                        lastFcuHeartbeatTime.set(0L)
                        // Only reconnect if disconnection was NOT intentional
                        if (!intentionalDisconnect) {
                            Log.i("MavlinkRepo", "Accidental disconnect detected, reconnecting...")
                            reconnect(this)
                        } else {
                            Log.i("MavlinkRepo", "Intentional disconnect - not reconnecting")
                        }
                    }
                }
            }
        }

        // Monitor FCU heartbeat timeout
        scope.launch {
            while (isActive) {
                delay(1000) // Check every second
                if (state.value.fcuDetected && lastFcuHeartbeatTime.get() > 0L) {
                    val timeSinceLastHeartbeat = System.currentTimeMillis() - lastFcuHeartbeatTime.get()
                    if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        if (state.value.connected) {
                            Log.w("MavlinkRepo", "FCU heartbeat timeout - marking as disconnected")
                            _state.update { it.copy(connected = false, fcuDetected = false) }
                            lastFcuHeartbeatTime.set(0L)
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
                // Send heartbeat even if not fully connected (to allow FCU detection)
                try {
                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, heartbeat)
                } catch (e: Exception) {
                    Log.e("MavlinkRepo", "Failed to send heartbeat", e)
                    _lastFailure.value = e
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

        // Detect FCU and set connected state based on FCU heartbeat
        scope.launch {
            mavFrame
                .filter { it.message is Heartbeat && (it.message as Heartbeat).type != MavType.GCS.wrap() }
                .collect {
                    // Update heartbeat timestamp
                    lastFcuHeartbeatTime.set(System.currentTimeMillis())

                    if (!state.value.fcuDetected) {
                        fcuSystemId = it.systemId
                        fcuComponentId = it.componentId
                        Log.i("MavlinkRepo", "FCU detected sysId=$fcuSystemId compId=$fcuComponentId")
                        _state.update { state -> state.copy(fcuDetected = true, connected = true) }

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
                    } else if (!state.value.connected) {
                        // FCU was detected before but connection was lost, now it's back
                        Log.i("MavlinkRepo", "FCU heartbeat resumed - marking as connected")
                        _state.update { state -> state.copy(connected = true) }
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
                        // Emit to the shared flow for ViewModels to consume
                        _commandAck.emit(ack)
                    } catch (t: Throwable) {
                        Log.i("MavlinkRepo", "COMMAND_ACK received (unable to stringify fields)")
                    }
                }
        }

        // Collector for incoming COMMAND_LONG messages from FC (e.g., for IMU calibration)
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<CommandLong>()
                .collect { cmd ->
                    try {
                        Log.i(
                            "MavlinkRepo",
                            "COMMAND_LONG received: command=${cmd.command.value} param1=${cmd.param1}"
                        )
                        // Emit to the shared flow for ViewModels to consume
                        _commandLong.emit(cmd)
                    } catch (t: Throwable) {
                        Log.i("MavlinkRepo", "COMMAND_LONG received (unable to stringify fields)")
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
                    val currentSpeed = state.value.groundspeed ?: 0f

                    val currentArmed = state.value.armed

                    // Announce armed/disarmed state transitions via TTS
                    if (currentArmed && !previousArmedState) {
                        // Drone just armed - announce it
                        Log.i("MavlinkRepo", "[TTS] Drone armed - announcing via TTS")
                        sharedViewModel.announceDroneArmed()
                    } else if (!currentArmed && previousArmedState) {
                        // Drone just disarmed - announce it
                        Log.i("MavlinkRepo", "[TTS] Drone disarmed - announcing via TTS")
                        sharedViewModel.announceDroneDisarmed()
                    }

                    // Manual mission tracking logic
                    // Start conditions: Drone transitions from disarmed to armed AND altitude > 1m
                    if (currentArmed && !previousArmedState) {
                        // Drone just armed - capture ground level altitude
                        groundLevelAltitude = relAltM
                        Log.i("MavlinkRepo", "[Manual Mission] Drone armed - ground level altitude: ${groundLevelAltitude}m")
                    }

                    // Check if flight should start (armed AND altitude > ground + 1m threshold)
                    val takeoffThreshold = groundLevelAltitude + 1f
                    if (currentArmed && !manualFlightActive && relAltM > takeoffThreshold && !hasShownMissionStarted) {
                        // Manual flight started
                        Log.i("MavlinkRepo", "[Manual Mission] ✅ Flight started - Armed and altitude ${relAltM}m > threshold ${takeoffThreshold}m")
                        manualPositionHistory.clear()
                        manualFlightDistance = 0f
                        manualFlightStartTime = System.currentTimeMillis()
                        manualFlightActive = true
                        hasShownMissionStarted = true

                        // Show "Mission started" notification
                        sharedViewModel.addNotification(
                            Notification(
                                "Mission started",
                                NotificationType.SUCCESS
                            )
                        )
                    }

                    // Track distance during active manual flight
                    if (manualFlightActive && lat != null && lon != null) {
                        if (manualPositionHistory.isNotEmpty()) {
                            val (prevLat, prevLon) = manualPositionHistory.last()
                            val dist = haversine(prevLat, prevLon, lat, lon)
                            manualFlightDistance += dist
                        }
                        manualPositionHistory.add(lat to lon)
                    }

                    // Stop conditions: altitude near ground level OR speed = 0 OR disarmed
                    val landingAltitudeThreshold = groundLevelAltitude + 0.5f
                    val hasLanded = relAltM <= landingAltitudeThreshold
                    val hasStoppedMoving = currentSpeed < 0.1f

                    if (manualFlightActive && (hasLanded || !currentArmed || hasStoppedMoving)) {
                        val reason = when {
                            !currentArmed -> "Disarmed"
                            hasLanded -> "Landed (altitude: ${relAltM}m ≤ ${landingAltitudeThreshold}m)"
                            hasStoppedMoving -> "Stopped (speed: ${currentSpeed}m/s)"
                            else -> "Unknown"
                        }

                        Log.i("MavlinkRepo", "[Manual Mission] ✅ Flight ended - Reason: $reason")
                        val flightDuration = (System.currentTimeMillis() - manualFlightStartTime) / 1000L
                        manualFlightActive = false
                        hasShownMissionStarted = false

                        // Store final values and mark mission as completed
                        _state.update {
                            it.copy(
                                altitudeMsl = altAMSLm,
                                altitudeRelative = relAltM,
                                latitude = lat,
                                longitude = lon,
                                totalDistanceMeters = manualFlightDistance,
                                missionElapsedSec = null,
                                missionCompleted = true,
                                lastMissionElapsedSec = flightDuration
                            )
                        }

                        // Show completion notification
                        sharedViewModel.addNotification(
                            Notification(
                                "Flight completed! Time: ${formatTime(flightDuration)}, Distance: ${formatDistance(manualFlightDistance)}",
                                NotificationType.SUCCESS
                            )
                        )

                        // Reset tracking variables
                        manualPositionHistory.clear()
                        manualFlightDistance = 0f
                        groundLevelAltitude = 0f
                    }

                    // Update state with current values
                    _state.update {
                        it.copy(
                            altitudeMsl = altAMSLm,
                            altitudeRelative = relAltM,
                            latitude = lat,
                            longitude = lon,
                            totalDistanceMeters = if (manualFlightActive) manualFlightDistance else null,
                            missionElapsedSec = if (manualFlightActive) (System.currentTimeMillis() - manualFlightStartTime) / 1000L else null
                        )
                    }

                    // Update previous armed state for next iteration
                    previousArmedState = currentArmed
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
                .filter { frame ->
                    state.value.fcuDetected &&
                    frame.systemId == fcuSystemId &&
                    frame.componentId == fcuComponentId  // Only process heartbeats from the main FCU component
                }
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

                    // Only update state if mode or armed status actually changed
                    if (mode != state.value.mode || armed != state.value.armed) {
                        _state.update { it.copy(armed = armed, mode = mode) }
                        Log.d("MavlinkRepo", "Mode updated to: $mode, Armed: $armed")
                    }

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

        // MISSION_CURRENT for mission progress and waypoint tracking
        var lastMissionSeq = -1
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<MissionCurrent>()
                .collect { missionCurrent ->
                    val currentSeq = missionCurrent.seq.toInt()
                    
                    // Update current waypoint in state
                    _state.update { it.copy(currentWaypoint = currentSeq) }
                    Log.d("MavlinkRepo", "Mission progress: waypoint $currentSeq")
                    
                    // Update SharedViewModel
                    sharedViewModel.updateCurrentWaypoint(currentSeq)
                    
                    if (currentSeq != lastMissionSeq) {
                        lastMissionSeq = currentSeq
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
                    // CRITICAL: Ignore ACKs during mission upload process
                    // The uploadMissionWithAck function handles its own ACKs internally
                    if (isMissionUploadInProgress) {
                        Log.d("MissionUpload", "Global listener: Ignoring ACK during upload (handled by upload function)")
                        return@collect
                    }

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

        // MAG_CAL_PROGRESS for compass calibration progress
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<MagCalProgress>()
                .collect { progress ->
                    Log.d("CompassCalVM", "📨 MAG_CAL_PROGRESS received:")
                    Log.d("CompassCalVM", "   └─ Compass ID: ${progress.compassId}")
                    Log.d("CompassCalVM", "   └─ Status: ${progress.calStatus.entry?.name ?: "UNKNOWN"}")
                    Log.d("CompassCalVM", "   └─ Completion: ${progress.completionPct}%")
                    Log.d("CompassCalVM", "   └─ Attempt: ${progress.attempt}")
                    Log.d("CompassCalVM", "   └─ Direction: X=${progress.directionX}, Y=${progress.directionY}, Z=${progress.directionZ}")
                    _magCalProgress.emit(progress)
                }
        }

        // MAG_CAL_REPORT for compass calibration final report
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<MagCalReport>()
                .collect { report ->
                    Log.d("CompassCalVM", "📊 MAG_CAL_REPORT received:")
                    Log.d("CompassCalVM", "   └─ Compass ID: ${report.compassId}")
                    Log.d("CompassCalVM", "   └─ Status: ${report.calStatus.entry?.name ?: "UNKNOWN"}")
                    Log.d("CompassCalVM", "   └─ Fitness: ${report.fitness}")
                    Log.d("CompassCalVM", "   └─ Offsets: X=${report.ofsX}, Y=${report.ofsY}, Z=${report.ofsZ}")
                    Log.d("CompassCalVM", "   └─ Autosaved: ${report.autosaved}")
                    _magCalReport.emit(report)
                }
        }

        // RC_CHANNELS for radio control calibration
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<RcChannels>()
                .collect { rcChannels ->
                    Log.d("RCCalVM", "📻 RC_CHANNELS received: ch1=${rcChannels.chan1Raw} ch2=${rcChannels.chan2Raw} ch3=${rcChannels.chan3Raw} ch4=${rcChannels.chan4Raw}")
                    _rcChannels.emit(rcChannels)
                }
        }

        // PARAM_VALUE for parameter reading
        scope.launch {
            mavFrame
                .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
                .map { it.message }
                .filterIsInstance<ParamValue>()
                .collect { paramValue ->
                    Log.d("ParamVM", "📝 PARAM_VALUE received: ${paramValue.paramId} = ${paramValue.paramValue}")
                    _paramValue.emit(paramValue)
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

    /**
     * Send a raw command using command ID (for ArduPilot-specific commands not in standard MAVLink).
     */
    suspend fun sendCommandRaw(commandId: UInt, param1: Float = 0f, param2: Float = 0f, param3: Float = 0f, param4: Float = 0f, param5: Float = 0f, param6: Float = 0f, param7: Float = 0f) {
        val commandLong = CommandLong(
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId,
            command = MavEnumValue.fromValue(commandId),
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
            Log.d("MavlinkRepo", "Sent COMMAND_LONG (raw): cmdId=$commandId p1=$param1 p2=$param2 p3=$param3 p4=$param4 p5=$param5 p6=$param6 p7=$param7")
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Failed to send raw command", e)
        }
    }

    /**
     * Send COMMAND_ACK message to autopilot.
     * This is used in ArduPilot's conversational calibration protocol where the GCS
     * sends ACK messages back to the autopilot to confirm user actions.
     */
    suspend fun sendCommandAck(
        commandId: UInt,
        result: MavResult,
        progress: UByte = 0u,
        resultParam2: Int = 0
    ) {
        val commandAck = CommandAck(
            command = MavEnumValue.fromValue(commandId),
            result = result.wrap(),
            progress = progress,
            resultParam2 = resultParam2,
            targetSystem = fcuSystemId,
            targetComponent = fcuComponentId
        )
        try {
            connection.trySendUnsignedV2(
                gcsSystemId,
                gcsComponentId,
                commandAck
            )
            Log.d("MavlinkRepo", "Sent COMMAND_ACK: cmd=$commandId result=${result.name} progress=$progress")
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Failed to send COMMAND_ACK", e)
        }
    }

    /**
     * Send pre-arm checks command to validate vehicle is ready to arm
     * Returns true if pre-arm checks pass, false otherwise
     */
    suspend fun sendPrearmChecks(): Boolean {
        Log.i("MavlinkRepo", "[Pre-arm] Sending RUN_PREARM_CHECKS command...")
        try {
            sendCommand(
                MavCmd.RUN_PREARM_CHECKS,
                0f  // param1: not used
            )
            Log.i("MavlinkRepo", "[Pre-arm] Command sent, waiting for status messages...")
            
            // Wait a bit for pre-arm status messages to arrive via STATUSTEXT
            // These will be automatically displayed via the existing STATUSTEXT handler
            delay(2000)
            
            // Check if vehicle became armable after pre-arm checks
            val armable = state.value.armable
            Log.i("MavlinkRepo", "[Pre-arm] Vehicle armable status: $armable")
            return armable
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Pre-arm] Failed to send pre-arm checks", e)
            return false
        }
    }

    /**
     * Arm the vehicle with retry logic and force-arm fallback
     * @param forceArm If true, uses force-arm immediately (param2 = 2989.0f)
     * @return true if armed successfully, false otherwise
     */
    suspend fun arm(forceArm: Boolean = false): Boolean {
        if (!state.value.armable && !forceArm) {
            Log.w("MavlinkRepo", "[Arm] Vehicle not armable, skipping arm command")
            sharedViewModel.addNotification(
                Notification("Vehicle not armable. Check pre-arm status.", NotificationType.ERROR)
            )
            return false
        }

        val maxAttempts = 3
        val retryDelays = listOf(1000L, 2000L, 3000L) // Exponential backoff
        
        for (attempt in 1..maxAttempts) {
            try {
                val param2 = if (forceArm || attempt == maxAttempts) 2989.0f else 0f
                val armType = if (param2 == 2989.0f) "FORCE-ARM" else "ARM"
                
                Log.i("MavlinkRepo", "[Arm] Attempt $attempt/$maxAttempts - Sending $armType command...")
                sendCommand(
                    MavCmd.COMPONENT_ARM_DISARM,
                    1f,      // param1: 1 = arm
                    param2   // param2: 0 = normal, 2989 = force-arm (Mission Planner magic value)
                )
                
                // Wait for arming to complete
                delay(1500)
                
                // Check if vehicle is now armed
                if (state.value.armed) {
                    Log.i("MavlinkRepo", "[Arm] ✅ Vehicle armed successfully on attempt $attempt")
                    sharedViewModel.addNotification(
                        Notification("Vehicle armed successfully", NotificationType.SUCCESS)
                    )
                    return true
                } else {
                    Log.w("MavlinkRepo", "[Arm] ⚠️ Attempt $attempt failed - vehicle not armed")
                    if (attempt < maxAttempts) {
                        Log.i("MavlinkRepo", "[Arm] Retrying in ${retryDelays[attempt-1]}ms...")
                        delay(retryDelays[attempt-1])
                    }
                }
            } catch (e: Exception) {
                Log.e("MavlinkRepo", "[Arm] Exception on attempt $attempt", e)
                if (attempt < maxAttempts) {
                    delay(retryDelays[attempt-1])
                }
            }
        }
        
        Log.e("MavlinkRepo", "[Arm] ❌ Failed to arm vehicle after $maxAttempts attempts")
        sharedViewModel.addNotification(
            Notification("Failed to arm vehicle. Check STATUSTEXT messages for details.", NotificationType.ERROR)
        )
        return false
    }

    suspend fun disarm() {
        sendCommand(
            MavCmd.COMPONENT_ARM_DISARM,
            0f  // 0 = disarm
        )
        Log.i("MavlinkRepo", "Disarm command sent")
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
        // Wait for Heartbeat to confirm mode change - increased timeout for Bluetooth/real hardware
        val timeoutMs = 8000L // Increased from 5s to 8s for real hardware reliability
        val start = System.currentTimeMillis()
        val expectedMode = when (customMode) {
            3u -> "Auto"
            0u -> "Stabilize"
            5u -> "Loiter"
            6u -> "RTL"
            9u -> "Land"
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
    suspend fun uploadMissionWithAck(missionItems: List<MissionItemInt>, timeoutMs: Long = 45000): Boolean {
        // Mark upload as in progress to prevent global listener from showing notifications
        isMissionUploadInProgress = true

        try {
            if (!state.value.fcuDetected) {
                Log.e("MissionUpload", "❌ FCU not detected")
                throw IllegalStateException("FCU not detected")
            }
            if (missionItems.isEmpty()) {
                Log.w("MissionUpload", "⚠️ No items to upload")
                return false
            }

            // Validate sequence numbering
            val sequences = missionItems.map { it.seq.toInt() }.sorted()
            if (sequences != (0 until missionItems.size).toList()) {
                Log.e("MissionUpload", "❌ Invalid sequence - Expected: 0-${missionItems.size-1}, Got: $sequences")
                throw IllegalStateException("Invalid mission sequence")
            }

            // Quick validation of critical mission parameters
            missionItems.forEachIndexed { idx, item ->
                if (item.command.value in listOf(16u, 22u)) { // NAV_WAYPOINT or NAV_TAKEOFF
                    val lat = item.x / 1e7
                    val lon = item.y / 1e7
                    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                        Log.e("MissionUpload", "❌ Invalid coords at seq=$idx: lat=$lat, lon=$lon")
                        throw IllegalArgumentException("Invalid coordinates at waypoint $idx")
                    }
                    if (item.z < 0f || item.z > 10000f) {
                        Log.e("MissionUpload", "❌ Invalid altitude at seq=$idx: ${item.z}m")
                        throw IllegalArgumentException("Invalid altitude at waypoint $idx")
                    }
                }
            }

            Log.i("MissionUpload", "═══════════════════════════════════════")
            Log.i("MissionUpload", "Starting upload: ${missionItems.size} items")
            Log.i("MissionUpload", "FCU: sys=$fcuSystemId comp=$fcuComponentId")
            Log.i("MissionUpload", "GCS: sys=$gcsSystemId comp=$gcsComponentId")
            Log.i("MissionUpload", "═══════════════════════════════════════")

            // Phase 1: Clear existing mission
            Log.i("MissionUpload", "Phase 1/2: Clearing existing mission...")
            val clearAckChannel = MutableSharedFlow<MissionAck>(replay = 0, extraBufferCapacity = 5)

            val clearCollectorJob = AppScope.launch {
                mavFrame
                    .filter { it.systemId == fcuSystemId && it.componentId == fcuComponentId }
                    .map { it.message }
                    .filterIsInstance<MissionAck>()
                    .collect {
                        Log.d("MissionUpload", "Clear phase ACK: ${it.type.entry?.name ?: it.type.value}")
                        clearAckChannel.emit(it)
                    }
            }

            var clearSuccess = false
            for (attempt in 1..2) {
                Log.d("MissionUpload", "MISSION_CLEAR_ALL attempt $attempt/2")
                val clearAll = MissionClearAll(
                    targetSystem = fcuSystemId, 
                    targetComponent = fcuComponentId,
                    missionType = MavEnumValue.of(MavMissionType.MISSION)
                )
                connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, clearAll)

                val ack = withTimeoutOrNull(5000L) {
                    clearAckChannel.first { it.type.value == MavMissionResult.MAV_MISSION_ACCEPTED.value }
                }

                if (ack != null) {
                    clearSuccess = true
                    Log.i("MissionUpload", "✅ Mission cleared on attempt $attempt")
                    break
                } else if (attempt < 2) {
                    Log.w("MissionUpload", "⚠️ Clear timeout, retrying...")
                    delay(1000L)
                }
            }
            
            clearCollectorJob.cancel()

            if (!clearSuccess) {
                Log.e("MissionUpload", "❌ Failed to clear mission after 2 attempts")
                return false
            }

            delay(800L)
            Log.d("MissionUpload", "Clear complete, proceeding to upload...")

            // Phase 2: Upload mission items
            Log.i("MissionUpload", "Phase 2/2: Uploading ${missionItems.size} items...")

            val missionCount = MissionCount(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                count = missionItems.size.toUShort(),
                missionType = MavEnumValue.of(MavMissionType.MISSION)
            )

            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
            Log.i("MissionUpload", "Sent MISSION_COUNT=${missionItems.size}, awaiting MISSION_REQUEST...")

            val finalAckDeferred = CompletableDeferred<Pair<Boolean, String>>()
            val sentSeqs = mutableSetOf<Int>()
            var firstRequestReceived = false
            var lastRequestTime = System.currentTimeMillis()

            // Simplified resend logic - only if no response
            val resendJob = AppScope.launch {
                delay(3000L)
                if (!firstRequestReceived && !finalAckDeferred.isCompleted) {
                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
                    Log.w("MissionUpload", "⚠️ Resent MISSION_COUNT (no response after 3s)")
                }
            }

            // Unified watchdog - simpler timeout logic
            val watchdogJob = AppScope.launch {
                while (isActive && !finalAckDeferred.isCompleted) {
                    delay(2000)
                    if (firstRequestReceived) {
                        val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime
                        if (timeSinceLastRequest > 10000L) {
                            Log.e("MissionUpload", "❌ Upload stalled - no FCU response for 10s (${sentSeqs.size}/${missionItems.size} sent)")
                            finalAckDeferred.complete(false to "Upload stalled - no FCU response")
                            break
                        }
                    }
                }
            }

            // Main message collector
            val collectorJob = AppScope.launch {
                connection.mavFrame.collect { frame ->
                    if (finalAckDeferred.isCompleted ||
                        frame.systemId != fcuSystemId ||
                        frame.componentId != fcuComponentId) {
                        return@collect
                    }

                    when (val msg = frame.message) {
                        is MissionRequestInt, is MissionRequest -> {
                            if (!firstRequestReceived) {
                                Log.i("MissionUpload", "✅ First MISSION_REQUEST received - upload starting")
                            }
                            firstRequestReceived = true
                            lastRequestTime = System.currentTimeMillis()

                            val seq = if (msg is MissionRequestInt) msg.seq.toInt() else (msg as MissionRequest).seq.toInt()

                            if (seq !in 0 until missionItems.size) {
                                Log.e("MissionUpload", "❌ Invalid seq requested: $seq (valid: 0-${missionItems.size-1})")
                                finalAckDeferred.complete(false to "Invalid sequence $seq")
                                return@collect
                            }

                            val item = missionItems[seq].copy(
                                targetSystem = fcuSystemId,
                                targetComponent = fcuComponentId,
                                seq = seq.toUShort()
                            )

                            // Adaptive delay: 50ms for BT/serial
                            if (seq > 0) delay(50L)

                            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, item)
                            sentSeqs.add(seq)

                            // Log progress: first, last, and every 10 items for verification
                            if (seq == 0 || seq == missionItems.size - 1 || seq % 10 == 0) {
                                val cmdName = item.command.entry?.name ?: "CMD_${item.command.value}"
                                Log.i("MissionUpload", "→ Sent seq=$seq: $cmdName (${sentSeqs.size}/${missionItems.size})")
                            }
                        }

                        is MissionAck -> {
                            if (!firstRequestReceived) {
                                Log.d("MissionUpload", "Ignoring ACK from clear phase")
                                return@collect
                            }

                            val ackType = msg.type.entry?.name ?: msg.type.value.toString()
                            Log.i("MissionUpload", "MISSION_ACK received: $ackType (${sentSeqs.size}/${missionItems.size} sent)")

                            when (msg.type.value) {
                                MavMissionResult.MAV_MISSION_ACCEPTED.value -> {
                                    // Verify all items sent before accepting
                                    if (sentSeqs.size == missionItems.size) {
                                        Log.i("MissionUpload", "✅ All items confirmed, accepting upload")
                                        finalAckDeferred.complete(true to "")
                                    } else {
                                        Log.w("MissionUpload", "⚠️ Premature ACCEPTED ACK (${sentSeqs.size}/${missionItems.size}), waiting for more items...")
                                    }
                                }
                                MavMissionResult.MAV_MISSION_INVALID_SEQUENCE.value -> {
                                    Log.e("MissionUpload", "❌ INVALID_SEQUENCE error")
                                    Log.e("MissionUpload", "   Sent sequences: ${sentSeqs.sorted()}")
                                    finalAckDeferred.complete(false to "Invalid sequence error")
                                }
                                MavMissionResult.MAV_MISSION_DENIED.value -> {
                                    Log.e("MissionUpload", "❌ DENIED by FCU")
                                    finalAckDeferred.complete(false to "Mission denied")
                                }
                                MavMissionResult.MAV_MISSION_ERROR.value -> {
                                    Log.e("MissionUpload", "❌ ERROR from FCU")
                                    finalAckDeferred.complete(false to "Mission error")
                                }
                                MavMissionResult.MAV_MISSION_UNSUPPORTED_FRAME.value -> {
                                    Log.e("MissionUpload", "❌ UNSUPPORTED_FRAME")
                                    finalAckDeferred.complete(false to "Unsupported frame type")
                                }
                                MavMissionResult.MAV_MISSION_NO_SPACE.value -> {
                                    Log.e("MissionUpload", "❌ NO_SPACE on FCU")
                                    finalAckDeferred.complete(false to "Not enough space")
                                }
                                in listOf(
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM1.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM2.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM3.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM4.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM5_X.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM6_Y.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM7.value
                                ) -> {
                                    Log.e("MissionUpload", "❌ INVALID_PARAM (error: ${msg.type.value})")
                                    finalAckDeferred.complete(false to "Invalid parameter")
                                }
                                MavMissionResult.MAV_MISSION_OPERATION_CANCELLED.value -> {
                                    Log.w("MissionUpload", "⚠️ CANCELLED by FCU")
                                    finalAckDeferred.complete(false to "Upload cancelled")
                                }
                                else -> {
                                    Log.w("MissionUpload", "⚠️ Unknown ACK type: ${msg.type.value}")
                                    finalAckDeferred.complete(false to "Unknown error")
                                }
                            }
                        }
                    }
                }
            }

            // Wait for first request (simplified timeout)
            var waitTime = 0L
            while (!firstRequestReceived && !finalAckDeferred.isCompleted && waitTime < 10000L) {
                delay(100)
                waitTime += 100
            }

            if (!firstRequestReceived && !finalAckDeferred.isCompleted) {
                Log.e("MissionUpload", "❌ No MISSION_REQUEST from FCU after 10s")
                Log.e("MissionUpload", "   This usually means:")
                Log.e("MissionUpload", "   1. Invalid mission structure (check seq 0 is HOME)")
                Log.e("MissionUpload", "   2. FCU rejected the MISSION_COUNT")
                Log.e("MissionUpload", "   3. Communication issue with FCU")
                finalAckDeferred.complete(false to "No response from FCU")
            }

            // Wait for final result
            val (success, errorMsg) = withTimeoutOrNull(timeoutMs) {
                finalAckDeferred.await()
            } ?: (false to "Upload timeout (${timeoutMs}ms)")

            collectorJob.cancel()
            resendJob.cancel()
            watchdogJob.cancel()

            if (success) {
                Log.i("MissionUpload", "═══════════════════════════════════════")
                Log.i("MissionUpload", "✅ SUCCESS - Mission uploaded!")
                Log.i("MissionUpload", "Total items: ${missionItems.size}")
                Log.i("MissionUpload", "All sequences confirmed: ${sentSeqs.sorted()}")
                Log.i("MissionUpload", "═══════════════════════════════════════")
            } else {
                Log.e("MissionUpload", "═══════════════════════════════════════")
                Log.e("MissionUpload", "❌ FAILED - $errorMsg")
                Log.e("MissionUpload", "Items sent: ${sentSeqs.size}/${missionItems.size}")
                Log.e("MissionUpload", "Sequences sent: ${sentSeqs.sorted()}")
                Log.e("MissionUpload", "═══════════════════════════════════════")
            }

            return success
        } catch (e: Exception) {
            Log.e("MissionUpload", "❌ Upload exception: ${e.message}", e)
            return false
        } finally {
            // Always reset flag when upload completes (success or failure)
            isMissionUploadInProgress = false
            Log.d("MissionUpload", "Upload process complete - re-enabling global ACK listener")
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
            sharedViewModel.addNotification(
                Notification("Cannot start mission - FCU not detected", NotificationType.ERROR)
            )
            return false
        }

        // Step 0: Run pre-arm checks
        try {
            Log.i("MavlinkRepo", "[Mission Start] Running pre-arm checks...")
            sharedViewModel.addNotification(
                Notification("Running pre-arm checks...", NotificationType.INFO)
            )
            val prearmOk = sendPrearmChecks()
            if (!prearmOk) {
                Log.w("MavlinkRepo", "[Mission Start] Pre-arm checks failed")
                sharedViewModel.addNotification(
                    Notification("Pre-arm checks failed. Check STATUSTEXT messages.", NotificationType.ERROR)
                )
                // Continue anyway - the arm() function will handle retries
            } else {
                Log.i("MavlinkRepo", "[Mission Start] Pre-arm checks passed")
                sharedViewModel.addNotification(
                    Notification("Pre-arm checks passed", NotificationType.SUCCESS)
                )
            }
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Start] Failed to run pre-arm checks", e)
            // Continue anyway - the arm() function will handle retries
        }

        // Step 1: Arm the vehicle with retry logic
        try {
            Log.i("MavlinkRepo", "[Mission Start] Arming vehicle...")
            val armed = arm(forceArm = false)
            if (!armed) {
                Log.e("MavlinkRepo", "[Mission Start] Failed to arm vehicle")
                sharedViewModel.addNotification(
                    Notification("Failed to arm vehicle. Check pre-arm status.", NotificationType.ERROR)
                )
                return false
            }
            Log.i("MavlinkRepo", "[Mission Start] Vehicle armed successfully")
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Start] Exception while arming vehicle", e)
            sharedViewModel.addNotification(
                Notification("Exception while arming: ${e.message}", NotificationType.ERROR)
            )
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
                sharedViewModel.addNotification(
                    Notification("Failed to switch to AUTO mode. Check if mission has NAV_TAKEOFF.", NotificationType.ERROR)
                )
                return false
            }
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Start] Failed to set AUTO mode", e)
            return false
        }

        Log.i("MavlinkRepo", "[Mission Start] Mission start workflow complete. Vehicle should be in AUTO mode.")
        sharedViewModel.addNotification(
            Notification("Mission started successfully", NotificationType.SUCCESS)
        )
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
            // Mark this as an intentional disconnect to prevent auto-reconnect
            intentionalDisconnect = true
            Log.i("MavlinkRepo", "User-initiated disconnect - auto-reconnect disabled")
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
//            speed >= 1000f -> String.format("%.3f km/s", speed / 1000f)
            speed >= 1f -> String.format("%.3f m/s", speed)
//            speed >= 0.01f -> String.format("%.1f cm/s", speed * 100f)
//            speed > 0f -> String.format("%.1f mm/s", speed * 1000f)
            else -> "0 m/s"
        }
    }

    // Format time for human-readable display
    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    // Format distance for human-readable display
    private fun formatDistance(meters: Float): String {
        return String.format("%.1f m", meters)
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

    /**
     * Set the current mission waypoint
     * @param seq Waypoint sequence number to resume from
     */
    suspend fun setCurrentWaypoint(seq: Int): Boolean {
        return try {
            val cmd = CommandLong(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                command = MavCmd.DO_SET_MISSION_CURRENT.wrap(),
                confirmation = 0u,
                param1 = seq.toFloat(),
                param2 = 0f,
                param3 = 0f,
                param4 = 0f,
                param5 = 0f,
                param6 = 0f,
                param7 = 0f
            )

            Log.i("MavlinkRepo", "Setting current waypoint to: $seq")
            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, cmd)
            delay(500)

            true
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "Failed to set current waypoint", e)
            false
        }
    }

    /**
     * Filter waypoints for resume mission following Mission Planner protocol.
     * 
     * Mission Planner Logic:
     * - Always keep HOME (waypoint 0)
     * - Keep TAKEOFF commands even before resume point
     * - Keep ALL DO commands (80-99 and 176-252) even before resume point
     * - Skip NAV waypoints before resume point
     * - Keep ALL waypoints from resume point onward
     *
     * @param allWaypoints Complete mission from flight controller
     * @param resumeWaypointSeq The waypoint sequence number to resume from
     * @return Filtered list of waypoints with HOME + DO commands + resume waypoints
     */
    suspend fun filterWaypointsForResume(
        allWaypoints: List<MissionItemInt>, 
        resumeWaypointSeq: Int
    ): List<MissionItemInt> {
        val filtered = mutableListOf<MissionItemInt>()
        
        // MAVLink command ID constants
        val MAV_CMD_NAV_TAKEOFF = 22u
        val MAV_CMD_NAV_LAST = 95u      // Last NAV command (MAV_CMD.LAST in Mission Planner)
        val MAV_CMD_DO_START = 80u       // First DO command
        val MAV_CMD_DO_LAST = 252u       // Last DO command
        
        Log.i("ResumeMission", "═══ Filtering Mission for Resume (Mission Planner Protocol) ═══")
        Log.i("ResumeMission", "Original mission: ${allWaypoints.size} waypoints")
        Log.i("ResumeMission", "Resume from waypoint: $resumeWaypointSeq")
        Log.i("ResumeMission", "Logic: HOME + TAKEOFF + DO commands + resume waypoints")
        
        for (waypoint in allWaypoints) {
            val seq = waypoint.seq.toInt()
            val cmdId = waypoint.command.value
            
            // Always keep HOME (waypoint 0)
            if (seq == 0) {
                filtered.add(waypoint)
                Log.d("ResumeMission", "✅ Keeping HOME (seq=$seq)")
                continue
            }
            
            // For waypoints before resume point
            if (seq < resumeWaypointSeq) {
                // Keep TAKEOFF commands (Mission Planner: if wpdata.id != TAKEOFF)
                if (cmdId == MAV_CMD_NAV_TAKEOFF) {
                    filtered.add(waypoint)
                    Log.d("ResumeMission", "✅ Keeping TAKEOFF (seq=$seq, cmd=$cmdId)")
                    continue
                }
                
                // Keep DO commands (Mission Planner: if wpdata.id >= MAV_CMD.LAST)
                // DO commands have two ranges in MAVLink:
                //   - 80-99: Conditional DO commands (e.g., DO_JUMP, DO_CHANGE_SPEED)
                //   - 176-252: Unconditional DO commands (e.g., DO_SET_SERVO, DO_SET_CAM_TRIGG)
                // Both types must be preserved as they set persistent mission parameters
                val isDoCommand = cmdId in MAV_CMD_DO_START..99u || cmdId in 176u..MAV_CMD_DO_LAST
                if (isDoCommand) {
                    filtered.add(waypoint)
                    Log.d("ResumeMission", "✅ Keeping DO command (seq=$seq, cmd=$cmdId)")
                    continue
                }
                
                // Skip NAV waypoints before resume point
                // Mission Planner C#: if (wpdata.id < MAV_CMD.LAST) continue;
                // MAV_CMD.LAST = 95, so we skip commands with ID <= 95 (NAV commands)
                if (cmdId <= MAV_CMD_NAV_LAST) {
                    Log.d("ResumeMission", "⏭ Skipping NAV waypoint (seq=$seq, cmd=$cmdId)")
                    continue
                }
                
                // Skip any other commands before resume point
                Log.d("ResumeMission", "⏭ Skipping waypoint (seq=$seq, cmd=$cmdId)")
                continue
            }
            
            // Keep ALL waypoints from resume point onward
            filtered.add(waypoint)
            Log.d("ResumeMission", "✅ Keeping waypoint (seq=$seq, cmd=$cmdId)")
        }
        
        Log.i("ResumeMission", "Filtered: ${allWaypoints.size} → ${filtered.size} waypoints")
        Log.i("ResumeMission", "Result: HOME + TAKEOFF + DO commands + resume waypoints")
        Log.i("ResumeMission", "═══════════════════════════════════════════════════════")
        
        return filtered
    }

    /**
     * Re-sequence waypoints to 0, 1, 2, 3...
     * Marks HOME (waypoint 0) as current.
     * 
     * @param waypoints List of waypoints to re-sequence
     * @return Re-sequenced list with sequential numbering
     */
    suspend fun resequenceWaypoints(waypoints: List<MissionItemInt>): List<MissionItemInt> {
        Log.i("ResumeMission", "Re-sequencing ${waypoints.size} waypoints...")
        
        return waypoints.mapIndexed { index, waypoint ->
            waypoint.copy(
                seq = index.toUShort(),
                current = if (index == 0) 1u else 0u // Mark HOME as current
            )
        }.also {
            Log.i("ResumeMission", "Re-sequenced: 0 to ${it.size - 1}")
        }
    }


}
