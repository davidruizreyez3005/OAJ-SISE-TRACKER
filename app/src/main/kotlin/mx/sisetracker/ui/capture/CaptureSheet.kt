package mx.sisetracker.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.core.CaseUrl

/**
 * "¿Guardar este expediente?": offered for a case seen in the portal WebView,
 * a shared link or a pasted one. Shows only what the link itself says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(
    url: CaseUrl,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.preview_expediente, url.expediente), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.capture_ids, url.organismo, url.tipoAsunto),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (url.tipoProcedimiento != CaseUrl.NO_TIPO_PROCEDIMIENTO) {
                Text(
                    stringResource(R.string.capture_procedimiento, url.tipoProcedimiento),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = androidx.compose.ui.Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
                Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
