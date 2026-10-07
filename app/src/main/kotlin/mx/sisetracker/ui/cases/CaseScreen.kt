package mx.sisetracker.ui.cases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import mx.sisetracker.R
import mx.sisetracker.core.OrganoTitle
import mx.sisetracker.core.SiseDates
import mx.sisetracker.data.db.AcuerdoEntity
import mx.sisetracker.data.db.AsuntoRelacionadoEntity
import mx.sisetracker.data.db.CapturaEntryEntity
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.data.db.ResolucionEntity
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.NewBadge
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.theme.LocalBrandColors
import mx.sisetracker.ui.components.portalErrorText
import mx.sisetracker.ui.components.openInBrowser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseScreen(
    onBack: () -> Unit,
    onOpenAcuerdo: (orden: Int) -> Unit,
    onSearchRelated: (AsuntoRelacionadoEntity) -> Unit,
    onOpenCase: (neun: String) -> Unit,
    viewModel: CaseViewModel = viewModel(factory = CaseViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val toolbarColor = LocalBrandColors.current.topBar.toArgb()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val message = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            SiseTopAppBar(
                title = state.case?.expediente.orEmpty(),
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (state.case != null) {
                        IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.case_refresh))
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.case_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.case_open_portal)) },
                                onClick = {
                                    menuOpen = false
                                    state.case?.let { context.openInBrowser(it.caseUrl, toolbarColor) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.case_delete)) },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val case = state.case
        if (case == null) {
            if (state.loaded && !state.deleted) {
                Text(
                    stringResource(R.string.case_removed),
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                CaseHeader(case)
                val tabs = listOf(
                    stringResource(R.string.case_tab_acuerdos, state.acuerdos.size),
                    stringResource(R.string.case_tab_resoluciones, state.resoluciones.size),
                    stringResource(R.string.case_tab_relacionados, state.relacionados.size),
                    stringResource(R.string.case_tab_datos),
                )
                PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                    }
                }
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (tab) {
                            0 -> acuerdos(state.acuerdos, state.newOrdenes, onOpenAcuerdo)
                            1 -> resoluciones(state.resoluciones) { url -> context.openInBrowser(url, toolbarColor) }
                            2 -> relacionados(state.relacionados, state.savedNeuns, onSearchRelated, onOpenCase)
                            else -> captura(state.captura, case.partyCount)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.case_delete_title)) },
            text = { Text(stringResource(R.string.case_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun messageText(message: CaseMessage): String = when (message) {
    is CaseMessage.Refreshed -> if (message.newCount == 0) {
        stringResource(R.string.case_refreshed_none)
    } else {
        pluralStringResource(R.plurals.case_refreshed_new, message.newCount, message.newCount)
    }
    CaseMessage.NotFoundOnPortal -> stringResource(R.string.case_not_found_on_portal)
    is CaseMessage.Failed -> portalErrorText(message.error, message.location)
}

private val checkedFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun CaseHeader(case: CaseEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(case.organoName, style = MaterialTheme.typography.titleMedium)
        Text(
            case.tipoAsuntoName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val ids = buildList {
            add(stringResource(R.string.case_neun, case.neun))
            if (case.noControlOcc.isNotEmpty()) add(stringResource(R.string.case_occ, case.noControlOcc))
        }
        Text(ids.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        val checked = Instant.ofEpochMilli(case.lastCheckedAt).atZone(ZoneId.systemDefault()).format(checkedFormat)
        Text(
            stringResource(R.string.case_checked, checked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.emptyMessage(text: @Composable () -> String) {
    item {
        Text(text(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
    }
}

private fun LazyListScope.acuerdos(
    acuerdos: List<AcuerdoEntity>,
    newOrdenes: Set<Int>,
    onOpen: (Int) -> Unit,
) {
    if (acuerdos.isEmpty()) emptyMessage { stringResource(R.string.case_no_acuerdos) }
    items(acuerdos, key = { it.orden }) { acuerdo ->
        AcuerdoCard(acuerdo, isNew = acuerdo.orden in newOrdenes, onClick = { onOpen(acuerdo.orden) })
    }
}

@Composable
private fun AcuerdoCard(acuerdo: AcuerdoEntity, isNew: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.acuerdo_numero, acuerdo.numero),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (isNew) NewBadge(stringResource(R.string.new_badge))
            }
            Text(
                stringResource(
                    R.string.acuerdo_dates,
                    SiseDates.formatSpan(acuerdo.fechaAuto),
                    acuerdo.fechaPublicacion?.let(SiseDates::formatSpan) ?: stringResource(R.string.acuerdo_sin_publicar),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                acuerdo.tipoCuaderno,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                acuerdo.resumen,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LazyListScope.resoluciones(resoluciones: List<ResolucionEntity>, onOpenFile: (String) -> Unit) {
    if (resoluciones.isEmpty()) emptyMessage { stringResource(R.string.case_no_resoluciones) }
    items(resoluciones, key = { it.position }) { resolucion ->
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.resolucion_fecha, SiseDates.formatSpan(resolucion.fechaIngreso)),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(resolucion.tema, style = MaterialTheme.typography.bodyMedium)
                resolucion.archivoUrl?.let { url ->
                    TextButton(onClick = { onOpenFile(url) }) { Text(stringResource(R.string.resolucion_open)) }
                }
            }
        }
    }
}

private fun LazyListScope.relacionados(
    relacionados: List<AsuntoRelacionadoEntity>,
    savedNeuns: Set<String>,
    onSearch: (AsuntoRelacionadoEntity) -> Unit,
    onOpenCase: (String) -> Unit,
) {
    if (relacionados.isEmpty()) emptyMessage { stringResource(R.string.case_no_relacionados) }
    items(relacionados, key = { it.position }) { relacionado ->
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(relacionado.expediente, style = MaterialTheme.typography.titleSmall)
                Text(OrganoTitle.organo(relacionado.organo), style = MaterialTheme.typography.bodyMedium)
                Text(
                    OrganoTitle.tipoAsunto(relacionado.organo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.relacionado_fecha, SiseDates.formatSpan(relacionado.fechaRelacion)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (relacionado.relatedNeun in savedNeuns) {
                    TextButton(onClick = { onOpenCase(relacionado.relatedNeun) }) { Text(stringResource(R.string.action_open)) }
                } else {
                    TextButton(onClick = { onSearch(relacionado) }) { Text(stringResource(R.string.relacionado_search)) }
                }
            }
        }
    }
}

private fun LazyListScope.captura(entries: List<CapturaEntryEntity>, partyCount: Int) {
    if (entries.isEmpty()) {
        emptyMessage { stringResource(R.string.case_no_captura) }
        return
    }
    if (partyCount > 1) {
        item {
            Text(
                stringResource(R.string.captura_parties, partyCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    entries.groupBy { it.section }.forEach { (section, sectionEntries) ->
        item(key = "section:$section") { CapturaSection(section, sectionEntries) }
    }
}

/** A collapsible "Captura de Información" section; each record is separated by a divider. */
@Composable
private fun CapturaSection(title: String, entries: List<CapturaEntryEntity>) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entries.groupBy { it.group }.values.forEachIndexed { index, record ->
                    if (index > 0) HorizontalDivider()
                    record.forEach { entry ->
                        Column {
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(entry.value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier)
            }
        }
    }
}
