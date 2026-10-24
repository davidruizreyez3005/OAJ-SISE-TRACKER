package mx.sisetracker.core

/**
 * The bundled TSV files in `mx/sisetracker/core/`, read by absolute path. A
 * relative lookup resolves against the calling class's package, which R8 may
 * rename in a release build, and the file would no longer be found.
 */
internal object BundledFiles {
    private const val DIR = "/mx/sisetracker/core/"

    /** The non-blank lines of [name]. */
    fun lines(name: String): List<String> {
        val stream = BundledFiles::class.java.getResourceAsStream(DIR + name)
            ?: error("$name is missing from the classpath")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.filter { it.isNotBlank() }.toList() }
    }
}
