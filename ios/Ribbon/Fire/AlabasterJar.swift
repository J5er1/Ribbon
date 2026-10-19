import CoreMotion
import SwiftUI
import RibbonCore

// The room's jar (I46): where every book the room finishes is kept. An
// alabaster jar — Mark 14:3's, the costly thing kept whole until it is
// given — of a translucent stone, so the embers in it show through its
// wall, brightest where the wall faces you and dimmer toward its edges,
// where the light goes through more stone. One jar for the room: each
// book's ember drops into it at the end of the book, and it holds more
// light the more it holds.
//
// It is drawn turned, as a thing with a body: a profile revolved about its
// axis, seen a little from above, so its mouth is an opening and its foot
// an ellipse; shaded round, lit down one side, with soft veins in the
// stone. Drawn in the same hand as the glass fire, so the two are one set.
//
// What is in it is heavy (`JarPhysics`): an ember dropped in falls, knocks
// against the others and the wall, and settles; tilt the phone and they
// roll and slide inside it, and stand up again when they stop. Held still
// under reduce motion, where every ember lies where it settled.

/// An ember in the jar: its book's size and its own shape.
struct JarEmber: Hashable {
    var scale: FireScale
    var shape: Double

    init(_ reading: Reading) {
        scale = reading.handiwork.scale
        shape = EmberView.shape(of: reading.id)
    }

    init(scale: FireScale, shape: Double) {
        self.scale = scale
        self.shape = shape
    }
}

enum JarPainter {
    /// Width over height of the jar's canvas.
    static let aspect: CGFloat = 1 / 1.5

    private static let stone = Color(hex: "EBD9B8")
    private static let stoneShade = Color(hex: "4E3A28")
    private static let vein = Color(hex: "F3E6CC")

    /// The profile, top to bottom: a fraction of the canvas's height, and
    /// the jar's radius there as a fraction of half its width.
    private static let profile: [(y: CGFloat, r: CGFloat)] = [
        (0.10, 0.40), (0.13, 0.33), (0.17, 0.27), (0.24, 0.42),
        (0.34, 0.72), (0.50, 0.86), (0.66, 0.82), (0.80, 0.68),
        (0.90, 0.54), (0.95, 0.52),
    ]
    /// How much of a circle's width its ellipse is tall, seen from a little
    /// above.
    static let tilt: CGFloat = 0.26

    /// The jar's radius at height `y` (a fraction of the canvas's height),
    /// through the profile's points on a Catmull-Rom curve.
    static func radius(at y: CGFloat) -> CGFloat {
        let p = profile
        guard y > p[0].y else { return p[0].r }
        guard y < p[p.count - 1].y else { return p[p.count - 1].r }
        let i = p.lastIndex { $0.y <= y } ?? 0
        let p0 = p[max(0, i - 1)], p1 = p[i], p2 = p[min(p.count - 1, i + 1)], p3 = p[min(p.count - 1, i + 2)]
        let t = (y - p1.y) / max(0.0001, p2.y - p1.y)
        let t2 = t * t, t3 = t2 * t
        return 0.5 * ((2 * p1.r) + (-p0.r + p2.r) * t
            + (2 * p0.r - 5 * p1.r + 4 * p2.r - p3.r) * t2
            + (-p0.r + 3 * p1.r - 3 * p2.r + p3.r) * t3)
    }

    struct Geometry {
        var cx: CGFloat
        var half: CGFloat
        var height: CGFloat
        var lipY: CGFloat
        var lipR: CGFloat
        var footY: CGFloat
        var footR: CGFloat
        var body: Path
        var mouth: Path
        /// The floor of its belly, inside.
        var floorY: CGFloat
        var bellyR: CGFloat
        /// How thick its wall is.
        var wall: CGFloat

        /// How far the inside of the wall is from the axis at `y`.
        func inner(at y: CGFloat) -> CGFloat {
            JarPainter.radius(at: y / height) * half - wall
        }
    }

