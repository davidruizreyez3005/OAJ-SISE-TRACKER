package mx.sisetracker.data.net

import android.content.Context
import android.webkit.WebSettings
import mx.sisetracker.BuildConfig

object UserAgent {
    /** The WebView's default User-Agent with ` SiseTracker/<versionName>` appended. Slow the first time: call off the main thread. */
    fun create(context: Context): String {
        val webView = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: RuntimeException) {
            // No usable WebView on this device.
            System.getProperty("http.agent").orEmpty()
        }
        return "$webView SiseTracker/${BuildConfig.VERSION_NAME}".trim()
    }
}
