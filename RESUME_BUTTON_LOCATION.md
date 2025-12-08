# Resume Button Location Guide

## 📍 Where is the Resume Button?

### Visual Location

```
┌─────────────────────────────────────────────────────────────────┐
│                         MainPage Screen                         │
│                                                                 │
│  ┌────────────────────────────────────────────────────────────┐│
│  │                                                            ││
│  │                     Map View Area                          ││
│  │                  (Google Maps Display)                     ││
│  │                                                            ││
│  │                                                            ││
│  │  ┌──────────┐                                             ││
│  │  │ Status   │                                             ││
│  │  │ Panel    │                           ┌────┐  ◄─────────┼┤ RIGHT SIDE
│  │  │ (Bottom  │                           │START│            ││ CENTER
│  │  │  Left)   │                           └────┘            ││
│  │  └──────────┘                              ↕               ││
│  │                                         ┌────┐            ││
│  │                                         │PAUSE│            ││
│  │                                         └────┘            ││
│  │                                            ↕               ││
│  │                                         ┌────┐            ││
│  │                                         │RESUME│ ◄─────────┼┤ ⭐ RESUME BUTTON
│  │                                         │ ▶   │            ││ (3rd from top)
│  │                                         └────┘            ││ Turns ORANGE
│  │                                            ↕               ││ when paused
│  │                                         ┌────┐            ││
│  │                                         │SPLIT│            ││
│  │                                         └────┘            ││
│  │                                            ↕               ││
│  │                                         ┌────┐            ││
│  │                                         │RECEN│            ││
│  │                                         │TER  │            ││
│  │                                         └────┘            ││
│  │                                            ↕               ││
│  │                                         ┌────┐            ││
│  │                                         │ MAP │            ││
│  │                                         │TYPE │            ││
│  │                                         └────┘            ││
│  │                                                            ││
│  └────────────────────────────────────────────────────────────┘│
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Exact Location Details

**Position:** Right side of the screen, vertically centered  
**Alignment:** `Alignment.CenterEnd` with 12dp padding  
**Button Order:** 3rd button from the top (below START and PAUSE, above SPLIT)  
**Size:** 70dp width × 56dp height  
**Shape:** FloatingActionButton (rounded rectangle)

### Button Appearance

#### Resume Button (Always Visible)
```
┌──────────┐
│    ▶     │  ← Play icon
│  Resume  │  ← Text label
└──────────┘
```
- **Background Color (When Paused):** Orange (0xFFFFA500) with 70% opacity ← Easy to spot!
- **Background Color (When Not Paused):** Black with 70% opacity
- **Icon:** PlayArrow (▶) - always a play icon
- **Text:** "Resume" - always says Resume
- **Icon & Text Color:** White

#### Pause Button (Above Resume)
```
┌──────────┐
│    ⏸     │  ← Pause icon
│  Pause   │  ← Text label
└──────────┘
```
- **Background Color:** Black with 70% opacity
- **Icon:** Pause (⏸) - always a pause icon
- **Text:** "Pause" - always says Pause
- **Icon & Text Color:** White
- **Active:** Only when mission is running

### Code Location

**File:** `MainPage.kt`  
**Component:** `FloatingButtons` composable  
**Lines:** 607-665 (approximately)

```kotlin
// Pause Button (only active when mission is running)
FloatingActionButton(
    onClick = {
        if (isMissionRunning) {
            onPauseMission()
        }
    },
    containerColor = Color.Black.copy(alpha = 0.7f),
    modifier = Modifier.size(width = 70.dp, height = 56.dp)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Pause,
            contentDescription = "Pause Mission",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Pause",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Resume Button (orange when mission is paused)
FloatingActionButton(
    onClick = {
        if (missionPaused) {
            onResumeMission()  // ← This triggers the resume dialogs
        }
    },
    containerColor = if (missionPaused)
        Color(0xFFFFA500).copy(alpha = 0.7f) // Orange when paused
    else
        Color.Black.copy(alpha = 0.7f), // Black when not paused
    modifier = Modifier.size(width = 70.dp, height = 56.dp)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Resume Mission",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Resume",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
```

### How the Buttons Work

#### Pause Button (2nd button)
- **Always visible** with pause icon (⏸) and "Pause" text
- **Background:** Black (70% opacity)
- **Active when:** Mission is running in AUTO mode
- **Click action:** Calls `onPauseMission()` → switches to LOITER mode
- **Inactive when:** Mission not running (button still visible but no action)

#### Resume Button (3rd button) - ⭐ THE NEW SEPARATE BUTTON
- **Always visible** with play icon (▶) and "Resume" text
- **Background:** 
  - **Orange (70% opacity)** when mission is paused ← Easy to spot!
  - **Black (70% opacity)** when mission is not paused
- **Active when:** Mission is paused (`missionPaused = true`)
- **Click action:** Calls `onResumeMission()` → shows warning dialog
- **Inactive when:** Mission not paused (button still visible but no action)

### Resume Flow from Button Click

```
User Clicks Orange "Resume" Button
         ↓
onResumeMission() callback triggered
         ↓
Gets last auto waypoint from telemetry state
         ↓
Sets resumeWaypointNumber variable
         ↓
Shows Warning Dialog (showResumeWarningDialog = true)
         ↓
[User sees: "Warning: This will reprogram your mission..."]
```

### Finding the Button Visually

**Look for:**
1. ✅ Right side of screen
2. ✅ Vertical stack of 6 buttons
3. ✅ **3rd button from top** (below START and PAUSE)
4. ✅ **Always shows play icon (▶) and "Resume" text**
5. ✅ **Turns ORANGE** when mission is paused ← Key visual indicator!
6. ✅ Stays black when mission is not paused

**Context Clues:**
- Above: "Pause" button (with pause icon ⏸)
- Below: "Split" button
- Side: Status panel on bottom left
- Background: Map view

### Button Visibility Conditions

The Resume button is **always visible** with:
- ✅ **Always shows:** Play icon (▶) and "Resume" text
- 🟠 **Active & Orange** when `missionPaused = true` ← Clickable
- ⚪ **Inactive & Black** when mission is not paused (not clickable)
- 📍 **Fixed position** - 3rd button, never moves

### Related State Variables

```kotlin
// In MainPage.kt
val missionPaused = telemetryState.missionPaused  // Comes from SharedViewModel
val pausedAtWaypoint = telemetryState.pausedAtWaypoint  // Last paused waypoint
val currentWaypoint = telemetryState.currentWaypoint  // Current mission waypoint
```

### Complete Button Stack (Top to Bottom)

```
1. START    ← Start mission button (always visible)
2. PAUSE    ← Pause mission button (always visible, pause icon)
3. RESUME   ← ⭐ RESUME BUTTON ⭐ (always visible, turns orange when paused)
4. SPLIT    ← Split plan button
5. RECENTER ← Recenter map button
6. MAP TYPE ← Toggle map type button
```

### Quick Reference

| Property | Value |
|----------|-------|
| **Screen** | MainPage |
| **Position** | Right side, center-aligned |
| **Order** | 3rd of 6 buttons |
| **Component** | FloatingActionButton |
| **Parent** | FloatingButtons composable |
| **Size** | 70dp × 56dp |
| **Padding** | 12dp from edge |
| **Spacing** | 8dp between buttons |
| **Color (Paused)** | Orange (#FFA500) |
| **Color (Not Paused)** | Black |
| **Opacity** | 70% (0.7f) |
| **Icon** | PlayArrow (▶) |
| **Text** | "Resume" |
| **Icon Size** | 20dp |
| **Text Size** | 9sp |

### Testing the Button

1. **Connect to drone** (SITL or real hardware)
2. **Upload a mission** to flight controller
3. **Start mission** (click START button)
4. **Wait for AUTO mode** and mission execution
5. **Click Pause button** → switches to LOITER, button turns orange
6. **Click Resume button** (now orange) → Warning dialog appears
7. **Follow dialog flow** to complete resume

### Keyboard Shortcut

Currently: **No keyboard shortcut assigned**  
Future enhancement: Could add keyboard shortcut for resume

---

## Summary

**The Resume Button is:**
- 📍 On the **right side** of the MainPage screen
- 🔢 The **3rd button** in a vertical stack of 6 floating buttons
- 🟠 **Turns orange** when the mission is paused (easy to spot!)
- ▶️ **Always shows** a **Play icon (▶)** and "Resume" text
- 🎯 Located at **Alignment.CenterEnd** (center-right edge)
- 🔒 **Separate from Pause button** - dedicated Resume button below Pause

**Button Layout:**
```
START  ← 1st
PAUSE  ← 2nd (always pause icon)
RESUME ← 3rd ⭐ DEDICATED RESUME BUTTON (always play icon, orange when active)
SPLIT  ← 4th
RECENTER ← 5th
MAP TYPE ← 6th
```

**To use it:**
1. Pause a running mission (click Pause button)
2. Look for the **3rd button** - it will turn **orange**
3. Click it to start the resume process
4. Follow the 3 dialog steps to complete resume

The Resume button is always in the same position and always shows "Resume" text with a play icon - it just changes color from black to orange when you can use it!

