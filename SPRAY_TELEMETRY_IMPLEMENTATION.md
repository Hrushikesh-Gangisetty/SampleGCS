# Spray Telemetry Implementation Summary

## Overview
Implemented complete spray telemetry system for agricultural drones following MVVM architecture. The system reads flow rate and liquid consumption data from MAVLink BATTERY_STATUS messages and displays them in the bottom telemetry overlay.

## Implementation Details

### 1. Data Layer (Model)

#### File: `Data.kt`
- **SprayTelemetry Data Class**: Encapsulates all spray-related telemetry
  - Flow sensor data (BATT2 - Instance 1):
    - `flowRateLiterPerMin`: Current flow rate in L/min
    - `consumedLiters`: Total liquid sprayed in liters
    - `flowCapacityLiters`: Total tank capacity
    - `flowRemainingPercent`: Remaining percentage
  - Level sensor data (BATT3 - Instance 2):
    - `tankVoltageMv`: Raw voltage from level sensor
    - `tankLevelPercent`: Tank level percentage
    - `tankCapacityLiters`: Tank capacity
  - Formatted values for UI display:
    - `formattedFlowRate`: e.g., "0.40 L/min"
    - `formattedConsumed`: e.g., "2.5 L" or "500 mL"

- **TelemetryState**: Updated to include `sprayTelemetry: SprayTelemetry`

### 2. Repository Layer (MAVLink Protocol Handler)

#### File: `TelemetryRepository.kt`

#### BATTERY_STATUS Message Parsing
The implementation parses BATTERY_STATUS messages based on the `id` field:

**Main Battery (id=0)**:
- Continues to handle main battery current as before

**Flow Sensor (id=1 - BATT2)**:
- **current_battery** (centi-Amps): Converted to flow rate
  - Formula: `flowRateLiterPerHour = currentBattery / 100` (cA → Amps → L/h)
  - Formula: `flowRateLiterPerMin = flowRateLiterPerHour / 60`
  - Physics mapping: 1 Amp ≈ 1 Liter/Hour (based on BATT2_AMP_PERVLT calibration)

- **current_consumed** (mAh): Converted to total volume consumed
  - Formula: `consumedLiters = currentConsumed / 1000` (mAh → mL → Liters)
  - ArduPilot mapping: 1 mAh = 1 mL

- **capacity**: Tank capacity in mAh (converted to liters)
  - Formula: `flowCapacityLiters = capacity / 1000`

- **battery_remaining**: Percentage of liquid remaining

**Level Sensor (id=2 - BATT3)**:
- **voltages[0]**: Raw voltage from analog level sensor in mV
- **battery_remaining**: Tank level percentage (if calculated by firmware)
- **capacity**: Tank capacity for level sensor

#### Logging for Debugging
All spray telemetry parsing includes comprehensive logging with tag `"Spray Telemetry"`:
- Raw data received from MAVLink messages
- Parsed values after conversion
- All intermediate calculations

Example logs:
```
D/Spray Telemetry: Flow sensor (BATT2) - Raw data: current_battery=2400 cA, current_consumed=2500 mAh, battery_remaining=37%
D/Spray Telemetry: Flow sensor (BATT2) - Parsed: flowRate=0.4 L/min, consumed=2.5 L, capacity=4.0 L, remaining=37%
```

### 3. View Layer (UI)

#### File: `MainPage.kt`

#### StatusPanel Updates
The bottom telemetry overlay now displays spray data in two fields:

**Top Row - Flow Rate**:
- Location: Right-most column in top row
- Display: "Flow: 0.40 L/min" or "Flow: N/A"
- Source: `telemetryState.sprayTelemetry.formattedFlowRate`

**Bottom Row - Consumed Volume**:
- Location: Right-most column in bottom row  
- Display: "Consumed: 2.5 L" or "Consumed: 500 mL" or "Consumed: N/A"
- Source: `telemetryState.sprayTelemetry.formattedConsumed`
- Smart formatting:
  - Values < 1L display in mL (e.g., "500 mL")
  - Values ≥ 1L display in L (e.g., "2.50 L")

