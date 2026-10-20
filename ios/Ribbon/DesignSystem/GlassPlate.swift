import SwiftUI

// The materials of the redesign (I47). Glass, dark and warm and lit along
// its top edge, for a page's tiles (`TileFinish.glass`); and, after the
// owner's turn toward the apps of the 2010s, the leather of the book's
// cover for the foot of a screen, and the stone the fire stands on — the
// glass things lie on real ones.

/// What a tile is made of. Paper, on the room; glass, on a page of
/// settings (I47).
enum TileFinish {
    case paper
    case glass
}

private struct TileFinishKey: EnvironmentKey {
    static let defaultValue = TileFinish.paper
}

extension EnvironmentValues {
    var tileFinish: TileFinish {
        get { self[TileFinishKey.self] }
        set { self[TileFinishKey.self] = newValue }
    }
}

/// A tile of glass in a tile's shape: dark and warm, edged a shade lighter.
/// Flat (I47: the owner had *"too many gradients"*).
struct GlassTile<S: InsettableShape>: View {
    var shape: S

    var body: some View {
        ZStack {
            shape.fill(Color(hex: "1A1611"))
            shape.strokeBorder(Palette.text.opacity(0.09), lineWidth: 1)
        }
    }
}

// MARK: - Leather (I47)

/// The foot of the book (I47): its leather cover across the foot of the
/// screen, the grain in it, stitched a few points in from the edge, and the
/// pages' gilt edge along the top — where the ribbon comes out. The glass
/// is for the things on it; the cover is the thing they lie on.
struct LeatherPlate: View {
    /// Its top corners, rounded almost to a half pill (*"make it almost a
    /// half pill shape"*).
    static let radius: CGFloat = 48

    var body: some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: Self.radius, topTrailingRadius: Self.radius, style: .continuous)
        ZStack(alignment: .top) {
            // The hide and its grain, a shade lighter where the light
            // falls along its top. Dim: the owner found it *"too ai
            // gradient"* bright, then asked for it *"dimmer instead of
            // getting rid of it"*.
            LinearGradient(
                colors: [Color(hex: "2A1D15"), Color(hex: "1D140E")],
                startPoint: .top, endPoint: .bottom)
            Image("PaperGrain")
                .resizable(resizingMode: .tile)
                .opacity(0.30)
                .blendMode(.overlay)
            Image("PaperGrain")
                .resizable(resizingMode: .tile)
                .scaleEffect(1.9, anchor: .topLeading)
                .opacity(0.14)
                .blendMode(.multiply)
            Stitching(radius: Self.radius - 9)
            // Deep at its foot: the cover goes into the dark at the bottom
            // of the phone (*"add a deep dark gradient on the bottom"*).
            LinearGradient(
                colors: [.clear, Color.black.opacity(0.7)],
                startPoint: UnitPoint(x: 0.5, y: 0.35), endPoint: .bottom)
            // The gilt edge of the pages, along the top, round its corners
            // and on down the phone's edges (*"the yellow wrapping the phone
            // edge"*), going into the dark with the cover.
            shape
                .strokeBorder(Color(hex: "C9A24A"), lineWidth: 2)
                .mask {
                    LinearGradient(
                        stops: [
                            .init(color: .black, location: 0),
                            .init(color: .black, location: 0.3),
                            .init(color: .clear, location: 1),
                        ],
                        startPoint: .top, endPoint: .bottom)
                }
        }
        .clipShape(shape)
        .shadow(color: .black.opacity(0.5), radius: 14, y: -6)
        .ignoresSafeArea(edges: .bottom)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The head of the book (I47): the same leather across the top of the page,
/// behind the clock, stitched, with the pages' gilt along its foot — where
/// the ribbon comes out, as it comes out of the foot in the room. The owner
/// found the page's ribbon, hanging from nothing, *"doenst match the
/// style"*.
struct LeatherHead: View {
    /// As tall as the status bar, and a few points more for the gilt.
    static func height(safeTop: CGFloat) -> CGFloat { max(safeTop, 20) + 6 }

