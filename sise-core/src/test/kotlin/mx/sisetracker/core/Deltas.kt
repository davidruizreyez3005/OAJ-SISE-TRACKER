package mx.sisetracker.core

import java.net.URI

/** Reads reference values out of the saved MS AJAX delta responses (tests only). */
object Deltas {
    /** The síntesis URL the portal opened, resolved like the browser did. */
    fun verAcuerdoUrl(fixture: String): String {
        val delta = Fixtures.load(fixture)
        val relative = Regex("AbrirVentanaTotal\\('([^']*)'").find(delta)?.groupValues?.get(1)
            ?: error("No AbrirVentanaTotal in $fixture")
        return URI(SiseUrls.CASE_PAGE).resolve(relative).toString()
    }
}
