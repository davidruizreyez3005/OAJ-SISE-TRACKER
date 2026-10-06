package mx.sisetracker.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import mx.sisetracker.R
import mx.sisetracker.core.CasePage
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.Circuito
import mx.sisetracker.core.FormOption
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.core.SiseDates
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.messageRes

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
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val noLinkMessage = stringResource(R.string.search_paste_no_link)

    val pasteLink: () -> Unit = {
        scope.launch {
            val clip = clipboard.getClipEntry()?.clipData
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            val url = text?.let(CaseUrl::findIn)
            if (url != null) onCaseLinkFound(url) else snackbarHostState.showSnackbar(noLinkMessage)
        }
    }
    val openPortal: () -> Unit = { viewModel.onOpenPortal()?.let(onOpenPortal) }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircuitoField(
                circuitos = state.circuitos,
                selectedLabel = state.circuitoLabel,
                onSelect = viewModel::onCircuitoSelected,
            )

            if (state.showsKindFilter) {
                KindChips(selected = state.kindFilter, onSelect = viewModel::onKindFilterChange)
            }

            OrganoField(
                text = state.organoText,
                organo = state.organo,
                organos = state.organos,
                suggestions = state.organoSuggestions,
                onOpen = viewModel::onOrganosRequested,
                onTextChange = viewModel::onOrganoTextChange,
                onSelect = viewModel::onOrganoSelected,
            )

            if (state.organo?.isClosed() == true) ClosedChip()

            if (state.knownOrganos.isNotEmpty()) {
                RecentOrganos(organos = state.knownOrganos, onSelect = viewModel::onOrganoSelected)
            }

            OptionField(
                label = stringResource(R.string.search_tipo_asunto),
                options = state.tiposAsunto,
                selected = state.tipoAsunto,
                enabled = state.canLoadTipos,
                disabledHelp = stringResource(R.string.search_tipo_asunto_disabled),
                emptyHelp = stringResource(R.string.search_tipo_asunto_empty),
                onOpen = viewModel::onTiposAsuntoRequested,
                onSelect = viewModel::onTipoAsuntoSelected,
            )
            if (state.canLoadOrganoTipos) {
                TextButton(onClick = viewModel::onLoadOrganoTipos) {
                    Text(stringResource(R.string.search_tipos_del_organo))
                }
            }

            if (state.showsProcedimiento) {
                OptionField(
                    label = stringResource(R.string.search_tipo_procedimiento),
                    options = state.tiposProcedimiento,
                    selected = state.tipoProcedimiento,
                    enabled = true,
                    disabledHelp = "",
                    emptyHelp = stringResource(R.string.search_tipo_procedimiento_empty),
                    onOpen = viewModel::onTiposProcedimientoRequested,
                    onSelect = viewModel::onTipoProcedimientoSelected,
                )
            }

            ExpedienteField(
                value = state.expediente,
                warning = state.expedienteWarning,
                onValueChange = viewModel::onExpedienteChange,
                onSearch = viewModel::onSearch,
            )

            Button(
                onClick = viewModel::onSearch,
                enabled = state.canSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.lookup == LookupState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.search_button))
                }
            }

            OutlinedButton(
                onClick = openPortal,
                enabled = state.canOpenPortal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.search_open_portal))
            }

            LookupResult(
                lookup = state.lookup,
                saving = state.saving,
                onSave = viewModel::onSave,
                onOpenSaved = viewModel::onOpenSaved,
                onOpenPortal = openPortal,
                offerPortalWhenNotFound = state.showsProcedimiento,
            )
        }
    }
}

/** The OAJ's circuit list, bundled with the app: choosing one makes no request until its órganos are needed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircuitoField(
    circuitos: List<Circuito>,
    selectedLabel: String,
    onSelect: (Circuito) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.search_circuito)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            circuitos.forEach { circuito ->
                DropdownMenuItem(
                    text = { Text(circuito.label) },
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

/** "Cerrado": the órgano's name carries an active period that has ended. */
@Composable
private fun ClosedChip() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            stringResource(R.string.organo_closed),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun KindChips(selected: OrganoKind?, onSelect: (OrganoKind?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.search_kind), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrganoKind.entries.forEach { kind ->
                val isSelected = kind == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(if (isSelected) null else kind) },
                    label = { Text(stringResource(kind.labelRes())) },
                )
            }
        }
    }
}

