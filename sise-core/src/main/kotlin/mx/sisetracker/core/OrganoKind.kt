package mx.sisetracker.core

/** The search screen's "Tipo de órgano" filter. */
enum class OrganoKind {
    JUZGADOS,
    TRIBUNALES,
    OTROS,
    ;

    companion object {
        // The leading noun, after at most two capitalized ordinal words
        // ("Juzgado…", "Segundo Tribunal…", "Décimo Primer Tribunal…").
        private val juzgado = Regex("""^(?:\p{Lu}\p{Ll}+\s){0,2}Juzgado\b""")
        private val tribunal = Regex("""^(?:\p{Lu}\p{Ll}+\s){0,2}Tribunal\b""")

        /**
         * Derived from the name's leading noun, since the portal's órgano list
         * has no type selector. "Contains Tribunal" would be wrong: "Unidad de
         * Instrucción de la Comisión de Conflictos Laborales del Tribunal de
         * Disciplina Judicial" is Otros.
         */
        fun fromName(name: String): OrganoKind {
            val trimmed = SiseText.normalizeSpace(name)
            return when {
                juzgado.containsMatchIn(trimmed) -> JUZGADOS
                tribunal.containsMatchIn(trimmed) -> TRIBUNALES
                else -> OTROS
            }
        }
    }
}
