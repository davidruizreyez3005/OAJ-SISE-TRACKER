package mx.sisetracker.core

import org.jsoup.Jsoup

/** An órgano from a circuit's list. [id] is the organismo ID used everywhere else. */
data class Organo(
    val id: String,
    val name: String,
    /** 0-based position in the portal's list. */
    val position: Int,
) {
    val kind: OrganoKind get() = OrganoKind.fromName(name)
}

/** A circuit's `circuitos.asp` page (step B). */
data class OrganoList(
    /**
     * The uppercase name the page shows ("PRIMER CIRCUITO"), which the search
     * form sends as `CircuitoName`. Null if the page doesn't show it.
     */
    val circuitoName: String?,
    val organos: List<Organo>,
)

/**
 * Parses `circuitos.asp?Cir={n}&Exp=1`: one flat `select[name=Organismo]`,
 * with no type selector, so kinds come from [OrganoKind.fromName]. The page is
 * windows-1252 on the wire; the caller passes it already decoded.
 */
object OrganoListParser {
    /** @throws SiseParseException when the page has no Organismo select. */
    fun parse(html: String): OrganoList {
        val document = Jsoup.parse(html, SiseUrls.circuitos("1"))
        val select = document.selectFirst("select[name=Organismo]")
            ?: throw SiseParseException("Órgano list has no Organismo select")
        val organos = select.dropdownOptions().map { Organo(it.value, it.label, it.position) }
        val circuitoName = document.select("th")
            .firstOrNull { SiseText.normalizeSpace(it.text()).startsWith("Circuito:") }
            ?.nextElementSibling()
            ?.takeIf { it.tagName() == "td" }
            ?.let { SiseText.normalizeSpace(it.text()) }
            ?.takeIf { it.isNotEmpty() }
        return OrganoList(circuitoName, organos)
    }
}
