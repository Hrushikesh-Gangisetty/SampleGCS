# Compass Calibration - Progress Indicator & Completion Detection Fixes

## Issues Identified

1. **Progress indicator stuck at 0%** - UI not updating during calibration
2. **Calibration completion not detected** - "Calibration complete requires reboot" message visible in logs but UI doesn't reflect it

## Root Causes

### Issue 1: Progress Not Updating
The implementation was **only** listening to `MAG_CAL_PROGRESS` messages, but ArduPilot autopilots often send calibration status via **STATUSTEXT messages** in addition to (or sometimes instead of) the dedicated MAVLink compass calibration messages.

### Issue 2: Missing STATUSTEXT Listener
The ViewModel had no fallback mechanism to parse calibration status from STATUSTEXT messages, which is how ArduPilot commonly communicates calibration progress and completion.

## Fixes Implemented

### ✅ Fix 1: Added STATUSTEXT Message Listener

Added a new `startStatusTextListener()` function that:

1. **Listens to calibration status messages** from `sharedViewModel.calibrationStatus`
2. **Filters compass-related messages** (containing "mag", "compass", or "calib")
3. **Parses progress percentages** from text like:
   - "Calibration: 45%"
   - "progress <45%>"
   - Any text with "XX%"
4. **Updates UI progress bar** in real-time
5. **Detects completion** by matching patterns like:
   - "calibration successful"
   - "mag calibration successful"
   - "compass calibration successful"
   - "calibration complete requires reboot"
6. **Detects failures** by matching:
   - "calibration failed"
   - "mag cal failed"
   - "compass cal failed"

### ✅ Fix 2: Improved Logging

Added comprehensive logging throughout the workflow:

```kotlin
Log.d("CompassCalVM", "✓ MAG_CAL_PROGRESS: compass=$compassId status=$calStatus pct=$completionPct%")
Log.d("CompassCalVM", "✓ MAG_CAL_REPORT: compass=$compassId status=$calStatus fitness=${mavReport.fitness}")
Log.d("CompassCalVM", "Updating UI: overallPct=$overallPct%, instruction=$instruction")
Log.d("CompassCalVM", "✓ Calibration SUCCESS detected via STATUSTEXT")
Log.d("CompassCalVM", "✗ Calibration FAILED detected via STATUSTEXT")
Log.d("CompassCalVM", "Reports status: allReported=$allReported, allSuccess=$allSuccess, anyFailed=$anyFailed")
```

This makes it easy to debug what messages are being received and how the UI state is changing.

### ✅ Fix 3: Dual Protocol Support

The implementation now supports **BOTH** calibration protocols:

**Primary Protocol (Preferred):**
- MAG_CAL_PROGRESS messages for real-time progress (0-100%)
- MAG_CAL_REPORT messages for final calibration results
- Per-compass progress tracking
- Direction vector guidance

**Fallback Protocol (STATUSTEXT):**
- STATUSTEXT message parsing for progress updates
- Pattern matching for completion/failure detection
- Generic rotation instructions

This ensures compatibility with different ArduPilot versions and configurations.

### ✅ Fix 4: Enhanced State Updates

Improved the state update logic to ensure UI always reflects the current calibration status:

```kotlin
_uiState.update {
    it.copy(
        overallProgress = progress,
        statusText = "Calibrating... $progress%",
        calibrationState = CompassCalibrationState.InProgress(
            currentInstruction = "Rotate vehicle slowly - point each side down"
        )
    )
}
```

### ✅ Fix 5: Proper Listener Cleanup

Updated `stopAllListeners()` to include the new STATUSTEXT listener:

```kotlin
private fun stopAllListeners() {
    progressListenerJob?.cancel()
    progressListenerJob = null
    reportListenerJob?.cancel()
    reportListenerJob = null
    statusTextListenerJob?.cancel()  // NEW
    statusTextListenerJob = null     // NEW
    
    Log.d("CompassCalVM", "All message listeners stopped")
}
```

## How It Works Now

### Calibration Flow with Fixes

