import CoreText
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
//
// Read again (A65): the same screen, pushed from You, with every release
// on it, newest first, each under the day it came and its own title. It is
// lazy, so only the pictures on screen move. Its way out says "Done" and
// goes back to You, and it records nothing — the launch's decision is the
// model's, and this never reaches it.

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
    /// What the screen tells: on a launch, the latest release alone; read
    /// again from You, every release, newest first.
    let releases: [WhatsNewRelease]
    /// Read again from You (A65): each release under the day it came, and
    /// the way out goes back to You rather than to the room. It decides
    /// nothing and records nothing — whether a launch shows the screen is
    /// the model's alone.
    let isHistory: Bool
    var onLeave: () -> Void

    /// The launch's screen: one release, under its own title.
    init(release: WhatsNewRelease, onLeave: @escaping () -> Void) {
        self.releases = [release]
        self.isHistory = false
        self.onLeave = onLeave
    }

    /// Every release, read again from You (A65).
    init(history releases: [WhatsNewRelease], onLeave: @escaping () -> Void) {
        self.releases = releases
        self.isHistory = true
        self.onLeave = onLeave
    }

    /// A finger is on the page — the only pull that leaves. A momentum
    /// bounce past the top is not somebody asking to go.
    @State private var fingerDown = false
    @State private var leaving = false
    /// Under VoiceOver the screen hands the listener its heading first.
    @AccessibilityFocusState private var headingFocused: Bool

    /// How far apart a release's pictures start, so they breathe in turn
    /// rather than in step.
    private static let stagger: Double = 0.6
    /// How far past the top the page has to be pulled to leave: the same
    /// pull that closes the book (S02).
    private static let pullToLeave: CGFloat = 90

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                // Lazy, so that read again — every release, one under the
                // other — only the pictures on screen are drawn and moving;
                // each starts when it arrives and stops when it goes.
                LazyVStack(alignment: .leading, spacing: 0) {
                    SmallCaps(Copy.whatsNewHeading, size: 13)
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($headingFocused)
                    ForEach(Array(releases.enumerated()), id: \.element.id) { place, release in
                        heading(of: release, first: place == 0)
                        ForEach(Array(release.items.enumerated()), id: \.element) { index, item in
                            WhatsNewItemView(item: item, startsAfter: Double(index) * Self.stagger)
                                .padding(.top, index == 0 ? 32 : 40)
                        }
                    }
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
            WayInButton(title: isHistory ? Copy.whatsNewHistoryDone : Copy.whatsNewDone, action: leave)
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

    /// A release's own title, in Literata display. Read again, the day it
    /// came is over it in small caps, like the screen's heading, and the
    /// two are one heading to a screen reader — the way from one release
    /// to the next.
    private func heading(of release: WhatsNewRelease, first: Bool) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if isHistory, let day = Copy.whatsNewReleased(release.released) {
                SmallCaps(day, size: 13)
            }
            Text(Copy.whatsNewTitle(release.id))
                .font(RibbonType.display(30))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, isHistory ? (first ? 28 : 64) : 6)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isHistory ? [.isHeader] : [])
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
        case .followingStays: FollowingStaysVignette(startsAfter: startsAfter)
        case .nativeSelection: NativeSelectionVignette(startsAfter: startsAfter)
        case .roomGroups: RoomGroupsVignette(startsAfter: startsAfter)
        case .lordReadsLord: DivineNameVignette(startsAfter: startsAfter)
        case .originalReadable: ReadableLineVignette(startsAfter: startsAfter)
        case .flyleaf: FlyleafVignette(startsAfter: startsAfter)
        case .yourShelf: YourShelfVignette(startsAfter: startsAfter)
        case .versionsByReading: VersionsByReadingVignette(startsAfter: startsAfter)
        case .yourPage: YourPageVignette(startsAfter: startsAfter)
        case .typeface: TypefaceVignette(startsAfter: startsAfter)
        case .notificationsByName: NotificationsByNameVignette(startsAfter: startsAfter)
        case .quietHoursNight: QuietHoursNightVignette(startsAfter: startsAfter)
        }
    }

    private var title: String {
        switch item {
        case .original: return Copy.whatsNewOriginalTitle
        case .ownVersion: return Copy.whatsNewOwnVersionTitle
        case .followingWords: return Copy.whatsNewFollowingTitle
        case .followingStays: return Copy.whatsNewFollowingStaysTitle
        case .nativeSelection: return Copy.whatsNewSelectionTitle
        case .roomGroups: return Copy.whatsNewRoomGroupsTitle
        case .lordReadsLord: return Copy.whatsNewLordTitle
        case .originalReadable: return Copy.whatsNewReadableTitle
        case .flyleaf: return Copy.whatsNewFlyleafTitle
        case .yourShelf: return Copy.whatsNewShelfTitle
        case .versionsByReading: return Copy.whatsNewVersionsTitle
        case .yourPage: return Copy.whatsNewPageTitle
        case .typeface: return Copy.whatsNewTypefaceTitle
        case .notificationsByName: return Copy.whatsNewNotificationsTitle
        case .quietHoursNight: return Copy.whatsNewQuietHoursTitle
        }
    }

    private var said: String {
        switch item {
        case .original: return Copy.whatsNewOriginalBody
        case .ownVersion: return Copy.whatsNewOwnVersionBody
        case .followingWords: return Copy.whatsNewFollowingBody
        case .followingStays: return Copy.whatsNewFollowingStaysBody
        case .nativeSelection: return Copy.whatsNewSelectionBody
        case .roomGroups: return Copy.whatsNewRoomGroupsBody
        case .lordReadsLord: return Copy.whatsNewLordBody
        case .originalReadable: return Copy.whatsNewReadableBody
        case .flyleaf: return Copy.whatsNewFlyleafBody
        case .yourShelf: return Copy.whatsNewShelfBody
        case .versionsByReading: return Copy.whatsNewVersionsBody
        case .yourPage: return Copy.whatsNewPageBody
        case .typeface: return Copy.whatsNewTypefaceBody
        case .notificationsByName: return Copy.whatsNewNotificationsBody
        case .quietHoursNight: return Copy.whatsNewQuietHoursBody
        }
    }
}

// MARK: - The pictures

