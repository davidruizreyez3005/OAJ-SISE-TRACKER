package mx.sisetracker.data.net

import java.io.IOException
import java.nio.charset.Charset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.sisetracker.core.FormRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

/** The app's only way to reach the portal. */
interface SiseClient {
    /** GETs an ASP.NET page (UTF-8): a case page or a síntesis page. */
    suspend fun getPage(url: String): String

    /** GETs a classic ASP search page (windows-1252): a circuit's órgano list. */
    suspend fun getFormPage(url: String): String

    /** POSTs one of the classic ASP search forms (windows-1252). */
    suspend fun postForm(request: FormRequest): String
}

/** The portal answered with an HTTP error. */
class SiseHttpException(val code: Int, url: String) : IOException("HTTP $code for $url")

class OkHttpSiseClient(
    private val http: OkHttpClient,
    private val queue: PoliteRequestQueue,
    private val userAgent: () -> String,
) : SiseClient {

    override suspend fun getPage(url: String): String =
        execute(Request.Builder().url(url).get(), Charsets.UTF_8)

    override suspend fun getFormPage(url: String): String =
        execute(Request.Builder().url(url).get(), formPageCharset)

    // The body is already percent-encoded from windows-1252 bytes; it goes out as-is.
    override suspend fun postForm(request: FormRequest): String =
        execute(
            Request.Builder().url(request.url).post(request.body.toRequestBody(formMediaType)),
            formPageCharset,
        )

    private suspend fun execute(builder: Request.Builder, pageCharset: Charset): String =
        queue.run {
            withContext(Dispatchers.IO) {
                val request = builder.header("User-Agent", userAgent()).build()
                http.newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) throw SiseHttpException(response.code, request.url.toString())
                    val charset = runCatching { response.body.contentType()?.charset() }.getOrNull() ?: pageCharset
                    response.body.bytes().toString(charset)
                }
            }
        }

    private companion object {
        val formMediaType = FormRequest.CONTENT_TYPE.toMediaType()
        val formPageCharset: Charset = Charset.forName(FormRequest.CHARSET)
    }
}
