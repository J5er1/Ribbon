package app.readribbon.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The furniture: cards, groups, rows.
//
// Two complaints made this file. The room "seems very barren", and the
// settings are "very condensed" and want "a completely new style". They have
// one cause between them — the app had exactly three kinds of drawn thing (a
// line of text, a hairline, and one chartreuse capsule), so a screen could
// only ever be a list of sentences with more or less space between them. A
// list of sentences with a lot of space is barren; a list of sentences with a
// little space is condensed. There was no third option to reach for.
//
// So there is one now, and it is the shape system §12.2 already asked for:
// **a group is a container, and a row lives inside it**. That is the whole
// idea. It gives a screen a middle register between "a heading" and "a line",
// which is what both complaints were missing, and it costs the app nothing it
// had — the type, the voice and the hairlines are unchanged.
//
// What is deliberately still absent: icons. Material's icon set is the
// fastest way to make this look like everybody else's settings, and §14's
// last test is whether the thing is "obviously made by a person rather than
// assembled from defaults". The few marks here — a chevron, a moving dot —
// are drawn, small, and specific to what they say.

/** The smallest thing a finger is allowed to have to hit (§11, deviation 12). */
private val MinTarget = 44.dp

/** A row's own height inside a group: taller than a finger, on purpose. */
private val RowHeight = 60.dp

/** A row with a subtitle under it needs the second line's room. */
private val TallRowHeight = 76.dp

/**
 * The seam between two tiles of one group.
 *
 * The group is not a drawn container. It is the tiles, plus the two
 * millimetres of grained ground that show between them — which is what makes
 * a group read as furniture standing on a floor rather than as a panel
 * bolted to one. It also avoids stacking two tonal surfaces on top of each
 * other, which on some wallpapers is two shades that are nearly the same.
 *
 * Public because the group DSL below is not the only place a group is built:
 * onboarding's four intents are a group too, and a second opinion about the
 * seam would be visible on the one screen this number matters most.
 */
val Seam = 2.dp

/** How far in a title sits from a tile's own edge. */
private val TextInset = 20.dp

/** How far in a control sits: nearer the edge than words are. */
private val WellInset = RibbonShape.nest

/** How far a pressed thing gives under the finger. */
private const val PRESS_SCALE = 0.975f

/**
 * A press, felt.
 *
 * The state layer (§12.2) says a touch was *received*; this says the thing
 * was *touched*. Material 3 Expressive squashes a pressed container by a
 * couple of percent and springs it back, and it is the single cheapest thing
 * in this whole pass — it costs one `graphicsLayer` and it is the difference
 * between a list that responds and a list that merely redraws.
 *
 * Critically damped, so it returns without bouncing (§9.1), and held still
 * under reduce motion, where the wash alone carries the press (§11).
 */
@Composable
fun Modifier.pressed(source: MutableInteractionSource): Modifier {
    val still = rememberReduceMotion()
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !still) PRESS_SCALE else 1f,
        animationSpec = RibbonMotion.touched(still),
        label = "a-press",
    )
    if (scale == 1f) return this
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A sheet of paper you can press, that gives when you press it.
 *
 * The one helper, and the order inside it is the whole reason it exists. A
 * `graphicsLayer` transforms what comes *after* it in the chain, so a press
 * scale applied after [paper] would shrink the row's words while leaving its
 * paper, its grain and its edge exactly where they were — the contents
 * visibly detaching from their own box. The squash goes first, so the tile
 * moves as one thing.
 */
