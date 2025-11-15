# Mission Upload MISSION_CLEAR_ALL Fix

## Issue Summary
Mission upload was failing at the MISSION_CLEAR_ALL step with the error:
```
[Mission Upload] MISSION_CLEAR_ALL failed after 3 attempts
Mission upload failed or timed out
```

## Root Cause Analysis

### The Problem
The code was waiting for a **COMMAND_ACK** message in response to `MISSION_CLEAR_ALL`, but according to the MAVLink protocol, `MISSION_CLEAR_ALL` returns a **MISSION_ACK** message, not a COMMAND_ACK.

### Evidence from Logs
The logs clearly showed that MISSION_ACK messages were being received from the FCU:
- `09:49:20.479` - Frame: MissionAck (sysId=1, compId=1) [after attempt 1]
- `09:49:26.505` - Frame: MissionAck (sysId=1, compId=1) [after attempt 2]
- `09:49:32.489` - Frame: MissionAck (sysId=1, compId=1) [after attempt 3]

But the code was filtering for `CommandAck` messages:
```kotlin
.filterIsInstance<CommandAck>()
.collect { ack ->
    if (ack.command.value == MISSION_CLEAR_ALL_CMD && ...) {
        // This never executed because MISSION_ACK was received, not COMMAND_ACK
    }
}
```

## Solution Implemented

### Changes Made
1. **Changed message type filter** from `CommandAck` to `MissionAck`
2. **Updated acknowledgment check** to verify `MavMissionResult.MAV_MISSION_ACCEPTED`
3. **Removed unused constant** `MISSION_CLEAR_ALL_CMD` (command ID 45)
4. **Added better logging** to show the actual MISSION_ACK type received

### Fixed Code
```kotlin
// Wait for MISSION_ACK for MISSION_CLEAR_ALL (not COMMAND_ACK)
val clearAckDeferred = CompletableDeferred<Boolean>()
val clearJob = AppScope.launch {
    connection.mavFrame
        .filter { it.systemId == fcuSystemId }
        .map { it.message }
        .filterIsInstance<MissionAck>()  // ← Changed from CommandAck
        .collect { ack ->
            Log.i("MavlinkRepo", "[Mission Upload] MISSION_ACK received: type=${ack.type.entry?.name ?: ack.type.value}")
            if (ack.type.value == MavMissionResult.MAV_MISSION_ACCEPTED.value) {
                Log.i("MavlinkRepo", "[Mission Upload] MISSION_CLEAR_ALL acknowledged by FCU")
                clearAckDeferred.complete(true)
            }
        }
}
```

## MAVLink Protocol Reference

According to the MAVLink protocol:
- **MISSION_CLEAR_ALL** (message ID 45): Clears all mission items stored on the autopilot
- **Response**: MISSION_ACK with result type (MAV_MISSION_ACCEPTED, MAV_MISSION_ERROR, etc.)

The mission protocol messages that return MISSION_ACK include:
- MISSION_CLEAR_ALL
- MISSION_COUNT
- MISSION_ITEM_INT / MISSION_ITEM
- MISSION_SET_CURRENT

## Expected Behavior After Fix

1. GCS sends MISSION_CLEAR_ALL to FCU
2. FCU responds with MISSION_ACK (type=MAV_MISSION_ACCEPTED)
3. GCS correctly detects the acknowledgment
4. Mission upload proceeds to send MISSION_COUNT and mission items

## Testing Recommendations

Test the complete mission upload flow:
1. Upload a mission with multiple waypoints
2. Verify MISSION_CLEAR_ALL succeeds on first attempt
3. Verify mission items are uploaded successfully
4. Check that mission can be started and executed

## Files Modified
- `TelemetryRepository.kt`: Fixed MISSION_CLEAR_ALL acknowledgment handling

## Date
2025-11-15

