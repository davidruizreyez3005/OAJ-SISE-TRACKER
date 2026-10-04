package mx.sisetracker

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import mx.sisetracker.ui.home.HomeScreen
import mx.sisetracker.ui.theme.SiseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableSiseEdgeToEdge()
        setContent {
            SiseTrackerTheme {
                HomeScreen(onSearchClick = {})
            }
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
