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
        ZStack {
            // A fire that changes state in front of you — banked by
            // somebody's quiet day, caught again by a reading — cross-fades
            // from the fire it was instead of being redrawn as another
            // between two frames. Both share the seed, so under the fade the
            // two breathe in step and only the state differs. A fade is
            // light rather than movement: it holds under reduce motion.
            flames(state)
                .id(state)
                .transition(.opacity)
        }
        .animation(RibbonMotion.settle, value: state)
        .frame(maxWidth: 560)
        .frame(height: frameHeight)
        .opacity(dimmed ? 0.92 : 1)
        .animation(RibbonMotion.arrive, value: dimmed)
        .accessibilityElement()
        .accessibilityLabel(Copy.fireIs(state.displayName))
    }

    @ViewBuilder
    private func flames(_ state: FireState) -> some View {
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
