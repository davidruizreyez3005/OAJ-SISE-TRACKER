package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DoVerAcuerdoTest {
    @Test
    fun `parses the grid link arguments`() {
        val link = DoVerAcuerdo.parse(
            "javascript:DoVerAcuerdo(767,38,40612904,1,\"31/08/2026 12:00:00 a.m.\",\"01/09/2026 12:00:00 a.m.\",\"1183/2025\")",
        )

        assertEquals(
            DoVerAcuerdo(
                organismoId = "767",
                orden = 38,
                neun = "40612904",
                asuntoId = "1",
                fechaAuto = "31/08/2026",
                fechaPublicacion = "01/09/2026",
                expediente = "1183/2025",
            ),
            link,
        )
    }

    @Test
    fun `tolerates spacing, single quotes and a trailing semicolon`() {
        val link = DoVerAcuerdo.parse(
            "javascript: DoVerAcuerdo( 18, 3, 42423129, 1, '17/09/2026 12:00:00 a.m.', '18/09/2026 12:00:00 a.m.', '293/2026' );",
        )

        assertEquals(DoVerAcuerdo("18", 3, "42423129", "1", "17/09/2026", "18/09/2026", "293/2026"), link)
    }

    @Test
    fun `rejects malformed links`() {
        assertThrows<SiseParseException> { DoVerAcuerdo.parse("javascript:__doPostBack('x','')") }
        assertThrows<SiseParseException> { DoVerAcuerdo.parse("javascript:DoVerAcuerdo(767,38,40612904)") }
        assertThrows<SiseParseException> {
            DoVerAcuerdo.parse("javascript:DoVerAcuerdo(767,x,40612904,1,\"31/08/2026\",\"01/09/2026\",\"1183/2025\")")
        }
        assertThrows<SiseParseException> {
            DoVerAcuerdo.parse("javascript:DoVerAcuerdo(767,38,40612904,1,\"31-08-2026\",\"01/09/2026\",\"1183/2025\")")
        }
    }
}
