package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Expected values from fixtures/README.md, `expedienteytipo_form_*.html` and the Accion=2 reload. */
class SearchFormFixturesTest {
    private fun form(organismo: String) = SearchFormParser.parse(Fixtures.load("expedienteytipo_form_$organismo.html"))

    private val defaultProcedimientos = listOf(
        "276" to "Apelación",
        "979" to "Conflicto competencial entre jueces",
        "1214" to "Denegada apelación",
        "1715" to "Impedimento",
        "1719" to "Impedimento (excusa)",
        "1720" to "Impedimento (recusación)",
        "2670" to "Otro",
        "3042" to "Queja",
        "4258" to "Sumario",
    )

    @ParameterizedTest
    @ValueSource(strings = ["4157", "10", "41", "534", "726", "727", "728", "4", "500", "4343", "3986", "930", "4386", "6315"])
    fun `every fresh form`(organismo: String) {
        val html = Fixtures.load("expedienteytipo_form_$organismo.html")
        val form = SearchFormParser.parse(html)

        assertEquals("PRIMER CIRCUITO", form.circuitoName)
        assertTrue(form.tipoAsuntoOptions.none { it.selected })
        assertFalse(form.isTipoProcedimientoShown)
        assertEquals(defaultProcedimientos, form.tipoProcedimientoOptions.map { it.value to it.label })
        assertEquals("", form.expediente)
        assertEquals(15, form.expedienteMaxLength)
        assertNull(CaseUrl.fromSearchResult(html))
    }

    @Test
    fun `organo names, tipo counts, first and last`() {
        data class Expected(val organo: String, val count: Int, val first: Pair<String, String>?, val last: Pair<String, String>?)
        val expected = mapOf(
            "4157" to Expected(
                "Juzgado Primero de Distrito en Materia de Concursos Mercantiles, con residencia en la Ciudad de México y jurisdicción en toda la República Mexicana",
                10, "1" to "Amparo Indirecto", "4" to "Procesos Civiles o Administrativos",
            ),
            "4" to Expected(
                "Primer Tribunal Colegiado en Materia Administrativa del Primer Circuito",
                14, "29" to "Amparo contra leyes", "16" to "Revisión Fiscal",
            ),
            "4343" to Expected(
                "Primer Tribunal Colegiado de Apelación en Materia Penal del Primer Circuito",
                6,
                "128" to "Amp Ind, Proc Fed Penales en 2a Instancia y Proc Fed Adm y Civ en 2a Instancia.",
                "127" to "Reconocimiento de Inocencia y Anulación de Sentencia.",
            ),
            "3986" to Expected(
                "Tribunal Laboral Federal de asuntos colectivos, con sede en la Ciudad de México",
                8, "115" to "Conflictos Colectivos de Naturaleza Económica", "120" to "Procedimientos Paraprocesales o Voluntarios",
            ),
            "930" to Expected(
                "Comisión de Conflictos Laborales del Poder Judicial de la Federación",
                3, "136" to "Designación Beneficiarios", "134" to "Procedimiento Ordinario.",
            ),
            "4386" to Expected(
                "Pleno Regional en Materias Administrativa y Civil de la Región Centro-Norte, con residencia en la Ciudad de México.",
                3, "131" to "Conflictos Competenciales.", "130" to "Solicitud de Declaratoria General de Inconstitucionalidad",
            ),
            "6315" to Expected("Secretaría General de Acuerdos", 0, null, null),
        )
        expected.forEach { (organismo, e) ->
            val form = form(organismo)
            val tipos = form.tipoAsuntoOptions.map { it.value to it.label }
            assertEquals(e.organo, form.organoName, organismo)
            assertEquals(e.count, tipos.size, organismo)
            assertEquals(e.first, tipos.firstOrNull(), organismo)
            assertEquals(e.last, tipos.lastOrNull(), organismo)
        }
    }