/// What the pictures share: their size, their lines, and the page's own
/// measurements for a wash.
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
        labelled(version.displayName, line)
    }

    /// A line under a name in small caps: a version's, or a person's.
    static func labelled(_ name: String, _ line: some View) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            SmallCaps(name, size: 11)
            line
        }
    }

    // MARK: Following stays with them

    /// The two people in the follow: the one reading, and you. Names in a
    /// picture, not things the app says, so they are set here with the
    /// picture's other words.
    static let reader = "Ruth"
    static let you = "You"

    /// John 1:5 as the World English begins it, in the three steps a
    /// reading line takes along it.
    static var light: Text {
        Text("\(words("The light", .step(0))) \(words("shines in", .step(1))) \(words("the darkness", .step(2)))")
    }
    static let lightSteps: [VignetteWords.Role] = [.step(0), .step(1), .step(2)]

    /// How far your row goes towards dark as a screen about to sleep:
    /// never all the way — it comes back first.
    static let sleepDim: Double = 0.7
    /// How long the dimming runs before the screen is kept on and it comes
    /// back, part of the way through `become`.
    static let dimming: Double = 1.4

    // MARK: Selecting, the way your phone does

    /// John 1:1 as it opens, after its number the way the page sets one:
    /// small caps, raised, at 45% (ChapterTextView), with a thin space.
    static var beginning: Text {
        let number = Text(verbatim: "1\u{2009}")
            .font(RibbonType.smallCaps(textSize * 0.62))
            .foregroundStyle(Palette.text.opacity(0.45))
            .baselineOffset(textSize * 0.3)
            .customAttribute(VignetteWords(role: .number))
        return Text("\(number)\(words("In the beginning", .step(0))) \(words("was the", .step(1))) \(words("Word", .held))")
    }

    /// The page's own selection: the accent the page's text view is tinted
    /// with (A62), at the strength the system lays a selection down.
    static let selectionTint: Double = 0.3
    /// The system's handles: a thin bar the height of the line, with a
    /// small round end — above the start, below the end.
    static let handleWidth: CGFloat = 2
    static let handleKnob: CGFloat = 8

    /// A fingertip, settling onto what it presses rather than landing on
    /// it: it arrives a little large and comes to rest.
    static func fingertip(on box: CGRect, press: Double, radius: CGFloat, in context: inout GraphicsContext) {
        guard press > 0 else { return }
        let size = radius * CGFloat(1.25 - 0.25 * press)
        let disc = CGRect(x: box.midX - size, y: box.midY - size, width: size * 2, height: size * 2)
        context.fill(Path(ellipseIn: disc), with: .color(Palette.text.opacity(0.1 * press)))
    }

    // MARK: However many of you there are

    /// John 1:3 in the World English as "In this room" sets another
    /// version's words against yours: the words yours shares a step back,
    /// the words that differ at full strength and Medium — and marked, for
    /// the wash that brings them forward.
    static var worldEnglishAgainstYours: Text {
        let shared = Text(verbatim: "All things were made")
            .foregroundStyle(Palette.text.opacity(0.7))
        let differs = words("through him", .from)
            .font(RibbonType.scriptureMedium(textSize))
            .foregroundStyle(Palette.text)
        return Text("\(shared) \(differs)")
    }

    /// Three of the room's inks, for three faces.
    static let faceInks: [Ink] = [.teal, .plum, .clay]
    static let face: CGFloat = 13
    static let faceStep: CGFloat = 9

    // MARK: The New King James, as printed

    /// Psalm 23:1, with the word the New King James prints as the divine
    /// name.
    static var shepherd: Text {
        Text("The \(words("Lord", .held)) is my shepherd")
    }

    /// The divine name as a printed page sets it: a capital, then capitals
    /// the height of the small letters. The page has no small capitals of
    /// its own (A64), so they are capitals set smaller, a little open.
    static var divineName: Text {
        let rest = Text(verbatim: "ORD")
            .font(RibbonType.scripture(textSize * smallCapital))
            .kerning(textSize * 0.04)
        return Text("\(Text(verbatim: "L"))\(rest)")
            .font(RibbonType.scripture(textSize))
            .foregroundStyle(Palette.text)
    }
    static let smallCapital: CGFloat = 0.78

    // MARK: The Hebrew, easy to read

    /// Exodus 15:11 as the Berean Standard has it, over two lines of a
    /// page — one text, so the two shrink together when the well is narrow:
    /// the line over the toolbar sits on the upper one, and the word held is
    /// in the lower.
    static var songOfTheSea: Text {
        Text("Who among the gods is like You, O LORD?\nWho is like You—\(words("majestic", .held)) in holiness,")
    }
    /// How far over the upper line's baseline its middle is, where the
    /// line over the toolbar is centred.
    static let lineMiddle: CGFloat = Vignette.textSize * 0.36

    /// The held word in the original, as the line over the toolbar says it:
    /// the Hebrew, then how to say it in italic and what the Berean says
    /// for it, between muted dots.
    static let hebrew = "נֶאְדָּר"
    static var hebrewSaid: Text {
        let translit = Text(verbatim: "ne’·dār").font(RibbonType.scriptureItalic(14))
        // Its first space set verbatim, so that nothing reading the line
        // as Markdown can take it off the front.
        return Text("\(Text(verbatim: " · "))\(translit) · \(Text(verbatim: "majestic"))")
            .font(RibbonType.ui(14))
            .foregroundStyle(Palette.muted)
    }
    /// The Hebrew as the line set it bare on the page, and as it sets it on
    /// its own ground (A66; `OriginalLineView.hebrewSize`).
    static let bareHebrew: CGFloat = 17
    static let readableHebrew: CGFloat = 21
    /// The bar's material as a picture can have it, with no glass to blur
    /// what is under it: the unlit ground, nearly opaque, its edge the
    /// palette's hairline.
    static let barGround: Double = 0.92
    /// Between the page's two lines: room for the line's ground to cover
    /// the middle of the upper one and stop short of the word held below.
    static let pageLeading: CGFloat = 10
    /// The line's room inside its ground, as Android's.
    static let barPadding = EdgeInsets(top: 5, leading: 16, bottom: 5, trailing: 16)

    // MARK: Your name in the front of the book

    /// Ruth's face as a portrait with no photograph draws one
    /// (`PortraitView`): her initial in her ink, on the raised ground.
    static func readerFace(_ size: CGFloat) -> some View {
        ZStack {
            Circle().fill(Palette.raised)
            Text(verbatim: String(reader.prefix(1)))
                .font(RibbonType.uiMedium(size * 0.42))
                .foregroundStyle(Ink.teal.color)
        }
        .frame(width: size, height: size)
    }

    /// A room's ribbon on the flyleaf: its ink, how far it hangs, and the
    /// room and the place it lies, as You sets them beside it.
    struct FlyleafRibbon {
        var color: Color
        var length: CGFloat
        var room: String
        var place: String
    }

    /// Three rooms, no two ribbons the same length — "two ribbons of
    /// slightly different length read as two people" (brief §5) — in the
    /// accent where nobody's ink is theirs, in teal and plum where it is.
    static let flyleaf: [FlyleafRibbon] = [
        FlyleafRibbon(color: Palette.chartreuse, length: 34, room: "us", place: "mark 4"),
        FlyleafRibbon(color: Ink.teal.color, length: 28, room: "thursday", place: "ruth 2"),
        FlyleafRibbon(color: Ink.plum.color, length: 38, room: "family", place: "john 1"),
    ]
    static let flyleafRibbonWidth: CGFloat = 12

    // MARK: Every book you have finished, on one shelf

    /// A book on the shelf: the size its fire was, its name, and who it
    /// was read with.
    struct Shelved {
        var scale: FireScale
        var book: String
        var company: String
    }

    /// A short book, a middling one and a long one, each the size its
    /// fire was (§2.9): Philemon small, Mark medium, Isaiah large.
    static let shelf: [Shelved] = [
        Shelved(scale: .small, book: "philemon", company: "with jo"),
        Shelved(scale: .medium, book: "mark", company: "with ruth"),
        Shelved(scale: .large, book: "isaiah", company: "with thursday study"),
    ]
    /// The embers at a little over half the size You draws them, in the
    /// same proportion to each other: Isaiah still looks like Isaiah.
    static let emberShrink: CGFloat = 0.6
    /// How far an ember rises onto the shelf as it comes.
    static let rise: CGFloat = 6

    // MARK: Choose a version by reading it

    /// A version's own words under its name, smaller than the page's: a
    /// specimen, as the Text screen sets one, and small enough that the
    /// longer of the two lines fits a row on the smallest phone.
    static let specimenSize: CGFloat = 14
    /// Two rows as a group sets them: round where they meet the well,
    /// small at the seam between them.
    static let upperRow = TileShape(topLeading: 14, bottomLeading: 5, bottomTrailing: 5, topTrailing: 14)
    static let lowerRow = TileShape(topLeading: 5, bottomLeading: 14, bottomTrailing: 14, topTrailing: 5)
    /// Where the words start inside a row of the picture, and how far in
    /// from its trailing edge the ribbon hangs.
    static let rowInset: CGFloat = 14
    /// The chosen marker at the picture's size: `ChoiceRibbon`'s 10 × 22,
    /// a little shorter.
    static let choiceWidth: CGFloat = 10
    static let choiceLength: CGFloat = 20

    // MARK: The page, the way you read it

    /// Smaller than the other pictures' lines: three verses must fit the
    /// well set a verse to a line, and six lines of Literata at 13 do.
    static let pageSize: CGFloat = 13

    /// A run of the picture's page: words, or a verse's number.
    enum PageRun {
        case words(String)
        case number(Int)
    }

    /// John 1:1–3 as the Berean Standard has it, set as the page sets one
    /// paragraph — the chapter's first verse without a number, the others
    /// with theirs. Set by hand, line by line, so that the heavier letters
    /// never send a word on to the next line, and the first line is the
    /// same in both settings: the page is set again under it, in place.
    static let asParagraph: [[PageRun]] = [
        [.words("In the beginning was the Word, and the Word")],
        [.words("was with God, and the Word was God. "), .number(2), .words("He was")],
        [.words("with God in the beginning. "), .number(3), .words("Through Him all")],
        [.words("things were made, and without Him nothing")],
        [.words("was made that has been made.")],
    ]

    /// The same three verses a verse to a line (A68): each numbered verse
    /// starts a line of its own, and the paragraph's first line is as it
    /// was.
    static let verseByVerse: [[PageRun]] = [
        [.words("In the beginning was the Word, and the Word")],
        [.words("was with God, and the Word was God.")],
        [.number(2), .words("He was with God in the beginning.")],
        [.number(3), .words("Through Him all things were made, and")],
        [.words("without Him nothing was made that has")],
        [.words("been made.")],
    ]

    /// The picture's page as one Text: the words in Literata at `weight`
    /// on its own axis — the page's face, held at the picture's size — and
    /// each number as the page sets one, small caps at 0.62 of the size,
    /// raised by 0.3 of it, in ivory at `numbers`.
    static func page(_ lines: [[PageRun]], weight: Int, numbers: Double) -> Text {
        let face = Font(RibbonType.uiLiterata(pageSize, weight: weight) as CTFont)
        var text = Text(verbatim: "")
        for (index, line) in lines.enumerated() {
            if index > 0 { text = Text("\(text)\n") }
            for run in line {
                switch run {
                case .words(let words):
                    text = Text("\(text)\(Text(verbatim: words).font(face))")
                case .number(let verse):
                    let number = Text(verbatim: "\(verse)\u{2009}")
                        .font(RibbonType.smallCaps(pageSize * 0.62))
                        .foregroundStyle(Palette.text.opacity(numbers))
                        .baselineOffset(pageSize * 0.3)
                    text = Text("\(text)\(number)")
                }
            }
        }
        return text
    }

    // MARK: Notifications say who

    /// Where Ruth's note was left, in the sentence the phone will say.
    static let notedAt = "Mark 4:12"
    /// The switch at the picture's size: the row's 46 × 28
    /// (`RibbonSwitch`) a little smaller, as the row's words are.
    static let switchWidth: CGFloat = 40
    static let switchHeight: CGFloat = 24
    static let switchKnob: CGFloat = 18
    static let switchInset: CGFloat = 3
    /// How far behind the pen a letter takes to come up whole, so the
    /// sentence is written in rather than wiped on.
    static let nib: CGFloat = 18

    // MARK: Quiet hours, drawn as the night

    /// The picture's night, ten in the evening to six in the morning, and
    /// the later start its handle is moved to — placed on the band from
    /// noon to noon by the core's arithmetic, as the setting's band is.
    static let nightFrom = QuietHoursBand.position(of: 22 * 60)
    static let laterFrom = QuietHoursBand.position(of: 23 * 60)
    static let nightTo = QuietHoursBand.position(of: 6 * 60)
    /// The hours under the band, in the order `QuietHoursBand.marks` sets
    /// them: words in a picture rather than the phone's clock, so both
    /// phones' pictures say the same.
    static let nightMarks = ["6 pm", "midnight", "6 am"]
    /// The setting's band at the picture's size: 44 high made 28, its
    /// corner and its handles in proportion.
    static let bandHeight: CGFloat = 28
    static let bandShape = TileShape(8)
    static let nightHandle = CGSize(width: 4, height: 18)

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
        /// One of a line's steps, in order: where a reading line goes in
        /// turn, or where a selection's start can stand.
        case step(Int)
        /// A verse's number, which a tap takes the verse by.
        case number
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
                Vignette.fingertip(on: box, press: press, radius: 17, in: &context)
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
/// one stop's words to the next — the `.from` words to the `.to` words
/// unless it is given others — position and width both, so it always sits
/// under words and never under a fraction of the line. `travelled` counts
/// stops: 1 is the second, 1.5 halfway from it to the third.
private struct ReadingLine: TextRenderer, Animatable {
    var travelled: Double
    var stops: [VignetteWords.Role] = [.from, .to]

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
        let boxes = stops.compactMap { layout.boxes(of: $0).first }
        guard boxes.count == stops.count, boxes.count > 1 else { return }
        let at = min(max(travelled, 0), Double(boxes.count - 1))
        let lower = min(Int(at.rounded(.down)), boxes.count - 2)
        let from = boxes[lower]
        let to = boxes[lower + 1]
        let t = CGFloat(at - Double(lower))
        func mix(_ a: CGFloat, _ b: CGFloat) -> CGFloat { a + (b - a) * t }
        let rule = CGRect(
            x: mix(from.rect.minX, to.rect.minX),
            y: mix(from.baseline, to.baseline) + Vignette.textSize * Vignette.belowBaseline + 3,
            width: mix(from.rect.width, to.rect.width),
            height: 1)
        context.fill(Path(rule), with: .color(Palette.chartreuse.opacity(0.7)))
    }
}

