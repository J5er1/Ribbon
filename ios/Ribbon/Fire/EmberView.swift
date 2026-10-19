import SwiftUI
import RibbonCore

// An ember — what you keep when you finish a book (§4.8). The flame goes
// down; the light stays. On the shelf, embers sit on a shared baseline with
// a faint warm bloom beneath them: light on a surface implies the surface,
// so no shelf is drawn.
//
// The ember is the fire's last flame, gone still and kept (I46): the same
// glass the flames are, lit from inside and dark at its tip, with a rounded
// foot of its own. It used to be a smooth oval with one seam of light
// across it, cross-faded in over the fire, so the finishing showed one
// picture giving way to another. Now the flame it was is the flame it is.
// Each reading's ember leans and swells a little its own way — seeded by
// the reading, so the one that forms at the end of the book is the one on
// the shelf — and is the size its book's fire was, so Isaiah still looks
// like Isaiah.

struct EmberView: View {
    var scale: FireScale
    /// This ember's shape: `EmberView.shape(of:)` of its reading. Zero for
    /// an ember that stands for no reading in particular.
    var shape: Double = 0

    @State private var seed = Double.random(in: 0..<1000)
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var sizeClass

    /// Diameter relative to the fire the ember was. The whole point of the
    /// shelf is that Isaiah looks like Isaiah — and on iPad the ember
    /// carries the fire's same 1.45× (CampfireView), so the finishing
    /// settle doesn't shrink mid-become.
    var size: CGFloat {
        scale.frameHeight * 0.30 * (sizeClass == .regular ? 1.45 : 1)
    }

    var body: some View {
        Group {
            if reduceMotion {
                Canvas { context, canvasSize in
                    Self.draw(in: &context, size: canvasSize, time: seed, shape: shape)
                }
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 12.0)) { timeline in
                    Canvas { context, canvasSize in
                        Self.draw(
                            in: &context, size: canvasSize,
                            time: timeline.date.timeIntervalSinceReferenceDate * 0.25 + seed,
                            shape: shape)
                    }
                }
            }
        }
        .frame(width: size * 1.7, height: size * 1.35)
        .accessibilityHidden(true)
    }

    /// A reading's ember shape, the same on every launch: from the id's own
    /// bytes, never its `hashValue`, which is seeded afresh each run.
    static func shape(of readingID: UUID) -> Double {
        let b = readingID.uuid
        let n = (UInt32(b.0) << 24) | (UInt32(b.1) << 16) | (UInt32(b.2) << 8) | UInt32(b.3)
        return Double(n % 100_000) / 100
    }

    /// Where the glass stands in an ember's canvas of `size`, offset by
    /// `origin` when that canvas sits inside a larger one: centred, a
    /// little above the middle, as the ember has always sat.
    static func target(in size: CGSize, origin: CGPoint = .zero, shape: Double) -> FirePainter.EmberTarget {
        let r = min(size.width, size.height) * 0.32
        return FirePainter.EmberTarget(
            foot: CGPoint(x: origin.x + size.width / 2, y: origin.y + size.height * 0.52 + r * 0.62),
            height: r * 1.85,
            halfWidth: r * 0.74,
            shape: shape)
    }

    /// The glass's outline in an ember's canvas — the ember itself, without
    /// the light it throws.
    static func outline(in size: CGSize, shape: Double = 0) -> Path {
        FirePainter.emberGlass(target(in: size, shape: shape)).outline
    }

    /// The bloom beneath — the light that implies the shelf.
    ///
    /// A pool, not a slab (A74). The bloom was an ellipse wider and deeper
    /// than the ember's own canvas, filled with a round gradient still well
    /// on at the ellipse's rim and at the canvas's sides and floor; the
    /// canvas cut it there, so every ember stood on a lit rectangle, and two
    /// large ones in a shelf row were two rectangles with a dark seam
    /// between them — a drawn shelf, the one thing this light is here to
    /// make unnecessary. Now the gradient is squashed into an ellipse that
    /// fits: as wide as the canvas, and as deep below the light's centre as
    /// the canvas goes, the same above. Its own clear is the rim, which meets
    /// the frame only where the light has gone, so nothing lit touches an
    /// edge and the seams go with it.
    static func drawBloom(in context: inout GraphicsContext, size: CGSize, origin: CGPoint = .zero, time: Double, strength: Double = 1) {
        let cx = origin.x + size.width / 2
        let r = min(size.width, size.height) * 0.32
        let pulse = 0.85 + 0.1 * FirePainter.breath(time, 0.37)
        let centre = CGPoint(x: cx, y: origin.y + size.height * 0.8)
        let across = min(r * 2.4, size.width / 2)
        let deep = origin.y + size.height - centre.y
        guard across > 0, strength > 0 else { return }
        // A copy carries the squash and leaves the context as it was.
        var squashed = context
        squashed.translateBy(x: centre.x, y: centre.y)
        squashed.scaleBy(x: 1, y: deep / across)
        squashed.fill(
            Path(CGRect(x: -across, y: -across, width: across * 2, height: across * 2)),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.flameDeep.opacity(0.22 * pulse * strength),
                    .clear,
                ]),
                center: .zero,
                startRadius: 0, endRadius: across))
    }

    /// The ember, drawn into its own canvas: its light, and its glass.
    /// `light` is how much of the light it throws is up — all of it, unless
    /// a picture is bringing it up.
    static func draw(in context: inout GraphicsContext, size: CGSize, time: Double, shape: Double = 0, light: Double = 1) {
        drawBloom(in: &context, size: size, time: time, strength: light)
        let glass = FirePainter.emberGlass(target(in: size, shape: shape))
        let pulse = 0.85 + 0.1 * FirePainter.breath(time, 0.37)
        FirePainter.drawGlass([glass], in: &context, glow: pulse, detailed: glass.halfWidth > 6)
    }
}

