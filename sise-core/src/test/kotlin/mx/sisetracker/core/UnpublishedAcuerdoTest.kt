package mx.sisetracker.core

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * An acuerdo not published yet: its "Fecha de publicación" cell is `&nbsp;`
 * and its link passes `""`. The row is verbatim from 1068/2025 at órgano 721
 * (October 2026; `vercaptura_acuerdo-sin-publicar_row.html`), appended to the
 * 1183/2025 grid. Before, the whole page failed with "formato inesperado".
 */
class UnpublishedAcuerdoTest {
    private val html = Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html").let { base ->
        val grid = base.indexOf("id=\"grvAcuerdos\"")
        val end = base.indexOf("</table>", grid)
        base.substring(0, end) + Fixtures.load("vercaptura_acuerdo-sin-publicar_row.html") + base.substring(end)
    }
    private val page = (CasePageParser.parse(html) as CaseLookup.Found).page
    private val unpublished = page.acuerdos.single { it.orden == 18 && it.link.neun == "39712162" }

    @Test
    fun `the page parses, with the acuerdo unpublished`() {
        assertEquals(29, page.acuerdos.size)
        assertEquals("16", unpublished.numero)
        assertEquals(LocalDate.of(2026, 9, 24), unpublished.fechaAuto)
        assertNull(unpublished.fechaPublicacion)
        assertFalse(unpublished.isPublished)
        assertTrue(unpublished.isResumenTruncated)
        assertEquals("", unpublished.link.fechaPublicacion)
        assertEquals("24/09/2026", unpublished.link.fechaAuto)
        assertTrue(page.acuerdos.filter { it !== unpublished }.all { it.isPublished })
    }

    @Test
    fun `its sintesis url passes the empty date as the portal does`() {
        assertTrue(VerAcuerdoUrl.build(unpublished).endsWith("&listaFAuto=24/09/2026&listaFPublicacion="))
    }

    @Test
    fun `a blank grid date is null, a malformed one still fails`() {
        assertNull(SiseDates.parseGridOrNull(" "))
        assertNull(SiseDates.parseGridOrNull(""))
        assertEquals(LocalDate.of(2026, 9, 3), SiseDates.parseGridOrNull("03-09-2026"))
        org.junit.jupiter.api.assertThrows<SiseParseException> { SiseDates.parseGridOrNull("2026-09-03") }
        org.junit.jupiter.api.assertThrows<SiseParseException> { SiseDates.datePart("") }
        assertEquals("", SiseDates.datePartOrEmpty(""))
    }
}
