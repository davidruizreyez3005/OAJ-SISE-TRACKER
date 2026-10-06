package mx.sisetracker.core

import java.net.URLDecoder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SearchRequestsTest {
    private val form = SearchFormParser.parse(Fixtures.load("expedienteytipo_result_1183-2025.html"))

    @Test
    fun `step C posts the four circuitos form fields`() {
        val request = SearchRequests.loadForm(circuito = "1", circuitoName = "PRIMER CIRCUITO", organismo = "767")

        assertEquals(SiseUrls.SEARCH_FORM, request.url)
        assertEquals("Organismo=767&Buscar=Buscar&Circuito=1&CircuitoName=PRIMER+CIRCUITO", request.encodedBody)
    }

    @Test
    fun `step D echoes the hidden fields with Accion 2`() {
        val request = SearchRequests.loadProcedimientos(form, tipoAsunto = "9", expediente = "1183/2025")

        assertEquals(SiseUrls.SEARCH_FORM, request.url)
        assertEquals(
            "Circuito=1&CircuitoName=PRIMER+CIRCUITO&Organismo=767&OrgName=&TipoOrganismo=" +
                "&TipoAsunto=9&Expediente=1183%2F2025&Accion=2",
            request.encodedBody,
        )
    }

    @Test
    fun `step D is only for tipos that show the procedimiento row`() {
        assertThrows<IllegalArgumentException> { SearchRequests.loadProcedimientos(form, tipoAsunto = "1") }
    }

    @Test
    fun `accents are encoded as windows-1252`() {
        val orgName = "Juzgado Sexto de Distrito en Materia Penal en la Ciudad de México"
        val withOrgName = form.copy(hiddenFields = form.hiddenFields + ("OrgName" to orgName))

        val request = SearchRequests.loadProcedimientos(withOrgName, tipoAsunto = "6")

        assertEquals(
            "Juzgado+Sexto+de+Distrito+en+Materia+Penal+en+la+Ciudad+de+M%E9xico",
            encodedValue(request, "OrgName"),
        )
        // Plain ASCII on the wire, and it decodes back as windows-1252.
        assertArrayEquals(request.encodedBody.toByteArray(Charsets.US_ASCII), request.body)
        assertEquals(orgName, URLDecoder.decode(encodedValue(request, "OrgName"), "windows-1252"))
    }

    @Test
    fun `PRIMER CIRCUITO and Mexico match the windows-1252 bytes`() {
        val request = FormRequest(SiseUrls.SEARCH_FORM, listOf("CircuitoName" to "PRIMER CIRCUITO", "OrgName" to "México"))

        assertEquals("CircuitoName=PRIMER+CIRCUITO&OrgName=M%E9xico", request.encodedBody)
        // UTF-8 would have been M%C3%A9xico.
        assertFalse(request.encodedBody.contains("%C3"))
    }

    @Test
    fun `rejects text that windows-1252 can't carry`() {
        assertThrows<IllegalArgumentException> {
            FormRequest(SiseUrls.SEARCH_FORM, listOf("Expediente" to "12/2026 → A"))
        }
    }

    @Test
    fun `windows-1252 punctuation that ISO-8859-1 lacks is encoded`() {
        val request = FormRequest(SiseUrls.SEARCH_FORM, listOf("OrgName" to "Juzgado “Primero” — México"))

        assertEquals("OrgName=Juzgado+%93Primero%94+%97+M%E9xico", request.encodedBody)
    }

    @Test
    fun `no request ever carries Accion 1 or a captcha response`() {
        val requests = listOf(
            SearchRequests.loadForm("1", "PRIMER CIRCUITO", "767"),
            SearchRequests.loadProcedimientos(form, "125"),
        )
        requests.forEach { request ->
            assertFalse(request.fields.any { it.first == "Accion" && it.second == "1" })
            assertFalse(request.fields.any { it.first.contains("captcha", ignoreCase = true) })
        }
    }

    private fun encodedValue(request: FormRequest, name: String): String =
        request.encodedBody.split('&').single { it.startsWith("$name=") }.substringAfter('=')
}
