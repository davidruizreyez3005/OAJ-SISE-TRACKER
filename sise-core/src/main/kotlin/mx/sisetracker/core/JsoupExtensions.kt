package mx.sisetracker.core

import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/** Whitespace-normalized text of the element with [id], or "" when it's missing. */
internal fun Element.textById(id: String): String =
    getElementById(id)?.let { SiseText.normalizeSpace(it.text()) }.orEmpty()

/**
 * Text with its line breaks kept (`wholeText()`, not `text()`, which collapses
 * them). Any `<br>` counts as a line break too.
 */
internal fun Element.multilineText(): String {
    select("br").forEach { it.replaceWith(TextNode("\n")) }
    return SiseText.normalizeMultiline(wholeText())
}

/** The table's own rows, skipping those of nested tables. */
internal fun Element.tableRows(): List<Element> =
    children().flatMap { child ->
        when (child.tagName()) {
            "tr" -> listOf(child)
            "thead", "tbody", "tfoot" -> child.children().filter { it.tagName() == "tr" }
            else -> emptyList()
        }
    }

/** The row's own `td` cells. */
internal fun Element.cells(): List<Element> = children().filter { it.tagName() == "td" }

/**
 * A dropdown's options in page order, value and label exactly as given (labels
 * whitespace-normalized), skipping only placeholders: value `0`, `-1` or empty.
 */
internal fun Element.dropdownOptions(): List<FormOption> =
    select("option")
        .filterNot { isPlaceholderValue(it.attr("value")) }
        .mapIndexed { position, option ->
            FormOption(
                value = option.attr("value").trim(),
                label = SiseText.normalizeSpace(option.text()),
                position = position,
                selected = option.hasAttr("selected"),
            )
        }

private fun isPlaceholderValue(value: String): Boolean = value.trim().let { it.isEmpty() || it == "0" || it == "-1" }
