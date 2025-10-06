package com.example.aerogcsclone.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.authentication.AuthViewModel
import com.example.aerogcsclone.authentication.LoginPage
import com.example.aerogcsclone.authentication.SignupPage
import com.example.aerogcsclone.authentication.WelcomeScreen
import com.example.aerogcsclone.integration.TlogIntegration
import com.example.aerogcsclone.uiconnection.ConnectionPage
import com.example.aerogcsclone.uiflyingmethod.SelectFlyingMethodScreen
import com.example.aerogcsclone.uimain.MainPage
import com.example.aerogcsclone.uimain.PlanScreen
import com.example.aerogcsclone.uimain.TopNavBar
import com.example.aerogcsclone.ui.components.PlotTemplatesScreen
import com.example.aerogcsclone.ui.logs.LogsScreen
import com.example.aerogcsclone.viewmodel.MissionTemplateViewModel
import com.example.aerogcsclone.viewmodel.TlogViewModel

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Connection : Screen("connection")
    object Main : Screen("main")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Plan : Screen("plan")
    object PlotTemplates : Screen("plot_templates")
    object Logs : Screen("logs")
    object SelectFlyingMethod : Screen("select_flying_method")
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    val sharedViewModel: SharedViewModel = viewModel()
    val authViewModel: AuthViewModel = viewModel()
    val missionTemplateViewModel: MissionTemplateViewModel = viewModel()

    // Initialize TlogIntegration with the SharedViewModel
    val context = LocalContext.current
    val activity = context as ComponentActivity

    LaunchedEffect(sharedViewModel) {
        TlogIntegration.initialize(
            application = activity.application,
            viewModelStoreOwner = activity,
            telemetryViewModel = sharedViewModel
        )
    }

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
        composable(Screen.SelectFlyingMethod.route) {
            SelectFlyingMethodScreen(navController = navController)
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
        composable(Screen.Logs.route) {
            LogsScreenWrapper(
                navController = navController,
                authViewModel = authViewModel,
                telemetryViewModel = sharedViewModel
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

@Composable
private fun LogsScreenWrapper(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    telemetryViewModel: SharedViewModel
) {
    val context = LocalContext.current
    val tlogViewModel: TlogViewModel = viewModel {
        TlogViewModel(context.applicationContext as Application)
    }

    LogsScreen(
        navController = navController,
        authViewModel = authViewModel,
        telemetryViewModel = telemetryViewModel,
        tlogViewModel = tlogViewModel
    )
}
