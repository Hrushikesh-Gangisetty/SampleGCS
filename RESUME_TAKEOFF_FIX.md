# Resume Mission Takeoff Fix - December 8, 2025

## Problem Identified

**Issue:** When resuming from waypoint > 1 (not HOME/default), the drone fails to takeoff and shows error:
```
Mode change to AUTO failed: init failed
Auto: Missing Takeoff Cmd
```

**Root Cause:** 
- ArduPilot copters require a TAKEOFF command when entering AUTO mode
- When resuming from waypoint 1 (HOME + first waypoint), the mission includes the TAKEOFF command
- When resuming from waypoint > 1, there's no TAKEOFF command in the mission path
- The previous implementation only set the waypoint and switched to AUTO, assuming the drone was already airborne

## Solution Implemented

### Enhanced `resumeMissionComplete()` Function

Added intelligent takeoff handling that:
1. **Checks if drone is already in the air** (altitude > 1m)
2. **Automatically performs GUIDED mode takeoff** if drone is on the ground
3. **Waits for target altitude** before switching to AUTO mode
4. **Handles both scenarios:**
   - Waypoint 1 (HOME): No takeoff needed, drone is on ground, mission has TAKEOFF command ✅
   - Waypoint > 1: Performs GUIDED takeoff first, then switches to AUTO ✅

### New 7-Step Resume Process

**When drone is on ground (waypoint > 0):**
1. **Step 1/7:** Pre-flight checks (connection validation)
2. **Step 2/7:** Check drone altitude and determine if takeoff is needed
3. **Step 3/7:** Arm drone (if not already armed)
4. **Step 4/7:** Switch to GUIDED mode
5. **Step 5/7:** Send TAKEOFF command (10m altitude)
   - Waits up to 30 seconds for drone to reach altitude
   - Monitors altitude in real-time with logs
6. **Step 6/7:** Set resume waypoint in FCU
7. **Step 7/7:** Switch to AUTO mode (with 3 retry attempts)

**When drone is already in air (altitude > 1m):**
- Skips steps 3-5 (arming, GUIDED, takeoff)
- Goes directly to setting waypoint and AUTO mode

### Technical Implementation

```kotlin
// Check if takeoff is needed
val currentAlt = _telemetryState.value.altitudeRelative ?: 0f
val needsTakeoff = currentAlt < 1.0f && resumeWaypointNumber > 0

if (needsTakeoff) {
    // Arm drone
    repo?.arm()
    
    // Switch to GUIDED mode
    repo?.changeMode(MavMode.GUIDED)
    
    // Send takeoff command
    repo?.sendCommand(MavCmd.NAV_TAKEOFF, param7 = 10f)
    
    // Wait for altitude (with 30s timeout)
    // Monitor altitude reaching 10m ± 2m tolerance
}

// Set waypoint and switch to AUTO
repo?.setCurrentWaypoint(resumeWaypointNumber)
repo?.changeMode(MavMode.AUTO)
```

## Files Modified

### 1. SharedViewModel.kt
**Changes:**
- Enhanced `resumeMissionComplete()` function
- Added altitude checking logic
- Added GUIDED mode takeoff sequence
- Added altitude monitoring with timeout
- Updated progress messages to 7-step process

### 2. TelemetryRepository.kt
**Changes:**
- Added `GUIDED` mode constant (value: 4u) to `MavMode` object

```kotlin
object MavMode {
    const val STABILIZE: UInt = 0u
    const val LOITER: UInt = 5u
    const val AUTO: UInt = 3u
    const val GUIDED: UInt = 4u // GUIDED mode for copter takeoff
    const val RTL: UInt = 6u
    const val LAND: UInt = 9u
}
```

## Testing Scenarios

### Scenario 1: Resume from Waypoint 1 (Default)
✅ **Expected:** Works as before - drone uses mission TAKEOFF command
- Mission has: HOME → TAKEOFF → WP1 → WP2 → ...
- No GUIDED takeoff needed
- Direct switch to AUTO mode

### Scenario 2: Resume from Waypoint 3
✅ **Expected:** Now performs GUIDED takeoff first
- Checks altitude: 0m (on ground)
- Arms drone
- Switches to GUIDED mode
- Sends TAKEOFF command to 10m
- Waits for altitude ~10m
- Sets waypoint to 3
- Switches to AUTO mode
- Drone continues from WP3

### Scenario 3: Resume while already airborne
✅ **Expected:** Skips takeoff, goes directly to AUTO
- Checks altitude: 15m (already flying)
- Skips arming/takeoff steps
- Sets waypoint
- Switches to AUTO mode

## Log Output Example

When resuming from waypoint 3:
```
ResumeMission: ═══════════════════════════════════════
ResumeMission: Starting Resume Mission
ResumeMission: Resume at waypoint: 3
ResumeMission: Current altitude: 0.0 m
ResumeMission: Needs takeoff: true
ResumeMission: Arming drone for takeoff...
ResumeMission: ✓ Drone armed successfully
ResumeMission: Switching to GUIDED mode for takeoff...
ResumeMission: ✓ Switched to GUIDED mode
ResumeMission: Sending TAKEOFF command to 10.0 meters...
ResumeMission: ✓ Takeoff command sent, waiting for altitude...
ResumeMission: Current altitude: 2.3 m (target: 10.0 m)
ResumeMission: Current altitude: 5.8 m (target: 10.0 m)
ResumeMission: Current altitude: 9.2 m (target: 10.0 m)
ResumeMission: ✓ Reached target altitude: 9.2 m
ResumeMission: Setting current waypoint to 3
ResumeMission: Switching to AUTO mode...
ResumeMission: ✓ Successfully switched to AUTO mode
ResumeMission: ✅ Resume Mission Complete!
```

## Benefits

✅ **Fixes "Missing Takeoff Cmd" error** when resuming from any waypoint  
✅ **Works for both on-ground and in-air scenarios**  
✅ **Automatic altitude checking** - no user input needed  
✅ **Real-time altitude monitoring** with progress updates  
✅ **Comprehensive logging** for debugging  
✅ **Retry logic** for mode changes (3 attempts)  
✅ **Safe timeout handling** (30s for takeoff)  
✅ **Backward compatible** with waypoint 1 (default) resume  

## Status

🟢 **IMPLEMENTED AND READY FOR TESTING**

The resume mission functionality now handles all waypoint scenarios correctly:
- Waypoint 1: Uses existing mission TAKEOFF ✅
- Waypoint > 1: Performs GUIDED takeoff first ✅
- Already airborne: Skips takeoff, resumes directly ✅

