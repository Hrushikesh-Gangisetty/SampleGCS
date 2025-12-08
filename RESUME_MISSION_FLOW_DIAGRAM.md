# Resume Mission - Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RESUME MISSION FLOW DIAGRAM                         │
└─────────────────────────────────────────────────────────────────────────────┘

USER INTERACTION                    SYSTEM PROCESSING                   FC COMMUNICATION
═══════════════════                ═════════════════════              ══════════════════

    [Resume Button]
         ▼
┌─────────────────────┐
│  Warning Dialog     │
│ "This will reprogram│
│  your mission..."   │
│  [Cancel] [Continue]│
└─────────────────────┘
         ▼ Continue
┌─────────────────────┐
│ Waypoint Selection  │
│ "Resume at wp: [5]" │
│  [Cancel] [Resume]  │
└─────────────────────┘
         ▼ Resume
┌─────────────────────┐
│  Progress Dialog    │          ┌─────────────────────────┐
│ "Step 1/10..."      │────────► │ Step 1: Pre-flight      │
│                     │          │ - Check connection      │
│ [⟳ Loading...]      │          │ - Get last waypoint     │
└─────────────────────┘          └─────────────────────────┘
         │                                    ▼
         │                       ┌─────────────────────────┐         ┌────────────┐
         │                       │ Step 3: Get Mission     │────────►│ MISSION_   │
         │                       │ - Request count         │         │ REQUEST_   │
         │                       │ - Request waypoints     │◄────────│ LIST       │
         │                       │ - Store all items       │         └────────────┘
         │                       └─────────────────────────┘                │
         │                                    ▼                             ▼
         │                       ┌─────────────────────────┐         ┌────────────┐
         │                       │ Step 4: Filter Mission  │         │ MISSION_   │
         │                       │ ┌────────────────────┐  │         │ COUNT: 10  │
         │                       │ │ Original Mission:  │  │         └────────────┘
         │                       │ │ 0. HOME            │  │                │
         │                       │ │ 1. TAKEOFF         │  │                ▼
         │                       │ │ 2. DO_SPEED        │  │         ┌────────────┐
         │                       │ │ 3. WAYPOINT        │  │    ┌───►│ MISSION_   │
         │                       │ │ 4. DO_SERVO        │  │    │    │ ITEM_INT   │
         │                       │ │ 5. WAYPOINT ◄──┐   │  │    │    │ seq=0..9   │
         │                       │ │ 6. WAYPOINT    │   │  │    │    └────────────┘
         │                       │ └────────────────┴───┘  │    │
         │                       │          Resume at 5    │    │
         │                       │ ┌────────────────────┐  │    │
         │                       │ │ Filtered Mission:  │  │    │
         │                       │ │ 0. HOME      (keep)│  │    │
         │                       │ │ 2. DO_SPEED  (keep)│  │    │
         │                       │ │ 4. DO_SERVO  (keep)│  │    └─── Multiple
         │                       │ │ 5. WAYPOINT  (keep)│  │          Requests
         │                       │ │ 6. WAYPOINT  (keep)│  │
         │                       │ └────────────────────┘  │
         │                       │    ↓ Re-sequence        │
         │                       │ ┌────────────────────┐  │
         │                       │ │ Final Mission:     │  │
         │                       │ │ 0. HOME            │  │
         │                       │ │ 1. DO_SPEED        │  │
         │                       │ │ 2. DO_SERVO        │  │
         │                       │ │ 3. WAYPOINT        │  │
         │                       │ │ 4. WAYPOINT        │  │
         │                       │ └────────────────────┘  │
         │                       └─────────────────────────┘
         │                                    ▼
         │                       ┌─────────────────────────┐         ┌────────────┐
         │                       │ Step 5: Upload Mission  │────────►│ MISSION_   │
         │                       │ - Clear existing        │         │ CLEAR_ALL  │
         │                       │ - Send count            │────────►│ MISSION_   │
         ���                       │ - Send items            │         │ COUNT: 5   │
         │                       │ - Wait for ACK          │◄────────│ MISSION_   │
         │                       └─────────────────────────┘         │ ACK        │
         │                                    ▼                       └────────────┘
         │                       ┌─────────────────────────┐
         │                       │ Step 6: Verify Upload   │
         │                       │ - Optional readback     │
         │                       └─────────────────────────┘
         │                                    ▼
         │                       ┌──────────────────��──────┐         ┌────────────┐
         │                       │ Step 7: Set Waypoint    │────────►│ COMMAND_   │
         │                       │ - Set current to 1      │         │ LONG       │
         │                       │   (HOME)                │         │ DO_SET_    │
         │                       └─────────────────────────┘         │ MISSION_   │
         │                                    ▼                       │ CURRENT    │
         │                       ┌─────────────────────────┐         └────────────┘
         │                       │ Step 8: Copter Takeoff  │
         │                       │ ┌─────────────────────┐ │         ┌────────────┐
         │                       │ │ 8a. GUIDED Mode     │ │────────►│ COMMAND_   │
         │                       │ │ - Send mode command │ │         │ LONG       │
         │                       │ │ - Wait for confirm  │ │◄────────│ DO_SET_    │
         │                       │ └─────────────────────┘ │         │ MODE       │
         │                       │          ▼              │         └────────────┘
         │                       │ ┌─────────────────────┐ │                │
         │                       │ │ 8b. ARM Vehicle     │ │                ▼
         │                       │ │ - Send arm command  │ │         ┌────────────┐
         │                       │ │ - Wait for armed    │ │────────►│ COMMAND_   │
         │                       │ └─────────────────────┘ │         │ LONG       │
         │                       │          ▼              │         │ COMPONENT_ │
         │                       │ ┌─────────────────────┐ │◄────────│ ARM_       │
         │                       │ │ 8c. TAKEOFF         │ │         │ DISARM     │
         │                       │ │ - Send takeoff cmd  │ │         └────────────┘
         │                       │ │ - Wait for altitude │ │                │
         │                       │ └─────────────────────┘ │                ▼
         │                       └─────────────────────────┘         ┌────────────┐
         │                                    ▼                       │ COMMAND_   │
         │                       ┌─────────────────────────┐         │ LONG       │
         │                       │ Step 9: AUTO Mode       │────────►│ NAV_       │
         │                       │ - Send mode command     │         │ TAKEOFF    │
         │                       │ - Wait for confirm      │◄────────│            │
         │                       └─────────────────────────┘         └────────────┘
         │                                    ▼                              │
         │                       ┌─────────────────────────┐                ▼
         │                       │ Step 10: Complete       │         ┌────────────┐
         │                       │ - Update state          │         │ HEARTBEAT  │
         │                       │ - Show notification     │◄────────│ mode=AUTO  │
         │                       │ - Announce via TTS      │         │ armed=true │
         │                       └─────────────────────────┘         └────────────┘
         ▼                                    ▼
