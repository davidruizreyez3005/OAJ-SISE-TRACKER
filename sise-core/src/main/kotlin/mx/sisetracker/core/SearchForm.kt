package mx.sisetracker.core

/** One `<option>` of a portal dropdown: value and label exactly as given, in page order. */
data class FormOption(
    val value: String,
    val label: String,
    /** 0-based position among the real (non-placeholder) options. */
    val position: Int,
    val selected: Boolean,
)

/** The portal's search form (`form[name=Editar]` on `ExpedienteyTipo.asp`). */
data class SearchForm(
    val tipoAsuntoOptions: List<FormOption>,
    val tipoProcedimientoOptions: List<FormOption>,
    /** Whether the portal shows the "Tipo de procedimiento" row for the current tipo de asunto. */
    val isTipoProcedimientoShown: Boolean,
    val expediente: String,
    val expedienteMaxLength: Int?,
    /** Hidden inputs in page order: Circuito, CircuitoName, Organismo, OrgName, TipoOrganismo, Accion. */
    val hiddenFields: Map<String, String>,
) {
    val selectedTipoAsunto: FormOption? get() = tipoAsuntoOptions.firstOrNull { it.selected }
}

/**
 * The portal shows the "Tipo de procedimiento" row only for some tipos de
 * asunto (its `EjecutaAntes()` script). The app shows it under the same rule.
 */
object TipoProcedimientoRule {
    val TIPOS_DE_ASUNTO = setOf(6, 9, 125, 126)

    /** Compares numerically, like the script's loose `==`. */
    fun isShown(tipoAsunto: String): Boolean = tipoAsunto.trim().toIntOrNull() in TIPOS_DE_ASUNTO
}
