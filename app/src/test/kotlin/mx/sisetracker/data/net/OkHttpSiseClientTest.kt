package mx.sisetracker.data.net

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mx.sisetracker.core.FormRequest
import mx.sisetracker.core.SearchRequests
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Runs the real OkHttp client against an interceptor that answers locally: no network. */
class OkHttpSiseClientTest {
    private val network = FakeNetwork()
    private val client = OkHttpSiseClient(
        http = OkHttpClient.Builder().addInterceptor(network).build(),
        queue = PoliteRequestQueue(minIntervalMillis = 0) { 0L },
        userAgent = { "Mozilla/5.0 (Linux; Android 16) SiseTracker/0.1.0" },
    )

    @Test
    fun `sends the app's User-Agent`() = runTest {
        network.respond("<html/>".toByteArray(), "text/html")

        client.getPage("https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx?tipoasunto=1")

        assertEquals("Mozilla/5.0 (Linux; Android 16) SiseTracker/0.1.0", network.requests.single().header("User-Agent"))
    }

    @Test
    fun `aspx pages default to UTF-8`() = runTest {
        network.respond("México".toByteArray(Charsets.UTF_8), "text/html")

        assertEquals("México", client.getPage("https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx"))
    }

    @Test
    fun `asp form posts send the windows-1252 body as-is and default to windows-1252`() = runTest {
        network.respond("Ejecución de Penas “Norte”".toByteArray(charset("windows-1252")), "text/html")
        val request = FormRequest(
            "https://www.dgej.cjf.gob.mx/internet/expedientes/ExpedienteyTipo.asp",
            listOf("CircuitoName" to "PRIMER CIRCUITO", "OrgName" to "México"),
        )

        val html = client.postForm(request)

        assertEquals("Ejecución de Penas “Norte”", html)
        val sent = network.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("application/x-www-form-urlencoded", sent.body?.contentType().toString())
        assertArrayEquals("CircuitoName=PRIMER+CIRCUITO&OrgName=M%E9xico".toByteArray(), network.bodies.single())
    }

    @Test
    fun `asp pages fetched with GET default to windows-1252`() = runTest {
        network.respond("Comisión de Disciplina".toByteArray(charset("windows-1252")), "text/html")

        val html = client.getFormPage("https://www.dgej.cjf.gob.mx/internet/expedientes/circuitos.asp?Cir=1&Exp=1")

        assertEquals("Comisión de Disciplina", html)
        assertEquals("GET", network.requests.single().method)
    }

    @Test
    fun `a charset in the response header wins`() = runTest {
        network.respond("Órgano".toByteArray(Charsets.UTF_8), "text/html; charset=utf-8")

        assertEquals("Órgano", client.postForm(SearchRequests.loadForm("1", "PRIMER CIRCUITO", "767")))
    }

    @Test
    fun `HTTP errors throw SiseHttpException`() {
        network.respond("Error".toByteArray(), "text/html", code = 503)

        val error = assertThrows(SiseHttpException::class.java) {
            runBlocking { client.getPage("https://www.dgej.cjf.gob.mx/siseinternet/reportes/vercaptura.aspx") }
        }
        assertEquals(503, error.code)
    }

    private class FakeNetwork : Interceptor {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<ByteArray?>()
        private var answer: (Request) -> Response = { error("No response set") }

        fun respond(body: ByteArray, contentType: String, code: Int = 200) {
            answer = { request ->
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Status $code")
                    .body(body.toResponseBody(contentType.toMediaType()))
                    .build()
            }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            bodies += request.body?.let { body -> Buffer().also { body.writeTo(it) }.readByteArray() }
            return answer(request)
        }
    }
}
