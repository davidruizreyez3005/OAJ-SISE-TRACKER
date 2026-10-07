package mx.sisetracker.core

import java.time.LocalDate
import org.jsoup.Jsoup

/** The full síntesis of one acuerdo, from `VerAcuerdo.aspx`. */
data class Sintesis(
    val expediente: String,
    val fechaAuto: LocalDate,
    val fechaPublicacion: LocalDate,
    /**
     * As published: line breaks and internal blank lines kept, trailing ones
     * trimmed. May start mid-sentence in lowercase, and may contain the
     * portal's masking (`*****`); neither is "fixed".
     */
    val text: String,
)

/** Parses the síntesis page, `VerAcuerdo.aspx`. */
object SintesisPageParser {
    /** @throws SiseParseException when the page doesn't look like a síntesis page. */
    fun parse(html: String): Sintesis {
        val document = Jsoup.parse(html, SiseUrls.VER_ACUERDO)
        val text = document.getElementById("lblAcuerdo")
            ?: throw parseFailure("Síntesis page has no #lblAcuerdo", PageSection.SINTESIS, field = PageField.SINTESIS_TEXT)
        return Sintesis(
            expediente = document.textById("lblNoExp"),
            fechaAuto = parsingAt(PageSection.SINTESIS, field = PageField.FECHA_AUTO) {
                SiseDates.parseSpan(document.textById("lblFAuto"))
            },
            fechaPublicacion = parsingAt(PageSection.SINTESIS, field = PageField.FECHA_PUBLICACION) {
                SiseDates.parseSpan(document.textById("lblFPublica"))
            },
            text = text.multilineText(),
        )
    }
}
