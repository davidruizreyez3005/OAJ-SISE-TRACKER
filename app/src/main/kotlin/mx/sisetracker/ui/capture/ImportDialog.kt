package mx.sisetracker.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.ui.ImportUiState

/**
 * "Importar expedientes": how many links are new and how many were already
 * saved. Importing makes no request; the cases load later (hard rule 3).
 */
@Composable
fun ImportDialog(state: ImportUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val new = state.preview.new.size
    val already = state.preview.alreadySaved
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (new > 0) {
                    Text(pluralStringResource(R.plurals.import_new, new, new), style = MaterialTheme.typography.titleSmall)
                }
                if (already > 0) {
                    Text(
                        pluralStringResource(R.plurals.import_already, already, already),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(if (new > 0) R.string.import_body else R.string.import_nothing_new),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            if (new > 0) {
                TextButton(onClick = onConfirm, enabled = !state.importing) { Text(stringResource(R.string.action_import)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (new > 0) {
                TextButton(onClick = onDismiss, enabled = !state.importing) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
