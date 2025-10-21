# Hold Nose Position Feature Implementation

## Overview
Implemented a "Hold Nose Position" feature for grid survey missions that uses MAV_CMD_CONDITION_YAW to maintain the drone's yaw throughout the mission, improving battery efficiency.

## Changes Made

### 1. GridWaypoint.kt
- **Added `holdNosePosition` parameter** to `GridSurveyParams` data class
  - Type: `Boolean` (default: `false`)
  - Purpose: Enable/disable yaw holding throughout the mission

### 2. GridMissionConverter.kt
- **Updated `convertToMissionItems()` function signature**
  - Added `holdNosePosition: Boolean = false` parameter
  - Added `initialYaw: Float = 0f` parameter (0-360 degrees, 0=North, 90=East)

- **Implemented MAV_CMD_CONDITION_YAW command**
  - Inserted immediately after takeoff (sequence 2)
  - Parameters:
    - `param1`: Target yaw angle in degrees (uses current drone heading)
    - `param2`: Yaw speed (0 = maximum)
    - `param3`: Direction (1 = clockwise)
    - `param4`: 0 = absolute angle
  - Frame: `MavFrame.MISSION`

### 3. PlanScreen.kt
- **Added UI state variable**
  - `var holdNosePosition by remember { mutableStateOf(false) }`

- **Added Toggle Switch in Grid Controls Panel**
  - Label: "Hold Nose Position"
  - Switch colors: Green when ON, Red when OFF
  - Helper text: "Nose will hold current position during survey lines"
  - Positioned after altitude slider, before geofence section

- **Updated Mission Upload Logic**
  - Retrieves current drone heading: `telemetryState.heading`
  - Passes `holdNosePosition` and `currentHeading` to converter
  - Mission includes CONDITION_YAW command when toggle is enabled

## How It Works

1. User enables "Hold Nose Position" toggle in the Grid Survey Parameters panel
2. When the mission is uploaded, the system:
   - Captures the drone's current heading
   - Generates the mission with MAV_CMD_CONDITION_YAW command after takeoff
   - Sets the yaw to the captured heading value
3. During mission execution:
   - Drone takes off
   - CONDITION_YAW command locks the nose to the specified heading
   - Drone maintains this yaw throughout all waypoints
   - This reduces unnecessary yaw rotations, saving battery power

## Benefits

- **Battery Efficiency**: Reduces power consumption from constant yaw adjustments
- **Stable Camera Angle**: Maintains consistent orientation for aerial photography/surveys
- **Reduced Mechanical Wear**: Less motor activity on the yaw axis
- **Faster Mission Completion**: No time wasted rotating between waypoints

## MAVLink Protocol

The implementation uses the standard MAVLink `MAV_CMD_CONDITION_YAW` command:
- Command ID: 115
- This is a conditional command that sets yaw behavior
- The yaw is maintained until another yaw command is received or RTL is triggered
- Compatible with ArduPilot and PX4 flight controllers

## Testing Recommendations

1. Test with hold nose position OFF (default behavior)
2. Test with hold nose position ON
3. Verify the drone maintains heading during grid survey
4. Compare battery consumption between both modes
5. Test with different initial headings (North, East, South, West)

## Future Enhancements

Potential improvements:
- Allow manual yaw angle input instead of using current heading
- Add yaw angle preview on the map
- Save holdNosePosition preference in mission templates
- Add option to set different yaw for different grid sections

