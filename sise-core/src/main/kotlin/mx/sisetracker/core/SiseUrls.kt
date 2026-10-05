package mx.sisetracker.core

/** Public SISE portal addresses. */
object SiseUrls {
    const val HOST = "www.dgej.cjf.gob.mx"

    /** Case page (ASP.NET, UTF-8): a public GET, see [CaseUrl]. */
    const val CASE_PAGE = "https://$HOST/siseinternet/reportes/vercaptura.aspx"
    internal const val CASE_PAGE_PATH = "/siseinternet/reportes/vercaptura.aspx"

    /** Full síntesis of one acuerdo (ASP.NET, UTF-8): a public GET, see [VerAcuerdoUrl]. */
    const val VER_ACUERDO = "https://$HOST/siseinternet/Actuaria/VerAcuerdo.aspx"

    /** The portal's search form (classic ASP, ISO-8859-1). */
    const val SEARCH_FORM = "https://$HOST/internet/expedientes/ExpedienteyTipo.asp"
}
