import SwiftUI
import RibbonCore

// What's new (A61, I38) — one screen, once per release, between the launch
// mark and the room.
//
// The build book puts nothing between opening the app and reading (§6.2)
// and has no tour (§6.1). The owner asked for this anyway — "just tell
// people about it when they open on a new version" — and chose a screen on
// launch. So it keeps everything else the rule protects: one screen, the
// latest release only, one tap from the room, and never in front of a
// launch that came from something the person tapped (the model decides
// that; see `AppModel.decideWhatsNew`).
//
// It is drawn on the room's own ground, with grain — not a sheet, not glass
// — because it is the room's first page, not something laid over it. Each
// thing new has a small picture made of the product's own type and inks,
// the way the tour's cards are: the page's lift, its wash, its follow line,
// moving on the house curves with a long breath between beats. No images,
// no confetti, no badges, no counts. Under reduce motion each picture is
// one still frame of how it ends, and nothing loops.
//
// Every way out — the control at the foot, a pull down past the top, the
// escape gesture, Esc on a keyboard — goes through `leaveWhatsNew`, which
// records it as seen. Nothing here waits on a timer: the button is live
// from the first frame, and the screen stays exactly as long as the person
// wants it to.

/// Where the screen sits in the window: over the room, under the launch
/// mark. It is drawn in-tree rather than as a presented cover so that the
/// mark fades straight off it, and so that a tapped notification or link
/// arriving while it is up can take it away in the same movement that
/// opens the book underneath — two presentations changing in one update is
/// one too many for SwiftUI (see RootView's invite handling).
struct WhatsNewCover: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            if let release = model.whatsNew {
                WhatsNewScreen(release: release, onLeave: { model.leaveWhatsNew() })
                    .onAppear { model.whatsNewShown() }
                    .transition(passage)
            }
        }
        .animation(RibbonMotion.settle, value: model.whatsNew)
    }

    /// It arrives by the mark lifting off it, so it does not move in. It
    /// leaves the way the book closes — down, fading — so the room it
    /// uncovers is the one it was standing in front of. Under reduce motion
    /// a page the size of the screen does not travel; it fades (§11).
    private var passage: AnyTransition {
        let removal: AnyTransition = reduceMotion
            ? .opacity
            : AnyTransition.move(edge: .bottom).combined(with: .opacity)
        return .asymmetric(insertion: .identity, removal: removal)
    }
}

struct WhatsNewScreen: View {
    let release: WhatsNewRelease
    var onLeave: () -> Void

    /// A finger is on the page — the only pull that leaves. A momentum
    /// bounce past the top is not somebody asking to go.
    @State private var fingerDown = false
    @State private var leaving = false
    /// Under VoiceOver the screen hands the listener its heading first.
    @AccessibilityFocusState private var headingFocused: Bool

    /// How far apart the three pictures start, so they breathe in turn
    /// rather than in step.
    private static let stagger: Double = 0.6
    /// How far past the top the page has to be pulled to leave: the same
    /// pull that closes the book (S02).
    private static let pullToLeave: CGFloat = 90

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    SmallCaps(Copy.whatsNewHeading, size: 13)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($headingFocused)
                    Text(Copy.whatsNewTitle)
                        .font(RibbonType.display(30))
                        .foregroundStyle(Palette.text)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 6)
                    VStack(alignment: .leading, spacing: 40) {
                        ForEach(Array(release.items.enumerated()), id: \.element) { index, item in
                            WhatsNewItemView(item: item, startsAfter: Double(index) * Self.stagger)
                        }
                    }
                    .padding(.top, 32)
                }
                .padding(.horizontal, RibbonShape.screenMargin)
                .padding(.top, 44)
                .padding(.bottom, 28)
                .readableColumn()
            }
            .scrollIndicators(.hidden)
            // Something to pull on even when all of it fits: the pull down
            // is one of the ways out.
            .scrollBounceBehavior(.always)
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top
            } action: { _, offset in
                if offset < -Self.pullToLeave, fingerDown { leave() }
            }
            .onScrollPhaseChange { _, phase in
                fingerDown = phase == .interacting || phase == .tracking
            }

            // Pinned at the foot, outside the scroll, so it is never
            // somewhere to be found.
            WayInButton(title: Copy.whatsNewDone, action: leave)
                // Esc on a hardware keyboard leaves, as it closes the book.
                .keyboardShortcut(.cancelAction)
                .padding(.horizontal, 40)
                .padding(.top, 12)
                .padding(.bottom, 20)
                .readableColumn(maxWidth: 460)
        }
        .room()
        // The system's way back for someone who cannot see the button: the
        // two-finger scrub leaves too.
        .accessibilityAction(.escape) { leave() }
        .task {
            // After the mark has faded off the screen, so the focus lands
            // on what is there.
            try? await Task.sleep(for: .seconds(RibbonMotion.settleDuration))
            if UIAccessibility.isVoiceOverRunning { headingFocused = true }
        }
    }

    /// Once, however many ways out are taken at the same moment — a pull
    /// that crosses the line keeps reporting until the page is gone.
    private func leave() {
        guard !leaving else { return }
        leaving = true
        onLeave()
    }
}

