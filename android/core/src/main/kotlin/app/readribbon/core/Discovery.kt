@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

// Being found (A72): a person who chooses to be found, a room that everyone
// in it has opened, and a stranger asking to join. The server decides who
// may see what (supabase/migrations/20261016180000_ribbon_being_found.sql);
// this file holds the shapes the phones draw and the few rules they apply
// themselves, so both phones apply them the same way.
//
// What is never here: a count. Not of members, not of asks, not of rooms.
// A room is open or it is not, full or it is not, and the people in it who
// chose to be found are faces, not a number.
//
// The port of Discovery.swift, case for case; DiscoveryTests.swift and
// DiscoveryTest.kt hold the two to the same answers.

/**
 * One line in your own words, on a profile or a room. Trimmed, inner runs
 * of whitespace closed up, and cut at a word near the limit; empty is no
 * line at all.
 */
object OwnLine {
    /** A profile's line or a room's (the server holds them to 140). */
    const val limit = 140

    /** What someone says when they ask to join (the server holds it to 280). */
    const val askLimit = 280

    fun clean(text: String?, limit: Int = OwnLine.limit): String? {
        if (text == null) return null
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val joined = words.joinToString(" ")
        if (joined.length <= limit) return joined
        // Cut at the last space that fits, so a word is never halved; a
        // single word longer than the limit is cut where the limit falls.
        val cut = joined.take(limit)
        val space = cut.lastIndexOf(' ')
        return if (space >= 0) cut.substring(0, space) else cut
    }
}

/**
 * Whether you can be found, and the line people see. Kept on this phone and
 * pushed to your profile; off unless you turn it on, and only once you have
 * said you are thirteen or older.
 */
@Serializable
data class BeingFound(
    val findable: Boolean = false,
    val line: String? = null,
    /** When you said you are thirteen or older. Null until you have. */
    val saidThirteenAt: Instant? = null,
) {
    /** Turning it on is only possible once thirteen has been said. */
    val canBeFindable: Boolean get() = saidThirteenAt != null
}

/** A face in a found room, or a found person: only what they chose to show. */
@Serializable
data class PublicFace(
    val id: Uuid,
    val name: String,
    val line: String? = null,
    val portraitPath: String? = null,
)

/** A room you could ask to join, as `find_rooms` describes it. */
data class OpenRoom(
    val id: Uuid,
    val name: String? = null,
    val line: String? = null,
    val full: Boolean = false,
    /** You have asked, and the ask is still standing. */
    val asked: Boolean = false,
    /** The book it is reading, if it is reading one. */
    val bookID: String? = null,
    /** The fire as stored; the phone's fire engine reads a state from it. */
    val fire: Handiwork? = null,
    /** The people in it who chose to be found, in the order they joined. */
    val faces: List<PublicFace> = emptyList(),
) {
    /**
     * The fire's state, as a glance would report it. Quiet days are the
     * room's own business, so a found room's fire is never shown banked.
     */
    fun fireState(now: Instant): FireState? = fire?.state(now)

    /** The room's own name, if it has one worth showing. */
    val shownName: String? get() = OwnLine.clean(name, limit = 80)

    /**
     * When the room has no name: the first names of up to three people who
     * chose to be found, for the phone to say in its own words.
     */
    val firstNames: List<String>
        get() = faces.take(3).map { face ->
            face.name.split(' ').firstOrNull { it.isNotEmpty() } ?: face.name
        }
}

/** Someone asking to join a room you are in. */
data class Knock(
    val roomID: Uuid,
    val person: PublicFace,
    /** What they said when they asked, if anything. */
    val line: String? = null,
    val askedAt: Instant,
) {
    val id: String get() = "$roomID/${person.id}"

    fun stands(now: Instant): Boolean = now - askedAt < lasts

    companion object {
        /** How long an ask stands before it lapses without anyone saying no. */
        val lasts = 14.days

        /**
         * The asks worth showing: standing ones, oldest first, so nobody who
         * asked earlier is pushed down by somebody who asked later.
         */
        fun standing(knocks: List<Knock>, now: Instant): List<Knock> =
            knocks.filter { it.stands(now) }.sortedBy { it.askedAt }
    }
}

/**
 * Whether a room can be found, from your side of it. A room is open only
 * while everyone in it has said yes, the way closing a room asks everyone
 * (§6.8). Who has not said yes is never named: a name attached to
 * something not done is an accusation.
 */
enum class RoomOpenness {
    /** You have not said yes. */
    closed,

    /** You have; somebody else has not yet. */
    waitingForOthers,

    /** Everyone has. The room can be found and asked to join. */
    open;

    companion object {
        fun of(memberships: List<Membership>, me: Uuid): RoomOpenness {
            val mine = memberships.firstOrNull { it.personID == me }
            if (mine == null || !mine.wouldOpen) return closed
            return if (memberships.all { it.wouldOpen }) open else waitingForOthers
        }
    }
}

/**
 * Turning what someone types into the books it could mean, so "mark",
 * "Mark 4" or "1 cor" find the rooms reading them. Matches a book's name or
 * its id from the start, ignoring case and a trailing chapter.
 */
object BookSearch {
    fun books(matching: String): List<String> {
        val words = matching.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // A trailing chapter or verse ("4", "4:12") says nothing about which
        // book; drop it, and anything after it.
        val bookWords = mutableListOf<String>()
        for (word in words) {
            if (word.first().isDigit() && bookWords.isNotEmpty()) break
            bookWords.add(word)
        }
        val wanted = bookWords.joinToString(" ")
        if (wanted.length < 2) return emptyList()
        return Bible.books.mapNotNull { book ->
            if (book.name.lowercase().startsWith(wanted) || book.id.lowercase() == wanted) book.id else null
        }
    }
}
