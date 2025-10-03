package com.example.aerogcsclone

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.example.aerogcsclone.navigation.AppNavGraph
import com.example.aerogcsclone.integration.TlogIntegration
import com.example.aerogcsclone.Telemetry.SharedViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.MapsInitializer

// ✅ Dark theme setup
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1E88E5),
    onPrimary = Color.White,
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

class MainActivity : ComponentActivity() {

    private val hasPermission = mutableStateOf(false)

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission.value = fine || coarse
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // Initialize Maps SDK with new API
        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) {
            // You can log or handle the chosen renderer here
        }

        setContent {
            val navController = rememberNavController()
            MaterialTheme(colorScheme = DarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        askLocationPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup TlogIntegration when activity is destroyed
        TlogIntegration.destroy()
    }

    private fun askLocationPermissions() {
        requestLocation.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}