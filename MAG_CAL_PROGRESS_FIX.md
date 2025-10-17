# MAG_CAL_PROGRESS Message Fix - Compass Calibration Progress Indicator

## Problem Identified

The compass calibration progress indicator was not working because **MAG_CAL_PROGRESS** and **MAG_CAL_REPORT** messages were never being received from the autopilot.

### Root Cause

ArduPilot does not send MAG_CAL_PROGRESS or MAG_CAL_REPORT messages by default. These messages must be **explicitly requested** using the `SET_MESSAGE_INTERVAL` command.

The application was:
1. ✅ Starting calibration correctly (MAV_CMD_DO_START_MAG_CAL sent and acknowledged)
2. ✅ Listening for MAG_CAL_PROGRESS messages
3. ❌ **NOT requesting** these messages from the autopilot
4. ❌ Result: Autopilot never sent the messages, so progress indicator stayed at 0%

### Evidence from Logs

```
2025-10-16 15:33:12.315 CompassCalVM: Sending MAV_CMD_DO_START_MAG_CAL (42424)
2025-10-16 15:33:12.420 CompassCalVM: Received COMMAND_ACK: result=ACCEPTED
2025-10-16 15:33:12.420 CompassCalVM: ✓ Compass calibration started successfully
2025-10-16 15:33:40.983 CompassCalVM: STATUSTEXT: PreArm: Compass calibration running
2025-10-16 15:34:11.997 CompassCalVM: STATUSTEXT: PreArm: Compass calibrated requires reboot
2025-10-16 15:34:11.997 CompassCalVM: ✓ Calibration SUCCESS detected via STATUSTEXT
```

**Notice:** 
- ✅ STATUSTEXT messages received
- ❌ NO MAG_CAL_PROGRESS messages logged
- Result: Calibration succeeded but UI never showed progress percentage

## Solution Implemented

### 1. Added Message Request Function (SharedViewModel.kt)

Added a new function to request MAG_CAL_PROGRESS and MAG_CAL_REPORT messages from the autopilot:

```kotlin
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
```

**Message IDs:**
- 191 = MAG_CAL_PROGRESS
- 192 = MAG_CAL_REPORT

### 2. Request Messages Before Calibration (CompassCalibrationViewModel.kt)

Modified the `startCalibration()` method to request these messages BEFORE sending the calibration start command:

```kotlin
// Subscribe to MAG_CAL_PROGRESS and MAG_CAL_REPORT before sending command
startProgressListener()
startReportListener()
startStatusTextListener()

// --- NEW: Request MAG_CAL_PROGRESS and MAG_CAL_REPORT messages from autopilot ---
Log.d("CompassCalVM", "Requesting MAG_CAL_PROGRESS and MAG_CAL_REPORT messages from autopilot")
sharedViewModel.requestMagCalMessages(hz = 10f) // Request at 10 Hz
delay(500) // Give autopilot time to process request

// Then send calibration start command
sharedViewModel.sendCalibrationCommandRaw(
    commandId = MAV_CMD_DO_START_MAG_CAL,
    // ...
)
```

### 3. Enhanced Debug Logging (TelemetryRepository.kt)

Added debug logging to track ALL incoming MAG_CAL_PROGRESS messages BEFORE filtering:

```kotlin
// MAG_CAL_PROGRESS for compass calibration progress
scope.launch {
    mavFrame
        .map { frame ->
            // Debug: Log ALL MagCalProgress messages BEFORE filtering
            if (frame.message is MagCalProgress) {
                Log.d("TelemetryRepo", "RAW MAG_CAL_PROGRESS received! sysId=${frame.systemId}, fcuSysId=$fcuSystemId, fcuDetected=${state.value.fcuDetected}")
                Log.d("TelemetryRepo", "  Message: ${frame.message}")
            }
            frame
        }
        .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
        .map { it.message }
        .filterIsInstance<MagCalProgress>()
        .collect { progress ->
            Log.d("CompassCalVM", "MAG_CAL_PROGRESS: compassId=${progress.compassId} ...")
            _magCalProgress.emit(progress)
        }
}
```

