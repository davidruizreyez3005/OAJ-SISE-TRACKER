package mx.sisetracker.core

/** Turns what the user types into a safe SQLite FTS4 MATCH expression. */
object FtsQuery {
    private val token = Regex("[\\p{L}\\p{N}]+")
    private const val MIN_TOKEN_LENGTH = 2

    /**
     * Every word as a prefix, all required: `suspensión defin` becomes
     * `"suspensión*" "defin*"`. Only letters and digits get through, so FTS
     * operators and quotes in the input can't break the query. Words shorter
     * than two characters are dropped. Null when nothing is left to search.
     *
     * Accents and case are folded by the table's tokenizer, not here.
     */
    fun from(input: String): String? =
        token.findAll(input)
            .map { it.value }
            .filter { it.length >= MIN_TOKEN_LENGTH }
            .distinct()
            .joinToString(" ") { "\"$it*\"" }
            .ifEmpty { null }
}
