package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TiposDeAsuntoTest {
    /** Form fixtures and the class of their órgano. */
    private val forms = mapOf(
        "expedienteytipo_form_4157.html" to OrganoClase.JUZGADO_DISTRITO,
        "expedienteytipo_result_1183-2025.html" to OrganoClase.JUZGADO_DISTRITO,
        "expedienteytipo_form_4.html" to OrganoClase.COLEGIADO_CIRCUITO,
        "expedienteytipo_form_4343.html" to OrganoClase.COLEGIADO_APELACION,
        "expedienteytipo_form_3986.html" to OrganoClase.TRIBUNAL_LABORAL,
        "expedienteytipo_form_930.html" to OrganoClase.CONFLICTOS_LABORALES,
        "expedienteytipo_form_4386.html" to OrganoClase.PLENO_REGIONAL,
        "expedienteytipo_form_6315.html" to OrganoClase.OTRO,
    )

    private val organos = OrganoListParser.parse(Fixtures.load("circuitos_cir1.html")).organos

    @Test
    fun `fixture classes match the organo names`() {
        forms.forEach { (file, clase) ->
            val id = Regex("""_(\d+)\.html$""").find(file)?.groupValues?.get(1) ?: "767"
            assertEquals(clase, organos.single { it.id == id }.clase, file)
        }
    }

    @Test
    fun `the bundled list is exactly the tipos in the form fixtures, by class in portal order`() {
        forms.forEach { (file, clase) ->
            val fromFixture = SearchFormParser.parse(Fixtures.load(file)).tipoAsuntoOptions.map { it.value to it.label }
            val bundled = TiposDeAsunto.all.filter { it.clase == clase }.map { it.id to it.label }
            assertEquals(fromFixture, bundled, file)
        }
        assertEquals(44, TiposDeAsunto.all.size)
        // IDs are global and each tipo was seen at one class only.
        assertEquals(44, TiposDeAsunto.all.map { it.id }.toSet().size)
    }

    @Test
    fun `known IDs`() {
        assertEquals("Amparo Directo", TiposDeAsunto.byId("10")?.label)
        assertEquals("Amparo en revisión", TiposDeAsunto.byId("11")?.label)
        assertEquals("Amparo Indirecto", TiposDeAsunto.byId("1")?.label)
        assertEquals(OrganoClase.COLEGIADO_CIRCUITO, TiposDeAsunto.byId("10")?.clase)
    }

    @Test
    fun `an organo's class shows only its own tipos`() {
        val colegiado = TiposDeAsunto.forClase(OrganoClase.COLEGIADO_CIRCUITO)
        assertEquals(14, colegiado.size)
        assertEquals(listOf("29", "10", "11"), colegiado.take(3).map { it.value })
        assertEquals((0 until 14).toList(), colegiado.map { it.position })
        assertTrue(colegiado.none { it.selected })

        assertEquals(10, TiposDeAsunto.forClase(OrganoClase.JUZGADO_DISTRITO).size)
        assertEquals(6, TiposDeAsunto.forClase(OrganoClase.COLEGIADO_APELACION).size)
        assertEquals(8, TiposDeAsunto.forClase(OrganoClase.TRIBUNAL_LABORAL).size)
        assertEquals(3, TiposDeAsunto.forClase(OrganoClase.PLENO_REGIONAL).size)
        // Unknown lists fall back to every known tipo.
        assertEquals(TiposDeAsunto.all.map { it.id }, TiposDeAsunto.forClase(OrganoClase.OTRO).map { it.value })
        assertEquals(TiposDeAsunto.all.map { it.id }, TiposDeAsunto.forClase(null).map { it.value })
    }
}
