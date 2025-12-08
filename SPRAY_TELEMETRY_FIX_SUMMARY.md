# Spray Telemetry Fixes - December 5, 2025

## Issues Fixed

### 1. ✅ RC7 Channel Monitoring for Spray System Status
**Problem**: The app was not monitoring RC channel 7 to detect when the spray system is ON/OFF.

**Solution**: 
- Added `sprayEnabled` and `rc7Value` fields to `SprayTelemetry` data class
- Added RC_CHANNELS message streaming request at 2Hz when FCU is detected
- Added RC7 monitoring logic that detects spray ON when PWM > 1500
- Added detailed logging: `🎮 RC7 channel: XXX PWM, Spray ENABLED/DISABLED`

**Files Modified**:
- `Data.kt`: Added spray status fields
- `TelemetryRepository.kt`: 
  - Line 254: Added `setMessageRate(65u, 2f)` for RC_CHANNELS
  - Lines 773-800: Added RC7 monitoring in RC_CHANNELS collector

**Expected Logs** (when spray is enabled):
```
D/Spray Telemetry: 🎮 RC7 channel: 1800 PWM, Spray ENABLED
D/Spray Telemetry: Flow sensor (BATT2) - Raw data: current_battery=2400 cA, ...
D/Spray Telemetry: Flow sensor (BATT2) - Parsed: flowRate=0.4 L/min, ...
```

---

### 2. ⚠️ Parameter Reading Analysis (IMPORTANT!)

**Your Current Issue**: 
```
BATT2_CAPACITY parameter read: 3300 mAh (3.3 L)
BATT3_CAPACITY parameter read: 3300 mAh (3.3 L)
```

**Root Cause**: The FCU is returning 3300 mAh for both BATT2_CAPACITY and BATT3_CAPACITY, which suggests:

1. **OPTION A**: These parameters are set to default battery values (3300 mAh = 3.3L)
2. **OPTION B**: The spray sensor parameters haven't been configured on the FCU

**What You Need to Check on Your FCU (via Mission Planner or MAVProxy)**:

#### Check Current Parameter Values:
```
BATT2_MONITOR = 11 (Should be "Fuel Flow" or similar for spray)
BATT2_CAPACITY = 4000 (Should be your tank capacity in mL)
BATT2_AMP_PERVLT = 0.694299 (Calibration factor from flow sensor)
BATT2_CURR_PIN = 53 (Digital input pin for flow sensor)

BATT3_MONITOR = 25 (Should be "Analog Voltage" for level sensor)
BATT3_CAPACITY = 3300 (Should be your tank capacity in mL)
BATT3_VOLT_PIN = 52 (Analog input pin for level sensor)
BATT3_VOLT_MULT = 10.1 (Voltage divider multiplier)
```

#### If Parameters Are Wrong:
Set the correct values in Mission Planner:
```
BATT2_CAPACITY = 4000  (or your actual tank capacity in mL)
BATT3_CAPACITY = 4000  (should match BATT2_CAPACITY)
```

Then save parameters and reboot the FCU.

---

## Understanding ArduPilot Battery Monitor Mapping

| Parameter Name | Battery Instance | BATTERY_STATUS id | Your Sensor |
|---------------|------------------|-------------------|-------------|
| BATT_CAPACITY | Battery 1 (id=0) | 0 | Main Battery |
| BATT2_CAPACITY | Battery 2 (id=1) | 1 | Flow Sensor |
| BATT3_CAPACITY | Battery 3 (id=2) | 2 | Level Sensor |

**Key Point**: The app is correctly requesting `BATT2_CAPACITY` and `BATT3_CAPACITY`. The issue is that your FCU has these set to 3300 (main battery backup capacity) instead of your spray tank capacity.

---

## Testing Steps

### 1. Verify RC7 Monitoring
1. Connect to drone
2. Move RC7 switch/channel
3. Check logcat: `adb logcat -s "Spray Telemetry"`
4. You should see: `🎮 RC7 channel: XXXX PWM, Spray ENABLED/DISABLED`

### 2. Fix Parameter Values on FCU
1. Open Mission Planner
2. Go to Config/Tuning → Full Parameter List
3. Search for `BATT2_CAPACITY` and set to your tank capacity in mL (e.g., 4000 for 4L)
4. Search for `BATT3_CAPACITY` and set to same value
5. Click "Write Params" and reboot FCU

### 3. Verify Parameter Reading
1. Reconnect GCS app
2. Check logcat for parameter readings
3. Expected logs:
```
I/Spray Telemetry: 📤 Requesting spray capacity parameters from FCU...
D/Spray Telemetry: 📤 Sent PARAM_REQUEST_READ for BATT2_CAPACITY
D/Spray Telemetry: 📤 Sent PARAM_REQUEST_READ for BATT3_CAPACITY
I/Spray Telemetry: 📊 BATT2_CAPACITY parameter read: 4000 mAh (4.0 L)
I/Spray Telemetry: 📊 BATT3_CAPACITY parameter read: 4000 mAh (4.0 L)
```

### 4. Test Spray Telemetry Flow
1. Turn on spray system (RC7 > 1500)
2. Check for BATTERY_STATUS messages with id=1 and id=2
3. Verify flow rate and consumption calculations

---

## Additional Debugging Commands

### Check All Parameters:
```bash
adb logcat -s "ParamVM" "Spray Telemetry"
```

### Check RC Channels:
```bash
adb logcat -s "RCCalVM" "Spray Telemetry"
```

### Check All Battery Messages:
```bash
adb logcat | grep -E "BATTERY_STATUS|Spray Telemetry"
```

---

## Summary

✅ **Fixed**: RC7 monitoring for spray system status detection  
✅ **Fixed**: RC_CHANNELS message streaming enabled at 2Hz  
✅ **Fixed**: Added detailed logging for spray system status  
⚠️ **Action Required**: Configure BATT2_CAPACITY and BATT3_CAPACITY on FCU to match tank capacity  

The app is now correctly reading parameters and monitoring RC7. The 3300 mAh readings indicate that your FCU parameters need to be updated to reflect the actual spray system configuration rather than battery capacities.

