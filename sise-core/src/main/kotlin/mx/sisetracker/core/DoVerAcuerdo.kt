package mx.sisetracker.core

/**
 * Arguments of the acuerdos grid's
 * `javascript:DoVerAcuerdo(org, orden, neun, asuntoId, "dd/MM/yyyy 12:00:00 a.m.", "dd/MM/yyyy 12:00:00 a.m.", "n/yyyy")`
 * link. Dates are kept as their `dd/MM/yyyy` part, exactly as given.
 *
 * [asuntoId] is not necessarily the case URL's `tipoasunto`: at a tribunal
 * colegiado (293/2026) it is 1 while the case page uses `tipoasunto=11`.
 */
data class DoVerAcuerdo(
    val organismoId: String,
    val orden: Int,
    val neun: String,
    val asuntoId: String,
    val fechaAuto: String,
    val fechaPublicacion: String,
    val expediente: String,
) {
    companion object {
        private val call = Regex("^\\s*javascript:\\s*DoVerAcuerdo\\s*\\((.*)\\)\\s*;?\\s*$", RegexOption.DOT_MATCHES_ALL)

        /** Parses the link's (entity-decoded) href. */
        fun parse(href: String): DoVerAcuerdo {
            val arguments = call.matchEntire(href)?.groupValues?.get(1)
                ?: throw SiseParseException("Not a DoVerAcuerdo link: '$href'")
            val args = splitArguments(arguments)
            if (args.size != 7) {
                throw SiseParseException("DoVerAcuerdo expects 7 arguments, got ${args.size}: '$href'")
            }
            val orden = args[1].toIntOrNull()
                ?: throw SiseParseException("DoVerAcuerdo orden isn't a number: '$href'")
            return DoVerAcuerdo(
                organismoId = args[0],
                orden = orden,
                neun = args[2],
                asuntoId = args[3],
                fechaAuto = SiseDates.datePart(args[4]),
                fechaPublicacion = SiseDates.datePart(args[5]),
                expediente = args[6],
            )
        }

        /** Splits a JavaScript argument list, unquoting '…' and "…" strings. */
        private fun splitArguments(arguments: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            for (c in arguments) {
                when {
                    quote != null && c == quote -> quote = null
                    quote != null -> current.append(c)
                    c == '"' || c == '\'' -> quote = c
                    c == ',' -> {
                        result += current.toString().trim()
                        current.clear()
                    }
                    else -> current.append(c)
                }
            }
            if (quote != null) throw SiseParseException("Unterminated string in DoVerAcuerdo arguments")
            result += current.toString().trim()
            return result
        }
    }
}