/// The finishing sequence's centerpiece (§6.5): the fire, drawn large one
/// last time, becoming an ember when the book is put on the shelf, and the
/// ember going into the room's jar (I46).
///
/// One drawing, from the fire to the jar. The flames calm and stop moving,
/// the side flames fold into the tallest, and the tallest is carried —
/// shrinking, rising, its foot rounding — to where the ember sits, while
/// the light draws in and the bed goes dark. The flames and the ember are
/// the same glass, so nothing gives way to anything. Then the room's jar
/// comes up under it with everything the room has read already in it, and
/// the ember drops in — falling, from the mouth on, under the jar's own
/// physics, onto the others — and the jar's light swells as it lands.
///
/// The jar is drawn below the fire's frame, over the book's name and the
/// controls, which have gone by then; the frame itself keeps the fire's
/// height, so the page is laid out as it always was.
///
/// It waits until it is asked: the book is finished by being put on the
/// shelf, not by being scrolled to the end of.
struct FireBecomesEmber: View {
    var scale: FireScale
    var coalDepth: Double
    /// The ember's shape: `EmberView.shape(of:)` of this reading.
    var shape: Double
    /// What the room's jar holds already.
    var jar: [JarEmber]
    /// Put on the shelf: the fire becomes an ember now, in front of the
    /// reader, and once (§9.1).
    var begin: Bool
    /// The ember is in the jar, and has been seen to land: the book can go.
    var onSettled: () -> Void = {}