@Composable
fun Modifier.pressablePaper(
    shape: Shape,
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressed(source)
        .paper(shape)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/**
 * The same, for something that is not a sheet of paper — a portrait in its
 * circle, a word in the room's header.
 *
 * Nothing is drawn behind these, so there is nothing for the squash to
 * detach from.
 */
@Composable
fun Modifier.pressable(
    role: Role = Role.Button,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressed(source)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/**
 * A recess cut into a sheet of paper: the track under a slider, the window a
 * live preview is shown through, the groove a segmented control runs in.
 *
 * The room's own ground rather than a paler surface, so a control sits *in*
 * its tile rather than on it — and the same edge a tile takes when the fill
 * alone cannot carry it. Without that, a well on Ribbon's own palette is a
 * 1.05:1 difference against the tile around it, which is to say no well at
 * all; the whole recessed register would exist only under a wallpaper.
 */
@Composable
fun Modifier.well(shape: Shape): Modifier {
    val room = LocalRoomColours.current
    return this
        .clip(shape)
        .background(room.ground)
        .grain()
        .then(
            if (room.tileNeedsEdge) Modifier.border(1.dp, room.rule, shape) else Modifier,
        )
}

/**
 * A section's name, over whatever it names.
 *
 * Small caps, the app's running-head voice, and announced as the heading it
 * is drawn as — the headings rotor is how a screen reader skims a screen, and
 * without this a settings page is four unlabelled piles (§11).
 */
@Composable
fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier = modifier
            // Label and title share one optical edge, so the label reads as
            // belonging to the card under it rather than floating near it.
            .padding(start = TextInset, end = TextInset, bottom = 10.dp)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallCaps(title, size = 12f, color = Palette.text.copy(alpha = 0.72f))
        if (detail != null) {
            // A middot, because two small-caps phrases with a plain gap
            // between them read as one phrase — "Jonathan Mark" rather than
            // "Jonathan · Mark".
            SmallCaps("·", size = 12f)
            SmallCaps(detail, size = 12f)
        }
    }
}

/**
 * A group of rows in one container.
 *
 * The rows are handed their own shapes as they are placed — first rounds at
 * the top, last at the bottom — so the group reads as one object while each
 * row keeps its own press. See [RibbonShape.inGroup] for why that is drawn
 * per row rather than clipped once.
 *
 * @param count how many rows will be placed. Passed rather than counted,
 *   because a `@Composable` lambda cannot be measured before it is run.
 */
@Composable
fun SettingsGroup(
    count: Int,
    modifier: Modifier = Modifier,
    title: String? = null,
    detail: String? = null,
    footnote: String? = null,
    content: @Composable GroupScope.() -> Unit,
) {
    val scope = remember(count) { GroupScope(count) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) SectionLabel(title, detail = detail)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Seam),
        ) {
            scope.content()
        }
        if (footnote != null) {
            Text(
                text = footnote,
                style = RibbonType.ui(13f),
                color = Palette.muted,
                modifier = Modifier.padding(start = TextInset, end = TextInset, top = 12.dp),
            )
        }
    }
}

/**
 * A sheet of paper: the one drawn thing every card, tile and row in the app
 * is made of.
 *
 * Clip, then fill, then grain, then — where the fill alone cannot be seen —
 * an edge. In that order, and each step is load-bearing: clipping first keeps
 * the press wash inside the shape's own corners; the grain goes over the fill
 * and under the content, so a tile is paper rather than a panel, which is
 * most of what keeps a screen of rounded rectangles from reading as somebody
 * else's Settings app.
 *
 * Everything that is a card goes through here, so that the edge decision is
 * made once rather than remembered in eleven places.
 */
@Composable
fun Modifier.paper(shape: Shape): Modifier {
    val room = LocalRoomColours.current
    return this
        .clip(shape)
        .background(room.surface)
        .grain()
        .then(
            // Where the fill cannot carry the card, the palette's own rule
            // does. See RoomColours.tileNeedsEdge for the arithmetic — on
            // Ribbon's own paint a card is drawn rather than filled, which is
            // the more bookish of the two answers.
            if (room.tileNeedsEdge) Modifier.border(1.dp, room.rule, shape) else Modifier,
        )
}

/**
 * Where a row is in its group, so it can take the right corners.
 *
 * A counter rather than an index parameter at every call site, because four
 * `Setting(...)` calls in order is what a group should look like in source.
 *
 * **Each row takes its slot exactly once**, with `remember(scope)`, and that
 * is not a detail. The content lambda is its own restart scope: when a value
 * inside it changes — the chosen translation, a switch — Compose recomposes
 * the lambda *without* re-entering the group's body, so a counter reset by
 * the group would never be reset and would climb past the end. Every row in
 * the group would then take an interior shape and the group would lose its
 * own corners the first time anybody touched it. Keying on the scope rather
 * than on nothing is the other half: a group whose row *count* changes gets
 * a new scope, which re-runs every row's slot in order.
 */
