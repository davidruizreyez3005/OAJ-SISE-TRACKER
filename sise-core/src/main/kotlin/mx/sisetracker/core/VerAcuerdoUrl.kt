package mx.sisetracker.core

/**
 * Builds the síntesis page URL (`VerAcuerdo.aspx`) from an acuerdo's
 * DoVerAcuerdo arguments, the same URL the portal opens after its postback.
 */
object VerAcuerdoUrl {
    fun build(acuerdo: Acuerdo): String = build(acuerdo.link)

    fun build(link: DoVerAcuerdo): String = buildString {
        append(SiseUrls.VER_ACUERDO)
        append("?listaAcOrd=").append(link.orden)
        append("&listaCatOrg=").append(QueryString.encode(link.organismoId))
        append("&listaNeun=").append(QueryString.encode(link.neun))
        append("&listaAsuId=").append(QueryString.encode(link.asuntoId))
        append("&listaExped=").append(QueryString.encode(link.expediente))
        append("&listaFAuto=").append(QueryString.encode(link.fechaAuto))
        append("&listaFPublicacion=").append(QueryString.encode(link.fechaPublicacion))
    }
}
