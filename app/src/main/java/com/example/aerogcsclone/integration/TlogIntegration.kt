package com.example.aerogcsclone.integration

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.aerogcsclone.manager.UnifiedFlightTracker
import com.example.aerogcsclone.telemetry.SharedViewModel
import com.example.aerogcsclone.viewmodel.TlogViewModel

/**
 * Singleton integration helper to set up automatic flight logging
 * Now uses UnifiedFlightTracker for proper state machine-based flight tracking
 */
object TlogIntegration {
    private var unifiedFlightTracker: UnifiedFlightTracker? = null
    private var isInitialized = false

    fun initialize(
        application: Application,
        viewModelStoreOwner: ViewModelStoreOwner,
        telemetryViewModel: SharedViewModel
    ) {
        if (isInitialized) return

        try {
            // Create TlogViewModel
            val tlogViewModel = ViewModelProvider(
                viewModelStoreOwner,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[TlogViewModel::class.java]

            // Initialize UnifiedFlightTracker for automatic logging
            // This replaces the old FlightManager with a proper state machine implementation
            unifiedFlightTracker = UnifiedFlightTracker(
                context = application,
                tlogViewModel = tlogViewModel,
                sharedViewModel = telemetryViewModel
            )

            isInitialized = true
        } catch (e: Exception) {
            // Handle initialization error gracefully
            e.printStackTrace()
        }
    }

    fun destroy() {
        unifiedFlightTracker?.destroy()
        unifiedFlightTracker = null
        isInitialized = false
    }

    fun isInitialized(): Boolean = isInitialized
}