@Stable
class GroupScope internal constructor(private val count: Int) {
    private var placed = 0

    internal fun next(): Shape = RibbonShape.inGroup(placed++, count)
}

/** This row's own shape in its group, taken once and kept. */
@Composable
private fun GroupScope.slot(): Shape = remember(this) { next() }

/**
 * One row of a group: what it is, what it does, and where it goes.
 *
 * The subtitle is the friendly half and half the reason this style exists. A
 * row that says only "Downloads" makes you open it to find out what it is; a
 * row that says "Both translations, whole, on this phone" has answered
 * already.
 */
@Composable
fun GroupScope.Setting(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = true,
    tint: Color? = null,
) {
    val shape = slot()
    val words = tint ?: Palette.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = if (subtitle == null) RowHeight else TallRowHeight)
            .pressablePaper(shape, onClick = onClick)
            .padding(horizontal = TextInset, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = title, style = RibbonType.ui(17f), color = words)
            if (subtitle != null) {
                Text(text = subtitle, style = RibbonType.ui(13f), color = Palette.muted)
            }
        }
        if (value != null) SmallCaps(value, size = 12f)
        if (chevron) Chevron()
    }
}

/**
 * One row of a group that is a switch.
 *
 * The whole row is the target and announces as one switch, which is what a
 * toggle is; the drawn switch is smaller than a finger, so the row carries
 * the minimum for it.
 */
@Composable
fun GroupScope.SettingSwitch(
    title: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val shape = slot()
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = if (subtitle == null) RowHeight else TallRowHeight)
            // Squash first, then the paper it is squashing — see
            // [pressablePaper] for why the order is load-bearing.
            .pressed(source)
            .paper(shape)
            .toggleable(
                value = value,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Switch,
                onValueChange = onChange,
            )
            .padding(start = TextInset, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = title, style = RibbonType.ui(17f), color = Palette.text)
            if (subtitle != null) {
                Text(text = subtitle, style = RibbonType.ui(13f), color = Palette.muted)
            }
        }
        Switch(
            checked = value,
            // The row owns the gesture and the semantics; the switch is the
            // drawing of the state.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.onAccent,
                checkedTrackColor = Palette.accent,
                checkedBorderColor = Palette.accent,
                uncheckedThumbColor = Palette.muted,
                // The ground, not the surface: the row *is* the surface, so
                // an unchecked track in that colour is no track at all —
                // a thumb floating in the row with only the rule around it,
                // and on Ribbon's own nearly-flat paint the rule is very
                // nearly nothing. The ground is the same recess every other
                // control inside a tile sits in.
                uncheckedTrackColor = Palette.ground,
                uncheckedBorderColor = Palette.rule,
            ),
        )
    }
}

/**
 * A row that states a fact and does nothing: a translation's size on disk,
 * what the plan is.
 *
 * Not pressable, and so not drawn as pressable — a tile that gives under the
 * finger and then does nothing is a worse lie than a line of text.
 */
@Composable
fun GroupScope.SettingValue(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
) {
    val shape = slot()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = if (subtitle == null) RowHeight else TallRowHeight)
            .paper(shape)
            // The one row in this family with no gesture on it, and a gesture
            // is what merges the others: without this its title, its subtitle
            // and its value are three separate stops, so a translation on
            // Downloads takes three swipes and its size can be reached
            // without the name it belongs to.
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = TextInset, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = title, style = RibbonType.ui(17f), color = Palette.text)
            if (subtitle != null) {
                Text(text = subtitle, style = RibbonType.ui(13f), color = Palette.muted)
            }
        }
        if (value != null) SmallCaps(value, size = 12f)
    }
}

/**
 * A tile that is one sentence — the thing a screen wants to say before it
 * starts offering settings.
 */
@Composable
fun GroupScope.SettingNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    val shape = slot()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .paper(shape)
            .padding(horizontal = TextInset, vertical = 18.dp),
    ) {
        Text(text = text, style = RibbonType.ui(16f), color = Palette.text)
    }
}

/**
 * A row that holds something drawn rather than said — a slider, a segmented
 * control, a row of swatches — under its own label.
 */
