# Resume Dialog Waypoint Tracking Fix

## Problem Statement
When a mission is paused at waypoint 10, the resume dialog shows "1" as the default waypoint instead of "10". This forces users to manually type the correct waypoint number every time they want to resume a mission.

## Root Cause
The previous implementation updated `currentWaypoint` on every MISSION_CURRENT message, but didn't specifically track the waypoint number during AUTO mode mission execution. This is inconsistent with Mission Planner's behavior which tracks `lastautowp` specifically during AUTO mode.

## Solution Implementation

### Changes Made

#### 1. Data.kt - Added `lastAutoWaypoint` field
**Location**: `app/src/main/java/com/example/aerogcsclone/telemetry/Data.kt:83`

```kotlin
// Last waypoint when in AUTO mode (Mission Planner's lastautowp equivalent)
// This tracks the waypoint number during mission execution to pre-fill resume dialog
val lastAutoWaypoint: Int = -1,
```

- Initialized to `-1` (same as Mission Planner) to indicate "not set"
- Separate from `currentWaypoint` which updates on all MISSION_CURRENT messages

#### 2. TelemetryRepository.kt - Conditional tracking in AUTO mode
**Location**: `app/src/main/java/com/example/aerogcsclone/telemetry/TelemetryRepository.kt:812-817`

```kotlin
// Track last AUTO waypoint (Mission Planner protocol)
// Only update lastAutoWaypoint when in AUTO mode and waypoint is non-zero
if (currentMode?.equals("Auto", ignoreCase = true) == true && currentSeq != 0) {
    _state.update { it.copy(lastAutoWaypoint = currentSeq) }
    Log.d("MavlinkRepo", "Updated lastAutoWaypoint to: $currentSeq (mode=$currentMode)")
}
```

This matches Mission Planner's protocol:
```csharp
if (mode.ToLower() == "auto" && wpno != 0) lastautowp = (int)wpno;
```

#### 3. SharedViewModel.kt - Use lastAutoWaypoint in pauseMission
**Location**: `app/src/main/java/com/example/aerogcsclone/telemetry/SharedViewModel.kt:1157-1161`

```kotlin
// Use lastAutoWaypoint (tracked during AUTO mode) for accurate pause tracking
// Falls back to currentWaypoint if lastAutoWaypoint is not set (-1)
val lastAutoWp = _telemetryState.value.lastAutoWaypoint
val waypointToStore = if (lastAutoWp > 0) lastAutoWp else _telemetryState.value.currentWaypoint
Log.i("SharedVM", "Pausing mission - lastAutoWaypoint: $lastAutoWp, currentWaypoint: ${_telemetryState.value.currentWaypoint}, using: $waypointToStore")
```

#### 4. SharedViewModel.kt - Fixed state synchronization
**Location**: `app/src/main/java/com/example/aerogcsclone/telemetry/SharedViewModel.kt:473-481`

```kotlin
newRepo.state.collect { repoState ->
    // Preserve SharedViewModel-managed fields (pause state) while updating from repository
    _telemetryState.update { currentState ->
        repoState.copy(
            missionPaused = currentState.missionPaused,
            pausedAtWaypoint = currentState.pausedAtWaypoint
        )
    }
}
```

This prevents repository state updates from overwriting pause-related fields that are managed by SharedViewModel.

## Data Flow

### During Mission Execution
1. Drone receives mission and enters AUTO mode
2. MISSION_CURRENT messages arrive with waypoint numbers (1, 2, 3, ...)
3. TelemetryRepository updates both `currentWaypoint` and `lastAutoWaypoint` (only if mode is AUTO and waypoint != 0)
4. State flows from Repository → SharedViewModel (preserving pause fields)

### When Pausing Mission
1. User clicks Pause button
2. `pauseMission()` is called
3. Function reads `lastAutoWaypoint` (or falls back to `currentWaypoint`)
4. Stores value in `pausedAtWaypoint`
5. Changes mode to LOITER to hold position

### When Resuming Mission
1. User clicks Resume button
2. Resume dialog opens
3. MainPage reads waypoint with fallback chain:
   - First: `telemetryState.pausedAtWaypoint`
   - Then: `telemetryState.currentWaypoint`
   - Finally: `1` (default)
