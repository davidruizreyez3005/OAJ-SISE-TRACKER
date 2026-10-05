package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SiseTextTest {
    @Test
    fun `normalizeSpace collapses whitespace including no-break spaces`() {
        assertEquals("Fecha presentación", SiseText.normalizeSpace("  Fecha   presentación\r\n"))
    }

    @Test
    fun `normalizeMultiline keeps internal blank lines and trims the edges`() {
        val raw = "\r\n  \r\nPrimer párrafo. \r\n\r\nSegundo\rpárrafo.\r\n\r\n  "

        assertEquals("Primer párrafo. \n\nSegundo\npárrafo.", SiseText.normalizeMultiline(raw))
    }

    @Test
    fun `normalizeMultiline keeps a lowercase start and leading spaces of the first line`() {
        assertEquals("  téngase por recibida", SiseText.normalizeMultiline("\n  téngase por recibida\n"))
    }
}
