package mx.sisetracker.core

/**
 * Loads saved portal responses from `src/test/resources/fixtures/`.
 *
 * Fixtures are byte-exact (CRLF line endings included), so they're decoded as
 * UTF-8 without any newline translation.
 */
object Fixtures {
    fun load(name: String): String {
        val stream = Fixtures::class.java.classLoader.getResourceAsStream("fixtures/$name")
            ?: error("Missing fixture: $name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
