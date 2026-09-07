package app.readribbon.core

import kotlinx.serialization.Serializable

/**
 * A verse address. Addresses are not scores (build book §01): "Mark 4:9" is
 * an address, and notes pin to verse addresses rather than text offsets so
 * that a note lands on the same verse in either person's translation (§2.6).
 */
@Serializable
data class VerseAddress(
    val bookID: String,
    val chapter: Int,
    val verse: Int,
) : Comparable<VerseAddress> {

    /** "Mark 4:9" — "Psalm 23:1" for the Psalter. */
    val formatted: String
        get() {
            val name = Bible.book(bookID)?.referenceName ?: bookID
            return "$name $chapter:$verse"
        }

    /** "Mark 4" — "Psalm 23" for the Psalter. */
    val chapterFormatted: String
        get() {
            val name = Bible.book(bookID)?.referenceName ?: bookID
            return "$name $chapter"
        }

    // Swift's `description` (CustomStringConvertible). Kotlin's `toString` is
    // the same hook, and declaring it here replaces the data class default.
    override fun toString(): String = formatted

    // Swift's `static func <`. A book no longer in the table sorts after every
    // real one (`firstIndex(...) ?? .max`), so an address never disappears out
    // of the front of a sorted list.
    override fun compareTo(other: VerseAddress): Int {
        if (bookID != other.bookID) {
            val l = Bible.books.indexOfFirst { it.id == bookID }.takeIf { it >= 0 } ?: Int.MAX_VALUE
            val r = Bible.books.indexOfFirst { it.id == other.bookID }.takeIf { it >= 0 } ?: Int.MAX_VALUE
            return l.compareTo(r)
        }
        if (chapter != other.chapter) return chapter.compareTo(other.chapter)
        return verse.compareTo(other.verse)
    }
}

/**
 * A contiguous run of verses within one chapter. Highlights snap to verse
 * boundaries by default (S06), and a drag never crosses a chapter.
 */
@Serializable
data class VerseRange(
    val bookID: String,
    val chapter: Int,
    var startVerse: Int,
    var endVerse: Int,
) {

    init {
        // Swift's memberwise init normalises the two ends (`min`/`max`), so a
        // drag made upwards is stored exactly as one made downwards. Doing it
        // here rather than in a factory means every route in gets it: the
        // constructor, `copy()`, and a decode off the wire.
        val low = minOf(startVerse, endVerse)
        val high = maxOf(startVerse, endVerse)
        startVerse = low
        endVerse = high
    }

    /** The one-verse range at [address] — Swift's `init(_ address:)`. */
    constructor(address: VerseAddress) :
        this(address.bookID, address.chapter, address.verse, address.verse)

    val start: VerseAddress get() = VerseAddress(bookID = bookID, chapter = chapter, verse = startVerse)

    val verses: IntRange get() = startVerse..endVerse

    operator fun contains(address: VerseAddress): Boolean =
        address.bookID == bookID && address.chapter == chapter && verses.contains(address.verse)

    fun overlaps(other: VerseRange): Boolean =
        bookID == other.bookID && chapter == other.chapter &&
            // Kotlin's IntRange has no `overlaps`. For two closed ranges it is
            // this comparison, endpoints included, exactly as Swift's is.
            startVerse <= other.endVerse && other.startVerse <= endVerse

    /** "Mark 4:9" for one verse, "Mark 4:9–11" for a run. */
    val formatted: String
        get() {
            val name = Bible.book(bookID)?.referenceName ?: bookID
            if (startVerse == endVerse) return "$name $chapter:$startVerse"
            return "$name $chapter:$startVerse–$endVerse"
        }

}
