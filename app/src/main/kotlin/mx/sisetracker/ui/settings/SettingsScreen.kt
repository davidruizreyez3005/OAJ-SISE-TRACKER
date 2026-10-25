package mx.sisetracker.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import mx.sisetracker.BuildConfig
import mx.sisetracker.R
import mx.sisetracker.core.Circuitos
import mx.sisetracker.data.capture.DownloadsSaver
import mx.sisetracker.data.net.PortalError
import mx.sisetracker.data.settings.CheckReport
import mx.sisetracker.data.settings.CrawlStatus
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.data.transfer.CaseListTransfer
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.SiseMotion
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.messageRes
import mx.sisetracker.ui.components.rememberNotificationPermission
import mx.sisetracker.ui.components.siseCardBorder
import mx.sisetracker.ui.components.siseCardColors
import mx.sisetracker.ui.lock.LocalDeviceUnlock

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportFile: (Uri) -> Unit = {},
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
        val outgoing = shareFile ?: return@LaunchedEffect
        viewModel.onShareHandled()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", outgoing.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(outgoing.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, shareTitle))
    }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val savedText = message?.let {
        when (it) {
            is SettingsMessage.Saved -> stringResource(R.string.settings_capture_saved, it.fileName)
            SettingsMessage.SaveFailed -> stringResource(R.string.settings_capture_save_failed)
            SettingsMessage.NothingToExport -> stringResource(R.string.settings_export_empty)
        }
    }
    // Shown in the screen's scope: clearing the message changes this effect's
    // key, which would cancel a snackbar shown from the effect itself.
    val scope = rememberCoroutineScope()
    LaunchedEffect(message) {
        val text = savedText ?: return@LaunchedEffect
        viewModel.onMessageShown()
        scope.launch { snackbar.showSnackbar(text) }
    }
    // Before Android 10 there's no permission-free way into Downloads: let the user pick the file.
    val createZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DownloadsSaver.MIME_TYPE),
        viewModel::onSaveLocationPicked,
    )
    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CaseListTransfer.MIME_TYPE),
        viewModel::onSaveLocationPicked,
    )
    val pickSaveLocation by viewModel.pickSaveLocation.collectAsStateWithLifecycle()
    LaunchedEffect(pickSaveLocation) {
        val outgoing = pickSaveLocation ?: return@LaunchedEffect
        viewModel.onPickSaveLocationHandled()
        (if (outgoing.mimeType == CaseListTransfer.MIME_TYPE) createText else createZip).launch(outgoing.file.name)
    }
    val openList = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onImportFile) }
    val deviceUnlock = LocalDeviceUnlock.current
    var lockUnavailable by remember { mutableStateOf(false) }
    val onLockToggle: (Boolean) -> Unit = { enable ->
        when {
            !enable -> viewModel.onAppLockChange(false)
            deviceUnlock?.available == true -> deviceUnlock.authenticate { unlocked -> if (unlocked) viewModel.onAppLockChange(true) }
            else -> lockUnavailable = true
        }
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
        val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_section_avisos), rememberVectorPainter(Icons.Filled.Notifications)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_daily_check)) },
                    supportingContent = { Text(stringResource(R.string.settings_daily_check_help)) },
                    trailingContent = { Switch(checked = state.dailyCheck, onCheckedChange = viewModel::onDailyCheckChange) },
                    colors = transparent,
                    modifier = Modifier.clickable { viewModel.onDailyCheckChange(!state.dailyCheck) },
                )
                AnimatedVisibility(
                    visible = state.dailyCheck,
                    enter = fadeIn(SiseMotion.spec()) + expandVertically(SiseMotion.spec()),
                    exit = fadeOut(SiseMotion.spec()) + shrinkVertically(SiseMotion.spec()),
                ) {
                    Column {
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
                        CheckStatus(
                            report = state.lastCheck,
                            intervalDays = state.intervalDays,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                        if (!permission.granted) {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.notifications_off)) },
                                supportingContent = { Text(stringResource(R.string.notifications_off_help)) },
                                trailingContent = {
                                    TextButton(onClick = permission.request) { Text(stringResource(R.string.notifications_allow)) }
                                },
                                colors = transparent,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_hearing_reminders)) },
                    supportingContent = { Text(stringResource(R.string.settings_hearing_reminders_help)) },
                    trailingContent = {
                        Switch(checked = state.hearingReminders, onCheckedChange = viewModel::onHearingRemindersChange)
                    },
                    colors = transparent,
                    modifier = Modifier.clickable { viewModel.onHearingRemindersChange(!state.hearingReminders) },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_expedientes), painterResource(R.drawable.ic_folder)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.settings_export_help))
                            FlowRow {
                                TextButton(onClick = viewModel::onSaveList) { Text(stringResource(R.string.settings_capture_save)) }
                                TextButton(onClick = viewModel::onShareList) { Text(stringResource(R.string.settings_capture_share)) }
                            }
                        }
                    },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_upload), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = transparent,
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_import)) },
                    supportingContent = { Text(stringResource(R.string.settings_import_help)) },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = transparent,
                    modifier = Modifier.clickable { openList.launch(arrayOf("text/*")) },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_privacidad), rememberVectorPainter(Icons.Filled.Lock)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_app_lock)) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.settings_app_lock_help))
                            if (lockUnavailable) {
                                Text(
                                    stringResource(R.string.settings_app_lock_unavailable),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    },
                    trailingContent = { Switch(checked = state.appLock, onCheckedChange = onLockToggle) },
                    colors = transparent,
                    modifier = Modifier.clickable { onLockToggle(!state.appLock) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        stringResource(R.string.settings_recents_hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            SettingsSection(stringResource(R.string.settings_section_catalogos), painterResource(R.drawable.ic_account_balance)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_refresh_catalogs)) },
                    supportingContent = { Text(stringResource(R.string.settings_refresh_catalogs_help)) },
                    leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = transparent,
                    modifier = Modifier.clickable(onClick = viewModel::onRefreshCatalogs),
                )
            }

            SettingsSection(stringResource(R.string.settings_section_diagnostico), rememberVectorPainter(Icons.Filled.Build)) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_capture)) },
                    supportingContent = { Text(stringResource(R.string.settings_capture_help)) },
                    trailingContent = { Switch(checked = state.captureEnabled, onCheckedChange = viewModel::onCaptureChange) },
                    colors = transparent,
                    modifier = Modifier.clickable { viewModel.onCaptureChange(!state.captureEnabled) },
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
                    colors = transparent,
                    modifier = Modifier.clickable { onCrawlToggle(!state.crawlEnabled) },
                )
                if (state.captureCount > 0 || state.captureEnabled || state.crawlEnabled) {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
                        colors = transparent,
                    )
                }
            }

            SettingsSection(stringResource(R.string.settings_section_acerca), rememberVectorPainter(Icons.Filled.Info)) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                    },
                    supportingContent = if (BuildConfig.DEBUG) {
                        { Text(stringResource(R.string.settings_version_debug)) }
                    } else {
                        null
                    },
                    colors = transparent,
                )
            }

            MessageCard(
                text = stringResource(R.string.settings_privacy),
                icon = rememberVectorPainter(Icons.Filled.Lock),
            )
        }
    }
}

