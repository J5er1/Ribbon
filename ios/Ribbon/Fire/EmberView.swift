import SwiftUI
import RibbonCore

// An ember — what you keep when you finish a book (§4.8). The flame goes
// down; the light stays. On the shelf, embers sit on a shared baseline with
// a faint warm bloom beneath them: light on a surface implies the surface,
// so no shelf is drawn.

struct EmberView: View {
    var scale: FireScale
    /// Diameter relative to the fire the ember was. The whole point of the
    /// shelf is that Isaiah looks like Isaiah.
    var size: CGFloat { scale.frameHeight * 0.30 }

    @State private var seed = Double.random(in: 0..<1000)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if reduceMotion {
                Canvas { context, canvasSize in
                    Self.draw(in: &context, size: canvasSize, time: seed)
                }
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 12.0)) { timeline in
                    Canvas { context, canvasSize in
                        Self.draw(
                            in: &context, size: canvasSize,
                            time: timeline.date.timeIntervalSinceReferenceDate * 0.25 + seed)
                    }
                }
            }
        }
        .frame(width: size * 1.7, height: size * 1.35)
        .accessibilityHidden(true)
    }

    static func draw(in context: inout GraphicsContext, size: CGSize, time: Double) {
        let cx = size.width / 2
        let cy = size.height * 0.52
        let r = min(size.width, size.height) * 0.32
        let pulse = 0.85 + 0.1 * FirePainter.breath(time, 0.37)

        // The bloom beneath — the light that implies the shelf.
        let bloomRect = CGRect(
            x: cx - r * 2.4, y: size.height * 0.66,
            width: r * 4.8, height: r * 1.5)
        context.fill(
            Path(ellipseIn: bloomRect),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.flameDeep.opacity(0.20 * pulse),
                    .clear,
                ]),
                center: CGPoint(x: cx, y: size.height * 0.8),
                startRadius: 0, endRadius: r * 2.4))

        // The ember itself: a warm heart in a dark husk.
        let husk = CGRect(x: cx - r, y: cy - r * 0.82, width: r * 2, height: r * 1.64)
        context.fill(
            Path(ellipseIn: husk),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.flameCore.opacity(0.95 * pulse),
                    Palette.coal.opacity(0.95),
                    Palette.coalDim,
                ]),
                center: CGPoint(x: cx - r * 0.2, y: cy - r * 0.15),
                startRadius: r * 0.05, endRadius: r * 1.1))

        // A seam of heat.
        var seam = Path()
        seam.move(to: CGPoint(x: cx - r * 0.55, y: cy + r * 0.1))
        seam.addQuadCurve(
            to: CGPoint(x: cx + r * 0.5, y: cy - r * 0.12),
            control: CGPoint(x: cx, y: cy + r * 0.35))
        context.stroke(
            seam,
            with: .color(Palette.flameBright.opacity(0.5 * pulse)),
            style: StrokeStyle(lineWidth: r * 0.09, lineCap: .round))
    }
}

/// The finishing sequence's centerpiece (§6.5): the fire, drawn large one
/// last time, settling into an ember over ~2.5 s. The flame goes down; the
/// light stays. It goes down the way a real fire does — through smaller:
/// the steady fire gives way to a few last licks before the ember, so the
/// settling reads as subsiding, not a projector cross-fade. The coal beds
/// of the two fires share their seeded geometry, so what actually changes
/// under the cross-fade is only the flame. Same machinery as before — one
/// piece of state and one task; the licks are just a middle value of it.
struct FireBecomesEmber: View {
    var scale: FireScale
    var coalDepth: Double
    /// 0 = the fire as it was, 1 = the last licks, 2 = the ember.
    @State private var settling = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            CampfireView(state: .steady, scale: scale, coalDepth: coalDepth)
                .opacity(settling == 0 ? 1 : 0)
            CampfireView(state: .catching, scale: scale, coalDepth: coalDepth)
                .opacity(settling == 1 ? 1 : 0)
            EmberView(scale: scale)
                .opacity(settling == 2 ? 1 : 0)
        }
        .animation(
            reduceMotion
                ? .easeInOut(duration: 0.4)
                : .easeInOut(duration: RibbonMotion.becomeDuration * 0.44),
            value: settling)
        .task {
            try? await Task.sleep(for: .milliseconds(600))
            if reduceMotion {
                // One quiet cross-fade; the intermediate flare is motion.
                settling = 2
                return
            }
            settling = 1
            try? await Task.sleep(for: .milliseconds(1100))
            settling = 2
        }
        .accessibilityElement()
        .accessibilityLabel("The fire settles into an ember.")
    }
}
