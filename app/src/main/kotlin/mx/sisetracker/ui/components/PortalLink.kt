package mx.sisetracker.ui.components

import android.content.ClipData
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import mx.sisetracker.R

/**
 * Copies a case's direct link on the portal (its `vercaptura.aspx` URL) to
 * the clipboard: the URL alone, never case text. It opens the case in any
 * browser with no captcha, and pastes back into the app ("Pegar enlace del
 * portal"). Where the system doesn't confirm copies itself, says so in
 * [snackbarHostState].
 */
@Composable
fun rememberCopyPortalLink(snackbarHostState: SnackbarHostState): (url: String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.portal_link_clip_label)
    val copied = stringResource(R.string.portal_link_copied)
    return remember(clipboard, scope, label, copied, snackbarHostState) {
        { url ->
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, url)))
                if (showsOwnCopyConfirmation()) snackbarHostState.showSnackbar(copied)
            }
        }
    }
}

/**
 * Android 13 and later confirm every copy themselves (a preview in the
 * corner), so the app adds its own "Enlace copiado" only on Android 12L and
 * lower, as the platform's guidance asks.
 */
fun showsOwnCopyConfirmation(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt <= Build.VERSION_CODES.S_V2
