package mx.sisetracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.data.net.PortalError

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

/** A short message in a card, with optional actions below it. */
@Composable
fun MessageCard(
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@StringRes
fun PortalError.messageRes(): Int = when (this) {
    PortalError.NETWORK -> R.string.error_network
    PortalError.SERVER -> R.string.error_server
    PortalError.UNEXPECTED_PAGE -> R.string.error_unexpected_page
}