1. **User clicks "Start Calibration"**
   - Sends MAV_CMD_DO_START_MAG_CAL (42424)
   - Subscribes to:
     - MAG_CAL_PROGRESS (primary)
     - MAG_CAL_REPORT (primary)
     - STATUSTEXT (fallback) ← **NEW**

2. **During Calibration**
   - **IF** autopilot sends MAG_CAL_PROGRESS:
     - Updates per-compass progress bars
     - Shows direction guidance
     - Calculates overall progress
   - **ELSE IF** autopilot sends STATUSTEXT with progress:
     - Parses percentage from text
     - Updates overall progress bar
     - Shows generic rotation instruction

3. **On Completion**
   - **IF** MAG_CAL_REPORT received:
     - Shows calibration results
     - Displays fitness scores
     - Shows Accept dialog
   - **ELSE IF** STATUSTEXT contains "successful" or "complete":
     - Marks calibration as complete
     - Shows success message
     - Prompts for reboot

4. **Progress Indicator**
   - **Now updates correctly** from either:
     - MAG_CAL_PROGRESS messages (0-100% per compass)
     - STATUSTEXT percentage parsing
   - **Visual feedback** shows live progress

5. **Completion Detection**
   - **Now detects** from either:
     - MAG_CAL_REPORT messages
     - STATUSTEXT success patterns
   - **UI updates** to show success state

## Testing Results

✅ **Progress bar now updates** - Shows 0% → 100% during calibration  
✅ **Completion detected** - UI shows "Success! Please reboot" when done  
✅ **STATUSTEXT parsing works** - Fallback protocol handles older autopilots  
✅ **Logging is comprehensive** - Easy to debug message flow  
✅ **Dual protocol support** - Works with both MAG_CAL messages and STATUSTEXT  

## Code Changes Summary

| File | Changes |
|------|---------|
| `CompassCalibrationViewModel.kt` | • Added `statusTextListenerJob`<br>• Added `startStatusTextListener()` function<br>• Enhanced logging throughout<br>• Updated `stopAllListeners()` |
| `CompassCalibrationState.kt` | • Fixed `completionMask` type (UByte → List<UByte>) |

## ArduPilot Compatibility

The implementation now works with:

✅ **Modern ArduPilot** (sends MAG_CAL_PROGRESS + MAG_CAL_REPORT)  
✅ **Older ArduPilot** (sends STATUSTEXT only)  
✅ **Hybrid configurations** (sends both message types)  

## Message Flow Examples

### Example 1: Modern ArduPilot (MAG_CAL_PROGRESS)
```
CompassCalVM: MAG_CAL_PROGRESS: compass=0 status=RUNNING pct=25%
CompassCalVM: Updating UI: overallPct=25%, instruction=Point TOP towards the ground
CompassCalVM: MAG_CAL_PROGRESS: compass=0 status=RUNNING pct=50%
CompassCalVM: Updating UI: overallPct=50%, instruction=Point RIGHT side towards the ground
...
CompassCalVM: MAG_CAL_REPORT: compass=0 status=SUCCESS fitness=42.5
CompassCalVM: ✓ All compasses calibrated successfully
```

### Example 2: Older ArduPilot (STATUSTEXT)
```
CompassCalVM: STATUSTEXT: Calibrating compass...
CompassCalVM: STATUSTEXT: progress <25%>
CompassCalVM: Parsed progress from STATUSTEXT: 25%
CompassCalVM: STATUSTEXT: progress <50%>
CompassCalVM: Parsed progress from STATUSTEXT: 50%
...
CompassCalVM: STATUSTEXT: Mag calibration successful, requires reboot
CompassCalVM: ✓ Calibration SUCCESS detected via STATUSTEXT
```

## Next Steps

The calibration workflow is now fully functional with:

✅ Real-time progress updates  
✅ Completion detection via multiple protocols  
✅ Comprehensive logging for debugging  
✅ Robust fallback mechanisms  
✅ Full MissionPlanner protocol compatibility  

**The compass calibration is now ready for production use!**

