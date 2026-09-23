import SwiftUI

// Paper and wells (build book §9.2, ledger A20/A23).
//
// Everything that is not the ground is one of two things. *Paper* sits on
// the room: a tile, a card, a sheet — surface colour with the grain on it.
// A *well* is a recess in the paper, or in the ground: the fire's hollow, a
// slider's track, a preview's frame. Both are drawn here and nowhere else.
//
// The edge. Surface and ground are 1.05:1 apart on this palette, which is
// exactly the distance a paper sheet is from a dark desk and exactly too
// little for a tile to have an edge on its own. So paper draws a one-point
// rule at its boundary. Not a border — the rule colour is the palette's own
// hairline, the same one under a section head — and it is what lets a tile
// stop where it stops.

extension View {
    /// Paper: surface, grain, a rule at the edge, in the shape the group
    /// handed this tile (or a card, on its own).
    func tile(edge: Bool = true) -> some View {
        modifier(TileModifier(recessed: false, edge: edge))
    }

    /// A well: the ground, with grain, sunk into whatever this sits on.
    func well() -> some View {
        modifier(TileModifier(recessed: true, edge: true))
    }

    /// Paper in a shape of the caller's own — a sheet, a capsule of a card.
    func paper(_ shape: TileShape, edge: Bool = true) -> some View {
        modifier(TileModifier(recessed: false, edge: edge))
            .environment(\.tileShape, shape)
    }

    func well(_ shape: TileShape) -> some View {
        modifier(TileModifier(recessed: true, edge: true))
            .environment(\.tileShape, shape)
    }
}

private struct TileModifier: ViewModifier {
    var recessed: Bool
    var edge: Bool
    @Environment(\.tileShape) private var tile

    func body(content: Content) -> some View {
        let shape = tile.shape
        content
            .clipShape(shape)
            .background {
                ZStack {
                    shape.fill(recessed ? Palette.ground : Palette.surface)
                    Image("PaperGrain")
                        .resizable(resizingMode: .tile)
                        .opacity(0.035)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                        .clipShape(shape)
                    if edge {
                        shape.strokeBorder(Palette.rule, lineWidth: 1)
                    }
                }
            }
    }
}

/// A tile that takes a press: it gives under the finger by 2.5% on a
/// critically damped spring and comes back the same way. No wash, no
/// ripple, no overshoot (§9.1). Under reduce motion it does not move — it
/// dims a little instead, because movement becomes a fade under §11, not
/// nothing, and a press that nothing answers is a press the finger has to
/// wonder about.
struct PressableStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed
        configuration.label
            .scaleEffect(pressed && !reduceMotion ? RibbonShape.pressedScale : 1)
            .opacity(pressed && reduceMotion ? 0.72 : 1)
            .animation(reduceMotion ? RibbonMotion.release : RibbonMotion.touched, value: pressed)
    }
}

extension ButtonStyle where Self == PressableStyle {
    static var pressable: PressableStyle { PressableStyle() }
}

// MARK: - The small furniture

/// A drawn chevron, 7 × 12, in the rule's weight — the one glyph the
/// settings use, because a system symbol is the one thing on these screens
/// that would look like somebody else's app.
struct Chevron: View {
    var pointsBack = false

    var body: some View {
        Path { p in
            if pointsBack {
                p.move(to: CGPoint(x: 7, y: 0))
                p.addLine(to: CGPoint(x: 1, y: 6))
                p.addLine(to: CGPoint(x: 7, y: 12))
            } else {
                p.move(to: CGPoint(x: 0, y: 0))
                p.addLine(to: CGPoint(x: 6, y: 6))
                p.addLine(to: CGPoint(x: 0, y: 12))
            }
        }
        .stroke(Palette.muted, style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
        .frame(width: 8, height: 12)
        .accessibilityHidden(true)
    }
}

/// The way back, in a 44-point target.
struct BackChevron: View {
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Chevron(pointsBack: true)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Copy.back)
    }
}

/// Air between things: a fixed breath of vertical space.
struct Air: View {
    var height: CGFloat
    init(_ height: CGFloat) { self.height = height }
    var body: some View { Color.clear.frame(height: height).accessibilityHidden(true) }
}

/// A section's name, in the running-head voice: small caps, a shade off
/// ivory, and a heading for a screen reader (§11). An optional detail
/// follows a middle dot in the muted colour — "Notifications · Mark".
struct SectionLabel: View {
    var title: String
    var detail: String?

    init(_ title: String, detail: String? = nil) {
        self.title = title
        self.detail = detail
    }

