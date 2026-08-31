import SwiftUI

// The Wave — the mark (W6 study, from /mark). Two tapered ribbons falling
// from a common edge, crossing low, each ending in a swallowtail. In the
// app it is: the only branded moment (onboarding), and the way out of the
// book (S02/S03).
//
// Occlusion, not transparency: the front ribbon knocks a hard-edged gap out
// of the back one. Drawn natively from the generated geometry so it is
// crisp at 20 pt and at 200 pt.

private func wavePath(_ segments: [WaveSegment], in rect: CGRect) -> Path {
    let scale = min(rect.width, rect.height) / WaveGeometry.gridSize
    let dx = rect.midX - WaveGeometry.gridSize * scale / 2
    let dy = rect.midY - WaveGeometry.gridSize * scale / 2
    func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
        CGPoint(x: x * scale + dx, y: y * scale + dy)
    }
    var path = Path()
    for segment in segments {
        switch segment {
        case let .move(x, y): path.move(to: point(x, y))
        case let .line(x, y): path.addLine(to: point(x, y))
        case let .quad(cx, cy, x, y): path.addQuadCurve(to: point(x, y), control: point(cx, cy))
        case .close: path.closeSubpath()
        }
    }
    return path
}

struct WaveMark: View {
    var color: Color = Palette.chartreuse

    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            let scale = min(size.width, size.height) / WaveGeometry.gridSize
            let back = wavePath(WaveGeometry.back, in: rect)
            let front = wavePath(WaveGeometry.front, in: rect)

            // Back ribbon, then the knockout: erase a stroke of the front
            // ribbon's outline from what's drawn so far, then lay the front
            // ribbon in. The gap is the ground showing through — a hard
            // edge, no opacity blending.
            context.drawLayer { layer in
                layer.fill(back, with: .color(color))
                layer.blendMode = .destinationOut
                layer.stroke(
                    front,
                    with: .color(.black),
                    style: StrokeStyle(
                        lineWidth: WaveGeometry.knockoutWidth * scale,
                        lineJoin: .miter, miterLimit: 6))
            }
            context.fill(front, with: .color(color))
        }
        .accessibilityHidden(true)
    }
}

#Preview("The Wave") {
    VStack(spacing: 40) {
        WaveMark().frame(width: 200, height: 200)
        WaveMark(color: Palette.text.opacity(0.6)).frame(width: 20, height: 20)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Palette.ground)
}
