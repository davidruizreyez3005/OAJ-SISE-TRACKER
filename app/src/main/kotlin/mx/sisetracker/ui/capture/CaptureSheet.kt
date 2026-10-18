package mx.sisetracker.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.ui.CaptureUiState
import mx.sisetracker.ui.components.IconBadge
import mx.sisetracker.ui.components.InfoChip
import mx.sisetracker.ui.components.MessageCard
import mx.sisetracker.ui.components.MessageTone
import mx.sisetracker.ui.components.portalErrorText

/**
 * "¿Guardar este expediente?": offered for a case seen in the portal WebView,
 * a shared link or a pasted one. Shows only what the link itself says until
 * "Guardar" fetches the case.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSheet(
    state: CaptureUiState,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val url = state.url
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(painterResource(R.drawable.ic_link), size = 44.dp)
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.preview_expediente, url.expediente),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                InfoChip(stringResource(R.string.capture_ids, url.organismo, url.tipoAsunto), icon = painterResource(R.drawable.ic_tag))
                if (url.tipoProcedimiento != CaseUrl.NO_TIPO_PROCEDIMIENTO) {
                    InfoChip(stringResource(R.string.capture_procedimiento, url.tipoProcedimiento))
                }
            }
            if (state.notFound) {
                MessageCard(text = stringResource(R.string.search_not_found))
            }
            state.error?.let { error ->
                MessageCard(text = portalErrorText(error, state.errorLocation), tone = MessageTone.ERROR)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !state.saving) {
                    Text(stringResource(R.string.action_not_now))
                }
                Button(onClick = onSave, enabled = !state.saving) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(if (state.error != null) R.string.action_retry else R.string.action_save))
                    }
                }
            }
        }
    }
}
