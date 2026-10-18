package mx.sisetracker.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.sisetracker.R
import mx.sisetracker.core.CasePage
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.Circuito
import mx.sisetracker.core.FormOption
import mx.sisetracker.core.SiseDates
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.InfoChip
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.MessageTone
import mx.sisetracker.ui.components.SectionCard
import mx.sisetracker.ui.components.SiseMotion
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.icon
import mx.sisetracker.ui.components.messageRes
import mx.sisetracker.ui.components.portalErrorText
import mx.sisetracker.ui.components.rememberCopyPortalLink
import mx.sisetracker.ui.components.rememberPasteCaseLink
import mx.sisetracker.ui.components.siseCardBorder
import mx.sisetracker.ui.components.siseCardColors
import mx.sisetracker.ui.theme.LocalBrandColors

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenPortal: (circuito: String) -> Unit,
    onCaseLinkFound: (CaseUrl) -> Unit,
    onOpenCase: (neun: String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openCase) {
        state.openCase?.let { neun ->
            onOpenCase(neun)
            viewModel.onCaseOpened()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val copyPortalLink = rememberCopyPortalLink(snackbarHostState)
    val pasteLink = rememberPasteCaseLink(snackbarHostState, onCaseLinkFound)
    val openPortal: () -> Unit = { viewModel.onOpenPortal()?.let(onOpenPortal) }
    var pickingOrgano by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SiseTopAppBar(
                title = stringResource(R.string.search_title),
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = pasteLink) {
                        Icon(
                            painterResource(R.drawable.ic_content_paste),
                            contentDescription = stringResource(R.string.search_paste_link),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(
                title = stringResource(R.string.search_section_organo),
                icon = painterResource(R.drawable.ic_account_balance),
                step = stringResource(R.string.search_step, 1),
            ) {
                CircuitoField(
                    circuitos = state.circuitos,
                    selected = state.circuito,
                    selectedLabel = state.circuitoLabel,
                    onSelect = viewModel::onCircuitoSelected,
                    onSelectAll = viewModel::onCircuitoCleared,
                )
                OrganoField(
                    state = state,
                    onClick = {
                        viewModel.onOrganosRequested()
                        pickingOrgano = true
                    },
                )
                if (state.knownOrganos.isNotEmpty()) {
                    RecentOrganos(organos = state.knownOrganos, onSelect = viewModel::onOrganoSelected)
                }
            }

            SectionCard(
                title = stringResource(R.string.search_section_asunto),
                icon = painterResource(R.drawable.ic_folder),
                step = stringResource(R.string.search_step, 2),
            ) {
                OptionField(
                    label = stringResource(R.string.search_tipo_asunto),
                    icon = painterResource(R.drawable.ic_folder),
                    options = state.tiposAsunto,
                    selected = state.tipoAsunto,
                    enabled = state.canLoadTipos,
                    disabledHelp = stringResource(R.string.search_tipo_asunto_disabled),
                    emptyHelp = stringResource(R.string.search_tipo_asunto_empty),
                    busyHelp = if (state.checkingOrganoTipos) stringResource(R.string.search_tipos_checking) else null,
                    onOpen = viewModel::onTiposAsuntoRequested,
                    onSelect = viewModel::onTipoAsuntoSelected,
                )
                if (state.canLoadOrganoTipos) {
                    TextButton(onClick = viewModel::onLoadOrganoTipos) {
                        Text(stringResource(R.string.search_tipos_del_organo))
                    }
                }
                AnimatedVisibility(
                    visible = state.showsProcedimiento,
                    enter = fadeIn(SiseMotion.spec()) + expandVertically(SiseMotion.spec()),
                    exit = fadeOut(SiseMotion.spec()) + shrinkVertically(SiseMotion.spec()),
                ) {
                    OptionField(
                        label = stringResource(R.string.search_tipo_procedimiento),
                        icon = painterResource(R.drawable.ic_description),
                        options = state.tiposProcedimiento,
                        selected = state.tipoProcedimiento,
                        enabled = true,
                        disabledHelp = "",
                        emptyHelp = stringResource(R.string.search_tipo_procedimiento_empty),
                        onOpen = viewModel::onTiposProcedimientoRequested,
                        onSelect = viewModel::onTipoProcedimientoSelected,
                    )
                }
            }

            SectionCard(
                title = stringResource(R.string.search_section_expediente),
                icon = painterResource(R.drawable.ic_tag),
                step = stringResource(R.string.search_step, 3),
            ) {
                ExpedienteField(
                    value = state.expediente,
                    warning = state.expedienteWarning,
                    onValueChange = viewModel::onExpedienteChange,
                    onSearch = viewModel::onSearch,
                )
                Button(
                    onClick = viewModel::onSearch,
                    enabled = state.canSearch,
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.lookup == LookupState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.search_button), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                TextButton(
                    onClick = openPortal,
                    enabled = state.canOpenPortal,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.search_open_portal), modifier = Modifier.padding(start = 8.dp))
                }
            }

            AnimatedContent(
                targetState = state.lookup,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(SiseMotion.spec()) + slideInVertically(SiseMotion.spec()) { it / 6 }) togetherWith
                        fadeOut(SiseMotion.spec()) using SizeTransform(clip = false)
                },
                label = "lookup",
            ) { lookup ->
                LookupResult(
                    lookup = lookup,
                    saving = state.saving,
                    onSave = viewModel::onSave,
                    onOpenSaved = viewModel::onOpenSaved,
                    onOpenPortal = openPortal,
                    onCopyLink = copyPortalLink,
                    offerPortalWhenNotFound = state.showsProcedimiento,
                )
            }
        }
    }

    if (pickingOrgano) {
        OrganoPickerSheet(
            state = state,
            onQueryChange = viewModel::onOrganoQueryChange,
            onKind = viewModel::onKindFilterChange,
            onClase = viewModel::onClaseFilterChange,
            onMateria = viewModel::onMateriaFilterChange,
            onShowClosed = viewModel::onShowClosedChange,
            onSelect = { organo ->
                viewModel.onOrganoSelected(organo)
                pickingOrgano = false
            },
            onDismiss = { pickingOrgano = false },
        )
    }
}

