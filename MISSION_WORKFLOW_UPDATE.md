# Mission Workflow Update - Manual Control

## Summary
Updated the mission workflow to require **manual arming, manual takeoff, and manual AUTO mode selection** via RC transmitter. The system now automatically starts the mission when AUTO mode is detected.

## Changes Made

### 1. **Modified `startMission()` Function**
- **Old Behavior:**
  - Checked if vehicle was armed
  - Automatically switched to AUTO mode
  - Sent mission start command
  
- **New Behavior:**
  - Validates mission readiness (uploaded mission, GPS satellites, etc.)
  - Checks if vehicle is manually armed via RC
  - Returns a message instructing user to switch to AUTO mode via RC
  - Mission starts automatically when AUTO mode is detected

### 2. **Added AUTO Mode Monitoring**
- New monitoring system in the `init` block watches for mode changes
- When AUTO mode is detected via RC:
  - Verifies mission is uploaded
  - Verifies vehicle is armed
  - Automatically calls `startMissionExecution()`
  - Starts mission mode monitoring

### 3. **Updated `resumeMission()` Function**
- **Old Behavior:**
  - Automatically switched to AUTO mode to resume mission
  
- **New Behavior:**
  - Sets waypoint for resume if needed
  - Clears paused state
  - Returns message instructing user to switch to AUTO mode via RC
  - Mission resumes automatically when AUTO mode is detected

### 4. **Updated `resumeFromSplitPlan()` Function**
- **Old Behavior:**
  - Automatically armed the vehicle
  - Automatically switched to AUTO mode
  
- **New Behavior:**
  - Validates split plan is ready
  - Checks if vehicle is manually armed via RC
  - Returns message instructing user to switch to AUTO mode via RC
  - Mission resumes automatically when AUTO mode is detected

## New Mission Workflow

### Starting a Mission

1. **Upload Mission**
   - User uploads waypoints/mission via GCS interface

2. **Manual Arming**
   - User arms the drone using RC transmitter
   - System validates: connection, FCU, mission uploaded, GPS satellites

3. **Manual Takeoff**
   - User manually takes off the drone using RC
   - Achieves desired altitude

4. **Switch to AUTO Mode**
   - User switches flight mode to AUTO using RC transmitter
   - **System automatically detects AUTO mode and starts mission**
   - Mission begins executing uploaded waypoints

5. **Mission Monitoring**
   - System monitors for manual mode changes
   - If pilot changes mode manually, system switches to LOITER

### Resuming After Pause

1. **Mission Paused**
   - System is in LOITER mode at paused waypoint

2. **Click Resume Button**
   - GCS prepares mission for resume
   - Waypoint is set if needed

3. **Switch to AUTO Mode**
   - User switches to AUTO mode via RC transmitter
   - **System automatically resumes mission**

### Resuming From Split Plan

1. **Split Plan Active**
   - Drone landed and disarmed after partial mission completion

2. **Manual Arming**
   - User arms drone via RC transmitter

3. **Manual Takeoff**
   - User takes off manually via RC

4. **Click Resume Split Plan Button**
   - GCS validates readiness

5. **Switch to AUTO Mode**
   - User switches to AUTO mode via RC transmitter
   - **System automatically continues mission from split point**

## Technical Implementation

### Files Modified
- `SharedViewModel.kt` - Mission control logic

### Key Functions

#### `startMission()`
```kotlin
/**
 * Start mission workflow - validates mission readiness.
 * 
 * NEW WORKFLOW (Manual Control):
 * 1. This function validates that mission is uploaded and conditions are met
 * 2. User must manually arm the drone using RC transmitter
 * 3. User must manually takeoff the drone using RC
 * 4. User must manually switch to AUTO mode using RC
 * 5. Mission will start AUTOMATICALLY when AUTO mode is detected
 */
```

#### AUTO Mode Monitoring (in `init` block)
```kotlin
// Monitor mode changes - automatically start/resume mission when AUTO mode is detected via RC
viewModelScope.launch {
    var previousMode: String? = null
    telemetryState.collect { state ->
        val currentMode = state.mode
        
        // Check if mode switched to AUTO
        if (previousMode?.contains("Auto", ignoreCase = true) != true &&
            currentMode?.contains("Auto", ignoreCase = true) == true) {
            
            // Conditions: Mission uploaded, Armed, Not already monitoring
            if (_missionUploaded.value && 
                state.armed && 
                !_isMissionModeMonitoringActive.value) {
                
                startMissionExecution()
            }
        }
        
        previousMode = currentMode
    }
}
```

#### `startMissionExecution()` (Private)
- Called automatically when AUTO mode detected
- Sends mission start command to FCU
- Starts mission mode monitoring
- Displays success/error notifications

## Benefits

1. **Safety**: Pilot has full control over arming, takeoff, and mode selection
2. **Flexibility**: Pilot can takeoff manually and gain altitude before starting mission
3. **Standard Practice**: Follows common drone operation procedures
4. **No Automation Risk**: System cannot arm or change modes without pilot action
5. **Seamless Integration**: Once AUTO mode is selected, mission starts automatically

## User Experience

### UI Changes Required
- Update mission start button text/tooltip to explain new workflow
- Show clear instructions: "Arm, takeoff, then switch to AUTO mode"
- Toast messages guide user through the process

### Expected Messages
- "Ready for mission. Switch to AUTO mode via RC to start."
- "Please arm and takeoff the drone manually using the RC transmitter, then switch to AUTO mode."
- "Mission started in AUTO mode" (when AUTO detected)

## Testing Checklist

- [ ] Upload mission with valid waypoints
- [ ] Try starting mission when not armed (should show error)
- [ ] Arm drone via RC
- [ ] Takeoff manually via RC
- [ ] Switch to AUTO mode via RC
- [ ] Verify mission starts automatically
- [ ] Pause mission during execution
- [ ] Resume mission by switching to AUTO mode via RC
- [ ] Test split plan workflow with manual arming and AUTO mode switch

## Notes

- No other functionalities have been changed
- Geofence, obstacle detection, and other features work as before
- Mission mode monitoring still active during mission execution
- Manual mode changes during mission still trigger LOITER mode

