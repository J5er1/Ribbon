package app.readribbon.core

import kotlinx.serialization.Serializable

/**
 * The eight inks — highlight and identity colors (brand brief §8, unchanged by
 * the build book). Eight is deliberate: enough for a room of five to be
 * distinct with real choice left over, while staying separable at a 6pt dot.
 *
 * Chartreuse is not among them and never becomes selectable: it is the
 * brand's, not the user's.
 */
@Serializable
enum class Ink {
    crimson,
    clay,
    ochre,
    moss,
    teal,
    indigo,
    plum,
    rose;

    /** Hex value against the dark (primary) ground. */
    val darkHex: String
        get() = when (this) {
            crimson -> "C9584E"
            clay -> "C87A46"
            ochre -> "DCA846"
            moss -> "8AA77B"
            teal -> "63A09A"
            indigo -> "7297CE"
            plum -> "B3849E"
            rose -> "CE6B84"
        }

    /**
     * Hex value against the light (paper) ground. The light palette is an
     * open question (build book §16.1); these are the brief's values, kept
     * so nothing has to be invented later.
     */
    val lightHex: String
        get() = when (this) {
            crimson -> "A2332C"
            clay -> "9A5430"
            ochre -> "8C6412"
            moss -> "4F6B45"
            teal -> "2F6360"
            indigo -> "3A578A"
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
