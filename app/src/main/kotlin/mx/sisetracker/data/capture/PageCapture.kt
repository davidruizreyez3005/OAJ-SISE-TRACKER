package mx.sisetracker.data.capture

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mx.sisetracker.data.settings.SettingsStore

/**
 * Receives the raw catalog pages the app loads, so they can be kept as
 * fixtures. Only catalog pages ever reach it (órgano lists, search forms and
 * Accion=2 reloads): never case pages or síntesis, which may carry names
 * (hard rule 6).
 */
fun interface PageCapture {
    suspend fun save(name: String, html: String)

    companion object {
        val None = PageCapture { _, _ -> }
    }
}

/**
 * "Capturar páginas del catálogo" (diagnostics): while on, each catalog page
 * the user's own actions load is saved to app-private storage under a fixture
 * name (`circuitos_cir5.html`, `expedienteytipo_form_767.html`…), and can be
 * shared as a zip. It never loads anything by itself: what's captured is
 * exactly what the user opened.
 */
class CaptureStore(
    private val dir: File,
    private val shareDir: File,
    private val settings: SettingsStore,
    private val versionName: String,
) : PageCapture {

    override suspend fun save(name: String, html: String) {
        if (!settings.captureEnabled.first()) return
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            File(dir, safeName(name) + ".html").writeText(html, Charsets.UTF_8)
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { files().size }

    suspend fun clear() = withContext(Dispatchers.IO) { files().forEach(File::delete) }

    /** A zip of every capture plus a short README, ready to share; null if there are none. */
    suspend fun zip(): File? = withContext(Dispatchers.IO) {
        val files = files().sortedBy { it.name }
        if (files.isEmpty()) return@withContext null
        shareDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val zip = File(shareDir, "sise-capturas-$stamp.zip")
        shareDir.listFiles()?.filter { it.name.startsWith("sise-capturas-") }?.forEach(File::delete)
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("README.txt"))
            out.write(readme(files).toByteArray(Charsets.UTF_8))
            out.closeEntry()
            files.forEach { file ->
                out.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        zip
    }

    private fun files(): List<File> = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".html") }.orEmpty()

    private fun readme(files: List<File>): String = buildString {
        appendLine("SISE Tracker $versionName: catalog pages captured on the device.")
        appendLine("Decoded from windows-1252 and saved as UTF-8, exactly as the app received them")
        appendLine("(hidden inputs included, unlike Chrome page saves). No case pages or síntesis.")
        appendLine()
        files.forEach { appendLine(it.name) }
    }

    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