// MARK: - One thing new

/// A picture, then what it is, then the one or two lines about it. One
/// element for a screen reader, read as the title and then the body; the
/// picture is decoration and says nothing.
private struct WhatsNewItemView: View {
    let item: WhatsNewItem
    let startsAfter: Double

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            picture
                .frame(maxWidth: .infinity)
                .frame(height: Vignette.height)
                .well(.card)
                // A picture of a page, not a page: it keeps its size, as
                // the tour's pictures keep their frame, and the words about
                // it below are the ones that grow with Dynamic Type.
                .dynamicTypeSize(.large)
                .accessibilityHidden(true)
            Text(title)
                .font(RibbonType.uiMedium(19))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 16)
            Text(said)
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.muted)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 4)
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var picture: some View {
        switch item {
        case .original: HeldWordVignette(startsAfter: startsAfter)
        case .ownVersion: SameWordsVignette(startsAfter: startsAfter)
        case .followingWords: FollowingWordsVignette(startsAfter: startsAfter)
        }
    }

    private var title: String {
        switch item {
        case .original: return Copy.whatsNewOriginalTitle
        case .ownVersion: return Copy.whatsNewOwnVersionTitle
        case .followingWords: return Copy.whatsNewFollowingTitle
        }
    }

    private var said: String {
        switch item {
        case .original: return Copy.whatsNewOriginalBody
        case .ownVersion: return Copy.whatsNewOwnVersionBody
        case .followingWords: return Copy.whatsNewFollowingBody
        }
    }
}

// MARK: - The pictures

/// What the three pictures share: their size, their lines, and the page's
/// own measurements for a wash.
private enum Vignette {
    /// Within the 120–160 the screen allows: room for two labelled lines
    /// and their marks without crowding the well's edge.
    static let height: CGFloat = 140
    /// The tour's Scripture size — a page seen from a little way off.
    static let textSize: CGFloat = 17
    /// Room over the held word for the line that says it in the Greek.
    static let glossRoom: CGFloat = 30

    /// The two beats' long breath (~1.6–2 s): long enough that a picture
    /// is looked at, not watched.
    static let rest: Double = 1.6
    /// The pause after everything has settled back, before it begins again
    /// — short, because the settling was itself a beat.
    static let breath: Double = 0.4

    /// Words a picture's beat is about, marked inside its line.
    static func words(_ text: String, _ role: VignetteWords.Role) -> Text {
        Text(verbatim: text).customAttribute(VignetteWords(role: role))
    }

    /// John 1:3, as the Berean Standard has it: "Through Him" first.
    static var berean: Text {
        Text("\(words("Through Him", .from)) \(words("all things", .to)) were made")
    }

    /// The same verse in the World English: "through him" last.
    static var worldEnglish: Text {
        Text("\(words("All things", .to)) were made \(words("through him", .from))")
    }