┌─────────────────────┐          ┌─────────────────────────┐
│  Success!           │          │ Mission Executes        │         ┌────────────┐
│ "Mission resumed    │          │ - Waypoint 0 (HOME)     │         │ MISSION_   │
│  from waypoint 5"   │          │ - Waypoint 1 (DO_SPEED) │────────►│ CURRENT    │
│                     │          │ - Waypoint 2 (DO_SERVO) │         │ Updates    │
│ [Dialog Closes]     │          │ - Waypoint 3 (TARGET)   │         │ 0→1→2→3... │
└─────────────────────┘          │ - ...continues...       │         └────────────┘
                                 └─────────────────────────┘


═══════════════════════════════════════════════════════════════════════════════

TIMELINE (Typical):

0s     ─┬─ User clicks Resume button
       │
1s     ├─ Warning dialog shown
       │
3s     ├─ User clicks Continue
       │
4s     ├─ Waypoint dialog shown
       │
6s     ├─ User clicks Resume
       │
7s     ├─ Progress dialog: "Step 1/10..."
       │
8s     ├─ Progress: "Step 3/10: Retrieving mission..." 
       │  ↓ MISSION_REQUEST_LIST sent
10s    ├─ MISSION_COUNT received (10 items)
       │  ↓ Request waypoints 0-9
15s    ├─ All waypoints received
       │
16s    ├─ Progress: "Step 4/10: Filtering..."
       │  ↓ Filter: Keep HOME, DO commands, waypoints ≥5
