package mx.sisetracker.testing

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import java.io.File

/**
 * Saves a PNG of the node when `SISE_SCREENSHOTS_DIR` is set, to look at the
 * screens without a device. Does nothing otherwise (CI included).
 */
fun SemanticsNodeInteraction.saveScreenshot(name: String) {
    val dir = System.getenv("SISE_SCREENSHOTS_DIR")?.takeIf { it.isNotBlank() } ?: return
    val bitmap = captureToImage().asAndroidBitmap()
    File(dir).mkdirs()
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
