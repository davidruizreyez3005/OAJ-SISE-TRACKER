package mx.sisetracker.testing

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
    const val SINTESIS_1183_38 = "veracuerdo_1183-2025_orden38.html"
}
