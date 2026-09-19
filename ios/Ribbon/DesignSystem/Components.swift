import SwiftUI
import RibbonCore

// Small shared pieces: portraits, note marks, ink dots, the wide way-in
// control. Each one is specified somewhere in the build book; the section
// is cited where it matters.

/// A person's face — or, without a portrait, a monogram in their ink.
struct PortraitView: View {
    var person: Person?
    var ink: Ink?
    var size: CGFloat = 44
    var image: UIImage?

    var body: some View {
        ZStack {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                Circle().fill(Palette.raised)
                Text(person?.monogram ?? "·")
                    .font(RibbonType.uiMedium(size * 0.42))
                    .foregroundStyle((ink?.color ?? Palette.muted))
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityLabel(person?.name ?? "")
    }
}

/// The marks in the gutter (§4.4): a solid 6 pt dot for a voice note, an
/// open 6 pt ring (1.4 pt stroke) for a written one, in the author's ink.
/// Unfound marks breathe — 0.65 → 1.0 over 4 s, eased both ways, slow
/// enough that it never reads as an alert. Your own marks never breathe.
/// Pending marks render hairline until they land.
///
/// One shape, not two (ledger A38): the dot and the ring are the same
/// circle with a different stroke, so a mark that changes kind or lands
/// morphs rather than swaps, and the breath fades out on found rather than
/// stopping between two frames.
struct NoteMark: View {
    var kind: NoteKind
    var ink: Ink
    var found: Bool
    var mine: Bool
    var pending: Bool

    @State private var breathing = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var breathes: Bool { !mine && !found && !pending && !reduceMotion }

    private var baseOpacity: Double {
        if pending { return 0.9 }
        if mine { return 0.8 }
        if found { return 0.55 }
        return breathing ? 1.0 : 0.65
    }

    /// The stroke: hairline while pending, the ring's weight for a written
    /// note, and the whole radius — a filled dot — for a voice note.
    private var lineWidth: CGFloat {
        if pending { return 0.7 }
        return kind == .voice ? 3 : 1.4
    }

    var body: some View {
        Circle()
            .strokeBorder(ink.color, lineWidth: lineWidth)
            .frame(width: 6, height: 6)
            .opacity(baseOpacity)
            .animation(RibbonMotion.settle(still: reduceMotion), value: lineWidth)
            .animation(RibbonMotion.settle(still: reduceMotion), value: found)
            .onAppear { setBreath() }
            .onChange(of: breathes) { _, _ in setBreath() }
            .accessibilityHidden(true)
    }

    private func setBreath() {
        if breathes {
            withAnimation(.easeInOut(duration: 4).repeatForever(autoreverses: true)) {
                breathing = true
            }
        } else {
            withAnimation(RibbonMotion.settle(still: reduceMotion)) { breathing = false }
        }
    }
}

/// A 6 pt ink dot — the quiet marker on the room's waiting rows (S01).
struct InkDot: View {
    var ink: Ink
    var body: some View {
        Circle().fill(ink.color).frame(width: 6, height: 6)
    }
}

/// The way in (S01): one wide control. A control says exactly what happens.
struct WayInButton: View {
    var title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(RibbonType.uiMedium(18))
                .foregroundStyle(Palette.ground)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 15)
                .background(Palette.chartreuse, in: Capsule())
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .hoverEffect(.lift)
    }
}

/// A quiet, low-emphasis text control — small caps, muted: "Mark a quiet
/// day", "set it down", "Send it again". Quiet in emphasis, not in touch:
/// the visible text stays small, the tappable area meets the 44 pt
/// minimum (a finger's tap is a blunt thing; a control that only a Pencil
/// can hit is broken).
struct QuietControl: View {
    var title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            SmallCaps(title, size: 13, color: Palette.muted)
                .frame(minHeight: 44)
                .contentShape(Rectangle().inset(by: -8))
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
    }
}

/// A hairline rule at the measure's width.
struct HairlineRule: View {
    var body: some View {
        Rectangle().fill(Palette.rule).frame(height: 1)
    }
}

extension View {
    /// One readable column, centered. The book designs phone screens; on
    /// an iPad the same layouts otherwise stretch edge to edge — 150-plus
    /// character Scripture lines, a way-in capsule a thousand points wide.
    /// A no-op at phone widths, so nothing branches on size class.
    func readableColumn(maxWidth: CGFloat = 620) -> some View {
        frame(maxWidth: maxWidth)
            .frame(maxWidth: .infinity)
    }
}
