# ✅ CORRECT Mission Planner Resume Protocol - IMPLEMENTED

## What Was Actually Needed

Your clarification revealed the **actual Mission Planner protocol**, which is different from both previous implementations.

## Mission Planner C# Code (The Truth)

```csharp
for (ushort a = 0; a < wpcount; a++) {
    var wpdata = MainV2.comPort.getWP(a);
    
    if (a < lastwpno && a != 0) // allow home
    {
        if (wpdata.id != (ushort) MAVLink.MAV_CMD.TAKEOFF)
            if (wpdata.id < (ushort) MAVLink.MAV_CMD.LAST)
                continue; // SKIP THIS WAYPOINT
    }
    
    cmds.Add(wpdata);
}
```

## Translation to Plain English

**FOR each waypoint:**
1. **IF waypoint is before resume point AND not HOME:**
   - **IF NOT TAKEOFF command:**
     - **IF command ID < 176 (NAV commands):**
       - **SKIP** this waypoint
   - **Otherwise KEEP** (TAKEOFF or DO commands)
2. **OTHERWISE KEEP** (HOME or waypoints from resume point onward)

## What Gets Kept

| Waypoint Type | Before Resume | At/After Resume |
|---------------|---------------|-----------------|
| HOME (seq 0) | ✅ KEEP | ✅ KEEP |
| TAKEOFF | ✅ KEEP | ✅ KEEP |
| DO Commands (cmd >= 176) | ✅ KEEP | ✅ KEEP |
| NAV Commands (cmd < 176) | ❌ SKIP | ✅ KEEP |

## Implementation

### Our Kotlin Code (Exact Match)

```kotlin
fun filterWaypointsForResume(allWaypoints: List<MissionItemInt>, resumeWaypointSeq: Int): List<MissionItemInt> {
    val filtered = mutableListOf<MissionItemInt>()
    
    val MAV_CMD_NAV_TAKEOFF = 22u
    val MAV_CMD_LAST = 176u // Commands >= 176 are DO commands
    
    for (waypoint in allWaypoints) {
        val seq = waypoint.seq.toInt()
        val cmdId = waypoint.command.value
        
        // Mission Planner: if (a < lastwpno && a != 0)
        if (seq < resumeWaypointSeq && seq != 0) {
            // Keep TAKEOFF: if (wpdata.id != TAKEOFF)
            if (cmdId == MAV_CMD_NAV_TAKEOFF) {
                filtered.add(waypoint)
                continue
            }
            
            // Keep DO commands: if (wpdata.id < MAV_CMD.LAST) continue;
            if (cmdId >= MAV_CMD_LAST) {
                filtered.add(waypoint)
                continue
            }
            
            // Skip NAV waypoints
            continue
        }
        
        // Keep HOME and everything from resume point onward
        filtered.add(waypoint)
    }
    
    return filtered
}
```

## Example: 10-Waypoint Mission

### Original Mission
```
0: HOME (lat=10, lon=20, alt=0)
1: TAKEOFF (alt=50)
2: DO_CHANGE_SPEED (5 m/s)
3: WP1 (lat=11, lon=21, alt=50)
4: DO_SET_SERVO (servo action)
5: WP2 (lat=12, lon=22, alt=50)
6: WP3 (lat=13, lon=23, alt=50) ← PAUSE HERE
7: WP4 (lat=14, lon=24, alt=50)
8: DO_CHANGE_SPEED (10 m/s)
9: WP5 (lat=15, lon=25, alt=50)
10: RTL
```

### Resume from Waypoint 6

**Filtering Process:**
- Seq 0 (HOME): Keep ✅ (always keep HOME)
- Seq 1 (TAKEOFF): Keep ✅ (TAKEOFF command)
- Seq 2 (DO_CHANGE_SPEED): Keep ✅ (DO command, cmd >= 176)
- Seq 3 (WP1): Skip ❌ (NAV waypoint before resume)
- Seq 4 (DO_SET_SERVO): Keep ✅ (DO command, cmd >= 176)
- Seq 5 (WP2): Skip ❌ (NAV waypoint before resume)
- Seq 6 (WP3): Keep ✅ (resume point)
- Seq 7-10: Keep ✅ (all after resume)

