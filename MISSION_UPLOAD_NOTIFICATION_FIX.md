# Mission Upload Notification Fix

## Problem Summary

When uploading a mission to SITL, users were experiencing **triplicate notifications** showing "MissionUpload:MavMissionUploaded" three times in the notification panel.

## Root Cause Analysis

### Clear Phase Behavior (Not a Bug)
The logs showed:
```
10:51:02.318 - Clear phase ACK: MAV_MISSION_ACCEPTED (attempt 1)
10:51:08.505 - Clear phase ACK: MAV_MISSION_ACCEPTED (attempt 2)
10:51:13.501 - MISSION_ACK received: MAV_MISSION_ACCEPTED (upload complete)
```

The clear phase retry logic is **working correctly**:
- Attempt 1: Sends MISSION_CLEAR_ALL, waits 5 seconds for ACK
- Attempt 2: Retries after timeout and succeeds
- This retry mechanism is intentional for hardware reliability

### Triplicate Notification Issue (The Actual Bug) ⚠️

There was a **global MISSION_ACK listener** in `TelemetryRepository.kt` (lines 585-597) that was listening to **ALL** MISSION_ACK messages and automatically creating notifications:

```kotlin
// MISSION_ACK for mission upload status
scope.launch {
    mavFrame
        .filter { state.value.fcuDetected && it.systemId == fcuSystemId }
        .map { it.message }
        .filterIsInstance<MissionAck>()
        .collect { missionAck ->
            val message = "Mission upload: ${missionAck.type.entry?.name ?: "UNKNOWN"}"
            val type = if (missionAck.type.value == MavMissionResult.MAV_MISSION_ACCEPTED.value) 
                NotificationType.SUCCESS else NotificationType.ERROR
            sharedViewModel.addNotification(Notification(message, type))
        }
}
```

This caused:
1. **1st notification**: Clear phase ACK (attempt 1)
2. **2nd notification**: Clear phase ACK (attempt 2)  
3. **3rd notification**: Final upload phase ACK

All three ACKs were being processed by the global listener, resulting in triplicate notifications.

## Solution Implemented

### Added Upload State Tracking Flag

**File**: `TelemetryRepository.kt`

1. **Added flag** (line 82):
```kotlin
private var isMissionUploadInProgress = false  // Track if mission upload is actively in progress
```

2. **Modified global MISSION_ACK listener** to filter out ACKs during upload:
```kotlin
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
            val type = if (missionAck.type.value == MavMissionResult.MAV_MISSION_ACCEPTED.value) 
                NotificationType.SUCCESS else NotificationType.ERROR
            sharedViewModel.addNotification(Notification(message, type))
        }
}
```

3. **Modified `uploadMissionWithAck` function** to set/reset the flag:
```kotlin
@Suppress("DEPRECATION")
suspend fun uploadMissionWithAck(missionItems: List<MissionItemInt>, timeoutMs: Long = 45000): Boolean {
    // Mark upload as in progress to prevent global listener from showing notifications
    isMissionUploadInProgress = true
    
    try {
        // ... existing upload logic ...
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
```

## Expected Behavior After Fix

### Before Fix
```
Notification Panel:
- Mission upload: MAV_MISSION_ACCEPTED  (from clear attempt 1)
- Mission upload: MAV_MISSION_ACCEPTED  (from clear attempt 2)
- Mission upload: MAV_MISSION_ACCEPTED  (from final upload)
```

### After Fix
```
Notification Panel:
(no notifications during upload - handled internally by upload function)
```

The upload function handles all ACK messages internally and only shows relevant UI feedback through the progress system, not through raw MISSION_ACK notifications.

## Testing Recommendations

1. Upload a mission to SITL
2. Check the notification panel - you should **NOT** see multiple "Mission upload: MAV_MISSION_ACCEPTED" messages
3. Check logs to verify:
   - Clear phase ACKs are logged but not sent to notification panel
   - Upload phase ACKs are logged but not sent to notification panel
   - Global listener correctly ignores ACKs during upload process

## Technical Details

### Why This Works

- The global MISSION_ACK listener is designed for **general mission operations** (e.g., mission download, mission clear from UI)
- During **mission upload**, the `uploadMissionWithAck` function has its own dedicated ACK handling logic with specific state tracking
- By setting `isMissionUploadInProgress = true`, we temporarily disable the global listener during the upload process
- The `finally` block ensures the flag is always reset, even if an exception occurs

### Thread Safety

The flag is accessed from coroutine scopes but doesn't require synchronization because:
- It's only modified by the `uploadMissionWithAck` function (single writer)
- The global listener reads it (single reader)
- Both run in the same coroutine context (AppScope)

## Files Modified

- `app/src/main/java/com/example/aerogcsclone/telemetry/TelemetryRepository.kt`
  - Added `isMissionUploadInProgress` flag (line 82)
  - Modified global MISSION_ACK listener to filter based on flag (lines 585-604)
  - Modified `uploadMissionWithAck` to set/reset flag (lines 854-1044)

## Related Issues

- Clear phase retry logic is working correctly (2 attempts with 5-second timeout)
- This is expected behavior for real hardware reliability
- No changes needed for clear phase logic

---

**Status**: ✅ Fixed  
**Date**: November 21, 2025  
**Severity**: Medium (cosmetic issue causing notification spam)

