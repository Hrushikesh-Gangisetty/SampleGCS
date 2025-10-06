package com.example.aerogcsclone.integration

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
//import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.manager.FlightManager
import com.example.aerogcsclone.telemetry.SharedViewModel
import com.example.aerogcsclone.viewmodel.TlogViewModel

/**
 * Singleton integration helper to set up automatic flight logging
 */
object TlogIntegration {
    private var flightManager: FlightManager? = null
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

            // Initialize FlightManager for automatic logging
            flightManager = FlightManager(
                context = application,
                tlogViewModel = tlogViewModel,
                telemetryViewModel = telemetryViewModel
            )

            isInitialized = true
        } catch (e: Exception) {
            // Handle initialization error gracefully
            e.printStackTrace()
        }
    }

    fun destroy() {
        flightManager?.destroy()
        flightManager = null
        isInitialized = false
    }

    fun isInitialized(): Boolean = isInitialized
}
