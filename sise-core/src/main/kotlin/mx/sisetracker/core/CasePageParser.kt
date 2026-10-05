package mx.sisetracker.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Parses the public case page, `vercaptura.aspx`. */
object CasePageParser {
    private val resolucionesCount = Regex("\\((\\d+)\\)")
    private val abrirVentana = Regex("AbrirVentana\\(\\s*'([^']*)'")
    private val partyPanelId = Regex("^pBody\\d+$")

    /**
     * [CaseLookup.NotFound] when `#lblNEUN` is present but empty. That is the
     * only signal: a real case can have zero acuerdos, so missing tables mean
     * nothing.
     *
     * @throws SiseParseException when the page doesn't look like a case page.
     */
    fun parse(html: String): CaseLookup {
        val document = Jsoup.parse(html, SiseUrls.CASE_PAGE)
        if (document.getElementById("lblNEUN") == null) {
            throw SiseParseException("Not a case page: no #lblNEUN")
        }
        val neun = document.textById("lblNEUN")
        if (neun.isEmpty()) return CaseLookup.NotFound
        val title = document.textById("lblNombreOrgano")
        return CaseLookup.Found(parseCase(document, neun, title))
    }

    private fun parseCase(document: Document, neun: String, title: String): CasePage =
        CasePage(
            neun = neun,
            organoName = OrganoTitle.organo(title),
            tipoAsuntoName = OrganoTitle.tipoAsunto(title),
            expediente = document.textById("lblNoExpedienteAsignado"),
            noControlOcc = document.textById("lblNoControlOCC"),
            resolucionesCount = resolucionesCount.find(document.textById("lblNumResultado"))
                ?.groupValues?.get(1)?.toIntOrNull(),
            acuerdos = parseAcuerdos(document),
            resoluciones = parseResoluciones(document, neun),
            asuntosRelacionados = parseAsuntosRelacionados(document),
            captura = parseCaptura(document),
        )

    private fun parseAcuerdos(document: Document): List<Acuerdo> {
        val table = document.getElementById("grvAcuerdos") ?: return emptyList()
        return table.tableRows().mapNotNull { row ->
            val cells = row.cells()
            if (cells.isEmpty()) return@mapNotNull null // header row
            if (cells.size < 6) throw SiseParseException("Acuerdos row has ${cells.size} cells, expected 6")
            // Only the link carries the arguments: the page also contains the
            // DoVerAcuerdo function definition, which must not be parsed.
            val href = cells[5].selectFirst("a[href^=\"javascript:DoVerAcuerdo\"]")?.attr("href")
                ?: throw SiseParseException("Acuerdos row without a DoVerAcuerdo link")
            Acuerdo(
                numero = SiseText.normalizeSpace(
                    (cells[0].selectFirst("span[id$=_lblContenido]") ?: cells[0]).text(),
                ),
                fechaAuto = SiseDates.parseGrid(cells[1].text()),
                tipoCuaderno = SiseText.normalizeSpace(cells[2].text()),
                fechaPublicacion = SiseDates.parseGrid(cells[3].text()),
                resumen = cells[4].multilineText(),
                link = DoVerAcuerdo.parse(href),
            )
        }
    }

    private fun parseResoluciones(document: Document, caseNeun: String): List<Resolucion> {
        val table = document.getElementById("grvReporteSentencias") ?: return emptyList()
        return table.tableRows().filter { it.cells().isNotEmpty() }.map { row ->
            val fecha = row.selectFirst("[id$=_lblFechaIngreso]")
                ?: throw SiseParseException("Resoluciones row without a fecha de ingreso")
            Resolucion(
                neun = row.selectFirst("[id$=_cmdAsuntoNeunid]")
                    ?.let { SiseText.normalizeSpace(it.text()) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: caseNeun,
                fechaIngreso = SiseDates.parseSpan(fecha.text()),
                tema = row.selectFirst("[id$=_lblTema]")?.multilineText().orEmpty(),
                archivoUrl = row.selectFirst("a[id$=_SentenciasLinkButton]")
                    ?.attr("onclick")
                    ?.let(::archivoUrl),
            )
        }
    }

    /**
     * The document link is in `onclick="AbrirVentana('http://…')"` (the href is
     * a postback). Jsoup already decodes `&amp;`; the replace guards against a
     * double-encoded attribute.
     */
    private fun archivoUrl(onclick: String): String? =
        abrirVentana.find(onclick)?.groupValues?.get(1)
            ?.replace("&amp;", "&")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun parseAsuntosRelacionados(document: Document): List<AsuntoRelacionado> {
        val table = document.getElementById("grvAsuntosRelacionados") ?: return emptyList()
        return table.tableRows().mapNotNull { row ->
            val cells = row.cells()
            if (cells.isEmpty()) return@mapNotNull null // header row
            // Spans by ID suffix (one of them has a non-ASCII "ú"), falling back
            // to the column.
            fun field(idSuffix: String, column: Int): String {
                val element = row.selectFirst("span[id$=$idSuffix]") ?: cells.getOrNull(column)
                return element?.let { SiseText.normalizeSpace(it.text()) }.orEmpty()
            }
            AsuntoRelacionado(
                neun = field("_lblContenido", 0),
                expediente = field("_lblNúmeroExpediente", 1),
                organo = field("_lblOrgano", 2),
                fechaRelacion = SiseDates.parseSpan(field("_lblFechaPresentacion", 3)),
            )
        }
    }

    /**
     * One accordion panel per party, each an identical copy of the case data:
     * parse the first and count them. Every section is a `table.div_principal`
     * whose title row is `tr.colapsable_vc`; each nested table below it is one
     * record of label/value rows.
     */
    private fun parseCaptura(document: Document): CapturaInfo {
        val vista = document.getElementById("pnlVista") ?: return CapturaInfo.EMPTY
        val panels = vista.select("div").filter { partyPanelId.matches(it.id()) }
        val firstPanel = panels.firstOrNull() ?: return CapturaInfo.EMPTY

        val entries = mutableListOf<CapturaEntry>()
        for (section in firstPanel.select("table.div_principal")) {
            val rows = section.tableRows()
            val title = rows.firstOrNull { it.hasClass("colapsable_vc") }
                ?.let { SiseText.normalizeSpace(it.text()) }
                ?: continue
            var group = 0
            val records = rows.filterNot { it.hasClass("colapsable_vc") }
                .flatMap { row -> row.cells().flatMap { cell -> cell.children().filter { it.tagName() == "table" } } }
            for (record in records) {
                val pairs = labelValuePairs(record).filterNot { (label, value) -> hiddenByPortal(label, value) }
                if (pairs.isEmpty()) continue
                pairs.forEach { (label, value) -> entries += CapturaEntry(title, group, label, value) }
                group++
            }
        }
        return CapturaInfo(entries = entries, partyCount = panels.size)
    }

    private fun labelValuePairs(record: Element): List<Pair<String, String>> =
        record.tableRows().mapNotNull { row ->
            val cells = row.cells()
            if (cells.size != 2) return@mapNotNull null // e.g. the empty tr.fila_agregada separator
            SiseText.normalizeSpace(cells[0].text()) to SiseText.normalizeSpace(cells[1].text())
        }

    /**
     * The portal's own script hides every cell containing "Observaciones"
     * (case-sensitive, like jQuery's `:contains`) inside the panels, and the
     * value cell next to a hidden label. The app hides the same data.
     */
    private fun hiddenByPortal(label: String, value: String): Boolean =
        label.contains(HIDDEN_TEXT) || value.contains(HIDDEN_TEXT)

    private const val HIDDEN_TEXT = "Observaciones"
}
