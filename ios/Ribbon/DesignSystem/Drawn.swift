import SwiftUI
import RibbonCore

// Things drawn in the room's own idiom (A67, I41).
//
// The last platform defaults on You and the settings screens were Apple's
// switch, slider and time wheel, and a circle with a check in it — the
// parts of the app that looked assembled rather than made (§17's last
// question). They are drawn here instead, out of the same few things the
// rest of the room is made of: paper, wells, the rule, ivory, chartreuse,
// and one new shape — the end of a ribbon.
//
// None of them plays a haptic. §9.3 and I25 keep the haptic list closed,
// and the selection tick a drawn control would reach for is the first
// thing it names as left off.

// MARK: - The ribbon's end (A67)

/// A straight ribbon end: a band as wide as its rect, ending in a
/// swallowtail. The Wave's own tails made straight — "straight reads calm
/// and bookish" (brief §5) — with the notch the same depth the Wave's are,
/// a little under half the ribbon's width (≈0.43 there; 0.45 here, where
/// there is no curve to soften it). Hard edges: no stroke, no gradient, no
/// shadow. Android draws the same five points (`Drawn.kt`).
struct RibbonTail: Shape {
    /// The notch's depth, as a fraction of the ribbon's width.
    var notch: CGFloat = 0.45

    func path(in rect: CGRect) -> Path {
        let depth = min(notch * rect.width, rect.height)
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY - depth))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

/// A ribbon hanging from an edge — a tile's top, the hairline across You's
/// flyleaf, the rule over the ink picker. It lays in by growing down from
/// that edge and lifts out the same way back up, so it reads as a ribbon
/// laid into a page rather than a mark switched on. Under reduce motion it
/// is drawn at full length and only fades (§11).
///
/// The caller says whether it is down (`laid`), how long after that turns
/// true it begins (a stagger across a row), and on which curve. A change of
/// length runs on the same curve; under reduce motion one length
/// cross-fades into the other rather than the ribbon growing.
///
/// Decoration: hidden from the screen reader and from the finger. The row
/// it marks says its state itself — `.isSelected`, a label.
struct HangingRibbon: View {
    var color: Color
    var width: CGFloat
    var length: CGFloat
    var laid = true
    var delay: Double = 0
    var motion: Animation = RibbonMotion.handled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack(alignment: .top) {
            if reduceMotion {
                // Each length is its own ribbon, so a new length fades in
                // over the old one instead of the ribbon being pulled.
                tail(length)
                    .id(length)
                    .transition(.opacity)
            } else {
                tail(length)
            }
        }
        .frame(width: width, height: length, alignment: .top)
        // Not quite nothing: a scale of zero is a matrix with no inverse,
        // and the progress bar's fill makes the same choice.
        .scaleEffect(x: 1, y: laid || reduceMotion ? 1 : 0.001, anchor: .top)
        .opacity(laid ? 1 : 0)
        .animation((reduceMotion ? RibbonMotion.arrive : motion).delay(laid ? delay : 0), value: laid)
        .animation(reduceMotion ? RibbonMotion.arrive : motion, value: length)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func tail(_ length: CGFloat) -> some View {
        RibbonTail()
            .fill(color)
            .frame(width: width, height: length)
    }
}

/// The chosen one of several (A67): a ribbon laid into the tile from its
/// top edge, where the circle and check used to be. A row becoming chosen
/// has its ribbon laid in, on the hand's spring — a thing the size of a
/// hand, settling into place; the row losing the choice has its ribbon
/// lifted out the same way. `SettingChoice` hangs it at the trailing side,
/// the current room in Your rooms at the leading.
struct ChoiceRibbon: View {
    var laid: Bool
    var color: Color = Palette.chartreuse
    var width: CGFloat = 10
    var length: CGFloat = 22

    var body: some View {
        HangingRibbon(color: color, width: width, length: length, laid: laid, motion: RibbonMotion.handled)
    }
}

// MARK: - The switch (I41)

