package mx.sisetracker.core

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Expected values from fixtures/README.md, `oaj_circuitos_excerpt.html`. */
class CircuitosTest {
    /** Test-only: the app never fetches the OAJ page, it bundles the list. */
    private fun parseOajCircuitList(html: String): List<Circuito> =
        Jsoup.parse(html).selectFirst("select#circuito")!!
            .dropdownOptions()
            .map { Circuito(it.value, it.label) }

    private val fromFixture = parseOajCircuitList(Fixtures.load("oaj_circuitos_excerpt.html"))

    @Test
    fun `the fixture lists 32 circuits after the placeholder`() {
        assertEquals(32, fromFixture.size)
        assertEquals((1..32).map(Int::toString), fromFixture.map { it.num })
    }

    @Test
    fun `the bundled list equals the fixture exactly`() {
        assertEquals(fromFixture, Circuitos.all)
    }

    @Test
    fun `labels are kept exactly, typos included`() {
        assertEquals("Primer Circuito Ciudad de México", Circuitos.byNum("1")?.label)
        assertEquals("Decimosexto Circuito Guanuajuato", Circuitos.byNum("16")?.label)
        assertEquals("Vigésimo Primer Circuito Guerrero", Circuitos.byNum("21")?.label)
        assertEquals("Trigésimo Segundo Circuito Colima", Circuitos.byNum("32")?.label)
        assertNull(Circuitos.byNum("33"))
        assertNull(Circuitos.byNum("-1"))
    }
}
