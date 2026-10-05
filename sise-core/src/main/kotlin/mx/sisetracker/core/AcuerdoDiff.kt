package mx.sisetracker.core

/** Finds the acuerdos published since the last check. */
object AcuerdoDiff {
    /**
     * Acuerdos on the page whose orden isn't stored yet: a set difference on
     * orden. Never compare against the highest stored orden (orden has gaps,
     * and a late-published one can fill a gap) or the displayed No.
     */
    fun newAcuerdos(storedOrdenes: Set<Int>, page: List<Acuerdo>): List<Acuerdo> =
        page.filter { it.orden !in storedOrdenes }
}