/**
 * The OAJ's circuit list, bundled with the app, after "Todos los circuitos"
 * (no circuit: the órgano picker lists every circuit's órganos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircuitoField(
    circuitos: List<Circuito>,
    selected: String,
    selectedLabel: String,
    onSelect: (Circuito) -> Unit,
    onSelectAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel.ifEmpty { stringResource(R.string.search_all_circuitos) },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.search_circuito)) },
            leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_all_circuitos), style = MaterialTheme.typography.bodyLarge) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_public), contentDescription = null) },
                trailingIcon = if (selected.isEmpty()) {
                    { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                onClick = {
                    onSelectAll()
                    expanded = false
                },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
            )
            circuitos.forEach { circuito ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(circuito.ordinal, style = MaterialTheme.typography.bodyLarge)
                            if (circuito.region.isNotEmpty()) {
                                Text(
                                    circuito.region,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailingIcon = if (circuito.num == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(circuito)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * The chosen órgano, or "Órgano" with how many there are to choose from.
 * Tapping it opens the picker. Styled like the outlined fields around it, but
 * the name wraps instead of being cut.
 */
@Composable
private fun OrganoField(state: SearchUiState, onClick: () -> Unit) {
    val organo = state.organo
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                organo?.kind?.icon() ?: painterResource(R.drawable.ic_gavel),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (organo == null) {
                    Text(stringResource(R.string.search_organo), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.search_organo), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    Text(organo.displayName(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
    Row(
        modifier = Modifier.padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when {
                organo != null -> organo.description()
                state.organos == Loadable.Loading -> stringResource(R.string.search_organos_loading)
                state.organos is Loadable.Failed -> stringResource(state.organos.error.messageRes()) + " " +
                    stringResource(R.string.search_organos_failed)
                state.listsAllCircuits -> pluralStringResource(R.plurals.search_organos_all, state.organoCount, state.organoCount)
                else -> pluralStringResource(R.plurals.search_organos_circuit, state.organoCount, state.organoCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.organos is Loadable.Failed && organo == null) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (organo?.isClosed() == true) ClosedChip()
    }
}

@Composable
private fun RecentOrganos(organos: List<KnownOrgano>, onSelect: (KnownOrgano) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.search_recent_organos),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(organos, key = { it.id }) { organo ->
                AssistChip(
                    onClick = { onSelect(organo) },
                    label = {
                        Text(organo.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_history),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        }
    }
}

/** A read-only dropdown, ready before it's opened (bundled or cached options). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionField(
    label: String,
    icon: Painter,
    options: Loadable<List<FormOption>>,
    selected: FormOption?,
    enabled: Boolean,
    disabledHelp: String,
    emptyHelp: String,
    onOpen: () -> Unit,
    onSelect: (FormOption) -> Unit,
    /** Shown, with a spinner, while a fresher list loads behind the current one. */
    busyHelp: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val loaded = (options as? Loadable.Loaded)?.value.orEmpty()
    val showMenu = expanded && loaded.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = showMenu,
        onExpandedChange = { open ->
            if (open && enabled) onOpen()
            expanded = open && enabled
        },
    ) {
        OutlinedTextField(
            value = selected?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = {
                if (options == Loadable.Loading || busyHelp != null) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu)
                }
            },
            supportingText = {
                val help = when {
                    !enabled -> disabledHelp
                    options == Loadable.Loading -> stringResource(R.string.search_loading_options)
                    busyHelp != null -> busyHelp
                    options is Loadable.Failed -> stringResource(options.error.messageRes()) + " " +
                        stringResource(R.string.search_tap_to_retry)
                    options is Loadable.Loaded && options.value.isEmpty() -> emptyHelp
                    else -> null
                }
                if (help != null) Text(help)
            },
            isError = options is Loadable.Failed,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            loaded.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = if (option.value == selected?.value) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * Numeric keyboard plus a "/" key: the expediente is usually `n/yyyy`, and
 * number keyboards rarely have a slash. Other shapes are allowed, with a hint.
 */
@Composable
private fun ExpedienteField(
    value: String,
    warning: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    }
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        label = { Text(stringResource(R.string.search_expediente)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_tag), contentDescription = null) },
        supportingText = {
            Text(
                stringResource(if (warning) R.string.search_expediente_warning else R.string.search_expediente_help),
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            TextButton(
                onClick = {
                    val selection = field.selection
                    val text = field.text.replaceRange(selection.min, selection.max, "/")
                    field = TextFieldValue(text, TextRange(selection.min + 1))
                    onValueChange(text)
                },
            ) {
                Text("/", style = MaterialTheme.typography.titleLarge)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LookupResult(
    lookup: LookupState,
    saving: Boolean,
    onSave: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenPortal: () -> Unit,
    onCopyLink: (url: String) -> Unit,
    offerPortalWhenNotFound: Boolean,
) {
    when (lookup) {
        LookupState.None, LookupState.Loading -> Unit
        is LookupState.Found -> CasePreview(
            page = lookup.page,
            alreadySaved = lookup.alreadySaved,
            saving = saving,
            onSave = onSave,
            onOpenSaved = onOpenSaved,
            onCopyLink = { onCopyLink(lookup.url.toUrl()) },
        )
        // For procedimiento tipos the case URL's tipoprocedimiento is a guess: offer the portal too.
        LookupState.NotFound -> if (offerPortalWhenNotFound) {
            MessageCard(text = stringResource(R.string.search_not_found)) {
                TextButton(onClick = onOpenPortal) { Text(stringResource(R.string.search_open_portal)) }
            }
        } else {
            MessageCard(text = stringResource(R.string.search_not_found))
        }
        is LookupState.Failed -> MessageCard(text = portalErrorText(lookup.error, lookup.location), tone = MessageTone.ERROR) {
            TextButton(onClick = onOpenPortal) { Text(stringResource(R.string.search_open_portal)) }
        }
    }
}

/** The case found: a guinda header like the case screen's, the counts, and what to do with it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CasePreview(
    page: CasePage,
    alreadySaved: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onOpenSaved: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val brand = LocalBrandColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = siseCardColors(),
        border = siseCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(brand.topBar, brand.topBarDeep)))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                page.tipoAsuntoName,
                style = MaterialTheme.typography.labelLarge,
                color = brand.goldOnTopBar,
            )
            Text(
                stringResource(R.string.preview_expediente, page.expediente),
                style = MaterialTheme.typography.titleLarge,
                color = brand.onTopBar,
            )
            Text(page.organoName, style = MaterialTheme.typography.bodyMedium, color = brand.onTopBarVariant)
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(
                    pluralStringResource(R.plurals.preview_acuerdos, page.acuerdos.size, page.acuerdos.size),
                    icon = painterResource(R.drawable.ic_description),
                )
                val latest = page.acuerdos.mapNotNull { it.fechaPublicacion }.maxOrNull()
                if (latest != null) {
                    InfoChip(
                        stringResource(R.string.preview_latest_acuerdo, SiseDates.formatSpan(latest)),
                        icon = painterResource(R.drawable.ic_event),
                    )
                }
            }
            if (alreadySaved) {
                Text(
                    stringResource(R.string.preview_already_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Wraps at large font sizes instead of squeezing the buttons.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (alreadySaved) {
                    Button(onClick = onOpenSaved) { Text(stringResource(R.string.action_open)) }
                } else {
                    Button(onClick = onSave, enabled = !saving) { Text(stringResource(R.string.action_save)) }
                }
                TextButton(onClick = onCopyLink) {
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.preview_copy_link), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
