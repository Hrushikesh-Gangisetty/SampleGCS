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
//import com.example.aerogcsclone.Telemetry.SharedViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.MapsInitializer
import com.google.accompanist.systemuicontroller.rememberSystemUiController

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

    private val hasRequiredPermissions = mutableStateOf(false)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineLocation = grants.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
        val coarseLocation = grants.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)

        val bluetoothScanGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grants.getOrDefault(Manifest.permission.BLUETOOTH_SCAN, false)
        } else {
            true // Not required for older APIs
        }

        val bluetoothConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            grants.getOrDefault(Manifest.permission.BLUETOOTH_CONNECT, false)
        } else {
            true // Not required for older APIs
        }

        // For the app to function, we need location and the relevant BT permissions.
        hasRequiredPermissions.value = (fineLocation || coarseLocation) && bluetoothScanGranted && bluetoothConnectGranted
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
            val systemUiController = rememberSystemUiController()
            // Hide the system status bar for immersive experience
            SideEffect {
                systemUiController.isStatusBarVisible = false
            }
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
        askForPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup TlogIntegration when activity is destroyed
        TlogIntegration.destroy()
    }

    private fun askForPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }
}