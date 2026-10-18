package mx.sisetracker.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.core.Materia
import mx.sisetracker.core.OrganoClase
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.ui.components.IconBadge
import mx.sisetracker.ui.components.icon

/**
 * The órgano picker: a full-height sheet with a search field, the filters
 * (tipo de órgano, class, materia, closed ones) and the matching órganos in a
 * lazy list, so even every circuit's órganos at once scroll smoothly.
 * Everything in it is already loaded (see [BundledOrganos]): opening it never
 * waits on the portal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganoPickerSheet(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onKind: (OrganoKind?) -> Unit,
    onClase: (OrganoClase?) -> Unit,
    onMateria: (Materia?) -> Unit,
    onShowClosed: (Boolean) -> Unit,
    onSelect: (KnownOrgano) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        OrganoPicker(
            state = state,
            onQueryChange = onQueryChange,
            onKind = onKind,
            onClase = onClase,
            onMateria = onMateria,
            onShowClosed = onShowClosed,
            onSelect = onSelect,
            modifier = Modifier.fillMaxHeight(),
        )
    }
}

@Composable
internal fun OrganoPicker(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onKind: (OrganoKind?) -> Unit,
    onClase: (OrganoClase?) -> Unit,
    onMateria: (Materia?) -> Unit,
    onShowClosed: (Boolean) -> Unit,
    onSelect: (KnownOrgano) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggestions = state.organoSuggestions
    val numberOption = state.organoNumberOption
    Column(modifier) {
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.organo_picker_title), style = MaterialTheme.typography.titleLarge)
            Text(
                if (state.listsAllCircuits) {
                    stringResource(R.string.search_all_circuitos)
                } else {
                    circuitoShortLabel(state.circuito).orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = state.organoQuery,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.organo_picker_query)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.organoQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        PickerFilters(state, onKind, onClase, onMateria, onShowClosed)
        Text(
            pluralStringResource(R.plurals.search_organos_count, suggestions.size, suggestions.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (numberOption != null) {
                item(key = "number") {
                    NumberRow(numberOption.id, onClick = { onSelect(numberOption) }, modifier = Modifier.animateItem())
                }
            }
            if (suggestions.isEmpty() && numberOption == null) {
                item(key = "none") {
                    Text(
                        stringResource(R.string.organo_picker_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
            if (state.listsAllCircuits) {
                // Every circuit's órganos, under a header per circuit that stays while its órganos scroll.
                suggestions.groupBy { it.circuito }.forEach { (circuito, organos) ->
                    stickyHeader(key = "circuito:$circuito") { CircuitoHeader(circuito) }
                    items(organos, key = { it.id }) { organo ->
                        OrganoRow(organo, selected = organo.id == state.organo?.id, onClick = { onSelect(organo) }, modifier = Modifier.animateItem())
                    }
                }
            } else {
                items(suggestions, key = { it.id }) { organo ->
                    OrganoRow(organo, selected = organo.id == state.organo?.id, onClick = { onSelect(organo) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

/** Tipo de órgano and class on one scrolling row, materia and "Incluir cerrados" on another. */
@Composable
private fun PickerFilters(
    state: SearchUiState,
    onKind: (OrganoKind?) -> Unit,
    onClase: (OrganoClase?) -> Unit,
    onMateria: (Materia?) -> Unit,
    onShowClosed: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChipScrollRow {
            state.kindOptions.forEach { kind ->
                val selected = kind == state.kindFilter
                FilterChip(
                    selected = selected,
                    onClick = { onKind(if (selected) null else kind) },
                    label = { Text(stringResource(kind.labelRes())) },
                    leadingIcon = { Icon(kind.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            state.claseOptions.forEach { clase ->
                val selected = clase == state.claseFilter
                FilterChip(
                    selected = selected,
                    onClick = { onClase(if (selected) null else clase) },
                    label = { Text(stringResource(clase.pluralRes())) },
                )
            }
        }
        if (state.materiaOptions.isNotEmpty() || state.hasClosed) {
            ChipScrollRow {
                state.materiaOptions.forEach { materia ->
                    val selected = materia == state.materiaFilter
                    FilterChip(
                        selected = selected,
                        onClick = { onMateria(if (selected) null else materia) },
                        label = { Text(stringResource(materia.labelRes())) },
                    )
                }
                if (state.hasClosed) {
                    FilterChip(
                        selected = state.showClosed,
                        onClick = { onShowClosed(!state.showClosed) },
                        label = { Text(stringResource(R.string.search_show_closed)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipScrollRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun CircuitoHeader(circuito: String?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            circuitoShortLabel(circuito).orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/** Kind-coded badges: guinda for juzgados, gold for tribunales, neutral for the rest. */
@Composable
private fun kindColors(kind: OrganoKind): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        OrganoKind.JUZGADOS -> colors.primaryContainer to colors.onPrimaryContainer
        OrganoKind.TRIBUNALES -> colors.secondaryContainer to colors.onSecondaryContainer
        OrganoKind.OTROS -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
}

@Composable
private fun OrganoRow(organo: KnownOrgano, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (container, content) = kindColors(organo.kind)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(organo.kind.icon(), size = 36.dp, container = container, content = content)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(organo.displayName(), style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    organo.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (organo.isClosed()) ClosedChip()
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** A typed organismo number no list has (an órgano newer than the catalog): used as is. */
@Composable
private fun NumberRow(number: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(painterResource(R.drawable.ic_tag), size = 36.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(stringResource(R.string.organo_picker_number, number), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.organo_picker_number_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
