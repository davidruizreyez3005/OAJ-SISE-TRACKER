package mx.sisetracker.core

/**
 * A tipo de asunto from the bundled list. Tipo IDs are global (the same ID has
 * the same label at every órgano seen), so the app can offer them and build a
 * case URL without loading the órgano's search form first.
 */
data class KnownTipoAsunto(
    val id: String,
    val label: String,
    /** The kinds of órgano whose search forms list it. */
    val kinds: Set<OrganoKind>,
)

/**
 * Every tipo de asunto seen in the search form fixtures, bundled as
 * `tipos_asunto.tsv` (id, label, kinds as J/T/O). A test checks it against
 * the fixtures; add new tipos there when new forms are captured.
 */
object TiposDeAsunto {
    val all: List<KnownTipoAsunto> by lazy {
        val stream = TiposDeAsunto::class.java.getResourceAsStream("tipos_asunto.tsv")
            ?: error("tipos_asunto.tsv is missing from the classpath")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { line ->
                    val (id, label, kinds) = line.split('\t', limit = 3)
                    KnownTipoAsunto(id, label, kinds.mapNotNull(::kindOf).toSet())
                }
                .toList()
        }
    }

    fun byId(id: String): KnownTipoAsunto? = all.firstOrNull { it.id == id.trim() }

    /**
     * The whole list, those seen at órganos of [kind] first (each part in
     * label order). With no kind, the whole list in label order.
     */
    fun forKind(kind: OrganoKind?): List<FormOption> {
        val ordered = if (kind == null) all else all.filter { kind in it.kinds } + all.filterNot { kind in it.kinds }
        return ordered.mapIndexed { position, tipo -> FormOption(tipo.id, tipo.label, position, selected = false) }
    }

    private fun kindOf(code: Char): OrganoKind? = when (code) {
        'J' -> OrganoKind.JUZGADOS
        'T' -> OrganoKind.TRIBUNALES
        'O' -> OrganoKind.OTROS
        else -> null
    }
}
