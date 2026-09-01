import SwiftUI
import RibbonCore

// The campfire (§4.1) — a single warm object, abstract, never a cartoon
// flame, never a number anywhere near it.
//
// Law 4: the frame is fixed by the book's scale and never changes during a
// reading. The state drives the flames; the coal bed beneath deepens over a
// long read and throws a wider warm light. The flicker is a slow 3.4 s
// irregular loop, seeded per instance so no two fires — and no two
// on-screen copies — ever breathe in step.
//
// How the drawing stays honest about being fire without becoming a picture
// of one: each tongue is an outline of short curve segments whose edges are
// pushed by value noise that travels upward, so the flame necks and bulges
// the way convected air does instead of waving like a flag. Each tongue is
// three nested bodies — a turbulent deep-orange sheath, a core-orange body,
// and a short bright heart that hugs the coals — because heat lives low; a
// flame whose brightest point is its tip reads as clip-art. Everything that
// is light adds (.plusLighter); only ash occludes.

struct CampfireView: View {
    var state: FireState
    var scale: FireScale
    /// 0...1, from `Handiwork.coalDepth`.
    var coalDepth: Double
    /// Dim by ~8% when rendering a last-known state offline (S01).
    var dimmed: Bool = false

    @State private var seed = Double.random(in: 0..<1000)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var sizeClass

    /// The frame is fixed by the book's scale (Law 4) — but the fixed
    /// frame is a display measure, and an iPad's room is a bigger room:
    /// every scale draws proportionally larger there, so relative sizes
    /// (Isaiah still towers over Philemon) are untouched.
    private var frameHeight: Double {
        scale.frameHeight * (sizeClass == .regular ? 1.45 : 1)
    }

    var body: some View {
        Group {
            if reduceMotion {
                // The fire holds a state instead of flickering (§11).
                Canvas { context, size in
                    FirePainter.draw(
                        in: &context, size: size, time: seed,
                        state: state, coalDepth: coalDepth)
                }
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
                    Canvas { context, size in
                        FirePainter.draw(
                            in: &context, size: size,
                            time: timeline.date.timeIntervalSinceReferenceDate + seed,
                            state: state, coalDepth: coalDepth)
                    }
                }
            }
        }
        .frame(maxWidth: 560)
        .frame(height: frameHeight)
        .opacity(dimmed ? 0.92 : 1)
        .accessibilityElement()
        .accessibilityLabel("The fire is \(state.displayName).")
    }
}

/// A tiny static fire — the chooser's length indicator (S13) and the rooms
/// sheet's state glyph (S14).
///
/// `height` is the box the glyph occupies *and* the height a large book's
/// fire draws at inside it. Given a `scale`, the drawing takes the book's
/// own share of the room-screen ratio (Law 4's 120 : 180 : 240), so
/// Philemon's fire is half of Psalms' wherever they stand next to each
/// other — in S13 the drawn fire is the only length indicator there is, so
/// a glyph that ignored its scale would be saying nothing. The box never
/// changes, so a row or a card keeps one baseline whatever book it carries.
///
/// Pass no scale where the glyph reports a *state* rather than a length
/// (S14's rooms sheet): it then fills its box, and its size says nothing.
struct CampfireGlyph: View {
    var state: FireState
    var scale: FireScale? = nil
    var height: CGFloat = 22

    /// The painter takes its whole geometry from the frame it is handed, so
    /// the scale is applied by shrinking the frame — one place, and the
    /// fire stays proportioned exactly as it is on the room screen.
    private var drawnHeight: CGFloat {
        guard let scale else { return height }
        return height * CGFloat(scale.frameHeight / FireScale.large.frameHeight)
    }

    var body: some View {
        Canvas { context, size in
            FirePainter.draw(
                in: &context, size: size, time: 402.7,
                state: state, coalDepth: 0.3)
        }
        .frame(width: drawnHeight * 1.4, height: drawnHeight)
        // The fires of a list sit on one base, small ones simply reaching
        // less far up the same box.
        .frame(width: height * 1.4, height: height, alignment: .bottom)
        .accessibilityHidden(true)
    }
}

