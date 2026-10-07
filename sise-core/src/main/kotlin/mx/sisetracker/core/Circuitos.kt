package mx.sisetracker.core

/**
 * A judicial circuit as the OAJ lists it. [num] is the OAJ's value (1 to 32),
 * the circuit's identity in the app; [label] is the OAJ's text ("Primer
 * Circuito Ciudad de México"), shown exactly as given. [portalCir] is the
 * portal's own number for it: the `Cir` parameter of `circuitos.asp` and the
 * `Circuito` form field. It equals [num] only for some circuits (e.g. the
 * Decimosexto is `Cir=45`). [portalName] is the `CircuitoName` the search
 * form sends ("DÉCIMO SEXTO CIRCUITO"), as the circuit's órgano list showed
 * it in the October 2026 crawl; a freshly loaded list
 * ([OrganoList.circuitoName]) takes precedence.
 */
data class Circuito(val num: String, val label: String, val portalCir: String, val portalName: String = "") {
    /** The label's ordinal part, for display: "Primer Circuito". */
    val ordinal: String get() = label.substringBefore(SEPARATOR, "").let { if (it.isEmpty()) label else "$it Circuito" }

    /** The label's state part, for display: "Ciudad de México" (empty if the label has none). */
    val region: String get() = label.substringAfter(SEPARATOR, "")

    private companion object {
        const val SEPARATOR = " Circuito "
    }
}

/**
 * The 32 circuits, bundled as `circuitos.tsv` (num, label, portal `Cir`,
 * CircuitoName; step A in CLAUDE.md). The app never fetches the OAJ pages they come from;
 * tests check the labels against `oaj_circuitos_excerpt.html` and the portal
 * numbers against `oaj_datos_expedientes.json`. Update them together.
 */
object Circuitos {
    val all: List<Circuito> by lazy {
        val stream = Circuitos::class.java.getResourceAsStream("circuitos.tsv")
            ?: error("circuitos.tsv is missing from the classpath")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { line ->
                    val (num, label, portalCir, portalName) = line.split('\t', limit = 4)
                    Circuito(num, label, portalCir, portalName)
                }
                .toList()
        }
    }

    fun byNum(num: String): Circuito? = all.firstOrNull { it.num == num.trim() }

    /** The portal's `Cir` for the OAJ circuit [num]; [num] itself if it isn't a known circuit. */
    fun portalCir(num: String): String = byNum(num)?.portalCir ?: num.trim()
}
