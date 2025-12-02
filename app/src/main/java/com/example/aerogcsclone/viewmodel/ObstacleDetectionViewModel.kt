package com.example.aerogcsclone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divpundir.mavlink.definitions.common.MissionItemInt
import com.example.aerogcsclone.obstacle.*
import com.example.aerogcsclone.telemetry.SharedViewModel
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing obstacle detection system
 * Integrates with SharedViewModel for telemetry and mission control
 */
class ObstacleDetectionViewModel(
    application: Application,
    private val sharedViewModel: SharedViewModel
) : AndroidViewModel(application) {

    // Obstacle detection manager
    private var detectionManager: ObstacleDetectionManager? = null

    private var isInitialized = false

    // Expose state flows
    private val _detectionStatus = MutableStateFlow(ObstacleDetectionStatus.INACTIVE)
    val detectionStatus: StateFlow<ObstacleDetectionStatus> = _detectionStatus.asStateFlow()

    private val _currentObstacle = MutableStateFlow<ObstacleInfo?>(null)
    val currentObstacle: StateFlow<ObstacleInfo?> = _currentObstacle.asStateFlow()

    private val _resumeOptions = MutableStateFlow<List<ResumeOption>>(emptyList())
    val resumeOptions: StateFlow<List<ResumeOption>> = _resumeOptions.asStateFlow()

    /**
     * Initialize the obstacle detection system
     * Must provide DAO from outside to avoid circular dependency
     */
    fun initialize(
        missionStateRepository: MissionStateRepository,
        config: ObstacleDetectionConfig = ObstacleDetectionConfig()
    ) {
        if (isInitialized) return

        detectionManager = ObstacleDetectionManager(
            context = getApplication<Application>().applicationContext,
            sharedViewModel = sharedViewModel,
            missionStateRepository = missionStateRepository,
            config = config
        )

        val success = detectionManager?.initialize() ?: false
        if (success) {
            isInitialized = true

            // Observe manager state flows
            viewModelScope.launch {
                detectionManager?.detectionStatus?.collect {
                    _detectionStatus.value = it
                }
            }
            viewModelScope.launch {
                detectionManager?.currentObstacle?.collect {
                    _currentObstacle.value = it
                }
            }
            viewModelScope.launch {
                detectionManager?.resumeOptions?.collect {
                    _resumeOptions.value = it
                }
            }

            android.util.Log.i("ObstacleDetectionVM", "✅ Obstacle detection system initialized")
        } else {
            android.util.Log.e("ObstacleDetectionVM", "❌ Failed to initialize obstacle detection system")
        }
    }

    /**
     * Start monitoring for obstacles during mission
     */
    fun startMissionMonitoring(
        waypoints: List<MissionItemInt>,
        homeLocation: LatLng,
        surveyPolygon: List<LatLng> = emptyList(),
        altitude: Float = 30f,
        speed: Float = 12f
    ) {
        if (!isInitialized) {
            android.util.Log.e("ObstacleDetectionVM", "System not initialized")
            return
        }

        val parameters = MissionParameters(
            altitude = altitude,
            speed = speed,
            loiterRadius = 10f,
            rtlAltitude = 60f,
            descentRate = 2f
        )

        detectionManager?.startMissionMonitoring(
            waypoints = waypoints,
            home = homeLocation,
            polygon = surveyPolygon,
            parameters = parameters
        )
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        detectionManager?.stopMissionMonitoring()
    }

    /**
     * Resume mission from selected waypoint
     */
    fun resumeMission(selectedOption: ResumeOption) {
        if (!isInitialized) return

        viewModelScope.launch {
            val success = detectionManager?.resumeMissionFromWaypoint(selectedOption) ?: false
            if (success) {
                android.util.Log.i("ObstacleDetectionVM", "✅ Mission resume initiated")
            } else {
                android.util.Log.e("ObstacleDetectionVM", "❌ Mission resume failed")
            }
        }
    }

    /**
     * Resume monitoring after mission uploaded and armed
     */
    fun resumeMonitoring() {
        detectionManager?.resumeMonitoring()
    }

    /**
     * Complete mission
     */
    fun completeMission() {
        detectionManager?.completeMission()
    }

    /**
     * Inject simulated obstacle for testing
     */
    fun injectSimulatedObstacle(distance: Float) {
        detectionManager?.injectSimulatedObstacle(distance)
    }

    fun getDetectionManager(): ObstacleDetectionManager? = detectionManager

    override fun onCleared() {
        super.onCleared()
        detectionManager?.cleanup()
    }
}
