package com.example.aerogcsclone.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.uiconnection.ConnectionPage
import com.example.aerogcsclone.uimain.MainPage

import com.example.aerogcsclone.uimain.AutomaticModeScreen

sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object Main : Screen("main")
    object Automatic : Screen("automatic")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    val sharedViewModel: SharedViewModel = viewModel()

    NavHost(navController = navController, startDestination = Screen.Connection.route) {
        composable(Screen.Connection.route) {
            ConnectionPage(
                navController = navController,
                viewModel = sharedViewModel
            )
        }
        composable(Screen.Main.route) {
            MainPage(navController = navController, telemetryViewModel = sharedViewModel)
        }
        composable(Screen.Automatic.route) {
            AutomaticModeScreen(navController = navController, telemetryViewModel = sharedViewModel)
        }
    }
}
