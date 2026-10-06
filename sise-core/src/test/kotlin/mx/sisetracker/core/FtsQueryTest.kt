package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FtsQueryTest {
    @Test
    fun `every word becomes a required prefix`() {
        assertEquals("\"suspensión*\" \"defin*\"", FtsQuery.from("suspensión defin"))
    }

    @Test
    fun `expediente numbers split into their parts`() {
        assertEquals("\"1183*\" \"2025*\"", FtsQuery.from("1183/2025"))
    }

    @Test
    fun `operators and quotes can't break the query`() {
        assertEquals("\"amparo*\" \"OR*\" \"NEAR*\"", FtsQuery.from("\"amparo\" OR NEAR(* -x"))
    }

    @Test
    fun `short words and empty input are dropped`() {
        assertEquals("\"de*\" \"plano*\"", FtsQuery.from("a y de plano"))
        assertNull(FtsQuery.from("  "))
        assertNull(FtsQuery.from("a y"))
        assertNull(FtsQuery.from("*()\""))
    }

    @Test
    fun `repeated words count once`() {
        assertEquals("\"plazo*\"", FtsQuery.from("plazo plazo"))
    }
}
