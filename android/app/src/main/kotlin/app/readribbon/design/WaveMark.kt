package app.readribbon.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The Wave (W6) — the mark, drawn natively at any size from the geometry
// tools/make_assets.py emits. It is the way out of the book everywhere it
// appears, and the closing drag is always duplicated by it (§11, motor).

internal fun List<WaveSegment>.toPath(): Path {
    val path = Path()
    for (segment in this) {
        when (segment) {
            is WaveSegment.Move -> path.moveTo(segment.x, segment.y)
            is WaveSegment.Line -> path.lineTo(segment.x, segment.y)
            is WaveSegment.Quad -> path.quadraticTo(segment.cx, segment.cy, segment.x, segment.y)
            WaveSegment.Close -> path.close()
        }
    }
    return path
}

/**
 * Draws the mark into the current [DrawScope], filling a square of [side]
 * pixels from the origin.
 *
 * The knockout is occlusion, not transparency: the back ribbon is filled,
 * then the front ribbon's outline is stroked in the ground colour, then the
 * front ribbon is filled over the inner half of its own stroke. One ribbon
 * passes behind the other with a hard edge — which is why [ground] has to be
 * whatever this mark is actually sitting on, and why the mark is never drawn
 * over a photograph.
 */
fun DrawScope.drawWave(
    side: Float,
    front: Path,
    back: Path,
    tint: Color = Palette.chartreuse,
    ground: Color = Palette.ground,
) {
    scale(scale = side / WaveGeometry.GRID_SIZE, pivot = Offset.Zero) {
        drawPath(back, tint)
        drawPath(
            path = front,
            color = ground,
            style = Stroke(
                width = WaveGeometry.KNOCKOUT_WIDTH,
                join = StrokeJoin.Miter,
                miter = 6f,
            ),
        )
        drawPath(front, tint)
    }
}

/**
 * The mark as a view. The mark's own 64-unit grid is scaled into [size].
 *
 * Sizing is the drawn size only. Every quiet control and both Waves out of
 * the book meet the 44 dp touch minimum (deviation 12) — that is the
 * caller's business, because the mark is usually drawn smaller than the
 * target that carries it.
 */
@Composable
fun WaveMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = Palette.chartreuse,
    ground: Color = Palette.ground,
) {
    val front = remember { WaveGeometry.front.toPath() }
    val back = remember { WaveGeometry.back.toPath() }
    Canvas(modifier = modifier.size(size)) {
        drawWave(
            side = minOf(this.size.width, this.size.height),
            front = front,
            back = back,
            tint = tint,
            ground = ground,
        )
    }
}