    /// The held word in the original, as the line over the toolbar says it
    /// (§7.5, `OriginalLineView`): the word in its own face, how to say it
    /// in italic, what your version says for it, between muted dots.
    static var gloss: Text {
        let word = Text(verbatim: "λόγος").font(RibbonType.original(17, hebrew: false))
        let translit = Text(verbatim: "logos").font(RibbonType.scriptureItalic(14))
        return Text("\(word) · \(translit) · \(Text(verbatim: "Word"))")
            .font(RibbonType.ui(14))
            .foregroundStyle(Palette.muted)
    }

    /// One version's line under its name in small caps, as the panel's
    /// "In this room" names them.
    static func labelled(_ version: TranslationID, _ line: some View) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            SmallCaps(version.displayName, size: 11)
            line
        }
    }

    // The page's wash (ChapterTextView's layout manager), in the page's
    // numbers: a band at the height of the letters, bled a little, round
    // at the corners, laid down along the words with a soft tip.
    static let bleedX: CGFloat = 2
    static let bleedY: CGFloat = 1.2
    static let corner: CGFloat = 5
    static let tip: CGFloat = 10
    static let aboveBaseline: CGFloat = 0.88
    static let belowBaseline: CGFloat = 0.28

    static func band(_ box: WordsBox) -> CGRect {
        let top = max(box.rect.minY, box.baseline - textSize * aboveBaseline) - bleedY
        let bottom = min(box.rect.maxY, box.baseline + textSize * belowBaseline) + bleedY
        return CGRect(
            x: box.rect.minX - bleedX, y: top,
            width: box.rect.width + bleedX * 2, height: max(1, bottom - top))
    }

    /// The pen travelling: revealed along the words in reading order,
    /// measured in ink laid down, its leading edge running out into nothing
    /// rather than wiping across the words.
    static func paint(_ bands: [CGRect], drawn: Double, ink: Color, in context: inout GraphicsContext) {
        let wash = ink.opacity(Palette.highlightWash)
        var left = bands.reduce(CGFloat(0)) { $0 + $1.width } * CGFloat(drawn)
        for band in bands {
            let reach = max(0, min(band.width, left))
            left -= reach
            guard reach > 0 else { continue }
            let shape = Path(roundedRect: band, cornerRadius: corner)
            var pen = context
            pen.clip(to: Path(CGRect(x: band.minX, y: band.minY, width: reach, height: band.height)))
            if left > 0 || reach >= band.width {
                pen.fill(shape, with: .color(wash))
            } else {
                let tipWidth = min(tip, reach)
                let solid = (reach - tipWidth) / reach
                pen.fill(shape, with: .linearGradient(
                    Gradient(stops: [
                        .init(color: wash, location: 0),
                        .init(color: wash, location: solid),
                        .init(color: ink.opacity(0), location: 1),
                    ]),
                    startPoint: CGPoint(x: band.minX, y: band.midY),
                    endPoint: CGPoint(x: band.minX + reach, y: band.midY)))
            }
        }
    }
}

/// Which words in a picture's line a beat is about.
private struct VignetteWords: TextAttribute {
    enum Role: Hashable {
        /// The word a finger holds.
        case held
        /// The same words, in either version — where a mark lands, and
        /// where each reading line starts.
        case from
        /// Where the reading lines go next.
        case to
    }
    var role: Role
}

/// Where a line's marked words sit: the box of their letters, and the
/// baseline they stand on.
private struct WordsBox {
    var rect: CGRect
    var baseline: CGFloat
}

extension Text.Layout {
    /// The marked words, one box per line they fall on — a line at a size
    /// that wraps still gets its marks in the right places.
    fileprivate func boxes(of role: VignetteWords.Role) -> [WordsBox] {
        var boxes: [WordsBox] = []
        for line in self {
            var box: WordsBox?
            for run in line where run[VignetteWords.self]?.role == role {
                let bounds = run.typographicBounds
                if let found = box {
                    box = WordsBox(rect: found.rect.union(bounds.rect), baseline: found.baseline)
                } else {
                    box = WordsBox(rect: bounds.rect, baseline: bounds.origin.y)
                }
            }
            if let box { boxes.append(box) }
        }
        return boxes
    }
}

/// Waits a beat, and says whether the picture is still on screen to go on.
private func beat(_ seconds: Double) async -> Bool {
    try? await Task.sleep(for: .seconds(seconds))
    return !Task.isCancelled
}

