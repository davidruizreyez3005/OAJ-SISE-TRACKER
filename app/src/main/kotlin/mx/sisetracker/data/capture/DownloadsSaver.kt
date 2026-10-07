package mx.sisetracker.data.capture

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Saves the captures zip where the user can find it without sharing it to an
 * app. Android 10+ writes straight to Downloads through MediaStore (no
 * permission); older versions copy it to a file the user picks
 * (`ACTION_CREATE_DOCUMENT`, which opens on Downloads by default).
 */
class DownloadsSaver(private val context: Context) {

    val canSaveDirectly: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Copies [file] into Downloads under its own name; false if that failed. */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToDownloads(file: File): Boolean = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        try {
            copy(file, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            true
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Copies [file] to a document the user picked; false if that failed. */
    suspend fun saveTo(uri: Uri, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            copy(file, uri)
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun copy(file: File, uri: Uri) {
        val out = context.contentResolver.openOutputStream(uri) ?: throw IOException("No output stream for $uri")
        out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }

    companion object {
        const val MIME_TYPE = "application/zip"
    }
}
