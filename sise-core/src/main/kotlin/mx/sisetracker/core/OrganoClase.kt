package mx.sisetracker.core

/**
 * The class of órgano within its [OrganoKind], derived from the name like the
 * kind itself. Each class has its own list of tipos de asunto on the portal
 * (see `tipos_asunto.tsv`), so it decides what the Tipo de asunto dropdown
 * offers before any request.
 */
enum class OrganoClase(val kind: OrganoKind, internal val code: Char) {
    /** Juzgados de Distrito, whatever their specialty. */
    JUZGADO_DISTRITO(OrganoKind.JUZGADOS, 'J'),

    /** Tribunales Colegiados de Circuito: amparo directo, revisión, queja… */
    COLEGIADO_CIRCUITO(OrganoKind.TRIBUNALES, 'C'),

    /** Tribunales Colegiados de Apelación (formerly Tribunales Unitarios). */
    COLEGIADO_APELACION(OrganoKind.TRIBUNALES, 'A'),

    /** Tribunales Laborales Federales (the labor reform's first instance). */
    TRIBUNAL_LABORAL(OrganoKind.TRIBUNALES, 'L'),

    /** Plenos Regionales. */
    PLENO_REGIONAL(OrganoKind.OTROS, 'P'),

    /** The Comisión de Conflictos Laborales del Poder Judicial de la Federación. */
    CONFLICTOS_LABORALES(OrganoKind.OTROS, 'K'),

    /** Administrative bodies and anything not recognized. */
    OTRO(OrganoKind.OTROS, 'O'),
    ;

    companion object {
        private val apelacion = Regex("""\btribunal (?:colegiado de apelacion|unitario)\b""")
        private val laboral = Regex("""\btribunal laboral\b""")
        private val pleno = Regex("""^pleno\b""")
        private val conflictos = Regex("""^comision de conflictos laborales\b""")

        fun fromName(name: String): OrganoClase {
            val folded = SearchText.fold(name)
            return when (OrganoKind.fromName(name)) {
                OrganoKind.JUZGADOS -> JUZGADO_DISTRITO
                OrganoKind.TRIBUNALES -> when {
                    apelacion.containsMatchIn(folded) -> COLEGIADO_APELACION
                    laboral.containsMatchIn(folded) -> TRIBUNAL_LABORAL
                    else -> COLEGIADO_CIRCUITO
                }
                OrganoKind.OTROS -> when {
                    pleno.containsMatchIn(folded) -> PLENO_REGIONAL
                    conflictos.containsMatchIn(folded) -> CONFLICTOS_LABORALES
                    else -> OTRO
                }
            }
        }

        internal fun ofCode(code: Char): OrganoClase? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The materia an órgano hears, read from its name ("en Materia Penal",
 * "en Materias Civil y de Trabajo", "Tribunal Laboral…"). An órgano can have
 * several; juzgados and tribunales whose name states none hear all of them
 * ([MIXTA]). Administrative bodies have none.
 */
enum class Materia {
    PENAL,
    CIVIL,
    ADMINISTRATIVA,
    TRABAJO,
    MERCANTIL,
    MIXTA,
    ;

    companion object {
        private val rules = listOf(
            PENAL to Regex("""\bpenal(?:es)?\b|\bpenas\b"""),
            CIVIL to Regex("""\bcivil(?:es)?\b|\bextincion de dominio\b"""),
            ADMINISTRATIVA to Regex("""\badministrativas?\b|\bcompetencia economica\b"""),
            TRABAJO to Regex("""\btrabajo\b|\blaboral(?:es)?\b"""),
            MERCANTIL to Regex("""\bmercantil(?:es)?\b"""),
        )

        fun fromName(name: String): Set<Materia> {
            val clase = OrganoClase.fromName(name)
            if (clase == OrganoClase.OTRO || clase == OrganoClase.CONFLICTOS_LABORALES) return emptySet()
            if (clase == OrganoClase.TRIBUNAL_LABORAL) return setOf(TRABAJO)
            val folded = SearchText.fold(name)
            val found = rules.filter { (_, regex) -> regex.containsMatchIn(folded) }.map { it.first }.toSet()
            return found.ifEmpty { setOf(MIXTA) }
        }
    }
}
