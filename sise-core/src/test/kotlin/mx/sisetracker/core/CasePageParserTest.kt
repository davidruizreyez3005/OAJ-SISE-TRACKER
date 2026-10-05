package mx.sisetracker.core

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Expected values from fixtures/README.md, `vercaptura_1183-2025_amparo-indirecto.html`. */
class CasePageParserTest {
    private val html = Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html")
    private val page = parseFound(html)

    @Test
    fun header() {
        assertEquals("40612904", page.neun)
        assertEquals("1183/2025", page.expediente)
        assertEquals("20255739005400076/2025", page.noControlOcc)
        assertEquals("Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México", page.organoName)
        assertEquals("Amparo Indirecto", page.tipoAsuntoName)
        assertEquals(1, page.resolucionesCount)
    }

    @Test
    fun `acuerdos are keyed by orden, in page order`() {
        assertEquals(28, page.acuerdos.size)
        assertEquals(
            listOf(1, 2, 4, 5, 6, 8, 9, 10, 12, 14, 15, 16, 17, 19, 21, 23, 24, 26, 27, 30, 31, 32, 33, 34, 35, 36, 37, 38),
            page.acuerdos.map { it.orden },
        )
        assertEquals((1..28).map { it.toString() }, page.acuerdos.map { it.numero })
    }

    @Test
    fun `first acuerdo`() {
        val acuerdo = page.acuerdos[0]

        assertEquals("1", acuerdo.numero)
        assertEquals(1, acuerdo.orden)
        assertEquals(LocalDate.of(2025, 11, 27), acuerdo.fechaAuto)
        assertEquals(LocalDate.of(2025, 11, 28), acuerdo.fechaPublicacion)
        assertEquals("Principal", acuerdo.tipoCuaderno)
        assertTrue(acuerdo.resumen.startsWith("Con fundamento en los artículos 15, 126 y 160 de la Ley de Amparo"))
        assertTrue(acuerdo.resumen.endsWith(" ..."))
        assertTrue(acuerdo.isResumenTruncated)
    }

    @Test
    fun `third acuerdo has orden 4 and a complete resume`() {
        val acuerdo = page.acuerdos[2]

        assertEquals("3", acuerdo.numero)
        assertEquals(4, acuerdo.orden)
        assertEquals(LocalDate.of(2025, 12, 5), acuerdo.fechaAuto)
        assertEquals(LocalDate.of(2025, 12, 8), acuerdo.fechaPublicacion)
        assertEquals("Incidental", acuerdo.tipoCuaderno)
        assertEquals("Único. Se declara sin materia la suspensión definitiva solicitada.", acuerdo.resumen)
        assertFalse(acuerdo.isResumenTruncated)
    }

    @Test
    fun `last acuerdo`() {
        val acuerdo = page.acuerdos[27]

        assertEquals("28", acuerdo.numero)
        assertEquals(38, acuerdo.orden)
        assertEquals(LocalDate.of(2026, 8, 31), acuerdo.fechaAuto)
        assertEquals(LocalDate.of(2026, 9, 1), acuerdo.fechaPublicacion)
        assertEquals("Principal", acuerdo.tipoCuaderno)
    }

    @Test
    fun `every acuerdo links to this case`() {
        page.acuerdos.forEach {
            assertEquals("767", it.link.organismoId)
            assertEquals("40612904", it.link.neun)
            assertEquals("1", it.link.asuntoId)
            assertEquals("1183/2025", it.link.expediente)
        }
    }

    @Test
    fun `resumes keep their line breaks without leading or trailing blank lines`() {
        val orden2 = page.acuerdos.single { it.orden == 2 }.resumen
        assertTrue(orden2.startsWith("De oficio se abre por separado el incidente de suspensión.\nAudiencia\nHora: 11:03"))

        // The raw cell starts with a line break.
        val orden6 = page.acuerdos.single { it.orden == 6 }.resumen
        assertTrue(orden6.startsWith("Agréguese al expediente la constancia de notificación"))

        // The raw cell ends with " \n".
        val orden5 = page.acuerdos.single { it.orden == 5 }.resumen
        assertTrue(orden5.endsWith("\n\nPóngase a la vista de las partes."))
        assertFalse(page.acuerdos.single { it.orden == 5 }.isResumenTruncated)
    }

    @Test
    fun `sintesis URL for orden 38 matches the portal's`() {
        val url = VerAcuerdoUrl.build(page.acuerdos.single { it.orden == 38 })

        assertEquals(
            "https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=38&listaCatOrg=767&listaNeun=40612904&listaAsuId=1&listaExped=1183/2025&listaFAuto=31/08/2026&listaFPublicacion=01/09/2026",
            url,
        )
        assertEquals(Deltas.verAcuerdoUrl("delta_ver-acuerdo_1183-2025_orden38.txt"), url)
    }

