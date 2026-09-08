import SwiftUI

/// A sleek, segmented progress indicator for Ribbon's onboarding tour,
/// rendered in warm chartreuse/ember tones over dark ground.
struct OnboardingProgressBar: View {
    let currentStep: Int
    let totalSteps: Int
    var onBack: (() -> Void)?
    var onSignIn: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Palette.muted)
                            .frame(width: 32, height: 32)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                } else {
                    Spacer().frame(width: 32)
                }

                HStack(spacing: 6) {
                    ForEach(0..<totalSteps, id: \.self) { index in
                        Capsule()
                            .fill(index <= currentStep ? Palette.chartreuse : Palette.rule)
                            .frame(height: 4)
                            .animation(RibbonMotion.settle, value: currentStep)
                    }
                }

                if let onSignIn {
                    Button(action: onSignIn) {
                        SmallCaps(Copy.signIn, size: 12, color: Palette.muted)
                            .frame(height: 32)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                } else {
                    Spacer().frame(width: 32)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
        }
    }
}
