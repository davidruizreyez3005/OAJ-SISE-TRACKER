package mx.sisetracker.core

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Expected values from fixtures/README.md, the three `veracuerdo_*.html` files. */
class SintesisPageParserTest {

    @Test
    fun `1183-2025 orden 38`() {
        val sintesis = SintesisPageParser.parse(Fixtures.load("veracuerdo_1183-2025_orden38.html"))

        assertEquals("1183/2025", sintesis.expediente)
        assertEquals(LocalDate.of(2026, 8, 31), sintesis.fechaAuto)
        assertEquals(LocalDate.of(2026, 9, 1), sintesis.fechaPublicacion)
        val lines = sintesis.text.lines()
        assertEquals(8, lines.size)
        assertEquals("Ciudad de México, treinta y uno de agosto de dos mil veintiséis.", lines[0])
        assertEquals("Tribunal colegiado acusa recibo", lines[1])
        assertTrue(lines[2].startsWith("Intégrese el oficio proveniente del Segundo Tribunal Colegiado"))
        assertEquals("Admite revisión", lines[3])
        assertTrue(lines[4].contains("293********************."))
        assertTrue(lines[5].startsWith("Asimismo comunica que desecho"))
        assertEquals("De lo que se toma conocimiento para los afectos legales conducentes.", lines[6])
        assertEquals("Notifíquese.", lines[7])
    }

    @Test
    fun `the truncated resume of orden 38 is a prefix of its sintesis`() {
        val page = parseFound(Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html"))
        val sintesis = SintesisPageParser.parse(Fixtures.load("veracuerdo_1183-2025_orden38.html"))
        val resumen = page.acuerdos.single { it.orden == 38 }.resumen

        assertTrue(resumen.endsWith(" ..."))
        assertTrue(sintesis.text.startsWith(resumen.removeSuffix(" ...")))
    }

    @Test
    fun `293-2026 orden 3 keeps its lowercase start and blank line`() {
        val sintesis = SintesisPageParser.parse(Fixtures.load("veracuerdo_293-2026_orden3.html"))

        assertEquals("293/2026", sintesis.expediente)
        assertEquals(LocalDate.of(2026, 9, 17), sintesis.fechaAuto)
        assertEquals(LocalDate.of(2026, 9, 18), sintesis.fechaPublicacion)
        val lines = sintesis.text.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("téngase por recibida la opinión ministerial 340/2026"))
        assertEquals("", lines[1])
        assertTrue(lines[2].startsWith("En atención a su contenido, téngase por hechas las manifestaciones"))
        assertTrue(lines[2].endsWith("sentencia correspondiente."))
    }

    @Test
    fun `293-2026 orden 3 URL from its grid row matches the portal's`() {
        val link = DoVerAcuerdo.parse(
            "javascript:DoVerAcuerdo(18,3,42423129,1,\"17/09/2026 12:00:00 a.m.\",\"18/09/2026 12:00:00 a.m.\",\"293/2026\")",
        )

        val url = VerAcuerdoUrl.build(link)

        assertEquals(
            "https://www.dgej.cjf.gob.mx/siseinternet/Actuaria/VerAcuerdo.aspx?listaAcOrd=3&listaCatOrg=18&listaNeun=42423129&listaAsuId=1&listaExped=293/2026&listaFAuto=17/09/2026&listaFPublicacion=18/09/2026",
            url,
        )
        assertEquals(Deltas.verAcuerdoUrl("delta_ver-acuerdo_293-2026_orden3.txt"), url)
    }

    @Test
    fun `1068-2025 orden 1 is a 21,507-character sintesis`() {
        val sintesis = SintesisPageParser.parse(Fixtures.load("veracuerdo_1068-2025_orden1.html"))

        assertEquals("1068/2025", sintesis.expediente)
        assertEquals(LocalDate.of(2025, 9, 2), sintesis.fechaAuto)
        assertEquals(LocalDate.of(2025, 9, 3), sintesis.fechaPublicacion)
        assertEquals(21_507, sintesis.text.length)
        val lines = sintesis.text.lines()
        assertEquals(57, lines.size)
        assertEquals("Chilpancingo de Los Bravo, Guerrero, dos de septiembre de dos mil veinticinco.", lines.first())
        assertTrue(lines.last().endsWith("en días hábiles."))
        listOf(
            "CUMPLIMIENTO AL ACUERDO GENERAL 12/2020.",
            "SUSPENSIÓN DE PLANO",
            "EXHORTOS.",
            "HABILITACIÓN.",
            "DOMICILIO Y AUTORIZADOS.",
            "AUTORIZACIÓN DE COPIAS",
        ).forEach { heading -> assertTrue(heading in lines, "Missing line: $heading") }
    }

    @Test
    fun `rejects a page without the sintesis span`() {
        assertThrows<SiseParseException> { SintesisPageParser.parse("<html><body></body></html>") }
    }
}
