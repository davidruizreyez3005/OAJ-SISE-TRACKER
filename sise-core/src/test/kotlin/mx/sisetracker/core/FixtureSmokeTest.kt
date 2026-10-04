package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FixtureSmokeTest {
    @Test
    fun `case page fixture loads from test resources`() {
        val html = Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html")

        assertTrue(html.contains("40612904"))
    }
}
