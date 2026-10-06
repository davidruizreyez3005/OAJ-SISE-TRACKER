package mx.sisetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.sisetracker.R
import mx.sisetracker.core.SiseDates
import mx.sisetracker.data.db.CaseSummary
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.NewBadge
import mx.sisetracker.ui.components.rememberNotificationPermission
import mx.sisetracker.ui.components.SiseTopAppBar

/** "Mis expedientes": the saved cases, with a filter by expediente or órgano. */
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onOpenCase: (neun: String) -> Unit,
    onSearchAcuerdos: () -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notifications = rememberNotificationPermission()

    Scaffold(
        topBar = {
            SiseTopAppBar(
                title = stringResource(R.string.home_title),
                actions = {
                    if (state.savedCount > 0) {
                        IconButton(onClick = onSearchAcuerdos) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.acuerdo_search_title))
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.savedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onSearchClick,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_search_button)) },
                )
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Unit
            state.savedCount == 0 -> EmptyHome(onSearchClick, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.suggestNotifications && !notifications.granted) {
                    item {
                        MessageCard(text = stringResource(R.string.notifications_prompt)) {
                            Row {
                                TextButton(onClick = notifications.request) {
                                    Text(stringResource(R.string.notifications_allow))
                                }
                                TextButton(onClick = viewModel::onDismissNotificationPrompt) {
                                    Text(stringResource(R.string.action_not_now))
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.filter,
                        onValueChange = viewModel::onFilterChange,
                        label = { Text(stringResource(R.string.home_filter)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.filter.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onFilterChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.cases.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.home_filter_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(state.cases, key = { it.case.neun }) { summary ->
                    CaseCard(summary = summary, onClick = { onOpenCase(summary.case.neun) })
                }
            }
        }
    }
}

@Composable
private fun CaseCard(summary: CaseSummary, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary.case.expediente,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (summary.unseenCount > 0) {
                    NewBadge(pluralStringResource(R.plurals.home_unseen, summary.unseenCount, summary.unseenCount))
                }
            }
            Text(
                summary.case.organoName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary.case.tipoAsuntoName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val latest = summary.latestPublicacion
            Text(
                if (latest != null) {
                    stringResource(R.string.home_latest_acuerdo, SiseDates.formatSpan(latest))
                } else {
                    stringResource(R.string.home_no_acuerdos)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHome(onSearchClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSearchClick) { Text(stringResource(R.string.home_search_button)) }
    }
}