/// A switch drawn on the page (I41): a 46 × 28 capsule. Off, it is a well
/// with a paper knob at its leading end; on, the well fills with the
/// accent and the knob — now the ground's own colour, as the way-in
/// button's words are on the accent — sits at the trailing end. The knob
/// travels on the fingertip's spring; the fill comes up on `arrive`. Under
/// reduce motion the knob does not travel: it fades out of one end and
/// into the other, as the Segments pill does.
///
/// A picture of a state, hidden from the screen reader: the `Toggle` it
/// is drawn for says on or off itself.
struct RibbonSwitch: View {
    var isOn: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    static let width: CGFloat = 46
    static let height: CGFloat = 28
    private static let knobSize: CGFloat = 22
    private static let inset: CGFloat = 3
    private static var far: CGFloat { width - knobSize - inset }

    var body: some View {
        ZStack(alignment: .leading) {
            Color.clear
                .frame(width: Self.width, height: Self.height)
                .well(TileShape(Self.height / 2))
            Capsule()
                .fill(Palette.chartreuse)
                .frame(width: Self.width, height: Self.height)
                .opacity(isOn ? 1 : 0)
                .animation(RibbonMotion.arrive, value: isOn)
            if reduceMotion {
                knob(on: false)
                    .offset(x: Self.inset)
                    .opacity(isOn ? 0 : 1)
                knob(on: true)
                    .offset(x: Self.far)
                    .opacity(isOn ? 1 : 0)
            } else {
                knob(on: isOn)
                    .offset(x: isOn ? Self.far : Self.inset)
            }
        }
        .frame(width: Self.width, height: Self.height)
        .animation(reduceMotion ? RibbonMotion.arrive : RibbonMotion.touched, value: isOn)
        .accessibilityHidden(true)
    }

    private func knob(on: Bool) -> some View {
        Circle()
            .fill(Palette.surface)
            .overlay { Circle().strokeBorder(Palette.rule, lineWidth: 1) }
            .overlay {
                // The knob's colour is a change of light, not of place: it
                // keeps its curve wherever the knob is.
                Circle()
                    .fill(Palette.ground)
                    .opacity(on ? 1 : 0)
                    .animation(RibbonMotion.arrive, value: on)
            }
            .frame(width: Self.knobSize, height: Self.knobSize)
    }
}

/// The toggle style every switch in the app wears (I41): the label, air,
/// and the drawn switch, and the whole row takes the tap — the row is the
/// control, the switch only shows where it stands. The row's insets and
/// height are the style's to draw, so the part a finger can take is the
/// whole tile and not the text inside its margins.
///
/// The `Toggle` is kept underneath, so what a screen reader is told — a
/// switch, its label, on or off — is the system's own and not a copy of it.
struct RibbonToggleStyle: ToggleStyle {
    var insets = EdgeInsets()
    var minHeight: CGFloat = 0

    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 12) {
            configuration.label
            Spacer(minLength: 8)
            RibbonSwitch(isOn: configuration.isOn)
        }
        .padding(insets)
        .frame(maxWidth: .infinity, minHeight: minHeight, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture { configuration.isOn.toggle() }
    }
}

// MARK: - A drag on a page that scrolls (I41)

/// What the drawn slider and the band share about a drag: how far a finger
/// moves before it is a drag at all — the app's own drags wait 8 to 12
/// points — and which way it went. Sideways is the control's; up or down
/// is the page's.
private enum PageDrag {
    static let slop: CGFloat = 10

    static func isSideways(_ translation: CGSize) -> Bool {
        abs(translation.width) > abs(translation.height)
    }
}

// MARK: - The slider (I41, A69)

