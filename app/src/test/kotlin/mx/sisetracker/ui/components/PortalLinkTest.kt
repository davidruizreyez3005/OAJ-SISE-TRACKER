package mx.sisetracker.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalLinkTest {
    @Test
    fun `the app confirms a copy itself only before Android 13`() {
        assertTrue(showsOwnCopyConfirmation(26))
        assertTrue(showsOwnCopyConfirmation(32))
        assertFalse(showsOwnCopyConfirmation(33))
        assertFalse(showsOwnCopyConfirmation(36))
    }
}
