package mx.sisetracker.core

import java.time.LocalDate
import java.time.LocalTime

/** The kinds of hearing "Captura de Información" names. */
enum class TipoAudiencia { CONSTITUCIONAL, INCIDENTAL, OTRA }

/** A hearing the case data says is set for [fecha], at [hora] when the record gives one. */
data class Audiencia(val tipo: TipoAudiencia, val fecha: LocalDate, val hora: LocalTime? = null)

/**
 * Hearings from a case's "Captura de Información". Seen on juzgado cases
 * (1183/2025): each kind has a record with "Fecha señalada para audiencia
 * {kind}" and "Hora señalada para audiencia {kind}", one more record per
 * postponement (labels ending in "(diferimiento)"), and "Fecha celebración
 * audiencia {kind}" once it was held. Labels are matched ignoring accents,
 * case and spacing. Tribunal cases may have no such labels at all.
 */
object Audiencias {
    private val scheduledDate = Regex("^fecha senalada para (?:la )?audiencia (\\p{L}+)")
    private val scheduledTime = Regex("^hora senalada para (?:la )?audiencia (\\p{L}+)")
    private val heldDate = Regex("^fecha (?:de )?celebracion (?:de )?(?:la )?audiencia (\\p{L}+)")
    private val date = Regex("\\b\\d{2}/\\d{2}/\\d{4}\\b")
    private val time = Regex("\\b(\\d{1,2}):(\\d{2})\\b")

    /** A record: hearing dates and times pair up within one. */
    private data class Record(val kind: String, val section: String, val group: Int)

    /**
     * Each kind's current date: its last record in page order (the latest
     * postponement), unless a celebración on or after that date says the
     * hearing was held. [entries] are in page order.
     */
    fun scheduled(entries: List<CapturaEntry>): List<Audiencia> {
        val scheduled = mutableMapOf<String, Pair<Record, LocalDate>>()
        val times = mutableMapOf<Record, LocalTime>()
        val held = mutableMapOf<String, LocalDate>()
        for (entry in entries) {
            val label = SearchText.fold(entry.label)
            scheduledDate.find(label)?.let { match ->
                val kind = match.groupValues[1]
                parseDate(entry.value)?.let { scheduled[kind] = Record(kind, entry.section, entry.group) to it }
            }
            scheduledTime.find(label)?.let { match ->
                parseTime(entry.value)?.let { times[Record(match.groupValues[1], entry.section, entry.group)] = it }
            }
            heldDate.find(label)?.let { match -> parseDate(entry.value)?.let { held[match.groupValues[1]] = it } }
        }
        return scheduled
            .filter { (kind, record) -> held[kind]?.let { it < record.second } ?: true }
            .map { (kind, record) -> Audiencia(tipo(kind), record.second, times[record.first]) }
    }

    /** The soonest hearing on or after [today], or null. */
    fun next(entries: List<CapturaEntry>, today: LocalDate): Audiencia? =
        scheduled(entries).filter { it.fecha >= today }.minByOrNull { it.fecha }

    private fun tipo(kind: String): TipoAudiencia = when (kind) {
        "constitucional" -> TipoAudiencia.CONSTITUCIONAL
        "incidental" -> TipoAudiencia.INCIDENTAL
        else -> TipoAudiencia.OTRA
    }

    private fun parseDate(value: String): LocalDate? {
        val text = date.find(value)?.value ?: return null
        return try {
            SiseDates.parseSpan(text)
        } catch (e: SiseParseException) {
            null
        }
    }

    private fun parseTime(value: String): LocalTime? {
        val (hours, minutes) = time.find(value)?.destructured ?: return null
        val h = hours.toInt()
        val m = minutes.toInt()
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }
}
