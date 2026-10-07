package mx.sisetracker.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Grids with no rows. The portal then shows a single message cell instead of
 * data. The markup below is verbatim from the live page of the tribunal case
 * 293/2026 (October 2026), spliced into the 1183/2025 fixture: that page had
 * neither resoluciones nor related cases, and failed to parse before.
 */
class EmptyGridsTest {
    private val base = Fixtures.load("vercaptura_1183-2025_amparo-indirecto.html")

    private val emptyResoluciones =
        """<table cellspacing="0" cellpadding="4" align="Center" rules="all" border="1" id="grvReporteSentencias" style="color:#180C3E;background-color:#9F8AE8;font-size:10pt;border-collapse:collapse;">
            <tr>
                <td colspan="5">No existen Sentencias asociadas para este expediente</td>
            </tr>
        </table>"""

    private val emptyRelacionados =
        """<table cellspacing="0" rules="all" border="1" id="grvAsuntosRelacionados" style="color:#180C3E;background-color:#9F8AE8;border-collapse:collapse;">
            <tr>
                <td>No existen Asuntos relacionados para este expediente</td>
            </tr>
        </table>"""

    /** Assumed: the acuerdos grid would use the same kind of row. */
    private val emptyAcuerdos =
        """<table id="grvAcuerdos"><tr><td colspan="6">No existen acuerdos para este expediente</td></tr></table>"""

    private fun replaceTable(html: String, id: String, replacement: String): String {
        val start = html.lastIndexOf("<table", html.indexOf("id=\"$id\""))
        val end = html.indexOf("</table>", start) + "</table>".length
        return html.substring(0, start) + replacement + html.substring(end)
    }

    private fun parse(html: String): CasePage = (CasePageParser.parse(html) as CaseLookup.Found).page

    @Test
    fun `no resoluciones and no related cases`() {
        val html = replaceTable(
            replaceTable(base, "grvReporteSentencias", emptyResoluciones),
            "grvAsuntosRelacionados",
            emptyRelacionados,
        )

        val page = parse(html)

        assertEquals("40612904", page.neun)
        assertTrue(page.resoluciones.isEmpty())
        assertTrue(page.asuntosRelacionados.isEmpty())
        assertEquals(28, page.acuerdos.size)
    }

    @Test
    fun `no acuerdos`() {
        val page = parse(replaceTable(base, "grvAcuerdos", emptyAcuerdos))

        assertTrue(page.acuerdos.isEmpty())
        assertEquals("40612904", page.neun)
    }

    /** Verbatim from 1068/2025 at órgano 721 (October 2026): a placeholder row instead of the message cell. */
    private val noRelatedPlaceholder =
        """<table cellspacing="0" rules="all" border="1" id="grvAsuntosRelacionados" style="color:#180C3E;background-color:#9F8AE8;border-collapse:collapse;">
            <tr> <th scope="col">Neun</th><th scope="col">Número de Expediente</th><th scope="col">Órgano Jurisdiccional</th><th scope="col">Fecha relación</th> </tr><tr> <td align="center" style="width:80px;"> <span id="grvAsuntosRelacionados_ctl02_lblContenido">0</span> </td><td align="justify" style="width:80px;"> <span id="grvAsuntosRelacionados_ctl02_lblNúmeroExpediente"></span> </td><td align="left" style="width:450px;"> <span id="grvAsuntosRelacionados_ctl02_lblOrgano">ASUNTO NO RELACIONADO</span> </td><td align="center" style="width:100px;"> <span id="grvAsuntosRelacionados_ctl02_lblFechaPresentacion"></span> </td> </tr>
        </table>"""

    @Test
    fun `the ASUNTO NO RELACIONADO placeholder row means no related cases`() {
        val page = parse(replaceTable(base, "grvAsuntosRelacionados", noRelatedPlaceholder))

        assertTrue(page.asuntosRelacionados.isEmpty())
        assertEquals(28, page.acuerdos.size)
    }
}