/// A slider drawn on the page (I41): a well with a picture of the scale's
/// small end at one side and its large end at the other, and between them
/// the rule, the accent up to the thumb, and a paper thumb. Text's size,
/// spacing, weight, letter spacing and margins are each one (A69).
///
/// It moves through positions, not numbers: the caller's scale says what
/// each position is (`PageType`'s scales, the size's 25 half points). A
/// mark on the rule shows where Book is, where Book is not an end — the
/// word the three stops used to say.
///
/// The thumb follows the finger exactly, with nothing easing it: while it
/// is held it is where the finger is, and a finger lifting off a step
/// leaves nothing to settle. While it is held the position is the
/// slider's own, told to `onHold` so the screen can show the page it
/// makes; it is written once, when the finger lifts. A tap, or a screen
/// reader's step, is written at once.
///
/// Nothing moves on a touch alone. The well sits in a page that scrolls,
/// and a thumb that lands on it on its way up the page is the page's: a
/// tap sets the position where the finger lifts, a drag takes the thumb
/// once it is plainly sideways, and a drag that is plainly up or down is
/// left to the scroll.
///
/// To a screen reader it is one adjustable element: its label, its value
/// said the caller's way ("19 point", "1.72, Book"), and a swipe up or
/// down moves one position.
struct RibbonSlider<Leading: View, Trailing: View>: View {
    @Binding var index: Int
    var count: Int
    var mark: Int?
    var label: String
    var spoken: (Int) -> String
    var onHold: (Int?) -> Void
    var leading: Leading
    var trailing: Trailing

    /// Whether the drag under way is the slider's — decided once, on its
    /// first movement, and nil between drags.
    @GestureState private var sideways: Bool? = nil
    /// The position under a finger that is still down, not yet written.
    @State private var held: Int?

    private static let thumb: CGFloat = 24

    init(
        index: Binding<Int>, count: Int, mark: Int? = nil,
        label: String, spoken: @escaping (Int) -> String,
        onHold: @escaping (Int?) -> Void = { _ in },
        @ViewBuilder leading: () -> Leading, @ViewBuilder trailing: () -> Trailing
    ) {
        self._index = index
        self.count = count
        self.mark = mark
        self.label = label
        self.spoken = spoken
        self.onHold = onHold
        self.leading = leading()
        self.trailing = trailing()
    }

    /// Where the thumb is: under the finger while it is held.
    private var shown: Int { held ?? index }

