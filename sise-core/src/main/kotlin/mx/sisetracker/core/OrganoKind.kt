package mx.sisetracker.core

/** The search screen's "Tipo de órgano" filter. */
enum class OrganoKind {
    JUZGADOS,
    TRIBUNALES,
    OTROS,
    ;

    companion object {
        /**
         * Derived from the name, since the portal's órgano list has no type
         * selector: "Juzgado…" → Juzgados, "…Tribunal…" → Tribunales, anything
         * else → Otros.
         */
        fun fromName(name: String): OrganoKind = when {
            name.trim().startsWith("Juzgado", ignoreCase = true) -> JUZGADOS
            name.contains("Tribunal", ignoreCase = true) -> TRIBUNALES
            else -> OTROS
        }
    }
}
