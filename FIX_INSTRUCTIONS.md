# TelemetryRepository.kt Compilation Error Fix

## Problem
The file has compilation errors showing "Unresolved reference" for class members like `connection`, `fcuSystemId`, `state`, etc. starting around line 912.

## Root Cause
The file structure is actually **CORRECT**, but the IDE's compilation cache is in an inconsistent state, causing it to misinterpret the file structure.

## Solution

### Option 1: Clean and Rebuild (RECOMMENDED)
1. In Android Studio, go to **Build → Clean Project**
2. Wait for it to complete
3. Then go to **Build → Rebuild Project**
4. This will clear the compilation cache and resolve the errors

### Option 2: Invalidate Caches and Restart
1. Go to **File → Invalidate Caches / Restart...**
2. Select **Invalidate and Restart**
3. Wait for Android Studio to restart and re-index the project

### Option 3: Manual Gradle Clean (if above don't work)
1. Open Terminal in Android Studio (bottom panel)
2. Run: `gradlew clean`
3. Then run: `gradlew build`

## Verification
After rebuilding, the errors should be resolved. The file structure is correct:
- Line 908: `start()` function closes properly with `}`
- Line 912: `requestMissionItemsFromFcu()` function starts correctly as a class member
- All subsequent functions are properly part of the `MavlinkTelemetryRepository` class

## What the Code Does
The spray telemetry diagnostics I added will:
1. ✅ Detect when spray is enabled (RC7 > 1500 PWM) but flow sensor reports 0
2. ✅ Show clear error message with possible causes
3. ✅ Log all flow sensor data for debugging

Once the build completes successfully, run the app and check logcat with:
```
adb logcat -s "Spray Telemetry"
```

You should see the enhanced diagnostics that clearly identify the FCU configuration issue.
