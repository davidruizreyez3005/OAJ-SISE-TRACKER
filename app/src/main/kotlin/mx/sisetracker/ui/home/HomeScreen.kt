package mx.sisetracker.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch
import mx.sisetracker.R
import mx.sisetracker.core.Audiencia
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.core.OrganoKind
import mx.sisetracker.core.SiseDates
import mx.sisetracker.data.settings.CaseSort
import mx.sisetracker.ui.components.BrandHeader
import mx.sisetracker.ui.components.CasesIllustration
import mx.sisetracker.ui.components.HeaderChip
import mx.sisetracker.ui.components.IconBadge
import mx.sisetracker.ui.components.InfoChip
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.NewBadge
import mx.sisetracker.ui.components.SiseCard
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.StepRow
import mx.sisetracker.ui.components.cardNameLines
import mx.sisetracker.ui.components.hearingLine
import mx.sisetracker.ui.components.icon
import mx.sisetracker.ui.components.portalErrorText
import mx.sisetracker.ui.components.rememberNotificationPermission
import mx.sisetracker.ui.components.rememberPasteCaseLink
import mx.sisetracker.ui.theme.LocalBrandColors

/** "Mis expedientes": the saved cases, with a filter, sort and chips, and the imported links still to load. */
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onOpenCase: (neun: String) -> Unit,
    onSearchAcuerdos: () -> Unit = {},
    onSettings: () -> Unit = {},
    onCaseLinkFound: (CaseUrl) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notifications = rememberNotificationPermission()
    val snackbarHostState = remember { SnackbarHostState() }
    val pasteLink = rememberPasteCaseLink(snackbarHostState, onCaseLinkFound)
    val listState = rememberLazyListState()
    // The header has its own search: the button only comes in once it's scrolled away.
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    val message = state.message?.let { message ->
        when (message) {
            HomeMessage.PendingNotFound -> stringResource(R.string.home_pending_not_found_message)
            is HomeMessage.Failed -> portalErrorText(message.error, message.location)
        }
    }
    // Shown in the screen's scope: clearing the message changes this effect's
    // key, which would cancel a snackbar shown from the effect itself.
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        if (message != null) {
            viewModel.onMessageShown()
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }
    LaunchedEffect(state.openCase) {
        state.openCase?.let { neun ->
            viewModel.onCaseOpened()
            onOpenCase(neun)
        }
    }

    Scaffold(
        topBar = {
            SiseTopAppBar(
                title = stringResource(R.string.app_name),
                showRule = false,
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
            AnimatedVisibility(
                visible = !state.isEmpty && showFab,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    onClick = onSearchClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_search_button)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                HomeHeader(
                    state = state,
                    onSearchClick = onSearchClick,
                    onPasteLink = pasteLink,
                    onSearchAcuerdos = onSearchAcuerdos,
                )
            }
            if (state.isEmpty) {
                item(key = "empty") { EmptyHome(onSearchClick = onSearchClick, onPasteLink = pasteLink) }
                return@LazyColumn
            }
            if (state.suggestNotifications && !notifications.granted) {
                item(key = "notifications") {
                    MessageCard(
                        text = stringResource(R.string.notifications_prompt),
                        icon = rememberVectorPainter(Icons.Filled.Notifications),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    ) {
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
            item(key = "filter") {
                OutlinedTextField(
                    value = state.filter,
                    onValueChange = viewModel::onFilterChange,
                    label = { Text(stringResource(R.string.home_filter)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_filter_list), contentDescription = null) },
                    trailingIcon = {
                        if (state.filter.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onFilterChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            if (state.savedCount > 0) {
                item(key = "chips") {
                    ListChips(
                        state = state,
                        onSort = viewModel::onSortChange,
                        onNewOnly = viewModel::onNewOnlyChange,
                        onLabel = viewModel::onLabelFilter,
                    )
                }
            }
            if (state.pending.isNotEmpty()) {
                item(key = "pending-section") {
                    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)) {
                        Text(
                            pluralStringResource(R.plurals.home_pending_section, state.pending.size, state.pending.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            stringResource(R.string.home_pending_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.pending, key = { "pending:${it.caseUrl}" }) { item ->
                    PendingCard(
                        item = item,
                        loading = state.loadingPending == item.caseUrl,
                        onClick = { viewModel.onOpenPending(item) },
                        onRemove = { viewModel.onRemovePending(item) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                }
            }
            if (state.savedCount == 0) return@LazyColumn
            item(key = "section") {
                Text(
                    stringResource(R.string.home_saved_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
                )
            }
            if (state.cases.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        stringResource(R.string.home_filter_no_match),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
            items(state.cases, key = { it.summary.case.neun }) { case ->
                CaseCard(
                    case = case,
                    today = state.today,
                    onClick = { onOpenCase(case.summary.case.neun) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
        }
    }
}

/**
 * The guinda band under the bar: the title, a search shortcut, and (with
 * saved cases) how many there are, how many acuerdos are new and whether
 * the daily check is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeHeader(
    state: HomeUiState,
    onSearchClick: () -> Unit,
    onPasteLink: () -> Unit,
    onSearchAcuerdos: () -> Unit,
) {
    val brand = LocalBrandColors.current
    BrandHeader {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, color = brand.onTopBar)
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = brand.onTopBarVariant,
        )
        Surface(
            onClick = onSearchClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.home_search_pill),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (state.savedCount > 0) {
                HeaderChip(
                    pluralStringResource(R.plurals.home_stat_cases, state.savedCount, state.savedCount),
                    icon = painterResource(R.drawable.ic_folder),
                )
                if (state.unseenTotal > 0) {
                    HeaderChip(
                        pluralStringResource(R.plurals.home_stat_new, state.unseenTotal, state.unseenTotal),
                        icon = painterResource(R.drawable.ic_description),
                    )
                }
                if (state.dailyCheck) {
                    HeaderChip(stringResource(R.string.home_stat_daily), icon = painterResource(R.drawable.ic_schedule))
                }
            }
            Surface(onClick = onPasteLink, shape = CircleShape, color = brand.onTopBar.copy(alpha = 0.14f), contentColor = brand.onTopBar) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_content_paste), contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(stringResource(R.string.home_paste), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Sort order, "Con acuerdos nuevos" and the user's labels, in one scrolling row. */
@Composable
private fun ListChips(
    state: HomeUiState,
    onSort: (CaseSort) -> Unit,
    onNewOnly: (Boolean) -> Unit,
    onLabel: (String?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item(key = "sort") {
            var open by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { open = true },
                    label = { Text(stringResource(R.string.home_sort, stringResource(state.sort.labelRes()))) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    CaseSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(sort.labelRes())) },
                            leadingIcon = {
                                if (sort == state.sort) Icon(Icons.Filled.Check, contentDescription = null)
                            },
                            onClick = {
                                open = false
                                onSort(sort)
                            },
                        )
                    }
                }
            }
        }
        if (state.unseenTotal > 0 || state.newOnly) {
            item(key = "new") {
                FilterChip(
                    selected = state.newOnly,
                    onClick = { onNewOnly(!state.newOnly) },
                    label = { Text(stringResource(R.string.home_filter_new)) },
                    leadingIcon = if (state.newOnly) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
        items(state.labels, key = { "label:$it" }) { label ->
            FilterChip(
                selected = state.labelFilter == label,
                onClick = { onLabel(label) },
                label = { Text(label) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_label), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

private fun CaseSort.labelRes(): Int = when (this) {
    CaseSort.ACTIVITY -> R.string.home_sort_activity
    CaseSort.HEARING -> R.string.home_sort_hearing
    CaseSort.EXPEDIENTE -> R.string.home_sort_expediente
    CaseSort.ORGANO -> R.string.home_sort_organo
    CaseSort.ADDED -> R.string.home_sort_added
}

/** An imported link: what the link itself says, until a tap loads the case. */
@Composable
private fun PendingCard(
    item: PendingItem,
    loading: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SiseCard(onClick = if (loading) null else onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(
                painterResource(R.drawable.ic_link),
                size = 44.dp,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(item.expediente, style = MaterialTheme.typography.titleLarge)
                item.tipoAsuntoName?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    item.organoName ?: stringResource(R.string.search_organo_number, item.organismoId),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = cardNameLines(),
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.home_pending_loading),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else if (item.notFound) {
                        InfoChip(
                            stringResource(R.string.home_pending_not_found),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        InfoChip(stringResource(R.string.home_pending_chip), icon = painterResource(R.drawable.ic_schedule))
                    }
                }
            }
            IconButton(onClick = onRemove, enabled = !loading) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_pending_remove))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaseCard(case: HomeCase, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val summary = case.summary
    val kind = remember(summary.case.organoName) { OrganoKind.fromName(summary.case.organoName) }
    SiseCard(
        onClick = onClick,
        accent = if (summary.unseenCount > 0) MaterialTheme.colorScheme.tertiary else null,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(kind.icon(), size = 44.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(summary.case.expediente, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (summary.unseenCount > 0) {
                        NewBadge(pluralStringResource(R.plurals.home_unseen, summary.unseenCount, summary.unseenCount))
                    }
                }
                Text(summary.case.tipoAsuntoName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(
                    summary.case.organoName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = cardNameLines(),
                    overflow = TextOverflow.Ellipsis,
                )
                summary.label?.takeIf { it.isNotEmpty() }?.let { label ->
                    InfoChip(
                        label,
                        icon = painterResource(R.drawable.ic_label),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                case.nextHearing?.let { HearingFact(it, today, modifier = Modifier.padding(top = 4.dp)) }
                val latest = summary.latestPublicacion
                CardFact(
                    painterResource(R.drawable.ic_event),
                    if (latest != null) {
                        stringResource(R.string.home_latest_acuerdo, SiseDates.formatSpan(latest))
                    } else {
                        stringResource(R.string.home_no_acuerdos)
                    },
                    modifier = Modifier.padding(top = if (case.nextHearing == null) 4.dp else 0.dp),
                )
                CardFact(
                    painterResource(R.drawable.ic_description),
                    pluralStringResource(R.plurals.preview_acuerdos, summary.acuerdoCount, summary.acuerdoCount),
                )
            }
        }
    }
}

/** The next hearing, in gold when it's within a week. */
@Composable
private fun HearingFact(audiencia: Audiencia, today: LocalDate, modifier: Modifier = Modifier) {
    val soon = ChronoUnit.DAYS.between(today, audiencia.fecha) < 7
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_event),
            contentDescription = null,
            tint = if (soon) colors.secondary else colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            hearingLine(audiencia, today),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (soon) FontWeight.SemiBold else null,
            color = if (soon) colors.secondary else colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** A small icon and a line of text: one fact on a case card. */
@Composable
private fun CardFact(icon: Painter, text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** No saved cases yet: a drawing, what to do, and how the app works. */
@Composable
private fun EmptyHome(onSearchClick: () -> Unit, onPasteLink: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CasesIllustration()
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSearchClick, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.home_search_button), modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPasteLink) {
            Icon(painterResource(R.drawable.ic_content_paste), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.search_paste_link), modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
        SiseCard {
            Text(stringResource(R.string.home_how_title), style = MaterialTheme.typography.titleMedium)
            StepRow(1, stringResource(R.string.home_how_1), modifier = Modifier.padding(top = 4.dp))
            StepRow(2, stringResource(R.string.home_how_2))
            StepRow(3, stringResource(R.string.home_how_3))
        }
    }
}