    @Test
    fun resoluciones() {
        val resolucion = page.resoluciones.single()

        assertEquals("40612904", resolucion.neun)
        assertEquals(LocalDate.of(2026, 6, 12), resolucion.fechaIngreso)
        assertEquals(
            "Se concede el amparo respecto del acuerdo que concede la extradición internacional del quejoso a los Estados Unidos de América.",
            resolucion.tema,
        )
        val archivoUrl = resolucion.archivoUrl.orEmpty()
        assertTrue(archivoUrl.startsWith("http://sise.cjf.gob.mx/SVP/word1.aspx?arch=767/0767000040612904025.pdf_1&"))
        assertFalse(archivoUrl.contains("&amp;"))
    }

    @Test
    fun `asuntos relacionados`() {
        val related = page.asuntosRelacionados.single()

        assertEquals("42423129", related.neun)
        assertEquals("293/2026", related.expediente)
        assertEquals("Segundo Tribunal Colegiado en Materia Penal del Primer Circuito - Amparo en revisión", related.organo)
        assertEquals("Segundo Tribunal Colegiado en Materia Penal del Primer Circuito", related.organoName)
        assertEquals("Amparo en revisión", related.tipoAsuntoName)
        assertEquals(LocalDate.of(2026, 8, 24), related.fechaRelacion)
    }

    @Test
    fun `captura de informacion sections`() {
        assertEquals(5, page.captura.partyCount)
        assertEquals(
            listOf(
                "Datos Generales",
                "Actos Reclamados",
                "Resolucion Inicial",
                "Audiencia",
                "Sentencia",
                "Sentencia Recurso Contra Sentencia",
                "Suspension Audiencia Incidental",
                "Suspension Suspension Definitiva",
                "Suspension Suspension De Plano",
                "Suspension Suspension Provisional",
            ),
            page.captura.sections,
        )
    }

    @Test
    fun `captura de informacion values`() {
        val captura = page.captura

        assertEquals(listOf("III"), captura.values("Mesa"))
        assertEquals(listOf("Ampara para efectos"), captura.values("Sentido sentencia o resolución que puso fin al juicio"))
        assertEquals(listOf("293/2026"), captura.values("Número de toca"))
        // Labels repeat, one record each.
        val diferimientos = captura.entries.filter { it.label == "Fecha señalada para audiencia constitucional(diferimiento)" }
        assertEquals(listOf("03/02/2026", "19/02/2026", "05/03/2026", "23/03/2026"), diferimientos.map { it.value })
        assertEquals(listOf(1, 2, 3, 4), diferimientos.map { it.group })
        assertTrue(diferimientos.all { it.section == "Audiencia" })
    }

    @Test
    fun `captura de informacion labels have normalized whitespace`() {
        assertEquals(listOf("27/11/2025"), page.captura.values("Fecha presentación"))
        assertTrue(page.captura.entries.none { "  " in it.label || "  " in it.value })
    }

    @Test
    fun `hides Observaciones like the portal's own script`() {
        // Same markup with the first panel's "Mesa" row relabelled.
        val withObservaciones = html.replaceFirst(">Mesa</td>", ">Observaciones</td>")

        val captura = parseFound(withObservaciones).captura

        assertTrue(captura.entries.none { "Observaciones" in it.label })
        assertTrue(captura.values("Mesa").isEmpty())
        assertEquals(page.captura.entries.size - 1, captura.entries.size)
    }

    @Test
    fun `rejects a page without the case header`() {
        assertThrows<SiseParseException> { CasePageParser.parse("<html><body>Error</body></html>") }
    }

    @Test
    fun `an empty NEUN means not found`() {
        // fixtures/README.md: every header span present and empty, no tables, no error message.
        val lookup = CasePageParser.parse(Fixtures.load("vercaptura_not-found_99999-2025.html"))

        assertEquals(CaseLookup.NotFound, lookup)
    }

    @Test
    fun `a real case is found`() {
        assertTrue(CasePageParser.parse(html) is CaseLookup.Found)
    }
}

/** Parses a case page that must be found. */
fun parseFound(html: String): CasePage =
    when (val lookup = CasePageParser.parse(html)) {
        is CaseLookup.Found -> lookup.page
        CaseLookup.NotFound -> error("Expected a case, got NotFound")
    }
