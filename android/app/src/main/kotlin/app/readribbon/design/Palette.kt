package app.readribbon.design

import androidx.compose.ui.graphics.Color
import app.readribbon.core.Ink

// The room's palette: chartreuse on true black with ivory (build book §00 —
// this supersedes the brief's crimson-and-lamp scheme). Dark is the hero
// condition; there is no light mode at launch (S18 — a light mode arrives
// only when the paper palette is resolved, open question §16.1).
//
// Chartreuse is chrome: hairlines, the follow thread, focus. The fire stays
// warm — it is "a warm object" and an amber thing in a chartreuse-accented
// room, never a chartreuse flame.

object Palette {
    /** The unlit ground. True black with a breath of warmth — never
     *  blue-black, which reads as a device instead of a room. */
    val ground = Color(0xFF0B0B0A)

    /** Cards and sheets. */
    val surface = Color(0xFF14120D)

    /** Bars and chips. */
    val raised = Color(0xFF1D1A13)

    /** Ivory — primary text. */
    val text = Color(0xFFF3F0E6)

    /** Metadata, small caps, quiet lines. */
    val muted = Color(0xFF8E8271)

    /** Borders and dividers. */
    val rule = Color(0xFF292118)

    /** The brand accent. Never a colour a user can pick. */
    val chartreuse = Color(0xFFD6E45C)

    // The fire's warmth — lamp and clay ambers from the brief, kept for the
    // one object in the room that must read warm.
    val flameBright = Color(0xFFF3C778)
    val flameCore = Color(0xFFE9A63F)
    val flameDeep = Color(0xFFC87A46)
    val coal = Color(0xFF8A4A24)
    val coalDim = Color(0xFF4A2714)

    /** The wash opacity for highlights (§4.5). */
    const val HIGHLIGHT_WASH = 0.24f
}

/** The ink's colour against the dark ground. */
val Ink.color: Color get() = Color(("ff" + darkHex).toLong(16))

/** The ink's colour against the light (paper) ground — unused until the
 *  light palette is resolved (§16.1), kept so nothing has to be invented. */
val Ink.lightColor: Color get() = Color(("ff" + lightHex).toLong(16))
