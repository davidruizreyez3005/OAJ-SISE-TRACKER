package mx.sisetracker.core

/**
 * A judicial circuit as the OAJ lists it. [num] is the `Cir` parameter of
 * `circuitos.asp`; [label] is the OAJ's text ("Primer Circuito Ciudad de
 * México"), shown exactly as given. It is not the `CircuitoName` the search
 * form sends: that one comes from the órgano list ([OrganoList.circuitoName]).
 */
data class Circuito(val num: String, val label: String)

/**
 * The 32 circuits, bundled as `circuitos.tsv` (step A in CLAUDE.md). The app
 * never fetches the OAJ page they come from; a test checks this list against
 * the `oaj_circuitos_excerpt.html` fixture. Update both together.
 */
object Circuitos {
    val all: List<Circuito> by lazy {
        val stream = Circuitos::class.java.getResourceAsStream("circuitos.tsv")
            ?: error("circuitos.tsv is missing from the classpath")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() }
                .map { line ->
                    val (num, label) = line.split('\t', limit = 2)
                    Circuito(num, label)
                }
                .toList()
        }
    }

    fun byNum(num: String): Circuito? = all.firstOrNull { it.num == num.trim() }
}
