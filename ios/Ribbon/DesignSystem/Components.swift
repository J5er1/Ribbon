import SwiftUI
import RibbonCore

// Small shared pieces: portraits, note marks, ink dots, the wide way-in
// control, the quiet control, text fields, section headers. Each one is
// specified somewhere in the build book; the section is cited where it
// matters. Screens compose these rather than restyling — that is how the
// app reads as one made thing rather than as assembled defaults (§17).

/// A person's face — or, without a portrait, a monogram in their ink (§03).
/// A room of two assigns no ink, so the fallback is a stable ink derived
/// from the person's id — the same on every device, never a choice.
struct PortraitView: View {
    var person: Person?
    var ink: Ink?
    var size: CGFloat = 44
    var image: UIImage?

    private var monogramInk: Ink {
        ink ?? person.map { Ink.stable(for: $0.id) } ?? .clay
    }

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
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .foregroundStyle(monogramInk.color)
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
struct NoteMark: View {
    var kind: NoteKind
    var ink: Ink
    var found: Bool
    var mine: Bool
    var pending: Bool

    @State private var breathing = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var baseOpacity: Double {
        if mine { return 0.8 }
        if found { return 0.55 }
        // Under Reduce Motion the mark holds at its bright end, so an
        // unfound note never reads as a found one (§11).
        if reduceMotion { return 1.0 }
        return breathing ? 1.0 : 0.65
    }

    var body: some View {
        Group {
            switch kind {
            case .voice:
                if pending {
                    Circle().strokeBorder(ink.color, lineWidth: 0.7)
                } else {
                    Circle().fill(ink.color)
                }
            case .written:
                Circle().strokeBorder(ink.color, lineWidth: pending ? 0.7 : 1.4)
            }
        }
        .frame(width: 6, height: 6)
        .opacity(pending ? 0.9 : baseOpacity)
        .onAppear {
            guard !mine, !found, !pending, !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 4).repeatForever(autoreverses: true)) {
                breathing = true
            }
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

/// The one primary capsule (S01's way in, and every other primary
/// control): chartreuse, ivory-on-ground text. Used as a Button label and,
/// through `PrimaryCapsuleLabel`, as a ShareLink or PasteButton label so
/// the two never drift apart.
struct PrimaryCapsuleLabel: View {
    var title: String

    var body: some View {
        Text(title)
            .font(RibbonType.uiMedium(18))
            .foregroundStyle(Palette.ground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .background(Palette.chartreuse, in: Capsule())
            .contentShape(Capsule())
    }
}

/// The way in (S01): one wide control. A control says exactly what happens.
struct WayInButton: View {
    var title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            PrimaryCapsuleLabel(title: title)
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

/// The way back on a pushed screen, and the way out of a sheet on a canvas
/// with no edge to swipe from (§05, §11 motor). Small caps, top corner,
/// 44 pt. Never a system chevron — the app has no navigation bars.
struct BackControl: View {
    var title: String = Copy.back
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            SmallCaps(title, size: 12, color: Palette.muted)
                .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .accessibilityLabel(title == Copy.back ? Copy.backLabel : title)
    }
}

/// A section header the way a book sets a running head: small caps,
/// muted, with a fixed breath beneath it.
struct SectionHeader: View {
    var title: String

    init(_ title: String) { self.title = title }

    var body: some View {
        SmallCaps(title, size: 12)
            .padding(.bottom, 8)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The text field, once: ivory text on the surface, a hairline rule, the
/// prompt in muted. Every field in the app is this one (S13, S15, S16,
/// S17, S18), so a person learns it once.
struct RibbonTextField: View {
    var prompt: String
    @Binding var text: String
    var centered = false
    var size: CGFloat = 17

    var body: some View {
        TextField("", text: $text, prompt: Text(prompt).foregroundStyle(Palette.muted))
            .font(RibbonType.ui(size))
            .foregroundStyle(Palette.text)
            .multilineTextAlignment(centered ? .center : .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Palette.rule, lineWidth: 1))
            .accessibilityLabel(prompt)
    }
}

/// A settings row that goes somewhere: the title, and a hand-drawn
/// disclosure — a hairline ring, not the system chevron.
struct SettingRow: View {
    var title: String

    var body: some View {
        HStack {
            Text(title)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
            Spacer()
            Circle()
                .strokeBorder(Palette.rule, lineWidth: 1.2)
                .frame(width: 10, height: 10)
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }
}

/// A switch drawn for the room: a small capsule, chartreuse when on, the
/// rule when off. System toggles are green-and-white chrome in a
/// candlelit room (§17 — "obviously made by a person").
struct RibbonToggleStyle: ToggleStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        Button {
            configuration.isOn.toggle()
        } label: {
            HStack {
                configuration.label
                Spacer()
                ZStack(alignment: configuration.isOn ? .trailing : .leading) {
                    Capsule()
                        .fill(configuration.isOn ? Palette.chartreuse.opacity(0.85) : Palette.raised)
                        .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: 1))
                        .frame(width: 40, height: 24)
                    Circle()
                        .fill(configuration.isOn ? Palette.ground : Palette.muted)
                        .frame(width: 18, height: 18)
                        .padding(3)
                }
                .animation(reduceMotion ? nil : RibbonMotion.arrive, value: configuration.isOn)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // A switch, to VoiceOver: on or off, not "selected" (§11).
        .accessibilityAddTraits(.isToggle)
        .accessibilityValue(configuration.isOn ? Copy.toggleOn : Copy.toggleOff)
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

    /// The one sheet treatment: the unlit ground behind the content, and
    /// the grain (S13–S18). `fitted` keeps a small sheet small on iPad.
    func ribbonSheet(fitted: Bool = false) -> some View {
        modifier(RibbonSheet(fitted: fitted))
    }

    /// A cross-fade for Ribbon-drawn appearances that honors Reduce
    /// Motion: a slide becomes a fade (§11).
    func ribbonTransition(_ edge: Edge, reduceMotion: Bool) -> some View {
        transition(reduceMotion ? .opacity : .move(edge: edge).combined(with: .opacity))
    }
}

private struct RibbonSheet: ViewModifier {
    var fitted: Bool

    func body(content: Content) -> some View {
        if fitted {
            content
                .room()
                .presentationBackground(Palette.ground)
                .presentationSizing(.fitted)
        } else {
            content
                .room()
                .presentationBackground(Palette.ground)
        }
    }
}
