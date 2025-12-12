# Flight Logging Diagnostic Guide

## Issue: Logs Not Being Saved

I've added extensive debug logging to help diagnose why logs aren't being saved. Here's what to check:

---

## Step 1: Check Database Initialization

**When app starts, look for:**
```
I/TlogViewModel: 🗄️ Database initialized - Total flights: X
```

**If you see an error instead:**
```
E/TlogViewModel: ❌ Database error during initialization
```
This means the database isn't accessible. Check for Room database errors.

**If there's an old active flight:**
```
W/TlogViewModel: ⚠️ Found active flight from previous session: X
W/TlogViewModel:    Cleaning up incomplete flight...
```
This is normal - it cleans up incomplete flights from crashes.

---

## Step 2: Check Flight Start

**When flight starts, you should see:**
```
I/UnifiedFlightTracker: 🚁 FLIGHT STARTING (mode=MANUAL)
D/TlogRepository: 📝 Creating new flight entry...
I/TlogRepository: ✅ Flight entry created in database - ID: 1
I/TlogViewModel: ✅ Flight started successfully - ID: 1
D/TlogRepository: 📝 Logging event for flight 1: Flight started - Armed
I/TlogRepository: ✅ Event saved: Flight started - Armed
I/UnifiedFlightTracker: ✅ Flight logging started successfully
```

**If you DON'T see "Flight entry created in database":**
- Database insert failed
- Check for Room errors in logcat

**If you see "⚠️ Cannot log telemetry - currentFlightId is null":**
- `startFlight()` was never called OR failed
- UnifiedFlightTracker not initialized properly

---

## Step 3: Check Telemetry Logging (Every 5 Seconds)

**During flight, you should see (every 30 seconds to avoid spam):**
```
D/TlogViewModel: 📊 Telemetry logged for flight 1
V/TlogRepository: 📊 Telemetry saved for flight 1 (alt=5.2, speed=2.1)
V/TlogRepository: 🗺️ Map position saved for flight 1
```

**If you DON'T see these:**
- `currentFlightId` is null (check Step 2)
- FlightLoggingService not running
- Telemetry data not coming from drone

**If you see errors:**
```
E/TlogRepository: ❌ Failed to save telemetry for flight 1
```
This indicates a database write error.

---

## Step 4: Check Flight End

**When flight ends, you should see:**
```
I/UnifiedFlightTracker: 🛬 FLIGHT ENDING
I/UnifiedFlightTracker: 📊 Final flight metrics:
I/UnifiedFlightTracker:    Duration: 00:02:15
I/UnifiedFlightTracker:    Distance: 45.3 m
D/TlogRepository: 📝 Logging event for flight 1: Flight ended - ...
I/TlogRepository: ✅ Event saved: Flight ended - ...
I/TlogViewModel: Ending flight 1 with area=null, consumed=null
D/TlogRepository: 📝 Completing flight 1...
I/TlogRepository: ✅ Flight 1 completed - Duration: 135s
I/TlogViewModel: ✅ Flight 1 ended successfully
```

**If you see:**
```
E/TlogViewModel: ❌ Cannot end flight - currentFlightId is null
```
This means `startFlight()` was never called or failed.

---

## Step 5: Verify Logs Were Saved

**Go to Logs Screen and check if flight appears.**

**In logcat, when you open Logs screen:**
```
I/TlogViewModel: 🗄️ Database initialized - Total flights: 1
```

**If total is 0:**
- No flights were saved
- Database writes failed
- Check for Room errors

---

## Common Problems & Solutions

### Problem 1: "currentFlightId is null" messages
**Cause**: `startFlight()` never called or failed
**Solution**: 
1. Check if UnifiedFlightTracker is initialized (TlogIntegration)
2. Look for "Flight started successfully - ID: X" message
3. If missing, database insert failed

### Problem 2: No telemetry logging messages
**Cause**: FlightLoggingService not running OR currentFlightId null
**Solution**:
1. Check for "Flight logging started successfully"
2. Verify drone is connected (telemetry flowing)
3. Check for "⚠️ Cannot log telemetry" warnings

### Problem 3: Database errors
**Cause**: Room database issues, schema mismatch, or corruption
**Solution**:
1. Look for Room error stack traces
2. Clear app data and restart (WARNING: deletes all logs)
3. Check database version in MissionTemplateDatabase.kt

### Problem 4: Flight starts but no data saved
**Cause**: Database writes failing silently
**Solution**:
1. Look for "❌ Failed to save" error messages
2. Check device storage space
3. Check app permissions

---

## Quick Test Procedure

1. **Clear logcat** (`adb logcat -c`)

2. **Start app** and look for:
   ```
   I/TlogViewModel: 🗄️ Database initialized - Total flights: X
   ```

3. **Connect to drone** and **arm**

4. **Fly for at least 10 seconds**

5. **Land and disarm**

6. **Filter logcat:**
   ```bash
   adb logcat | grep -E "TlogViewModel|TlogRepository|UnifiedFlightTracker"
   ```

7. **Look for the sequence:**
   - ✅ Database initialized
   - ✅ Flight started successfully - ID: X
   - ✅ Flight entry created in database
   - ✅ Event saved
   - 📊 Telemetry logged (multiple times)
   - ✅ Flight completed

8. **Go to Logs screen** - flight should appear

---

## What to Report

If logs still don't save after these fixes, please provide:

1. **Full logcat output** from app start to flight end
2. **Any error messages** with red text
3. **Total flights count** from logcat when app starts
4. **Whether you see "Flight started successfully - ID: X"**
5. **Whether you see any "❌" error messages**

The extensive logging will show EXACTLY where the failure is happening.

---

## Expected Full Log Sequence

```
I/TlogViewModel: 🗄️ Database initialized - Total flights: 0
I/UnifiedFlightTracker: 🚁 FLIGHT STARTING (mode=MANUAL)
D/TlogRepository: 📝 Creating new flight entry...
I/TlogRepository: ✅ Flight entry created in database - ID: 1
I/TlogViewModel: ✅ Flight started successfully - ID: 1
D/TlogRepository: 📝 Logging event for flight 1: Flight started - Armed
I/TlogRepository: ✅ Event saved: Flight started - Armed
D/TlogViewModel: ✅ Start event logged for flight 1
I/UnifiedFlightTracker: ✅ Flight logging started successfully
[... flight in progress ...]
D/TlogViewModel: 📊 Telemetry logged for flight 1
V/TlogRepository: 📊 Telemetry saved for flight 1 (alt=5.2, speed=2.1)
[... more telemetry ...]
I/UnifiedFlightTracker: 🛬 FLIGHT ENDING
D/TlogRepository: 📝 Logging event for flight 1: Flight ended - ...
I/TlogRepository: ✅ Event saved: Flight ended - ...
I/TlogViewModel: Ending flight 1 with area=null, consumed=null
D/TlogRepository: 📝 Completing flight 1...
I/TlogRepository: ✅ Flight 1 completed - Duration: 135s
I/TlogViewModel: ✅ Flight 1 ended successfully
```

If you see this full sequence, logs ARE being saved!

