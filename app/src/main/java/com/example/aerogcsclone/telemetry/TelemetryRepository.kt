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
    private val HEARTBEAT_TIMEOUT_MS = 3000L // 3 seconds timeout

    // For total distance tracking
    private val positionHistory = mutableListOf<Pair<Double, Double>>()
    private var totalDistanceMeters: Float = 0f
    private var lastMissionRunning = false
    private var flightStartTime: Long = 0L  // Track when flight actually started
    private var isFlightActive = false  // Track if flight is in progress

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

                    // NEW LOGIC: Track distance/time based on armed + altitude, not just AUTO mode
                    val currentArmed = state.value.armed
                    val altitudeThreshold = 0.5f  // Consider flight active when altitude > 0.5m
                    val landingThreshold = 0.5f   // Consider landed when altitude < 0.5m

                    // Check if flight should be active (armed AND altitude > threshold)
                    val shouldBeActive = currentArmed && (relAltM ?: 0f) > altitudeThreshold

                    // Start flight tracking
                    if (shouldBeActive && !isFlightActive) {
                        Log.i("MavlinkRepo", "[Flight Tracking] Flight started - Armed and altitude > ${altitudeThreshold}m")
                        positionHistory.clear()
                        totalDistanceMeters = 0f
                        flightStartTime = System.currentTimeMillis()
                        isFlightActive = true
                    }

                    // Track distance during active flight
                    if (isFlightActive && lat != null && lon != null) {
                        if (positionHistory.isNotEmpty()) {
                            val (prevLat, prevLon) = positionHistory.last()
                            val dist = haversine(prevLat, prevLon, lat, lon)
                            totalDistanceMeters += dist
                        }
                        positionHistory.add(lat to lon)
                    }

                    // End flight tracking when altitude returns to near 0
                    if (isFlightActive && (relAltM ?: 0f) <= landingThreshold) {
                        Log.i("MavlinkRepo", "[Flight Tracking] Flight ended - Altitude returned to ~0m")
                        val flightDuration = (System.currentTimeMillis() - flightStartTime) / 1000L
                        isFlightActive = false

                        // Store final values and mark mission as completed
                        _state.update {
                            it.copy(
                                altitudeMsl = altAMSLm,
                                altitudeRelative = relAltM,
                                latitude = lat,
                                longitude = lon,
                                totalDistanceMeters = totalDistanceMeters,
                                missionElapsedSec = null,
                                missionCompleted = true,
                                lastMissionElapsedSec = flightDuration
                            )
                        }

                        // Show completion notification
                        sharedViewModel.addNotification(
                            Notification(
                                "Flight completed! Time: ${formatTime(flightDuration)}, Distance: ${formatDistance(totalDistanceMeters)}",
                                NotificationType.SUCCESS
                            )
                        )
                        return@collect
                    }

                    // Update state with current values
                    _state.update {
                        it.copy(
                            altitudeMsl = altAMSLm,
                            altitudeRelative = relAltM,
                            latitude = lat,
                            longitude = lon,
                            totalDistanceMeters = if (isFlightActive) totalDistanceMeters else null,
                            missionElapsedSec = if (isFlightActive) (System.currentTimeMillis() - flightStartTime) / 1000L else null
                        )
                    }

                    lastMissionRunning = shouldBeActive
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
    suspend fun uploadMissionWithAck(missionItems: List<MissionItemInt>, timeoutMs: Long = 45000): Boolean {
        if (!state.value.fcuDetected) {
            Log.e("MavlinkRepo", "FCU not detected, cannot upload mission")
            throw IllegalStateException("FCU not detected")
        }
        if (missionItems.isEmpty()) {
            Log.w("MavlinkRepo", "No mission items to upload")
            return false
        }

        // Validate sequence numbering
        val sequences = missionItems.map { it.seq.toInt() }.sorted()
        val expectedSequences = (0 until missionItems.size).toList()
        if (sequences != expectedSequences) {
            Log.e("MavlinkRepo", "[Mission Upload] Invalid sequence numbering detected!")
            Log.e("MavlinkRepo", "Expected: $expectedSequences")
            Log.e("MavlinkRepo", "Got: $sequences")
            throw IllegalStateException("Invalid mission sequence: gaps or duplicates detected")
        }

        // NEW: Validate mission parameters to catch errors before upload
        missionItems.forEachIndexed { idx, item ->
            // Validate latitude/longitude for waypoint commands
            if (item.command.value == 16u || item.command.value == 22u) { // NAV_WAYPOINT or NAV_TAKEOFF
                val lat = item.x / 1e7
                val lon = item.y / 1e7
                if (lat < -90.0 || lat > 90.0) {
                    Log.e("MavlinkRepo", "[Mission Upload] Invalid latitude at seq=$idx: $lat")
                    throw IllegalArgumentException("Invalid latitude at waypoint $idx: $lat (must be -90 to 90)")
                }
                if (lon < -180.0 || lon > 180.0) {
                    Log.e("MavlinkRepo", "[Mission Upload] Invalid longitude at seq=$idx: $lon")
                    throw IllegalArgumentException("Invalid longitude at waypoint $idx: $lon (must be -180 to 180)")
                }
                if (lat == 0.0 && lon == 0.0 && item.command.value == 16u) {
                    Log.w("MavlinkRepo", "[Mission Upload] Warning: Waypoint at seq=$idx has lat=0, lon=0 (null island)")
                }
            }

            // Validate altitude for waypoint commands
            if (item.command.value == 16u || item.command.value == 22u) {
                if (item.z < 0f) {
                    Log.e("MavlinkRepo", "[Mission Upload] Invalid altitude at seq=$idx: ${item.z} (negative altitude)")
                    throw IllegalArgumentException("Invalid altitude at waypoint $idx: ${item.z} (cannot be negative)")
                }
                if (item.z > 10000f) {
                    Log.w("MavlinkRepo", "[Mission Upload] Warning: Very high altitude at seq=$idx: ${item.z}m (>10km)")
                }
            }
        }

        // Validate target IDs are set
        val invalidItems = missionItems.filter { it.targetSystem == 0u.toUByte() || it.targetComponent == 0u.toUByte() }
        if (invalidItems.isNotEmpty()) {
            Log.w("MavlinkRepo", "[Mission Upload] Warning: ${invalidItems.size} mission items have targetSystem/Component set to 0")
            Log.w("MavlinkRepo", "[Mission Upload] These will be corrected to FCU IDs: sys=$fcuSystemId comp=$fcuComponentId")
        }

        // Log mission structure for debugging
        Log.i("MavlinkRepo", "[Mission Upload] ═══════════════════════════════════════")
        Log.i("MavlinkRepo", "[Mission Upload] FCU IDs: sys=$fcuSystemId comp=$fcuComponentId")
        Log.i("MavlinkRepo", "[Mission Upload] GCS IDs: sys=$gcsSystemId comp=$gcsComponentId")
        Log.i("MavlinkRepo", "[Mission Upload] Mission structure (${missionItems.size} items):")
        missionItems.forEachIndexed { idx, item ->
            val cmdName = item.command.entry?.name ?: "CMD_${item.command.value}"
            val frameName = item.frame.entry?.name ?: "FRAME_${item.frame.value}"
            Log.i("MavlinkRepo", "  [$idx] seq=${item.seq} cmd=$cmdName frame=$frameName current=${item.current} " +
                    "target=${item.targetSystem}/${item.targetComponent} " +
                    "pos=${item.x / 1e7},${item.y / 1e7},${item.z}m " +
                    "autocont=${item.autocontinue}")
        }
        Log.i("MavlinkRepo", "[Mission Upload] ═══════════════════════════════════════")

        try {
            // Step 0: Clear previous mission with retry logic
            Log.i("MavlinkRepo", "[Mission Upload] Phase 1: Clearing existing mission...")
            var clearAck = false
            val maxClearAttempts = 3

            // CRITICAL FIX: Use a channel to collect MISSION_ACK during clear phase
            val clearAckChannel = MutableSharedFlow<MissionAck>(replay = 0, extraBufferCapacity = 10)

            // Start collector job BEFORE sending MISSION_CLEAR_ALL
            val clearCollectorJob = AppScope.launch {
                mavFrame
                    .filter { frame ->
                        frame.systemId == fcuSystemId && frame.componentId == fcuComponentId
                    }
                    .map { it.message }
                    .filterIsInstance<MissionAck>()
                    .collect { ack ->
                        Log.i("MavlinkRepo", "[Mission Upload CLEAR] MISSION_ACK intercepted: type=${ack.type.entry?.name ?: ack.type.value}")
                        clearAckChannel.emit(ack)
                    }
            }

            for (attempt in 1..maxClearAttempts) {
                Log.i("MavlinkRepo", "[Mission Upload] MISSION_CLEAR_ALL attempt $attempt/$maxClearAttempts")
                val clearAll = MissionClearAll(
                    targetSystem = fcuSystemId, 
                    targetComponent = fcuComponentId,
                    missionType = MavEnumValue.of(MavMissionType.MISSION)
                )
                connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, clearAll)
                Log.i("MavlinkRepo", "[Mission Upload] Sent MISSION_CLEAR_ALL, waiting for ACK...")

                // Wait for MISSION_ACK from the channel
                val ackReceived = withTimeoutOrNull(7000L) {
                    clearAckChannel.first { ack ->
                        Log.i("MavlinkRepo", "[Mission Upload] Processing MISSION_ACK: type=${ack.type.entry?.name ?: ack.type.value}")
                        if (ack.type.value == MavMissionResult.MAV_MISSION_ACCEPTED.value) {
                            Log.i("MavlinkRepo", "[Mission Upload] ✅ MISSION_CLEAR_ALL acknowledged by FCU")
                            true
                        } else {
                            Log.e("MavlinkRepo", "[Mission Upload] ❌ MISSION_CLEAR_ALL rejected: ${ack.type.entry?.name ?: ack.type.value}")
                            false
                        }
                    }
                }

                if (ackReceived != null && ackReceived.type.value == MavMissionResult.MAV_MISSION_ACCEPTED.value) {
                    clearAck = true
                    Log.i("MavlinkRepo", "[Mission Upload] ✅ MISSION_CLEAR_ALL successful on attempt $attempt")
                    break
                } else if (attempt < maxClearAttempts) {
                    Log.w("MavlinkRepo", "[Mission Upload] ⚠️ No ACK for MISSION_CLEAR_ALL on attempt $attempt, retrying...")
                    delay(1500L)
                }
            }
            
            // Cancel the collector job
            clearCollectorJob.cancel()

            if (!clearAck) {
                Log.e("MavlinkRepo", "[Mission Upload] ❌ MISSION_CLEAR_ALL failed after $maxClearAttempts attempts")
                return false
            }

            // CRITICAL: Give FCU MORE time to process the clear command (real hardware needs this)
            delay(1000L)  // Increased from 500ms to 1000ms

            Log.i("MavlinkRepo", "[Mission Upload] Phase 2: Uploading ${missionItems.size} mission items...")
            Log.i("MavlinkRepo", "[Mission Upload] Preparing MISSION_COUNT message...")
            Log.i("MavlinkRepo", "[Mission Upload]   - Target: sys=$fcuSystemId comp=$fcuComponentId")
            Log.i("MavlinkRepo", "[Mission Upload]   - Count: ${missionItems.size}")
            Log.i("MavlinkRepo", "[Mission Upload]   - Sender: sys=$gcsSystemId comp=$gcsComponentId")

            // Send MissionCount
            val missionCount = MissionCount(
                targetSystem = fcuSystemId,
                targetComponent = fcuComponentId,
                count = missionItems.size.toUShort(),
                missionType = MavEnumValue.of(MavMissionType.MISSION)
            )

            try {
                connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
                Log.i("MavlinkRepo", "[Mission Upload] ✅ Sent MISSION_COUNT=${missionItems.size}")
                Log.i("MavlinkRepo", "[Mission Upload] Waiting for FCU to send MISSION_REQUEST messages...")
            } catch (e: Exception) {
                Log.e("MavlinkRepo", "[Mission Upload] ❌ Failed to send MISSION_COUNT", e)
                return false
            }

            val finalAckDeferred = CompletableDeferred<Pair<Boolean, String>>()
            val sentSeqs = mutableSetOf<Int>()
            var firstRequestReceived = false
            var uploadCancelled = false
            var lastRequestedSeq = -1
            var lastRequestTime = System.currentTimeMillis()

            // NEW: Track request count per sequence to detect duplicates
            val requestCountPerSeq = mutableMapOf<Int, Int>()

            // IMPROVED: More conservative MISSION_COUNT resend
            val resendJob = AppScope.launch {
                var resendCount = 0
                val maxResendAttempts = 3 // Reduced from 8
                val resendInterval = 2500L // Increased from 1500ms

                while (isActive && !firstRequestReceived && !finalAckDeferred.isCompleted && resendCount < maxResendAttempts) {
                    delay(resendInterval)
                    if (!firstRequestReceived && !finalAckDeferred.isCompleted) {
                        try {
                            connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionCount)
                            resendCount++
                            Log.w("MavlinkRepo", "[Mission Upload] ⚠️ FCU not responding - Resent MISSION_COUNT (attempt ${resendCount + 1}/${maxResendAttempts + 1})")
                        } catch (e: Exception) {
                            Log.e("MavlinkRepo", "[Mission Upload] Failed to resend MISSION_COUNT", e)
                        }
                    }
                }

                if (resendCount >= maxResendAttempts && !firstRequestReceived) {
                    Log.e("MavlinkRepo", "[Mission Upload] ❌ FCU never responded to MISSION_COUNT after ${maxResendAttempts + 1} attempts")
                    Log.e("MavlinkRepo", "[Mission Upload] ❌ This usually means:")
                    Log.e("MavlinkRepo", "[Mission Upload]    1. FCU rejected the mission structure")
                    Log.e("MavlinkRepo", "[Mission Upload]    2. Mission has invalid items (check seq 0 is HOME with z=0)")
                    Log.e("MavlinkRepo", "[Mission Upload]    3. Communication issue with FCU")
                }
            }

            // NEW: Watchdog to detect stalled uploads
            val watchdogJob = AppScope.launch {
                val itemRequestTimeout = 8000L // 8 seconds per item
                while (isActive && !finalAckDeferred.isCompleted) {
                    delay(1000)

                    if (firstRequestReceived && !finalAckDeferred.isCompleted) {
                        val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime

                        if (timeSinceLastRequest > itemRequestTimeout) {
                            Log.e("MavlinkRepo", "[Mission Upload] ❌ Upload stalled - no request from FCU for ${timeSinceLastRequest}ms")
                            Log.e("MavlinkRepo", "[Mission Upload] Last requested seq=$lastRequestedSeq, sent ${sentSeqs.size}/${missionItems.size} items")
                            finalAckDeferred.complete(false to "Upload stalled - FCU stopped requesting items after $lastRequestedSeq")
                            break
                        }
                    }
                }
            }

            // Collector job
            val job = AppScope.launch {
                connection.mavFrame
                    .collect { frame ->
                        if (uploadCancelled || finalAckDeferred.isCompleted) return@collect

                        val senderSys = frame.systemId
                        val senderComp = frame.componentId

                        // Only process messages from the target FCU
                        if (senderSys != fcuSystemId || senderComp != fcuComponentId) {
                            return@collect
                        }

                        when (val msg = frame.message) {
                            is MissionRequestInt -> {
                                firstRequestReceived = true
                                lastRequestTime = System.currentTimeMillis()
                                val seq = msg.seq.toInt()

                                // Track duplicate requests
                                val requestCount = requestCountPerSeq.getOrDefault(seq, 0) + 1
                                requestCountPerSeq[seq] = requestCount

                                if (requestCount > 1) {
                                    Log.w("MavlinkRepo", "[Mission Upload] ⚠️ FCU re-requested seq=$seq (request #$requestCount) - possible packet loss")
                                }

                                Log.d("MavlinkRepo", "[Mission Upload] MissionRequestInt seq=${msg.seq} (request #$requestCount)")
                                lastRequestedSeq = seq

                                if (seq < 0 || seq >= missionItems.size) {
                                    Log.e("MavlinkRepo", "[Mission Upload] ❌ FCU requested invalid seq=$seq (valid: 0-${missionItems.size-1})")
                                    finalAckDeferred.complete(false to "FCU requested invalid sequence $seq")
                                    return@collect
                                }

                                val item = missionItems[seq]
                                val cmdName = item.command.entry?.name ?: "CMD_${item.command.value}"
                                Log.i("MavlinkRepo", "[Mission Upload] → Sending seq=$seq: $cmdName lat=${item.x / 1e7} lon=${item.y / 1e7} alt=${item.z}m current=${item.current}")

                                // Ensure target IDs are correct
                                val missionItem = item.copy(
                                    targetSystem = fcuSystemId,
                                    targetComponent = fcuComponentId,
                                    seq = seq.toUShort()
                                )

                                try {
                                    // CRITICAL: Add small delay between items for Bluetooth/real hardware
                                    if (seq > 0) {
                                        delay(50L)  // 50ms delay between items for slower connections
                                    }

                                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionItem)
                                    sentSeqs.add(seq)
                                    Log.i("MavlinkRepo", "[Mission Upload] ✓ Sent seq=$seq (${sentSeqs.size}/${missionItems.size})")
                                } catch (e: Exception) {
                                    Log.e("MavlinkRepo", "[Mission Upload] ❌ Failed to send seq=$seq", e)
                                    finalAckDeferred.complete(false to "Network error sending item $seq: ${e.message}")
                                }
                            }

                            is MissionRequest -> {
                                firstRequestReceived = true
                                lastRequestTime = System.currentTimeMillis()
                                val seq = msg.seq.toInt()

                                // Track duplicate requests
                                val requestCount = requestCountPerSeq.getOrDefault(seq, 0) + 1
                                requestCountPerSeq[seq] = requestCount

                                if (requestCount > 1) {
                                    Log.w("MavlinkRepo", "[Mission Upload] ⚠️ FCU re-requested seq=$seq (request #$requestCount) - possible packet loss")
                                }

                                Log.d("MavlinkRepo", "[Mission Upload] MissionRequest (legacy) seq=${msg.seq} (request #$requestCount)")
                                lastRequestedSeq = seq

                                if (seq < 0 || seq >= missionItems.size) {
                                    Log.e("MavlinkRepo", "[Mission Upload] ❌ FCU requested invalid seq=$seq (valid: 0-${missionItems.size-1})")
                                    finalAckDeferred.complete(false to "FCU requested invalid sequence $seq")
                                    return@collect
                                }

                                val item = missionItems[seq]
                                val cmdName = item.command.entry?.name ?: "CMD_${item.command.value}"
                                Log.i("MavlinkRepo", "[Mission Upload] → Sending seq=$seq: $cmdName lat=${item.x / 1e7} lon=${item.y / 1e7} alt=${item.z}m current=${item.current}")

                                val missionItem = item.copy(
                                    targetSystem = fcuSystemId,
                                    targetComponent = fcuComponentId,
                                    seq = seq.toUShort()
                                )

                                try {
                                    // CRITICAL: Add small delay between items for Bluetooth/real hardware
                                    if (seq > 0) {
                                        delay(50L)  // 50ms delay between items for slower connections
                                    }

                                    connection.trySendUnsignedV2(gcsSystemId, gcsComponentId, missionItem)
                                    sentSeqs.add(seq)
                                    Log.i("MavlinkRepo", "[Mission Upload] ✓ Sent seq=$seq (${sentSeqs.size}/${missionItems.size})")
                                } catch (e: Exception) {
                                    Log.e("MavlinkRepo", "[Mission Upload] ❌ Failed to send seq=$seq", e)
                                    finalAckDeferred.complete(false to "Network error sending item $seq: ${e.message}")
                                }
                            }

                            is MissionAck -> {
                                // CRITICAL: Ignore ACKs from MISSION_CLEAR_ALL phase
                                if (!firstRequestReceived) {
                                    Log.d("MavlinkRepo", "[Mission Upload] Ignoring MISSION_ACK before upload started (from CLEAR phase)")
                                    return@collect
                                }

                                val ackType = msg.type.entry?.name ?: msg.type.value.toString()
                                Log.i("MavlinkRepo", "[Mission Upload] MISSION_ACK received: type=$ackType (sent=${sentSeqs.size}/${missionItems.size}, lastReq=$lastRequestedSeq)")

                                when (msg.type.value) {
                                    MavMissionResult.MAV_MISSION_ACCEPTED.value -> {
                                        // Only accept if ALL items requested and sent
                                        if (sentSeqs.size == missionItems.size && lastRequestedSeq == missionItems.size - 1) {
                                            // NEW: Verify all sequences 0 to N-1 were sent
                                            val allSequences = (0 until missionItems.size).toSet()
                                            if (sentSeqs == allSequences) {
                                                Log.i("MavlinkRepo", "[Mission Upload] ✅ Mission ACCEPTED - all ${missionItems.size} items confirmed")
                                                Log.i("MavlinkRepo", "[Mission Upload] Sequence verification: ${sentSeqs.sorted()}")
                                                finalAckDeferred.complete(true to "")
                                            } else {
                                                val missing = allSequences - sentSeqs
                                                Log.e("MavlinkRepo", "[Mission Upload] ❌ ACCEPTED but missing sequences: $missing")
                                                finalAckDeferred.complete(false to "Mission incomplete - missing sequences: $missing")
                                            }
                                        } else {
                                            Log.w("MavlinkRepo", "[Mission Upload] ⚠️ Premature ACCEPTED ACK - sent=${sentSeqs.size}/${missionItems.size}, lastReq=$lastRequestedSeq")
                                            // Don't complete - wait for all items
                                        }
                                    }
                                    MavMissionResult.MAV_MISSION_INVALID_SEQUENCE.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ MAV_MISSION_INVALID_SEQUENCE")
                                        Log.e("MavlinkRepo", "[Mission Upload] Diagnostics:")
                                        Log.e("MavlinkRepo", "  - Last requested seq: $lastRequestedSeq")
                                        Log.e("MavlinkRepo", "  - Sent sequences: ${sentSeqs.sorted()}")
                                        Log.e("MavlinkRepo", "  - Expected range: 0-${missionItems.size - 1}")
                                        Log.e("MavlinkRepo", "  - Duplicate requests: ${requestCountPerSeq.filter { it.value > 1 }}")
                                        finalAckDeferred.complete(false to "Invalid sequence error at item $lastRequestedSeq")
                                    }
                                    MavMissionResult.MAV_MISSION_DENIED.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ MAV_MISSION_DENIED")
                                        finalAckDeferred.complete(false to "Mission denied by flight controller")
                                    }
                                    MavMissionResult.MAV_MISSION_ERROR.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ MAV_MISSION_ERROR")
                                        finalAckDeferred.complete(false to "Mission error reported by flight controller")
                                    }
                                    MavMissionResult.MAV_MISSION_UNSUPPORTED_FRAME.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ MAV_MISSION_UNSUPPORTED_FRAME")
                                        finalAckDeferred.complete(false to "Unsupported frame type in mission items")
                                    }
                                    MavMissionResult.MAV_MISSION_UNSUPPORTED.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ MAV_MISSION_UNSUPPORTED")
                                        finalAckDeferred.complete(false to "Mission type not supported by flight controller")
                                    }
                                    MavMissionResult.MAV_MISSION_NO_SPACE.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ MAV_MISSION_NO_SPACE")
                                        finalAckDeferred.complete(false to "Not enough space on flight controller for mission")
                                    }
                                    MavMissionResult.MAV_MISSION_INVALID.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ MAV_MISSION_INVALID")
                                        finalAckDeferred.complete(false to "Invalid mission data")
                                    }
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM1.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM2.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM3.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM4.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM5_X.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM6_Y.value,
                                    MavMissionResult.MAV_MISSION_INVALID_PARAM7.value -> {
                                        Log.e("MavlinkRepo", "[Mission Upload] ❌ Invalid parameter in mission item (error: ${msg.type.value})")
                                        Log.e("MavlinkRepo", "[Mission Upload] Problem at seq=$lastRequestedSeq")
                                        if (lastRequestedSeq >= 0 && lastRequestedSeq < missionItems.size) {
                                            val problemItem = missionItems[lastRequestedSeq]
                                            Log.e("MavlinkRepo", "[Mission Upload] Item details: cmd=${problemItem.command.value} " +
                                                    "lat=${problemItem.x / 1e7} lon=${problemItem.y / 1e7} alt=${problemItem.z}")
                                        }
                                        finalAckDeferred.complete(false to "Invalid parameter in mission item $lastRequestedSeq")
                                    }
                                    MavMissionResult.MAV_MISSION_OPERATION_CANCELLED.value -> {
                                        Log.w("MavlinkRepo", "[Mission Upload] ⚠️ MAV_MISSION_OPERATION_CANCELLED")
                                        uploadCancelled = true
                                        finalAckDeferred.complete(false to "Mission upload cancelled")
                                    }
                                    else -> {
                                        Log.w("MavlinkRepo", "[Mission Upload] ⚠️ Unknown MISSION_ACK type: ${msg.type.value}")
                                        finalAckDeferred.complete(false to "Unknown mission error: $ackType")
                                    }
                                }
                            }

                            else -> {
                                // ignore other messages
                            }
                        }
                    }
            }

            // Wait for first request with reasonable timeout
            val firstRequestTimeout = 12000L // Increased from 10s
            val startWait = System.currentTimeMillis()
            while (!firstRequestReceived && !finalAckDeferred.isCompleted && System.currentTimeMillis() - startWait < firstRequestTimeout) {
                delay(100)
            }

            if (!firstRequestReceived && !finalAckDeferred.isCompleted) {
                Log.e("MavlinkRepo", "[Mission Upload] ❌ No MissionRequest received within ${firstRequestTimeout}ms")
                Log.e("MavlinkRepo", "[Mission Upload] FCU may not support mission upload or is not responding")
                finalAckDeferred.complete(false to "No response from flight controller")
            }

            // Wait for final ACK or timeout
            val (success, errorMsg) = withTimeoutOrNull(timeoutMs) {
                finalAckDeferred.await()
            } ?: (false to "Mission upload timeout after ${timeoutMs}ms")

            job.cancel()
            resendJob.cancel()
            watchdogJob.cancel()

            if (success) {
                Log.i("MavlinkRepo", "[Mission Upload] ═══════════════════════════════════════")
                Log.i("MavlinkRepo", "[Mission Upload] ✅ SUCCESS - Mission uploaded!")
                Log.i("MavlinkRepo", "[Mission Upload] Total items: ${missionItems.size}")
                Log.i("MavlinkRepo", "[Mission Upload] Sequences sent: ${sentSeqs.sorted()}")
                Log.i("MavlinkRepo", "[Mission Upload] Duplicate requests: ${requestCountPerSeq.count { it.value > 1 }}")
                Log.i("MavlinkRepo", "[Mission Upload] ═══════════════════════════════════════")
                return true
            } else {
                Log.e("MavlinkRepo", "[Mission Upload] ═══════════════════════════════════════")
                Log.e("MavlinkRepo", "[Mission Upload] ❌ FAILED - $errorMsg")
                Log.e("MavlinkRepo", "[Mission Upload] Items sent: ${sentSeqs.size}/${missionItems.size}")
                Log.e("MavlinkRepo", "[Mission Upload] Sequences sent: ${sentSeqs.sorted()}")
                Log.e("MavlinkRepo", "[Mission Upload] Last requested: $lastRequestedSeq")
                Log.e("MavlinkRepo", "[Mission Upload] ═══════════════════════════════════════")
                return false
            }
        } catch (e: Exception) {
            Log.e("MavlinkRepo", "[Mission Upload] ❌ Exception during upload", e)
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


}
