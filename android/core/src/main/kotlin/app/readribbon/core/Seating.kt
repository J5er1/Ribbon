package app.readribbon.core

// The room's people, seated round the fire (A19), in a room of up to twelve
// (A70).
//
// Six faces sit in one row, as they always have: a row of faces wrapped onto
// two lines reads as a list, not as a circle round a fire, so up to six the
// seats give up a little each rather than wrap. Past six one row cannot hold
// them at the size a finger is owed, and a row that scrolls hides half the
// room, so they sit in two rows instead, split as evenly as they go, the
// longer row nearer the fire. No row is ever labelled and nothing is counted.
//
// The port of Seating.swift, case for case; SeatingTests.swift and
// SeatingTest.kt hold the two to the same answers.

object Seating {
    /** The most faces one row holds. */
    const val perRow = 6

    /**
     * How many places each row holds, the row nearest the fire first.
     * `places` is everyone drawn, plus the open seat when there is one.
     */
    fun rows(places: Int): List<Int> {
        if (places <= 0) return emptyList()
        val count = (places + perRow - 1) / perRow
        val each = places / count
        val longer = places % count
        return (0 until count).map { if (it < longer) each + 1 else each }
    }
}
