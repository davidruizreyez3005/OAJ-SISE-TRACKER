package mx.sisetracker.core

import java.time.LocalDate
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 1183/2025 (fixtures README): audiencia constitucional set for 08/01/2026,
 * postponed four times to 23/03/2026 at 10:23 and held then; audiencia
 * incidental set for 05/12/2025 at 11:03, with no celebración recorded.
 */
class AudienciasTest {
    private val captura = parseFound(Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html")).captura.entries
    private val incidental = Audiencia(TipoAudiencia.INCIDENTAL, LocalDate.of(2025, 12, 5), LocalTime.of(11, 3))

    @Test
    fun `the last postponement is the current date, and a celebracion on that day means held`() {
        assertEquals(listOf(incidental), Audiencias.scheduled(captura))
    }

    @Test
    fun `next is the soonest hearing on or after today`() {
        assertEquals(incidental, Audiencias.next(captura, LocalDate.of(2025, 12, 1)))
        assertEquals(incidental, Audiencias.next(captura, LocalDate.of(2025, 12, 5)))
        assertNull(Audiencias.next(captura, LocalDate.of(2025, 12, 6)))
    }

    @Test
    fun `a postponed hearing that wasn't held yet stays scheduled, with its record's time`() {
        val beforeHearing = captura.filterNot { "celebración" in it.label }

        assertEquals(
            Audiencia(TipoAudiencia.CONSTITUCIONAL, LocalDate.of(2026, 3, 23), LocalTime.of(10, 23)),
            Audiencias.next(beforeHearing, LocalDate.of(2026, 3, 10)),
        )
    }

    @Test
    fun `labels match ignoring accents, case and spacing, and blank values are skipped`() {
        val entries = listOf(
            CapturaEntry("Audiencia", 0, "FECHA SENALADA PARA  LA AUDIENCIA  de juicio", "02/02/2027"),
            CapturaEntry("Audiencia", 0, "Fecha señalada para audiencia constitucional", ""),
            CapturaEntry("Audiencia", 1, "Fecha señalada para audiencia constitucional(diferimiento)", "10/11/2026"),
            CapturaEntry("Audiencia", 1, "Hora señalada para audiencia constitucional (diferimiento)", "9:30"),
            CapturaEntry("Audiencia", 2, "Hora señalada para audiencia de juicio", "25:99"),
        )

        assertEquals(
            listOf(
                Audiencia(TipoAudiencia.OTRA, LocalDate.of(2027, 2, 2)),
                Audiencia(TipoAudiencia.CONSTITUCIONAL, LocalDate.of(2026, 11, 10), LocalTime.of(9, 30)),
            ),
            Audiencias.scheduled(entries),
        )
    }

    @Test
    fun `a celebracion before the latest date doesn't cancel it`() {
        val entries = listOf(
            CapturaEntry("Audiencia", 0, "Fecha señalada para audiencia constitucional", "08/01/2026"),
            CapturaEntry("Audiencia", 0, "Fecha celebración audiencia constitucional", "08/01/2026"),
            CapturaEntry("Audiencia", 1, "Fecha señalada para audiencia constitucional(diferimiento)", "03/02/2026"),
        )

        assertEquals(listOf(Audiencia(TipoAudiencia.CONSTITUCIONAL, LocalDate.of(2026, 2, 3))), Audiencias.scheduled(entries))
    }

    @Test
    fun `no hearing labels, no hearings`() {
        assertEquals(emptyList<Audiencia>(), Audiencias.scheduled(listOf(CapturaEntry("Datos Generales", 0, "Mesa", "III"))))
    }
}
