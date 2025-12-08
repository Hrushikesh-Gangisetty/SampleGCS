# Resume Mission - Quick Testing Guide

## How to Test

### Prerequisites
1. ✅ Connected to flight controller (real or SITL)
2. ✅ Mission uploaded to FC
3. ✅ Mission started and paused (or know which waypoint to resume from)

### Test Flow

#### 1. Simple Resume Test
**Steps:**
1. Start a mission in AUTO mode
2. Click **Pause** button (switches to LOITER)
3. Wait for pause confirmation
4. Click **Resume** button
5. **Warning Dialog** appears
6. Click **Continue**
7. **Waypoint Selection Dialog** appears with default waypoint number
8. Click **Resume** (or modify waypoint number first)
9. **Progress Dialog** shows step-by-step progress
10. Mission resumes automatically

**Expected Result:**
- Mission uploads successfully
- Copter arms and takes off
- Mode switches to AUTO
- Mission continues from resume waypoint

#### 2. Custom Waypoint Resume Test
**Steps:**
1. Click **Resume** button
2. In Waypoint Selection Dialog, change waypoint number (e.g., from 3 to 5)
3. Click **Resume**
4. Observe progress

**Expected Result:**
- Mission skips waypoints 1-4 (except HOME and DO commands)
- Resumes from waypoint 5

#### 3. DO Command Preservation Test
**Setup:** Create a mission with:
- Waypoint 0: HOME
- Waypoint 1: TAKEOFF
- Waypoint 2: DO_CHANGE_SPEED (set speed to 5 m/s)
- Waypoint 3: NAV_WAYPOINT
- Waypoint 4: DO_SET_SERVO (servo action)
- Waypoint 5: NAV_WAYPOINT
- Waypoint 6: NAV_WAYPOINT

**Test:** Resume from waypoint 5

**Expected Result:**
Filtered mission should contain:
- Seq 0: HOME (original wp 0)
- Seq 1: DO_CHANGE_SPEED (original wp 2) ✅ PRESERVED
- Seq 2: DO_SET_SERVO (original wp 4) ✅ PRESERVED
- Seq 3: NAV_WAYPOINT (original wp 5)
- Seq 4: NAV_WAYPOINT (original wp 6)

**Waypoints 1, 3 SKIPPED (NAV commands before resume point)**

### Verify in Logs

#### Enable Logging
Check Android Logcat for tags:
- `ResumeMission` - All resume mission steps
- `MissionUpload` - Mission upload process
- `MavlinkRepo` - Low-level MAVLink messages

#### Key Log Messages

**Step 1-3: Mission Retrieval**
```
ResumeMission: Starting Resume Mission Protocol
ResumeMission: Resume at waypoint: 5
ResumeMission: Sent MISSION_REQUEST_LIST
ResumeMission: Received MISSION_COUNT=10
ResumeMission: Retrieving 10 waypoints from FC
ResumeMission: Successfully retrieved 10 waypoints
```

**Step 4: Filtering**
```
ResumeMission: Keeping HOME waypoint (seq=0)
ResumeMission: Keeping DO command before resume (seq=2, cmd=178)
ResumeMission: Skipping NAV command before resume (seq=3, cmd=16)
ResumeMission: Keeping DO command before resume (seq=4, cmd=183)
ResumeMission: Keeping waypoint from resume point (seq=5, cmd=16)
ResumeMission: Filtered 10 waypoints to 5 for resume at wp 5
```

**Step 5: Upload**
```
MissionUpload: Starting upload: 5 items
MissionUpload: Sent MISSION_COUNT=5, awaiting MISSION_REQUEST...
MissionUpload: ✅ First MISSION_REQUEST received - upload starting
MissionUpload: → Sent seq=0: MAV_CMD_NAV_WAYPOINT
MissionUpload: → Sent seq=1: MAV_CMD_DO_CHANGE_SPEED
MissionUpload: ✅ SUCCESS - Mission uploaded!
```

**Step 8: Copter Takeoff**
```
ResumeMission: Copter detected - starting takeoff sequence
ResumeMission: ✅ Switched to GUIDED mode
ResumeMission: ✅ Vehicle armed
ResumeMission: Sent TAKEOFF command to altitude 50.0
ResumeMission: ✅ Takeoff altitude reached
```

**Step 9-10: AUTO Mode**
```
ResumeMission: ✅ Switched to AUTO mode
ResumeMission: ✅ Resume Mission Complete!
```

### UI Verification

#### Progress Dialog Messages
You should see these messages in sequence:
1. "Step 1/10: Pre-flight checks..."
2. "Step 3/10: Retrieving mission from FC..."
3. "Step 4/10: Filtering waypoints..."
4. "Step 5/10: Uploading modified mission..."
5. "Step 6/10: Verifying upload..."
6. "Step 7/10: Setting current waypoint..."
7. "Step 8a/10: Switching to GUIDED mode..."
8. "Step 8b/10: Arming vehicle..."
9. "Step 8c/10: Taking off to [X]m..."
10. "Step 9/10: Switching to AUTO mode..."
11. "Step 10/10: Mission resumed!"

