# Mission Start Quick Reference - Manual Control

## Overview
The mission workflow has been updated to require **manual arming, manual takeoff, and manual AUTO mode selection** via the RC transmitter.

## Step-by-Step Guide

### 🎯 Starting a New Mission

1. **Upload Mission**
   ```
   ✓ Create waypoints in GCS
   ✓ Upload mission to drone
   ✓ Verify mission uploaded successfully
   ```

2. **Click "Start Mission" Button in GCS**
   ```
   ✓ GCS validates mission readiness
   ✓ Checks GPS satellites (minimum 6 required)
   ✓ Returns message: "Ready for mission. Switch to AUTO mode via RC to start."
   ```

3. **Manual Arming (RC Transmitter)**
   ```
   ✓ Arm the drone using RC transmitter
   ✓ System detects armed state
   ```

4. **Manual Takeoff (RC Transmitter)**
   ```
   ✓ Takeoff manually using RC
   ✓ Achieve desired altitude (e.g., 10-20 meters)
   ✓ Ensure stable hover
   ```

5. **Switch to AUTO Mode (RC Transmitter)**
   ```
   ✓ Switch flight mode to AUTO on RC
   ✓ System automatically detects AUTO mode
   ✓ Mission starts AUTOMATICALLY
   ✓ Toast message: "Mission started in AUTO mode"
   ```

### ⏸️ Pausing and Resuming

1. **Pause Mission**
   ```
   ✓ Click "Pause" button in GCS
   ✓ Drone switches to LOITER mode
   ✓ Holds current position
   ```

2. **Resume Mission**
   ```
   ✓ Click "Resume" button in GCS
   ✓ GCS prepares waypoint for resume
   ✓ Message: "Switch to AUTO mode via RC to resume mission"
   ✓ Switch to AUTO mode on RC
   ✓ Mission resumes AUTOMATICALLY
   ```

### ✂️ Split Plan Resume

1. **After Landing**
   ```
   ✓ Drone has landed from partial mission
   ✓ Split plan is active
   ```

2. **Manual Arming (RC Transmitter)**
   ```
   ✓ Arm the drone using RC transmitter
   ```

3. **Manual Takeoff (RC Transmitter)**
   ```
   ✓ Takeoff manually using RC
   ✓ Achieve desired altitude
   ```

4. **Click "Resume Split Plan" in GCS**
   ```
   ✓ GCS validates readiness
   ✓ Message: "Switch to AUTO mode via RC to continue mission from split point"
   ```

5. **Switch to AUTO Mode (RC Transmitter)**
   ```
   ✓ Switch to AUTO mode on RC
   ✓ Mission continues AUTOMATICALLY from split point
   ```

## Important Notes

### ✅ What Changed
- ❌ **No automatic arming** - Must arm via RC
- ❌ **No automatic takeoff** - Must takeoff via RC  
- ❌ **No automatic AUTO mode switch** - Must switch via RC
- ✅ **Automatic mission start** - When AUTO mode detected

### 🔒 Safety Features (Unchanged)
- ✅ Geofence violation detection
- ✅ Mission mode monitoring
- ✅ Manual mode override switches to LOITER
- ✅ Obstacle detection (if enabled)

### ⚠️ Pre-Flight Checks (Still Enforced)
- ✅ Mission must be uploaded
- ✅ FCU must be detected
- ✅ Minimum 6 GPS satellites required
- ✅ Vehicle must be armable
- ✅ Vehicle must be armed (manually)

## Error Messages

| Message | Meaning | Action Required |
|---------|---------|----------------|
| "Not connected to vehicle" | No connection to drone | Connect to drone first |
| "FCU not detected" | Flight controller not responding | Check connection |
| "No mission uploaded" | No waypoints uploaded | Upload mission first |
| "Please arm and takeoff manually..." | Drone not armed | Arm via RC transmitter |
| "Insufficient GPS satellites" | Less than 6 satellites | Wait for better GPS lock |
| "AUTO mode active but no mission uploaded" | AUTO selected but no mission | Upload mission first |

## Testing the New Workflow

1. Connect to drone
2. Upload a simple 3-waypoint mission
3. Click "Start Mission" - should show "Ready for mission. Switch to AUTO mode via RC to start."
4. Arm drone via RC
5. Takeoff manually via RC to 10m altitude
6. Switch RC to AUTO mode
7. Observe mission starting automatically
8. Test pause/resume by switching modes

## Troubleshooting

**Q: I clicked "Start Mission" but nothing happened**
- A: This is expected. Now manually arm, takeoff, and switch to AUTO mode via RC.

**Q: I switched to AUTO mode but mission didn't start**
- A: Check that:
  - Mission is uploaded (check status)
  - Drone is armed (check telemetry)
  - GPS has at least 6 satellites

**Q: Can I still change modes during mission?**
- A: Yes! If you manually change mode during mission, system will automatically switch to LOITER to hold position safely.

**Q: Does geofence still work?**
- A: Yes! Geofence violation still triggers RTL mode automatically.

## Benefits of New Workflow

1. **Full Pilot Control** - Pilot controls arming, takeoff, and mode selection
2. **Safety First** - No unexpected automatic actions
3. **Flexible Operations** - Can achieve desired altitude before starting mission
4. **Standard Practice** - Follows industry-standard drone operation procedures
5. **Seamless Automation** - Once conditions are met, mission starts smoothly

---

**Date:** December 5, 2025  
**Version:** 1.0  
**Status:** ✅ Implementation Complete

