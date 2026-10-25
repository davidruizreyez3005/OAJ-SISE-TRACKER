package mx.sisetracker.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import mx.sisetracker.R
import mx.sisetracker.core.OrganoTitle
import mx.sisetracker.ui.acuerdo.AcuerdoScreen
import mx.sisetracker.ui.capture.CaptureSheet
import mx.sisetracker.ui.capture.ImportDialog
import mx.sisetracker.ui.cases.CaseScreen
import mx.sisetracker.ui.components.SiseMotion
import mx.sisetracker.ui.home.HomeScreen
import mx.sisetracker.ui.portal.PortalScreen
import mx.sisetracker.ui.search.SearchScreen
import mx.sisetracker.ui.searchacuerdos.AcuerdoSearchScreen
import mx.sisetracker.ui.settings.SettingsScreen

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

@Serializable
data object AcuerdoSearchRoute

@Serializable
data object SettingsRoute

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

    // Forward screens slide in from the side over a fade; back reverses it.
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, SiseMotion.spec()) +
                fadeIn(SiseMotion.spec())
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, SiseMotion.spec()) { it / 4 } +
                fadeOut(SiseMotion.spec())
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, SiseMotion.spec()) { it / 4 } +
                fadeIn(SiseMotion.spec())
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, SiseMotion.spec()) +
                fadeOut(SiseMotion.spec())
        },
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onSearchClick = { navController.navigate(SearchRoute()) },
                onOpenCase = { neun -> navController.navigate(CaseRoute(neun)) },
                onSearchAcuerdos = { navController.navigate(AcuerdoSearchRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onCaseLinkFound = appViewModel::onCaseCaptured,
            )
        }
        composable<AcuerdoSearchRoute> {
            AcuerdoSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenAcuerdo = { neun, orden -> navController.navigate(AcuerdoRoute(neun, orden)) },
                onOpenCase = { neun -> navController.navigate(CaseRoute(neun)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() }, onImportFile = appViewModel::onImportFile)
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

    val import by appViewModel.import.collectAsStateWithLifecycle()
    import?.let { state ->
        ImportDialog(state = state, onConfirm = appViewModel::onConfirmImport, onDismiss = appViewModel::onImportDismissed)
    }

    val context = LocalContext.current
    val message by appViewModel.message.collectAsStateWithLifecycle()
    val messageText = when (val shown = message) {
        is AppMessage.Imported -> pluralStringResource(R.plurals.import_done, shown.count, shown.count)
        AppMessage.NoLinks -> stringResource(R.string.import_no_links)
        null -> null
    }
    LaunchedEffect(message) {
        val text = messageText ?: return@LaunchedEffect
        appViewModel.onMessageShown()
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    val openCase by appViewModel.openCase.collectAsStateWithLifecycle()
    LaunchedEffect(openCase) {
        openCase?.let { neun ->
            openSavedCase(neun)
            appViewModel.onCaseOpened()
        }
    }
}
