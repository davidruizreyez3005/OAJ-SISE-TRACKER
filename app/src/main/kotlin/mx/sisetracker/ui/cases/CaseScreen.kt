package mx.sisetracker.ui.cases

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import mx.sisetracker.R
import mx.sisetracker.core.Audiencia
import mx.sisetracker.core.OrganoTitle
import mx.sisetracker.core.SiseDates
import mx.sisetracker.data.db.AcuerdoEntity
import mx.sisetracker.data.db.AsuntoRelacionadoEntity
import mx.sisetracker.data.db.CapturaEntryEntity
import mx.sisetracker.data.db.CaseEntity
import mx.sisetracker.data.db.CaseNoteEntity
import mx.sisetracker.data.db.ResolucionEntity
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.BrandHeader
import mx.sisetracker.ui.components.HeaderChip
import mx.sisetracker.ui.components.IconBadge
import mx.sisetracker.ui.components.InfoChip
import mx.sisetracker.ui.components.NewBadge
import mx.sisetracker.ui.components.SiseCard
import mx.sisetracker.ui.components.SiseMotion
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.hearingLine
import mx.sisetracker.ui.components.openInBrowser
import mx.sisetracker.ui.components.portalErrorText
import mx.sisetracker.ui.components.rememberCopyPortalLink
import mx.sisetracker.ui.theme.LocalBrandColors

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
    val copyPortalLink = rememberCopyPortalLink(snackbarHostState)
    val context = LocalContext.current
    val toolbarColor = LocalBrandColors.current.topBar.toArgb()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingNote by rememberSaveable { mutableStateOf(false) }

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
                showRule = state.case == null,
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
                                leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    state.case?.let { context.openInBrowser(it.caseUrl, toolbarColor) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.case_copy_portal_link)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    state.case?.let { copyPortalLink(it.caseUrl) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.case_note_menu)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    editingNote = true
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
                CaseHeader(case, state.note, state.nextHearing, state.today, onEditNote = { editingNote = true })
                val tabs = listOf(
                    stringResource(R.string.case_tab_acuerdos, state.acuerdos.size),
                    stringResource(R.string.case_tab_resoluciones, state.resoluciones.size),
                    stringResource(R.string.case_tab_relacionados, state.relacionados.size),
                    stringResource(R.string.case_tab_datos),
                )
                val pager = rememberPagerState(initialPage = tab) { tabs.size }
                val scope = rememberCoroutineScope()
                LaunchedEffect(pager.currentPage) { tab = pager.currentPage }
                PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            text = { Text(title) },
                        )
                    }
                }
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // Swipe between the tabs.
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(if (page == 0) 0.dp else 10.dp),
                        ) {
                            when (page) {
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
    }

    if (editingNote) {
        NoteSheet(
            note = state.note,
            onSave = { label, note ->
                editingNote = false
                viewModel.saveNote(label, note)
            },
            onDismiss = { editingNote = false },
        )
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

/**
 * The guinda band under the bar: tipo de asunto, órgano, the next hearing,
 * the user's label, the case's numbers and when it was last checked, then
 * the user's note (tap to edit).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaseHeader(
    case: CaseEntity,
    note: CaseNoteEntity?,
    nextHearing: Audiencia?,
    today: LocalDate,
    onEditNote: () -> Unit,
) {
    val brand = LocalBrandColors.current
    BrandHeader {
        Text(case.tipoAsuntoName, style = MaterialTheme.typography.labelLarge, color = brand.goldOnTopBar)
        Text(case.organoName, style = MaterialTheme.typography.titleMedium, color = brand.onTopBar)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            nextHearing?.let { HeaderChip(hearingLine(it, today), icon = painterResource(R.drawable.ic_event)) }
            note?.label?.takeIf { it.isNotEmpty() }?.let { HeaderChip(it, icon = painterResource(R.drawable.ic_label)) }
            HeaderChip(stringResource(R.string.case_neun, case.neun), icon = painterResource(R.drawable.ic_tag))
            if (case.noControlOcc.isNotEmpty()) {
                HeaderChip(stringResource(R.string.case_occ, case.noControlOcc))
            }
            val checked = Instant.ofEpochMilli(case.lastCheckedAt).atZone(ZoneId.systemDefault()).format(checkedFormat)
            HeaderChip(stringResource(R.string.case_checked, checked), icon = painterResource(R.drawable.ic_schedule))
        }
        note?.note?.takeIf { it.isNotEmpty() }?.let { text ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClickLabel = stringResource(R.string.case_note_edit), onClick = onEditNote),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = brand.goldOnTopBar,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp),
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = brand.onTopBarVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * "Etiqueta y nota": the user's own words for the case, kept on the phone
 * only. A sheet, like the órgano picker, so a long note has room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(note: CaseNoteEntity?, onSave: (label: String, note: String) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf(note?.label.orEmpty()) }
    var text by rememberSaveable { mutableStateOf(note?.note.orEmpty()) }
    val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.case_note_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(MAX_LABEL_LENGTH) },
                label = { Text(stringResource(R.string.case_note_label)) },
                supportingText = { Text(stringResource(R.string.case_note_label_help)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_label), contentDescription = null) },
                singleLine = true,
                keyboardOptions = sentences,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NOTE_LENGTH) },
                label = { Text(stringResource(R.string.case_note_text)) },
                minLines = 4,
                maxLines = 10,
                keyboardOptions = sentences,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.case_note_private),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onSave(label, text) }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

private const val MAX_LABEL_LENGTH = 40
private const val MAX_NOTE_LENGTH = 2000

private fun LazyListScope.emptyMessage(text: @Composable () -> String) {
    item {
        Text(
            text(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}

private fun LazyListScope.acuerdos(
    acuerdos: List<AcuerdoEntity>,
    newOrdenes: Set<Int>,
    onOpen: (Int) -> Unit,
) {
    if (acuerdos.isEmpty()) emptyMessage { stringResource(R.string.case_no_acuerdos) }
    itemsIndexed(acuerdos, key = { _, acuerdo -> acuerdo.orden }) { index, acuerdo ->
        TimelineRow(
            isNew = acuerdo.orden in newOrdenes,
            isFirst = index == 0,
            isLast = index == acuerdos.lastIndex,
            modifier = Modifier.animateItem(),
        ) {
            AcuerdoCard(acuerdo, isNew = acuerdo.orden in newOrdenes, onClick = { onOpen(acuerdo.orden) })
        }
    }
}

/**
 * The acuerdos as a timeline, newest first: a rail with a dot per acuerdo,
 * gold for the new ones. The rail is drawn behind the row once it's laid
 * out, so the card keeps its natural height.
 */
@Composable
private fun TimelineRow(
    isNew: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val line = colors.outlineVariant
    val ring = if (isNew) colors.tertiaryContainer else colors.primaryContainer
    val dot = if (isNew) colors.tertiary else colors.primary
    Row(
        modifier = modifier.drawBehind {
            val x = 14.dp.toPx()
            val dotY = 26.dp.toPx()
            val stroke = 2.dp.toPx()
            if (!isFirst) drawLine(line, Offset(x, 0f), Offset(x, dotY), stroke)
            if (!isLast) drawLine(line, Offset(x, dotY), Offset(x, size.height), stroke)
            drawCircle(ring, radius = 7.dp.toPx(), center = Offset(x, dotY))
            drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, dotY))
        },
    ) {
        Spacer(Modifier.width(28.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, bottom = 10.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcuerdoCard(acuerdo: AcuerdoEntity, isNew: Boolean, onClick: () -> Unit) {
    SiseCard(onClick = onClick, accent = if (isNew) MaterialTheme.colorScheme.tertiary else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.acuerdo_numero, acuerdo.numero),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (isNew) NewBadge(stringResource(R.string.new_badge))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoChip(
                stringResource(R.string.acuerdo_chip_auto, SiseDates.formatSpan(acuerdo.fechaAuto)),
                icon = painterResource(R.drawable.ic_event),
            )
            val publicacion = acuerdo.fechaPublicacion
            if (publicacion != null) {
                InfoChip(
                    stringResource(R.string.acuerdo_chip_publicado, SiseDates.formatSpan(publicacion)),
                    icon = painterResource(R.drawable.ic_event),
                )
            } else {
                InfoChip(
                    stringResource(R.string.acuerdo_chip_sin_publicar),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            InfoChip(acuerdo.tipoCuaderno, icon = painterResource(R.drawable.ic_folder))
        }
        Text(
            acuerdo.resumen,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun LazyListScope.resoluciones(resoluciones: List<ResolucionEntity>, onOpenFile: (String) -> Unit) {
    if (resoluciones.isEmpty()) emptyMessage { stringResource(R.string.case_no_resoluciones) }
    items(resoluciones, key = { it.position }) { resolucion ->
        SiseCard(modifier = Modifier.animateItem()) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(painterResource(R.drawable.ic_description), size = 36.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.resolucion_fecha, SiseDates.formatSpan(resolucion.fechaIngreso)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(resolucion.tema, style = MaterialTheme.typography.bodyMedium)
                    resolucion.archivoUrl?.let { url ->
                        TextButton(onClick = { onOpenFile(url) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.resolucion_open), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
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
        SiseCard(modifier = Modifier.animateItem()) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(
                    painterResource(R.drawable.ic_link),
                    size = 36.dp,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(relacionado.expediente, style = MaterialTheme.typography.titleMedium)
                    Text(
                        OrganoTitle.tipoAsunto(relacionado.organo),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(OrganoTitle.organo(relacionado.organo), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.relacionado_fecha, SiseDates.formatSpan(relacionado.fechaRelacion)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (relacionado.relatedNeun in savedNeuns) {
                        TextButton(onClick = { onOpenCase(relacionado.relatedNeun) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(stringResource(R.string.action_open))
                        }
                    } else {
                        TextButton(onClick = { onSearch(relacionado) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text(stringResource(R.string.relacionado_search))
                        }
                    }
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
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, SiseMotion.spec(), label = "chevron")
    SiseCard(contentPadding = 0.dp, modifier = Modifier.animateContentSize(SiseMotion.spec())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(SiseMotion.spec()) + expandVertically(SiseMotion.spec()),
            exit = fadeOut(SiseMotion.spec()) + shrinkVertically(SiseMotion.spec()),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entries.groupBy { it.group }.values.forEachIndexed { index, record ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            }
        }
    }
}
