package mx.sisetracker.core

/**
 * A portal page doesn't have the structure the parsers expect (the portal may
 * have changed). [location] says where it broke, so the app can show it; the
 * message may quote page text and is never shown.
 */
class SiseParseException(
    message: String,
    cause: Throwable? = null,
    val location: ParseLocation? = null,
) : RuntimeException(message, cause)

/**
 * Where a page failed to parse: the part of the page, the row within its grid
 * (1-based, data rows only) and the field. Built only from these constants and
 * the row number, never from page text, so it can be shown and reported
 * without exposing case text (hard rule 6).
 */
data class ParseLocation(val section: PageSection, val row: Int? = null, val field: PageField? = null)

enum class PageSection {
    /** The case page as a whole (e.g. not a case page at all). */
    CASE_PAGE,
    ACUERDOS,
    RESOLUCIONES,
    ASUNTOS_RELACIONADOS,
    SINTESIS,
    SEARCH_FORM,
    ORGANO_LIST,
}

enum class PageField {
    /** The page's identifying element (`#lblNEUN`, the form, the select…) is missing. */
    STRUCTURE,
    NEUN,
    EXPEDIENTE,
    CELL_COUNT,
    FECHA_AUTO,
    FECHA_PUBLICACION,
    SINTESIS_LINK,
    SINTESIS_TEXT,
    FECHA_INGRESO,
    FECHA_RELACION,
    TIPO_ASUNTO,
}

/**
 * Runs [block], tagging a [SiseParseException] it throws with this location
 * unless an inner call already did (the innermost location wins).
 */
internal inline fun <T> parsingAt(
    section: PageSection,
    row: Int? = null,
    field: PageField? = null,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: SiseParseException) {
        if (e.location != null) throw e
        throw SiseParseException(e.message.orEmpty(), e, ParseLocation(section, row, field))
    }

internal fun parseFailure(message: String, section: PageSection, row: Int? = null, field: PageField? = null) =
    SiseParseException(message, location = ParseLocation(section, row, field))