// MARK: 1. The original

/// "the Word was with God": a soft press settles on "Word", the word lifts
/// as the page lifts a held verse, and the original line fades in over it.
/// Then all of it settles back.
private struct HeldWordVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var press = 0.0
    @State private var lift = 0.0
    @State private var gloss = 0.0

    var body: some View {
        Text("the \(Vignette.words("Word", .held)) was with God")
            .font(RibbonType.scripture(Vignette.textSize))
            .foregroundStyle(Palette.text)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .textRenderer(HeldWord(press: press, lift: lift, gloss: gloss))
            .padding(.top, Vignette.glossRoom)
            .padding(.horizontal, 20)
            .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        guard !reduceMotion else {
            // The still frame is the end of the beat: the word held, lifted,
            // and said in the Greek.
            press = 1
            lift = 1
            gloss = 1
            return
        }
        press = 0
        lift = 0
        gloss = 0
        guard await beat(startsAfter) else { return }
        while true {
            withAnimation(RibbonMotion.arrive) { press = 1 }
            guard await beat(RibbonMotion.arriveDuration) else { return }
            withAnimation(RibbonMotion.open) {
                lift = 1
                gloss = 1
            }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) {
                press = 0
                lift = 0
                gloss = 0
            }
            guard await beat(RibbonMotion.settleDuration + Vignette.rest) else { return }
        }
    }
}

/// Draws the held word: the press under it, the word raised off its line —
/// the page's own lift, a soft shadow and two points up — and the original
/// line above it.
private struct HeldWord: TextRenderer, Animatable {
    var press: Double
    var lift: Double
    var gloss: Double

    var animatableData: AnimatablePair<Double, AnimatablePair<Double, Double>> {
        get { AnimatablePair(press, AnimatablePair(lift, gloss)) }
        set {
            press = newValue.first
            lift = newValue.second.first
            gloss = newValue.second.second
        }
    }

    /// The press, the shadow and the line above all reach past the words.
    var displayPadding: EdgeInsets {
        EdgeInsets(top: Vignette.glossRoom + 8, leading: 80, bottom: 16, trailing: 80)
    }

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        for line in layout {
            for run in line {
                guard run[VignetteWords.self]?.role == .held else {
                    context.draw(run)
                    continue
                }
                let box = run.typographicBounds.rect
                if press > 0 {
                    // A fingertip, settling onto the word rather than
                    // landing on it: it arrives a little large and comes to
                    // rest.
                    let radius = CGFloat(17 * (1.25 - 0.25 * press))
                    let disc = CGRect(
                        x: box.midX - radius, y: box.midY - radius,
                        width: radius * 2, height: radius * 2)
                    context.fill(Path(ellipseIn: disc), with: .color(Palette.text.opacity(0.1 * press)))
                }
                var raised = context
                if lift > 0 {
                    raised.addFilter(.shadow(color: .black.opacity(0.7 * lift), radius: 8, x: 0, y: 3))
                    raised.translateBy(x: 0, y: CGFloat(-2 * lift))
                }
                raised.draw(run)
                if gloss > 0 {
                    var above = context
                    above.opacity = gloss
                    let said = above.resolve(Vignette.gloss)
                    // Rising the last few points as it comes, and riding
                    // up with the word.
                    let foot = box.minY - 8 - CGFloat(2 * lift - 4 * (1 - gloss))
                    above.draw(said, at: CGPoint(x: box.midX, y: foot), anchor: .bottom)
                }
            }
        }
    }
}

// MARK: 2. Your own version

