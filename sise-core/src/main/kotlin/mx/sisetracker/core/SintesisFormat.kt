package mx.sisetracker.core

/** Presentation hints for síntesis text. The text itself is always shown as published. */
object SintesisFormat {
    private const val MAX_HEADING_LENGTH = 80

    /**
     * Short ALL-CAPS lines are section headings in long síntesis
     * (`SUSPENSIÓN DE PLANO`, `HABILITACIÓN.`). Long all-caps lines are quoted
     * tesis titles, not headings, and lines without letters (`[.]`) aren't
     * either.
     */
    fun isHeading(line: String): Boolean {
        val text = line.trim()
        if (text.length > MAX_HEADING_LENGTH) return false
        val letters = text.filter { it.isLetter() }
        return letters.length >= 3 && letters.all { it.isUpperCase() }
    }
}
