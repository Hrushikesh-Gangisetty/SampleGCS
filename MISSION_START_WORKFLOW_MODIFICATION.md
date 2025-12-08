# Mission Start Workflow Modification

## Summary
Modified the mission start workflow to require manual arming via RC transmitter and added automatic mode override detection with LOITER failsafe.

## Changes Made

### 1. Manual Arming Requirement
**File**: `SharedViewModel.kt`

#### Before:
- The `startMission()` function automatically armed the drone
- Switched to Stabilize mode if needed
- Attempted arming with timeout
- Then switched to AUTO mode

#### After:
- **Manual arming required**: Users must arm the drone using the RC transmitter
- The `startMission()` function checks if the drone is already armed
- If not armed, returns error: "Please arm the drone manually using the RC transmitter before starting mission."
- Only proceeds with mission start if drone is already armed
- Automatically switches to AUTO mode once mission starts

### 2. Mission Mode Monitoring
**File**: `SharedViewModel.kt`

#### New Feature: Automatic LOITER on Mode Override
When mission starts, the system now monitors for manual mode changes:

- **Monitoring starts**: When mission successfully starts in AUTO mode
- **Detection**: If user manually changes flight mode during mission (via RC)
- **Action**: Automatically switches to LOITER mode to hold position
- **Notification**: User is notified of mode change and LOITER activation
- **Monitoring stops**: When mission is paused, completed, drone is disarmed, or connection is lost

#### Implementation Details:
```kotlin
// State variable to track monitoring
private val _isMissionModeMonitoringActive = MutableStateFlow(false)

// Start monitoring after mission begins
private fun startMissionModeMonitoring()

// Stop monitoring
fun stopMissionModeMonitoring()
```

#### Monitoring Triggers:
- **Starts when**:
  - Mission starts successfully
  - Mission resumes from pause
  
- **Stops when**:
  - Mission is paused
  - Drone is disarmed
  - Connection is cancelled/lost
  - Manual mode override detected (to prevent recursive mode changes)

### 3. Armed State Monitoring
**File**: `SharedViewModel.kt`

Added monitoring in the `init` block to detect when drone is disarmed during mission:
- Monitors armed state changes
- Automatically stops mission mode monitoring when drone disarms
- Prevents unnecessary mode monitoring after landing

## User Workflow

### Old Workflow:
1. Upload mission
2. Press "Start" button
3. System automatically arms and starts mission

### New Workflow:
1. Upload mission
2. **Manually arm drone via RC transmitter**
3. Press "Start" button
4. System switches to AUTO mode and starts mission
5. Mission runs autonomously
6. **If user changes mode via RC**: System automatically switches to LOITER

## Safety Features

### 1. Pre-Flight Checks (Unchanged)
- Mission must be uploaded
- FCU must be detected
- Vehicle must be armable
- Minimum 6 GPS satellites required

### 2. Mode Override Safety (New)
- Detects manual mode changes during autonomous flight
- Automatically switches to LOITER to hold position
- Prevents drone from continuing in unexpected mode
- User receives notification of mode change

### 3. Automatic Monitoring Cleanup (New)
- Monitoring stops when mission pauses
- Monitoring stops when drone disarms
- Monitoring stops on disconnect
- Prevents resource leaks and unwanted mode changes

## Error Messages

### New Error Messages:
1. **Not Armed**: "Please arm the drone manually using the RC transmitter before starting mission."
2. **Mode Override Detected**: "Manual mode change detected. Switching to LOITER mode."
3. **LOITER Activated**: "LOITER mode activated - holding position"

## Technical Notes

### Mode Monitoring Implementation:
- Uses coroutine flow to monitor telemetry state
- Checks for mode changes from AUTO to any other mode
- Implements single-shot detection to avoid recursive mode changes
- Automatically stops monitoring on mode override

### Performance:
- Minimal overhead - only active during mission execution
- Automatic cleanup prevents memory leaks
- Uses existing telemetry flow infrastructure

## Testing Recommendations

1. **Test Manual Arming**:
   - Try starting mission without arming → Should get error message
   - Arm via RC, then start mission → Should succeed

2. **Test Mode Override**:
   - Start mission (arms via RC, then start)
   - Switch mode via RC during flight
   - Verify drone switches to LOITER automatically

3. **Test Monitoring Cleanup**:
   - Start mission
   - Pause mission → Verify monitoring stops
   - Resume mission → Verify monitoring restarts
   - Disarm drone → Verify monitoring stops

4. **Test Error Cases**:
   - Test with insufficient satellites
   - Test with unarmable vehicle
   - Test without uploaded mission

## Benefits

1. **Safety**: Manual arming gives pilot final control over takeoff authorization
2. **Failsafe**: Automatic LOITER on mode override prevents unexpected flight behavior
3. **User Control**: Pilot can override mission at any time via RC
4. **Robust**: Automatic monitoring cleanup prevents resource issues
5. **Clear Feedback**: User notifications explain all mode changes

## Files Modified

1. `app/src/main/java/com/example/aerogcsclone/telemetry/SharedViewModel.kt`
   - Modified `startMission()` function
   - Added `startMissionModeMonitoring()` function
   - Added `stopMissionModeMonitoring()` function
   - Added mission mode monitoring state variable
   - Updated `pauseMission()` to stop monitoring
   - Updated `resumeMission()` to restart monitoring
   - Updated `cancelConnection()` to stop monitoring
   - Added armed state monitoring in `init` block

## Backward Compatibility

- UI code (MainPage.kt) requires no changes
- Existing mission upload/pause/resume functions work as before
- Only change is removal of automatic arming
- Error messages guide users to new workflow