private val reportTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private fun formatTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(reportTime)

/**
 * What the last daily check did: when, how many cases it read out of how
 * many, how many had news, and whether a connection failure stopped it;
 * then roughly when the next one runs (Android picks the exact time).
 */
@Composable
private fun CheckStatus(report: CheckReport?, intervalDays: Int, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (report == null) {
            Text(stringResource(R.string.settings_check_never), style = MaterialTheme.typography.bodySmall, color = muted)
            return@Column
        }
        Text(stringResource(R.string.settings_check_last, formatTime(report.startedAt)), style = MaterialTheme.typography.bodyMedium)
        val counts = buildList {
            add(pluralStringResource(R.plurals.settings_check_counts, report.total, report.checked, report.total))
            if (report.notified > 0) add(pluralStringResource(R.plurals.settings_check_new, report.notified, report.notified))
            if (report.loaded > 0) add(pluralStringResource(R.plurals.settings_check_loaded, report.loaded, report.loaded))
        }
        Text(counts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted)
        report.error?.let { name -> PortalError.entries.firstOrNull { it.name == name } }?.let { error ->
            Text(
                stringResource(R.string.settings_check_stopped, stringResource(error.messageRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            stringResource(R.string.settings_check_next, formatTime(report.startedAt + TimeUnit.DAYS.toMillis(intervalDays.toLong()))),
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

/** A titled group of settings: a gold title with its icon over a card. */
@Composable
private fun SettingsSection(title: String, icon: Painter, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = siseCardColors(),
            border = siseCardBorder(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
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