    var body: some View {
        HStack(spacing: 6) {
            SmallCaps(title, size: 12, color: Palette.text.opacity(0.72))
            if let detail {
                SmallCaps("· \(detail)", size: 12, color: Palette.muted)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

// MARK: - Groups and rows (S18–S22, ledger A23)

/// A stack of tiles with seams between them, the outer corners on the
/// group and the inner ones small, under an optional section label and
/// over an optional footnote. Each row draws its own paper in the corners
/// this group hands it, so a row is the same code alone and in company.
struct SettingsGroup<Content: View>: View {
    var title: String?
    var detail: String?
    var footnote: String?
    /// A footnote that changes while the screen is up ("This phone can
    /// sign you in now.") is announced when it does.
    var footnoteAnnounces = false
    var content: Content

    init(
        title: String? = nil, detail: String? = nil, footnote: String? = nil,
        footnoteAnnounces: Bool = false, @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.detail = detail
        self.footnote = footnote
        self.footnoteAnnounces = footnoteAnnounces
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title {
                SectionLabel(title, detail: detail)
                    .padding(.horizontal, RibbonShape.textInset)
            }
            Group(subviews: content) { subviews in
                VStack(spacing: RibbonShape.seam) {
                    ForEach(subviews.indices, id: \.self) { index in
                        subviews[index]
                            .environment(\.tileShape, RibbonShape.inGroup(index, of: subviews.count))
                    }
                }
            }
            if let footnote {
                Text(footnote)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, RibbonShape.textInset)
                    .padding(.top, 2)
                    .accessibilityAddTraits(footnoteAnnounces ? .updatesFrequently : [])
            }
        }
    }
}

/// The text of a row: a title in ivory, and under it, when there is one,
/// the true small thing about it.
struct RowText: View {
    var title: String
    var subtitle: String?
    var titleColor: Color = Palette.text

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(RibbonType.ui(17))
                .foregroundStyle(titleColor)
            if let subtitle {
                Text(subtitle)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .multilineTextAlignment(.leading)
    }
}

/// A row that leads somewhere, or does something: title, an optional
/// subtitle, an optional value in small caps, and a chevron when it leads.
struct SettingRow: View {
    var title: String
    var subtitle: String?
    var value: String?
    var chevron = true
    /// The row whose value is being set right now, in a picker opened
    /// beneath it: its value lights, so the picker says whose it is.
    var active = false
    var action: () -> Void

    init(_ title: String, subtitle: String? = nil, value: String? = nil, chevron: Bool = true, active: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.value = value
        self.chevron = chevron
        self.active = active
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                RowText(title: title, subtitle: subtitle)
                Spacer(minLength: 8)
                if let value {
                    SmallCaps(value, size: 13, color: active ? Palette.chartreuse : Palette.muted)
                        .animation(RibbonMotion.arrive, value: active)
                }
                if chevron {
                    Chevron()
                }
            }
            .padding(.horizontal, RibbonShape.textInset)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: subtitle == nil ? RibbonShape.rowHeight : RibbonShape.tallRowHeight, alignment: .leading)
            .contentShape(Rectangle())
            .tile()
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}

/// A row with a switch on it.
struct SettingSwitch: View {
    var title: String
    var subtitle: String?
    @Binding var isOn: Bool

    init(_ title: String, subtitle: String? = nil, isOn: Binding<Bool>) {
        self.title = title
        self.subtitle = subtitle
        self._isOn = isOn
    }

    var body: some View {
        Toggle(isOn: $isOn) {
            RowText(title: title, subtitle: subtitle)
        }
        .tint(Palette.chartreuse)
        .padding(.horizontal, RibbonShape.textInset)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, minHeight: subtitle == nil ? RibbonShape.rowHeight : RibbonShape.tallRowHeight, alignment: .leading)
        .tile()
    }
}

/// A row that states a fact and does nothing: a title and its value.
struct SettingValue: View {
    var title: String
    var value: String?
    var note: String?

    init(_ title: String, value: String? = nil, note: String? = nil) {
        self.title = title
        self.value = value
        self.note = note
    }

    var body: some View {
        HStack(spacing: 12) {
            RowText(title: title, subtitle: note)
            Spacer(minLength: 8)
            if let value {
                SmallCaps(value, size: 13, color: Palette.muted)
            }
        }
        .padding(.horizontal, RibbonShape.textInset)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, minHeight: note == nil ? RibbonShape.rowHeight : RibbonShape.tallRowHeight, alignment: .leading)
        .tile()
        .accessibilityElement(children: .combine)
    }
}

