package mx.sisetracker.ui.acuerdo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.sisetracker.R
import mx.sisetracker.core.SintesisFormat
import mx.sisetracker.core.SiseDates
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.messageRes

@Composable
fun AcuerdoScreen(
    onBack: () -> Unit,
    viewModel: AcuerdoViewModel = viewModel(factory = AcuerdoViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val acuerdo = state.acuerdo

    Scaffold(
        topBar = {
            SiseTopAppBar(
                title = acuerdo?.let { stringResource(R.string.acuerdo_title, it.numero) }.orEmpty(),
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (acuerdo == null) return@Scaffold
        // One selectable text in a scrolling column: smooth for 20,000+
        // characters and selectable from start to end.
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                listOf(state.expediente, state.organoName).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.acuerdo_fecha_auto, SiseDates.formatSpan(acuerdo.fechaAuto)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.acuerdo_fecha_publicacion, SiseDates.formatSpan(acuerdo.fechaPublicacion)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.acuerdo_cuaderno, acuerdo.tipoCuaderno),
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (state.loadingSintesis) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.acuerdo_loading), style = MaterialTheme.typography.bodySmall)
            }
            state.sintesisError?.let { error ->
                MessageCard(text = stringResource(error.messageRes()) + " " + stringResource(R.string.acuerdo_resumen_only)) {
                    TextButton(onClick = viewModel::loadSintesis) { Text(stringResource(R.string.action_retry)) }
                }
            }

            val headingColor = MaterialTheme.colorScheme.primary
            val text = remember(state.text, headingColor) { sintesisText(state.text, headingColor) }
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** The text as published, with short ALL-CAPS lines styled as headings. */
private fun sintesisText(text: String, headingColor: Color): AnnotatedString = buildAnnotatedString {
    text.lines().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        if (SintesisFormat.isHeading(line)) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = headingColor)) { append(line) }
        } else {
            append(line)
        }
    }
}
