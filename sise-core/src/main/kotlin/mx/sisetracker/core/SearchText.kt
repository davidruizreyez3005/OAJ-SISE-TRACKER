package mx.sisetracker.core

import java.text.Normalizer

/** Text matching for the app's filters: accent- and case-insensitive. */
object SearchText {
    private val marks = Regex("\\p{Mn}+")

    /** "Juzgado Sexto … México" → "juzgado sexto … mexico". */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "")
            .lowercase()
            .let(SiseText::normalizeSpace)

    /**
     * The same name, compared exactly apart from whitespace runs: for matching
     * a name shown in one place of the portal against another (e.g. a related
     * case's órgano against a known órgano).
     */
    fun sameName(a: String, b: String): Boolean =
        SiseText.normalizeSpace(a) == SiseText.normalizeSpace(b)

    /** Whether every word of [query] appears in [text], ignoring accents and case. */
    fun matches(text: String, query: String): Boolean {
        val haystack = fold(text)
        return fold(query).split(' ').filter { it.isNotEmpty() }.all { it in haystack }
    }
}

/** Expediente numbers have always been `n/yyyy` so far; other shapes only get a warning. */
object ExpedienteFormat {
    private val usual = Regex("^\\d+/\\d{4}$")

    fun isUsual(expediente: String): Boolean = usual.matches(expediente.trim())
}
