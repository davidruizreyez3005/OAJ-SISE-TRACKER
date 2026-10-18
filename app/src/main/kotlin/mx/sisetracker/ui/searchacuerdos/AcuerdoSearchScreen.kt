package mx.sisetracker.ui.searchacuerdos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.sisetracker.R
import mx.sisetracker.core.SiseDates
import mx.sisetracker.data.db.AcuerdoSearchResult
import mx.sisetracker.data.db.SnippetMarkers
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.IconBadge
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.SiseCard
import mx.sisetracker.ui.components.SiseTopAppBar

/** Full-text search over every saved résumé and síntesis. */
@Composable
fun AcuerdoSearchScreen(
    onBack: () -> Unit,
    onOpenAcuerdo: (neun: String, orden: Int) -> Unit,
    onOpenCase: (neun: String) -> Unit,
    viewModel: AcuerdoSearchViewModel = viewModel(factory = AcuerdoSearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            SiseTopAppBar(
                title = stringResource(R.string.acuerdo_search_title),
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.acuerdo_search_field)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(focus),
            )
            val found = results
            when {
                found == null -> Hint(stringResource(R.string.acuerdo_search_hint))
                found.isEmpty() -> Hint(stringResource(R.string.acuerdo_search_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(found, key = { "${it.neun}/${it.orden}" }) { result ->
                        ResultCard(
                            result = result,
                            onOpenAcuerdo = { onOpenAcuerdo(result.neun, result.orden) },
                            onOpenCase = { onOpenCase(result.neun) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    MessageCard(
        text = text,
        icon = rememberVectorPainter(Icons.Filled.Search),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ResultCard(
    result: AcuerdoSearchResult,
    onOpenAcuerdo: () -> Unit,
    onOpenCase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlight = MaterialTheme.colorScheme.primary
    val highlightBackground = MaterialTheme.colorScheme.tertiaryContainer
    SiseCard(onClick = onOpenAcuerdo, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(painterResource(R.drawable.ic_description), size = 36.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    stringResource(
                        R.string.acuerdo_search_result,
                        result.expediente,
                        result.numero,
                        result.fechaPublicacion?.let(SiseDates::formatSpan) ?: stringResource(R.string.acuerdo_sin_publicar),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    result.organoName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            remember(result.snippet, highlight, highlightBackground) { highlighted(result.snippet, highlight, highlightBackground) },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onOpenCase, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.acuerdo_search_open_case))
        }
    }
}

/** The snippet on one line, with the matched words bold on a gold tint. */
private fun highlighted(snippet: String, color: Color, background: Color): AnnotatedString = buildAnnotatedString {
    var rest = snippet.replace('\n', ' ')
    while (rest.isNotEmpty()) {
        val start = rest.indexOf(SnippetMarkers.START)
        if (start < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, start))
        val end = rest.indexOf(SnippetMarkers.END, start + 1).takeIf { it >= 0 } ?: rest.length
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color, background = background)) {
            append(rest.substring(start + 1, end))
        }
        rest = if (end < rest.length) rest.substring(end + 1) else ""
    }
}