enum FirePainter {
    /// A smooth pseudo-noise: two incommensurate sines around the 3.4 s
    /// breath (RibbonMotion.flickerPeriod), so the loop never visibly
    /// repeats. EmberView leans on this too — the signature is load-bearing.
    static func breath(_ t: Double, _ phase: Double) -> Double {
        let a = sin((t / 3.4 + phase) * 2 * .pi)
        let b = sin((t / 2.13 + phase * 1.7) * 2 * .pi + 1.1)
        let c = sin((t / 7.9 + phase * 0.31) * 2 * .pi + 4.2)
        return a * 0.5 + b * 0.35 + c * 0.15
    }

    /// A deterministic hash → 0..<1. Everything "random" about the fire —
    /// lump shapes, spark clocks, shed gates — comes through here, never
    /// through a RNG, so the same (time, state, size, coalDepth) always
    /// draws the same frame. Reduce-motion holds one arbitrary instant
    /// (§11), and that instant must be a fire, not a roll of the dice.
    static func hash(_ n: Double) -> Double {
        let s = sin(n * 127.1 + 311.7) * 43758.5453123
        return s - s.rounded(.down)
    }

    /// 1-D value noise in -1...1, C¹-smooth. Callers sample it at
    /// (time · speed − height · k) so the perturbation climbs the flame;
    /// wobble that travels upward is most of what separates fire from
    /// jelly.
    static func noise(_ x: Double, _ seed: Double) -> Double {
        let i = x.rounded(.down)
        let f = x - i
        let u = f * f * (3 - 2 * f)
        let a = hash(i + seed * 57.31)
        let b = hash(i + 1 + seed * 57.31)
        return (a + (b - a) * u) * 2 - 1
    }

    private static func fract(_ x: Double) -> Double { x - x.rounded(.down) }

    /// Smooths a polyline into quad curves through segment midpoints —
    /// short segments in, one continuous organic edge out.
    private static func addSmoothSpine(_ path: inout Path, through points: [CGPoint]) {
        guard points.count > 2 else {
            if let last = points.last { path.addLine(to: last) }
            return
        }
        for i in 1..<(points.count - 1) {
            let mid = CGPoint(
                x: (points[i].x + points[i + 1].x) / 2,
                y: (points[i].y + points[i + 1].y) / 2)
            path.addQuadCurve(to: mid, control: points[i])
        }
        path.addLine(to: points[points.count - 1])
    }

    /// One tongue outline. Both edges are sample points perturbed by
    /// upward-travelling value noise — left and right sample different
    /// lanes, so the tongue is never symmetric — then smoothed. The base
    /// is planted (the noise envelope is zero at the coals) and the width
    /// necks to nothing at the tip, with a slight belly low down.
    private static func tonguePath(
        baseX: CGFloat, baseY: CGFloat, height: CGFloat, halfWidth: CGFloat,
        time: Double, phase: Double, agitation: Double, tempo: Double,
        sway: CGFloat
    ) -> Path {
        let segments = 8
        var left: [CGPoint] = []
        var right: [CGPoint] = []
        left.reserveCapacity(segments + 1)
        right.reserveCapacity(segments + 1)
        let travel = time * 0.55 * tempo
        for j in 0...segments {
            let u = Double(j) / Double(segments)
            let planted = u * u * (3 - 2 * u)                     // 0 at the coals
            let profile = (1 - u) * (1 + 1.6 * u - 0.4 * u * u)   // belly low, neck high
            let squeezeL = noise(travel - u * 2.6, phase + 3.1)
            let squeezeR = noise(travel - u * 2.6 + 11.7, phase + 7.7)
            let bend = noise(travel * 0.7 - u * 1.8, phase + 13.0)
            let mid = baseX + sway * CGFloat(planted)
                + CGFloat(bend * agitation * planted) * halfWidth * 0.5
            let reach = halfWidth * CGFloat(profile)
            let y = baseY - height * CGFloat(u)
            left.append(CGPoint(
                x: mid - reach * CGFloat(1 + 0.38 * agitation * planted * squeezeL), y: y))
            right.append(CGPoint(
                x: mid + reach * CGFloat(1 + 0.38 * agitation * planted * squeezeR), y: y))
        }
        var outline = left
        outline.append(contentsOf: right.dropLast().reversed())
        var path = Path()
        path.move(to: outline[0])
        addSmoothSpine(&path, through: outline)
        path.closeSubpath()
        return path
    }

