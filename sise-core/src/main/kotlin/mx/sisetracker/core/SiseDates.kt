package mx.sisetracker.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** Date formats used by the portal. */
object SiseDates {
    private val gridFormat = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val spanFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

    /** Acuerdos grid cells: `dd-MM-yyyy`. */
    fun parseGrid(text: String): LocalDate = parse(text, gridFormat)

    /** Spans on the case and síntesis pages: `dd/MM/yyyy`. */
    fun parseSpan(text: String): LocalDate = parse(text, spanFormat)

    fun formatSpan(date: LocalDate): String = spanFormat.format(date)

    /**
     * DoVerAcuerdo arguments look like `dd/MM/yyyy 12:00:00 a.m.`. Returns the
     * date part exactly as given, after checking that it is a valid date.
     */
    fun datePart(argument: String): String {
        val date = argument.trim().substringBefore(' ')
        parseSpan(date)
        return date
    }

    private fun parse(text: String, format: DateTimeFormatter): LocalDate {
        val value = text.trim()
        return try {
            LocalDate.parse(value, format)
        } catch (e: DateTimeParseException) {
            throw SiseParseException("Unexpected date: '$value'", e)
        }
    }
}