    @Test
    fun `spot checks`() {
        assertTrue("11" to "Amparo en revisión" in form("4").tipoAsuntoOptions.map { it.value to it.label })
        assertTrue(
            "126" to "Procedimientos federales administrativos y civiles en segunda instancia." in
                form("4343").tipoAsuntoOptions.map { it.value to it.label },
        )
        // Same 10 tipos as 767 in expedienteytipo_result_1183-2025.html.
        assertEquals(
            SearchFormParser.parse(Fixtures.load("expedienteytipo_result_1183-2025.html")).tipoAsuntoOptions.map { it.value to it.label },
            form("4157").tipoAsuntoOptions.map { it.value to it.label },
        )
        assertTrue("125" in form("4343").tipoAsuntoOptions.map { it.value })
    }

    @Test
    fun `the Accion 2 reload for tipo 125 shows its real procedimientos`() {
        val html = Fixtures.load("expedienteytipo_accion2_4343_tipo125.html")
        val form = SearchFormParser.parse(html)

        assertEquals("Primer Tribunal Colegiado de Apelación en Materia Penal del Primer Circuito", form.organoName)
        assertEquals("PRIMER CIRCUITO", form.circuitoName)
        assertEquals(form("4343").tipoAsuntoOptions.map { it.value to it.label }, form.tipoAsuntoOptions.map { it.value to it.label })
        assertEquals("125", form.selectedTipoAsunto?.value)
        assertTrue(form.isTipoProcedimientoShown)
        assertEquals(
            listOf(
                "22800" to "Apelación",
                "22801" to "Denegada apelación",
                "22802" to "Impedimento (excusa)",
                "22803" to "Impedimento (recusación)",
                "22804" to "Queja",
                "22805" to "Conflicto competencial entre jueces",
                "22806" to "Conflicto de acumulación entre jueces",
                "22807" to "Sumario",
                "22808" to "Conflicto de acumulación",
                "22809" to "Impedimento",
                "22810" to "Otro",
            ),
            form.tipoProcedimientoOptions.map { it.value to it.label },
        )
        assertTrue(form.tipoProcedimientoOptions.none { it.selected })
        val defaults = defaultProcedimientos.map { it.first }.toSet()
        assertTrue(form.tipoProcedimientoOptions.none { it.value in defaults })
        assertEquals("", form.expediente)
        assertEquals(15, form.expedienteMaxLength)
        assertNull(CaseUrl.fromSearchResult(html))
    }

    @Test
    fun `a form the app loaded keeps its hidden fields, OrgName included`() {
        val form = form("500")
        val name = "Octavo Tribunal Colegiado en Materia Penal del Primer Circuito"

        assertEquals(name, form.organoName)
        assertEquals(
            listOf(
                "Circuito" to "1",
                "CircuitoName" to "PRIMER CIRCUITO",
                "Organismo" to "500",
                "OrgName" to name,
                "TipoOrganismo" to "0",
                "Accion" to "0",
            ),
            form.hiddenFields.toList(),
        )
        // Another colegiado de circuito, another materia: the same 14 tipos as órgano 4.
        assertEquals(
            form("4").tipoAsuntoOptions.map { it.value to it.label },
            form.tipoAsuntoOptions.map { it.value to it.label },
        )
    }

    @Test
    fun `a juzgado in another circuit echoes that circuit's portal Cir`() {
        val form = form("721")

        assertEquals("VIGÉSIMO PRIMER CIRCUITO", form.circuitoName)
        assertEquals("Juzgado Séptimo de Distrito en el Estado de Guerrero", form.organoName)
        assertEquals("51", form.hiddenFields["Circuito"])
        assertEquals("721", form.hiddenFields["Organismo"])
        // A mixed-materia juzgado in Guerrero: the same 10 tipos as the specialized ones in Mexico City.
        assertEquals(
            form("10").tipoAsuntoOptions.map { it.value to it.label },
            form.tipoAsuntoOptions.map { it.value to it.label },
        )
    }
}