    var body: some View {
        HStack(spacing: 12) {
            leading
                .dynamicTypeSize(.large)
                .accessibilityHidden(true)
            GeometryReader { proxy in
                track(width: proxy.size.width, height: proxy.size.height)
            }
            trailing
                .dynamicTypeSize(.large)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .well(.small)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(label)
        .accessibilityValue(spoken(shown))
        .accessibilityAdjustableAction { direction in
            if direction == .increment {
                write(index + 1)
            } else if direction == .decrement {
                write(index - 1)
            }
        }
        // A drag the scroll took away ends without `onEnded`: what it held
        // is written all the same.
        .onChange(of: sideways) { _, now in
            if now == nil { release() }
        }
    }

    private func track(width: CGFloat, height: CGFloat) -> some View {
        let thumb = Self.thumb
        let travel = max(1, width - thumb)
        let centre = thumb / 2 + travel * CGFloat(fraction(shown))
        return ZStack(alignment: .leading) {
            Rectangle()
                .fill(Palette.rule)
                .frame(height: 1)
            if let mark, mark > 0, mark < count - 1 {
                Rectangle()
                    .fill(Palette.muted)
                    .frame(width: 1, height: 8)
                    .offset(x: thumb / 2 + travel * CGFloat(fraction(mark)) - 0.5)
            }
            Rectangle()
                .fill(Palette.chartreuse)
                .frame(width: centre, height: 2)
            Circle()
                .fill(Palette.surface)
                .overlay { Circle().strokeBorder(Palette.rule, lineWidth: 1) }
                .frame(width: thumb, height: thumb)
                .offset(x: centre - thumb / 2)
        }
        .frame(width: width, height: height)
        .contentShape(Rectangle())
        .onTapGesture(coordinateSpace: .local) { location in
            write(position(location.x, travel: travel))
        }
        // Alongside the page's scroll rather than in place of it, so that a
        // drag up or down still moves the page.
        .simultaneousGesture(
            DragGesture(minimumDistance: PageDrag.slop)
                .updating($sideways) { drag, sideways, _ in
                    if sideways == nil { sideways = PageDrag.isSideways(drag.translation) }
                }
                .onChanged { drag in
                    guard sideways ?? PageDrag.isSideways(drag.translation) else { return }
                    let under = position(drag.location.x, travel: travel)
                    guard under != held else { return }
                    var still = Transaction()
                    still.disablesAnimations = true
                    withTransaction(still) { held = under }
                    onHold(under)
                }
                .onEnded { _ in release() }
        )
    }

    /// The position under a point on the track.
    private func position(_ x: CGFloat, travel: CGFloat) -> Int {
        guard count > 1 else { return 0 }
        let along = min(max((x - Self.thumb / 2) / travel, 0), 1)
        return Int((Double(along) * Double(count - 1)).rounded())
    }

    /// Where a position sits along the track, 0 to 1.
    private func fraction(_ position: Int) -> Double {
        guard count > 1 else { return 0 }
        return min(max(Double(position) / Double(count - 1), 0), 1)
    }

    /// The finger has lifted: what it held is written, once.
    private func release() {
        guard let last = held else { return }
        held = nil
        write(last)
        onHold(nil)
    }

    /// A position, held to the ends, written only when it is a different
    /// one — the binding persists the whole state.
    private func write(_ proposed: Int) {
        let next = min(max(proposed, 0), max(count - 1, 0))
        guard next != index else { return }
        var still = Transaction()
        still.disablesAnimations = true
        withTransaction(still) { index = next }
    }
}

// MARK: - The slider's ends (A69)

/// The pictures at a slider's two ends: each a drawing of the scale's small
/// or large end, not a word, so none is in Copy and nobody hears them. Held
/// at one type size by the slider, as What's New holds its pictures.
enum SliderEnd {
    /// A letter, or two, set in the reader's typeface: Text size's A's,
    /// Weight's light and heavy a, Letter spacing's close and open ab.
    static func letters(
        _ text: String, size: CGFloat, weight: Int = 400,
        face: PageFace = PageFaces.literata, tracking: CGFloat = 0
    ) -> some View {
        Text(verbatim: text)
            .font(RibbonType.scripture(size, weight: weight, face: face))
            .tracking(tracking)
            .foregroundStyle(Palette.muted)
    }

    /// Three short rules, close or open: Line spacing's ends.
    static func rules(gap: CGFloat) -> some View {
        VStack(spacing: gap) {
            ForEach(0..<3, id: \.self) { _ in
                Rectangle().fill(Palette.muted).frame(width: 14, height: 1)
            }
        }
        .frame(width: 14)
    }

    /// A tiny page with narrow or wide margins: Margins' ends.
    static func page(inset: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 1.5)
            .strokeBorder(Palette.muted, lineWidth: 1)
            .frame(width: 14, height: 18)
            .overlay {
                VStack(spacing: 3) {
                    ForEach(0..<3, id: \.self) { _ in
                        Rectangle().fill(Palette.muted).frame(height: 1)
                    }
                }
                .padding(.horizontal, inset)
            }
    }
}

// MARK: - Quiet hours, drawn as the night (A67)

/// The quiet hours as one band (A67), where there were two rows and a
/// wheel under each: a day laid out from noon to noon, the waking hours
/// raised, the quiet stretch banked — the ground with the paper's grain,
/// the dark part of the day — and a handle at each end of it. The
/// arithmetic is the core's (`QuietHoursBand`), so a handle sits on the
/// same place for the same time on both phones.
///
/// A finger anywhere on the band takes the nearer handle (the end handle
/// when they are level, as they are when there are no quiet hours) and
/// moves it to the quarter hour under it, with nothing easing it — on a
/// tap, where the finger lifts, and on a drag once it is plainly sideways.
/// A touch alone moves nothing, and a drag up or down is the page's: the
/// band is in a page that scrolls, and a scroll that happened to start on
/// it must not change when the phone stays quiet. A screen reader finds
/// the two handles as two adjustable elements, the beginning first, each
/// moving by a quarter of an hour and stopping at the band's ends.
struct QuietHoursBandView: View {
    @Binding var start: Int
    @Binding var end: Int
    /// The handle a finger took when it came down; nil between drags. Gesture
    /// state, so a drag the scroll view takes away leaves nothing held.
    @GestureState private var held: Handle? = nil
    /// Whether the drag under way is the band's — decided once, on its
    /// first movement, and nil between drags.
    @GestureState private var sideways: Bool? = nil

