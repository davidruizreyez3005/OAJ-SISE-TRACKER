package mx.sisetracker.data.transfer

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.sisetracker.R
import mx.sisetracker.core.CaseUrl
import mx.sisetracker.data.cases.CaseRepository

/**
 * "Exportar" and "Importar" for the saved list: a plain text file with one
 * portal link per line, so cases move to another phone or survive a
 * reinstall. Links only: no case text and no notes (hard rule 6). Neither
 * direction makes a portal request.
 */
class CaseListTransfer(
    private val context: Context,
    private val cases: CaseRepository,
    private val shareDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Writes the list to a file in [shareDir] (only the latest is kept); null when nothing is saved. */
    suspend fun exportFile(): File? = withContext(Dispatchers.IO) {
        val links = cases.exportLinks()
        if (links.isEmpty()) return@withContext null
        val day = Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate()
        val res = context.resources
        val text = buildString {
            appendLine(res.getQuantityString(R.plurals.export_file_header, links.size, links.size, day.format(shownDate)))
            appendLine(res.getString(R.string.export_file_help))
            appendLine()
            links.forEach(::appendLine)
        }
        shareDir.mkdirs()
        shareDir.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.forEach(File::delete)
        File(shareDir, "$FILE_PREFIX${day.format(fileDate)}.txt").apply { writeText(text, Charsets.UTF_8) }
    }

    /** The case links in a picked or shared file; empty if it can't be read or has none. */
    suspend fun readLinks(uri: Uri): List<CaseUrl> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { CaseUrl.findAllIn(readText(it)) }.orEmpty()
        } catch (e: IOException) {
            emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** At most [MAX_BYTES]: an exported list of thousands of cases is far smaller. */
    private fun readText(stream: InputStream): String {
        val buffer = ByteArray(MAX_BYTES)
        var size = 0
        while (size < MAX_BYTES) {
            val read = stream.read(buffer, size, MAX_BYTES - size)
            if (read < 0) break
            size += read
        }
        return String(buffer, 0, size, Charsets.UTF_8)
    }

    companion object {
        const val MIME_TYPE = "text/plain"
        private const val FILE_PREFIX = "sise-expedientes-"
        private const val MAX_BYTES = 2 * 1024 * 1024
        private val shownDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val fileDate = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
