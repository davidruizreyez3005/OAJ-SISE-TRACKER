package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Classes and materias of the Primer Circuito's órganos (`circuitos_cir1.html`). */
class OrganoClaseTest {
    private val organos = OrganoListParser.parse(Fixtures.load("circuitos_cir1.html")).organos
    private fun organo(id: String) = organos.single { it.id == id }

    @Test
    fun `class counts`() {
        assertEquals(
            mapOf(
                OrganoClase.JUZGADO_DISTRITO to 79,
                OrganoClase.COLEGIADO_CIRCUITO to 70,
                OrganoClase.COLEGIADO_APELACION to 4,
                OrganoClase.TRIBUNAL_LABORAL to 18,
                OrganoClase.PLENO_REGIONAL to 5,
                OrganoClase.CONFLICTOS_LABORALES to 1,
                OrganoClase.OTRO to 7,
            ),
            organos.groupingBy { it.clase }.eachCount(),
        )
        organos.forEach { assertEquals(it.kind, it.clase.kind, it.name) }
    }

    @Test
    fun `classes from names`() {
        assertEquals(OrganoClase.JUZGADO_DISTRITO, organo("767").clase)
        assertEquals(OrganoClase.COLEGIADO_CIRCUITO, organo("18").clase)
        assertEquals(OrganoClase.COLEGIADO_CIRCUITO, organo("1372").clase)
        assertEquals(OrganoClase.COLEGIADO_APELACION, organo("4343").clase)
        assertEquals(OrganoClase.TRIBUNAL_LABORAL, organo("3986").clase)
        assertEquals(OrganoClase.TRIBUNAL_LABORAL, organo("4272").clase)
        assertEquals(OrganoClase.PLENO_REGIONAL, organo("4386").clase)
        assertEquals(OrganoClase.CONFLICTOS_LABORALES, organo("930").clase)
        assertEquals(OrganoClase.OTRO, organo("6207").clase)
        assertEquals(OrganoClase.OTRO, organo("6315").clase)
        // Former name of the apelación tribunals, still used in older lists.
        assertEquals(OrganoClase.COLEGIADO_APELACION, OrganoClase.fromName("Primer Tribunal Unitario del Segundo Circuito"))
    }

    @Test
    fun `materia counts among juzgados and colegiados de circuito`() {
        fun counts(clase: OrganoClase) =
            organos.filter { it.clase == clase }.flatMap { it.materias }.groupingBy { it }.eachCount()

        assertEquals(
            mapOf(
                Materia.ADMINISTRATIVA to 20,
                Materia.CIVIL to 20,
                Materia.TRABAJO to 9,
                Materia.PENAL to 26,
                Materia.MERCANTIL to 8,
                Materia.MIXTA to 2,
            ),
            counts(OrganoClase.JUZGADO_DISTRITO),
        )
        assertEquals(
            mapOf(
                Materia.ADMINISTRATIVA to 26,
                Materia.CIVIL to 16,
                Materia.TRABAJO to 16,
                Materia.PENAL to 10,
                Materia.MIXTA to 2,
            ),
            counts(OrganoClase.COLEGIADO_CIRCUITO),
        )
    }

    @Test
    fun `materias from names`() {
        assertEquals(setOf(Materia.PENAL), organo("767").materias)
        assertEquals(setOf(Materia.PENAL), organo("1194").materias) // Ejecución de Penas
        assertEquals(setOf(Materia.PENAL), organo("555").materias) // Procesos Penales Federales
        assertEquals(setOf(Materia.CIVIL, Materia.MERCANTIL), organo("4002").materias)
        assertEquals(setOf(Materia.MERCANTIL), organo("4157").materias)
        assertEquals(setOf(Materia.ADMINISTRATIVA), organo("1301").materias)
        assertEquals(setOf(Materia.MIXTA), organo("949").materias) // Centro Auxiliar
        assertEquals(setOf(Materia.TRABAJO), organo("4260").materias)
        assertEquals(setOf(Materia.CIVIL, Materia.ADMINISTRATIVA), organo("4341").materias)
        assertEquals(setOf(Materia.PENAL, Materia.TRABAJO), organo("4385").materias)
        assertEquals(setOf(Materia.ADMINISTRATIVA), organo("4393").materias)
        // Administrative bodies have no materia, whatever their name says.
        assertEquals(emptySet<Materia>(), organo("930").materias)
        assertEquals(emptySet<Materia>(), organo("6318").materias)
        assertEquals(emptySet<Materia>(), organo("6207").materias)
        assertEquals(
            setOf(Materia.PENAL, Materia.ADMINISTRATIVA),
            Materia.fromName("Primer Tribunal Colegiado en Materias Penal y Administrativa del Segundo Circuito"),
        )
    }
}