/// A tile with one sentence on it.
struct SettingNote: View {
    var text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(RibbonType.ui(16))
            .foregroundStyle(Palette.text)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, RibbonShape.textInset)
            .padding(.vertical, 18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .tile()
    }
}

/// A row with a control under its title — a slider, a segmented choice —
/// and room for the control to be as wide as the tile.
struct SettingControl<Control: View>: View {
    var title: String
    var subtitle: String?
    var control: Control

    init(_ title: String, subtitle: String? = nil, @ViewBuilder control: () -> Control) {
        self.title = title
        self.subtitle = subtitle
        self.control = control()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            RowText(title: title, subtitle: subtitle)
            control
        }
        .padding(.horizontal, RibbonShape.textInset)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .tile()
    }
}

/// One of several: a row that is chosen or not, with a drawn check when it
/// is. The group is the radio; each row reports its state.
struct SettingChoice: View {
    var title: String
    var subtitle: String?
    var chosen: Bool
    var action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(_ title: String, subtitle: String? = nil, chosen: Bool, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.chosen = chosen
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                RowText(title: title, subtitle: subtitle)
                Spacer(minLength: 8)
                ZStack {
                    Circle().strokeBorder(chosen ? Palette.chartreuse : Palette.rule, lineWidth: 1.5)
                    // The check draws itself in the way a pen would, and
                    // lifts off the same way when the choice moves on — a
                    // dot moving down a list, on the fingertip's spring.
                    // Under reduce motion it is simply there, fading.
                    Path { p in
                        p.move(to: CGPoint(x: 5, y: 10))
                        p.addLine(to: CGPoint(x: 8.5, y: 13.5))
                        p.addLine(to: CGPoint(x: 15, y: 7))
                    }
                    .trim(from: 0, to: chosen || reduceMotion ? 1 : 0)
                    .stroke(Palette.chartreuse, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .opacity(chosen ? 1 : 0)
                }
                .frame(width: 20, height: 20)
                .animation(reduceMotion ? RibbonMotion.arrive : RibbonMotion.touched, value: chosen)
                .accessibilityHidden(true)
            }
            .padding(.horizontal, RibbonShape.textInset)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: subtitle == nil ? RibbonShape.rowHeight : RibbonShape.tallRowHeight, alignment: .leading)
            .contentShape(Rectangle())
            .tile()
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(chosen ? [.isSelected] : [])
    }
}

/// A row on the menu that leads somewhere: the same tile, the ivory title,
/// and a chevron — the menu's own MenuRow, now on paper.
struct MenuTile: View {
    var title: String
    var subtitle: String?
    var quiet = false
    var action: () -> Void

    init(_ title: String, subtitle: String? = nil, quiet: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.quiet = quiet
        self.action = action
    }

    var body: some View {
        SettingRow(title, subtitle: subtitle, chevron: !quiet, action: action)
    }
}

// MARK: - A screen (ledger A29)

/// A pushed screen: a pinned bar carrying only the way back (and, when
/// there is one, an action on the right), then the page — a display title,
/// a lede in the muted colour, and the content — on the room's ground.
/// No icons, no system bar; the title is on the page where a book would
/// put it, not in the chrome.
struct RibbonScreen<Content: View, Actions: View>: View {
    var title: String
    var lede: String?
    var onBack: (() -> Void)?
    var actions: Actions
    var content: Content

    init(
        title: String, lede: String? = nil, onBack: (() -> Void)? = nil,
        @ViewBuilder actions: () -> Actions, @ViewBuilder content: () -> Content
    ) {
        self.title = title
        self.lede = lede
        self.onBack = onBack
        self.actions = actions()
        self.content = content()
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                if let onBack {
                    BackChevron(action: onBack)
                } else {
                    Color.clear.frame(width: 44, height: 44)
                }
                Spacer()
                actions
            }
            .padding(.horizontal, 8)
            .frame(height: 52)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(title)
                        .font(RibbonType.display(30))
                        .foregroundStyle(Palette.text)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                    if let lede {
                        Text(lede)
                            .font(RibbonType.ui(15))
                            .foregroundStyle(Palette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(.top, 6)
                    }
                    content
                        .padding(.top, 24)
                }
                .padding(.horizontal, RibbonShape.screenMargin)
                .padding(.bottom, 40)
                .readableColumn()
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .room()
        .toolbar(.hidden, for: .navigationBar)
    }
}

extension RibbonScreen where Actions == EmptyView {
    init(title: String, lede: String? = nil, onBack: (() -> Void)? = nil, @ViewBuilder content: () -> Content) {
        self.init(title: title, lede: lede, onBack: onBack, actions: { EmptyView() }, content: content)
    }
}
