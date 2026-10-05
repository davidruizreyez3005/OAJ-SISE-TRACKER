package mx.sisetracker.core

import java.net.URLEncoder

/**
 * A form POST to the portal's classic ASP pages.
 *
 * Those pages are ISO-8859-1, and `CircuitoName`/`OrgName` carry accents, so
 * the body is percent-encoded from ISO-8859-1 bytes ("México" becomes
 * `M%E9xico`). The result is plain ASCII: send [body] as-is, never through a
 * form builder that re-encodes it as UTF-8.
 */
class FormRequest(val url: String, val fields: List<Pair<String, String>>) {
    init {
        fields.forEach { (name, value) ->
            require(latin1.canEncode(name) && latin1.canEncode(value)) {
                "$name can't be sent to an ISO-8859-1 form: '$value'"
            }
        }
    }

    val encodedBody: String =
        fields.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }

    val body: ByteArray get() = encodedBody.toByteArray(Charsets.US_ASCII)

    override fun toString(): String = "POST $url $encodedBody"

    companion object {
        const val CONTENT_TYPE = "application/x-www-form-urlencoded"

        private val latin1 get() = Charsets.ISO_8859_1.newEncoder()

        // Charset-name overload: the Charset one needs Android API 33.
        private fun encode(text: String): String = URLEncoder.encode(text, "ISO-8859-1")
    }
}

/**
 * Builds the portal's search requests (steps C and D in CLAUDE.md).
 *
 * There is deliberately no builder for step E (`Accion=1`, the captcha-protected
 * search): the app never submits it (hard rule 1). It fetches the case page
 * directly with [CaseUrl.forLookup] instead.
 */
object SearchRequests {
    /**
     * Step C: loads the search form for one órgano, which lists its tipos de
     * asunto. Same fields the portal's `circuitos.asp` form posts.
     */
    fun loadForm(circuito: String, circuitoName: String, organismo: String): FormRequest =
        FormRequest(
            SiseUrls.SEARCH_FORM,
            listOf(
                "Organismo" to organismo,
                "Buscar" to "Buscar",
                "Circuito" to circuito,
                "CircuitoName" to circuitoName,
            ),
        )

    /**
     * Step D: re-renders [form] for [tipoAsunto] (`Accion=2`, what the portal
     * does when the tipo changes), which fills its tipos de procedimiento.
     * Only for tipos that show the procedimiento row.
     */
    fun loadProcedimientos(form: SearchForm, tipoAsunto: String, expediente: String = ""): FormRequest =
        loadProcedimientos(form.hiddenFields, tipoAsunto, expediente)

    /** Step D from a form's saved [SearchForm.hiddenFields]. */
    fun loadProcedimientos(hiddenFields: Map<String, String>, tipoAsunto: String, expediente: String = ""): FormRequest {
        require(TipoProcedimientoRule.isShown(tipoAsunto)) { "Tipo de asunto $tipoAsunto has no procedimientos" }
        fun hidden(name: String) = hiddenFields[name].orEmpty()
        return FormRequest(
            SiseUrls.SEARCH_FORM,
            listOf(
                "Circuito" to hidden("Circuito"),
                "CircuitoName" to hidden("CircuitoName"),
                "Organismo" to hidden("Organismo"),
                "OrgName" to hidden("OrgName"),
                "TipoOrganismo" to hidden("TipoOrganismo"),
                "TipoAsunto" to tipoAsunto,
                "Expediente" to expediente,
                "Accion" to "2",
            ),
        )
    }
}
