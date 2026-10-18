package mx.sisetracker.ui.acuerdo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import mx.sisetracker.core.Resumen
import mx.sisetracker.core.SintesisFormat
import mx.sisetracker.core.SiseDates
import mx.sisetracker.ui.components.BackButton
import mx.sisetracker.ui.components.InfoChip
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.MessageTone
import mx.sisetracker.ui.components.SiseCard
import mx.sisetracker.ui.components.SiseTopAppBar
import mx.sisetracker.ui.components.portalErrorText
import mx.sisetracker.ui.components.siseCardBorder
import mx.sisetracker.ui.theme.LocalBrandColors

@OptIn(ExperimentalLayoutApi::class)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SiseCard {
                Text(
                    listOf(state.expediente, state.organoName).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip(
                        stringResource(R.string.acuerdo_fecha_auto, SiseDates.formatSpan(acuerdo.fechaAuto)),
                        icon = painterResource(R.drawable.ic_event),
                    )
                    InfoChip(
                        stringResource(
                            R.string.acuerdo_fecha_publicacion,
                            acuerdo.fechaPublicacion?.let(SiseDates::formatSpan) ?: stringResource(R.string.acuerdo_sin_publicar),
                        ),
                        icon = painterResource(R.drawable.ic_event),
                        container = if (acuerdo.fechaPublicacion == null) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        content = if (acuerdo.fechaPublicacion == null) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    InfoChip(
                        stringResource(R.string.acuerdo_cuaderno, acuerdo.tipoCuaderno),
                        icon = painterResource(R.drawable.ic_folder),
                    )
                }
                if (acuerdo.fechaPublicacion == null && Resumen.isTruncated(acuerdo.resumen)) {
                    Text(
                        stringResource(R.string.acuerdo_sintesis_pendiente),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = state.loadingSintesis, enter = fadeIn(), exit = fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.acuerdo_loading), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.sintesisError?.let { error ->
                MessageCard(
                    text = portalErrorText(error, state.sintesisErrorLocation) + "\n" + stringResource(R.string.acuerdo_resumen_only),
                    tone = MessageTone.ERROR,
                ) {
                    TextButton(onClick = viewModel::loadSintesis) { Text(stringResource(R.string.action_retry)) }
                }
            }

            // The text on a page of its own, under a short gold rule.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = siseCardBorder(),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_description),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            stringResource(if (state.isComplete) R.string.acuerdo_sintesis_heading else R.string.acuerdo_resumen_heading),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(width = 40.dp, height = 3.dp)
                            .background(LocalBrandColors.current.goldRule, CircleShape),
                    )
                    val headingColor = MaterialTheme.colorScheme.primary
                    val text = remember(state.text, headingColor) { sintesisText(state.text, headingColor) }
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
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
