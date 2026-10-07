package mx.sisetracker.core

import java.time.LocalDate

/** What the case page answered for a lookup. */
sealed interface CaseLookup {
    data class Found(val page: CasePage) : CaseLookup

    /**
     * The portal's normal page shell with an empty `#lblNEUN`: the expediente
     * doesn't exist, or (most likely) the órgano or tipo de asunto is wrong.
     */
    data object NotFound : CaseLookup
}

/** Everything the public case page (`vercaptura.aspx`) shows for one case. */
data class CasePage(
    /** NEUN: the stable, unique case ID. */
    val neun: String,
    val organoName: String,
    val tipoAsuntoName: String,
    val expediente: String,
    /** Control number of the Oficina de Correspondencia Común. */
    val noControlOcc: String,
    /** From "Listado de Resoluciones (N)", when shown. */
    val resolucionesCount: Int?,
    /** In page order (ascending orden). */
    val acuerdos: List<Acuerdo>,
    val resoluciones: List<Resolucion>,
    val asuntosRelacionados: List<AsuntoRelacionado>,
    val captura: CapturaInfo,
)

/** One row of the acuerdos grid. */
data class Acuerdo(
    /** The displayed "No.". Not a key: use [orden]. */
    val numero: String,
    val fechaAuto: LocalDate,
    val tipoCuaderno: String,
    /**
     * Null while the acuerdo isn't published yet: the grid shows `&nbsp;` and
     * the link passes `""` (seen October 2026, 1068/2025 at órgano 721).
     */
    val fechaPublicacion: LocalDate?,
    /** The grid's résumé, line breaks kept. */
    val resumen: String,
    /** The "Ver síntesis" link, which also carries the acuerdo's key. */
    val link: DoVerAcuerdo,
) {
    /** The acuerdo's key within its case. Has gaps (1, 2, 4, 5…). */
    val orden: Int get() = link.orden

    val isPublished: Boolean get() = fechaPublicacion != null

    /** Whether the résumé was cut short, so the full síntesis must be fetched. */
    val isResumenTruncated: Boolean get() = Resumen.isTruncated(resumen)
}

object Resumen {
    const val TRUNCATION_SUFFIX = " ..."

    /**
     * The grid shows the first 255 characters of a long síntesis followed by
     * `" ..."` (seen in 3 of 3 samples). A résumé without that suffix is
     * assumed to be the whole síntesis, which is not yet verified, so this is
     * the one place to change if a counterexample shows up.
     */
    fun isTruncated(resumen: String): Boolean = resumen.endsWith(TRUNCATION_SUFFIX)
}

/** One row of "Listado de Resoluciones". */
data class Resolucion(
    val neun: String,
    val fechaIngreso: LocalDate,
    val tema: String,
    /** Cleartext `http://` document link: open it in the browser, never fetch it. */
    val archivoUrl: String?,
)

/** One row of "Asuntos Relacionados". */
data class AsuntoRelacionado(
    val neun: String,
    val expediente: String,
    /** `"{órgano} - {tipo asunto}"`, as shown. */
    val organo: String,
    val fechaRelacion: LocalDate,
) {
    val organoName: String get() = OrganoTitle.organo(organo)
    val tipoAsuntoName: String get() = OrganoTitle.tipoAsunto(organo)
}

/** "Captura de Información": the case data, as an ordered list. */
data class CapturaInfo(
    /** In page order. Labels repeat, so this is a list rather than a map. */
    val entries: List<CapturaEntry>,
    /** Number of party panels (they're identical copies of the case data). */
    val partyCount: Int,
) {
    val sections: List<String> get() = entries.map { it.section }.distinct()

    fun values(label: String): List<String> = entries.filter { it.label == label }.map { it.value }

    companion object {
        val EMPTY = CapturaInfo(entries = emptyList(), partyCount = 0)
    }
}

data class CapturaEntry(
    val section: String,
    /** Index of the record within its section; e.g. each diferimiento of an audiencia is its own record. */
    val group: Int,
    val label: String,
    val value: String,
)

/** `"{órgano} - {tipo asunto}"` titles, split on the last `" - "`. */
object OrganoTitle {
    private const val SEPARATOR = " - "

    fun organo(title: String): String =
        if (title.contains(SEPARATOR)) title.substringBeforeLast(SEPARATOR).trim() else title.trim()

    fun tipoAsunto(title: String): String =
        if (title.contains(SEPARATOR)) title.substringAfterLast(SEPARATOR).trim() else ""
}
