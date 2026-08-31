import SwiftUI

// Texture (build book §9.2). A tileable paper grain over the ground at 3.5%
// opacity. At #0B0B0A a flat fill reads as switched-off app chrome; the
// grain is the entire difference between a near-black screen and an unlit
// room. Applied above the ground and below all content, never over
// Scripture glyphs themselves.

struct GrainBackground: View {
    var body: some View {
        ZStack {
            Palette.ground
            Image("PaperGrain")
                .resizable(resizingMode: .tile)
                .opacity(0.035)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .ignoresSafeArea()
    }
}

extension View {
    /// The unlit room: ground plus grain behind this view.
    func room() -> some View {
        background(GrainBackground())
    }
}
