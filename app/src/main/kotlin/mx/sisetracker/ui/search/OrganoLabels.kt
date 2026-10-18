package mx.sisetracker.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mx.sisetracker.R
import mx.sisetracker.core.Circuitos
import mx.sisetracker.core.Materia
import mx.sisetracker.core.OrganoClase
import mx.sisetracker.core.OrganoKind

@StringRes
internal fun OrganoKind.labelRes(): Int = when (this) {
    OrganoKind.JUZGADOS -> R.string.kind_juzgados
    OrganoKind.TRIBUNALES -> R.string.kind_tribunales
    OrganoKind.OTROS -> R.string.kind_otros
}

@StringRes
internal fun OrganoClase.pluralRes(): Int = when (this) {
    OrganoClase.JUZGADO_DISTRITO -> R.string.clase_juzgado_distrito_plural
    OrganoClase.COLEGIADO_CIRCUITO -> R.string.clase_colegiado_circuito_plural
    OrganoClase.COLEGIADO_APELACION -> R.string.clase_colegiado_apelacion_plural
    OrganoClase.TRIBUNAL_LABORAL -> R.string.clase_tribunal_laboral_plural
    OrganoClase.PLENO_REGIONAL -> R.string.clase_pleno_regional_plural
    OrganoClase.CONFLICTOS_LABORALES -> R.string.clase_conflictos_laborales_plural
    OrganoClase.OTRO -> R.string.clase_otro_plural
}

@StringRes
internal fun OrganoClase.labelRes(): Int = when (this) {
    OrganoClase.JUZGADO_DISTRITO -> R.string.clase_juzgado_distrito
    OrganoClase.COLEGIADO_CIRCUITO -> R.string.clase_colegiado_circuito
    OrganoClase.COLEGIADO_APELACION -> R.string.clase_colegiado_apelacion
    OrganoClase.TRIBUNAL_LABORAL -> R.string.clase_tribunal_laboral
    OrganoClase.PLENO_REGIONAL -> R.string.clase_pleno_regional
    OrganoClase.CONFLICTOS_LABORALES -> R.string.clase_conflictos_laborales
    OrganoClase.OTRO -> R.string.clase_otro
}

@StringRes
internal fun Materia.labelRes(): Int = when (this) {
    Materia.PENAL -> R.string.materia_penal
    Materia.CIVIL -> R.string.materia_civil
    Materia.ADMINISTRATIVA -> R.string.materia_administrativa
    Materia.TRABAJO -> R.string.materia_trabajo
    Materia.MERCANTIL -> R.string.materia_mercantil
    Materia.MIXTA -> R.string.materia_mixta
}

/** "Tribunal Colegiado de Circuito · Penal": what an órgano is. A bare organismo number has no description. */
@Composable
internal fun KnownOrgano.description(): String {
    if (name.isEmpty()) return stringResource(R.string.search_organo_number, id)
    val clase = stringResource(clase.labelRes())
    if (materias.isEmpty()) return clase
    val materia = materias.sortedBy { it.ordinal }.map { stringResource(it.labelRes()) }.joinToString(", ")
    return stringResource(R.string.search_organo_description, clase, materia)
}

/** The órgano's name, or "Organismo 9999" for a bare number. */
@Composable
internal fun KnownOrgano.displayName(): String = name.ifEmpty { stringResource(R.string.search_organo_number, id) }

/** "Primer Circuito · Ciudad de México", for a circuit number. */
internal fun circuitoShortLabel(num: String?): String? {
    val circuito = num?.let(Circuitos::byNum) ?: return null
    return listOf(circuito.ordinal, circuito.region).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** "Cerrado": the órgano's name carries an active period that has ended. */
@Composable
internal fun ClosedChip(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.organo_closed),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