@Composable
fun GroupScope.SettingControl(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = slot()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .paper(shape)
            .padding(horizontal = WellInset, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A control reaches nearer the tile's edge than words do, so
                // the words are pushed back out to the text inset the rows
                // above and below them keep.
                .padding(start = TextInset - WellInset, end = TextInset - WellInset),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = RibbonType.ui(17f),
                color = Palette.text,
                modifier = Modifier.semantics { heading() },
            )
            // Under the title, not beside it. A detail here is a sentence
            // rather than a value — "Scripture only. Everything else stays
            // where it is." does not fit on a line beside "Text size", and
            // a sentence that has to be told apart from its own heading is
            // not doing the job the subtitle was added for.
            if (detail != null) {
                Text(text = detail, style = RibbonType.ui(13f), color = Palette.muted)
            }
        }
        content()
    }
}

/**
 * A row that is one of a set you pick from, with the chosen one marked.
 *
 * The dot belongs to its row and fades up where the choice landed, rather
 * than sliding down the list past rows nobody chose — a mark that travels
 * implies the rows in between were passed through, and they weren't.
 */
@Composable
fun GroupScope.SettingChoice(
    title: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val shape = slot()
    val still = rememberReduceMotion()
    val accent = Palette.accent
    val dot by animateColorAsState(
        targetValue = if (chosen) accent else Color.Transparent,
        animationSpec = RibbonMotion.touched(still),
        label = "the-chosen-one",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = if (subtitle == null) RowHeight else TallRowHeight)
            .pressablePaper(shape, role = Role.RadioButton, onClick = onClick)
            // The dot is a colour, and colour is never the only signal
            // (§11): the row also announces itself as the chosen one.
            .semantics { selected = chosen }
            .padding(horizontal = TextInset, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = title, style = RibbonType.ui(17f), color = Palette.text)
            if (subtitle != null) {
                Text(text = subtitle, style = RibbonType.ui(13f), color = Palette.muted)
            }
        }
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot),
        )
    }
}

/**
 * The mark at the end of a row that goes somewhere.
 *
 * Drawn rather than an icon font, at the weight of a hairline, and muted —
 * it is punctuation, not a control.
 */
@Composable
fun Chevron(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val muted = Palette.muted
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val arm = this.size.minDimension * 0.26f
                val centre = Offset(this.size.width * 0.55f, this.size.height / 2f)
                val stroke = 1.5.dp.toPx()
                drawLine(
                    color = muted,
                    start = Offset(centre.x - arm * 0.6f, centre.y - arm),
                    end = Offset(centre.x + arm * 0.6f, centre.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = muted,
                    start = Offset(centre.x + arm * 0.6f, centre.y),
                    end = Offset(centre.x - arm * 0.6f, centre.y + arm),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            },
    )
}

/**
 * The way back out of a pushed screen, drawn to match the new furniture.
 *
 * iOS gets this free from `NavigationStack`. Compose draws nothing, and a
 * screen whose only way back is a gesture has no tap equivalent (§11), so it
 * is drawn — now as a round target on the raised surface rather than as a
 * bare stroke floating on the ground, which is what the rest of this style
 * would lead you to expect of it.
 */
@Composable
fun BackChevron(
    onBack: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val muted = Palette.muted
    Box(
        modifier = modifier
            .size(MinTarget)
            .pressablePaper(CircleShape, onClick = onBack)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .drawBehind {
                    val arm = this.size.minDimension * 0.28f
                    val centre = Offset(this.size.width * 0.54f, this.size.height / 2f)
                    val stroke = 1.7.dp.toPx()
                    drawLine(
                        color = muted,
                        start = Offset(centre.x + arm * 0.6f, centre.y - arm),
                        end = Offset(centre.x - arm * 0.6f, centre.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = muted,
                        start = Offset(centre.x - arm * 0.6f, centre.y),
                        end = Offset(centre.x + arm * 0.6f, centre.y + arm),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                },
        )
    }
}

/** A screen's own big heading: the one line that says what you are looking at. */
@Composable
fun ScreenTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = RibbonType.display(30f),
            color = Palette.text,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(text = subtitle, style = RibbonType.ui(15f), color = Palette.muted)
        }
    }
}

/** Vertical air, named, so a screen's rhythm is legible in its source. */
@Composable
fun Air(height: Dp) {
    Spacer(Modifier.height(height))
}