**Filtered Mission:**
```
0: HOME
1: TAKEOFF
2: DO_CHANGE_SPEED (5 m/s)
3: DO_SET_SERVO
4: WP3 (resume point)
5: WP4
6: DO_CHANGE_SPEED (10 m/s)
7: WP5
8: RTL
```

**After Re-sequence:**
```
0: HOME
1: TAKEOFF
2: DO_CHANGE_SPEED (5 m/s)
3: DO_SET_SERVO
4: WP3 (resume point) ← Set as current waypoint
5: WP4
6: DO_CHANGE_SPEED (10 m/s)
7: WP5
8: RTL
```

**Mission execution starts at seq 4 (WP3)**

## Why This Makes Sense

### Keep TAKEOFF
- Copters need TAKEOFF command to know altitude
- Even if resuming mid-mission, TAKEOFF defines flight parameters
- Critical for copter flight profile

### Keep DO Commands
- DO_CHANGE_SPEED affects entire mission behavior
- DO_SET_SERVO might be needed for payload
- DO_SET_CAM_TRIGG affects camera throughout
- These are **persistent settings**, not one-time waypoints

### Skip NAV Waypoints
- WP1, WP2 already visited
- Don't fly back to them
- Save time and battery

### Keep HOME
- RTL destination
- Reference point for relative commands
- Critical for safety

## Mission Planner Resume Steps

After filtering and re-sequencing:

1. **Upload filtered mission** to vehicle
2. **Set current waypoint to 1** (first waypoint after HOME)
   - In our example: This would be TAKEOFF (seq 1)
   - **WAIT!** This seems wrong for our case...

### ⚠️ Important Clarification Needed

According to your example:
> "Current waypoint is set to 1 (which is the original WP3)"

But in Mission Planner code with DO commands, the filtered mission would be:
```
0: HOME
1: TAKEOFF
2: DO_CHANGE_SPEED
3: DO_SET_SERVO
4: WP3 (original resume point)
```

So setting current waypoint to 1 would start at TAKEOFF, not WP3!

### The Solution

Mission Planner likely sets current waypoint to the **first NAV waypoint after HOME**, not just waypoint 1.

So the algorithm should be:
1. Filter mission (keep HOME, TAKEOFF, DO commands, skip NAV before resume)
2. Re-sequence (0, 1, 2, ...)
3. **Find first NAV waypoint after HOME**
4. **Set that as current**

In our example:
- Seq 0: HOME
- Seq 1: TAKEOFF (not a destination)
- Seq 2: DO_CHANGE_SPEED (not a destination)
- Seq 3: DO_SET_SERVO (not a destination)
- Seq 4: WP3 ← **FIRST NAV WAYPOINT**, set as current!

## Updated Understanding

The resume process should:
1. ✅ Filter mission (DONE)
2. ✅ Re-sequence (DONE)
3. ❓ Find first NAV waypoint after HOME
4. ❓ Set that as current (not always waypoint 1)

## Current Implementation Status

✅ **Filtering Logic:** Correct (matches Mission Planner C#)  
✅ **Re-sequencing:** Correct  
❌ **Set Current Waypoint:** Sets to 1, should set to first NAV waypoint  

## Files Modified

**TelemetryRepository.kt** - `filterWaypointsForResume()`
- Added TAKEOFF preservation (cmd == 22)
- Kept DO command preservation (cmd >= 176)
- Kept HOME preservation (seq == 0)
- Skip NAV waypoints before resume

## Next Step Required

We need to update SharedViewModel to:
1. After re-sequencing, find the first NAV waypoint (not TAKEOFF, not DO command)
2. Set that as the current waypoint
3. NOT just set waypoint 1

Should I implement this fix now?

---

**Status:** ✅ Filtering logic correct  
**Pending:** Set current waypoint to first NAV waypoint logic  
**Date:** December 5, 2025

