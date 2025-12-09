# Advanced Options Collapsible Button Implementation

## Summary
Added a collapsible "Advanced" button in the PlanScreen that can expand/collapse to show additional mission options.

## Date
December 9, 2025

## Changes Made

### 1. Added State Variable
- Added `showAdvancedOptions` state variable to track whether the advanced options are expanded or collapsed
- Located after the `showWaypointList` variable declaration

### 2. Added Advanced Options Button
- **Location**: Placed directly below the "Upload Mission" button in the top-right button column
- **Appearance**: Dark gray button (Color 0xFF2E2E2E) with white text
- **Icon**: Uses `Menu` icon (hamburger menu style) - stays consistent when expanded/collapsed
- **Functionality**: Toggles the visibility of advanced options when clicked
- **Layout**: Horizontal Row layout - options appear to the **LEFT** side of the button (wrapping around it)

### 3. Added Two Advanced Options (Collapsible - Horizontal)
When the "Advanced" button is clicked, it expands to show options **to the side** (horizontally):

#### a) Obstacle Avoidance
- Slightly lighter gray button (Color 0xFF3A3A3A)
- Warning icon
- Shortened text: "Obstacle Avoid" (fits better horizontally)
- Currently shows "Coming Soon" toast message when clicked
- Ready for future implementation

#### b) Waypoint Adjust
- Slightly lighter gray button (Color 0xFF3A3A3A)
- Edit icon
- Currently shows "Coming Soon" toast message when clicked
- Ready for future implementation

### 4. UI Design Features
- **Horizontal Layout**: Options appear to the **LEFT** side of the Advanced button using Row layout
- **Menu Icon**: Uses classic hamburger menu icon (☰) for better UX - stays consistent
- **Wrap Around Design**: Options wrap around the button from the left, creating a natural flow
- **Spacing**: 8.dp spacing between buttons for clean appearance
- **Consistent Styling**: Matches existing button design patterns in PlanScreen
- **Smooth Collapse/Expand**: Uses Compose's automatic animation for showing/hiding elements
- **Elevation**: Main button has 6.dp elevation, sub-options have 4.dp elevation for visual hierarchy
- **Better UX**: Horizontal layout saves vertical space and looks more modern
- **Intuitive Placement**: Left-side expansion keeps buttons closer to screen edge

## File Modified
- `c:\Users\chidvilas.PAVAMANK\StudioProjects\SampleGCS\app\src\main\java\com\example\aerogcsclone\uimain\PlanScreen.kt`

## Button Hierarchy (Top Right)
```
1. Add Point (Black transparent)
2. Save Mission (Dark gray)
3. Upload Mission (Darker gray)
4. Advanced (Dark gray) ← NEW (expands horizontally to the LEFT)
   
   When collapsed: [☰ Advanced]
   When expanded:  [Obstacle Avoid] [Waypoint Adjust] [☰ Advanced]
   
   Options wrap around the button from the left side!
```

## Future Implementation
The structure is ready for implementing actual functionality for:
1. **Obstacle Avoidance**: Can integrate obstacle detection and avoidance logic
2. **Waypoint Adjust**: Can add waypoint editing/adjustment capabilities

Simply replace the Toast messages with actual implementation code.

## Testing
- Verify the Advanced button appears below Upload Mission button
- Click Advanced button to expand/collapse options
- Verify two sub-options (Obstacle Avoidance, Waypoint Adjust) appear when expanded
- Verify sub-options are indented and styled correctly
- Click each sub-option to verify toast messages appear

## Notes
- No compilation errors introduced
- Maintains existing styling and design patterns
- Ready for future feature additions
- Clean and maintainable code structure