17s    ├─ Filtered to 5 items, re-sequenced to 0-4
       │
18s    ├─ Progress: "Step 5/10: Uploading..."
       │  ↓ MISSION_CLEAR_ALL sent
20s    ├─ Clear ACK received
       │  ↓ MISSION_COUNT=5 sent
21s    ├─ MISSION_REQUEST received for seq 0
       │  ↓ Send MISSION_ITEM_INT for each waypoint
25s    ├─ MISSION_ACK received (ACCEPTED)
       │
26s    ├─ Progress: "Step 7/10: Setting waypoint..."
       │  ↓ SET_MISSION_CURRENT(1) sent
       │
28s    ├─ Progress: "Step 8a/10: GUIDED mode..."
       │  ↓ DO_SET_MODE(GUIDED) sent
30s    ├─ HEARTBEAT confirms mode=GUIDED
       │
31s    ├─ Progress: "Step 8b/10: Arming..."
       │  ↓ ARM command sent
33s    ├─ HEARTBEAT confirms armed=true
       │
34s    ├─ Notification: "Vehicle armed - taking off..."
       │
35s    ├─ Progress: "Step 8c/10: Taking off to 50m..."
       │  ↓ TAKEOFF command sent repeatedly
45s    ├─ Altitude reached (50m)
       │
46s    ├─ Notification: "Takeoff complete - switching to AUTO"
       │
47s    ├─ Progress: "Step 9/10: AUTO mode..."
       │  ↓ DO_SET_MODE(AUTO) sent
49s    ├─ HEARTBEAT confirms mode=AUTO
       │
50s    ├─ Progress: "Step 10/10: Complete!"
       │
51s    ├─ Dialog closes
       │  ↓ Notification: "Mission resumed from waypoint 5"
       │
52s+   └─ Mission executes from waypoint 5 onward

═══════════════════════════════════════════════════════════════════════════════

LEGEND:
───────  User action
═══════  System processing
- - - -  MAVLink message
  ▼      Flow direction
  ◄────  Response/feedback
  ┌──┐   Dialog box
  │  │   Process block
  
═══════════════════════════════════════════════════════════════════════════════
```

## Key Points Illustrated

### 1. Three-Phase User Interaction
- Phase 1: Warning (safety confirmation)
- Phase 2: Waypoint selection (user customization)
- Phase 3: Progress feedback (transparency)

### 2. Mission Filtering Logic
```
Original (10 waypoints) → Filtered (5 waypoints)
- Keep: HOME (seq 0)
- Keep: DO commands before resume point
- Skip: NAV commands before resume point
- Keep: All waypoints from resume point onward
```

### 3. Copter Takeoff Sequence
```
LOITER/LAND → GUIDED → ARM → TAKEOFF → AUTO
```

### 4. Timing Expectations
- Fast: 15-25 seconds (small mission, good connection)
- Typical: 30-45 seconds (medium mission, normal connection)
- Slow: 45-60 seconds (large mission, slow connection)

### 5. MAVLink Message Flow
```
GCS → FC: MISSION_REQUEST_LIST
GCS ← FC: MISSION_COUNT
GCS → FC: MISSION_REQUEST_INT (x10)
GCS ← FC: MISSION_ITEM_INT (x10)
GCS → FC: MISSION_CLEAR_ALL
GCS ← FC: MISSION_ACK
GCS → FC: MISSION_COUNT
GCS ← FC: MISSION_REQUEST (x5)
GCS → FC: MISSION_ITEM_INT (x5)
GCS ← FC: MISSION_ACK
GCS → FC: COMMAND_LONG (SET_CURRENT)
GCS → FC: COMMAND_LONG (DO_SET_MODE)
GCS ← FC: HEARTBEAT (mode=GUIDED)
GCS → FC: COMMAND_LONG (ARM)
GCS ← FC: HEARTBEAT (armed=true)
GCS → FC: COMMAND_LONG (TAKEOFF)
GCS ← FC: GLOBAL_POSITION_INT (altitude updates)
GCS → FC: COMMAND_LONG (DO_SET_MODE)
GCS ← FC: HEARTBEAT (mode=AUTO)
```

This visualization helps understand the complete flow from user interaction through system processing to flight controller communication!

