package mx.sisetracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import mx.sisetracker.core.OrganoTitle
import mx.sisetracker.ui.acuerdo.AcuerdoScreen
import mx.sisetracker.ui.capture.CaptureSheet
import mx.sisetracker.ui.cases.CaseScreen
import mx.sisetracker.ui.home.HomeScreen
import mx.sisetracker.ui.portal.PortalScreen
import mx.sisetracker.ui.search.SearchScreen

@Serializable
data object HomeRoute

/** The search screen, optionally pre-filled from a related case ("Buscar este expediente"). */
@Serializable
data class SearchRoute(
    val expediente: String? = null,
    val organoName: String? = null,
    val tipoAsuntoName: String? = null,
)

@Serializable
data class PortalRoute(val circuito: String)

@Serializable
data class CaseRoute(val neun: String)

@Serializable
data class AcuerdoRoute(val neun: String, val orden: Int)

@Composable
fun SiseNavHost(
    appViewModel: AppViewModel,
    navController: NavHostController = rememberNavController(),
) {
    // A newly saved case opens on top of "Mis expedientes".
    val openSavedCase: (String) -> Unit = { neun ->
        navController.navigate(CaseRoute(neun)) {
            popUpTo<HomeRoute>()
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onSearchClick = { navController.navigate(SearchRoute()) },
                onOpenCase = { neun -> navController.navigate(CaseRoute(neun)) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenPortal = { circuito -> navController.navigate(PortalRoute(circuito)) },
                onCaseLinkFound = appViewModel::onCaseCaptured,
                onOpenCase = openSavedCase,
            )
        }
        composable<PortalRoute> { entry ->
            PortalScreen(
                circuito = entry.toRoute<PortalRoute>().circuito,
                onBack = { navController.popBackStack() },
                onCaseCaptured = appViewModel::onCaseCaptured,
            )
        }
        composable<CaseRoute> { entry ->
            val neun = entry.toRoute<CaseRoute>().neun
            CaseScreen(
                onBack = { navController.popBackStack() },
                onOpenAcuerdo = { orden -> navController.navigate(AcuerdoRoute(neun, orden)) },
                onSearchRelated = { related ->
                    navController.navigate(
                        SearchRoute(
                            expediente = related.expediente,
                            organoName = OrganoTitle.organo(related.organo),
                            tipoAsuntoName = OrganoTitle.tipoAsunto(related.organo),
                        ),
                    )
                },
                onOpenCase = { other -> navController.navigate(CaseRoute(other)) },
            )
        }
        composable<AcuerdoRoute> {
            AcuerdoScreen(onBack = { navController.popBackStack() })
        }
    }

    val capture by appViewModel.capture.collectAsStateWithLifecycle()
    capture?.let { state ->
        CaptureSheet(
            state = state,
            onSave = appViewModel::onSaveCaptured,
            onDismiss = appViewModel::onCaptureDismissed,
        )
    }

    val openCase by appViewModel.openCase.collectAsStateWithLifecycle()
    LaunchedEffect(openCase) {
        openCase?.let { neun ->
            openSavedCase(neun)
            appViewModel.onCaseOpened()
        }
    }
}
