package mx.sisetracker.ui.portal

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Collections
import mx.sisetracker.R
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.SiseUrls
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.SiseTopAppBar

/**
 * "Abrir en el portal": the portal's own search in a WebView, where the user
 * picks the órgano and solves the captcha by hand (the app never touches it,
 * hard rule 1). When the page loads a case, the app offers to save it.
 */
@Composable
fun PortalScreen(
    circuito: String,
    onBack: () -> Unit,
    onCaseCaptured: (CaseUrl) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val captured by rememberUpdatedState(onCaseCaptured)

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    Scaffold(
        topBar = {
            SiseTopAppBar(
                title = stringResource(R.string.portal_title),
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.portal_reload))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        configure()
                        webViewClient = CaseCapturingClient(
                            onCaseUrl = { url -> captured(url) },
                            onHistoryChanged = { view -> canGoBack = view.canGoBack() },
                            onLoading = { loading = it },
                        )
                        loadUrl(SiseUrls.circuitos(circuito))
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

// The portal needs JavaScript for its forms and its reCAPTCHA. The app adds no
// JavaScript interface and injects nothing.
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configure() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    // reCAPTCHA runs in a google.com iframe inside the portal's page.
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

/**
 * Watches the WebView's requests for the case page (`vercaptura.aspx`) without
 * changing anything: [shouldInterceptRequest] always returns null, so the
 * WebView loads every request itself. Each case is offered once per visit.
 */
private class CaseCapturingClient(
    private val onCaseUrl: (CaseUrl) -> Unit,
    private val onHistoryChanged: (WebView) -> Unit,
    private val onLoading: (Boolean) -> Unit,
) : WebViewClient() {
    private val offered: MutableSet<CaseUrl> = Collections.synchronizedSet(mutableSetOf())

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val path = request.url.path.orEmpty()
        if (path.endsWith(CASE_PAGE, ignoreCase = true)) {
            // Query values kept exactly as the portal sent them.
            val case = CaseUrl.parse(request.url.toString())
            if (case != null && offered.add(case)) view.post { onCaseUrl(case) }
        }
        return null
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        onHistoryChanged(view)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        onLoading(true)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onLoading(false)
    }

    private companion object {
        const val CASE_PAGE = "/vercaptura.aspx"
    }
}
