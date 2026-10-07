package mx.sisetracker.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import mx.sisetracker.R
import mx.sisetracker.core.Circuitos
import mx.sisetracker.data.capture.DownloadsSaver
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.settings.CrawlStatus
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.messageRes
import mx.sisetracker.ui.components.rememberNotificationPermission

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clearedMessage = stringResource(R.string.settings_catalogs_cleared)
    LaunchedEffect(state.catalogsCleared) {
        if (state.catalogsCleared) {
            snackbar.showSnackbar(clearedMessage)
            viewModel.onCatalogsMessageShown()
        }
    }
    val permission = rememberNotificationPermission()
    val context = LocalContext.current
    val shareFile by viewModel.shareFile.collectAsStateWithLifecycle()
    val shareTitle = stringResource(R.string.settings_capture_share)
    LaunchedEffect(shareFile) {
        val file = shareFile ?: return@LaunchedEffect
        viewModel.onShareHandled()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, shareTitle))
    }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val savedText = message?.let {
        when (it) {
            is SettingsMessage.Saved -> stringResource(R.string.settings_capture_saved, it.fileName)
            SettingsMessage.SaveFailed -> stringResource(R.string.settings_capture_save_failed)
        }
    }
    LaunchedEffect(message) {
        val text = savedText ?: return@LaunchedEffect
        viewModel.onMessageShown()
        snackbar.showSnackbar(text)
    }
    // Before Android 10 there's no permission-free way into Downloads: let the user pick the file.
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DownloadsSaver.MIME_TYPE),
        viewModel::onSaveLocationPicked,
    )
    val pickSaveLocation by viewModel.pickSaveLocation.collectAsStateWithLifecycle()
    LaunchedEffect(pickSaveLocation) {
        val zip = pickSaveLocation ?: return@LaunchedEffect
        viewModel.onPickSaveLocationHandled()
        createDocument.launch(zip.name)
    }
    var confirmCrawl by remember { mutableStateOf(false) }
    if (confirmCrawl) {
        AlertDialog(
            onDismissRequest = { confirmCrawl = false },
            title = { Text(stringResource(R.string.settings_crawl_confirm_title)) },
            text = { Text(stringResource(R.string.settings_crawl_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCrawl = false
                        viewModel.onCrawlChange(true)
                    },
                ) { Text(stringResource(R.string.settings_crawl_start)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCrawl = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    val onCrawlToggle: (Boolean) -> Unit = { enable -> if (enable) confirmCrawl = true else viewModel.onCrawlChange(false) }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshCaptureCount()
        onPauseOrDispose {}
    }

    Scaffold(
        topBar = { SiseTopAppBar(stringResource(R.string.settings_title), navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_daily_check)) },
                supportingContent = { Text(stringResource(R.string.settings_daily_check_help)) },
                trailingContent = { Switch(checked = state.dailyCheck, onCheckedChange = viewModel::onDailyCheckChange) },
                modifier = Modifier.clickable { viewModel.onDailyCheckChange(!state.dailyCheck) },
            )
            if (state.dailyCheck) {
                var days by remember(state.intervalDays) { mutableFloatStateOf(state.intervalDays.toFloat()) }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        pluralStringResource(R.plurals.settings_interval, days.roundToInt(), days.roundToInt()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = days,
                        onValueChange = { days = it },
                        onValueChangeFinished = { viewModel.onIntervalChange(days.roundToInt()) },
                        valueRange = SettingsStore.MIN_INTERVAL_DAYS.toFloat()..SettingsStore.MAX_INTERVAL_DAYS.toFloat(),
                        steps = SettingsStore.MAX_INTERVAL_DAYS - SettingsStore.MIN_INTERVAL_DAYS - 1,
                    )
                }
                if (!permission.granted) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.notifications_off)) },
                        supportingContent = { Text(stringResource(R.string.notifications_off_help)) },
                        trailingContent = {
                            TextButton(onClick = permission.request) { Text(stringResource(R.string.notifications_allow)) }
                        },
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_refresh_catalogs)) },
                supportingContent = { Text(stringResource(R.string.settings_refresh_catalogs_help)) },
                modifier = Modifier.clickable(onClick = viewModel::onRefreshCatalogs),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_capture)) },
                supportingContent = { Text(stringResource(R.string.settings_capture_help)) },
                trailingContent = { Switch(checked = state.captureEnabled, onCheckedChange = viewModel::onCaptureChange) },
                modifier = Modifier.clickable { viewModel.onCaptureChange(!state.captureEnabled) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_crawl)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.settings_crawl_help))
                        crawlStatusText(state.crawlStatus)?.let { status ->
                            Text(
                                status,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                },
                trailingContent = { Switch(checked = state.crawlEnabled, onCheckedChange = onCrawlToggle) },
                modifier = Modifier.clickable { onCrawlToggle(!state.crawlEnabled) },
            )
            if (state.captureCount > 0 || state.captureEnabled || state.crawlEnabled) {
                ListItem(
                    headlineContent = {
                        Text(pluralStringResource(R.plurals.settings_capture_count, state.captureCount, state.captureCount))
                    },
                    supportingContent = {
                        FlowRow {
                            TextButton(onClick = viewModel::onSaveCaptures, enabled = state.captureCount > 0) {
                                Text(stringResource(R.string.settings_capture_save))
                            }
                            TextButton(onClick = viewModel::onShareCaptures, enabled = state.captureCount > 0) {
                                Text(stringResource(R.string.settings_capture_share))
                            }
                            TextButton(onClick = viewModel::onClearCaptures, enabled = state.captureCount > 0) {
                                Text(stringResource(R.string.action_delete))
                            }
                        }
                    },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun crawlStatusText(status: CrawlStatus): String? {
    val error = status.error?.let { name -> PortalError.entries.firstOrNull { it.name == name } }
    val errorText = error?.let { stringResource(it.messageRes()) }.orEmpty()
    return when (status.state) {
        CrawlStatus.State.IDLE -> null
        CrawlStatus.State.WAITING ->
            if (error == null) stringResource(R.string.crawl_status_queued) else stringResource(R.string.crawl_status_retry, errorText)
        CrawlStatus.State.RUNNING -> stringResource(
            R.string.crawl_status_running,
            status.circuito?.let(Circuitos::byNum)?.label ?: status.circuito.orEmpty(),
        )
        CrawlStatus.State.FINISHED -> stringResource(R.string.crawl_status_finished)
        CrawlStatus.State.STOPPED ->
            if (error == null) stringResource(R.string.crawl_status_stopped) else stringResource(R.string.crawl_status_stopped_error, errorText)
    }
}
