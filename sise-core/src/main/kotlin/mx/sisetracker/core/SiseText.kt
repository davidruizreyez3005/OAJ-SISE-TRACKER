package mx.sisetracker.core

internal object SiseText {
    private val whitespace = Regex("[\\s\\u00A0\\u2007\\u202F]+")
    private val leadingBlankLines = Regex("^(?:[ \\t\\u00A0]*\\n)+")

    /** Collapses runs of whitespace (including no-break spaces) into one space and trims. */
    fun normalizeSpace(text: String): String = text.replace(whitespace, " ").trim()

    /**
     * For résumés and síntesis, where line breaks carry meaning: unifies line
     * endings to `\n`, drops leading blank lines and trailing whitespace, and
     * keeps everything else (internal blank lines, spacing, casing) as given.
     */
    fun normalizeMultiline(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(leadingBlankLines, "")
            .trimEnd()
}
