package com.example.aerogcsclone

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import com.example.aerogcsclone.navigation.AppNavGraph
import com.google.android.gms.maps.MapsInitializer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Splash ViewModel
class SplashViewModel : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    // This state will be used to navigate to the main screen
    private val _startMainScreen = MutableStateFlow(false)
    val startMainScreen = _startMainScreen.asStateFlow()

    init {
        viewModelScope.launch {
            delay(2000) // Simulate a delay
            _isLoading.value = false
            _startMainScreen.value = true
        }
    }
}

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

    private val splashViewModel: SplashViewModel by viewModels()
    private val hasPermission = mutableStateOf(false)

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasPermission.value = fine || coarse
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val splashScreen = installSplashScreen()
            splashScreen.setKeepOnScreenCondition {
                splashViewModel.isLoading.value
            }
        }

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
                    val startMainScreen by splashViewModel.startMainScreen.collectAsState()
                    val showContent = remember { mutableStateOf(false) }

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        LaunchedEffect(Unit) {
                            delay(2000) // Fallback splash screen delay
                            showContent.value = true
                        }

                        if (!showContent.value) {
                            SplashScreen()
                        } else {
                            AppNavGraph(navController = navController)
                        }
                    } else {
                        if (startMainScreen) {
                            showContent.value = true
                        }

                        if (showContent.value) {
                            AppNavGraph(navController = navController)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        askLocationPermissions()
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

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Make sure you have a drawable named 'pavaman_logo.png' in your res/drawable folder.
        Image(
            painter = painterResource(id = R.drawable.pavaman_logo),
            contentDescription = "Splash Screen Logo"
        )
    }
}