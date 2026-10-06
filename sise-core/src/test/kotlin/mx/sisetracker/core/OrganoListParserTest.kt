package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Expected values from fixtures/README.md, `circuitos_cir1.html`. */
class OrganoListParserTest {
    private val list = OrganoListParser.parse(Fixtures.load("circuitos_cir1.html"))
    private fun name(id: String) = list.organos.single { it.id == id }.name

    @Test
    fun `184 organos in page order with no duplicates`() {
        assertEquals(184, list.organos.size)
        assertEquals((0 until 184).toList(), list.organos.map { it.position })
        assertEquals(184, list.organos.map { it.id }.toSet().size)
    }

    @Test
    fun `first and last`() {
        assertEquals("10", list.organos.first().id)
        assertEquals("Juzgado Primero de Distrito en Materia Administrativa en la Ciudad de México", list.organos.first().name)
        assertEquals("4393", list.organos.last().id)
        assertEquals(
            "Pleno Regional Especializado en Competencia Económica, Radiodifusión y Telecomunicaciones",
            list.organos.last().name,
        )
    }

    @Test
    fun `spot checks`() {
        assertEquals("Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México", name("767"))
        assertEquals("Segundo Tribunal Colegiado en Materia Penal del Primer Circuito", name("18"))
        assertEquals("Octavo Tribunal Colegiado en Materia Penal del Primer Circuito", name("500"))
        assertEquals("Décimo Tribunal Colegiado en Materia Penal del Primer Circuito.", name("1671"))
        assertEquals("Comisión de Disciplina", name("6316"))
        assertEquals(OrganoKind.OTROS, list.organos.single { it.id == "6316" }.kind)
    }

    @Test
    fun `kinds derived from names`() {
        val counts = list.organos.groupingBy { it.kind }.eachCount()
        assertEquals(mapOf(OrganoKind.JUZGADOS to 79, OrganoKind.TRIBUNALES to 93, OrganoKind.OTROS to 12), counts)
    }

    @Test
    fun `circuito name from the Circuito row, without the no-break space`() {
        assertEquals("PRIMER CIRCUITO", list.circuitoName)
    }

    @Test
    fun `a page without the Circuito row has no circuito name`() {
        val html = """<form name="Editar"><select name="Organismo"><option value="767">Juzgado</select></form>"""

        val parsed = OrganoListParser.parse(html)

        assertNull(parsed.circuitoName)
        assertEquals(listOf(Organo("767", "Juzgado", 0)), parsed.organos)
    }

    @Test
    fun `a page without the Organismo select is an error`() {
        assertThrows<SiseParseException> { OrganoListParser.parse("<html><body>Error</body></html>") }
    }
}
