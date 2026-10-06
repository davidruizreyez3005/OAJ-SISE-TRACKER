package mx.sisetracker

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.ui.AppViewModel
import mx.sisetracker.ui.SiseNavHost
import mx.sisetracker.ui.theme.SiseTrackerTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_CASE = "mx.sisetracker.action.OPEN_CASE"
        const val EXTRA_NEUN = "neun"
    }

    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSiseEdgeToEdge()
        // No autofill suggestions in the app's fields: nothing here is a
        // login or an address, and case numbers shouldn't go to autofill services.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (savedInstanceState == null) handleIntent(intent)
        scheduleDailyCheck()
        setContent {
            SiseTrackerTheme {
                SiseNavHost(appViewModel)
            }
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

    /** A `vercaptura.aspx` link shared from another app opens the save sheet. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val url = CaseUrl.findIn(text)
        if (url != null) {
            appViewModel.onCaseCaptured(url)
        } else {
            Toast.makeText(this, R.string.share_no_case_link, Toast.LENGTH_LONG).show()
        }
    }

    // The top app bar is `primary`: dark blue in the light theme (needs light
    // status bar icons) and light blue in the dark theme (needs dark icons).
    private fun enableSiseEdgeToEdge() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val statusBarStyle = if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        } else {
            SystemBarStyle.dark(Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = statusBarStyle)
    }
}