    private static func ellipseArc(
        _ path: inout Path, centre: CGPoint, radius: CGFloat, from: Double, to: Double
    ) {
        path.addArc(
            center: centre, radius: radius,
            startAngle: .degrees(from), endAngle: .degrees(to), clockwise: true,
            transform: CGAffineTransform(translationX: centre.x, y: centre.y)
                .scaledBy(x: 1, y: tilt).translatedBy(x: -centre.x, y: -centre.y))
    }

    static func geometry(in size: CGSize) -> Geometry {
        let w = size.width, h = size.height
        let cx = w / 2
        let half = w / 2 * 0.96
        let top = profile[0].y, bottom = profile[profile.count - 1].y
        let steps = 40
        var left: [CGPoint] = []
        var right: [CGPoint] = []
        for s in 0...steps {
            let y = top + (bottom - top) * CGFloat(s) / CGFloat(steps)
            let r = radius(at: y) * half
            left.append(CGPoint(x: cx - r, y: y * h))
            right.append(CGPoint(x: cx + r, y: y * h))
        }
        let lipY = top * h, lipR = profile[0].r * half
        let footY = bottom * h, footR = profile[profile.count - 1].r * half
        var body = Path()
        body.move(to: left[0])
        for p in left.dropFirst() { body.addLine(to: p) }
        // The foot's near edge: the lower half of its ellipse.
        ellipseArc(&body, centre: CGPoint(x: cx, y: footY), radius: footR, from: 180, to: 0)
        for p in right.reversed().dropFirst() { body.addLine(to: p) }
        // Back across the top along the lip's far edge.
        ellipseArc(&body, centre: CGPoint(x: cx, y: lipY), radius: lipR, from: 0, to: 180)
        body.closeSubpath()
        let mouth = Path(ellipseIn: CGRect(x: cx - lipR, y: lipY - lipR * tilt, width: lipR * 2, height: lipR * 2 * tilt))
        return Geometry(
            cx: cx, half: half, height: h, lipY: lipY, lipR: lipR, footY: footY, footR: footR,
            body: body, mouth: mouth, floorY: footY - h * 0.04, bellyR: radius(at: 0.66) * half,
            wall: half * 0.07)
    }

    /// How tall the embers are in a jar holding `count`: as large as the
    /// belly allows, and smaller as it fills, so it never overflows.
    static func unit(count: Int, in g: Geometry) -> CGFloat {
        let rows = max(1, (count + 2) / 3)
        let room = g.floorY - (g.lipY + g.height * 0.22)
        return min(g.bellyR * 0.62, room / CGFloat(rows) / 0.62)
    }

    static func height(of ember: JarEmber, unit: CGFloat) -> CGFloat {
        let sized: CGFloat = switch ember.scale {
        case .small: 0.72
        case .medium: 0.86
        case .large: 1.0
        }
        return unit * sized
    }

    /// Where the `index`th ember in a jar of `count` starts: from the floor
    /// up, a few to a row. Where it starts, not where it rests — the jar's
    /// physics settles it from here.
    static func slot(_ index: Int, of count: Int, ember: JarEmber, in g: Geometry) -> (foot: CGPoint, height: CGFloat) {
        let perRow = 3
        let u = unit(count: count, in: g)
        let row = index / perRow
        let inRow = min(perRow, count - row * perRow)
        let column = index % perRow
        let spread = CGFloat(column) - CGFloat(inRow - 1) / 2
        let jitter = CGFloat(FirePainter.hash(ember.shape + 0.3) - 0.5) * u * 0.18
        return (
            CGPoint(x: g.cx + spread * u * 0.78 + jitter, y: g.floorY - CGFloat(row) * u * 0.62),
            height(of: ember, unit: u))
    }

    /// An ember where it is: the centre of its round foot, how tall it is,
    /// and how far it leans.
    struct Placed {
        var ember: JarEmber
        var centre: CGPoint
        var height: CGFloat
        var angle: Double
    }

