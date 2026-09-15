package app.readribbon.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The shape system (§12.2 — "Yes, heavily").
//
// Material 3 Expressive's argument is that a corner radius is a tone of
// voice: a 4 dp corner is an instrument panel, a 28 dp corner is a thing you
// would pick up. Ribbon wants the second one nearly everywhere, because the
// room is furniture rather than chrome — and because the owner's note on
// this pass was that the app reads as too severe.
//
// The scale below is the one the whole app now draws with, so that a card in
// the room, a group in Appearance and a sheet over the book are recognisably
// the same family of object rather than three people's guesses.
//
// **The nesting rule.** A shape inside another shape takes the next size
// down, and the gap between them is the difference: a 12 dp row inside a
// 28 dp group sits on 8 dp of padding, and the two curves stay concentric
// instead of the inner one looking pinched. Android 16's own settings do
// this and it is most of why they look drawn rather than assembled.

object RibbonShape {

    /** A chip, an ink swatch, a small tag. */
    val small: Dp = 12.dp

    /** One row inside a group — the inner half of the nesting rule. */
    val row: Dp = 16.dp

    /** A card standing on the ground: a waiting note, an ember record. */
    val card: Dp = 22.dp

    /** A group of rows, and the hearth. The app's signature radius. */
    val group: Dp = 28.dp

    /** A sheet, a cover, anything that arrived over something else. */
    val sheet: Dp = 34.dp

    /** The gap that keeps a [row] concentric inside a [group]. */
    val nest: Dp = group - row

    val smallShape = RoundedCornerShape(small)
    val rowShape = RoundedCornerShape(row)
    val cardShape = RoundedCornerShape(card)
    val groupShape = RoundedCornerShape(group)

    /** A sheet or a cover: rounded at the top, square where it meets the
     *  bottom of the screen. */
    val sheetShape = RoundedCornerShape(topStart = sheet, topEnd = sheet)

    /**
     * One row's shape inside a group of [count], at [index].
     *
     * The group is a single rounded container and the rows inside it share
     * its corners: the first row rounds at the top, the last at the bottom,
     * and everything between is square. A single row is the whole group.
     *
     * Drawn per row rather than by clipping the group, because a clipped
     * group cannot give each row its own press state without the wash
     * bleeding over the corner it is supposed to be inside.
     */
    fun inGroup(index: Int, count: Int): RoundedCornerShape {
        val top = if (index == 0) group else small
        val bottom = if (index == count - 1) group else small
        return RoundedCornerShape(
            topStart = top,
            topEnd = top,
            bottomStart = bottom,
            bottomEnd = bottom,
        )
    }
}

/**
 * Material's own scale, pointed at Ribbon's.
 *
 * Anything drawn by Material rather than by us — a time picker's dialog, a
 * segmented button, a menu — lands on these, so a control we did not draw
 * still belongs to the same room.
 */
val RibbonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RibbonShape.smallShape,
    medium = RibbonShape.rowShape,
    large = RibbonShape.cardShape,
    extraLarge = RibbonShape.groupShape,
)
