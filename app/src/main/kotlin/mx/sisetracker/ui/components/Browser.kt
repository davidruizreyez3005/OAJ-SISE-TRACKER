package mx.sisetracker.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import mx.sisetracker.R

/**
 * Opens a link in a Custom Tab (or the browser). Used for the resolución
 * documents, which are cleartext http:// links the app must never fetch itself.
 */
fun Context.openInBrowser(url: String, @ColorInt toolbarColor: Int) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
        .build()
    try {
        intent.launchUrl(this, url.toUri())
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_no_browser, Toast.LENGTH_LONG).show()
    }
}
