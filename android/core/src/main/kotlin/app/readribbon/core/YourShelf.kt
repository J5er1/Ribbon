@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Your shelf (A66): every book you have finished, in every room you have
// read in, as one row of embers under your name on You. A room's shelf
// (S10) is that room's; this one is yours — what the printed keepsake
// would be if it were made of a person rather than of a room.
//
// It is a row of objects, never a tally. Nothing here is counted or
// returned as a count, and the screen draws no number near it (§17's first
// question). Each ember says what book it was and who it was read with,
// and that is all.
// A port of core/Sources/RibbonCore/YourShelf.swift, case for case.

/** Who a finished book was read with, as an ember on your shelf says it. */
sealed interface ShelfCompany {
    /** Nobody else is in that room now. */
    data object Alone : ShelfCompany

    /**
     * The others, in the order they came into the room, and whether there
     * are more than are named — said as "and others", never as a number.
     */
    data class People(val people: List<Uuid>, val andOthers: Boolean) : ShelfCompany

    /**
     * A room of three or more that has a name of its own is said by it:
     * "with the Thursday study" rather than a list of first names.
     */
    data class Room(val name: String) : ShelfCompany
}

object YourShelf {
    /** The most people an ember names before "and others". */
    const val named = 2

    /**
     * Every finished reading this phone holds, the first finished first — the
     * order the embers were made in. That includes the books of a room you
     * have since left: leaving says "You'll keep the books on your shelf"
     * (§6.8), and until there was a shelf of your own there was nowhere for
     * that to be true. Leaving takes the room and your membership; its
     * readings stay on the phone, and so on here.
     */
    fun embers(readings: List<Reading>): List<Reading> =
        readings
            .filter { it.isFinished }
            .sortedWith(
                compareBy<Reading> { it.finishedAt }
                    .thenBy { it.id.toString().lowercase() },
            )

    /**
     * Who a reading was read with: everybody else in its room now, in the
     * order they joined. A room of three or more with a name is said by its
     * name; otherwise the first [named] people are named, and "and others"
     * carries the rest. A room you have left has no members on this phone any
     * more, and its books are simply yours: [ShelfCompany.Alone], said by
     * nothing.
     */
    fun company(
        reading: Reading,
        rooms: List<app.readribbon.core.Room>,
        memberships: List<Membership>,
        me: Uuid?,
    ): ShelfCompany {
        val others = memberships
            .filter { it.roomID == reading.roomID && it.personID != me }
            .sortedWith(
                compareBy<Membership> { it.joinedAt }
                    .thenBy { it.personID.toString().lowercase() },
            )
            .map { it.personID }
        if (others.isEmpty()) return ShelfCompany.Alone
        val name = rooms.firstOrNull { it.id == reading.roomID }?.name?.trim().orEmpty()
        if (others.size >= 2 && name.isNotEmpty()) return ShelfCompany.Room(name)
        return ShelfCompany.People(others.take(named), andOthers = others.size > named)
    }
}