// MARK: 4. Following stays with them

/// Ruth's line and yours, the same words. Her reading line steps along it a
/// few words at a time and yours follows a beat behind, onto the same
/// words. Between the steps your row begins to dim, as a screen about to
/// sleep, and comes back before it is dark — the screen kept on.
private struct FollowingStaysVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var leader = 0.0
    @State private var follower = 0.0
    @State private var dim = 0.0

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Vignette.labelled(Vignette.reader, line(travelled: leader))
            Vignette.labelled(Vignette.you, line(travelled: follower))
                .opacity(1 - Vignette.sleepDim * dim)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    private func line(travelled: Double) -> some View {
        Vignette.light
            .font(RibbonType.scripture(Vignette.textSize))
            .foregroundStyle(Palette.text)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .textRenderer(ReadingLine(travelled: travelled, stops: Vignette.lightSteps))
    }

    private func play() async {
        guard !reduceMotion else {
            // How it ends: both lines under "the darkness", your screen on.
            leader = 2
            follower = 2
            dim = 0
            return
        }
        leader = 0
        follower = 0
        dim = 0
        guard await beat(startsAfter) else { return }
        while true {
            for step in 1...2 {
                withAnimation(RibbonMotion.open) { leader = Double(step) }
                // A beat behind: the follower's line goes where the
                // reader's went.
                guard await beat(RibbonMotion.arriveDuration) else { return }
                withAnimation(RibbonMotion.open) { follower = Double(step) }
                guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
                if step == 1 {
                    // Going down slowly, the way a screen about to sleep
                    // does, and brought back before it gets there.
                    withAnimation(RibbonMotion.become) { dim = 1 }
                    guard await beat(Vignette.dimming) else { return }
                    withAnimation(RibbonMotion.arrive) { dim = 0 }
                    guard await beat(RibbonMotion.arriveDuration + Vignette.breath) else { return }
                }
            }
            withAnimation(RibbonMotion.settle) {
                leader = 0
                follower = 0
            }
            guard await beat(RibbonMotion.settleDuration + Vignette.breath) else { return }
        }
    }
}

// MARK: 5. Selecting, the way your phone does

