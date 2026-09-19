import SwiftUI

// The launch mark (ledger A28): the Wave, unfurling. The launch screen is
// the unlit ground and nothing else, and the app's first frame draws the
// mark on it — a band of the Wave widening from its middle over 440 ms,
// then the whole mark settling from 1.04 to 1.0 over 560 ms — so the
// hand-off from the system's screen to the app's is one surface with one
// thing happening on it, and never a spinner. It leaves when the app is
// ready *and* the mark has settled. Under reduce motion the mark is simply
// there, and simply goes.

struct LaunchMark: View {
    var onSettled: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The band the mark shows through, in the Wave's own 108-unit grid:
    /// 22 units either side of the middle to begin with, 64 at the end.
    @State private var band: CGFloat = 22
    @State private var scale: CGFloat = 1.04

    var body: some View {
        ZStack {
            GrainBackground()
            WaveMark()
                .frame(width: 108, height: 108)
                .mask {
                    Rectangle()
                        .frame(width: 108, height: band)
                }
                .scaleEffect(scale)
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
        .onAppear {
            if reduceMotion {
                band = 108
                scale = 1
                onSettled()
                return
            }
            withAnimation(.easeOut(duration: 0.44)) { band = 108 }
            withAnimation(.easeOut(duration: 0.56).delay(0.44)) { scale = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { onSettled() }
        }
    }
}