#### Notifications
- "Vehicle armed - taking off..."
- "Takeoff complete - switching to AUTO"
- "Mission resumed from waypoint [X]"

### Common Issues & Solutions

#### Issue 1: "Failed to retrieve mission from FC"
**Cause:** No mission on FC or connection problem
**Solution:** Upload a mission first, verify connection

#### Issue 2: "Failed to upload modified mission"
**Cause:** Invalid waypoint data or FC rejection
**Solution:** Check logs for specific error, verify waypoint coordinates

#### Issue 3: "Failed to switch to GUIDED mode"
**Cause:** Vehicle not armable or mode not supported
**Solution:** Check pre-arm status, ensure GPS lock

#### Issue 4: "Failed to arm vehicle"
**Cause:** Pre-arm checks failing
**Solution:** Check STATUSTEXT messages, resolve pre-arm errors

#### Issue 5: Takeoff altitude not reached
**Cause:** Slow climb rate or incorrect altitude reading
**Solution:** Check altitude sensor, increase timeout, verify target altitude

### SITL Testing

#### Start ArduCopter SITL
```bash
sim_vehicle.py -v ArduCopter --console --map
```

#### Upload Test Mission
```
wp load ../missions/test_mission.txt
```

#### Start Mission
```
mode AUTO
arm throttle
```

#### Pause and Resume
1. Switch to GCS app
2. Click Pause (switches to LOITER)
3. Click Resume
4. Follow dialog flow

### Real Hardware Testing

#### Safety Checklist
- ✅ Clear flight area
- ✅ Props removed (for initial test)
- ✅ Battery charged
- ✅ GPS lock acquired
- ✅ Compass calibrated
- ✅ RC failsafe configured
- ✅ Observer present

#### Test Sequence
1. **Bench Test** (props off)
   - Upload mission
   - Test resume dialogs
   - Verify arming
   - Cancel before takeoff

2. **Tethered Test** (with props, tethered)
   - Upload mission
   - Test resume with takeoff
   - Verify altitude hold
   - Kill switch ready

3. **Flight Test**
   - Start mission normally
   - Pause at safe altitude
   - Resume from current waypoint
   - Monitor closely

### Debugging Tips

#### Enable Verbose Logging
Add to TelemetryRepository.kt:
```kotlin
Log.setLevel(Log.VERBOSE)
```

#### Monitor MAVLink Messages
Use MAVLink Inspector in QGroundControl or Mission Planner to see raw messages:
- MISSION_REQUEST_LIST
- MISSION_COUNT
- MISSION_REQUEST_INT
- MISSION_ITEM_INT
- MISSION_ACK
- COMMAND_LONG
- HEARTBEAT

#### Check Waypoint Sequences
After filtering, verify sequences are continuous (0, 1, 2, ...) with no gaps.

#### Verify DO Commands
Check that all DO commands before resume point are included in filtered mission.

### Performance Expectations

#### Timing
- Mission retrieval: 1-5 seconds (depends on mission size)
- Mission upload: 2-10 seconds (depends on mission size)
- Mode changes: 1-2 seconds each
- Arming: 1-2 seconds
- Takeoff: 5-20 seconds (depends on altitude)
- **Total time: 15-45 seconds typically**

#### Network Requirements
- Latency: < 100ms for best results
- Packet loss: < 5% acceptable
- Bandwidth: Low (MAVLink messages are small)

### Success Criteria

✅ **Mission Retrieved:** All waypoints downloaded from FC  
✅ **Mission Filtered:** DO commands preserved, NAV commands skipped  
✅ **Mission Uploaded:** MISSION_ACK = ACCEPTED received  
✅ **Waypoint Set:** Current waypoint = 1 (HOME)  
✅ **Mode Changes:** GUIDED → AUTO transitions successful  
✅ **Arming:** Vehicle armed within timeout  
✅ **Takeoff:** Target altitude reached (copters only)  
✅ **Execution:** Mission continues from resume waypoint  
✅ **No Errors:** No timeout or rejection errors  

### Troubleshooting Commands

#### Check Current Mode
```
mode
```

#### Check Armed Status
```
arm check
```

#### Check GPS Status
```
gps status
```

#### Check Current Waypoint
```
wp show
```

#### View Mission
```
wp list
```

## Summary

The Resume Mission feature implements the complete Mission Planner protocol with:
- ✅ User confirmations (3 dialogs)
- ✅ Mission retrieval from FC
- ✅ DO command preservation
- ✅ Waypoint filtering and re-sequencing
- ✅ Mission upload
- ✅ Copter takeoff sequence
- ✅ Mode management
- ✅ Progress feedback
- ✅ Error handling

**Test thoroughly in SITL before real hardware testing!**