/// "In the beginning was the Word", after its number: a soft press on
/// "Word", the phone's own selection over it with its two handles, the
/// start handle carried back to "was the Word"; then a press on the verse's
/// number and the selection takes the whole verse. Then it lets go.
private struct NativeSelectionVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var press = 0.0
    @State private var number = 0.0
    @State private var shown = 0.0
    @State private var reach = 0.0

    var body: some View {
        Vignette.beginning
            .font(RibbonType.scripture(Vignette.textSize))
            .foregroundStyle(Palette.text)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .textRenderer(NativeSelection(press: press, number: number, shown: shown, reach: reach))
            .padding(.horizontal, 20)
            .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        guard !reduceMotion else {
            // How it ends: the whole verse selected.
            press = 0
            number = 0
            shown = 1
            reach = 2
            return
        }
        press = 0
        number = 0
        shown = 0
        reach = 0
        guard await beat(startsAfter) else { return }
        while true {
            withAnimation(RibbonMotion.arrive) { press = 1 }
            guard await beat(RibbonMotion.arriveDuration) else { return }
            // The press gives way to the selection.
            withAnimation(RibbonMotion.arrive) {
                press = 0
                shown = 1
            }
            guard await beat(RibbonMotion.arriveDuration + Vignette.breath) else { return }
            withAnimation(RibbonMotion.open) { reach = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.arrive) { number = 1 }
            guard await beat(RibbonMotion.arriveDuration) else { return }
            withAnimation(RibbonMotion.open) {
                number = 0
                reach = 2
            }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            // Let go of: the selection fades off rather than shrinking back.
            withAnimation(RibbonMotion.settle) { shown = 0 }
            guard await beat(RibbonMotion.settleDuration) else { return }
            reach = 0
            guard await beat(Vignette.breath) else { return }
        }
    }
}

/// Draws the phone's own selection on the line: the tint under the words
/// from wherever its start stands to the end of "Word", the two handles
/// over them, and a fingertip's press on the word or on the number.
private struct NativeSelection: TextRenderer, Animatable {
    var press: Double
    var number: Double
    var shown: Double
    /// Where the selection starts: 0 at "Word", 1 at "was the Word", 2 at
    /// the start of the verse.
    var reach: Double

    var animatableData: AnimatablePair<AnimatablePair<Double, Double>, AnimatablePair<Double, Double>> {
        get { AnimatablePair(AnimatablePair(press, number), AnimatablePair(shown, reach)) }
        set {
            press = newValue.first.first
            number = newValue.first.second
            shown = newValue.second.first
            reach = newValue.second.second
        }
    }

    /// The presses and the handles' round ends reach past the words.
    var displayPadding: EdgeInsets {
        EdgeInsets(top: 20, leading: 24, bottom: 20, trailing: 24)
    }

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        let held = layout.boxes(of: .held).first
        if let held {
            Vignette.fingertip(on: held.rect, press: press, radius: 17, in: &context)
        }
        if let numeral = layout.boxes(of: .number).first {
            Vignette.fingertip(on: numeral.rect, press: number, radius: 12, in: &context)
        }

        var selected: CGRect?
        if shown > 0, let held,
           let was = layout.boxes(of: .step(1)).first,
           let start = layout.boxes(of: .step(0)).first {
            let starts = [held.rect.minX, was.rect.minX, start.rect.minX]
            let at = min(max(reach, 0), 2)
            let lower = min(Int(at.rounded(.down)), 1)
            let t = CGFloat(at - Double(lower))
            let left = starts[lower] + (starts[lower + 1] - starts[lower]) * t
            let rect = CGRect(x: left, y: held.rect.minY, width: held.rect.maxX - left, height: held.rect.height)
            var tint = context
            tint.opacity = shown
            tint.fill(Path(rect), with: .color(Palette.chartreuse.opacity(Vignette.selectionTint)))
            selected = rect
        }

        // The words over the tint, never under it.
        for line in layout {
            context.draw(line)
        }

        guard let rect = selected else { return }
        var handles = context
        handles.opacity = shown
        let ink = GraphicsContext.Shading.color(Palette.chartreuse)
        let bar = Vignette.handleWidth
        let knob = Vignette.handleKnob
        // The start: a bar at the left edge, its round end above.
        handles.fill(Path(CGRect(x: rect.minX - bar / 2, y: rect.minY, width: bar, height: rect.height)), with: ink)
        handles.fill(Path(ellipseIn: CGRect(
            x: rect.minX - knob / 2, y: rect.minY - knob, width: knob, height: knob)), with: ink)
        // The end: a bar at the right edge, its round end below.
        handles.fill(Path(CGRect(x: rect.maxX - bar / 2, y: rect.minY, width: bar, height: rect.height)), with: ink)
        handles.fill(Path(ellipseIn: CGRect(
            x: rect.maxX - knob / 2, y: rect.maxY, width: knob, height: knob)), with: ink)
    }
}

// MARK: 6. However many of you there are

/// Two blocks as "In this room" draws them: yours, labelled as yours; then
/// the World English's, its readers' faces arriving one by one beside its
/// name and then "and others", and the words it says differently brought
/// forward by the wash.
private struct RoomGroupsVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// How many have arrived: the three faces, then "and others".
    @State private var gathered = 0
    @State private var drawn = 0.0
    /// The wash as a whole, which fades off at the end rather than
    /// undrawing.
    @State private var washed = 1.0

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Vignette.labelled(
                Copy.roomYours([TranslationID.bsb.displayName]),
                Vignette.berean
                    .font(RibbonType.scripture(Vignette.textSize))
                    .foregroundStyle(Palette.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8))
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .center, spacing: 8) {
                    SmallCaps(TranslationID.web.displayName, size: 11)
                    faces
                    SmallCaps(Copy.roomAndOthers, size: 11)
                        .opacity(gathered > Vignette.faceInks.count ? 1 : 0)
                }
                Vignette.worldEnglishAgainstYours
                    .font(RibbonType.scripture(Vignette.textSize))
                    .foregroundStyle(Palette.text)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .textRenderer(WashAlong(drawn: drawn, alpha: washed, ink: Ink.ochre.color))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    /// Three faces, overlapping, first on top, each cut from the one under
    /// it by a ring of the well's ground — the room's faces, small, in its
    /// inks.
    private var faces: some View {
        ZStack(alignment: .leading) {
            ForEach(Array(Vignette.faceInks.enumerated()), id: \.offset) { index, ink in
                Circle()
                    .fill(ink.color)
                    .frame(width: Vignette.face - 3, height: Vignette.face - 3)
                    .padding(1.5)
                    .background(Circle().fill(Palette.ground))
                    .offset(x: Vignette.faceStep * CGFloat(index))
                    .zIndex(Double(Vignette.faceInks.count - index))
                    .opacity(index < gathered ? 1 : 0)
            }
        }
        .frame(
            width: Vignette.face + Vignette.faceStep * CGFloat(Vignette.faceInks.count - 1),
            height: Vignette.face,
            alignment: .leading)
    }

    private func play() async {
        washed = 1
        guard !reduceMotion else {
            // How it ends: everyone here, and the difference brought out.
            gathered = Vignette.faceInks.count + 1
            drawn = 1
            return
        }
        gathered = 0
        drawn = 0
        guard await beat(startsAfter) else { return }
        while true {
            for count in 1...(Vignette.faceInks.count + 1) {
                withAnimation(RibbonMotion.arrive) { gathered = count }
                guard await beat(RibbonMotion.arriveDuration) else { return }
            }
            guard await beat(Vignette.breath) else { return }
            withAnimation(RibbonMotion.open) { drawn = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) {
                washed = 0
                gathered = 0
            }
            guard await beat(RibbonMotion.settleDuration) else { return }
            drawn = 0
            washed = 1
            guard await beat(Vignette.breath) else { return }
        }
    }
}

// MARK: 7. The New King James, as printed