    private enum Handle { case start, end }

    static let height: CGFloat = 44

    init(start: Binding<Int>, end: Binding<Int>) {
        self._start = start
        self._end = end
    }

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { proxy in
                band(width: proxy.size.width)
            }
            .frame(height: Self.height)
            marks
        }
    }

    private func band(width: CGFloat) -> some View {
        let shape = TileShape.small.shape
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .leading) {
                Palette.raised
                ForEach(QuietHoursBand.spans(start: start, end: end), id: \.self) { span in
                    Palette.ground
                        .frame(width: max(0, CGFloat(span.to - span.from) * width))
                        .offset(x: CGFloat(span.from) * width)
                }
                Image("PaperGrain")
                    .resizable(resizingMode: .tile)
                    .opacity(0.035)
                    .allowsHitTesting(false)
            }
            .clipShape(shape)
            .overlay { shape.strokeBorder(Palette.rule, lineWidth: 1) }
            .accessibilityHidden(true)

            handle(.start, minute: start, width: width)
            handle(.end, minute: end, width: width)
        }
        .frame(width: width, height: Self.height)
        .contentShape(Rectangle())
        .onTapGesture(coordinateSpace: .local) { location in
            guard width > 0 else { return }
            set(nearer(to: location.x, width: width), to: QuietHoursBand.minute(at: Double(location.x / width)))
        }
        // Alongside the page's scroll rather than in place of it, so that a
        // drag up or down still moves the page.
        .simultaneousGesture(drag(width: width))
        .accessibilityElement(children: .contain)
    }

    /// A 6 × 30 ivory capsule in a 44-point column: the column is what a
    /// screen reader's cursor frames, and what a finger aims at.
    private func handle(_ which: Handle, minute: Int, width: CGFloat) -> some View {
        let x = min(max(CGFloat(QuietHoursBand.position(of: minute)) * width, 3), max(3, width - 3))
        return Capsule()
            .fill(Palette.text)
            .frame(width: 6, height: 30)
            .frame(width: 44, height: Self.height)
            .contentShape(Rectangle())
            .accessibilityElement()
            .accessibilityLabel(which == .start ? Copy.quietHoursBegin : Copy.quietHoursEnd)
            .accessibilityValue(Self.clock(minute))
            .accessibilityAdjustableAction { direction in
                if direction == .increment {
                    move(which, by: 1)
                } else if direction == .decrement {
                    move(which, by: -1)
                }
            }
            .accessibilitySortPriority(which == .start ? 1 : 0)
            .position(x: x, y: Self.height / 2)
    }

    /// The three hours under the band — six in the evening, midnight, six
    /// in the morning — each a tick at the band's lower edge and the hour
    /// as the reader's own clock says it, hour only. A picture of where the
    /// night falls; the handles say the times themselves.
    private var marks: some View {
        PlacedAcross(fractions: QuietHoursBand.marks.map { QuietHoursBand.position(of: $0) }) {
            ForEach(QuietHoursBand.marks, id: \.self) { minute in
                VStack(spacing: 3) {
                    Rectangle()
                        .fill(Palette.rule)
                        .frame(width: 1, height: 5)
                    SmallCaps(Self.hour(minute), size: 10)
                }
            }
        }
        // They grow with the reader's type up to a size at which three of
        // them still fit side by side on the narrowest phone, and stop
        // there rather than running into one another: they are a picture
        // of the day, and the handles and the title above say the times.
        .dynamicTypeSize(...DynamicTypeSize.accessibility3)
        .accessibilityHidden(true)
    }

    /// The handle is chosen once, where the finger came down, and kept for
    /// the whole drag — chosen afresh each time, the start handle dragged
    /// past the end one would hand the drag over to it halfway.
    private func drag(width: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: PageDrag.slop)
            .updating($held) { drag, held, _ in
                if held == nil { held = nearer(to: drag.startLocation.x, width: width) }
            }
            .updating($sideways) { drag, sideways, _ in
                if sideways == nil { sideways = PageDrag.isSideways(drag.translation) }
            }
            .onChanged { drag in
                guard width > 0, sideways ?? PageDrag.isSideways(drag.translation) else { return }
                let which = held ?? nearer(to: drag.startLocation.x, width: width)
                set(which, to: QuietHoursBand.minute(at: Double(drag.location.x / width)))
            }
    }

    private func nearer(to x: CGFloat, width: CGFloat) -> Handle {
        let fromStart = abs(CGFloat(QuietHoursBand.position(of: start)) * width - x)
        let fromEnd = abs(CGFloat(QuietHoursBand.position(of: end)) * width - x)
        return fromStart < fromEnd ? .start : .end
    }

    private func move(_ which: Handle, by steps: Int) {
        set(which, to: QuietHoursBand.stepped(which == .start ? start : end, by: steps))
    }

    /// Written through only when the minute is a different one: the
    /// binding persists and re-registers push, and a finger held still on
    /// the band sends a change every frame.
    private func set(_ which: Handle, to minute: Int) {
        let edge = which == .start ? $start : $end
        guard minute != edge.wrappedValue else { return }
        var still = Transaction()
        still.disablesAnimations = true
        withTransaction(still) { edge.wrappedValue = minute }
    }

    /// A minute of the day as the reader's clock says it ("10:00 PM",
    /// "22:00"): the handles' spoken values, and the tile's title.
    static func clock(_ minute: Int) -> String {
        date(minute).formatted(date: .omitted, time: .shortened)
    }

    /// The hour alone ("6 PM", "18"), for the marks.
    static func hour(_ minute: Int) -> String {
        date(minute).formatted(.dateTime.hour())
    }

    /// A minute of the day on a day the clocks do not change: today, an
    /// hour that a change of clocks skips would be read as the hour after.
    private static func date(_ minute: Int) -> Date {
        let m = QuietHoursBand.wrapped(minute)
        let day = Date(timeIntervalSinceReferenceDate: 43_200)
        return Calendar.current.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: day) ?? day
    }
}

