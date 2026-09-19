import SwiftUI

/// The tour's progress: one hairline segment per card, filling on the
/// settle curve — held still under reduce motion — with the way back on
/// the left and sign-in on the right. No system glyphs: the chevron is the
/// product's own.
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
                    Capsule()
                        .fill(index <= currentStep ? Palette.chartreuse : Palette.rule)
                        .frame(height: 3)
                }
            }
            .animation(RibbonMotion.settle(still: reduceMotion), value: currentStep)
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