/// "The Lord is my shepherd": "Lord" gives way, in its place, to LORD in
/// small capitals, with the soft wash under it; then it settles back.
private struct DivineNameVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var printed = 0.0
    @State private var drawn = 0.0
    @State private var washed = 1.0

    var body: some View {
        Vignette.shepherd
            .font(RibbonType.scripture(Vignette.textSize))
            .foregroundStyle(Palette.text)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .textRenderer(DivineName(printed: printed, drawn: drawn, alpha: washed))
            .padding(.horizontal, 20)
            .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        washed = 1
        guard !reduceMotion else {
            // How it ends: LORD, as printed, on its wash.
            printed = 1
            drawn = 1
            return
        }
        printed = 0
        drawn = 0
        guard await beat(startsAfter) else { return }
        while true {
            withAnimation(RibbonMotion.open) {
                printed = 1
                drawn = 1
            }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) {
                printed = 0
                washed = 0
            }
            guard await beat(RibbonMotion.settleDuration) else { return }
            drawn = 0
            washed = 1
            guard await beat(Vignette.rest) else { return }
        }
    }
}

/// Draws the held word crossfading into the divine name as printed. The
/// name is wider than the word, so the words either side make room for it
/// as it comes — half each way, so the line stays where it was — and the
/// wash is laid along the room it takes.
private struct DivineName: TextRenderer, Animatable {
    var printed: Double
    var drawn: Double
    var alpha: Double

    var animatableData: AnimatablePair<Double, AnimatablePair<Double, Double>> {
        get { AnimatablePair(printed, AnimatablePair(drawn, alpha)) }
        set {
            printed = newValue.first
            drawn = newValue.second.first
            alpha = newValue.second.second
        }
    }

    var displayPadding: EdgeInsets {
        EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12)
    }

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        guard let held = layout.boxes(of: .held).first else {
            for line in layout {
                context.draw(line)
            }
            return
        }
        let name = context.resolve(Vignette.divineName)
        let size = name.measure(in: CGSize(width: 1000, height: 200))
        let grow = max(0, size.width - held.rect.width) * CGFloat(printed)

        if drawn > 0, alpha > 0 {
            var wash = context
            wash.opacity = alpha
            let room = WordsBox(
                rect: CGRect(
                    x: held.rect.minX - grow / 2, y: held.rect.minY,
                    width: held.rect.width + grow, height: held.rect.height),
                baseline: held.baseline)
            Vignette.paint([Vignette.band(room)], drawn: drawn, ink: Ink.ochre.color, in: &wash)
        }

        var past = false
        for line in layout {
            for run in line {
                if run[VignetteWords.self]?.role == .held {
                    past = true
                    var word = context
                    word.opacity = 1 - printed
                    word.draw(run)
                    continue
                }
                var aside = context
                aside.translateBy(x: (past ? grow : -grow) / 2, y: 0)
                aside.draw(run)
            }
        }

        if printed > 0 {
            var caps = context
            caps.opacity = printed
            let baseline = name.firstBaseline(in: size)
            caps.draw(name, in: CGRect(
                x: held.rect.midX - size.width / 2, y: held.baseline - baseline,
                width: size.width, height: size.height))
        }
    }
}

// MARK: 8. The Hebrew, easy to read

/// Exodus 15:11 over two lines of a page: a press settles on "majestic" and
/// the phone's tint takes it; the line over the toolbar arrives bare over
/// the line above — muted all through, the Hebrew small, the verse running
/// through it — and is left there long enough to see the trouble. Then the
/// bar's ground comes under it, and the Hebrew grows and brightens on it.
/// Then all of it settles back.
private struct ReadableLineVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var press = 0.0
    @State private var selected = 0.0
    @State private var line = 0.0
    @State private var ground = 0.0

    // The loop's clock is Android's (`readableFrame`, on LONG_LOOP_MS), so
    // the two phones tell it alike: the press at 0.5 s, the tint at 0.82,
    // the bare line at 1.0, the ground at 2.3, everything going back at
    // 4.6, and again at 6.2.

    /// The page at rest as each loop opens; and from the press to the line.
    private static let opening: Double = 0.5
    /// The bare line, held after it arrives so the eye sees the tangle.
    private static let tangled: Double = 0.9
    /// The line on its ground, held after it arrives: the still frame.
    private static let held: Double = 1.82
    /// After everything has settled back, before the next loop opens.
    private static let after: Double = 1.2

    var body: some View {
        // The two lines as one block, centred in the well, as Android
        // draws them.
        Vignette.songOfTheSea
            .font(RibbonType.scripture(Vignette.textSize))
            .foregroundStyle(Palette.text)
            .lineSpacing(Vignette.pageLeading)
            .lineLimit(2)
            // The widest line of any picture: at 320 points (Display Zoom,
            // or an iPad's narrowest column) the upper line needs about
            // 0.7 to stay whole, and at 0.8 it would wrap and cut the held
            // word off the lower one. It only goes as small as it must.
            .minimumScaleFactor(0.66)
            .textRenderer(SelectedWord(press: press, shown: selected))
            // Centred over the middle of the upper line — the widest, so
            // the block's middle is its middle — as the real one sits over
            // the page above the toolbar.
            .overlay(alignment: Alignment(horizontal: .center, vertical: .firstTextBaseline)) {
                ReadableLine(ground: ground)
                    .opacity(line)
                    .alignmentGuide(.firstTextBaseline) { d in d.height / 2 + Vignette.lineMiddle }
            }
            .padding(.horizontal, 20)
            .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        guard !reduceMotion else {
            // The still frame is how it ends: the word held and selected,
            // and its line on its own ground over the verse, the Hebrew
            // large and at full strength.
            press = 1
            selected = 1
            line = 1
            ground = 1
            return
        }
        press = 0
        selected = 0
        line = 0
        ground = 0
        guard await beat(startsAfter) else { return }
        while true {
            guard await beat(Self.opening) else { return }
            // A press settles on the word, and the phone takes it.
            withAnimation(RibbonMotion.arrive) { press = 1 }
            guard await beat(RibbonMotion.arriveDuration) else { return }
            withAnimation(RibbonMotion.arrive) { selected = 1 }
            guard await beat(Self.opening - RibbonMotion.arriveDuration) else { return }
            // The line comes as it was: bare on the page, muted, the
            // Hebrew at 17 in among the verse's letters.
            withAnimation(RibbonMotion.settle) { line = 1 }
            guard await beat(RibbonMotion.settleDuration + Self.tangled) else { return }
            // The bar's ground comes under it, and the Hebrew grows and
            // comes up to full strength on it: one beat, because it was
            // one change.
            withAnimation(RibbonMotion.open) { ground = 1 }
            guard await beat(RibbonMotion.openDuration + Self.held) else { return }
            withAnimation(RibbonMotion.settle) {
                press = 0
                selected = 0
                line = 0
                ground = 0
            }
            guard await beat(RibbonMotion.settleDuration + Self.after) else { return }
        }
    }
}

/// The line over the toolbar saying "majestic", from how it was to how it
/// is: at 0, bare — muted all through, the Hebrew at 17, nothing under it;
/// at 1, on the bar's ground, hugging what it says, the Hebrew at 21 and at
/// full strength. One number, so the ground, the size and the strength move
/// as one.
private struct ReadableLine: View, Animatable {
    var ground: Double

    var animatableData: Double {
        get { ground }
        set { ground = newValue }
    }

    private var hebrewSize: CGFloat {
        Vignette.bareHebrew + (Vignette.readableHebrew - Vignette.bareHebrew) * CGFloat(ground)
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            // The word muted, and over it the same word at full strength
            // coming up as the ground comes: one word, brightening.
            Text(verbatim: Vignette.hebrew)
                .foregroundStyle(Palette.muted)
                .overlay {
                    Text(verbatim: Vignette.hebrew)
                        .foregroundStyle(Palette.text)
                        .opacity(ground)
                }
                .font(RibbonType.original(hebrewSize, hebrew: true))
            Vignette.hebrewSaid
        }
        .lineLimit(1)
        .fixedSize()
        .padding(Vignette.barPadding)
        .background {
            Capsule()
                .fill(Palette.ground.opacity(Vignette.barGround))
                .overlay(Capsule().strokeBorder(Palette.rule, lineWidth: 1))
                .opacity(ground)
        }
    }
}

