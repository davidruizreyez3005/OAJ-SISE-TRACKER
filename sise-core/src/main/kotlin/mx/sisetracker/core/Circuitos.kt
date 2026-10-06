package mx.sisetracker.core

/**
 * A judicial circuit as the OAJ lists it. [num] is the OAJ's value (1 to 32),
 * the circuit's identity in the app; [label] is the OAJ's text ("Primer
 * Circuito Ciudad de México"), shown exactly as given. [portalCir] is the
 * portal's own number for it: the `Cir` parameter of `circuitos.asp` and the
 * `Circuito` form field. It equals [num] only for some circuits (e.g. the
 * Decimosexto is `Cir=45`). Neither is the `CircuitoName` the search form
 * sends: that one comes from the órgano list ([OrganoList.circuitoName]).
 */
data class Circuito(val num: String, val label: String, val portalCir: String)

/**
 * The 32 circuits, bundled as `circuitos.tsv` (num, label, portal `Cir`;
 * step A in CLAUDE.md). The app never fetches the OAJ pages they come from;
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
                    val (num, label, portalCir) = line.split('\t', limit = 3)
                    Circuito(num, label, portalCir)
                }
                .toList()
        }
    }

    fun byNum(num: String): Circuito? = all.firstOrNull { it.num == num.trim() }

    /** The portal's `Cir` for the OAJ circuit [num]; [num] itself if it isn't a known circuit. */
    fun portalCir(num: String): String = byNum(num)?.portalCir ?: num.trim()
}
