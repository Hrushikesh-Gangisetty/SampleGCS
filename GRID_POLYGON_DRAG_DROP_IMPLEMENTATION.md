# Grid Polygon Point Drag and Drop Implementation

## Overview
Implemented manual drag-and-drop functionality for grid survey polygon points, allowing users to adjust polygon vertices dynamically. When a polygon point is moved, the grid waypoints are automatically regenerated based on the updated polygon shape.

## Features Implemented

### 1. Draggable Polygon Points
- All polygon points in grid survey mode are now draggable
- Long press and drag any purple polygon marker to adjust its position
- Grid waypoints automatically regenerate when polygon is modified

### 2. Visual Feedback
- **Default State**: Purple markers for polygon points
- **Selected State**: Yellow markers when a polygon point is selected
- Clear visual distinction between:
  - Regular waypoints (blue)
  - Polygon points (purple/yellow)
  - Grid waypoints (orange with green start 'S' and red end 'E')

### 3. Polygon Point Selection
- Tap on any polygon point to select it
- Selected point is highlighted in yellow
- Selection is cleared when:
  - A waypoint is selected (in waypoint mode)
  - Another polygon point is selected
  - The point is deleted

### 4. Smart Deletion
- Delete button now works contextually:
  - **Grid Survey Mode**: Deletes selected polygon point
  - **Waypoint Mode**: Deletes selected waypoint
- Shows appropriate toast message if no point is selected
- Automatically regenerates grid after deletion
- Updates geofence buffer after deletion

### 5. Real-time Grid Regeneration
- Grid waypoints update automatically when polygon points are dragged
- Grid lines recalculate based on new polygon shape
- Geofence buffer updates dynamically

## Technical Implementation

### Modified Files

#### 1. **GcsMap.kt**
Added new parameters for polygon point interaction:
```text
// Polygon point drag callback
onPolygonPointDrag: (index: Int, newPosition: LatLng) -> Unit = { _, _ -> },
// Polygon point selection
selectedPolygonPointIndex: Int? = null,
onPolygonPointClick: (index: Int) -> Unit = {}
```

Made polygon markers draggable with selection highlighting:
- Added `rememberMarkerState` for position tracking
- Enabled `draggable = true` on polygon markers
- Added `LaunchedEffect` to detect position changes
- Implemented yellow highlight for selected polygon points

#### 2. **PlanScreen.kt**
Added state management for polygon point selection:
```text
var selectedPolygonPointIndex by remember { mutableStateOf<Int?>(null) }
```

Implemented drag handler with auto-regeneration:
```
onPolygonPointDrag = { index, newPosition ->
    if (index in surveyPolygon.indices) {
        surveyPolygon = surveyPolygon.toMutableList().apply {
            this[index] = newPosition
        }
        if (surveyPolygon.size >= 3) {
            regenerateGrid()
        }
    }
}
```

Enhanced delete button logic:
- Context-aware deletion (polygon point vs waypoint)
- Selection-based deletion
- Automatic grid regeneration
- Geofence buffer updates

## User Experience

### How to Use

1. **Enter Grid Survey Mode**
   - Switch to grid survey mode from the planning screen

2. **Add Polygon Points**
   - Use "Add Point" button to add polygon vertices
   - At least 3 points required to generate grid

3. **Adjust Polygon Points**
   - Tap on any purple polygon marker to select it (turns yellow)
   - Long press and drag to reposition the point
   - Grid automatically updates as you drag

4. **Delete Polygon Points**
   - Select a polygon point by tapping on it
   - Press the delete button (trash icon)
   - Grid regenerates with updated polygon

5. **Clear Selection**
   - Tap on another polygon point
   - Tap on a waypoint (if in waypoint mode)
   - Delete the selected point

## Benefits

1. **Flexibility**: Easy adjustment of survey area without redrawing entire polygon
2. **Real-time Feedback**: See grid changes immediately as you drag
3. **Precision**: Fine-tune polygon vertices for exact survey coverage
4. **Efficiency**: No need to delete and recreate polygon points
5. **Visual Clarity**: Clear selection indicators show which point is active
6. **Consistent UX**: Same drag-and-drop pattern as regular waypoints

## Integration with Existing Features

- ✅ Works seamlessly with grid parameter controls
- ✅ Compatible with geofence buffer generation
- ✅ Maintains mission upload workflow
- ✅ Respects grid survey settings (angle, spacing, altitude, speed)
- ✅ Integrates with template save/load system

## Testing Checklist

- [x] Polygon points are draggable in grid survey mode
- [x] Grid regenerates automatically when points are moved
- [x] Selection highlighting works correctly
- [x] Delete button removes selected polygon point
- [x] Geofence buffer updates after drag/delete
- [x] Grid waypoints recalculate correctly
- [x] Selection clears appropriately
- [x] Toast messages guide user when no point selected
- [x] Works with 3+ polygon points
- [x] Maintains polygon closure (visual connection)

## Future Enhancements (Optional)

- Add undo/redo for polygon point movements
- Show distance/area calculations as polygon changes
- Add snapping to grid intersections
- Multi-select for moving multiple points together
- Copy/paste polygon shapes
- Rotate entire polygon around center point

## Date
December 8, 2025