/// Draws a word the phone has just taken: a fingertip's press on it, and
/// the page's selection tint under it — the words over the tint, never
/// under it.
private struct SelectedWord: TextRenderer, Animatable {
    var press: Double
    var shown: Double

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(press, shown) }
        set {
            press = newValue.first
            shown = newValue.second
        }
    }

    /// The press reaches past the word, above and below.
    var displayPadding: EdgeInsets {
        EdgeInsets(top: 14, leading: 8, bottom: 14, trailing: 8)
    }

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        if let held = layout.boxes(of: .held).first {
            Vignette.fingertip(on: held.rect, press: press, radius: 17, in: &context)
            if shown > 0 {
                var tint = context
                tint.opacity = shown
                tint.fill(Path(held.rect), with: .color(Palette.chartreuse.opacity(Vignette.selectionTint)))
            }
        }
        for line in layout {
            context.draw(line)
        }
    }
}

// MARK: 9. Your name in the front of the book

/// The top of You as it opens now (A67), a flyleaf: Ruth's face and her
/// name, the binding under them, and from it her rooms' ribbons laid in
/// one after another, each with the room and where it lies. Then they
/// fade off together, and are laid in again.
private struct FlyleafVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// How many of the ribbons are laid in, left to right.
    @State private var laid = 0
    /// The ribbons as a whole, which fade off at the end rather than
    /// lifting out one by one.
    @State private var shown = 1.0

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Vignette.readerFace(30)
                Text(Vignette.reader)
                    .font(RibbonType.display(24))
                    .foregroundStyle(Palette.text)
                    .lineLimit(1)
            }
            VStack(spacing: 0) {
                HairlineRule()
                HStack(alignment: .top, spacing: 0) {
                    ForEach(Array(Vignette.flyleaf.enumerated()), id: \.offset) { index, ribbon in
                        hanging(ribbon, down: index < laid)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .opacity(shown)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    /// A ribbon at the left of its third, and beside it the room and,
    /// muted, where it lies. The words come with their ribbon.
    private func hanging(_ ribbon: Vignette.FlyleafRibbon, down: Bool) -> some View {
        HStack(alignment: .top, spacing: 8) {
            VignetteRibbon(color: ribbon.color, width: Vignette.flyleafRibbonWidth, length: ribbon.length, down: down)
            VStack(alignment: .leading, spacing: 2) {
                SmallCaps(ribbon.room, size: 11, color: Palette.text.opacity(0.8))
                SmallCaps(ribbon.place, size: 11)
            }
            .lineLimit(1)
            .padding(.top, 4)
            .opacity(down ? 1 : 0)
        }
    }

    private func play() async {
        shown = 1
        guard !reduceMotion else {
            // How it ends: every room's ribbon laid in.
            laid = Vignette.flyleaf.count
            return
        }
        laid = 0
        guard await beat(startsAfter) else { return }
        while true {
            for count in 1...Vignette.flyleaf.count {
                withAnimation(RibbonMotion.open) { laid = count }
                guard await beat(RibbonMotion.arriveDuration) else { return }
            }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) { shown = 0 }
            guard await beat(RibbonMotion.settleDuration) else { return }
            laid = 0
            shown = 1
            guard await beat(Vignette.breath) else { return }
        }
    }
}

/// A ribbon hanging from the edge above it — the flyleaf's binding, a
/// row's top — laid in by growing down from that edge, and lifted out back
/// up it, as the app's own are (`HangingRibbon`). Drawn here rather than
/// borrowed so that it moves only on the picture's beat, on the curve the
/// beat gives it.
private struct VignetteRibbon: View {
    var color: Color
    var width: CGFloat
    var length: CGFloat
    var down: Bool

    var body: some View {
        RibbonTail()
            .fill(color)
            .frame(width: width, height: length)
            // Not quite nothing, as `HangingRibbon`'s: a scale of zero has
            // no inverse.
            .scaleEffect(x: 1, y: down ? 1 : 0.001, anchor: .top)
            .opacity(down ? 1 : 0)
    }
}

// MARK: 10. Every book you have finished, on one shelf

/// Three embers on one baseline, a small book's, a middling one's and a
/// long one's, each over its book and who it was read with. They rise
/// onto the shelf in turn, and after a while fade off it together.
private struct YourShelfVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// How many embers have risen onto the shelf, left to right.
    @State private var risen = 0
    /// The shelf as a whole, which fades at the end rather than the embers
    /// sinking back.
    @State private var shown = 1.0

    var body: some View {
        // A grid, so the embers stand on one line whatever their size and
        // the words hang from another, each book's words under its own
        // ember — no shelf drawn (S10).
        Grid(horizontalSpacing: 18, verticalSpacing: 6) {
            GridRow(alignment: .bottom) {
                ForEach(Array(Vignette.shelf.enumerated()), id: \.offset) { index, shelved in
                    VignetteEmber(scale: shelved.scale, seed: 0.4 + Double(index) * 1.7)
                        .opacity(index < risen ? 1 : 0)
                        .offset(y: index < risen ? 0 : Vignette.rise)
                }
            }
            GridRow(alignment: .top) {
                ForEach(Array(Vignette.shelf.enumerated()), id: \.offset) { index, shelved in
                    VStack(spacing: 2) {
                        SmallCaps(shelved.book, size: 12, color: Palette.text.opacity(0.8))
                        SmallCaps(shelved.company, size: 11)
                    }
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .opacity(index < risen ? 1 : 0)
                    .offset(y: index < risen ? 0 : Vignette.rise)
                }
            }
        }
        .opacity(shown)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        shown = 1
        guard !reduceMotion else {
            // How it ends: every book on the shelf.
            risen = Vignette.shelf.count
            return
        }
        risen = 0
        guard await beat(startsAfter) else { return }
        while true {
            for count in 1...Vignette.shelf.count {
                withAnimation(RibbonMotion.arrive) { risen = count }
                guard await beat(RibbonMotion.arriveDuration) else { return }
            }
            guard await beat(Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) { shown = 0 }
            guard await beat(RibbonMotion.settleDuration) else { return }
            risen = 0
            shown = 1
            guard await beat(Vignette.breath) else { return }
        }
    }
}

/// An ember drawn by the ember's own hand (`EmberView.draw`), in
/// `EmberView`'s proportions at the size a picture of a shelf has room
/// for. It breathes as an ember on the shelf does, each at its own time,
/// and holds one instant under reduce motion.
private struct VignetteEmber: View {
    var scale: FireScale
    var seed: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let size = CGFloat(scale.frameHeight) * 0.30 * Vignette.emberShrink
        Group {
            if reduceMotion {
                Canvas { context, canvasSize in
                    EmberView.draw(in: &context, size: canvasSize, time: seed)
                }
            } else {
                TimelineView(.animation(minimumInterval: 1.0 / 12.0)) { timeline in
                    Canvas { context, canvasSize in
                        EmberView.draw(
                            in: &context, size: canvasSize,
                            time: timeline.date.timeIntervalSinceReferenceDate * 0.25 + seed)
                    }
                }
            }
        }
        .frame(width: size * 1.7, height: size * 1.35)
    }
}

// MARK: 11. Choose a version by reading it

