import SwiftUI
import RibbonCore

/// Visual card preview for each slide in Ribbon's progressive walkthrough tour.
struct OnboardingTourCard: View {
    let index: Int

    var body: some View {
        VStack(spacing: 28) {
            Spacer()

            // Visual feature showcase
            featureVisual
                .frame(height: 220)

            // Textual content
            VStack(spacing: 14) {
                Text(title)
                    .font(RibbonType.display(24))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)

                Text(bodyText)
                    .font(RibbonType.ui(16))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .padding(.horizontal, 16)
            }

            Spacer()
        }
    }

    private var title: String {
        switch index {
        case 0: return Copy.walkthroughVisionTitle
        case 1: return Copy.walkthroughPresenceTitle
        case 2: return Copy.walkthroughNotesTitle
        case 3: return Copy.walkthroughFireTitle
        default: return ""
        }
    }

    private var bodyText: String {
        switch index {
        case 0: return Copy.walkthroughVisionBody
        case 1: return Copy.walkthroughPresenceBody
        case 2: return Copy.walkthroughNotesBody
        case 3: return Copy.walkthroughFireBody
        default: return ""
        }
    }

    @ViewBuilder
    private var featureVisual: some View {
        switch index {
        case 0:
            visionVisual
        case 1:
            presenceVisual
        case 2:
            notesVisual
        case 3:
            fireVisual
        default:
            EmptyView()
        }
    }

    // MARK: - Slide 0: The Vision
    private var visionVisual: some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(
                        colors: [Palette.chartreuse.opacity(0.12), .clear],
                        center: .center,
                        startRadius: 20,
                        endRadius: 100
                    )
                )
                .frame(width: 180, height: 180)

            WaveMark()
                .frame(width: 90, height: 90)
        }
    }

    // MARK: - Slide 1: Presence Preview
    private var presenceVisual: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("In the beginning was the Word, and the Word was with God, and the Word was God.")
                .font(RibbonType.body(17))
                .foregroundStyle(Palette.text.opacity(0.85))
                .lineSpacing(6)
                .padding(.horizontal, 20)

            HStack(spacing: 8) {
                Spacer()
                HStack(spacing: 8) {
                    Circle()
                        .fill(Ink.teal.color)
                        .frame(width: 18, height: 18)
                        .overlay(
                            Text("R")
                                .font(RibbonType.uiMedium(10))
                                .foregroundStyle(Palette.ground)
                        )
                    Text(Copy.walkthroughPresenceSample)
                        .font(RibbonType.ui(13))
                        .foregroundStyle(Palette.text)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Palette.surface, in: Capsule())
                .overlay(
                    Capsule().strokeBorder(Ink.teal.color.opacity(0.4), lineWidth: 1)
                )
                .shadow(color: Ink.teal.color.opacity(0.15), radius: 8, x: 0, y: 3)
            }
            .padding(.trailing, 16)
        }
        .padding(.vertical, 16)
        .background(Palette.surface.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 20)
    }

    // MARK: - Slide 2: Notes Left Behind Preview
    private var notesVisual: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 10) {
                Circle()
                    .fill(Ink.ochre.color)
                    .frame(width: 7, height: 7)
                    .padding(.top, 7)

                Text("The Light shines in the darkness, and the darkness has not overcome it.")
                    .font(RibbonType.body(17))
                    .foregroundStyle(Palette.text.opacity(0.85))
                    .lineSpacing(5)
            }
            .padding(.horizontal, 16)

            // Pinned voice note preview
            HStack(spacing: 10) {
                Image(systemName: "waveform")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Ink.ochre.color)

                Text(Copy.walkthroughNoteSample)
                    .font(RibbonType.ui(13))
                    .foregroundStyle(Palette.text)

                Spacer()

                Image(systemName: "play.fill")
                    .font(.system(size: 10))
                    .foregroundStyle(Palette.ground)
                    .padding(6)
                    .background(Ink.ochre.color, in: Circle())
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Palette.raised, in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Ink.ochre.color.opacity(0.3), lineWidth: 1)
            )
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 16)
        .background(Palette.surface.opacity(0.4), in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 20)
    }

    // MARK: - Slide 3: The Shared Fire Preview
    private var fireVisual: some View {
        ZStack {
            // Warm ambient bloom
            Circle()
                .fill(
                    RadialGradient(
                        colors: [
                            Palette.flameCore.opacity(0.35),
                            Palette.flameDeep.opacity(0.15),
                            .clear
                        ],
                        center: .center,
                        startRadius: 10,
                        endRadius: 90
                    )
                )
                .frame(width: 180, height: 180)

            // Warm ember visual
            VStack(spacing: 8) {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Palette.flameBright, Palette.flameCore, Palette.flameDeep],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 44, height: 44)
                    .shadow(color: Palette.flameCore.opacity(0.6), radius: 14)

                Capsule()
                    .fill(Palette.coalDim.opacity(0.6))
                    .frame(width: 54, height: 6)
            }
        }
    }
}
