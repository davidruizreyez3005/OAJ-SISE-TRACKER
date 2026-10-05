package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcuerdoDiffAndFormatTest {
    private val page = parseFound(Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html"))

    @Test
    fun `new acuerdos are a set difference on orden`() {
        val stored = page.acuerdos.map { it.orden }.toSet() - setOf(37, 38)

        assertEquals(listOf(37, 38), AcuerdoDiff.newAcuerdos(stored, page.acuerdos).map { it.orden })
    }

    @Test
    fun `a late acuerdo filling a gap counts as new even below the highest orden`() {
        // Orden 3 never appeared on this page; pretend it was published late.
        val late = page.acuerdos.first().let { it.copy(link = it.link.copy(orden = 3)) }
        val stored = page.acuerdos.map { it.orden }.toSet()

        assertEquals(listOf(3), AcuerdoDiff.newAcuerdos(stored, page.acuerdos + late).map { it.orden })
    }

    @Test
    fun `nothing is new when every orden is stored`() {
        assertTrue(AcuerdoDiff.newAcuerdos(page.acuerdos.map { it.orden }.toSet(), page.acuerdos).isEmpty())
    }

    @Test
    fun `headings in the 21,507-character sintesis`() {
        val text = SintesisPageParser.parse(Fixtures.load("veracuerdo_1068-2025_orden1.html")).text

        assertEquals(
            listOf(
                "CUMPLIMIENTO AL ACUERDO GENERAL 12/2020.",
                "SUSPENSIÓN DE PLANO",
                "EXHORTOS.",
                "HABILITACIÓN.",
                "DOMICILIO Y AUTORIZADOS.",
                "AUTORIZACIÓN DE COPIAS",
            ),
            text.lines().filter(SintesisFormat::isHeading),
        )
    }

    @Test
    fun `ordinary lines are not headings`() {
        assertFalse(SintesisFormat.isHeading("Tribunal colegiado acusa recibo"))
        assertFalse(SintesisFormat.isHeading("[.]"))
        assertFalse(SintesisFormat.isHeading(""))
        assertTrue(SintesisFormat.isHeading("  EXHORTOS. "))
    }
}