    /// The round of an ember's foot sits this far above where its glass
    /// stands, as a fraction of its height.
    static let footLift: CGFloat = 0.15

    private static func draw(
        _ placed: [Placed], in context: inout GraphicsContext, glow: Double, detailed: Bool
    ) {
        for p in placed {
            var c = context
            c.translateBy(x: p.centre.x, y: p.centre.y)
            c.rotate(by: .radians(p.angle))
            let glass = FirePainter.emberGlass(FirePainter.EmberTarget(
                foot: CGPoint(x: 0, y: p.height * footLift), height: p.height,
                halfWidth: p.height * 0.4, shape: p.ember.shape))
            FirePainter.drawGlass([glass], in: &c, glow: glow, detailed: detailed)
        }
    }

    /// The jar, with what it keeps: `placed` inside it, seen through the
    /// stone, and `front`, anything still falling toward its mouth, in
    /// front of it. `swell` brightens its light a moment as something
    /// arrives.
    static func draw(
        in context: inout GraphicsContext, size: CGSize, time: Double,
        placed: [Placed], front: [Placed] = [], swell: Double = 0
    ) {
        let g = geometry(in: size)
        let pulse = 0.88 + 0.08 * FirePainter.breath(time, 0.19)
        // More light for more kept, and never none.
        let held = 0.35 + 0.65 * (1 - exp(-Double(placed.count) / 4))
        let light = min(1.4, held * pulse + 0.5 * swell)
        let detailed = g.half > 40

        // The light it throws on what it stands on, squashed into an
        // ellipse that fits the canvas, so its own clear is its rim and
        // nothing lit meets an edge (A74's rule for the ember's bloom).
        let poolR = min(g.half * 1.2, size.width / 2 * 0.98)
        let poolDeep = min(poolR * 0.3, (size.height - g.footY) * 0.95)
        if poolR > 0, poolDeep > 0 {
            var floor = context
            floor.blendMode = .plusLighter
            floor.translateBy(x: g.cx, y: g.footY)
            floor.scaleBy(x: 1, y: poolDeep / poolR)
            floor.fill(
                Path(CGRect(x: -poolR, y: -poolR, width: poolR * 2, height: poolR * 2)),
                with: .radialGradient(
                    Gradient(colors: [Palette.flameDeep.opacity(0.3 * light), .clear]),
                    center: .zero, startRadius: 0, endRadius: poolR))
        }
        // And its shadow, close under it.
        context.fill(
            Path(ellipseIn: CGRect(
                x: g.cx - g.footR * 1.1, y: g.footY - g.footR * tilt * 0.9,
                width: g.footR * 2.2, height: g.footR * 2 * tilt * 0.9)),
            with: .color(Color.black.opacity(0.45)))

        // Inside: what it keeps, seen through the stone.
        var inside = context
        inside.clip(to: g.body)
        // The inner light, round where the embers lie.
        let glowY = g.floorY - g.height * 0.12
        var lit = inside
        lit.blendMode = .plusLighter
        lit.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.flameCore.opacity(0.55 * light),
                    Palette.flameDeep.opacity(0.22 * light),
                    .clear,
                ]),
                center: CGPoint(x: g.cx, y: glowY), startRadius: 0, endRadius: g.half * 1.05))
        inside.drawLayer { layer in
            // A little soft: they are behind a wall of stone.
            layer.addFilter(.blur(radius: max(0.4, g.half * 0.012)))
            draw(placed, in: &layer, glow: pulse, detailed: detailed)
        }

        // The stone: translucent, rounded by its shading — darker at both
        // edges where it turns away, lightest a third of the way across
        // where the light falls on it.
        context.fill(g.body, with: .linearGradient(
            Gradient(stops: [
                .init(color: stoneShade.opacity(0.85), location: 0),
                .init(color: stone.opacity(0.4), location: 0.3),
                .init(color: stone.opacity(0.3), location: 0.55),
                .init(color: stoneShade.opacity(0.9), location: 1),
            ]),
            startPoint: CGPoint(x: g.cx - g.half, y: 0), endPoint: CGPoint(x: g.cx + g.half, y: 0)))
        // Thicker toward the bottom: the stone darkens down to the foot.
        context.fill(g.body, with: .linearGradient(
            Gradient(colors: [.clear, Color.black.opacity(0.22)]),
            startPoint: CGPoint(x: g.cx, y: g.height * 0.45), endPoint: CGPoint(x: g.cx, y: g.footY)))
        // The light through the wall, strongest where the wall faces you.
        var through = context
        through.clip(to: g.body)
        through.blendMode = .plusLighter
        through.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .radialGradient(
                Gradient(colors: [
                    Palette.flameCore.opacity(0.5 * light),
                    Palette.flameDeep.opacity(0.16 * light),
                    .clear,
                ]),
                center: CGPoint(x: g.cx, y: glowY), startRadius: 0, endRadius: g.half))
        // The stone itself is warm where light is in it: honey, not grey.
        through.fill(g.body, with: .color(Palette.flameDeep.opacity(0.1 + 0.08 * light)))

        // Veins in the stone: a few soft clouded wisps, slanting across
        // its turn.
        var veins = context
        veins.clip(to: g.body)
        veins.drawLayer { layer in
            layer.addFilter(.blur(radius: max(0.8, g.half * 0.035)))
            for k in 0..<2 {
                let fk = Double(k)
                var wisp = Path()
                for step in 0...14 {
                    let u = CGFloat(step) / 14
                    let x = g.cx - g.half * 0.8 + u * g.half * 1.6
                    let y = g.height * CGFloat(0.36 + 0.22 * fk) + (u - 0.5) * g.height * 0.16 * (k == 0 ? 1 : -0.7)
                        + CGFloat(FirePainter.noise(Double(u) * 2.4 + fk * 5, fk + 2)) * g.height * 0.035
                    if step == 0 { wisp.move(to: CGPoint(x: x, y: y)) } else { wisp.addLine(to: CGPoint(x: x, y: y)) }
                }
                layer.stroke(wisp, with: .color(vein.opacity(0.14)), lineWidth: max(1, g.half * 0.05))
            }
        }

        // The round of it: two faint bands where it turns, at the shoulder
        // and above the foot.
        for y in [g.height * 0.24, g.height * 0.86] {
            var band = Path()
            ellipseArc(&band, centre: CGPoint(x: g.cx, y: y), radius: radius(at: y / g.height) * g.half, from: 180, to: 0)
            veins.stroke(band, with: .color(Color.black.opacity(0.18)), lineWidth: max(0.5, g.half * 0.015))
        }

        // The mouth: dark inside, lit from below, and its rim catching the
        // light along its near edge.
        context.fill(g.mouth, with: .color(Color(hex: "120B07")))
        var mouthLight = context
        mouthLight.clip(to: g.mouth)
        mouthLight.blendMode = .plusLighter
        mouthLight.fill(g.mouth, with: .radialGradient(
            Gradient(colors: [Palette.flameCore.opacity(0.6 * light), .clear]),
            center: CGPoint(x: g.cx, y: g.lipY + g.lipR * tilt * 0.6), startRadius: 0, endRadius: g.lipR))
        var rim = Path()
        ellipseArc(&rim, centre: CGPoint(x: g.cx, y: g.lipY), radius: g.lipR, from: 180, to: 0)
        context.stroke(rim, with: .color(stone.opacity(0.6)), lineWidth: max(1, g.half * 0.035))
        context.stroke(g.mouth, with: .color(stone.opacity(0.25)), lineWidth: max(0.5, g.half * 0.015))

        // The polish: a long soft light down its left, and a glint on its
        // shoulder.
        var shine = context
        shine.clip(to: g.body)
        shine.blendMode = .plusLighter
        shine.drawLayer { layer in
            layer.addFilter(.blur(radius: max(1, g.half * 0.06)))
            var streak = Path()
            let steps = 20
            for s in 0...steps {
                let y = g.height * (0.2 + 0.68 * CGFloat(s) / CGFloat(steps))
                let p = CGPoint(x: g.cx - radius(at: y / g.height) * g.half * 0.58, y: y)
                if s == 0 { streak.move(to: p) } else { streak.addLine(to: p) }
            }
            layer.stroke(streak, with: .color(Palette.text.opacity(0.32)),
                         style: StrokeStyle(lineWidth: max(1.5, g.half * 0.09), lineCap: .round))
        }
        let shoulderY = g.height * 0.31
        let gx = g.cx - radius(at: 0.31) * g.half * 0.45
        shine.fill(
            Path(ellipseIn: CGRect(x: gx - g.half * 0.04, y: shoulderY - g.half * 0.05, width: g.half * 0.08, height: g.half * 0.1)),
            with: .color(Palette.text.opacity(0.4)))

        // Whatever is still falling toward its mouth is in front of it.
        draw(front, in: &context, glow: pulse, detailed: true)
    }
}

