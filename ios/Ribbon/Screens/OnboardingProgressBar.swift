import SwiftUI

/// The tour's progress: one hairline segment per card, the accent filling
/// along each on the settle curve — brightening in place under reduce
/// motion — with the way back on the left and sign-in on the right. No
/// system glyphs: the chevron is the product's own. One bar for the whole
/// thread (OnboardingFlow), so the fill is seen to move.
struct OnboardingProgressBar: View {
    let currentStep: Int
    let totalSteps: Int
    var onBack: (() -> Void)?
    var onSignIn: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 12) {
            if let onBack {
                BackChevron(action: onBack)
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
            HStack(spacing: 6) {
                ForEach(0..<totalSteps, id: \.self) { index in
                    let filled = index <= currentStep
                    Capsule()
                        .fill(Palette.rule)
                        .overlay(alignment: .leading) {
                            // The accent runs along the segment the way the
                            // thread runs, and back again going back.
                            Capsule()
                                .fill(Palette.chartreuse)
                                .scaleEffect(x: filled || reduceMotion ? 1 : 0.001, y: 1, anchor: .leading)
                                .opacity(filled ? 1 : 0)
                        }
                        .frame(height: 3)
                }
            }
            .animation(RibbonMotion.settle, value: currentStep)
            .accessibilityHidden(true)
            if let onSignIn {
                Button(action: onSignIn) {
                    SmallCaps(Copy.signIn, size: 12, color: Palette.muted)
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }
}