    /// When the becoming began; nil while it is still the fire.
    @State private var beganAt: Date?
    /// It is an ember, and done moving.
    @State private var settled: Bool
    @State private var seed = Double.random(in: 0..<1000)
    /// The jar's bodies, and when the ember landed among them.
    @State private var drop = Drop()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.horizontalSizeClass) private var sizeClass

    private final class Drop {
        let physics = JarPhysics()
        var released = false
        var landedAt: TimeInterval?
    }

    // The beats, in seconds from the tap.
    /// The jar starts to come up as the ember finishes forming.
    private static let jarRises = (from: 2.0, to: 2.7)
    /// The ember is let go, and reaches the jar's mouth.
    private static let falls = (from: 2.85, to: 3.2)
    /// The book goes.
    private static let done = 4.9
    /// Under reduce motion: the ember, then the jar, each a fade.
    private static let stillDone = 2.2

    /// `alreadyAnEmber`: the book was finished before it was opened this
    /// time. Fire → ember happens once per book (§9.1), so a finished book
    /// read again ends on its ember rather than burning down a second time.
    init(
        scale: FireScale, coalDepth: Double, shape: Double, jar: [JarEmber] = [],
        begin: Bool, alreadyAnEmber: Bool, onSettled: @escaping () -> Void = {}
    ) {
        self.scale = scale
        self.coalDepth = coalDepth
        self.shape = shape
        self.jar = jar
        self.begin = begin
        self.onSettled = onSettled
        _settled = State(initialValue: alreadyAnEmber)
    }

    /// The fire's frame, as CampfireView sets it, and the ember's, as
    /// EmberView does: the ember forms centred in the fire's frame, where
    /// the finishing has always shown it.
    private var frameHeight: CGFloat {
        scale.frameHeight * (sizeClass == .regular ? 1.45 : 1)
    }
    private var emberSize: CGSize {
        let s = frameHeight * 0.30
        return CGSize(width: s * 1.7, height: s * 1.35)
    }
    /// The jar, as wide as the shelf draws it, and where it stands: under
    /// the frame, over where the name and the controls were.
    private static let jarWidth: CGFloat = 120
    private var jarSize: CGSize { CGSize(width: Self.jarWidth, height: Self.jarWidth / JarPainter.aspect) }
    private var stageHeight: CGFloat { frameHeight + 14 + jarSize.height }

    var body: some View {
        Color.clear
            .frame(maxWidth: 560)
            .frame(height: frameHeight)
            .overlay(alignment: .top) {
                stage
                    .frame(height: stageHeight)
                    .allowsHitTesting(false)
            }
            .task(id: begin) {
                guard begin else { return }
                if !settled {
                    if reduceMotion {
                        withAnimation(.easeInOut(duration: 0.4)) { beganAt = .now }
                        try? await Task.sleep(for: .seconds(Self.stillDone))
                    } else {
                        beganAt = .now
                        try? await Task.sleep(for: .seconds(Self.done))
                    }
                    settled = true
                }
                guard !Task.isCancelled else { return }
                onSettled()
            }
            .accessibilityElement()
            .accessibilityLabel(beganAt != nil || settled
                ? Copy.theFireSettlesIntoAnEmber
                : Copy.fireIs(FireState.steady.displayName))
    }

    @ViewBuilder
    private var stage: some View {
        if reduceMotion {
            // Held still: the fire, then the ember where it stood, then the
            // jar with it inside — light changing, nothing moving (§11).
            TimelineView(.periodic(from: .now, by: 0.1)) { timeline in
                Canvas { context, size in
                    let t = beganAt.map { timeline.date.timeIntervalSince($0) } ?? (settled ? 99 : 0)
                    drawStill(in: &context, size: size, t: t)
                }
            }
        } else {
            TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { timeline in
                Canvas { context, size in
                    let now = timeline.date.timeIntervalSinceReferenceDate
                    let t = beganAt.map { timeline.date.timeIntervalSince($0) } ?? (settled ? -1 : 0)
                    draw(in: &context, size: size, t: t, now: now)
                }
            }
            .onAppear { JarMotion.shared.start() }
            .onDisappear { JarMotion.shared.stop() }
        }
    }

    private func frames(in size: CGSize) -> (origin: CGPoint, target: FirePainter.EmberTarget, jar: CGRect) {
        let ember = emberSize
        let origin = CGPoint(x: (size.width - ember.width) / 2, y: (frameHeight - ember.height) / 2)
        let target = EmberView.target(in: ember, origin: origin, shape: shape)
        let jar = CGRect(
            x: (size.width - jarSize.width) / 2, y: frameHeight + 14,
            width: jarSize.width, height: jarSize.height)
        return (origin, target, jar)
    }

    /// `t`: seconds since the book was put on the shelf. Before then, 0 —
    /// the fire; for a book that was already an ember, -1 — the ember.
    private func draw(in context: inout GraphicsContext, size: CGSize, t: Double, now: Double) {
        let (origin, target, jarRect) = frames(in: size)
        let fireSize = CGSize(width: size.width, height: frameHeight)
        if t < 0 {
            // Already an ember: as it stands on the shelf.
            var e = context
            e.translateBy(x: origin.x, y: origin.y)
            EmberView.draw(in: &e, size: emberSize, time: now * 0.25 + seed, shape: shape)
            return
        }
        let settling = min(1, t / RibbonMotion.becomeDuration)
        let carry = FirePainter.smoothstep(0.35, 1, settling)
        let fall = (t - Self.falls.from) / (Self.falls.to - Self.falls.from)

        // The jar, coming up.
        let rise = FirePainter.smoothstep(Self.jarRises.from, Self.jarRises.to, t)
        let d = drop
        if rise > 0 {
            d.physics.prepare(embers: jar, size: jarRect.size)
        }

        // The fire, becoming the ember — until the ember is let go.
        if fall <= 0 {
            if carry > 0 {
                EmberView.drawBloom(in: &context, size: emberSize, origin: origin, time: now * 0.25 + seed, strength: carry)
            }
            var fire = context
            fire.clip(to: Path(CGRect(origin: .zero, size: fireSize)))
            FirePainter.draw(
                in: &fire, size: fireSize, time: now + seed,
                state: .steady, scale: scale, coalDepth: coalDepth,
                settling: settling, ember: target)
        }

        // The ember, falling to the jar's mouth: quickening, and growing
        // small to the size it has in the jar. At the mouth, the jar's own
        // physics takes it.
        let ember = JarEmber(scale: scale, shape: shape)
        let mouth = CGPoint(x: jarRect.midX, y: jarRect.minY + JarPainter.geometry(in: jarRect.size).lipY)
        if fall > 0, fall < 1 {
            let small = d.physics.height(of: ember)
            let height = target.height + (small - target.height) * CGFloat(FirePainter.smoothstep(0, 1, fall))
            let from = CGPoint(x: target.foot.x, y: target.foot.y - target.height * JarPainter.footLift)
            let to = CGPoint(x: mouth.x, y: mouth.y - small * 0.9)
            let eased = CGFloat(fall * fall)
            var e = context
            e.translateBy(x: from.x + (to.x - from.x) * eased, y: from.y + (to.y - from.y) * eased)
            let glass = FirePainter.emberGlass(FirePainter.EmberTarget(
                foot: CGPoint(x: 0, y: height * JarPainter.footLift), height: height,
                halfWidth: height * 0.4, shape: shape))
            FirePainter.drawGlass([glass], in: &e, glow: 0.9, detailed: true)
        } else if fall >= 1, !d.released {
            d.released = true
            let small = d.physics.height(of: ember)
            let from = CGPoint(x: target.foot.x, y: target.foot.y)
            let to = CGPoint(x: mouth.x, y: mouth.y - small * 0.9)
            // Its speed at the mouth: the fall above, quickening to it.
            let speed = 2 * (to.y - from.y) / (Self.falls.to - Self.falls.from)
            d.physics.drop(ember, at: CGPoint(x: to.x - jarRect.minX, y: to.y - jarRect.minY), velocity: CGVector(dx: 0, dy: speed))
        }

        if rise > 0 {
            d.physics.step(to: now, gravity: JarMotion.shared.down)
            if d.released, d.landedAt == nil, d.physics.front.isEmpty { d.landedAt = now }
            let swell = d.landedAt.map { max(0, 1 - (now - $0) / 1.1) } ?? 0
            var j = context
            j.opacity = rise
            j.translateBy(x: jarRect.minX, y: jarRect.minY + (1 - rise) * 24)
            JarPainter.draw(
                in: &j, size: jarRect.size, time: now * 0.25 + seed,
                placed: d.physics.placed, front: d.physics.front, swell: swell)
        }
    }

    /// The same, held still: three states, each faded into the next.
    private func drawStill(in context: inout GraphicsContext, size: CGSize, t: Double) {
        let (origin, target, jarRect) = frames(in: size)
        let fireSize = CGSize(width: size.width, height: frameHeight)
        let toEmber = FirePainter.smoothstep(0, 0.4, t)
        let toJar = FirePainter.smoothstep(0.9, 1.3, t)
        if toEmber < 1 {
            var fire = context
            fire.opacity = 1 - toEmber
            FirePainter.draw(in: &fire, size: fireSize, time: seed, state: .steady, scale: scale, coalDepth: coalDepth)
        }
        if toEmber > 0, toJar < 1 {
            var e = context
            e.opacity = toEmber * (1 - toJar)
            FirePainter.draw(
                in: &e, size: fireSize, time: seed, state: .steady, scale: scale, coalDepth: coalDepth,
                settling: 1, ember: target)
            EmberView.drawBloom(in: &e, size: emberSize, origin: origin, time: seed)
        }
        if toJar > 0 {
            let all = jar + [JarEmber(scale: scale, shape: shape)]
            drop.physics.prepare(embers: all, size: jarRect.size)
            var j = context
            j.opacity = toJar
            j.translateBy(x: jarRect.minX, y: jarRect.minY)
            JarPainter.draw(in: &j, size: jarRect.size, time: seed, placed: drop.physics.placed)
        }
    }
}
