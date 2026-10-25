package mx.sisetracker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.ui.AppViewModel
import mx.sisetracker.ui.SiseNavHost
import mx.sisetracker.ui.lock.DeviceUnlock
import mx.sisetracker.ui.lock.LocalDeviceUnlock
import mx.sisetracker.ui.lock.LockScreen
import mx.sisetracker.ui.lock.LockState
import mx.sisetracker.ui.theme.SiseTrackerTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_CASE = "mx.sisetracker.action.OPEN_CASE"
        const val EXTRA_NEUN = "neun"
    }

    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }
    private val deviceUnlock = DeviceUnlock(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSiseEdgeToEdge()
        // No autofill suggestions in the app's fields: nothing here is a
        // login or an address, and case numbers shouldn't go to autofill services.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        // Case text stays out of the recent-apps screen (hard rule 6); screenshots still work.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        if (savedInstanceState == null) handleIntent(intent)
        scheduleDailyCheck()
        watchHearings()
        setContent {
            SiseTrackerTheme {
                // Outside the lock, so the screens the user was on come back after unlocking.
                val navController = rememberNavController()
                val lock by appViewModel.lock.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalDeviceUnlock provides deviceUnlock) {
                    when (lock) {
                        LockState.CHECKING -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        LockState.LOCKED -> LockScreen(onUnlock = ::unlock)
                        LockState.UNLOCKED -> SiseNavHost(appViewModel, navController)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onAppShown()
    }

    override fun onStop() {
        super.onStop()
        appViewModel.onAppHidden()
    }

    private fun unlock() {
        deviceUnlock.authenticate { unlocked -> if (unlocked) appViewModel.onUnlocked() }
    }

    /** Hearing reminders follow what's saved: saves, refreshes, deletes and the setting. */
    private fun watchHearings() {
        val container = (application as SiseApp).container
        lifecycleScope.launch {
            combine(
                container.caseRepository.observeHearings().distinctUntilChanged(),
                container.settings.hearingReminders.distinctUntilChanged(),
            ) { _, _ -> }
                .collectLatest { container.hearingReminders.update() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_CASE) {
            intent.getStringExtra(EXTRA_NEUN)?.let(appViewModel::openCase)
        } else {
            handleShare(intent)
        }
    }

    /** Keeps the existing schedule; Settings applies changes. */
    private fun scheduleDailyCheck() {
        val container = (application as SiseApp).container
        lifecycleScope.launch {
            container.dailyCheckScheduler.apply(
                enabled = container.settings.dailyCheckEnabled.first(),
                intervalDays = container.settings.checkIntervalDays.first(),
                replace = false,
            )
        }
    }

    /**
     * Shared from another app: a `vercaptura.aspx` link opens the save sheet,
     * several links or an exported list file the import dialog.
     */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val urls = CaseUrl.findAllIn(text)
        val file = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        when {
            urls.isNotEmpty() -> appViewModel.onLinksShared(urls)
            file != null -> appViewModel.onImportFile(file)
            else -> Toast.makeText(this, R.string.share_no_case_link, Toast.LENGTH_LONG).show()
        }
    }

    // The top app bar is deep guinda in both themes, so the status bar
    // always needs light icons.
    private fun enableSiseEdgeToEdge() {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
    }
}