// MARK: - The weight of what is kept

/// The embers in a jar, as bodies (I46): each the round of its foot, with
/// weight, knocking against the others and the inside of the wall. A small
/// Verlet integrator — positions carry their own velocity, and contacts are
/// resolved by moving things apart — which is steady enough for a jar of
/// a few dozen, and cheap enough to run every frame the jar is seen. It
/// sleeps once nothing has moved for a while, and wakes when the phone
/// tilts or something is dropped in.
final class JarPhysics {
    struct Body {
        var ember: JarEmber
        var p: CGPoint
        var prev: CGPoint
        var r: CGFloat
        var height: CGFloat
        var angle: Double
        /// Still above the mouth: not held by the jar yet.
        var falling: Bool = false
    }

    private(set) var bodies: [Body] = []
    private var kept: [JarEmber] = []
    private var size: CGSize = .zero
    private var last: TimeInterval?
    private var still: Double = 0
    private var gravity = CGVector(dx: 0, dy: 1)

    /// What the painter draws: inside the jar, and still falling into it.
    var placed: [JarPainter.Placed] {
        bodies.filter { !$0.falling }.map { JarPainter.Placed(ember: $0.ember, centre: $0.p, height: $0.height, angle: $0.angle) }
    }
    var front: [JarPainter.Placed] {
        bodies.filter(\.falling).map { JarPainter.Placed(ember: $0.ember, centre: $0.p, height: $0.height, angle: $0.angle) }
    }

