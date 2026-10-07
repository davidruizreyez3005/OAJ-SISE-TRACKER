package mx.sisetracker.core

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The Vigésimo Primer Circuito (Guerrero), `circuitos_cir21.html`: captured
 * on a device from `circuitos.asp?Cir=51&Exp=1`, the portal number of OAJ
 * circuit 21. A circuit with mixed-materia juzgados and paired materias.
 */
class OrganoListCir21Test {
    private val html = Fixtures.load("circuitos_cir21.html")
    private val list = OrganoListParser.parse(html)
    private fun organo(id: String) = list.organos.single { it.id == id }

    @Test
    fun `circuit name and 24 organos in page order`() {
        assertEquals("VIGÉSIMO PRIMER CIRCUITO", list.circuitoName)
        assertEquals(24, list.organos.size)
        assertEquals("375", list.organos.first().id)
        assertEquals("4392", list.organos.last().id)
        assertEquals((0 until 24).toList(), list.organos.map { it.position })
    }

    @Test
    fun `the page's hidden inputs hold the portal Cir and the CircuitoName`() {
        val hidden = org.jsoup.Jsoup.parse(html).select("input[type=hidden]").associate { it.attr("name") to it.attr("value") }
        assertEquals(mapOf("Circuito" to "51", "CircuitoName" to "VIGÉSIMO PRIMER CIRCUITO"), hidden)
        assertEquals("51", Circuitos.portalCir("21"))
    }

    @Test
    fun `classes`() {
        assertEquals(
            mapOf(
                OrganoClase.JUZGADO_DISTRITO to 11,
                OrganoClase.COLEGIADO_CIRCUITO to 5,
                OrganoClase.TRIBUNAL_LABORAL to 2,
                OrganoClase.COLEGIADO_APELACION to 1,
                OrganoClase.PLENO_REGIONAL to 5,
            ),
            list.organos.groupingBy { it.clase }.eachCount(),
        )
        // No materia in the name: "Tribunal Colegiado de Apelación del Vigésimo Primer Circuito…".
        assertEquals(OrganoClase.COLEGIADO_APELACION, organo("4366").clase)
        assertEquals(setOf(Materia.MIXTA), organo("4366").materias)
        assertEquals(OrganoClase.TRIBUNAL_LABORAL, organo("5601").clase)
    }

    @Test
    fun `materias`() {
        // "Juzgado … de Distrito en el Estado de Guerrero" hears every materia.
        assertEquals(10, list.organos.count { it.clase == OrganoClase.JUZGADO_DISTRITO && it.materias == setOf(Materia.MIXTA) })
        assertEquals(setOf(Materia.MERCANTIL), organo("3823").materias)
        assertEquals(setOf(Materia.CIVIL, Materia.TRABAJO), organo("749").materias)
        assertEquals(setOf(Materia.PENAL, Materia.ADMINISTRATIVA), organo("751").materias)
        assertEquals(setOf(Materia.ADMINISTRATIVA), organo("4390").materias)
    }

    @Test
    fun `closed plenos have periods without spaces around the dash`() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals(listOf("4390", "4391", "4389"), list.organos.filter { it.isClosed(today) }.map { it.id })
        assertEquals(LocalDate.of(2024, 1, 15), organo("4389").closedOn)
    }
}
