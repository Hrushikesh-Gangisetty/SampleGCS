package com.example.aerogcsclone.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.authentication.AuthViewModel
import com.example.aerogcsclone.authentication.LoginPage
import com.example.aerogcsclone.authentication.SignupPage
import com.example.aerogcsclone.authentication.WelcomeScreen
import com.example.aerogcsclone.uiconnection.ConnectionPage
import com.example.aerogcsclone.uimain.MainPage
import com.example.aerogcsclone.uimain.PlanScreen
import com.example.aerogcsclone.uimain.TopNavBar
import com.example.aerogcsclone.ui.components.PlotTemplatesScreen
import com.example.aerogcsclone.viewmodel.MissionTemplateViewModel

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Connection : Screen("connection")
    object Main : Screen("main")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Plan : Screen("plan")
    object PlotTemplates : Screen("plot_templates")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    val sharedViewModel: SharedViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val missionTemplateViewModel: MissionTemplateViewModel = viewModel()

    NavHost(navController = navController, startDestination = Screen.Welcome.route) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(navController = navController)
        }
        composable(Screen.Login.route) {
            LoginPage(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        composable(Screen.Signup.route) {
            SignupPage(
                navController = navController,
                authViewModel = authViewModel
            )
        }
        composable(Screen.Connection.route) {
            ConnectionPage(
                navController = navController,
                viewModel = sharedViewModel
            )
        }
        composable(Screen.Main.route) {
            MainPage(
                telemetryViewModel = sharedViewModel,
                authViewModel = authViewModel,
                navController = navController
            )
        }
        composable(Screen.Plan.route) {
            PlanScreen(
                telemetryViewModel = sharedViewModel,
                authViewModel = authViewModel,
                navController = navController,
                missionTemplateViewModel = missionTemplateViewModel
            )
        }
        composable(Screen.PlotTemplates.route) {
            PlotTemplatesScreenWrapper(
                navController = navController,
                authViewModel = authViewModel,
                telemetryViewModel = sharedViewModel,
                missionTemplateViewModel = missionTemplateViewModel
            )
        }
    }
}

@Composable
private fun PlotTemplatesScreenWrapper(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    telemetryViewModel: SharedViewModel,
    missionTemplateViewModel: MissionTemplateViewModel
) {
    val templates by missionTemplateViewModel.templates.collectAsState(initial = emptyList())
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopNavBar(
            telemetryState = telemetryState,
            authViewModel = authViewModel,
            navController = navController
        )

        PlotTemplatesScreen(
            templates = templates,
            onLoadTemplate = { template ->
                missionTemplateViewModel.loadTemplate(template.id)
                navController.navigate(Screen.Plan.route)
            },
            onDeleteTemplate = { template ->
                missionTemplateViewModel.deleteTemplate(template)
            },
            modifier = Modifier.weight(1f)
        )
    }
}