    /// An irregular rounded coal: six vertices with hashed radius jitter,
    /// smoothed through midpoints, flattened as if seen at the bed's angle.
    private static func lumpPath(center: CGPoint, radius: CGFloat, seed: Double) -> Path {
        let vertices = 6
        var pts: [CGPoint] = []
        pts.reserveCapacity(vertices)
        for v in 0..<vertices {
            let angle = Double(v) / Double(vertices) * 2 * Double.pi
            let rx = radius * CGFloat(0.72 + 0.56 * hash(seed + Double(v) * 3.77))
            let ry = radius * 0.62 * CGFloat(0.72 + 0.56 * hash(seed + Double(v) * 9.13))
            pts.append(CGPoint(
                x: center.x + CGFloat(cos(angle)) * rx,
                y: center.y + CGFloat(sin(angle)) * ry))
        }
        var path = Path()
        let firstMid = CGPoint(
            x: (pts[vertices - 1].x + pts[0].x) / 2,
            y: (pts[vertices - 1].y + pts[0].y) / 2)
        path.move(to: firstMid)
        for v in 0..<vertices {
            let next = pts[(v + 1) % vertices]
            let mid = CGPoint(x: (pts[v].x + next.x) / 2, y: (pts[v].y + next.y) / 2)
            path.addQuadCurve(to: mid, control: pts[v])
        }
        path.closeSubpath()
        return path
    }

