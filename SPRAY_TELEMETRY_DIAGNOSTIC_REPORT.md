# Spray Telemetry Diagnostic Report - December 5, 2025

## Problem Summary

**ISSUE**: Flow rate shows 0 L/min and consumed volume shows 0 mL even when spray is enabled (RC7 = 1951 PWM).

## Root Cause Analysis

### ✅ Parsing Code is CORRECT

The parsing logic in `TelemetryRepository.kt` is working **correctly**:

```kotlin
// Flow rate conversion (CORRECT)
val rate = b.currentBattery / 100f  // Convert cA to Amps (= L/h)

// Consumed volume conversion (CORRECT)  
val consumed = b.currentConsumed / 1000f  // Convert mAh (mL) to Liters
```

### ❌ Flight Controller Configuration Issue

**The problem is that your FCU is sending `current_battery = 0 cA`** for BATT2 (flow sensor), which means:

```
Your Logs:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 BATTERY_STATUS message received:
   Battery ID: 1                          ← BATT2 (flow sensor)
   current_battery: 0 cA (0.0 A)         ← ❌ ZERO - No flow data!
   current_consumed: 0 mAh                ← ❌ ZERO - No consumption!
   battery_remaining: 100%
   voltages[0]: 1000 mV
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🎮 RC7 channel: 1951 PWM, Spray ENABLED  ← ✅ Spray IS enabled
```

**This is a hardware/configuration mismatch**, not a parsing error.

## Enhanced Diagnostics Added

I've added comprehensive error detection that will now show:

```
⚠️⚠️⚠️ CONFIGURATION ERROR DETECTED ⚠️⚠️⚠️
RC7 shows spray ENABLED (1951 PWM > 1500)
BUT flow sensor reports 0 cA (no flow data)

Possible causes:
1. Flow sensor not physically connected
2. BATT2_MONITOR parameter not set correctly
   (should be 11 for Fuel Flow sensor)
3. Flow sensor pin not configured in FCU
4. Flow sensor not calibrated (BATT2_AMP_PERVLT)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Required Actions

### 1. Check Flow Sensor Hardware

**Verify physical connection:**
- Flow sensor signal wire connected to FCU
- Power and ground properly connected
- Sensor is functional (test with multimeter if possible)

### 2. Verify FCU Parameters

**Check these parameters in Mission Planner:**

```
BATT2_MONITOR = 11        ← Must be "Fuel Flow (Pulse)" sensor type
BATT2_AMP_PERVLT = ???    ← Calibration factor (depends on your sensor)
BATT2_CAPACITY = 3300     ← Tank capacity in mL (you already have this)
```

**To check/set parameters:**
1. Open Mission Planner
2. Go to CONFIG → Full Parameter List
3. Search for "BATT2_MONITOR"
4. **If it's 0 or 4**: Change to 11 (Fuel Flow sensor)
5. Search for "BATT2_AMP_PERVLT"
6. Set to your sensor's calibration value (e.g., 10, 100, depends on sensor)

### 3. Verify Sensor Pin Configuration

**Check which pin the flow sensor is connected to:**
- Common pins: ADC pins (e.g., PIN 13, 14, 15)
- Verify the pin is enabled for battery monitoring

### 4. Test with Sensor Connected

**After configuration, the FCU should send:**

```
✅ Expected when working:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 BATTERY_STATUS message received:
   Battery ID: 1
   current_battery: 2400 cA (24.0 A)     ← Should show real flow rate
   current_consumed: 150 mAh              ← Should increase over time
   battery_remaining: 95%
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## How to Verify the Fix

### Run the app and check logcat:

```bash
adb logcat -s "Spray Telemetry" | grep "CONFIGURATION ERROR"
```

**If you see the error message**, follow the steps above to fix the FCU configuration.

**If you don't see the error**, the sensor is working correctly.

## Common Flow Sensor Types

| Sensor Type | BATT2_MONITOR Value | Typical AMP_PERVLT |
|-------------|---------------------|---------------------|
| Pulse Flow Sensor | 11 | 10-100 (calibrate) |
| Analog Voltage | 3 or 4 | Varies |
| I2C/Digital | Check docs | N/A |

## Expected Behavior After Fix

Once the FCU is properly configured:

```
🚿 Processing FLOW SENSOR (BATT2 - id=1)
✓ Flow rate calculated: 24.0 L/h
✓ Flow rate per minute: 0.40 L/min    ← Real flow rate!
✓ Consumed volume: 0.15 L (150 mL)    ← Increases during spraying
✓ Tank capacity: 3.3 L
✓ Remaining: 95%                       ← Decreases as tank empties
```

## Summary

- ✅ **App parsing is correct** - no code changes needed for parsing
- ❌ **FCU configuration issue** - flow sensor not reporting data
- ✅ **Enhanced diagnostics added** - will show clear error when misconfigured
- 📋 **Action required**: Configure BATT2_MONITOR and calibrate flow sensor

The app will now clearly tell you when there's a configuration mismatch between RC7 (spray enabled) and BATT2 (no flow data).