    /// The embers the jar holds, in the canvas it is drawn in. The first
    /// time, or after a resize, each is set at its slot and settled before
    /// it is seen; after that, one that arrives keeps the others where
    /// they lie.
    func prepare(embers: [JarEmber], size: CGSize) {
        guard embers != kept || size != self.size else { return }
        let g = JarPainter.geometry(in: size)
        let unit = JarPainter.unit(count: embers.count, in: g)
        let fresh = size != self.size
        var next: [Body] = []
        for (i, ember) in embers.enumerated() {
            let height = JarPainter.height(of: ember, unit: unit)
            if !fresh, i < bodies.count, bodies[i].ember == ember {
                var body = bodies[i]
                body.height = height
                body.r = height * 0.4
                next.append(body)
                continue
            }
            let slot = JarPainter.slot(i, of: embers.count, ember: ember, in: g)
            let centre = CGPoint(x: slot.foot.x, y: slot.foot.y - slot.height * JarPainter.footLift)
            next.append(Body(ember: ember, p: centre, prev: centre, r: height * 0.4, height: height, angle: 0))
        }
        // Anything dropped in and not yet among `embers` stays.
        next.append(contentsOf: bodies.dropFirst(min(bodies.count, kept.count)).filter { b in !embers.contains(b.ember) })
        bodies = next
        kept = embers
        self.size = size
        if fresh {
            gravity = CGVector(dx: 0, dy: 1)
            for _ in 0..<360 { integrate(1.0 / 120) }
            for i in bodies.indices { bodies[i].prev = bodies[i].p }
            still = 0
        }
    }

