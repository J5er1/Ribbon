import SwiftUI
import RibbonCore

// One card of the tour (S17): a picture of the thing, then the true small
// thing about it. The pictures are the product's own objects — the Wave,
// a page with someone on it, a note in a margin, the fire in its well —
// drawn the way the product draws them. No glow behind the mark, no
// gradients, no system glyphs (§13).

struct OnboardingTourCard: View {
    let index: Int

    var body: some View {
        VStack(spacing: 28) {
            Spacer()
            featureVisual
                .frame(height: 220)
                .accessibilityHidden(true)
            VStack(spacing: 14) {
                Text(title)
                    .font(RibbonType.display(24))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
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
        case 0: visionVisual
        case 1: presenceVisual
        case 2: notesVisual
        case 3: fireVisual
        default: EmptyView()
        }
    }

    // The mark, on the bare ground. Nothing behind it — a glow behind the
    // Wave is the first thing on the never-ship list.
    private var visionVisual: some View {
        WaveMark()
            .frame(width: 90, height: 90)
    }

    // A page, and someone on it: the presence lozenge as the reading
    // surface draws it.
    private var presenceVisual: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("In the beginning was the Word, and the Word was with God, and the Word was God.")
                .font(RibbonType.scripture(17))
                .foregroundStyle(Palette.text.opacity(0.85))
                .lineSpacing(6)
                .padding(.horizontal, 20)
            HStack {
                Spacer()
                HStack(spacing: 8) {
                    PortraitView(person: Person(name: "Ruth"), ink: .teal, size: 18)
                    Text(Copy.walkthroughPresenceSample)
                        .font(RibbonType.ui(13))
                        .foregroundStyle(Palette.text)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .paper(.init(RibbonShape.row))
            }
            .padding(.trailing, 16)
        }
        .padding(.vertical, 16)
        .well(.card)
        .padding(.horizontal, 20)
    }

    // A verse with a mark in its margin, and the note open under it: the
    // waveform in the author's ink, no duration anywhere.
    private var notesVisual: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 10) {
                NoteMark(kind: .voice, ink: .ochre, found: false, mine: false, pending: false)
                    .padding(.top, 8)
                Text("The Light shines in the darkness, and the darkness has not overcome it.")
                    .font(RibbonType.scripture(17))
                    .foregroundStyle(Palette.text.opacity(0.85))
                    .lineSpacing(5)
            }
            .padding(.horizontal, 16)
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    PortraitView(person: Person(name: "Ruth"), ink: .ochre, size: 18)
                    SmallCaps(Copy.walkthroughNoteSample, size: 12)
                }
                WaveformView(peaks: Self.samplePeaks, ink: .ochre, progress: 0.35, onScrub: { _ in }, onTap: {})
                    .allowsHitTesting(false)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .paper(.row)
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 16)
        .well(.card)
        .padding(.horizontal, 20)
    }

    // The fire itself, in its well, as the hearth draws it.
    private var fireVisual: some View {
        VStack(spacing: 10) {
            CampfireView(state: .burning, scale: .medium, coalDepth: 0.4)
            HairlineRule()
                .scaleEffect(x: 0.62, y: 1)
            SmallCaps(FireState.burning.displayName, size: 13)
        }
        .padding(.vertical, 18)
        .frame(maxWidth: .infinity)
        .well(.card)
        .padding(.horizontal, 40)
    }

    private static let samplePeaks: [Float] = (0..<40).map { i in
        0.25 + 0.55 * Float(abs(sin(Double(i) * 0.7) * cos(Double(i) * 0.23)))
    }
}