4. Dialog shows the correct waypoint number
5. User can modify if needed or accept default

## Testing Instructions

### Test Case 1: Basic Pause/Resume
1. Upload a mission with 10+ waypoints
2. Arm and start mission in AUTO mode
3. Wait for drone to reach waypoint 10
4. Click Pause button
5. **Verify**: Notification shows "Mission paused at waypoint 10"
6. Click Resume button
7. **Expected**: Dialog shows "10" as default waypoint (not "1")
8. Click Resume to continue mission
9. **Verify**: Mission resumes from waypoint 10

### Test Case 2: No Mission Run Yet
1. Connect to drone
2. Click Resume button (without running a mission first)
3. **Expected**: Dialog shows "1" as default (lastAutoWaypoint = -1)
4. This is the fallback behavior

### Test Case 3: Multiple Pause/Resume Cycles
1. Start mission, pause at waypoint 5
2. Resume, let it continue to waypoint 8
3. Pause again at waypoint 8
4. **Expected**: Resume dialog shows "8" (not "5" from previous pause)
5. This verifies that lastAutoWaypoint updates continuously

### Test Case 4: Mode Change Without Mission
1. Connect to drone
2. Manually change mode to GUIDED or LOITER
3. Change back to AUTO (without a mission)
4. **Verify**: lastAutoWaypoint should still be -1 (because no MISSION_CURRENT with non-zero waypoint)

## Logging for Debugging

To debug waypoint tracking issues, check these log messages:

### In TelemetryRepository
```
MavlinkRepo: Mission progress: waypoint X
MavlinkRepo: Updated lastAutoWaypoint to: X (mode=Auto)
```

### In SharedViewModel
```
SharedVM: Pausing mission - lastAutoWaypoint: X, currentWaypoint: X, using: X
SharedVM: Mission paused successfully. pausedAtWaypoint set to: X
```

### In MainPage
The dialog initialization logs should show the correct default waypoint being selected.

## Expected Behavior Changes

### Before Fix
- Resume dialog always showed "1" regardless of pause waypoint
- User had to manually type the correct waypoint number
- Risk of user error when typing waypoint numbers

### After Fix
✅ Resume dialog shows the actual waypoint where mission was paused
✅ Pre-filled value matches the last AUTO waypoint
✅ User can still manually change if needed
✅ Fallback to "1" if no mission has run yet

## Mission Planner Parity

This implementation now matches Mission Planner's behavior:

**Mission Planner (C#)**:
```csharp
// Track last auto waypoint (line 3379)
if (mode.ToLower() == "auto" && wpno != 0) lastautowp = (int)wpno;

// Use in resume dialog (lines 1484-1488)
string lastwp = MainV2.comPort.MAV.cs.lastautowp.ToString();
if (lastwp == "-1") lastwp = "1";
if (InputBox.Show("Resume at", "Resume mission at waypoint#", ref lastwp) == DialogResult.OK)
```

**SampleGCS (Kotlin)** - Now implements the same logic:
```kotlin
// Track last auto waypoint
if (currentMode?.equals("Auto", ignoreCase = true) == true && currentSeq != 0) {
    _state.update { it.copy(lastAutoWaypoint = currentSeq) }
}

// Use in pause
val lastAutoWp = _telemetryState.value.lastAutoWaypoint
val waypointToStore = if (lastAutoWp > 0) lastAutoWp else _telemetryState.value.currentWaypoint

// Dialog shows pausedAtWaypoint with fallback chain
val defaultWaypoint = telemetryState.pausedAtWaypoint
    ?: telemetryState.currentWaypoint
    ?: 1
```

## Notes

1. **State Preservation**: The state synchronization fix ensures that pause-related fields are preserved when repository state flows to SharedViewModel. This is critical for preventing race conditions.

2. **Thread Safety**: Using `_telemetryState.update { }` ensures atomic updates even with concurrent state changes.

3. **Fallback Logic**: The fallback chain (lastAutoWaypoint → currentWaypoint → 1) ensures the system always has a valid default value.

4. **Mission Planner Compatibility**: The logic matches Mission Planner's implementation for consistent behavior across GCS platforms.
