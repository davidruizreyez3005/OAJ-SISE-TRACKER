package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchHelpersTest {
    @Test
    fun `organo kind from the name`() {
        assertEquals(OrganoKind.JUZGADOS, OrganoKind.fromName("Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México"))
        assertEquals(OrganoKind.TRIBUNALES, OrganoKind.fromName("Segundo Tribunal Colegiado en Materia Penal del Primer Circuito"))
        assertEquals(OrganoKind.TRIBUNALES, OrganoKind.fromName("Primer Tribunal Unitario del Primer Circuito"))
        assertEquals(OrganoKind.OTROS, OrganoKind.fromName("Centro de Justicia Penal Federal en la Ciudad de México"))
    }

    @Test
    fun `search text ignores accents, case and word order`() {
        val organo = "Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México"

        assertTrue(SearchText.matches(organo, "mexico"))
        assertTrue(SearchText.matches(organo, "PENAL sexto"))
        assertTrue(SearchText.matches(organo, ""))
        assertFalse(SearchText.matches(organo, "civil"))
        assertEquals("suspension de plano", SearchText.fold("  SUSPENSIÓN  de plano "))
    }

    @Test
    fun `usual expediente format`() {
        assertTrue(ExpedienteFormat.isUsual("1183/2025"))
        assertTrue(ExpedienteFormat.isUsual(" 7/2026 "))
        assertFalse(ExpedienteFormat.isUsual("1183"))
        assertFalse(ExpedienteFormat.isUsual("1183/25"))
        assertFalse(ExpedienteFormat.isUsual("12/2026-A"))
    }

    @Test
    fun `finds a case URL in shared text`() {
        val text = "Mira este expediente: https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx" +
            "?tipoasunto=1&organismo=767&expediente=1183%2f2025&tipoprocedimiento=0. Saludos"

        assertEquals(CaseUrl("1", "767", "1183/2025", "0"), CaseUrl.findIn(text))
    }

    @Test
    fun `skips other links in shared text`() {
        val text = "https://www.oaj.gob.mx/ y (https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx" +
            "?tipoasunto=11&organismo=18&expediente=293/2026&tipoprocedimiento=0)"

        assertEquals(CaseUrl("11", "18", "293/2026", "0"), CaseUrl.findIn(text))
        assertNull(CaseUrl.findIn("https://www.oaj.gob.mx/ sin expediente"))
    }

    @Test
    fun `circuitos URL`() {
        assertEquals(
            "https://www.dgej.cjf.gob.mx/internet/expedientes/circuitos.asp?Cir=1&Exp=1",
            SiseUrls.circuitos("1"),
        )
    }
}
