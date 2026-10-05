package mx.sisetracker.data.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Keeps the portal's session cookies for the life of the process, like a
 * browser tab would (the classic ASP form pages may rely on them). Never
 * written to disk.
 */
class InMemoryCookieJar(private val now: () -> Long = System::currentTimeMillis) : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            this.cookies += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= now() }
        return cookies.filter { it.matches(url) }
    }
}
