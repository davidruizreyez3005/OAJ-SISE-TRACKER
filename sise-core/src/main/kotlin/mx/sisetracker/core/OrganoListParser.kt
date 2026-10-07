package mx.sisetracker.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import org.jsoup.Jsoup

/** An órgano from a circuit's list. [id] is the organismo ID used everywhere else. */
data class Organo(
    val id: String,
    val name: String,
    /** 0-based position in the portal's list. */
    val position: Int,
) {
    val kind: OrganoKind get() = OrganoKind.fromName(name)

    val clase: OrganoClase get() = OrganoClase.fromName(name)

    val materias: Set<Materia> get() = Materia.fromName(name)

    /** For a closed órgano, the end of the active period its name carries; else null. */
    val closedOn: LocalDate? get() = OrganoPeriod.endOf(name)

    /** Closed by [today]: its cases stay searchable, the app only marks it "Cerrado". */
    fun isClosed(today: LocalDate): Boolean = closedOn?.let { it < today } == true
}

/**
 * Closed órganos stay listed with their active period at the end of the name:
 * `… en la Ciudad de México (13/12/2001 - 31/08/2024)`.
 */
object OrganoPeriod {
    private val period = Regex("""\((\d{2}/\d{2}/\d{4})\s*-\s*(\d{2}/\d{2}/\d{4})\)\s*\.?\s*$""")
    private val format = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun endOf(name: String): LocalDate? {
        val end = period.find(name)?.groupValues?.get(2) ?: return null
        return runCatching { LocalDate.parse(end, format) }.getOrNull()
    }
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
        return OrganoList(document.rowValue("Circuito:"), organos)
    }
}