## MAVLink Protocol Mapping

### Physical Analogy
The spray system uses battery monitoring messages with the following physical mappings:

| Electrical Term | Spray System Equivalent |
|----------------|-------------------------|
| Current (Amps) | Flow Rate (L/h) |
| Capacity (mAh) | Volume (mL) |
| Voltage (V) | Hydrostatic Level |
| Electrons | Fluid Droplets |

### Message Rate Configuration
- BATTERY_STATUS message rate: **1 Hz** (already configured in TelemetryRepository)
- Message ID: **147**

### Sensor Configuration (from ArduPilot parameters)
- **BATT2_MONITOR = 11**: Fuel Flow (Pulse) sensor
- **BATT2_CAPACITY = 4000**: 4L tank capacity
- **BATT2_AMP_PERVLT = 0.694299**: Calibration factor (high precision from bucket test)
- **BATT2_CURR_PIN = 53**: Digital pulse input pin

- **BATT3_MONITOR = 25**: Analog Voltage monitor
- **BATT3_VOLT_MULT = 10.1**: Voltage divider multiplier
- **BATT3_CAPACITY = 3300**: 3.3L (note: discrepancy with BATT2)
- **BATT3_VOLT_PIN = 52**: Analog voltage input pin

## Architecture Compliance (MVVM)

✅ **Model**: `SprayTelemetry` data class in `Data.kt`
✅ **Repository**: MAVLink parsing logic in `TelemetryRepository.kt`
✅ **ViewModel**: State flows through existing `SharedViewModel.telemetryState`
✅ **View**: UI rendering in `MainPage.kt` via `StatusPanel` composable

## Testing & Debugging

### Log Tags
Use `"Spray Telemetry"` to filter logs:
```bash
adb logcat -s "Spray Telemetry"
```

### Expected Log Flow
1. Connection established → BATTERY_STATUS messages start arriving at 1Hz
2. For each BATT2 message:
   - Raw data log shows current_battery, current_consumed, battery_remaining
   - Parsed data log shows converted values in L/min and L
3. For each BATT3 message:
   - Raw data log shows voltage and level percentage
   - Parsed data log shows tank level information

### Verification Checklist
- [ ] BATTERY_STATUS messages arriving at 1Hz
- [ ] Flow sensor (id=1) messages being parsed
- [ ] Level sensor (id=2) messages being parsed
- [ ] UI updating with "Flow: X.XX L/min" when spraying
- [ ] UI updating with "Consumed: X.XX L" as liquid is used
- [ ] Values show "N/A" when no spray data available

## Current State
✅ Data reading implementation complete
✅ UI display integration complete
✅ Logging for debugging enabled
⏳ Calibration features - deferred to next phase as requested

## Next Steps (Future Implementation)
1. **Calibration UI**: Create settings screen for calibrating sensors
   - BATT2_AMP_PERVLT adjustment (bucket test workflow)
   - BATT3 voltage range calibration (empty/full tank)
2. **Tank Level Display**: Add visual tank level indicator using BATT3 data
3. **Spray Rate Control**: Interface to adjust pump PWM (Servo 9)
4. **Application Rate Calculation**: Calculate liquid per hectare based on speed and flow rate
5. **Low Tank Warnings**: Alert when remaining liquid < threshold

## Files Modified
1. `app/src/main/java/com/example/aerogcsclone/telemetry/Data.kt`
2. `app/src/main/java/com/example/aerogcsclone/telemetry/TelemetryRepository.kt`
3. `app/src/main/java/com/example/aerogcsclone/uimain/MainPage.kt`

## Compilation Status
✅ No errors
⚠️ Minor warnings only (unused parameters, deprecation notices - non-critical)

---

**Implementation Date**: December 5, 2025
**Status**: Phase 1 Complete - Data Reading & Display

