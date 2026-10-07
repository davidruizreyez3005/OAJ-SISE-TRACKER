package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * A page that doesn't parse says where it broke, so "formato inesperado" on a
 * device points at what to fix. Each case breaks one spot of a real fixture.
 * The location is built from constants and the row number only: it never
 * carries page text.
 */
class ParseLocationTest {
    private val base = Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html")
    private val row = Fixtures.load("vercaptura_acuerdo-sin-publicar_row.html")

    /** The 1183/2025 page with [extraRow] as the 29th and last acuerdo. */
    private fun withAcuerdo(extraRow: String): String {
        val grid = base.indexOf("id=\"grvAcuerdos\"")
        val end = base.indexOf("</table>", grid)
        return base.substring(0, end) + extraRow + base.substring(end)
    }

    private fun caseFailure(html: String): SiseParseException =
        assertThrows<SiseParseException> { CasePageParser.parse(html) }

    @Test
    fun `a malformed fecha del auto names the acuerdos row and field`() {
        val e = caseFailure(withAcuerdo(row.replace(">24-09-2026<", ">2026-09-24<")))
        assertEquals(ParseLocation(PageSection.ACUERDOS, 29, PageField.FECHA_AUTO), e.location)
    }

    @Test
    fun `a malformed fecha de publicacion names its field`() {
        val e = caseFailure(withAcuerdo(row.replace(">&nbsp;<", ">sin fecha<")))
        assertEquals(ParseLocation(PageSection.ACUERDOS, 29, PageField.FECHA_PUBLICACION), e.location)
    }

    @Test
    fun `a missing or malformed sintesis link names the link`() {
        val noLink = caseFailure(withAcuerdo(row.replace("javascript:DoVerAcuerdo", "javascript:Otra")))
        assertEquals(ParseLocation(PageSection.ACUERDOS, 29, PageField.SINTESIS_LINK), noLink.location)
        val badArgs = caseFailure(withAcuerdo(row.replace(",&quot;1068/2025&quot;)", ")")))
        assertEquals(ParseLocation(PageSection.ACUERDOS, 29, PageField.SINTESIS_LINK), badArgs.location)
    }

    @Test
    fun `a short row names the column count`() {
        val short = row.substringBefore("<td style=\"width:70%;\">") + "</tr>"
        val e = caseFailure(withAcuerdo(short))
        assertEquals(ParseLocation(PageSection.ACUERDOS, 29, PageField.CELL_COUNT), e.location)
    }

    @Test
    fun `resoluciones and related cases name their own grids`() {
        val resolucion = caseFailure(base.replace("lblFechaIngreso\">12/06/2026", "lblFechaIngreso\">junio"))
        assertEquals(ParseLocation(PageSection.RESOLUCIONES, 1, PageField.FECHA_INGRESO), resolucion.location)
        val relacionado = caseFailure(base.replace("lblFechaPresentacion\">24/08/2026", "lblFechaPresentacion\">agosto"))
        assertEquals(ParseLocation(PageSection.ASUNTOS_RELACIONADOS, 1, PageField.FECHA_RELACION), relacionado.location)
    }

    @Test
    fun `pages that aren't what was asked for say so`() {
        val notCase = caseFailure("<html><body>Error</body></html>")
        assertEquals(ParseLocation(PageSection.CASE_PAGE, field = PageField.STRUCTURE), notCase.location)

        val sintesis = Fixtures.load("veracuerdo_1183-2025_orden38.html")
        val badDate = assertThrows<SiseParseException> {
            SintesisPageParser.parse(sintesis.replace(">01/09/2026<", "><"))
        }
        assertEquals(ParseLocation(PageSection.SINTESIS, field = PageField.FECHA_PUBLICACION), badDate.location)

        val form = assertThrows<SiseParseException> { SearchFormParser.parse("<html></html>") }
        assertEquals(ParseLocation(PageSection.SEARCH_FORM, field = PageField.STRUCTURE), form.location)
        val organos = assertThrows<SiseParseException> { OrganoListParser.parse("<html></html>") }
        assertEquals(ParseLocation(PageSection.ORGANO_LIST, field = PageField.STRUCTURE), organos.location)
    }

    @Test
    fun `the location carries no page text, only the message does`() {
        val e = caseFailure(withAcuerdo(row.replace(">24-09-2026<", ">Chilpancingo<")))
        assertFalse(e.location.toString().contains("Chilpancingo"))
        assertEquals(true, e.cause?.message?.contains("Chilpancingo"))
    }
}