private fun OrganoKind.labelRes(): Int = when (this) {
    OrganoKind.JUZGADOS -> R.string.kind_juzgados
    OrganoKind.TRIBUNALES -> R.string.kind_tribunales
    OrganoKind.OTROS -> R.string.kind_otros
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganoField(
    text: String,
    organo: KnownOrgano?,
    organos: Loadable<List<KnownOrgano>>,
    suggestions: List<KnownOrgano>,
    onOpen: () -> Unit,
    onTextChange: (String) -> Unit,
    onSelect: (KnownOrgano) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val showMenu = expanded && suggestions.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = showMenu,
        onExpandedChange = { open ->
            if (open) onOpen()
            expanded = open
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                if (!expanded) onOpen()
                onTextChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.search_organo)) },
            supportingText = {
                Text(
                    when {
                        organo != null && organo.name.isEmpty() -> stringResource(R.string.search_organo_number, organo.id)
                        organos == Loadable.Loading -> stringResource(R.string.search_organos_loading)
                        organos is Loadable.Failed -> stringResource(organos.error.messageRes()) + " " +
                            stringResource(R.string.search_organos_failed)
                        else -> stringResource(R.string.search_organo_help)
                    },
                )
            },
            isError = organos is Loadable.Failed,
            singleLine = true,
            trailingIcon = {
                if (organos == Loadable.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu)
                }
            },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = showMenu, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion.name.ifEmpty { suggestion.id }) },
                    trailingIcon = {
                        Column(horizontalAlignment = Alignment.End) {
                            if (suggestion.isClosed()) ClosedChip()
                            Text(suggestion.id, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    onClick = {
                        onSelect(suggestion)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentOrganos(organos: List<KnownOrgano>, onSelect: (KnownOrgano) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.search_recent_organos), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            organos.forEach { organo ->
                SuggestionChip(
                    onClick = { onSelect(organo) },
                    label = {
                        Text(
                            organo.name.ifEmpty { organo.id },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
        }
    }
}

/** A read-only dropdown whose options load when it's first opened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionField(
    label: String,
    options: Loadable<List<FormOption>>,
    selected: FormOption?,
    enabled: Boolean,
    disabledHelp: String,
    emptyHelp: String,
    onOpen: () -> Unit,
    onSelect: (FormOption) -> Unit,
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
            trailingIcon = {
                if (options == Loadable.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu)
                }
            },
            supportingText = {
                val help = when {
                    !enabled -> disabledHelp
                    options == Loadable.Loading -> stringResource(R.string.search_loading_options)
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
        )
        // For procedimiento tipos the case URL's tipoprocedimiento is a guess: offer the portal too.
        LookupState.NotFound -> if (offerPortalWhenNotFound) {
            MessageCard(text = stringResource(R.string.search_not_found)) {
                TextButton(onClick = onOpenPortal) { Text(stringResource(R.string.search_open_portal)) }
            }
        } else {
            MessageCard(text = stringResource(R.string.search_not_found))
        }
        is LookupState.Failed -> MessageCard(text = stringResource(lookup.error.messageRes())) {
            TextButton(onClick = onOpenPortal) { Text(stringResource(R.string.search_open_portal)) }
        }
    }
}

@Composable
private fun CasePreview(
    page: CasePage,
    alreadySaved: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onOpenSaved: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(page.organoName, style = MaterialTheme.typography.titleMedium)
            Text(
                page.tipoAsuntoName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.preview_expediente, page.expediente),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                pluralStringResource(R.plurals.preview_acuerdos, page.acuerdos.size, page.acuerdos.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            val latest = page.acuerdos.maxOfOrNull { it.fechaPublicacion }
            if (latest != null) {
                Text(
                    stringResource(R.string.preview_latest_acuerdo, SiseDates.formatSpan(latest)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (alreadySaved) {
                Text(
                    stringResource(R.string.preview_already_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = onOpenSaved) { Text(stringResource(R.string.action_open)) }
            } else {
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
