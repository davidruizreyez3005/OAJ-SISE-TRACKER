package mx.sisetracker.core

import java.net.URLDecoder

// Uses the charset-name overloads of the JDK helpers: the Charset overloads
// only exist from Android API 33, and this library runs on API 26.
internal object QueryString {
    private const val HEX = "0123456789ABCDEF"

    /**
     * Percent-encodes a query value as UTF-8. Unreserved characters and `/`
     * stay as they are, matching the portal's own links (`expediente=1183/2025`).
     */
    fun encode(value: String): String {
        val out = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            if (isUnreserved(c) || c == '/'.code) {
                out.append(c.toChar())
            } else {
                out.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
        return out.toString()
    }

    /**
     * Splits a raw query string into decoded pairs, in order.
     * @throws IllegalArgumentException on malformed percent escapes.
     */
    fun parse(query: String): List<Pair<String, String>> =
        query.split('&')
            .filter { it.isNotEmpty() }
            .map { part ->
                val key = part.substringBefore('=')
                val value = part.substringAfter('=', missingDelimiterValue = "")
                decode(key) to decode(value)
            }

    private fun decode(text: String): String = URLDecoder.decode(text, "UTF-8")

    private fun isUnreserved(c: Int): Boolean =
        c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
}
