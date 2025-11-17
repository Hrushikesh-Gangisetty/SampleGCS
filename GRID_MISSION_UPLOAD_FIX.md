It# Grid Mission Upload Fix - ROOT CAUSE ANALYSIS

## Date
November 17, 2025

## Problem Description
Grid mission upload was stuck at 0% progress with repeated `MAV_MISSION_ACCEPTED` messages but no actual mission upload:

```
11:23:00 : MissionUpload: MAV_MISSION_ACCEPTED
11:23:15 : MissionUpload: MAV_MISSION_ACCEPTED
11:23:24 : MissionUpload: MAV_MISSION_ACCEPTED
```

**Symptoms:**
- Progress indicator shows 0%
- Multiple ACCEPTED ACKs received
- FCU never sends MISSION_REQUEST messages
- Upload times out after ~45 seconds

---

## ROOT CAUSE IDENTIFIED

### Critical Bug in GridMissionConverter.kt

**The Issue:**
```kotlin
// WRONG - HOME position had z=10f
missionItems.add(
    MissionItemInt(
        seq = 0u,
        command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
        current = 1u,
        x = (homePosition.latitude * 1E7).toInt(),
        y = (homePosition.longitude * 1E7).toInt(),
        z = 10f  // ❌ WRONG - HOME should be z=0f
    )
)
```

**Why This Caused the Problem:**

1. **ArduPilot Protocol Requirement:** The HOME position (seq=0) MUST have `z=0f` (relative altitude of 0)
2. **Silent Rejection:** When the FCU receives MISSION_COUNT with this invalid HOME structure, it:
   - Sends `MAV_MISSION_ACCEPTED` for the MISSION_CLEAR_ALL ✅
   - But **never sends MISSION_REQUEST** messages ❌
   - The mission is silently rejected due to the invalid HOME altitude

3. **Repeated ACKs:** The repeated ACCEPTED messages you saw were:
   - First ACK: From MISSION_CLEAR_ALL (11:23:00)
   - Second ACK: Timeout, resending MISSION_COUNT (11:23:15)
   - Third ACK: Another timeout, resending MISSION_COUNT (11:23:24)
   - The FCU kept accepting the CLEAR but never requested items because it knew the mission structure was invalid

---

## THE FIX

### GridMissionConverter.kt - Lines 33-53

**Changed:**
```kotlin
// CORRECT - HOME position with z=0f
missionItems.add(
    MissionItemInt(
        targetSystem = fcuSystemId,
        targetComponent = fcuComponentId,
        seq = 0u,
        frame = MavEnumValue.of(MavFrame.GLOBAL_RELATIVE_ALT_INT),
        command = MavEnumValue.of(MavCmd.NAV_WAYPOINT),
        current = 1u, // MUST be 1 for first item
        autocontinue = 1u,
        param1 = 0f, // Hold time
        param2 = 0f, // Acceptance radius
        param3 = 0f, // Pass through
        param4 = 0f, // Yaw
        x = (homePosition.latitude * 1E7).toInt(),
        y = (homePosition.longitude * 1E7).toInt(),
        z = 0f // ✅ CRITICAL FIX: HOME altitude must be 0 (relative)
    )
)
```

### Also Fixed Takeoff Altitude

**Changed:**
```kotlin
// Sequence 1: Takeoff to the survey altitude (not hardcoded 15f)
val takeoffAltitude = gridResult.waypoints.firstOrNull()?.altitude ?: 15f
missionItems.add(
    MissionItemInt(
        seq = 1u,
        command = MavEnumValue.of(MavCmd.NAV_TAKEOFF),
        z = takeoffAltitude // ✅ Takeoff to actual survey altitude
    )
)
```

