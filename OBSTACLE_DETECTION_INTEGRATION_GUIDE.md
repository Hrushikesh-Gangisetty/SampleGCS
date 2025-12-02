# Obstacle Detection Mission Split - Integration Guide

## Overview
This implementation provides a complete obstacle detection and mission split system following the detailed 14-phase guide. The system automatically detects obstacles during flight, triggers RTL (Return to Launch), saves mission state, and allows users to resume missions from safe waypoints.

## Architecture Components

### 1. Data Models (`ObstacleData.kt`)
- **ObstacleInfo**: Detected obstacle information
- **SavedMissionState**: Mission state saved when obstacle detected
- **ResumeOption**: Available resume waypoints for user selection
- **ObstacleDetectionConfig**: System configuration parameters
- **ThreatLevel**: Classification (NONE, LOW, MEDIUM, HIGH)

### 2. Sensor Management (`SensorManager.kt`)
- Manages LIDAR/Ultrasonic/Proximity sensors
- Collects sensor data at 100ms intervals
- Applies moving average filter to reduce noise
- Supports multiple sensor types and simulated sensors

### 3. Obstacle Detection (`ObstacleDetector.kt`)
- Processes sensor readings
- Classifies threat levels based on distance
- Calculates obstacle GPS location
- Requires 3 consecutive HIGH detections before triggering RTL

### 4. Mission State Storage (`MissionStateRepository.kt`)
- Persists mission state to Room database
- Serializes/deserializes waypoints and GPS data
- Tracks resolved and unresolved missions
- Supports mission recovery after app restart

### 5. Main Manager (`ObstacleDetectionManager.kt`)
- Coordinates all components
- Implements all 14 phases from the guide
- Monitors mission progress
- Handles RTL triggering and monitoring
- Generates resume options

### 6. ViewModel (`ObstacleDetectionViewModel.kt`)
- Integrates with existing SharedViewModel
- Manages lifecycle and state
- Provides UI-friendly state flows

### 7. UI (`ObstacleDetectionScreen.kt`)
- Displays real-time obstacle status
- Shows RTL monitoring progress
- Presents resume options to user
- Visual threat indicators

## Integration Steps

### Step 1: Initialize Database
Add to your existing database or create new instance:

```kotlin
// In your Application class or setup code
val obstacleDatabase = ObstacleDatabase.getDatabase(context)
val savedMissionStateDao = obstacleDatabase.savedMissionStateDao()
```

### Step 2: Create ViewModel
Initialize the obstacle detection ViewModel with your SharedViewModel:

```kotlin
val obstacleViewModel = ObstacleDetectionViewModel(
    application = application,
    sharedViewModel = sharedViewModel,
    savedMissionStateDao = savedMissionStateDao
)

// Initialize with configuration
val config = ObstacleDetectionConfig(
    maxDetectionRange = 50f,
    highThreatThreshold = 10f,
    mediumThreatThreshold = 20f,
    lowThreatThreshold = 50f,
    detectionIntervalMs = 100,
    minimumConsecutiveDetections = 3,
    enableAutoRTL = true,
    sensorType = SensorType.SIMULATED // or PROXIMITY, LIDAR, ULTRASONIC
)

obstacleViewModel.initialize(config)
```

### Step 3: Start Monitoring When Mission Begins
Hook into your mission upload and start sequence:

```kotlin
// After mission upload successful
sharedViewModel.uploadMission(missionItems) { success, error ->
    if (success) {
        // Start obstacle monitoring
        obstacleViewModel.startMissionMonitoring(
            waypoints = missionItems,
            homeLocation = homeLatLng,
            surveyPolygon = surveyPolygonPoints,
            altitude = 30f,
            speed = 12f
        )
        
        Log.i("Mission", "✅ Mission uploaded and obstacle monitoring started")
    }
}

// When drone arms and takes off, monitoring is already active
```

### Step 4: Add UI to Display Status
Add the obstacle detection screen to your navigation:

