# IMU Calibration - MissionPlanner Protocol Implementation

## Summary
Successfully restructured the IMU (accelerometer) calibration to replicate the MissionPlanner protocol flow.

## Key Changes Made

### 1. TelemetryRepository.kt
- **Added COMMAND_LONG flow**: Added `_commandLong` SharedFlow to capture incoming COMMAND_LONG messages from the FC
- **Added COMMAND_LONG collector**: Added a message collector that listens for incoming COMMAND_LONG messages (especially for `MAV_CMD_ACCELCAL_VEHICLE_POS`)
- This allows the GCS to receive position requests from the flight controller

### 2. SharedViewModel.kt
- **Exposed commandLong flow**: Added `commandLong` property to expose the COMMAND_LONG SharedFlow to ViewModels
- This enables the CalibrationViewModel to subscribe to incoming COMMAND_LONG messages

### 3. CalibrationState.kt
- **Updated AccelCalibrationPosition enum**: 
  - Changed `paramValue` from Float to Int to match ArduPilot's ACCELCAL_VEHICLE_POS enum values (1-6)
  - Added `fromParamValue()` method to convert param1 value from COMMAND_LONG to position enum
- **Added buttonText to CalibrationUiState**: To support dynamic button text changes ("Start Calibration" → "Click when Done")

### 4. CalibrationViewModel.kt - Complete Rewrite
**New Protocol Flow**:

#### Initial State:
- Button shows: "Start Calibration"
- `_inCalibration = false`

#### Step 1: User Clicks "Start IMU Calibration"
- Sends `MAV_CMD_PREFLIGHT_CALIBRATION` with `param5 = 1` (triggers accelerometer calibration)
- Sets `_inCalibration = true`
- Button text changes to: "Click when Done"
- Subscribes to:
  - STATUSTEXT messages (for position prompts and completion status)
  - COMMAND_LONG messages (for ACCELCAL_VEHICLE_POS position requests)

#### Step 2: FC Responds with Position Request
Two possible message types from FC:

**Option A: STATUSTEXT**
```
STATUSTEXT {
  text: "Place vehicle level and press any key"
}
```

**Option B: COMMAND_LONG (preferred)**
```
COMMAND_LONG {
  command: MAV_CMD_ACCELCAL_VEHICLE_POS (42429),
  param1: 1  // LEVEL position
}
```

The ViewModel:
- Extracts position from `param1` (or parses STATUSTEXT)
- Updates UI with position instruction
- Stores position in `currentPosition`
- Keeps button text as "Click when Done"

#### Step 3: User Places Drone and Clicks "Click when Done"
- Sends `MAV_CMD_ACCELCAL_VEHICLE_POS` with `param1 = currentPosition.paramValue`
- Increments counter (`count++`)
- Waits for next position request from FC

#### Step 4: Repeat for All 6 Positions
- LEVEL (1)
- LEFT (2)
- RIGHT (3)
- NOSEDOWN (4)
- NOSEUP (5)
- BACK (6)

#### Step 5: Calibration Complete
FC sends STATUSTEXT:
```
"Calibration successful"  or  "Calibration failed"
```

The ViewModel:
- Sets calibration state to Success/Failed
- Button text returns to: "Start Calibration"
- Unsubscribes message listeners
- Resets `_inCalibration = false`

## MAVLink Messages Used

| Direction | Message Type | Command | Parameters | Description |
|-----------|-------------|---------|------------|-------------|
| GCS → FC | COMMAND_LONG | MAV_CMD_PREFLIGHT_CALIBRATION (241) | param5=1 | Start accelerometer calibration |
| FC → GCS | STATUSTEXT | — | text="Place vehicle..." | Position instruction |
| FC → GCS | COMMAND_LONG | MAV_CMD_ACCELCAL_VEHICLE_POS (42429) | param1=position | Request specific position |
| GCS → FC | COMMAND_LONG | MAV_CMD_ACCELCAL_VEHICLE_POS (42429) | param1=position | Confirm position ready |
| FC → GCS | STATUSTEXT | — | text="Calibration successful" | Completion status |

## Complete Protocol Flow Diagram

```
[User Clicks "Start IMU Calibration"]
              ↓
Send COMMAND_LONG: MAV_CMD_PREFLIGHT_CALIBRATION (param5=1)
              ↓
[Flight Controller Starts Calibration]
              ↓
FC sends STATUSTEXT: "Place vehicle level"
  OR FC sends COMMAND_LONG: ACCELCAL_VEHICLE_POS (param1=1)
              ↓
[GUI Updates: "Please place vehicle LEVEL"]
[Button: "Click when Done"]
              ↓
[User places drone and clicks "Click when Done"]
              ↓
Send COMMAND_LONG: ACCELCAL_VEHICLE_POS (param1=1)
              ↓
FC continues to next position (RIGHT, LEFT, etc.)
              ↓
... Repeat for all 6 positions ...
              ↓
FC sends STATUSTEXT: "Calibration successful"
              ↓
[GUI Shows Success, Button: "Start Calibration"]
              ↓
Unsubscribe listeners, reset state
```

## Key Differences from Previous Implementation

| Aspect | Previous (COMMAND_ACK Protocol) | New (MissionPlanner Protocol) |
|--------|--------------------------------|------------------------------|
| Position Notification | GCS sends COMMAND_ACK to FC | GCS sends ACCELCAL_VEHICLE_POS to FC |
| Position Request | Parsed from STATUSTEXT only | COMMAND_LONG or STATUSTEXT |
| Button Behavior | Separate "Start" and "Next" buttons | Single button with dynamic text |
| Command Used | MAV_RESULT_ACCEPTED ACK | MAV_CMD_ACCELCAL_VEHICLE_POS |
| State Tracking | _incalibrate boolean | _inCalibration boolean |

## Testing Recommendations

1. **Connect to real FC or SITL**
2. **Start IMU calibration** - verify button changes to "Click when Done"
3. **Check logs** - should see:
   - "Sending MAV_CMD_PREFLIGHT_CALIBRATION (param5=1)"
   - Incoming STATUSTEXT or COMMAND_LONG messages
4. **Follow position prompts** - place drone and click button for each position
5. **Verify completion** - should see success message and button returns to "Start Calibration"

## Files Modified

1. `app/src/main/java/com/example/aerogcsclone/telemetry/TelemetryRepository.kt`
2. `app/src/main/java/com/example/aerogcsclone/telemetry/SharedViewModel.kt`
3. `app/src/main/java/com/example/aerogcsclone/calibration/CalibrationState.kt`
4. `app/src/main/java/com/example/aerogcsclone/calibration/CalibrationViewModel.kt`

## Notes

- The implementation supports both STATUSTEXT parsing (legacy) and COMMAND_LONG position requests (preferred)
- ArduPilot may send either message type depending on firmware version
- The MAV_CMD_ACCELCAL_VEHICLE_POS command ID is 42429 (ArduPilot-specific)
- All 6 positions must be completed for successful calibration

