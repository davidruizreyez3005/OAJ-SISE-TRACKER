package mx.sisetracker.core

import java.net.URI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CaseUrlTest {
    private val url1183 =
        "https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025&tipoprocedimiento=0"
    private val case1183 = CaseUrl(tipoAsunto = "1", organismo = "767", expediente = "1183/2025", tipoProcedimiento = "0")

    @Test
    fun `parses a case URL with a raw slash`() {
        assertEquals(case1183, CaseUrl.parse(url1183))
    }

    @Test
    fun `parses a case URL with an encoded slash`() {
        assertEquals(case1183, CaseUrl.parse(url1183.replace("1183/2025", "1183%2f2025")))
        assertEquals(case1183, CaseUrl.parse(url1183.replace("1183/2025", "1183%2F2025")))
    }

    @Test
    fun `host, path and parameter names are case-insensitive`() {
        val url = "HTTPS://WWW.DGEJ.CJF.GOB.MX/SiseInternet/Reportes/VerCaptura.aspx" +
            "?TipoAsunto=1&Organismo=767&Expediente=1183/2025&TipoProcedimiento=0"
        assertEquals(case1183, CaseUrl.parse(url))
    }

    @Test
    fun `keeps values exactly as given`() {
        val url = "https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx" +
            "?tipoasunto=125&organismo=0767&expediente=12/2026-A&tipoprocedimiento=3042"
        assertEquals(CaseUrl("125", "0767", "12/2026-A", "3042"), CaseUrl.parse(url))
    }

    @Test
    fun `a missing tipoprocedimiento reads as 0`() {
        val url = "https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025"
        assertEquals(case1183, CaseUrl.parse(url))
    }

    @Test
    fun `rejects anything that isn't a case URL`() {
        assertNull(CaseUrl.parse("https://example.com/siseinternet/reportes/vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025"))
        assertNull(CaseUrl.parse("https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=38"))
        assertNull(CaseUrl.parse(url1183.replace("&expediente=1183/2025", "")))
        assertNull(CaseUrl.parse(url1183.replace("organismo=767", "organismo=")))
        assertNull(CaseUrl.parse(url1183.replace("1183/2025", "1183%2")))
        assertNull(CaseUrl.parse("vercaptura.aspx?tipoasunto=1&organismo=767&expediente=1183/2025"))
        assertNull(CaseUrl.parse("not a url"))
    }

    @Test
    fun `builds the portal's own URL`() {
        assertEquals(url1183, case1183.toUrl())
    }

    @Test
    fun `round-trips through parse`() {
        val case = CaseUrl("11", "18", "293/2026", "0")
        assertEquals(case, CaseUrl.parse(case.toUrl()))
    }

    @Test
    fun `extracts the case URL from the search result page`() {
        val html = Fixtures.load("expedienteytipo_result_1183-2025.html")

        val case = CaseUrl.fromSearchResult(html)

        assertEquals(case1183, case)
        assertEquals(url1183, case?.toUrl())
    }

    @Test
    fun `the tribunal case 293-2026 uses tipoasunto 11 at organismo 18`() {
        // The síntesis postback for 293/2026 echoes its page's form action.
        val delta = Fixtures.load("delta_ver-acuerdo_293-2026_orden3.txt")
        val formAction = Regex("\\|formAction\\|\\|([^|]*)\\|").find(delta)?.groupValues?.get(1).orEmpty()

        val case = CaseUrl.parse(URI(SiseUrls.CASE_PAGE).resolve(formAction).toString())

        assertEquals(CaseUrl("11", "18", "293/2026", "0"), case)
    }

    @Test
    fun `lookup uses tipoprocedimiento 0 when the row is hidden`() {
        val case = CaseUrl.forLookup(
            organismo = "767",
            tipoAsunto = "1",
            tipoProcedimientoShown = false,
            tipoProcedimiento = "3042",
            expediente = " 1183/2025 ",
        )

        assertEquals(case1183, case)
        assertEquals(url1183, case.toUrl())
    }

    @Test
    fun `lookup uses the chosen procedimiento when the row is shown`() {
        val case = CaseUrl.forLookup(
            organismo = "767",
            tipoAsunto = "125",
            tipoProcedimientoShown = true,
            tipoProcedimiento = "3042",
            expediente = "12/2026",
        )

        assertEquals(CaseUrl("125", "767", "12/2026", "3042"), case)
    }

    @Test
    fun `lookup needs a procedimiento when the row is shown`() {
        assertThrows<IllegalArgumentException> {
            CaseUrl.forLookup("767", "125", tipoProcedimientoShown = true, tipoProcedimiento = null, expediente = "12/2026")
        }
    }
}
