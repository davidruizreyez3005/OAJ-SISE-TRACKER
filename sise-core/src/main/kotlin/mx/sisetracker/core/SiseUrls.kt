package mx.sisetracker.core

/** Public SISE portal addresses. */
object SiseUrls {
    const val HOST = "www.dgej.cjf.gob.mx"

    /** Case page (ASP.NET, UTF-8): a public GET, see [CaseUrl]. */
    const val CASE_PAGE = "https://$HOST/siseinternet/reportes/vercaptura.aspx"
    internal const val CASE_PAGE_PATH = "/siseinternet/reportes/vercaptura.aspx"

    /** Full síntesis of one acuerdo (ASP.NET, UTF-8): a public GET, see [VerAcuerdoUrl]. */
    const val VER_ACUERDO = "https://$HOST/siseinternet/Actuaria/VerAcuerdo.aspx"

    /** The portal's search form (classic ASP, windows-1252). */
    const val SEARCH_FORM = "https://$HOST/internet/expedientes/ExpedienteyTipo.asp"

    /**
     * A circuit's órgano list, where a search on the portal starts (step B).
     * [circuito] is the app's (OAJ) circuit number; it's mapped to the
     * portal's own `Cir` here.
     */
    fun circuitos(circuito: String): String =
        "https://$HOST/internet/expedientes/circuitos.asp?Cir=${QueryString.encode(Circuitos.portalCir(circuito))}&Exp=1"
}
