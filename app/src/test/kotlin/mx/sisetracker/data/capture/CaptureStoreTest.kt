package mx.sisetracker.data.capture

import java.nio.file.Files
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
import mx.sisetracker.data.settings.SettingsStore
import mx.sisetracker.testing.InMemoryPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStoreTest {
    private val root = Files.createTempDirectory("captures").toFile()
    private val settings = SettingsStore(InMemoryPreferences())
    private val store = CaptureStore(root.resolve("captures"), root.resolve("shared"), settings, "0.1.0")

    @Test
    fun `nothing is saved while capture is off`() = runTest {
        store.save("circuitos_cir1", "<html>")

        assertEquals(0, store.count())
        assertNull(store.zip())
    }

    @Test
    fun `saves pages as UTF-8 and zips them with a readme`() = runTest {
        settings.setCaptureEnabled(true)

        store.save("circuitos_cir5", "<td>QUINTO CIRCUITO</td> Comisión")
        store.save("expedienteytipo_form_767", "<form>")
        store.save("circuitos_cir5", "<td>QUINTO CIRCUITO</td> Comisión (again)")

        assertEquals(2, store.count())
        val zip = checkNotNull(store.zip())
        ZipFile(zip).use { file ->
            val names = file.entries().toList().map { it.name }
            assertEquals(listOf("README.txt", "circuitos_cir5.html", "expedienteytipo_form_767.html"), names)
            val page = file.getInputStream(file.getEntry("circuitos_cir5.html")).readBytes().toString(Charsets.UTF_8)
            assertEquals("<td>QUINTO CIRCUITO</td> Comisión (again)", page)
            val readme = file.getInputStream(file.getEntry("README.txt")).readBytes().toString(Charsets.UTF_8)
            assertTrue(readme.contains("0.1.0"))
        }

        store.clear()
        assertEquals(0, store.count())
    }

    @Test
    fun `names can't escape the capture folder`() = runTest {
        settings.setCaptureEnabled(true)

        store.save("../../etc/x", "<html>")

        assertEquals(listOf(".._.._etc_x.html"), root.resolve("captures").list()?.toList())
    }
}
