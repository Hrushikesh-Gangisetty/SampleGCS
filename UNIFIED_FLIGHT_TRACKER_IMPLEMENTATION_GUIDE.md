# Unified Flight Tracking Implementation - COMPLETE REFACTOR REQUIRED

## Date: December 12, 2025

## CRITICAL ISSUES IDENTIFIED

The current implementation has **TWO SEPARATE flight tracking systems** running simultaneously:

1. **TelemetryRepository** (lines ~370-486) - Tracking manual flights, showing notifications
2. **FlightManager** - Trying to log flights to database

These systems conflict with each other, causing:
- ❌ Logs not saving (FlightManager timing doesn't match TelemetryRepository)
- ❌ Wrong time/distance (0.0m, 00:00:00) in notifications
- ❌ Duplicate "flight completed" and "Mission Started" messages
- ❌ Time glitching between 0 and actual time

## ROOT CAUSE

Both systems independently evaluate the same flight start/stop conditions:
- TelemetryRepository calculates time and distance → shows notifications
- FlightManager doesn't see TelemetryRepository's data → saves wrong values to database

The notifications show "00:00:00" and "0.0m" because TelemetryRepository's tracking happens AFTER FlightManager has already saved the flight with no data.

## SOLUTION: Unified Flight Tracker

I've created a **UnifiedFlightTracker** that implements the proper state machine you specified:

### Files Created
1. `UnifiedFlightTracker.kt` - Single source of truth for flight tracking
2. Updated `SharedViewModel.kt` - Added `updateFlightState()` method

### State Machine Implementation

```
IDLE → STARTING (debounce) → ACTIVE → STOPPING (debounce) → FINALIZING → IDLE
```

**Start Conditions (Global Preconditions + Mode-Specific):**
- ✅ MAVLink connection healthy
- ✅ GPS acceptable (HDOP check)
- ✅ Mode detection (AUTO vs MANUAL)
- ✅ Altitude threshold (ground + 1m)
- ✅ Movement detection (speed or altitude change)
- ✅ 1.5s debounce

**Stop Conditions (Priority Order):**
1. User STOP (not yet implemented, but highest priority)
2. AUTO-only: mission last-item reached
3. Common: disarmed + low speed
4. Common: landed (altitude ≤ ground + 0.5m)
5. Failsafe modes (RTL, Land)
6. 2s debounce

**During Flight:**
- ✅ Real-time distance calculation (haversine with HDOP filtering)
- ✅ Real-time timer (updates every telemetry cycle)
- ✅ Telemetry logging every 5s (via FlightLoggingService)
- ✅ Battery warnings
- ✅ Connection loss handling

## REQUIRED CHANGES

### 1. Remove Conflicting Code from TelemetryRepository.kt

**Lines to REMOVE (~370-486 in GLOBAL_POSITION_INT handler):**
```kotlin
// DELETE ALL OF THIS MANUAL FLIGHT TRACKING CODE:
- Manual mission tracking logic
- Check if flight should start
- Track distance during active manual flight
- Stop conditions: altitude near ground level OR speed = 0 OR disarmed
- All the notification generation
- All the state updates for missionElapsedSec, totalDistanceMeters, missionCompleted
```

**What to KEEP:**
```kotlin
// KEEP ONLY THESE:
- TTS announcements for armed/disarmed (lines ~370-378)
- Basic state updates for altitude, lat, lon (position data only)
```

### 2. Update MainActivity to Use UnifiedFlightTracker

**Location:** `MainActivity.kt` (or wherever FlightManager is initialized)

**Replace:**
```kotlin
// OLD:
private var flightManager: FlightManager? = null

// Initialize
flightManager = FlightManager(context, tlogViewModel, sharedViewModel)

// Cleanup
flightManager?.destroy()
```

**With:**
```kotlin
// NEW:
private var unifiedFlightTracker: UnifiedFlightTracker? = null

// Initialize (after connecting)
unifiedFlightTracker = UnifiedFlightTracker(context, tlogViewModel, sharedViewModel)

// Cleanup
unifiedFlightTracker?.destroy()
```

### 3. Remove FlightManager.kt (Optional)

The old FlightManager can be deleted or kept as backup. It will no longer be used.

### 4. Clean Up Unused Variables in TelemetryRepository.kt

Remove these commented-out variables (lines ~106-114):
```kotlin
/*
private var manualFlightActive = false
private var manualFlightStartTime: Long = 0L
// ... etc
*/
```

## STEP-BY-STEP IMPLEMENTATION

### Step 1: Clean TelemetryRepository.kt

In the `GLOBAL_POSITION_INT` handler (~line 360), replace ALL the manual flight tracking code with:

```kotlin
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

            // Update state with position data only
            _state.update {
                it.copy(
                    altitudeMsl = altAMSLm,
                    altitudeRelative = relAltM,
                    latitude = lat,
                    longitude = lon
                )
            }
            
            // NOTE: Flight tracking removed - now handled by UnifiedFlightTracker
        }
}
```

### Step 2: Find MainActivity and Wire Up UnifiedFlightTracker

Search for where `FlightManager` is initialized (probably in `MainActivity.kt` or similar):

```bash
# Find the file
grep -r "FlightManager" --include="*.kt"
```

Replace the initialization with `UnifiedFlightTracker`.

### Step 3: Remove Unused Variables

Remove the commented-out variables at the top of TelemetryRepository (~line 106-114).

### Step 4: Test

1. Connect to drone
2. Arm and take off
3. Fly for a bit
4. Land and disarm
5. Check:
   - ✅ Exactly ONE "Mission started" notification
   - ✅ Exactly ONE "Flight completed!" notification with CORRECT time/distance
   - ✅ Time in telemetry bar updates correctly (no glitching)
   - ✅ Flight appears in logs with correct data

## VERIFICATION CHECKLIST

After implementation:

- [ ] Remove all manual flight tracking from TelemetryRepository.kt
- [ ] Wire up UnifiedFlightTracker in MainActivity
- [ ] Test flight: notifications show correct time/distance
- [ ] Test flight: no duplicate notifications
- [ ] Test flight: logs save correctly
- [ ] Test flight: time doesn't glitch
- [ ] Test AUTO mode flight (mission)
- [ ] Test MANUAL mode flight (no mission)

## FILES MODIFIED

1. ✅ **UnifiedFlightTracker.kt** - Created (single source of truth)
2. ✅ **SharedViewModel.kt** - Added `updateFlightState()` method
3. ⚠️ **TelemetryRepository.kt** - NEEDS CLEANUP (remove manual tracking)
4. ⚠️ **MainActivity.kt** - NEEDS UPDATE (wire up UnifiedFlightTracker)
5. ⚠️ **FlightManager.kt** - OBSOLETE (can be deleted after testing)

## EXPECTED BEHAVIOR AFTER FIX

### Normal Flight
1. Arm → Capture ground level
2. Altitude > ground + 1m for 1.5s → "Mission started" (once)
3. Flight in progress → Time and distance update in real-time
4. Land (altitude ≤ ground + 0.5m) for 2s → "Flight completed!" with correct time/distance (once)
5. Flight saved to database with correct metrics

### Notifications
- ✅ "Mission started" - exactly once at takeoff
- ✅ "Flight completed! Time: HH:MM:SS, Distance: X.Xm" - exactly once at landing
- ✅ Correct values in notification (not 00:00:00 or 0.0m)

### Telemetry Bar
- ✅ Time: Shows elapsed time during flight
- ✅ Time: No glitching between 0 and actual time
- ✅ Distance: Shows actual distance flown
- ✅ Completed state properly managed

### Database
- ✅ Flight logs save after each flight
- ✅ Correct start time, end time, duration
- ✅ Correct distance
- ✅ Correct mode (MANUAL or AUTO)

## WHY THIS FIXES ALL ISSUES

1. **Logs not saving** → Fixed: UnifiedFlightTracker directly calls tlogViewModel.startFlight() and endFlight() with correct timing

2. **Time/distance glitching** → Fixed: Single state machine manages missionElapsedSec and totalDistanceMeters, no conflicting updates

3. **Duplicate notifications** → Fixed: hasShownMissionCompleted flag prevents duplicates, proper state transitions

4. **Wrong values (00:00:00, 0.0m)** → Fixed: UnifiedFlightTracker calculates actual values before showing notification

## NEXT STEPS

1. **Manually apply the cleanup** to TelemetryRepository.kt (remove manual flight tracking code)
2. **Find MainActivity** and replace FlightManager with UnifiedFlightTracker
3. **Rebuild and test** thoroughly
4. **Compare before/after** logs to verify all issues resolved

If you need help with any specific step, let me know which file to edit next!

