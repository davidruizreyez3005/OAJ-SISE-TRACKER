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
import mx.sisetracker.core.PageField
import mx.sisetracker.core.PageSection
import mx.sisetracker.core.ParseLocation
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
    PortalError.SECURE_CONNECTION -> R.string.error_secure_connection
    PortalError.SERVER -> R.string.error_server
    PortalError.UNEXPECTED_PAGE -> R.string.error_unexpected_page
}

/**
 * The error's message, plus where the page broke when it didn't parse
 * ("Dónde: Acuerdos, fila 16, fecha de publicación"), so a screenshot says
 * what to fix. The location never contains case text.
 */
@Composable
fun portalErrorText(error: PortalError, location: ParseLocation?): String {
    val message = stringResource(error.messageRes())
    if (location == null) return message
    val parts = buildList {
        add(stringResource(location.section.labelRes()))
        location.row?.let { add(stringResource(R.string.error_location_row, it)) }
        location.field?.let { add(stringResource(it.labelRes())) }
    }
    return message + "\n" + stringResource(R.string.error_location, parts.joinToString(", "))
}

@StringRes
private fun PageSection.labelRes(): Int = when (this) {
    PageSection.CASE_PAGE -> R.string.error_section_case_page
    PageSection.ACUERDOS -> R.string.error_section_acuerdos
    PageSection.RESOLUCIONES -> R.string.error_section_resoluciones
    PageSection.ASUNTOS_RELACIONADOS -> R.string.error_section_relacionados
    PageSection.SINTESIS -> R.string.error_section_sintesis
    PageSection.SEARCH_FORM -> R.string.error_section_search_form
    PageSection.ORGANO_LIST -> R.string.error_section_organo_list
}

@StringRes
private fun PageField.labelRes(): Int = when (this) {
    PageField.STRUCTURE -> R.string.error_field_structure
    PageField.NEUN -> R.string.error_field_neun
    PageField.EXPEDIENTE -> R.string.error_field_expediente
    PageField.CELL_COUNT -> R.string.error_field_cell_count
    PageField.FECHA_AUTO -> R.string.error_field_fecha_auto
    PageField.FECHA_PUBLICACION -> R.string.error_field_fecha_publicacion
    PageField.SINTESIS_LINK -> R.string.error_field_sintesis_link
    PageField.SINTESIS_TEXT -> R.string.error_field_sintesis_text
    PageField.FECHA_INGRESO -> R.string.error_field_fecha_ingreso
    PageField.FECHA_RELACION -> R.string.error_field_fecha_relacion
    PageField.TIPO_ASUNTO -> R.string.error_field_tipo_asunto
}
