package mx.sisetracker.core

import org.jsoup.Jsoup

/**
 * A case page address:
 * `vercaptura.aspx?tipoasunto={t}&organismo={o}&expediente={n/yyyy}&tipoprocedimiento={p}`.
 *
 * Values are kept exactly as given; parsing only percent-decodes them.
 */
data class CaseUrl(
    val tipoAsunto: String,
    val organismo: String,
    val expediente: String,
    val tipoProcedimiento: String,
) {
    init {
        require(tipoAsunto.isNotBlank()) { "tipoAsunto is blank" }
        require(organismo.isNotBlank()) { "organismo is blank" }
        require(expediente.isNotBlank()) { "expediente is blank" }
        require(tipoProcedimiento.isNotBlank()) { "tipoProcedimiento is blank" }
    }

    /** The case page URL. The expediente's slash stays raw, like the portal's own links. */
    fun toUrl(): String = buildString {
        append(SiseUrls.CASE_PAGE)
        append("?tipoasunto=").append(QueryString.encode(tipoAsunto))
        append("&organismo=").append(QueryString.encode(organismo))
        append("&expediente=").append(QueryString.encode(expediente))
        append("&tipoprocedimiento=").append(QueryString.encode(tipoProcedimiento))
    }

    companion object {
        /** What the portal sends when the tipo de asunto has no procedimiento. */
        const val NO_TIPO_PROCEDIMIENTO = "0"

        private val urlPattern =
            Regex("^(https?)://([^/?#]+)([^?#]*)(?:\\?([^#]*))?(?:#.*)?$", RegexOption.IGNORE_CASE)

        /**
         * The case page for the user's dropdown choices: what the portal's
         * iframe would show after its (captcha-protected) search.
         *
         * `tipoprocedimiento` is `0` unless the tipo de asunto shows the
         * procedimiento row, and then the chosen procedimiento. That second
         * part is unverified (every capture so far had `0`); this is the one
         * place to change once the pending Accion=2 fixture confirms it.
         */
        fun forLookup(
            organismo: String,
            tipoAsunto: String,
            tipoProcedimientoShown: Boolean,
            tipoProcedimiento: String?,
            expediente: String,
        ): CaseUrl {
            val procedimiento = if (tipoProcedimientoShown) {
                requireNotNull(tipoProcedimiento?.takeIf { it.isNotBlank() }) {
                    "Tipo de asunto $tipoAsunto needs a tipo de procedimiento"
                }
            } else {
                NO_TIPO_PROCEDIMIENTO
            }
            return CaseUrl(
                tipoAsunto = tipoAsunto,
                organismo = organismo,
                expediente = expediente.trim(),
                tipoProcedimiento = procedimiento,
            )
        }

        /**
         * Parses a `vercaptura.aspx` URL, with the expediente's slash raw or as
         * `%2f`. Returns null for anything else. A missing `tipoprocedimiento`
         * is read as [NO_TIPO_PROCEDIMIENTO].
         */
        fun parse(url: String): CaseUrl? {
            val match = urlPattern.matchEntire(url.trim()) ?: return null
            val (_, authority, path, query) = match.destructured
            if (!authority.substringBefore(':').equals(SiseUrls.HOST, ignoreCase = true)) return null
            if (!path.equals(SiseUrls.CASE_PAGE_PATH, ignoreCase = true)) return null

            val params = try {
                QueryString.parse(query)
            } catch (e: IllegalArgumentException) {
                return null
            }
            fun param(name: String): String? =
                params.firstOrNull { it.first.equals(name, ignoreCase = true) }
                    ?.second
                    ?.takeIf { it.isNotBlank() }

            return CaseUrl(
                tipoAsunto = param("tipoasunto") ?: return null,
                organismo = param("organismo") ?: return null,
                expediente = param("expediente") ?: return null,
                tipoProcedimiento = param("tipoprocedimiento") ?: NO_TIPO_PROCEDIMIENTO,
            )
        }

        private val urlInText = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

        /** The first case URL in shared or pasted text, if any. */
        fun findIn(text: String): CaseUrl? =
            urlInText.findAll(text).firstNotNullOfOrNull { match ->
                parse(match.value.trimEnd('.', ',', ';', ')', ']', '>'))
            }

        /**
         * The case shown by the portal's search result page
         * (`ExpedienteyTipo.asp`), which embeds it as `iframe#ifr`.
         */
        fun fromSearchResult(html: String): CaseUrl? {
            val document = Jsoup.parse(html, SiseUrls.SEARCH_FORM)
            val src = document.selectFirst("iframe#ifr")?.absUrl("src")
            return src?.takeIf { it.isNotEmpty() }?.let(::parse)
        }
    }
}
