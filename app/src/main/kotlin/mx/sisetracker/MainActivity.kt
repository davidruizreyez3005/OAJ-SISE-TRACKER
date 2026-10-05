package mx.sisetracker

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.ui.AppViewModel
import mx.sisetracker.ui.SiseNavHost
import mx.sisetracker.ui.theme.SiseTrackerTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSiseEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            SiseTrackerTheme {
                SiseNavHost(appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
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