    var body: some View {
        ZStack(alignment: .bottom) {
            // Darkest at the very top, where it goes over the edge of the
            // book; a shade lighter where the light falls, at its foot.
            LinearGradient(
                colors: [Color(hex: "120C08"), Color(hex: "241911")],
                startPoint: .top, endPoint: .bottom)
            Image("PaperGrain")
                .resizable(resizingMode: .tile)
                .opacity(0.30)
                .blendMode(.overlay)
            Image("PaperGrain")
                .resizable(resizingMode: .tile)
                .scaleEffect(1.9, anchor: .topLeading)
                .opacity(0.14)
                .blendMode(.multiply)
            Canvas { context, size in
                var path = Path()
                let y = size.height - 8
                path.move(to: CGPoint(x: 10, y: y))
                path.addLine(to: CGPoint(x: size.width - 10, y: y))
                let dash = StrokeStyle(lineWidth: 1.4, lineCap: .round, dash: [5, 4])
                context.stroke(path.offsetBy(dx: 0, dy: 1), with: .color(.black.opacity(0.6)), style: dash)
                context.stroke(path, with: .color(Color(hex: "C9A86A").opacity(0.45)), style: dash)
            }
            // The gilt, along its foot.
            Rectangle()
                .fill(Color(hex: "C9A24A"))
                .frame(height: 1.5)
        }
        .shadow(color: .black.opacity(0.55), radius: 8, y: 3)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// A line of stitching round the cover, a few points in, round its corners:
/// the thread, and the shadow each stitch leaves in the leather.
private struct Stitching: View {
    var radius: CGFloat

    var body: some View {
        Canvas { context, size in
            let inset: CGFloat = 9
            let r = min(radius, (size.width - inset * 2) / 2)
            var path = Path()
            path.move(to: CGPoint(x: inset, y: size.height))
            path.addLine(to: CGPoint(x: inset, y: inset + r))
            path.addArc(
                center: CGPoint(x: inset + r, y: inset + r), radius: r,
                startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            path.addLine(to: CGPoint(x: size.width - inset - r, y: inset))
            path.addArc(
                center: CGPoint(x: size.width - inset - r, y: inset + r), radius: r,
                startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
            path.addLine(to: CGPoint(x: size.width - inset, y: size.height))
            let dash = StrokeStyle(lineWidth: 1.4, lineCap: .round, dash: [5, 4])
            context.stroke(path.offsetBy(dx: 0, dy: 1), with: .color(.black.opacity(0.6)), style: dash)
            context.stroke(path, with: .color(Color(hex: "C9A86A").opacity(0.55)), style: dash)
        }
    }
}

/// The stone the fire stands on (I47): a flat slab seen from a little
/// above, dark at its edges and warm where the fire lights it.
struct Hearthstone: View {
    var warmth: Double = 1

    var body: some View {
        ZStack {
            // Its edge, below the top face.
            Ellipse()
                .fill(LinearGradient(
                    colors: [Color(hex: "2A211A"), Color(hex: "0E0B09")],
                    startPoint: .top, endPoint: .bottom))
                .frame(width: 300, height: 58)
                .offset(y: 9)
            // Its top face, lit.
            Ellipse()
                .fill(EllipticalGradient(
                    stops: [
                        .init(color: Color(hex: "5A4A3D").opacity(0.55 + 0.45 * warmth), location: 0),
                        .init(color: Color(hex: "3A2C21"), location: 0.45),
                        .init(color: Color(hex: "1B1511"), location: 1),
                    ],
                    center: .center, startRadiusFraction: 0, endRadiusFraction: 0.5))
                .frame(width: 300, height: 56)
            // Its grain, faint.
            Image("PaperGrain")
                .resizable(resizingMode: .tile)
                .opacity(0.25)
                .blendMode(.overlay)
                .frame(width: 300, height: 56)
                .clipShape(Ellipse())
            // The rim catching the light.
            Ellipse()
                .strokeBorder(
                    LinearGradient(
                        colors: [Palette.flameBright.opacity(0.14 * warmth), .clear],
                        startPoint: .top, endPoint: .bottom),
                    lineWidth: 1)
                .frame(width: 300, height: 56)
        }
        .accessibilityHidden(true)
    }
}
