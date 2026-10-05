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
