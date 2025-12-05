# Mission Start Workflow - Quick Reference Guide

## 🚁 New Mission Start Procedure

### Step-by-Step Instructions:

1. **Upload Mission**
   - Plan your waypoints
   - Upload mission to the drone
   - Wait for upload confirmation

2. **Pre-Flight Checks**
   - Ensure GPS has at least 6 satellites
   - Verify vehicle is armable (all sensors green)
   - Check battery level

3. **ARM THE DRONE** ⚠️ **NEW REQUIREMENT**
   - **Use RC transmitter to arm the drone manually**
   - Wait for arming confirmation on screen
   - Do NOT use the on-screen Arm button (if present)

4. **Start Mission**
   - Press the "Start" button in the app
   - System will automatically switch to AUTO mode
   - Mission begins

5. **During Mission**
   - Mission runs autonomously in AUTO mode
   - **If you change mode via RC**: Drone automatically switches to LOITER
   - You receive a notification about the mode change

## ⚠️ Key Changes

### What Changed:
| Before | After |
|--------|-------|
| On-screen "Arm" button | **Manual RC arming required** |
| App arms the drone | **Pilot arms via RC transmitter** |
| No mode override detection | **Auto-LOITER on mode change** |

### Why These Changes:
1. **Safety**: Pilot has final control over arming
2. **Compliance**: Standard practice for autonomous operations
3. **Failsafe**: Automatic LOITER prevents unexpected flight behavior

## 🔴 Error Messages You Might See

### "Please arm the drone manually using the RC transmitter before starting mission."
**Cause**: You pressed Start without arming the drone first  
**Solution**: Arm the drone using your RC transmitter, then press Start again

### "Manual mode change detected. Switching to LOITER mode."
**Cause**: You changed flight mode via RC during autonomous mission  
**Solution**: This is normal - the drone will hold position in LOITER mode for safety

### "Insufficient GPS satellites (X). Need at least 6 for mission."
**Cause**: GPS doesn't have enough satellite lock  
**Solution**: Wait for more satellites or move to an open area

### "Vehicle not armable. Check sensors and GPS."
**Cause**: Pre-arm checks failing  
**Solution**: Check sensor calibration and GPS status

## 🎮 RC Transmitter Usage

### Arming via RC:
- Typically: **Throttle down + rudder right** (hold for ~2 seconds)
- Check your RC manual for specific arming sequence
- Wait for audible beep or LED confirmation

### Mode Override During Mission:
- Switch mode on RC transmitter to override autonomous flight
- Drone automatically enters LOITER to hold position
- Mission monitoring stops automatically
- You can manually control or switch back to AUTO

## 📊 Mission Monitoring Status

### Monitoring Starts When:
- ✅ Mission starts successfully
- ✅ Mission resumes from pause

### Monitoring Stops When:
- 🛑 Mission is paused
- 🛑 Drone is disarmed
- 🛑 Connection is lost
- 🛑 Manual mode override detected

## 🔧 Troubleshooting

### Problem: Can't arm the drone
**Check:**
- GPS has at least 6 satellites
- All sensors are calibrated
- Battery is sufficiently charged
- No pre-arm errors showing

### Problem: Mission won't start
**Check:**
- Drone is armed (via RC)
- Mission is uploaded
- FCU is connected
- GPS has 6+ satellites

### Problem: Drone switches to LOITER during mission
**Cause:** You (or someone) changed the mode via RC  
**Action:** This is by design - for safety, the drone holds position
**Resolution:** Switch back to AUTO mode manually if you want to continue mission

## 🎯 Best Practices

1. **Always arm via RC** - This gives you direct control
2. **Check telemetry before arming** - Verify all systems are green
3. **Keep RC in hand during mission** - For manual override if needed
4. **Monitor mission progress** - Watch waypoint progression
5. **Know your failsafes** - Understand LOITER and RTL behaviors

## 📞 Support

If you encounter issues:
1. Check error messages on screen
2. Review notification panel for warnings
3. Verify all pre-flight checks pass
4. Ensure RC transmitter is properly configured
5. Check logs for detailed error information

## 🚀 Summary

**Old Way:** Press Start → Drone arms and flies  
**New Way:** Arm via RC → Press Start → Drone flies  

**Benefit:** You have full control over when the drone arms and takes off!

