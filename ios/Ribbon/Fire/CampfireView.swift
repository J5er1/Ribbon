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

struct CampfireView: View {
    var state: FireState
    var scale: FireScale
    /// 0...1, from `Handiwork.coalDepth`.
    var coalDepth: Double
    /// Dim by ~8% when rendering a last-known state offline (S01).
    var dimmed: Bool = false

    @State private var seed = Double.random(in: 0..<1000)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if reduceMotion {
                // The fire holds a state instead of flickering (§11).
                Canvas { context, size in
                    FirePainter.draw(
                        in: &context, size: size, time: seed,
                        state: state, scale: scale, coalDepth: coalDepth)
                }
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { timeline in
                    Canvas { context, size in
                        FirePainter.draw(
                            in: &context, size: size,
                            time: timeline.date.timeIntervalSinceReferenceDate + seed,
                            state: state, scale: scale, coalDepth: coalDepth)
                    }
                }
            }
        }
        .frame(height: scale.frameHeight)
        .opacity(dimmed ? 0.92 : 1)
        .accessibilityElement()
        .accessibilityLabel("The fire is \(state.displayName).")
    }
}

/// A tiny static fire — the chooser's length indicator (S13) and the rooms
/// sheet's state glyph (S14).
struct CampfireGlyph: View {
    var state: FireState
    var scale: FireScale
    var height: CGFloat = 22

    var body: some View {
        Canvas { context, size in
            FirePainter.draw(
                in: &context, size: size, time: 402.7,
                state: state, scale: scale, coalDepth: 0.3)
        }
        .frame(width: height * 1.4, height: height)
        .accessibilityHidden(true)
    }
}

enum FirePainter {
    /// A smooth pseudo-noise: two incommensurate sines around the 3.4 s
    /// breath, so the loop never visibly repeats.
    static func breath(_ t: Double, _ phase: Double) -> Double {
        let a = sin((t / 3.4 + phase) * 2 * .pi)
        let b = sin((t / 2.13 + phase * 1.7) * 2 * .pi + 1.1)
        let c = sin((t / 7.9 + phase * 0.31) * 2 * .pi + 4.2)
        return a * 0.5 + b * 0.35 + c * 0.15
    }

    static func draw(
        in context: inout GraphicsContext, size: CGSize, time: Double,
        state: FireState, scale: FireScale, coalDepth: Double
    ) {
        let w = size.width
        let h = size.height
        let baseY = h * 0.88
        let cx = w / 2

        // The fire's footprint grows with the book's scale.
        let footprint = min(w * 0.6, h * 1.05)

        // --- The warm throw -------------------------------------------------
        // A radial bloom behind everything. Steady throws wide and even;
        // banked is a dim, close glow; the coal bed widens it as it deepens.
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
            x: cx - throwRadius * glowBreath,
            y: baseY - throwRadius * glowBreath * 0.62,
            width: throwRadius * 2 * glowBreath,
            height: throwRadius * glowBreath * 1.05)
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
                endRadius: throwRadius * glowBreath))

        // --- The coal bed ---------------------------------------------------
        // The one thing that may grow. A low mound of warmth whose width and
        // heat come from coalDepth; under ash when banked.
        let bedWidth = footprint * (0.5 + 0.42 * coalDepth)
        let bedHeight = footprint * (0.1 + 0.05 * coalDepth)
        let bedRect = CGRect(
            x: cx - bedWidth / 2, y: baseY - bedHeight / 2,
            width: bedWidth, height: bedHeight)
        let bedGlow = state == .banked
            ? 0.5 + 0.08 * breath(time / 2.6, 0.71)   // banked coals pulse, very slowly
            : 0.75 + 0.1 * breath(time, 0.44)
        context.fill(
            Path(ellipseIn: bedRect),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.coal.opacity(0.9 * bedGlow),
                    Palette.coalDim.opacity(0.8 * bedGlow),
                    Palette.coalDim.opacity(0),
                ]),
                center: CGPoint(x: cx, y: baseY),
                startRadius: 0,
                endRadius: bedWidth / 2))

        // A few distinct coals, warm points in the bed.
        for i in 0..<5 {
            let f = Double(i)
            let px = cx + CGFloat(breath(f * 13.7, f * 0.9)) * bedWidth * 0.3
            let py = baseY - bedHeight * 0.1 + CGFloat(breath(f * 7.1, f * 0.4)) * bedHeight * 0.18
            let r = footprint * 0.022 * (1 + 0.5 * abs(breath(f * 3.3, f)))
            let ember = 0.45 + 0.25 * breath(time / 1.7, f * 0.618)
            context.fill(
                Path(ellipseIn: CGRect(x: px - r, y: py - r, width: r * 2, height: r * 2)),
                with: .color(Palette.flameDeep.opacity(ember * (state == .banked ? 0.7 : 1))))
        }

        // --- Ash, when banked ----------------------------------------------
        if state == .banked {
            let ashRect = bedRect.insetBy(dx: -bedWidth * 0.06, dy: -bedHeight * 0.14)
            context.fill(
                Path(ellipseIn: ashRect),
                with: .linearGradient(
                    Gradient(colors: [
                        Palette.text.opacity(0.10),
                        Palette.muted.opacity(0.16),
                    ]),
                    startPoint: CGPoint(x: cx, y: ashRect.minY),
                    endPoint: CGPoint(x: cx, y: ashRect.maxY)))
            return  // coals under ash, a dim glow, no flame
        }

        // --- Flames ---------------------------------------------------------
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

        // Steady breathes gently; burning moves more.
        let liveliness: Double = state == .steady ? 0.35 : (state == .catching ? 0.8 : 1.0)

        for tongue in tongues {
            let sway = breath(time, tongue.phase) * 0.05 * liveliness
            let rise = 1 + breath(time * 1.13, tongue.phase + 0.3) * 0.14 * liveliness
            let baseX = cx + tongue.x * footprint
            let tipX = baseX + CGFloat(sway) * footprint
            let tipY = baseY - tongue.height * footprint * CGFloat(rise)
            let halfW = tongue.width * footprint / 2 * (1 + CGFloat(breath(time * 0.9, tongue.phase + 0.6)) * 0.08)

            var flame = Path()
            flame.move(to: CGPoint(x: baseX - halfW, y: baseY))
            flame.addQuadCurve(
                to: CGPoint(x: tipX, y: tipY),
                control: CGPoint(x: baseX - halfW * 1.15, y: baseY - (baseY - tipY) * 0.55))
            flame.addQuadCurve(
                to: CGPoint(x: baseX + halfW, y: baseY),
                control: CGPoint(x: baseX + halfW * 1.15, y: baseY - (baseY - tipY) * 0.5))
            flame.closeSubpath()

            context.fill(
                flame,
                with: .linearGradient(
                    Gradient(colors: [
                        Palette.flameDeep.opacity(0.92),
                        Palette.flameCore.opacity(0.9),
                        Palette.flameBright.opacity(0.85),
                    ]),
                    startPoint: CGPoint(x: baseX, y: baseY),
                    endPoint: CGPoint(x: tipX, y: tipY)))
        }
    }
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
