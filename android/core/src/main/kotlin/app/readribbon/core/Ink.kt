package app.readribbon.core

import kotlinx.serialization.Serializable

/**
 * The twelve inks — highlight and identity colors. The brief's eight (§8),
 * and four more so that a room of twelve has an ink for everyone (A70):
 * bronze, jade, sky and iris, each measured to sit at least as far from its
 * nearest neighbour as plum already sits from rose, on both grounds, so a
 * 6pt dot is still told apart. They are listed around the colour wheel,
 * which is the order every picker shows them in.
 *
 * Chartreuse is not among them and never becomes selectable: it is the
 * brand's, not the user's.
 */
@Serializable
enum class Ink {
    crimson,
    clay,
    ochre,
    bronze,
    moss,
    jade,
    teal,
    sky,
    indigo,
    iris,
    plum,
    rose;

    /** Hex value against the dark (primary) ground. */
    val darkHex: String
        get() = when (this) {
            crimson -> "C9584E"
            clay -> "C87A46"
            ochre -> "DCA846"
            bronze -> "98853B"
            moss -> "8AA77B"
            jade -> "52B88D"
            teal -> "63A09A"
            sky -> "21A8CE"
            indigo -> "7297CE"
            iris -> "BA95E9"
            plum -> "B3849E"
            rose -> "CE6B84"
        }

    /**
     * Hex value against the light (paper) ground. The light palette is an
     * open question (build book §16.1); the first eight are the brief's
     * values, kept so nothing has to be invented later, and the four added
     * in A70 each hold 4.5:1 against the paper.
     */
    val lightHex: String
        get() = when (this) {
            crimson -> "A2332C"
            clay -> "9A5430"
            ochre -> "8C6412"
            bronze -> "5E5435"
            moss -> "4F6B45"
            jade -> "0A7959"
            teal -> "2F6360"
            sky -> "27728F"
            indigo -> "3A578A"
            iris -> "805BA1"
            plum -> "74445D"
            rose -> "9E4059"
        }

    /**
     * Lowercase display name, matching the voice rules (small caps do the
     * capitalization work in the interface, not the string).
     */
    val displayName: String get() = name

    companion object {
        /** The inks not yet claimed by a membership, in canonical order. */
        fun remaining(taken: Iterable<Ink>): List<Ink> {
            val taken = taken.toSet()
            return entries.filter { !taken.contains(it) }
        }
    }
}
