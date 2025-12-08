# Resume Dialog Debug - Troubleshooting Guide

## Issue
When clicking the Resume button, no dialog box appears.

## Debugging Changes Added

### 1. Button Click Logging
Added logging to the Resume button's onClick handler:
```kotlin
onClick = {
    android.util.Log.i("MainPage", "Resume button clicked! missionPaused=$missionPaused")
    if (missionPaused) {
        onResumeMission()
    } else {
        // For testing: allow resume even when not paused
        android.util.Log.w("MainPage", "Resume clicked but mission not paused - allowing anyway for testing")
        onResumeMission()
    }
}
```

### 2. Callback Logging
Added comprehensive logging to the onResumeMission callback:
```kotlin
onResumeMission = {
    android.util.Log.i("MainPage", "=== RESUME CALLBACK TRIGGERED ===")
    Toast.makeText(context, "Resume callback triggered!", Toast.LENGTH_LONG).show()
    
    val lastAutoWp = telemetryState.pausedAtWaypoint ?: telemetryState.currentWaypoint ?: 1
    resumeWaypointNumber = lastAutoWp
    
    android.util.Log.i("MainPage", "showResumeWarningDialog BEFORE: $showResumeWarningDialog")
    showResumeWarningDialog = true
    android.util.Log.i("MainPage", "showResumeWarningDialog AFTER: $showResumeWarningDialog")
}
```

### 3. State Change Monitoring
Added LaunchedEffect to monitor dialog state:
```kotlin
LaunchedEffect(showResumeWarningDialog) {
    android.util.Log.i("MainPage", "showResumeWarningDialog changed to: $showResumeWarningDialog")
}
LaunchedEffect(showResumeWaypointDialog) {
    android.util.Log.i("MainPage", "showResumeWaypointDialog changed to: $showResumeWaypointDialog")
}
```

### 4. Dialog Render Logging
Added logging when dialog is rendered:
```kotlin
if (showResumeWarningDialog) {
    android.util.Log.i("MainPage", "Rendering Resume Warning Dialog")
    AlertDialog(...)
}
```

## How to Debug

### Step 1: Check Button Click
1. Click the Resume button (3rd button from top on right side)
2. Watch for Toast message: "Resume callback triggered!"
3. Check Logcat for: `"Resume button clicked! missionPaused=..."`

**If you DON'T see these:**
- Button click is not being registered
- Check if button is obscured by another UI element
- Check if click event is being captured by something else

### Step 2: Check Callback Execution
Look for in Logcat:
```
MainPage: === RESUME CALLBACK TRIGGERED ===
MainPage: Resume button clicked! Showing warning dialog. Waypoint: X
MainPage: showResumeWarningDialog BEFORE: false
MainPage: showResumeWarningDialog AFTER: true
```

**If you see "BEFORE: true":**
- State is already true, dialog might be stuck
- Try clicking Cancel/dismiss if dialog is invisible

**If you see AFTER but no dialog:**
- State is set correctly but dialog not rendering
- Check Step 3

### Step 3: Check State Changes
Look for in Logcat:
```
MainPage: showResumeWarningDialog changed to: true
```

**If you DON'T see this:**
- State change not triggering recomposition
- Possible issue with remember/mutableStateOf

**If you DO see this but no dialog:**
- Proceed to Step 4

### Step 4: Check Dialog Rendering
Look for in Logcat:
```
MainPage: Rendering Resume Warning Dialog
```

**If you DON'T see this:**
- The `if (showResumeWarningDialog)` condition is not being evaluated as true
- State might be getting reset somehow
- Check if there's a recomposition issue

**If you DO see this:**
- Dialog is being created but not visible
- Check UI hierarchy (might be hidden behind something)

## Expected Log Sequence

When you click Resume, you should see:
```
1. MainPage: Resume button clicked! missionPaused=true (or false)
2. MainPage: === RESUME CALLBACK TRIGGERED ===
3. Toast: "Resume callback triggered!"
4. MainPage: Resume button clicked! Showing warning dialog. Waypoint: 1
5. MainPage: showResumeWarningDialog BEFORE: false
6. MainPage: showResumeWarningDialog AFTER: true
7. Toast: "Opening Resume dialog..."
8. MainPage: showResumeWarningDialog changed to: true
9. MainPage: Rendering Resume Warning Dialog
10. [Dialog appears on screen]
```

## Common Issues and Solutions

### Issue 1: Button Not Clickable
**Symptom:** No logs at all when clicking Resume button
**Cause:** Button is disabled or covered by another UI element
**Solution:** 
- Check if `missionPaused` is false (button won't work in original code)
- Current code allows clicking even when not paused for testing
- Check if FloatingButtons component is visible

### Issue 2: Callback Not Triggered
**Symptom:** Button click logged but no "RESUME CALLBACK TRIGGERED"
**Cause:** The `onResumeMission` lambda is not being called
**Solution:**
- Check FloatingButtons parameters
- Ensure onResumeMission is passed correctly
- Check for null or empty lambda

### Issue 3: State Not Updating
**Symptom:** "BEFORE: false" but "AFTER: false" (doesn't change to true)
**Cause:** State mutation issue
**Solution:**
- Check if `showResumeWarningDialog` is defined correctly with `remember { mutableStateOf(false) }`
- Check if there's a scope issue

### Issue 4: Dialog Not Rendering
**Symptom:** State is true but no "Rendering Resume Warning Dialog" log
**Cause:** Composable not recomposing or conditional not evaluating
**Solution:**
- Force recomposition by changing something else
- Check if dialog code is in the right scope (inside Column)
- Check if there's an exception being swallowed

### Issue 5: Dialog Rendered But Not Visible
**Symptom:** "Rendering Resume Warning Dialog" logged but no visual dialog
**Cause:** Dialog hidden behind other UI or z-index issue
**Solution:**
- Check AlertDialog theme
- Check if dialog is behind the map or other full-screen elements
- Try adding higher elevation to dialog

## Testing Steps

### Test 1: Force Dialog
Try this in MainPage to force the dialog to show on startup:
```kotlin
var showResumeWarningDialog by remember { mutableStateOf(true) } // Changed to true
```
**Expected:** Dialog should appear immediately when app starts

### Test 2: Button Test
Add a simple counter to verify button clicks:
```kotlin
var clickCount by remember { mutableStateOf(0) }
// In button onClick:
clickCount++
Toast.makeText(context, "Clicked $clickCount times", Toast.LENGTH_SHORT).show()
```

### Test 3: Simple Dialog Test
Replace Resume button's onClick with:
```kotlin
onClick = {
    android.util.Log.i("TEST", "Button clicked!")
    Toast.makeText(context, "TEST CLICK", Toast.LENGTH_LONG).show()
}
```

## Logcat Filter

Use this filter to see only relevant logs:
```
tag:MainPage
```

Or for all debug info:
```
MainPage|ResumeMission|SharedVM
```

## Next Steps

1. **Run the app** with the debug changes
2. **Click the Resume button** (3rd from top on right side)
3. **Watch for Toast messages** and check Logcat
4. **Follow the debug sequence** above
5. **Identify which step fails**
6. **Apply appropriate solution**

## Current Code State

All debug logging is now in place:
- ✅ Button click logging
- ✅ Callback execution logging  
- ✅ State change monitoring
- ✅ Dialog render logging
- ✅ Toast messages for visual feedback
- ✅ Button works even when mission not paused (for testing)

**The logs will tell us exactly where the flow is breaking!**

