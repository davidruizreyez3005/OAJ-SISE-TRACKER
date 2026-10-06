package mx.sisetracker.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses the search form on `ExpedienteyTipo.asp` (steps C and D). The page is
 * windows-1252 on the wire; the caller passes it already decoded.
 *
 * The reCAPTCHA in `div#recaptchaArea` is deliberately ignored (hard rule 1).
 */
object SearchFormParser {
    /** @throws SiseParseException when the page has no search form. */
    fun parse(html: String): SearchForm {
        val document = Jsoup.parse(html, SiseUrls.SEARCH_FORM)
        val form = document.selectFirst("form[name=Editar]")
            ?: throw SiseParseException("Search page has no Editar form")
        val tipoAsunto = form.selectFirst("select[name=TipoAsunto]")
            ?: throw SiseParseException("Search form has no TipoAsunto select")
        val expediente = form.selectFirst("input[name=Expediente]")
        return SearchForm(
            tipoAsuntoOptions = tipoAsunto.dropdownOptions(),
            tipoProcedimientoOptions = form.selectFirst("select[name=TipoProcedimiento]")?.dropdownOptions().orEmpty(),
            isTipoProcedimientoShown = currentValue(tipoAsunto)?.let(TipoProcedimientoRule::isShown) ?: false,
            expediente = expediente?.attr("value").orEmpty(),
            expedienteMaxLength = expediente?.attr("maxlength")?.trim()?.toIntOrNull(),
            hiddenFields = form.select("input[type=hidden]")
                .filter { it.hasAttr("name") }
                .associate { it.attr("name") to it.attr("value") },
        )
    }

    /** What the browser would read as the select's value: the selected option, else the first. */
    private fun currentValue(select: Element): String? {
        val options = select.select("option")
        return (options.firstOrNull { it.hasAttr("selected") } ?: options.firstOrNull())?.attr("value")
    }
}