```kotlin
@Composable
fun MainScreen(
    sharedViewModel: SharedViewModel,
    obstacleViewModel: ObstacleDetectionViewModel
) {
    var showObstacleScreen by remember { mutableStateOf(false) }
    
    // Main content
    Box {
        // Your existing UI
        
        // Obstacle detection button
        FloatingActionButton(
            onClick = { showObstacleScreen = true }
        ) {
            Icon(Icons.Default.Warning, "Obstacle Detection")
        }
    }
    
    // Obstacle detection overlay
    if (showObstacleScreen) {
        obstacleViewModel.detectionStatus?.let { statusFlow ->
            obstacleViewModel.currentObstacle?.let { obstacleFlow ->
                // Only create manager when needed
                val manager = remember {
                    ObstacleDetectionManager(
                        context = LocalContext.current,
                        sharedViewModel = sharedViewModel,
                        missionStateRepository = /* repository */,
                        config = ObstacleDetectionConfig()
                    )
                }
                
                ObstacleDetectionScreen(
                    detectionManager = manager,
                    onResumeSelected = { option ->
                        obstacleViewModel.resumeMission(option)
                        showObstacleScreen = false
                    },
                    onDismiss = { showObstacleScreen = false }
                )
            }
        }
    }
}
```

### Step 5: Handle Mission Completion
Stop monitoring when mission completes:

```kotlin
// When mission completes (all waypoints reached or manual stop)
obstacleViewModel.completeMission()
```

### Step 6: Check for Unresolved Missions on Startup
Check if there are interrupted missions when app starts:

```kotlin
// In your MainActivity or main ViewModel
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    obstacleViewModel.checkForUnresolvedMissions()
}
```

## Usage Examples

### Example 1: Normal Mission with Obstacle Detection
```kotlin
// 1. Upload mission
val waypoints = listOf(/* your waypoints */)
sharedViewModel.uploadMission(waypoints) { success, _ ->
    if (success) {
        // 2. Start monitoring
        obstacleViewModel.startMissionMonitoring(
            waypoints = waypoints,
            homeLocation = LatLng(37.7749, -122.4194)
        )
        
        // 3. Arm and start mission
        sharedViewModel.arm { armSuccess ->
            if (armSuccess) {
                sharedViewModel.takeoff(30f)
            }
        }
    }
}

// System automatically monitors and triggers RTL if HIGH threat detected
```

### Example 2: Simulated Obstacle Testing
```kotlin
// For testing without real sensors
val config = ObstacleDetectionConfig(
    sensorType = SensorType.SIMULATED,
    enableAutoRTL = true
)
obstacleViewModel.initialize(config)

// Start mission
obstacleViewModel.startMissionMonitoring(waypoints, home)

// Inject simulated obstacle after 10 seconds
Handler(Looper.getMainLooper()).postDelayed({
    obstacleViewModel.injectSimulatedObstacle(8.5f) // 8.5m - HIGH threat
}, 10000)

// System will trigger RTL automatically
```

### Example 3: Resume Mission After Obstacle
```kotlin
// Observe resume options
obstacleViewModel.resumeOptions?.collect { options ->
    if (options.isNotEmpty()) {
        // Display options to user
        options.forEach { option ->
            println("${if (option.isRecommended) "⭐" else ""} " +
                    "WP${option.waypointIndex}: " +
                    "${option.distanceFromObstacle}m from obstacle, " +
                    "${option.coveragePercentage}% coverage")
        }
        
        // User selects recommended option
        val recommended = options.first { it.isRecommended }
        obstacleViewModel.resumeMission(recommended)
        
        // After upload completes, arm and start
        // System automatically resumes monitoring
    }
}
```

## Configuration Parameters

### Detection Ranges
- **Low Threat**: 20-50 meters (warning only)
- **Medium Threat**: 10-20 meters (caution alert)
- **High Threat**: <10 meters (triggers RTL)

### Detection Logic
- Check interval: 100 milliseconds
- Consecutive detections required: 3 (prevents false positives)
- Angle tolerance: ±30° (obstacle must be in flight path)

### RTL Monitoring
- Arrival threshold: 5 meters from home
- Consecutive arrival checks: 3 (confirms landing)