/// Two versions as the Text screen now sets them, each a name over its own
/// words for the verse. The ribbon that marks yours lifts out of the first
/// and is laid into the second; after a while it goes back.
private struct VersionsByReadingVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Where the ribbon is laid, one flag to a row, so that it lifts out
    /// of one before it is laid into the other.
    @State private var inFirst = true
    @State private var inSecond = false

    var body: some View {
        VStack(spacing: RibbonShape.seam) {
            row(.bsb, Vignette.berean, shape: Vignette.upperRow, holds: inFirst)
            row(.web, Vignette.worldEnglish, shape: Vignette.lowerRow, holds: inSecond)
        }
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    /// A row of paper: the version's name in small caps over its line, and
    /// the ribbon hanging from the row's top edge at the trailing side when
    /// the row holds it — where `SettingChoice` hangs it.
    private func row(_ version: TranslationID, _ line: Text, shape: TileShape, holds: Bool) -> some View {
        HStack(spacing: 8) {
            Vignette.labelled(
                version,
                line
                    .font(RibbonType.scripture(Vignette.specimenSize))
                    .foregroundStyle(Palette.text.opacity(0.86))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8))
            Spacer(minLength: 0)
            // The ribbon's room, kept on both rows, so a line never runs
            // under it.
            Color.clear.frame(width: Vignette.choiceWidth, height: 0)
        }
        .padding(.horizontal, Vignette.rowInset)
        .padding(.vertical, 7)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .topTrailing) {
            VignetteRibbon(
                color: Palette.chartreuse, width: Vignette.choiceWidth,
                length: Vignette.choiceLength, down: holds)
                .padding(.trailing, Vignette.rowInset)
        }
        .paper(shape)
    }

    private func play() async {
        guard !reduceMotion else {
            // How it ends: the ribbon in the version chosen by its words.
            inFirst = false
            inSecond = true
            return
        }
        inFirst = true
        inSecond = false
        guard await beat(startsAfter) else { return }
        while true {
            withAnimation(RibbonMotion.settle) { inFirst = false }
            guard await beat(RibbonMotion.settleDuration) else { return }
            withAnimation(RibbonMotion.open) { inSecond = true }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) { inSecond = false }
            guard await beat(RibbonMotion.settleDuration) else { return }
            withAnimation(RibbonMotion.open) { inFirst = true }
            guard await beat(RibbonMotion.openDuration + Vignette.breath) else { return }
        }
    }
}

// MARK: 12. The page, the way you read it

/// A small page of John 1:1–3, set the ways Text now offers (A68). One
/// paragraph at Book, its numbers quiet; then the page is set again a
/// verse to a line, as a cross-fade rather than words travelling; then the
/// numbers come clear; then the letters thicken from Book to Heavier on
/// Literata's own axis. Held, and then all of it settles back together.
private struct YourPageVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// How far the page has gone from one paragraph to a verse to a line.
    @State private var lined = 0.0
    /// How far the numbers have gone from quiet to clear.
    @State private var clear = 0.0
    /// How far the letters have gone from Book to Heavier.
    @State private var heavier = 0.0

    var body: some View {
        // The two settings added together rather than laid one over the
        // other: where they set the same letters in the same place — the
        // first line, and the words a line keeps — the ink stays whole
        // through the middle instead of dipping as a plain cross-fade's
        // does, so only what is set again is seen to change, as on Android.
        // In a group of its own, so the adding stops at the page.
        ZStack(alignment: .topLeading) {
            VignettePage(lines: Vignette.asParagraph, heavier: heavier, clear: clear)
                .opacity(1 - lined)
            VignettePage(lines: Vignette.verseByVerse, heavier: heavier, clear: clear)
                .opacity(lined)
                .blendMode(.plusLighter)
        }
        .compositingGroup()
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 20)
        .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        guard !reduceMotion else {
            // How it ends: a verse to a line, the numbers clear, the
            // letters heavier.
            lined = 1
            clear = 1
            heavier = 1
            return
        }
        lined = 0
        clear = 0
        heavier = 0
        guard await beat(startsAfter) else { return }
        while true {
            // The page as it was, looked at before anything changes.
            guard await beat(Vignette.rest) else { return }
            withAnimation(RibbonMotion.open) { lined = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.open) { clear = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.open) { heavier = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) {
                lined = 0
                clear = 0
                heavier = 0
            }
            guard await beat(RibbonMotion.settleDuration) else { return }
        }
    }
}

/// The picture's page at a point between Book and Heavier and between
/// quiet numbers and clear ones, from the page's own table (A68). It is
/// set again at every step of the way, so the letters thicken along the
/// axis, as the reader's page would be set at each weight, rather than one
/// weight fading over another.
private struct VignettePage: View, Animatable {
    let lines: [[Vignette.PageRun]]
    var heavier: Double
    var clear: Double

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(heavier, clear) }
        set {
            heavier = newValue.first
            clear = newValue.second
        }
    }

    var body: some View {
        // Book and Heavier: the middle and last of the table's three.
        let book = Double(PageType.weights[1])
        let heavy = Double(PageType.weights[2])
        let quiet = PageType.quietVerseNumberAlpha
        Vignette.page(
            lines,
            weight: Int((book + (heavy - book) * heavier).rounded()),
            numbers: quiet + (PageType.clearVerseNumberAlpha - quiet) * clear)
            .foregroundStyle(Palette.text)
            .lineLimit(lines.count)
            .minimumScaleFactor(0.8)
    }
}

// MARK: 13. Notifications say who

/// The first switch of a room of two: "Notes left for you", and under it
/// the sentence the phone will say, with Ruth's face. The switch turns on
/// and the sentence writes itself in; then it fades and the switch turns
/// off.
private struct NotificationsByNameVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var on = 0.0
    @State private var written = 0.0
    /// The sentence as a whole, which fades at the end rather than being
    /// unwritten.
    @State private var said = 1.0

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            VStack(alignment: .leading, spacing: 8) {
                Text(Copy.notesLeftForYou)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
                    .lineLimit(1)
                // The example as the row draws it (`SettingExampleView`):
                // a small well, the face, and the words that will arrive.
                HStack(alignment: .top, spacing: 6) {
                    Vignette.readerFace(14)
                        .padding(.top, 1)
                    Text(Copy.notifNoteLeft(Vignette.reader, Vignette.notedAt))
                        .font(RibbonType.ui(13))
                        .foregroundStyle(Palette.text.opacity(0.8))
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                        .textRenderer(WritesIn(written: written))
                        .opacity(said)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
                .well(.small)
            }
            Spacer(minLength: 0)
            VignetteSwitch(on: on)
        }
        .padding(.horizontal, Vignette.rowInset)
        .padding(.vertical, 12)
        .paper(.row)
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        said = 1
        guard !reduceMotion else {
            // How it ends: on, and the sentence it lets through written.
            on = 1
            written = 1
            return
        }
        on = 0
        written = 0
        guard await beat(startsAfter) else { return }
        while true {
            withAnimation(RibbonMotion.open) { on = 1 }
            guard await beat(RibbonMotion.openDuration) else { return }
            withAnimation(RibbonMotion.open) { written = 1 }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.settle) {
                said = 0
                on = 0
            }
            guard await beat(RibbonMotion.settleDuration) else { return }
            written = 0
            said = 1
            guard await beat(Vignette.breath) else { return }
        }
    }
}

/// The row's switch at the picture's size, drawn as the app draws its own
/// (`RibbonSwitch`, I41): a well, the accent filling it as it turns on,
/// and a paper knob crossing to the trailing end and taking the ground's
/// colour. One value, `on`, from 0 to 1, so the whole of it moves on the
/// picture's beat and nothing else's.
private struct VignetteSwitch: View {
    var on: Double

