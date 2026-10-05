package mx.sisetracker.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import mx.sisetracker.R
import mx.sisetracker.ui.capture.CaptureSheet
import mx.sisetracker.ui.home.HomeScreen
import mx.sisetracker.ui.portal.PortalScreen
import mx.sisetracker.ui.search.SearchScreen

@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data class PortalRoute(val circuito: String)

@Composable
fun SiseNavHost(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    // Saving arrives with "Mis expedientes" (milestone 6).
    val saveNotAvailable = {
        Toast.makeText(context, R.string.save_not_available_yet, Toast.LENGTH_SHORT).show()
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(onSearchClick = { navController.navigate(SearchRoute) })
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenPortal = { circuito -> navController.navigate(PortalRoute(circuito)) },
                onCaseLinkFound = appViewModel::onCaseCaptured,
                onSave = { _, _ -> saveNotAvailable() },
            )
        }
        composable<PortalRoute> { entry ->
            PortalScreen(
                circuito = entry.toRoute<PortalRoute>().circuito,
                onBack = { navController.popBackStack() },
                onCaseCaptured = appViewModel::onCaseCaptured,
            )
        }
    }

    val captured by appViewModel.capturedCase.collectAsStateWithLifecycle()
    captured?.let { url ->
        CaptureSheet(
            url = url,
            onSave = {
                saveNotAvailable()
                appViewModel.onCaptureDismissed()
            },
            onDismiss = appViewModel::onCaptureDismissed,
        )
    }
}
