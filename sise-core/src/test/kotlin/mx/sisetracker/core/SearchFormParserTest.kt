package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Expected values from fixtures/README.md, `expedienteytipo_result_1183-2025.html` (search form `Editar`). */
class SearchFormParserTest {
    private val html = Fixtures.load("expedienteytipo_result_1183-2025.html")
    private val form = SearchFormParser.parse(html)

    @Test
    fun `tipos de asunto in page order`() {
        assertEquals(
            listOf(
                "1" to "Amparo Indirecto",
                "2" to "Causa Penal",
                "46" to "Concursos Mercantiles",
                "71" to "Denuncia por Incumplimiento de la Declaratoria General de Inconstitucionalidad",
                "67" to "Ejecución de Penas",
                "58" to "Extinción de Dominio",
                "68" to "Juicio Oral Mercantil",
                "18" to "Medidas Precautorias",
                "19" to "Procedimientos de Extradición",
                "4" to "Procesos Civiles o Administrativos",
            ),
            form.tipoAsuntoOptions.map { it.value to it.label },
        )
        assertEquals((0..9).toList(), form.tipoAsuntoOptions.map { it.position })
        assertEquals("1", form.selectedTipoAsunto?.value)
        assertEquals(1, form.tipoAsuntoOptions.count { it.selected })
    }

    @Test
    fun `tipos de procedimiento in page order`() {
        assertEquals(
            listOf(
                "276" to "Apelación",
                "979" to "Conflicto competencial entre jueces",
                "1214" to "Denegada apelación",
                "1715" to "Impedimento",
                "1719" to "Impedimento (excusa)",
                "1720" to "Impedimento (recusación)",
                "2670" to "Otro",
                "3042" to "Queja",
                "4258" to "Sumario",
            ),
            form.tipoProcedimientoOptions.map { it.value to it.label },
        )
    }

    @Test
    fun `the procedimiento row is hidden for tipo 1`() {
        assertFalse(form.isTipoProcedimientoShown)
    }

    @Test
    fun `the procedimiento row shows for a tipo in 6, 9, 125 or 126`() {
        // Same markup with a selected tipo 125 added before the others.
        val with125 = html
            .replace("<option value=\"1\" selected>", "<option value=\"1\">")
            .replace("<option value=\"1\">", "<option value=\"125\" selected>Tipo 125</option><option value=\"1\">")

        assertTrue(SearchFormParser.parse(with125).isTipoProcedimientoShown)
    }

    @Test
    fun `skips placeholder options only`() {
        val withPlaceholders = html.replace(
            "<option value=\"1\" selected>",
            "<option value=\"0\">Seleccione</option><option value=\"\">--</option><option value=\"1\" selected>",
        )

        assertEquals(form.tipoAsuntoOptions, SearchFormParser.parse(withPlaceholders).tipoAsuntoOptions)
    }

    @Test
    fun `expediente field`() {
        assertEquals("1183/2025", form.expediente)
        assertEquals(15, form.expedienteMaxLength)
    }

    @Test
    fun `hidden fields in page order`() {
        assertEquals(
            listOf(
                "Circuito" to "1",
                "CircuitoName" to "PRIMER CIRCUITO",
                "Organismo" to "767",
                "OrgName" to "",
                "TipoOrganismo" to "",
                "Accion" to "0",
            ),
            form.hiddenFields.toList(),
        )
    }

    @Test
    fun `rejects a page without the search form`() {
        assertThrows<SiseParseException> { SearchFormParser.parse("<html><body></body></html>") }
    }

    @Test
    fun `tipo de procedimiento rule`() {
        listOf("6", "9", "125", "126", " 9 ").forEach { assertTrue(TipoProcedimientoRule.isShown(it), it) }
        listOf("1", "2", "0", "", "x").forEach { assertFalse(TipoProcedimientoRule.isShown(it), it) }
    }
}