    /// Everything drawn is a fraction of the frame handed in — the caller
    /// sizes that frame from the book's scale (Law 4), so the painter never
    /// needs to know which scale it is drawing.
    static func draw(
        in context: inout GraphicsContext, size: CGSize, time: Double,
        state: FireState, coalDepth: Double
    ) {
        let w = size.width
        let h = size.height
        let baseY = h * 0.88
        let cx = w / 2

        // The fire's footprint — and with it every flame, coal and spark —
        // comes from the frame, which the caller fixed from the book's scale.
        let footprint = min(w * 0.6, h * 1.05)

        // Below this the fire is a glyph (S13/S14): skip blur passes,
        // sparks, shed blobs and hot air so it stays a crisp, cheap mark.
        let detailed = h >= 48
        let banked = state == .banked

        // --- The warm throw -------------------------------------------------
        // A radial bloom behind everything, additive so the flames sit in
        // their own light. Steady throws wide and even; banked is a dim,
        // close glow; the coal bed widens it as it deepens.
        let throwRadius: CGFloat
        let throwOpacity: Double
        switch state {
        case .catching:
            throwRadius = footprint * (0.5 + 0.25 * coalDepth)
            throwOpacity = 0.16
        case .burning:
            throwRadius = footprint * (0.75 + 0.3 * coalDepth)
            throwOpacity = 0.24
        case .steady:
            throwRadius = footprint * (1.0 + 0.35 * coalDepth)
            throwOpacity = 0.3
        case .banked:
            throwRadius = footprint * (0.42 + 0.2 * coalDepth)
            throwOpacity = 0.13
        }
        let glowBreath = 1 + 0.05 * breath(time, 0.13)
        let glowRect = CGRect(
            x: cx - throwRadius * CGFloat(glowBreath),
            y: baseY - throwRadius * CGFloat(glowBreath) * 0.62,
            width: throwRadius * 2 * CGFloat(glowBreath),
            height: throwRadius * CGFloat(glowBreath) * 1.05)
        context.blendMode = .plusLighter
        context.fill(
            Path(ellipseIn: glowRect),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.flameCore.opacity(throwOpacity),
                    Palette.flameDeep.opacity(throwOpacity * 0.4),
                    .clear,
                ]),
                center: CGPoint(x: cx, y: baseY),
                startRadius: 0,
                endRadius: throwRadius * CGFloat(glowBreath)))
        context.blendMode = .normal

        // --- The coal bed ---------------------------------------------------
        // The one thing that may grow (Law 4). Not a single ellipse: a low
        // mound carrying eleven seeded, irregular lumps, each pulsing on
        // its own slow clock, with bright fissures where heat shows between
        // them. Width and warmth come from coalDepth — a fact you can feel
        // but never count (Law 2). The bed is a thing, so it blends normal;
        // only its fissures are light.
        let bedWidth = footprint * (0.5 + 0.42 * coalDepth)
        let bedHeight = footprint * (0.1 + 0.05 * coalDepth)
        let bedRect = CGRect(
            x: cx - bedWidth / 2, y: baseY - bedHeight / 2,
            width: bedWidth, height: bedHeight)
        let bedGlow = banked
            ? 0.45 + 0.08 * breath(time / 2.6, 0.71)   // banked coals pulse, very slowly
            : 0.7 + 0.1 * breath(time, 0.44)
        context.fill(
            Path(ellipseIn: bedRect),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.coal.opacity(0.85 * bedGlow),
                    Palette.coalDim.opacity(0.75 * bedGlow),
                    Palette.coalDim.opacity(0),
                ]),
                center: CGPoint(x: cx, y: baseY),
                startRadius: 0,
                endRadius: bedWidth / 2))

        for i in 0..<11 {
            let fi = Double(i)
            let hx = hash(fi * 12.99 + 4.1)
            let hy = hash(fi * 78.23 + 9.7)
            let hr = hash(fi * 39.43 + 2.3)
            let px = cx + CGFloat(hx - 0.5) * bedWidth * 0.88
            let py = baseY + CGFloat(hy - 0.5) * bedHeight * 0.5
            let r = footprint * CGFloat(0.024 + 0.02 * hr) * CGFloat(0.85 + 0.3 * coalDepth)
            // Lumps near the middle run hotter; each one's glow drifts on
            // its own long period so the bed never beats in unison.
            let centerBias = 1 - min(1, abs(Double(px - cx)) / Double(bedWidth / 2))
            let pulse = 0.5 + 0.5 * breath(time / (2.1 + 2.3 * hash(fi + 31.7)), fi * 0.618)
            var heat = (0.3 + 0.55 * centerBias) * (0.55 + 0.45 * pulse)
            if banked { heat *= 0.5 }
            context.fill(
                lumpPath(center: CGPoint(x: px, y: py), radius: r, seed: fi * 5.77),
                with: .radialGradient(
                    Gradient(colors: [
                        Palette.coal.opacity(0.55 + 0.4 * heat),
                        Palette.coalDim.opacity(0.9),
                    ]),
                    center: CGPoint(x: px, y: py - r * 0.25),
                    startRadius: 0,
                    endRadius: r * 1.5))
        }

        // Fissures — the heat that shows between coals. Additive, so where
        // two cross, the bed brightens the way real embers do.
        context.blendMode = .plusLighter
        let fissureCount = detailed ? 7 : 4
        for k in 0..<fissureCount {
            let fk = Double(k)
            let x0 = cx + CGFloat(hash(fk * 3.37 + 7.2) - 0.5) * bedWidth * 0.72
            let y0 = baseY + CGFloat(hash(fk * 5.11 + 2.9) - 0.5) * bedHeight * 0.42
            let len = bedWidth * CGFloat(0.07 + 0.09 * hash(fk * 9.23 + 1.1))
            let angle = hash(fk * 4.71 + 6.6) * Double.pi
            let dx = CGFloat(cos(angle)) * len
            let dy = CGFloat(sin(angle)) * len * 0.3   // the bed is seen at an angle
            var fissure = Path()
            fissure.move(to: CGPoint(x: x0 - dx / 2, y: y0 - dy / 2))
            fissure.addQuadCurve(
                to: CGPoint(x: x0 + dx / 2, y: y0 + dy / 2),
                control: CGPoint(x: x0 + dy * 0.8, y: y0 - dx * 0.15))
            let flick = 0.5 + 0.5 * breath(time / 1.9, fk * 0.77 + 0.2)
            let glow = banked ? 0.1 + 0.1 * flick : 0.22 + 0.3 * flick
            context.stroke(
                fissure,
                with: .linearGradient(
                    Gradient(colors: [
                        Palette.flameDeep.opacity(glow),
                        Palette.flameCore.opacity(glow * 0.85),
                        Palette.flameDeep.opacity(glow * 0.5),
                    ]),
                    startPoint: CGPoint(x: x0 - dx / 2, y: y0),
                    endPoint: CGPoint(x: x0 + dx / 2, y: y0)),
                style: StrokeStyle(lineWidth: max(0.7, footprint * 0.012), lineCap: .round))
        }
        context.blendMode = .normal

        // --- Ash, when banked ----------------------------------------------
        // Banked is an act, never a lapse — and it must never be mistaken
        // for catching. So: nothing flame-shaped past this point, and the
        // ash blends normal so it genuinely occludes the glow it covers.
        if banked {
            let ashRect = bedRect.insetBy(dx: -bedWidth * 0.06, dy: -bedHeight * 0.14)
            context.fill(
                Path(ellipseIn: ashRect),
                with: .linearGradient(
                    Gradient(colors: [
                        Palette.text.opacity(0.12),
                        Palette.muted.opacity(0.18),
                    ]),
                    startPoint: CGPoint(x: cx, y: ashRect.minY),
                    endPoint: CGPoint(x: cx, y: ashRect.maxY)))
            // A thinner drift off one side, so the veil reads settled by a
            // hand, not stamped by a machine.
            let driftRect = CGRect(
                x: cx - bedWidth * 0.18, y: baseY - bedHeight * 0.52,
                width: bedWidth * 0.62, height: bedHeight * 0.5)
            context.fill(
                Path(ellipseIn: driftRect),
                with: .color(Palette.muted.opacity(0.1)))
            return  // coals under ash, a dim glow, no flame
        }

        // --- Flames ---------------------------------------------------------
        // Tongue geometry per state (§4.1): x, height and width are
        // fractions of the footprint; phase desyncs the tongues from one
        // another (the per-instance seed already desyncs whole fires).
        let tongues: [(x: CGFloat, height: CGFloat, width: CGFloat, phase: Double)]
        switch state {
        case .catching:
            // Small, low, a few licks, working at it.
            tongues = [
                (-0.10, 0.30, 0.16, 0.21),
                (0.08, 0.24, 0.13, 0.57),
            ]
        case .burning:
            // Full, active, irregular.
            tongues = [
                (-0.16, 0.52, 0.22, 0.13),
                (0.00, 0.78, 0.28, 0.41),
                (0.15, 0.44, 0.19, 0.74),
            ]
        case .steady:
            // Broad, even, almost calm.
            tongues = [
                (-0.20, 0.46, 0.30, 0.11),
                (0.00, 0.62, 0.40, 0.36),
                (0.20, 0.48, 0.30, 0.67),
            ]
        case .banked:
            tongues = []
        }

        // Character per state. Steady is the return for reading together
        // and the return is a mood: its turbulence is both damped and
        // slowed, not merely shrunk. Shed is the chance a given cycle lets
        // a tip go — burning sheds most, catching only the odd lick that
        // lifts and dies.
        let character: (liveliness: Double, agitation: Double, tempo: Double, shed: Double)
        switch state {
        case .catching: character = (0.8, 0.85, 1.0, 0.25)
        case .burning: character = (1.0, 1.0, 1.2, 0.8)
        case .steady: character = (0.35, 0.45, 0.6, 0.18)
        case .banked: character = (0, 0, 0, 0)   // unreachable — returned above
        }

        context.blendMode = .plusLighter

        // Three nested bodies per tongue. The sheath is widest and most
        // turbulent; the heart is short, calm, and hugs the coals. Every
        // gradient puts its brightest stop at the base and dies into
        // translucent deep orange at the tip — heat lives low. Additive
        // blending is commutative, so the layers can be batched.
        func drawFlameLayer(
            _ ctx: inout GraphicsContext,
            heightF: CGFloat, widthF: CGFloat, agitationF: Double,
            swayF: CGFloat, phaseShift: Double, stops: [Gradient.Stop]
        ) {
            for tongue in tongues {
                let phase = tongue.phase + phaseShift
                let sway = CGFloat(breath(time, tongue.phase) * 0.055 * character.liveliness)
                    * footprint * swayF
                let rise = 1 + breath(time * 1.13, phase + 0.3) * 0.11 * character.liveliness
                let height = tongue.height * footprint * CGFloat(rise) * heightF
                let halfW = tongue.width * footprint / 2 * widthF
                let baseX = cx + tongue.x * footprint
                let path = tonguePath(
                    baseX: baseX, baseY: baseY, height: height, halfWidth: halfW,
                    time: time, phase: phase,
                    agitation: character.agitation * agitationF,
                    tempo: character.tempo, sway: sway)
                ctx.fill(
                    path,
                    with: .linearGradient(
                        Gradient(stops: stops),
                        startPoint: CGPoint(x: baseX, y: baseY),
                        endPoint: CGPoint(x: baseX + sway, y: baseY - height)))
            }
        }

        let sheathStops: [Gradient.Stop] = [
            .init(color: Palette.flameDeep.opacity(0.55), location: 0),
            .init(color: Palette.flameDeep.opacity(0.32), location: 0.45),
            .init(color: Palette.flameDeep.opacity(0), location: 1),
        ]
        let bodyStops: [Gradient.Stop] = [
            .init(color: Palette.flameCore.opacity(0.85), location: 0),
            .init(color: Palette.flameCore.opacity(0.5), location: 0.5),
            .init(color: Palette.flameDeep.opacity(0), location: 1),
        ]
        let heartStops: [Gradient.Stop] = [
            .init(color: Palette.flameBright.opacity(0.95), location: 0),
            .init(color: Palette.flameCore.opacity(0.55), location: 0.55),
            .init(color: Palette.flameCore.opacity(0), location: 1),
        ]

        if detailed {
            // Only the sheath is softened; the heart stays crisp. One
            // shared layer keeps the blur to a single offscreen pass.
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 2.2))
                layer.blendMode = .plusLighter
                drawFlameLayer(
                    &layer, heightF: 1, widthF: 1, agitationF: 1,
                    swayF: 1, phaseShift: 0, stops: sheathStops)
            }
        } else {
            drawFlameLayer(
                &context, heightF: 1, widthF: 1, agitationF: 1,
                swayF: 1, phaseShift: 0, stops: sheathStops)
        }
        drawFlameLayer(
            &context, heightF: 0.72, widthF: 0.68, agitationF: 0.65,
            swayF: 0.8, phaseShift: 0.14, stops: bodyStops)
        drawFlameLayer(
            &context, heightF: 0.42, widthF: 0.42, agitationF: 0.35,
            swayF: 0.55, phaseShift: 0.27, stops: heartStops)

        // --- Tip shedding ---------------------------------------------------
        // Now and then a tongue lets a small body go: it lifts, shrinks and
        // dissolves. Whether a given cycle sheds is a hash gate on the
        // cycle number, and the blob's envelope is zero at both ends of its
        // life — nothing pops, and a frozen frame (§11) can never catch a
        // half-born artifact.
        if detailed && character.shed > 0 {
            for tongue in tongues {
                let period = 3.6 + 2.9 * hash(tongue.phase * 71.3)
                let cycle = (time + tongue.phase * 47.1) / period
                let turn = cycle.rounded(.down)
                guard hash(turn + tongue.phase * 91.7) < character.shed else { continue }
                let age = cycle - turn
                let lift = CGFloat(age) * footprint * 0.24
                let blobX = cx + tongue.x * footprint
                    + CGFloat(breath(time, tongue.phase) * 0.055 * character.liveliness) * footprint
                    + CGFloat(breath(time * 0.9, tongue.phase + 2.4) * 0.02) * footprint
                let blobY = baseY - tongue.height * footprint * 0.9 - lift
                let r = footprint * CGFloat(0.022 + 0.04 * Double(tongue.width))
                    * CGFloat(1 - 0.6 * age)
                let fade = (1 - age) * min(1, age * 5)
                context.fill(
                    Path(ellipseIn: CGRect(
                        x: blobX - r, y: blobY - r * 1.2, width: r * 2, height: r * 2.4)),
                    with: .radialGradient(
                        Gradient(colors: [
                            Palette.flameCore.opacity(0.5 * fade),
                            Palette.flameDeep.opacity(0.28 * fade),
                            Palette.flameDeep.opacity(0),
                        ]),
                        center: CGPoint(x: blobX, y: blobY),
                        startRadius: 0,
                        endRadius: r * 1.4))
            }
        }

        // --- Sparks ---------------------------------------------------------
        // A few motes born on the bed that rise, wander on the breath,
        // shrink and cool from bright to deep. Deterministic per (i, time);
        // subtle by construction — never a fountain.
        let sparkCount: Int
        switch state {
        case .burning: sparkCount = 6
        case .steady: sparkCount = 3
        case .catching: sparkCount = 2
        case .banked: sparkCount = 0
        }
        if detailed {
            for i in 0..<sparkCount {
                let fi = Double(i)
                let period = 2.7 + 2.6 * hash(fi * 7.31 + 5.2)
                let age = fract(time / period + hash(fi * 13.7 + 1.3))
                let born = cx + CGFloat(hash(fi * 29.4 + 8.8) - 0.5) * bedWidth * 0.6
                let riseH = footprint * CGFloat(0.45 + 0.4 * hash(fi * 3.93 + 2.6))
                let x = born + CGFloat(breath(time * 0.7, fi * 0.83) * 0.05 * age) * footprint
                let y = baseY - bedHeight * 0.2 - riseH * CGFloat(age)
                let r = CGFloat(max(0.5, (0.9 + 0.7 * hash(fi * 17.3 + 3.4)) * (1 - 0.55 * age)))
                let fade = (1 - age) * min(1, age * 7)
                context.fill(
                    Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                    with: .radialGradient(
                        Gradient(colors: [
                            Palette.flameBright.opacity(0.55 * fade),
                            Palette.flameDeep.opacity(0.3 * fade),
                            Palette.flameDeep.opacity(0),
                        ]),
                        center: CGPoint(x: x, y: y),
                        startRadius: 0,
                        endRadius: r * 1.4))
            }
        }

        // --- Hot air ---------------------------------------------------------
        // Two nearly invisible warm wisps above the tips, 3–4% at their
        // peak. The book title sits right below the fire, so this must
        // never read as smoke or distortion — only a suggestion that the
        // air up there is warm. Room-size fires only.
        if detailed && h >= 120 {
            let crest = tongues.map { $0.height }.max() ?? 0.5
            context.drawLayer { layer in
                layer.addFilter(.blur(radius: 3))
                layer.blendMode = .plusLighter
                for i in 0..<2 {
                    let fi = Double(i)
                    let age = fract(time / (5.5 + fi * 1.7) + fi * 0.5)
                    let wx = cx + CGFloat(breath(time * 0.5, fi + 0.9) * 0.08) * footprint
                    let wy = baseY - crest * footprint - CGFloat(age) * footprint * 0.22
                    let ww = footprint * 0.3
                    let wh = footprint * 0.1
                    let fade = 0.035 * sin(Double.pi * age)
                    layer.fill(
                        Path(ellipseIn: CGRect(
                            x: wx - ww / 2, y: wy - wh / 2, width: ww, height: wh)),
                        with: .color(Palette.flameDeep.opacity(fade)))
                }
            }
        }

        context.blendMode = .normal
    }
}

#Preview("Glyph scales") {
    // S13's ladder, on one baseline: the box is the same for every book,
    // the fire in it is not.
    VStack(alignment: .leading, spacing: 14) {
        ForEach(FireScale.allCases, id: \.self) { scale in
            HStack(spacing: 12) {
                CampfireGlyph(state: .burning, scale: scale, height: 20)
                SmallCaps(scale.rawValue)
            }
        }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .room()
}

#Preview("States") {
    VStack(spacing: 12) {
        ForEach([FireState.catching, .burning, .steady, .banked], id: \.self) { state in
            VStack {
                CampfireView(state: state, scale: .medium, coalDepth: 0.4)
                SmallCaps(state.displayName)
            }
        }
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .room()
}