    /// Drops `ember` into the jar: its round foot at `centre`, moving at
    /// `velocity` (points per second), at the size it has in this jar.
    func drop(_ ember: JarEmber, at centre: CGPoint, velocity: CGVector) {
        let g = JarPainter.geometry(in: size)
        let unit = JarPainter.unit(count: kept.count + 1, in: g)
        let height = JarPainter.height(of: ember, unit: unit)
        let step = 1.0 / 120
        bodies.append(Body(
            ember: ember, p: centre,
            prev: CGPoint(x: centre.x - velocity.dx * step, y: centre.y - velocity.dy * step),
            r: height * 0.4, height: height, angle: 0, falling: true))
        still = 0
    }

    /// The jar's size for what is dropped in: how tall an ember is there.
    func height(of ember: JarEmber) -> CGFloat {
        let g = JarPainter.geometry(in: size)
        return JarPainter.height(of: ember, unit: JarPainter.unit(count: kept.count + 1, in: g))
    }

    /// Moves everything on to `now`, under `gravity` — which way is down
    /// on the screen, as long as it is when the phone stands upright.
    func step(to now: TimeInterval, gravity down: CGVector) {
        let turned = abs(down.dx - gravity.dx) + abs(down.dy - gravity.dy)
        gravity = down
        if turned > 0.02 { still = 0 }
        let elapsed = min(now - (last ?? now), 1.0 / 15)
        last = now
        // Asleep: nothing has moved for a second and a half.
        guard still < 1.5 else { return }
        let dt = 1.0 / 120
        var left = elapsed
        var moved: CGFloat = 0
        while left > dt * 0.5 {
            moved = max(moved, integrate(dt))
            left -= dt
        }
        still = moved < 0.02 ? still + elapsed : 0
    }

    /// One step. Returns how far the fastest thing moved.
    @discardableResult
    private func integrate(_ dt: Double) -> CGFloat {
        guard !bodies.isEmpty else { return 0 }
        let g = JarPainter.geometry(in: size)
        // Heavy, and slower than a stone in air: the jar is small, and a
        // true fall across it would be over before it was seen.
        let pull = 2.4 * g.height * CGFloat(dt * dt) * 9
        var fastest: CGFloat = 0
        for i in bodies.indices {
            var b = bodies[i]
            let vx = (b.p.x - b.prev.x) * 0.996
            let vy = (b.p.y - b.prev.y) * 0.996
            b.prev = b.p
            b.p.x += vx + gravity.dx * pull
            b.p.y += vy + gravity.dy * pull
            bodies[i] = b
        }
        for _ in 0..<4 {
            // Each other: pushed apart along the line between them.
            for i in bodies.indices {
                for j in (i + 1)..<bodies.count {
                    let dx = bodies[j].p.x - bodies[i].p.x
                    let dy = bodies[j].p.y - bodies[i].p.y
                    let reach = bodies[i].r + bodies[j].r
                    let d2 = dx * dx + dy * dy
                    guard d2 < reach * reach, d2 > 0.0001 else { continue }
                    let d = d2.squareRoot()
                    let push = (reach - d) / d * 0.5
                    bodies[i].p.x -= dx * push
                    bodies[i].p.y -= dy * push
                    bodies[j].p.x += dx * push
                    bodies[j].p.y += dy * push
                }
            }
            for i in bodies.indices { contain(&bodies[i], in: g) }
        }
        for i in bodies.indices {
            var b = bodies[i]
            let vx = b.p.x - b.prev.x
            let vy = b.p.y - b.prev.y
            fastest = max(fastest, abs(vx) + abs(vy))
            // Bottom-heavy: it stands against whichever way is down, and
            // leans the way it is moving.
            let upright = atan2(-Double(gravity.dx), Double(gravity.dy))
            let lean = max(-0.6, min(0.6, Double(vx / max(1, b.r)) * 6))
            b.angle += (upright + lean - b.angle) * 0.1
            bodies[i] = b
        }
        return fastest
    }

