package mx.sisetracker.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import mx.sisetracker.R
import mx.sisetracker.core.Audiencia
import mx.sisetracker.core.TipoAudiencia

@StringRes
fun TipoAudiencia.labelRes(): Int = when (this) {
    TipoAudiencia.CONSTITUCIONAL -> R.string.audiencia_constitucional
    TipoAudiencia.INCIDENTAL -> R.string.audiencia_incidental
    TipoAudiencia.OTRA -> R.string.audiencia_otra
}

private val spanish = Locale.forLanguageTag("es-MX")
private val dayMonth = DateTimeFormatter.ofPattern("dd/MM")
private val fullDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val clock = DateTimeFormatter.ofPattern("H:mm")

/**
 * When a hearing is, from [today]: "hoy", "mañana", the weekday within a
 * week ("lunes 26/10"), else the date; then its time, if known
 * ("mañana a las 10:23"). Also used by the reminder notifications.
 */
fun hearingWhen(resources: Resources, audiencia: Audiencia, today: LocalDate): String {
    val day = when (val days = ChronoUnit.DAYS.between(today, audiencia.fecha)) {
        0L -> resources.getString(R.string.audiencia_today)
        1L -> resources.getString(R.string.audiencia_tomorrow)
        in 2L..6L -> audiencia.fecha.dayOfWeek.getDisplayName(TextStyle.FULL, spanish) + " " + audiencia.fecha.format(dayMonth)
        else -> audiencia.fecha.format(fullDate)
    }
    val hora = audiencia.hora ?: return day
    return resources.getString(R.string.audiencia_at, day, hora.format(clock))
}

/** "Audiencia constitucional: mañana a las 10:23". */
@Composable
fun hearingLine(audiencia: Audiencia, today: LocalDate): String =
    stringResource(
        R.string.audiencia_line,
        stringResource(audiencia.tipo.labelRes()),
        hearingWhen(LocalResources.current, audiencia, today),
    )
