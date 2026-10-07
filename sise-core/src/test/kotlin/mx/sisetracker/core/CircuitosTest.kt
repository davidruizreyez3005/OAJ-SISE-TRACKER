package mx.sisetracker.core

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Expected values from fixtures/README.md, `oaj_circuitos_excerpt.html` and `oaj_datos_expedientes.json`. */
class CircuitosTest {
    /** Test-only: the app never fetches the OAJ page, it bundles the list. */
    private fun parseOajCircuitList(html: String): List<Pair<String, String>> =
        checkNotNull(Jsoup.parse(html).selectFirst("select#circuito"))
            .dropdownOptions()
            .map { it.value to it.label }

    /** Test-only: OAJ circuit number → the `Cir` in its circuitos.asp link, from the OAJ map data. */
    private fun parseOajLinks(json: String): Map<String, String> =
        Regex(""""value":\s*(\d+),\s*"href":\s*"[^"]*circuitos\.asp\?Cir=(\d+)&Exp=1"""")
            .findAll(json)
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val fromFixture = parseOajCircuitList(Fixtures.load("oaj_circuitos_excerpt.html"))

    @Test
    fun `the fixture lists 32 circuits after the placeholder`() {
        assertEquals(32, fromFixture.size)
        assertEquals((1..32).map(Int::toString), fromFixture.map { it.first })
    }

    @Test
    fun `the bundled list equals the fixture exactly`() {
        assertEquals(fromFixture, Circuitos.all.map { it.num to it.label })
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

    @Test
    fun `portal Cir numbers come from the OAJ map data`() {
        val links = parseOajLinks(Fixtures.load("oaj_datos_expedientes.json"))

        assertEquals(32, links.size)
        assertEquals(links, Circuitos.all.associate { it.num to it.portalCir })
        // Only some circuits keep their own number on the portal.
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "20", "30"),
            links.filter { it.key == it.value }.keys.toList(),
        )
        assertEquals("45", Circuitos.portalCir("16"))
        assertEquals("109", Circuitos.portalCir("32"))
        assertEquals("99", Circuitos.portalCir("99"))
    }

    @Test
    fun `urls and forms use the portal number`() {
        assertEquals("https://www.dgej.cjf.gob.mx/internet/expedientes/circuitos.asp?Cir=45&Exp=1", SiseUrls.circuitos("16"))
        assertEquals("https://www.dgej.cjf.gob.mx/internet/expedientes/circuitos.asp?Cir=1&Exp=1", SiseUrls.circuitos("1"))
        val form = SearchRequests.loadForm("16", "DÉCIMO SEXTO CIRCUITO", "313")
        assertEquals("45", form.fields.toMap()["Circuito"])
    }

    @Test
    fun `labels split into ordinal and region for display`() {
        Circuitos.all.forEach {
            assertEquals(it.label, "${it.ordinal} ${it.region}")
        }
        assertEquals("Primer Circuito", Circuitos.byNum("1")?.ordinal)
        assertEquals("Ciudad de México", Circuitos.byNum("1")?.region)
        assertEquals("Vigésimo Primer Circuito", Circuitos.byNum("21")?.ordinal)
        assertEquals("Guerrero", Circuitos.byNum("21")?.region)
    }
}