    /// Keeps a body inside the jar: off the floor, inside the wall where
    /// it stands, and under the mouth once it has come through it. A
    /// little bounce off what it hits, and a little friction along it.
    private func contain(_ b: inout Body, in g: JarPainter.Geometry) {
        if b.falling {
            // Free until it is through the mouth.
            if b.p.y - b.r * 0.2 > g.lipY + g.height * 0.05 { b.falling = false } else { return }
        }
        let floor = g.floorY - b.r
        if b.p.y > floor {
            let vy = b.p.y - b.prev.y
            b.p.y = floor
            b.prev.y = b.p.y + vy * 0.25
            b.prev.x = b.p.x - (b.p.x - b.prev.x) * 0.9
        }
        let lid = g.lipY + g.height * 0.05 + b.r
        if b.p.y < lid {
            b.p.y = lid
            b.prev.y = b.p.y
        }
        // The wall where this body is, at its narrowest across its height.
        let inner = min(g.inner(at: b.p.y - b.r * 0.7), g.inner(at: b.p.y), g.inner(at: b.p.y + b.r * 0.7))
        let room = inner - b.r
        let off = b.p.x - g.cx
        if room <= 0 {
            // Too narrow here — the neck: it goes down into the belly.
            b.p.x = g.cx
            b.p.y += 0.5
        } else if abs(off) > room {
            let vx = b.p.x - b.prev.x
            b.p.x = g.cx + (off > 0 ? room : -room)
            b.prev.x = b.p.x + vx * 0.25
            b.prev.y = b.p.y - (b.p.y - b.prev.y) * 0.9
        }
    }
}

/// Which way is down, from the phone's own sense of it, for the jars on
/// screen. Listens only while one is.
final class JarMotion {
    static let shared = JarMotion()
    private let manager = CMMotionManager()
    private var watching = 0

    func start() {
        watching += 1
        guard watching == 1, manager.isDeviceMotionAvailable else { return }
        manager.deviceMotionUpdateInterval = 1.0 / 30
        manager.startDeviceMotionUpdates()
    }

    func stop() {
        watching = max(0, watching - 1)
        if watching == 0 { manager.stopDeviceMotionUpdates() }
    }

    /// Down, on the screen of a phone held upright: (0, 1) when it is, and
    /// tipping as it does. Lying flat, very little — what is in the jar
    /// stays where it is.
    var down: CGVector {
        guard let g = manager.deviceMotion?.gravity else { return CGVector(dx: 0, dy: 1) }
        return CGVector(dx: g.x, dy: -g.y)
    }
}

/// The room's jar, as the shelf shows it.
struct JarView: View {
    var embers: [JarEmber]
    var width: CGFloat = 132

    @State private var seed = Double.random(in: 0..<1000)
    @State private var physics = JarPhysics()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if reduceMotion {
                Canvas { context, size in
                    physics.prepare(embers: embers, size: size)
                    JarPainter.draw(in: &context, size: size, time: seed, placed: physics.placed)
                }
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 60.0)) { timeline in
                    Canvas { context, size in
                        let now = timeline.date.timeIntervalSinceReferenceDate
                        physics.prepare(embers: embers, size: size)
                        physics.step(to: now, gravity: JarMotion.shared.down)
                        JarPainter.draw(
                            in: &context, size: size, time: now * 0.25 + seed,
                            placed: physics.placed, front: physics.front)
                    }
                }
                .onAppear { JarMotion.shared.start() }
                .onDisappear { JarMotion.shared.stop() }
            }
        }
        .frame(width: width, height: width / JarPainter.aspect)
        .accessibilityHidden(true)
    }
}