## Phase Implementation Mapping

| Phase | Component | Method |
|-------|-----------|--------|
| 1 | System Design | `ObstacleData.kt` classes |
| 2 | Initialization | `initialize()` |
| 3 | Mission Start | `startMissionMonitoring()` |
| 4 | Monitoring | `processObstacleDetection()` |
| 5 | RTL Trigger | `triggerEmergencyRTL()` |
| 6 | RTL Monitor | `monitorRTLProgress()` |
| 7 | Resume Prep | `generateResumeOptions()` |
| 8 | Create Plan | `buildResumeMission()` |
| 9 | Upload | `resumeMissionFromWaypoint()` |
| 10 | Arm/Resume | `resumeMonitoring()` |
| 11 | Continue | Auto-continues monitoring |
| 12 | Complete | `completeMission()` |
| 13 | Special Cases | Built-in error handling |
| 14 | Testing | `injectSimulatedObstacle()` |

## Database Schema

### saved_mission_states Table
```sql
CREATE TABLE saved_mission_states (
    missionId TEXT PRIMARY KEY,
    interruptedWaypointIndex INTEGER,
    currentDroneLat REAL,
    currentDroneLng REAL,
    homeLat REAL,
    homeLng REAL,
    originalWaypointsJson TEXT,
    remainingWaypointsJson TEXT,
    obstacleLat REAL,
    obstacleLng REAL,
    obstacleDistance REAL,
    obstacleBearing REAL,
    obstacleThreatLevel TEXT,
    missionProgress REAL,
    timestamp INTEGER,
    surveyPolygonJson TEXT,
    altitude REAL,
    speed REAL,
    isResolved INTEGER DEFAULT 0,
    resumedAt INTEGER
)
```

## Testing Checklist

- [ ] Initialize system with simulated sensor
- [ ] Start mission monitoring
- [ ] Inject LOW threat (20-50m) - should log warning only
- [ ] Inject MEDIUM threat (10-20m) - should show caution alert
- [ ] Inject HIGH threat (<10m) once - should not trigger (needs 3)
- [ ] Inject HIGH threat 3 times consecutively - should trigger RTL
- [ ] Monitor RTL progress back to home
- [ ] Verify mission state saved to database
- [ ] Check resume options generated
- [ ] Select and upload resume mission
- [ ] Verify new mission starts from correct waypoint
- [ ] Complete mission and verify statistics

## Logs to Monitor

Key log tags:
- `ObstacleDetectionMgr` - Main manager operations
- `ObstacleSensorManager` - Sensor readings
- `ObstacleDetector` - Threat detection
- `MissionStateRepo` - Database operations
- `ObstacleDetectionVM` - ViewModel operations

## Troubleshooting

### Obstacle not detected
- Check sensor is initialized: Look for "✅ Sensor calibrated" log
- Verify monitoring is active: Status should be MONITORING
- Check detection range: Distance must be 0-50m
- Verify obstacle is in flight path: Angle must be within ±30°

### RTL not triggering
- Verify 3 consecutive HIGH detections: Check consecutive counter
- Ensure `enableAutoRTL = true` in config
- Check MAVLink connection is active

### Resume mission not working
- Verify mission state was saved: Check database
- Ensure mission uploaded successfully
- Check waypoint indices are valid

## Performance Considerations

- Sensor polling: 100ms (10 Hz) - acceptable for most sensors
- Moving average filter: 5 readings - balances responsiveness and noise
- Database writes: Only on obstacle detection (not continuous)
- Memory: ~1-5 KB per saved mission state

## Future Enhancements

1. Support for LIDAR sensors via USB/Serial
2. Support for ultrasonic rangefinders
3. Multi-obstacle tracking
4. 3D obstacle mapping
5. Automatic path planning around obstacles
6. Machine learning for obstacle classification
7. Integration with terrain databases
8. Collision prediction algorithms

## License & Credits

Based on detailed step-by-step guide for obstacle detection mission split.
Implements MAVLink protocol for drone communication.
Uses Room database for persistent storage.