/// The same verse in two versions: the ochre wash draws along "Through Him"
/// in one, then the same ink along "through him" in the other — the same
/// words, at opposite ends of the line.
private struct SameWordsVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var first = 0.0
    @State private var second = 0.0
    /// The wash itself, as a whole: it fades off both lines at the end of
    /// the loop rather than undrawing.
    @State private var washed = 1.0

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Vignette.labelled(.bsb, line(Vignette.berean, drawn: first))
            Vignette.labelled(.web, line(Vignette.worldEnglish, drawn: second))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    private func line(_ text: Text, drawn: Double) -> some View {
        text
            .font(RibbonType.scripture(Vignette.textSize))
            .foregroundStyle(Palette.text)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .textRenderer(WashAlong(drawn: drawn, alpha: washed, ink: Ink.ochre.color))
    }

    private func play() async {
        washed = 1
        guard !reduceMotion else {
            first = 1
            second = 1
            return
        }
        first = 0
        second = 0
        guard await beat(startsAfter) else { return }
        while true {
            withAnimation(RibbonMotion.open) { first = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.open) { second = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            // The ink lifts off both, the way a mark taken back does — a
            // fade, not the pen running backwards.
            withAnimation(RibbonMotion.settle) { washed = 0 }
            guard await beat(RibbonMotion.settleDuration) else { return }
            first = 0
            second = 0
            washed = 1
            guard await beat(Vignette.breath) else { return }
        }
    }
}

/// Lays the wash under a line's `.from` words, `drawn` of the way along.
private struct WashAlong: TextRenderer, Animatable {
    var drawn: Double
    var alpha: Double
    var ink: Color

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(drawn, alpha) }
        set {
            drawn = newValue.first
            alpha = newValue.second
        }
    }

    var displayPadding: EdgeInsets {
        EdgeInsets(top: 4, leading: 4, bottom: 4, trailing: 6)
    }

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        if drawn > 0, alpha > 0 {
            var wash = context
            wash.opacity = alpha
            Vignette.paint(layout.boxes(of: .from).map(Vignette.band), drawn: drawn, ink: ink, in: &wash)
        }
        // The words over the ink, never under it.
        for line in layout {
            context.draw(line)
        }
    }
}

// MARK: 3. Following lands on the same words

/// The same two lines, each with its reading line resting under the same
/// words. The first moves on to "all things"; then the second glides to
/// "All things" in its own line — back to its start, where those words
/// are, and nowhere near the same share of the line.
private struct FollowingWordsVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var leader = 0.0
    @State private var follower = 0.0

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Vignette.labelled(.bsb, line(Vignette.berean, travelled: leader))
            Vignette.labelled(.web, line(Vignette.worldEnglish, travelled: follower))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    private func line(_ text: Text, travelled: Double) -> some View {
        text
            .font(RibbonType.scripture(Vignette.textSize))
            .foregroundStyle(Palette.text)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .textRenderer(ReadingLine(travelled: travelled))
    }

    private func play() async {
        guard !reduceMotion else {
            leader = 1
            follower = 1
            return
        }
        leader = 0
        follower = 0
        guard await beat(startsAfter) else { return }
        while true {
            withAnimation(RibbonMotion.open) { leader = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.open) { follower = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) {
                leader = 0
                follower = 0
            }
            guard await beat(RibbonMotion.settleDuration + Vignette.breath) else { return }
        }
    }
}

/// A line's reading line: a hairline under the words, in the follow
/// thread's chartreuse (the brief's lamp, on this palette), carried from
/// the `.from` words to the `.to` words — position and width both, so it
/// always sits under words and never under a fraction of the line.
private struct ReadingLine: TextRenderer, Animatable {
    var travelled: Double

    var animatableData: Double {
        get { travelled }
        set { travelled = newValue }
    }

    var displayPadding: EdgeInsets {
        EdgeInsets(top: 0, leading: 4, bottom: 10, trailing: 4)
    }

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        for line in layout {
            context.draw(line)
        }
        guard let from = layout.boxes(of: .from).first, let to = layout.boxes(of: .to).first else { return }
        let t = CGFloat(travelled)
        func mix(_ a: CGFloat, _ b: CGFloat) -> CGFloat { a + (b - a) * t }
        let rule = CGRect(
            x: mix(from.rect.minX, to.rect.minX),
            y: mix(from.baseline, to.baseline) + Vignette.textSize * Vignette.belowBaseline + 3,
            width: mix(from.rect.width, to.rect.width),
            height: 1)
        context.fill(Path(rule), with: .color(Palette.chartreuse.opacity(0.7)))
    }
}

#Preview("What's new") {
    WhatsNewScreen(release: WhatsNew.releases[0], onLeave: {})
        .preferredColorScheme(.dark)
}