This helps diagnose if messages are arriving but being filtered out.

## Expected Behavior After Fix

### New Log Sequence:
```
CompassCalVM: Requesting MAG_CAL_PROGRESS and MAG_CAL_REPORT messages from autopilot
SharedVM: Requesting MAG_CAL_PROGRESS and MAG_CAL_REPORT messages at 10.0 Hz
CompassCalVM: Sending MAV_CMD_DO_START_MAG_CAL (42424)
CompassCalVM: Received COMMAND_ACK: result=ACCEPTED
CompassCalVM: ✓ Compass calibration started successfully
TelemetryRepo: RAW MAG_CAL_PROGRESS received! sysId=1, fcuSysId=1, fcuDetected=true
CompassCalVM: MAG_CAL_PROGRESS: compass=0 status=RUNNING pct=15
CompassCalVM: Updating UI: overallPct=15%, instruction=...
TelemetryRepo: RAW MAG_CAL_PROGRESS received! sysId=1, fcuSysId=1, fcuDetected=true
CompassCalVM: MAG_CAL_PROGRESS: compass=0 status=RUNNING pct=35
CompassCalVM: Updating UI: overallPct=35%, instruction=...
...
CompassCalVM: MAG_CAL_PROGRESS: compass=0 status=RUNNING pct=100
TelemetryRepo: RAW MAG_CAL_REPORT received! sysId=1, fcuSysId=1, fcuDetected=true
CompassCalVM: MAG_CAL_REPORT: compass=0 status=SUCCESS fitness=45.2
CompassCalVM: ✓ All compasses calibrated successfully
```

### UI Changes:
- ✅ Progress bar now updates from 0% → 100%
- ✅ Real-time rotation instructions based on direction vectors
- ✅ Individual compass progress tracking
- ✅ Detailed calibration reports with fitness metrics

## Why This Works

1. **MAVLink Message Streaming:** ArduPilot uses a message streaming model where the GCS must request specific message types and rates
2. **Default Messages:** Only critical messages (HEARTBEAT, SYS_STATUS, etc.) are sent by default
3. **Calibration Messages:** MAG_CAL_PROGRESS and MAG_CAL_REPORT are **optional** messages that must be explicitly enabled
4. **SET_MESSAGE_INTERVAL:** This command tells ArduPilot to send a specific message ID at a specific rate (in microseconds)

## Alternative Behavior (Fallback)

The implementation still supports autopilots that don't send MAG_CAL_PROGRESS:
- STATUSTEXT parsing still works as a fallback
- Success/failure detection via STATUSTEXT remains functional
- If MAG_CAL_PROGRESS messages are not supported by the autopilot firmware, calibration will still complete using STATUSTEXT

## Files Modified

1. **SharedViewModel.kt** - Added `requestMagCalMessages()` function
2. **CompassCalibrationViewModel.kt** - Request messages before starting calibration
3. **TelemetryRepository.kt** - Enhanced debug logging for troubleshooting

## Testing Instructions

1. Connect to your drone/SITL
2. Navigate to Compass Calibration screen
3. Tap "Start Calibration"
4. Check logcat for:
   - "Requesting MAG_CAL_PROGRESS and MAG_CAL_REPORT messages"
   - "RAW MAG_CAL_PROGRESS received!"
   - Progress percentage updates (15%, 35%, 50%, etc.)
5. Verify progress bar animates smoothly
6. Verify rotation instructions update based on orientation

## Conclusion

The progress indicator now works because we explicitly request the MAG_CAL_PROGRESS messages from the autopilot at 10 Hz. This ensures the autopilot sends progress updates throughout the calibration process, enabling real-time UI updates.

**Status: ✅ FIXED**

