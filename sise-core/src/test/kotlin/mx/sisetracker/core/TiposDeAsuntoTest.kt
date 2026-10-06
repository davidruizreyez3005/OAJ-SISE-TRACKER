package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TiposDeAsuntoTest {
    /** Form fixtures and the kind of their órgano. */
    private val forms = mapOf(
        "expedienteytipo_form_4157.html" to OrganoKind.JUZGADOS,
        "expedienteytipo_result_1183-2025.html" to OrganoKind.JUZGADOS,
        "expedienteytipo_form_4.html" to OrganoKind.TRIBUNALES,
        "expedienteytipo_form_4343.html" to OrganoKind.TRIBUNALES,
        "expedienteytipo_form_3986.html" to OrganoKind.TRIBUNALES,
        "expedienteytipo_form_930.html" to OrganoKind.OTROS,
        "expedienteytipo_form_4386.html" to OrganoKind.OTROS,
        "expedienteytipo_form_6315.html" to OrganoKind.OTROS,
    )

    @Test
    fun `the bundled list is exactly the tipos in the form fixtures, with their kinds`() {
        val fromFixtures = mutableMapOf<String, Pair<String, MutableSet<OrganoKind>>>()
        forms.forEach { (file, kind) ->
            SearchFormParser.parse(Fixtures.load(file)).tipoAsuntoOptions.forEach { option ->
                val entry = fromFixtures.getOrPut(option.value) { option.label to mutableSetOf() }
                // IDs are global: the same ID always has the same label.
                assertEquals(entry.first, option.label, option.value)
                entry.second += kind
            }
        }

        assertEquals(44, TiposDeAsunto.all.size)
        assertEquals(
            fromFixtures.mapValues { (_, v) -> v.first to v.second.toSet() },
            TiposDeAsunto.all.associate { it.id to (it.label to it.kinds) },
        )
    }

    @Test
    fun `known IDs`() {
        assertEquals("Amparo Directo", TiposDeAsunto.byId("10")?.label)
        assertEquals("Amparo en revisión", TiposDeAsunto.byId("11")?.label)
        assertEquals("Amparo Indirecto", TiposDeAsunto.byId("1")?.label)
    }

    @Test
    fun `an organo's kind puts its tipos first`() {
        val tribunal = TiposDeAsunto.forKind(OrganoKind.TRIBUNALES)
        val tribunalIds = TiposDeAsunto.all.filter { OrganoKind.TRIBUNALES in it.kinds }.map { it.id }

        assertEquals(44, tribunal.size)
        assertEquals(tribunalIds.toSet(), tribunal.take(tribunalIds.size).map { it.value }.toSet())
        assertTrue("10" in tribunal.take(tribunalIds.size).map { it.value })
        assertEquals((0 until 44).toList(), tribunal.map { it.position })
        assertTrue(tribunal.none { it.selected })
        assertEquals(TiposDeAsunto.all.map { it.id }, TiposDeAsunto.forKind(null).map { it.value })
    }
}