    var body: some View {
        let near = Vignette.switchInset
        let far = Vignette.switchWidth - Vignette.switchKnob - Vignette.switchInset
        ZStack(alignment: .leading) {
            Color.clear
                .frame(width: Vignette.switchWidth, height: Vignette.switchHeight)
                .well(TileShape(Vignette.switchHeight / 2))
            Capsule()
                .fill(Palette.chartreuse)
                .frame(width: Vignette.switchWidth, height: Vignette.switchHeight)
                .opacity(on)
            Circle()
                .fill(Palette.surface)
                .overlay { Circle().strokeBorder(Palette.rule, lineWidth: 1) }
                .overlay { Circle().fill(Palette.ground).opacity(on) }
                .frame(width: Vignette.switchKnob, height: Vignette.switchKnob)
                .offset(x: near + (far - near) * CGFloat(on))
        }
        .frame(width: Vignette.switchWidth, height: Vignette.switchHeight)
    }
}

/// Writes a line in the way a pen does: letter by letter in reading order,
/// across a wrap and on, each letter coming up out of nothing over the
/// nib's width behind the pen rather than a wipe crossing the words.
/// `written` is how far along, 0 to 1.
private struct WritesIn: TextRenderer, Animatable {
    var written: Double

    var animatableData: Double {
        get { written }
        set { written = newValue }
    }

    func draw(layout: Text.Layout, in context: inout GraphicsContext) {
        let length = layout.reduce(CGFloat(0)) { $0 + $1.typographicBounds.rect.width }
        // Run on past the last letter by the nib, so that it too is whole
        // when the pen stops.
        let pen = (length + Vignette.nib) * CGFloat(written)
        var before: CGFloat = 0
        for line in layout {
            let edge = line.typographicBounds.rect
            for run in line {
                for letter in run {
                    let at = before + letter.typographicBounds.rect.minX - edge.minX
                    let come = min(max((pen - at) / Vignette.nib, 0), 1)
                    guard come > 0 else { continue }
                    var ink = context
                    ink.opacity = Double(come)
                    ink.draw(letter)
                }
            }
            before += edge.width
        }
    }
}

// MARK: 14. Quiet hours, drawn as the night

/// The quiet hours' band (A67), noon to noon, with its three hours under
/// it. The night draws itself from ten in the evening to six in the
/// morning, its end handle carried out along it; the start handle is moved
/// on an hour and the night follows it, and after a while is moved back.
/// Then the night fades.
private struct QuietHoursNightVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Where the quiet hours begin, along the band.
    @State private var from = Vignette.nightFrom
    /// How much of the night is drawn, out from its beginning.
    @State private var drawn = 0.0
    /// The night as a whole — the dark stretch and its handles — which
    /// fades at the end rather than undrawing.
    @State private var shown = 0.0

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { proxy in
                band(width: proxy.size.width)
            }
            .frame(height: Vignette.bandHeight)
            PlacedAcross(fractions: QuietHoursBand.marks.map { QuietHoursBand.position(of: $0) }) {
                ForEach(Vignette.nightMarks, id: \.self) { mark in
                    VStack(spacing: 3) {
                        Rectangle()
                            .fill(Palette.rule)
                            .frame(width: 1, height: 4)
                        SmallCaps(mark, size: 9)
                    }
                }
            }
        }
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    /// The setting's band, smaller (`QuietHoursBandView`): the waking hours
    /// raised, the night banked — the ground with its grain — a rule round
    /// the whole, and an ivory handle at each end of the night.
    private func band(width: CGFloat) -> some View {
        let shape = Vignette.bandShape.shape
        let reach = from + (Vignette.nightTo - from) * drawn
        return ZStack(alignment: .leading) {
            Palette.raised
            Palette.ground
                .frame(width: max(0, CGFloat(reach - from) * width))
                .offset(x: CGFloat(from) * width)
                .opacity(shown)
            Image("PaperGrain")
                .resizable(resizingMode: .tile)
                .opacity(0.035)
            handle(at: from, width: width)
            handle(at: reach, width: width)
        }
        .frame(width: width, height: Vignette.bandHeight)
        .clipShape(shape)
        .overlay { shape.strokeBorder(Palette.rule, lineWidth: 1) }
    }

    private func handle(at position: Double, width: CGFloat) -> some View {
        Capsule()
            .fill(Palette.text)
            .frame(width: Vignette.nightHandle.width, height: Vignette.nightHandle.height)
            .offset(x: CGFloat(position) * width - Vignette.nightHandle.width / 2)
            .opacity(shown)
    }

    private func play() async {
        from = Vignette.nightFrom
        guard !reduceMotion else {
            // How it ends: ten in the evening to six in the morning, drawn.
            drawn = 1
            shown = 1
            return
        }
        drawn = 0
        shown = 0
        guard await beat(startsAfter) else { return }
        while true {
            // The handles come with the night they hold, rather than
            // standing on an empty band.
            withAnimation(RibbonMotion.open) {
                drawn = 1
                shown = 1
            }
            // A breath between the night drawn and its start moved, so the
            // two read as two things done rather than one movement.
            guard await beat(RibbonMotion.openDuration + Vignette.breath) else { return }
            withAnimation(RibbonMotion.open) { from = Vignette.laterFrom }
            guard await beat(RibbonMotion.openDuration + Vignette.rest) else { return }
            withAnimation(RibbonMotion.open) { from = Vignette.nightFrom }
            guard await beat(RibbonMotion.openDuration) else { return }
            withAnimation(RibbonMotion.settle) { shown = 0 }
            guard await beat(RibbonMotion.settleDuration) else { return }
            drawn = 0
            guard await beat(Vignette.breath) else { return }
        }
    }
}

// MARK: 15. Set in the type you read best

/// John 1:1's first words five times, each in one of the page's typefaces
/// (A69) at the size that looks like Literata's, the face's name after it
/// in small caps. A short ribbon marks the chosen one — the chosen row in
/// ivory, the rest quieter — and moves down a row at a beat, then comes
/// back to Literata.
private struct TypefaceVignette: View {
    let startsAfter: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The row the ribbon marks, Literata's first.
    @State private var chosen = 0

    private static let line = "In the beginning was the Word"
    /// Literata's size for the picture; every other face is set at the
    /// size that looks like it.
    private static let size: CGFloat = 12
    private static let row: CGFloat = 24
    private static let ribbon = CGSize(width: 3, height: 14)
    /// How long a row is chosen before the ribbon moves on.
    private static let dwell: Double = 1.2

    var body: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(PageFaces.all.enumerated()), id: \.offset) { index, face in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text(verbatim: Self.line)
                            .font(Font(RibbonType.uiPageFace(
                                face, CGFloat(PageType.pointSize(Double(Self.size), face: face)),
                                weight: 400) as CTFont))
                            .foregroundStyle(Palette.text)
                        SmallCaps(face.name, size: 9)
                    }
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(height: Self.row, alignment: .leading)
                    .opacity(index == chosen ? 1 : 0.45)
                }
            }
            .padding(.leading, 14)
            Rectangle()
                .fill(Palette.chartreuse)
                .frame(width: Self.ribbon.width, height: Self.ribbon.height)
                .offset(y: CGFloat(chosen) * Self.row + (Self.row - Self.ribbon.height) / 2)
        }
        .padding(.horizontal, 22)
        .task(id: reduceMotion) { await play() }
    }

    private func play() async {
        chosen = 0
        // How it ends, and how it stands under reduce motion: Literata.
        guard !reduceMotion else { return }
        guard await beat(startsAfter) else { return }
        while true {
            for next in Array(1..<PageFaces.all.count) + [0] {
                guard await beat(Self.dwell) else { return }
                withAnimation(RibbonMotion.settle) { chosen = next }
            }
            guard await beat(Vignette.breath) else { return }
        }
    }
}

#Preview("What's new") {
    WhatsNewScreen(release: WhatsNew.releases[0], onLeave: {})
        .preferredColorScheme(.dark)
}

#Preview("What's new, read again") {
    WhatsNewScreen(history: WhatsNew.releases, onLeave: {})
        .preferredColorScheme(.dark)
}
