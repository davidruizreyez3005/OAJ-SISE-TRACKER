package mx.sisetracker.testing

import mx.sisetracker.core.CaseLookup
import mx.sisetracker.core.CasePage
import mx.sisetracker.core.CasePageParser

/** The saved portal responses shared with :sise-core (see app/build.gradle.kts). */
object Fixtures {
    fun load(name: String): String {
        val stream = Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/$name")
            ?: error("Missing fixture: $name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    const val CASE_1183 = "vercaptura_1183-2025_amparo-indirecto.html"
    const val CASE_NOT_FOUND = "vercaptura_not-found_99999-2025.html"
    const val SEARCH_FORM_1183 = "expedienteytipo_result_1183-2025.html"
    const val ORGANOS_CIR1 = "circuitos_cir1.html"
    const val SINTESIS_1183_38 = "veracuerdo_1183-2025_orden38.html"
}

/** A case page fixture that must parse as found. */
fun parseCase(fixture: String): CasePage =
    (CasePageParser.parse(Fixtures.load(fixture)) as CaseLookup.Found).page
