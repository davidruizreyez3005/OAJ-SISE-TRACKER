package mx.sisetracker.core

/** An órgano as the bundled catalog lists it, in one circuit's list. */
data class CatalogoOrgano(
    /** The OAJ circuit number whose list has it (an órgano can be in several). */
    val circuito: String,
    val organo: Organo,
    /**
     * The class whose tipo list its search form showed in the crawl, or null
     * when the form offered no tipos at all.
     */
    val tipos: OrganoClase?,
)

/**
 * A snapshot of the whole portal catalog, from the October 2026 crawl of all
 * 32 circuits (949 distinct órganos), bundled so the search screen needs no
 * request before the lookup itself:
 * - `organos.tsv`: each circuit's órgano list in the portal's order (OAJ
 *   circuit, organismo, tipo-list class code or `-` for none, name exactly
 *   as listed). Every form's tipos were exactly one class's list from
 *   `tipos_asunto.tsv`, or none.
 * - `tipos_procedimiento.tsv`: the procedimientos of tipos 125 and 126, the
 *   same at all 39 apelación tribunals (tipo, id, label, in the portal's order).
 *
 * The portal can add órganos later, so the live lists still win when loaded.
 */
object Catalogo {
    private val organos: List<CatalogoOrgano> by lazy {
        val positions = mutableMapOf<String, Int>()
        lines("organos.tsv").map { line ->
            val (circuito, id, code, name) = line.split('\t', limit = 4)
            val position = positions.merge(circuito, 1, Int::plus)?.minus(1) ?: 0
            val tipos = if (code == "-") null else code.singleOrNull()?.let(OrganoClase::ofCode)
                ?: error("Unknown tipo class '$code' for órgano $id")
            CatalogoOrgano(circuito, Organo(id, name, position), tipos)
        }
    }

    private val procedimientos: Map<String, List<FormOption>> by lazy {
        lines("tipos_procedimiento.tsv")
            .map { it.split('\t', limit = 3) }
            .groupBy({ it[0] }, { it[1] to it[2] })
            .mapValues { (_, options) ->
                options.mapIndexed { position, (id, label) -> FormOption(id, label, position, selected = false) }
            }
    }

    /** The circuit's órgano list as of the crawl; empty for an unknown circuit. */
    fun organos(circuito: String): List<Organo> = organos.filter { it.circuito == circuito.trim() }.map { it.organo }

    /** Every circuit list entry for [organismo] (several for órganos shared by circuits, e.g. Plenos Regionales). */
    fun entries(organismo: String): List<CatalogoOrgano> = organos.filter { it.organo.id == organismo.trim() }

    /** Entries whose órgano is named exactly [name] (apart from whitespace runs). */
    fun findByName(name: String): List<CatalogoOrgano> = organos.filter { SearchText.sameName(it.organo.name, name) }

    /**
     * The tipos de asunto [organismo]'s search form offers, in the portal's
     * order (empty when it offers none), or null if the órgano isn't in the
     * snapshot.
     */
    fun tiposDeAsunto(organismo: String): List<FormOption>? {
        val entry = entries(organismo).firstOrNull() ?: return null
        return entry.tipos?.let(TiposDeAsunto::forClase).orEmpty()
    }

    /** The procedimientos of [tipoAsunto] (the same at every órgano seen), or null if not in the snapshot. */
    fun tiposDeProcedimiento(tipoAsunto: String): List<FormOption>? = procedimientos[tipoAsunto.trim()]

    private fun lines(resource: String): List<String> {
        val stream = Catalogo::class.java.getResourceAsStream(resource)
            ?: error("$resource is missing from the classpath")
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.filter { it.isNotBlank() }.toList() }
    }
}