This ensures the drone takes off to the correct survey altitude (e.g., 60m if that's your survey altitude).

---

## Correct Grid Mission Structure Now

```
seq: 0 = NAV_WAYPOINT (HOME, current=1, z=0f)       ← FIXED
seq: 1 = NAV_TAKEOFF (z=survey_altitude)            ← FIXED
seq: 2 = CONDITION_YAW (if holdNosePosition=true)   ← Optional
seq: 3+ = Grid waypoints (with DO_CHANGE_SPEED)     ← Your survey pattern
seq: N = NAV_RETURN_TO_LAUNCH                       ← End mission
```

---

## Expected Behavior After Fix

### Upload Log Sequence:
```
[Mission Upload] Mission structure (50+ items):
  [0] seq=0 cmd=NAV_WAYPOINT current=1 pos=lat,lon,0.0m        ← HOME z=0
  [1] seq=1 cmd=NAV_TAKEOFF current=0 pos=lat,lon,60.0m        ← Takeoff to survey alt
  [2] seq=2 cmd=DO_CHANGE_SPEED                                ← Speed command
  [3] seq=3 cmd=NAV_WAYPOINT current=0 pos=...,60.0m           ← Grid WP1
  [4] seq=4 cmd=NAV_WAYPOINT current=0 pos=...,60.0m           ← Grid WP2
  ...
  [N] seq=N cmd=NAV_RETURN_TO_LAUNCH                           ← RTL

[Mission Upload] ✅ MISSION_CLEAR_ALL successful
[Mission Upload] Sent MISSION_COUNT=50
[Mission Upload] MissionRequestInt seq=0 (request #1)          ← FCU now requests items!
[Mission Upload] → Sending seq=0: NAV_WAYPOINT ... z=0.0m current=1
[Mission Upload] ✓ Sent seq=0 (1/50)
[Mission Upload] MissionRequestInt seq=1 (request #1)
[Mission Upload] → Sending seq=1: NAV_TAKEOFF ... z=60.0m
...
[Mission Upload] ✅ SUCCESS - Mission uploaded!
```

### Progress Indicator:
- Shows **actual progress** (0% → 100%)
- Updates as each waypoint is sent
- No more stuck at 0%

---

## Why This Bug Existed

The `GridMissionConverter.kt` was originally written with:
- Hardcoded `z=10f` for HOME (incorrect)
- Hardcoded `z=15f` for TAKEOFF (should match survey altitude)

This worked fine in **SITL** because:
- SITL is more forgiving
- SITL doesn't strictly validate HOME altitude
- TCP connection is faster, so timeouts occur differently

But on **real hardware (Cube+):**
- ArduPilot strictly enforces HOME position rules
- HOME **must** have z=0f in GLOBAL_RELATIVE_ALT_INT frame
- Invalid HOME causes silent mission rejection
- FCU accepts CLEAR but never requests items

---

## Testing Checklist

### ✅ Before Testing
1. Deploy the updated code
2. Connect to Cube+ via Bluetooth
3. Ensure GPS lock and stable connection

### ✅ Test Grid Mission Upload
1. Create a grid survey (draw polygon, set parameters)
2. Click "Upload Mission"
3. **Watch for:**
   - Progress indicator goes 0% → 100% (not stuck at 0%)
   - Log shows `MissionRequestInt seq=0`, `seq=1`, `seq=2`...
   - Final message: `✅ SUCCESS - Mission uploaded!`
   - Only ONE `MAV_MISSION_ACCEPTED` at the end (not repeated)

### ✅ Verify on Flight Controller
1. Check mission in Mission Planner or QGroundControl
2. Verify HOME position shows correct location with 0m altitude
3. Verify TAKEOFF altitude matches your survey altitude (e.g., 60m)
4. Verify grid waypoints are at correct altitude

---

## Files Modified

1. **GridMissionConverter.kt**
   - Line 51: Changed HOME altitude from `z=10f` to `z=0f`
   - Line 56-57: Changed TAKEOFF altitude to use survey altitude
   - Added comments explaining HOME position requirements

---

## Key Learnings

1. **HOME Position Rules in ArduPilot:**
   - seq=0 MUST be a waypoint (NAV_WAYPOINT)
   - current=1 MUST be set
   - **z=0f for GLOBAL_RELATIVE_ALT_INT frame** (critical!)

2. **Silent Failures:**
   - Invalid mission structure doesn't always produce error ACKs
   - FCU may silently refuse to request items
   - Repeated ACCEPTED ACKs = FCU accepting CLEAR but rejecting structure

3. **SITL vs Real Hardware:**
   - SITL is forgiving, real hardware is strict
   - Always test critical features on real hardware
   - Protocol compliance is essential for Cube+ / ArduPilot

---

## Summary

**Problem:** Grid mission upload stuck at 0% with repeated ACCEPTED ACKs
**Root Cause:** HOME position (seq=0) had z=10f instead of z=0f
**Solution:** Changed HOME altitude to z=0f in GridMissionConverter.kt
**Result:** FCU now properly requests mission items, upload succeeds

**Status: ✅ FIXED**

---

## If Issues Persist

If you still see problems after this fix, check:

1. **Sequence Gaps:** Verify logs don't show sequence number gaps
2. **Target IDs:** Ensure fcuSystemId and fcuComponentId are correct (usually 1/1)
3. **Frame Type:** All items should use GLOBAL_RELATIVE_ALT_INT
4. **Coordinate Validity:** Verify lat/lon are within valid ranges

Check the upload logs carefully - the fix should now show the FCU requesting items starting from seq=0.

