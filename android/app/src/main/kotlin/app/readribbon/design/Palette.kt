package app.readribbon.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.readribbon.core.Ink

// The room's palette.
//
// Ribbon's own is chartreuse on true black with ivory (build book §00 — this
// supersedes the brief's crimson-and-lamp scheme). Dark is the hero
// condition; there is no light mode at launch (S18 — a light mode arrives
// only when the paper palette is resolved, open question §16.1).
//
// **The room now takes its colour from the wallpaper by default** (deviation
// A20, owner's call). §12.2 declined dynamic colour outright; the owner's
// answer is that Material You was the point of building on Android at all,
// and that a room which quietly matches the phone it lives on is friendlier
// than one which insists on its own paint. So the six room roles below are
// wired to Android's dynamic dark scheme, unharmonised — whatever the
// wallpaper gives, Ribbon wears — and `Appearance` offers the way back to
// Ribbon's own chartreuse for anyone who wants it.
//
// Two things do not move, and both are the same reason: they are objects in
// the room rather than the room itself.
//
//   - **The fire.** It is "a single warm object" (§4.1) and its warmth is
//     the product, not chrome. A blue fire is not a fire. The flame ramp,
//     the coals, the smoke and the ash are fixed here whatever the
//     wallpaper says — a warm fire in a cool room is a better picture than
//     either alone, and it is the picture the brief describes.
//   - **The eight inks.** They are identity (§4.5): whose highlight this is.
//     Repainting them from a wallpaper would change who a mark belonged to.
//
// Everything a screen reads goes through [Palette], whose properties are
// composable getters over [LocalRoomColours]. That is deliberate: a colour
// read outside a composition is a colour that cannot follow the wallpaper,
// so the compiler refuses it rather than letting one screen quietly keep
// painting itself the old way.

/**
 * The six roles the room is built out of, plus the accent's contrast pair.
 *
 * One value class rather than six composition locals, so a screen can never
 * be halfway between two palettes.
 */
@Immutable
data class RoomColours(
    /** The unlit ground. Page background. */
    val ground: Color,
    /** Cards and sheets. */
    val surface: Color,
    /** Bars, chips, and anything lifted off a surface. */
    val raised: Color,
    /** Primary text. */
    val text: Color,
    /** Metadata, small caps, quiet lines. */
    val muted: Color,
    /** Borders and dividers. */
    val rule: Color,
    /** The single accent: hairlines, the follow thread, focus, the way in. */
    val accent: Color,
    /** What is legible *on* the accent. */
    val onAccent: Color,
    /**
     * Whether a card has to draw its own edge to be seen at all.
     *
     * Ribbon's own palette is the case this exists for, and it is not a
     * hypothetical: `Brand.surface` against `Brand.ground` is 1.05:1 and
     * `Brand.raised` is 1.13:1. Those numbers are *correct* — the room is
     * meant to be nearly flat, and a card that announced itself on that
     * ground would be chrome. But the whole new register of this pass is
     * "a card is a shade paler than the ground", and on a difference that
     * small the register simply does not exist: with the wallpaper declined
     * every tile in the app would be invisible.
     *
     * So where the fill cannot carry it, a hairline does — in `rule`, which
     * is the palette's own divider and nothing new. Some extracted
     * wallpapers land in the same place, so the test is on the colours
     * rather than on which palette they came from.
     */
    val tileNeedsEdge: Boolean = false,
)

/**
 * Ribbon's own colours — the ones the brand owns, used when the wallpaper is
 * declined and as the source of every fixed value in [Palette].
 */
object Brand {

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

    /** The brand accent. */
    val chartreuse = Color(0xFFD6E45C)

    /**
     * The room, in Ribbon's own paint.
     *
     * The six values are exactly what the build book sets, and the tile edge
     * is on — the arithmetic for why is on [RoomColours.tileNeedsEdge]. The
     * brand's card is a *drawn* card rather than a filled one, which is the
     * more bookish of the two answers anyway. The same test runs over a
     * wallpaper's scheme in `Theme.kt`; this one is decided here because
     * these six values never change.
     */
    val room = RoomColours(
        ground = ground,
        surface = surface,
        raised = raised,
        text = text,
        muted = muted,
        rule = rule,
        accent = chartreuse,
        onAccent = ground,
        tileNeedsEdge = true,
    )
}

/**
 * The room the current composition is standing in.
 *
 * Static rather than dynamic: changing the palette is a whole-app event
 * (a toggle in Appearance, or the wallpaper changing under the app), and it
 * should repaint everything rather than be read reactively by ten thousand
 * leaves.
 */
val LocalRoomColours = staticCompositionLocalOf { Brand.room }

object Palette {

    // MARK: The room — the wallpaper's, or Ribbon's own.

    val ground: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.ground

    val surface: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.surface

    val raised: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.raised

    val text: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.text

    val muted: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.muted

    val rule: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.rule

    /**
     * The single accent.
     *
     * Still called `chartreuse` at every call site because that is what it is
     * whenever the room is Ribbon's own, and renaming eighty lines would have
     * said nothing the comment above does not. [accent] is the same value
     * under the name that is true in both cases; new code should prefer it.
     */
    val chartreuse: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.accent

    val accent: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.accent

    /** What is legible on [accent] — the way in's own label. */
    val onAccent: Color @Composable @ReadOnlyComposable get() = LocalRoomColours.current.onAccent

    // MARK: The fire — fixed, whatever the wallpaper says.

    val flameBright = Color(0xFFF3C778)
    val flameCore = Color(0xFFE9A63F)
    val flameDeep = Color(0xFFC87A46)
    val coal = Color(0xFF8A4A24)
    val coalDim = Color(0xFF4A2714)

    /**
     * The hot air above the tips, and the ash that drifts out of it.
     *
     * Drawn by `FirePainter`, which is not a composition — it is a pure
     * function of a clock — so these cannot be room roles even if we wanted
     * them to be. They were ivory and muted, and they stay ivory and muted:
     * smoke off a warm fire is not the colour of somebody's wallpaper.
     */
    val smoke = Brand.text
    val ash = Brand.muted

    /** The wash opacity for highlights (§4.5). */
    const val HIGHLIGHT_WASH = 0.24f
}

/** The ink's colour against the dark ground. Identity, never repainted. */
val Ink.color: Color get() = Color(("ff" + darkHex).toLong(16))

/** The ink's colour against the light (paper) ground — unused until the
 *  light palette is resolved (§16.1), kept so nothing has to be invented. */
val Ink.lightColor: Color get() = Color(("ff" + lightHex).toLong(16))
