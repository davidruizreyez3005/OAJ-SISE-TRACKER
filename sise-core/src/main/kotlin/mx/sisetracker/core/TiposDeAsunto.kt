package mx.sisetracker.core

/**
 * A tipo de asunto from the bundled list. Tipo IDs are global (the same ID has
 * the same label at every órgano seen), so the app can offer them and build a
 * case URL without loading the órgano's search form first.
 */
data class KnownTipoAsunto(
    val id: String,
    val label: String,
    /** The class of órgano whose search form lists it (each tipo seen at one class only). */
    val clase: OrganoClase,
)

/**
 * Every tipo de asunto seen in the search form fixtures, bundled as
 * `tipos_asunto.tsv` (id, label, class code), grouped by class in the
 * portal's own order. A test checks it against the fixtures; add new tipos
 * there when new forms are captured.
 */
object TiposDeAsunto {
    val all: List<KnownTipoAsunto> by lazy {
        val stream = TiposDeAsunto::class.java.getResourceAsStream("tipos_asunto.tsv")
            ?: error("tipos_asunto.tsv is missing from the classpath")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { line ->
                    val (id, label, code) = line.split('\t', limit = 3)
                    val clase = code.trim().singleOrNull()?.let(OrganoClase::ofCode)
                        ?: error("Unknown órgano class '$code' for tipo $id")
                    KnownTipoAsunto(id, label, clase)
                }
                .toList()
        }
    }

    fun byId(id: String): KnownTipoAsunto? = all.firstOrNull { it.id == id.trim() }

    /**
     * Only the tipos the portal lists for órganos of [clase], in the portal's
     * order. With no class, or one whose list isn't known (administrative
     * bodies, a bare organismo number), every known tipo, grouped by class.
     */
    fun forClase(clase: OrganoClase?): List<FormOption> {
        val own = all.filter { it.clase == clase }
        return own.ifEmpty { all }
            .mapIndexed { position, tipo -> FormOption(tipo.id, tipo.label, position, selected = false) }
    }
}