/// Subviews set along a width, each centred over its fraction of it and
/// hung from the top: marks under a band, wherever the band says they go.
/// As tall as the tallest, so the hours under the quiet-hours band grow
/// with Dynamic Type instead of spilling out of a fixed row. It does not
/// stop them meeting side by side; the caller keeps them narrow enough.
struct PlacedAcross: Layout {
    var fractions: [Double]

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let natural = sizes.reduce(0) { $0 + $1.width }
        let width = proposal.width.map { $0.isFinite ? $0 : natural } ?? natural
        return CGSize(width: width, height: sizes.map(\.height).max() ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for index in subviews.indices {
            let fraction = index < fractions.count ? fractions[index] : 0
            subviews[index].place(
                at: CGPoint(x: bounds.minX + bounds.width * CGFloat(fraction), y: bounds.minY),
                anchor: .top, proposal: .unspecified)
        }
    }
}

// MARK: - A notification, shown before it arrives (A67)

/// What a switch's notification will say, in a room of two: the other
/// person's face and the sentence that will arrive (S19, A67). A picture
/// of the notification, so a switch says what it lets through rather than
/// describing it.
struct SettingExample {
    var person: Person?
    var ink: Ink?
    var image: UIImage?
    var sentence: String
}

/// The example drawn: a small well inside the tile, the face at 16 points
/// and the sentence beside it. One element to a screen reader, said as an
/// example ("It reads: …") so it is not mistaken for a notification that
/// has come.
struct SettingExampleView: View {
    var example: SettingExample

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            PortraitView(person: example.person, ink: example.ink, size: 16, image: example.image)
                .padding(.top, 1)
            Text(example.sentence)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.text.opacity(0.8))
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .well(.small)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Copy.notificationExampleSpoken(example.sentence))
    }
}
