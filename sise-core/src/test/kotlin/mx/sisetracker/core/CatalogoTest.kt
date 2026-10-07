package mx.sisetracker.core

import org.jsoup.Jsoup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** The bundled catalog against the crawl's saved pages (fixtures README, "Expected values: the October 2026 crawl"). */
class CatalogoTest {
    private val allOrganismos = (1..32).flatMap { Catalogo.organos(it.toString()) }.map { it.id }.toSet()

    @ParameterizedTest
    @MethodSource("circuitos")
    fun `each circuit's bundled list is its saved page, in order`(num: String) {
        val html = Fixtures.load("circuitos_cir$num.html")
        val page = OrganoListParser.parse(html)
        val circuito = checkNotNull(Circuitos.byNum(num))

        assertEquals(page.organos, Catalogo.organos(num))
        assertEquals(page.circuitoName, circuito.portalName)
        val hidden = Jsoup.parse(html).select("input[type=hidden]").associate { it.attr("name") to it.attr("value") }
        assertEquals(circuito.portalCir, hidden["Circuito"])
        assertEquals(circuito.portalName, hidden["CircuitoName"])
    }

    @Test
    fun `949 distinct organos in 1083 list entries`() {
        assertEquals(949, allOrganismos.size)
        assertEquals(1083, (1..32).sumOf { Catalogo.organos(it.toString()).size })
        assertEquals(184, Catalogo.organos("1").size)
        assertEquals(11, Catalogo.organos("32").size)
        // Plenos Regionales are listed in every circuit of their region.
        assertEquals(listOf("1", "21"), Catalogo.entries("4612").map { it.circuito }.filter { it == "1" || it == "21" })
        assertEquals(emptyList<Organo>(), Catalogo.organos("33"))
    }

    @Test
    fun `every form's tipos are its name's class list, except 10 organos with none`() {
        val entries = allOrganismos.map { Catalogo.entries(it).first() }
        entries.filter { it.tipos != null }.forEach { assertEquals(it.organo.clase, it.tipos, it.organo.name) }
        assertEquals(
            listOf("1275", "1277", "1288", "1293", "6315", "6316", "6317", "6318", "6319", "6320"),
            entries.filter { it.tipos == null }.map { it.organo.id }.sorted(),
        )
    }

    @ParameterizedTest
    @MethodSource("forms")
    fun `bundled tipos equal every saved form`(file: String) {
        val organismo = checkNotNull(Regex("""_(\d+)\.html$""").find(file)).groupValues[1]
        val form = SearchFormParser.parse(Fixtures.load(file))

        assertEquals(form.tipoAsuntoOptions.map { it.value to it.label }, Catalogo.tiposDeAsunto(organismo)?.map { it.value to it.label })
    }

    @Test
    fun `organos without tipos and the Unidad de Instruccion`() {
        assertEquals(emptyList<FormOption>(), Catalogo.tiposDeAsunto("1288"))
        assertEquals(emptyList<FormOption>(), Catalogo.tiposDeAsunto("6315"))
        assertEquals(listOf("136", "135", "134"), Catalogo.tiposDeAsunto("6207")?.map { it.value })
        assertEquals(OrganoClase.CONFLICTOS_LABORALES, Catalogo.entries("6207").single().organo.clase)
        assertNull(Catalogo.tiposDeAsunto("999999"))
    }

    @Test
    fun `procedimientos equal the Accion=2 reloads`() {
        listOf("125", "126").forEach { tipo ->
            val reload = SearchFormParser.parse(Fixtures.load("expedienteytipo_accion2_4343_tipo$tipo.html"))
            assertEquals(
                reload.tipoProcedimientoOptions.map { it.value to it.label },
                Catalogo.tiposDeProcedimiento(tipo)?.map { it.value to it.label },
            )
        }
        assertEquals(11, Catalogo.tiposDeProcedimiento("125")?.size)
        assertEquals(12, Catalogo.tiposDeProcedimiento("126")?.size)
        assertNull(Catalogo.tiposDeProcedimiento("6"))
    }

    companion object {
        @JvmStatic
        fun circuitos() = (1..32).map(Int::toString)

        @JvmStatic
        fun forms() = listOf(
            "4157", "10", "41", "534", "726", "727", "728", "721", "1288",
            "4", "500", "4343", "4345", "3986", "4260", "930", "6207", "4386", "6315",
        ).map { "expedienteytipo_form_$it.html" }
    }
}
