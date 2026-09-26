import SwiftUI
import RibbonCore

// S02 — the surface everything else exists to protect. No top bar, no back
// button, no toolbar until you ask for one. Two ways out, both at the
// bottom: the Wave mark, and a downward drag from scroll-top that settles
// like a book closing.

/// Where the book is asked to open: a verse, or the card at the foot of a
/// chapter. A card is not a verse — it sits below the chapter's last one
/// (§4.6) — and "The cards are open" has to land on the card, not on the
/// chapter a whole chapter above it.
enum ReadingPlace: Equatable {
    case verse(VerseAddress)
    case card(chapter: Int)
}

struct ReadingScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    let room: Room
    let reading: Reading
    /// A named place to open at (a waiting row's note, a quoted verse, a
    /// card that has opened) — nil opens at your own position.
    var openAt: ReadingPlace?
    /// The page has been let up: the book is open, not still rising under
    /// a pull that may yet be let go.
    var isOpen = true
    var onClose: () -> Void
    var onFinished: () -> Void
    /// "Start another" at the finishing (§6.5) — lands in the chooser
    /// (S13), not back on the room's way-in.
    var onStartAnother: () -> Void

    // Composition state
    @State private var lifted: VerseRange?
    @State private var liftedChapter: Int?
    @State private var composer: ComposerState?
    @State private var recorder = VoiceRecorder()
    @State private var editingNote: Note?
    /// A mark you made just now, revealed along its words (A41d).
    @State private var justMarked: UUID?
    /// The typeset page of each chapter on screen, for the handles.
    @State private var pages: [Int: ChapterPageHandle] = [:]
    /// The chapter list (A31), from the running-head pill at the foot.
    @State private var showChapters = false
    /// The one question about notifications (§6.1), raised from the two
    /// moments the build book names — a note left, and a note found.
    @State private var askAboutNotifications = false
    /// Your own place when the book opened, so closing knows whether you
    /// read anywhere (A30). Not where the page was sent: opening on a note
    /// and closing again is a glance, and a glance leaves no ribbon.
    @State private var openedAt: VerseAddress?
    /// Where you are right now, as the page reports it — ahead of the
    /// throttled save, so the ribbon is left where you were and not where
    /// the last save was.
    @State private var latestAddress: VerseAddress?
    /// The chapter the running head names. Kept apart from
    /// `latestAddress`, and written only when it changes, so that the page
    /// is not re-read on every verse a scroll passes.
    @State private var headChapter: Int?
    /// Licensed chapters that would not come, and how many times each has
    /// been asked for — the retry re-keys the fetch (A45).
    @State private var chapterAttempts: [Int: Int] = [:]
    @State private var chapterFailed: Set<Int> = []

    // Open note (one at a time; a stack opens whole)
    @State private var openNoteVerse: VerseAddress?
    @State private var noteCardHeight: CGFloat = 120
    @State private var noteSlotY: [Int: CGFloat] = [:]

    // Layout & tracking
    @State private var chapterLayouts: [Int: ChapterLayout] = [:]
    @State private var chapterFrames: [Int: CGRect] = [:]
    @State private var closing = false
    @State private var fingerDown = false
    /// The live viewport height — "the upper third" must mean this
    /// screen's third, not a phone's (a hardcoded 240 misplaces the
    /// position by half a screen on a 13" iPad).
    @State private var viewportHeight: CGFloat = 800
    @State private var lastFuelRecord = Date.distantPast
    @State private var lastPositionSave = Date.distantPast
    @State private var highlightLabel: Highlight?
    @State private var didReachEnd = false
    /// After a follow ends, the form quietly offers "back to where you
    /// were" for about two minutes from its end, then forgets (§4.2). The
    /// place is where you were when the follow began.
    @State private var followBackOffer: (address: VerseAddress, until: Date)?
    /// Asks the ScrollViewReader to go somewhere, from outside its closure.
    @State private var scrollCommand: Int?

    // Following (§4.2)
    /// Every follow gets its own number, so that following someone else —
    /// or the same person again — starts the loop afresh.
    @State private var followEpoch = 0
    /// Where the follow's mark sits, in its chapter's own space.
    @State private var followMark: FollowPlace?
    /// The follow's next move, for the ScrollViewReader to make.
    @State private var followStep: FollowStep?
    /// The text, faded out for the length of a step under reduce motion.
    @State private var pageOpacity: Double = 1
    /// Everything following keeps between ticks. Never read by the body.
    @State private var followState = FollowLoopState()

    // Landing on a verse (deviation 7, I30)
    /// The verse the page has been sent to, and how far it has got.
    @State private var landing: Landing?
    /// Where the landing's mark sits in its chapter, in the chapter's own
    /// space: the point the scroll view is asked to put at its top.
    @State private var landingMark: (chapter: Int, y: CGFloat)?
    /// The landing's next move, for the ScrollViewReader to make.
    @State private var landingMove: LandingMove?
    /// Where a scroll to `.top` really puts a view, in the `.scrollView`
    /// space the page measures itself in: the safe area, give or take. Read
    /// off the first landing, which always starts from a known place.
    @State private var scrollTop: CGFloat?
    /// The scroll view's top inset — what `scrollTop` is taken to be until
    /// the page has seen it.
    @State private var topInset: CGFloat = 0
    /// The chapters the lazy stack has actually built. A chapter's lines can
    /// be aimed at only while it is here.
    @State private var chaptersOnPage: Set<Int> = []

    enum ComposerState: Equatable {
        case toolbar
        case write(VerseAddress)
        case speak(VerseAddress)
    }

    /// A verse the page has been sent to: its own place when the book
    /// opens, a named place (a waiting row's note, the ribbon, a quoted
    /// verse, a notification), or the way back after a follow.
    ///
    /// A verse's line is only known once its chapter has been typeset, so a
    /// chapter that is not on the page is two moves: the chapter, then the
    /// line. Once there, the landing stops being a move and becomes a hold —
    /// the page says you are at that verse until a scroll carries the
    /// reading line off it.
    private struct Landing: Equatable {
        enum Approach: Equatable {
            /// The chapter was on the page already.
            case onPage
            /// The book has just opened on its first chapter, which is
            /// already where the page starts.
            case bookTop
            /// A chapter-length move first — down to it, arriving at its
            /// top, or up to it, arriving at its foot — and whether that has
            /// finished.
            case travelling(down: Bool, done: Bool)
        }
        var address: VerseAddress
        var animated: Bool
        var approach: Approach
        /// The verse's own move has been asked for.
        var placed = false
        /// There. The page is held at the verse from here on.
        var arrived = false
        /// Where `trackReading` found the reading line once the page came to
        /// rest: the hold lasts until it finds it somewhere else.
        var line: VerseAddress?
        /// A follow's landing under VoiceOver: arriving, it hands the
        /// listener the verse it came to.
        var speaks = false
        /// Where the page was when it was sent.
        var from: VerseAddress?

        /// The page moved for it: a chapter's travel, or the move to the
        /// line.
        var moved: Bool {
            if placed { return true }
            if case .travelling = approach { return true }
            return false
        }
    }

    private struct LandingMove: Equatable {
        enum Target: Equatable {
            case chapter(Int, UnitPoint)
            case mark
            /// The passage end below a chapter, where its card is.
            case passageEnd(Int)
        }
        var target: Target
        var animated: Bool
    }

    /// The landing's mark, as the ScrollViewReader knows it.
    private struct LandingMark: Hashable {}
    /// The follow's mark — the landing's, for a page that follows.
    private struct FollowMark: Hashable {}
    /// A chapter's passage end, as the ScrollViewReader knows it.
    private struct PassageEndMark: Hashable { var chapter: Int }

    /// What the follow loop is started and stopped by: who, which follow,
    /// and whether there is an open page in an app that is not in the
    /// background to carry. Coming back from the background starts it
    /// again, with a fresh guess — the last one has been running on for as
    /// long as the phone was away. A moment of `.inactive` — Control Center
    /// pulled down, a call's banner, Siri — is not away: the loop, and what
    /// it has learned of their pace, carries on.
    private struct FollowKey: Equatable {
        var person: UUID?
        var epoch: Int
        var open: Bool
        var active: Bool
    }

    private var followKey: FollowKey {
        FollowKey(
            person: model.followingPersonID, epoch: followEpoch,
            open: isOpen, active: scenePhase != .background)
    }

    /// How far above the reading line a landed verse's first line rests.
    /// Just above, not on it: the page counts a verse as yours once its
    /// first line has crossed the line, and a verse sitting exactly on it is
    /// a coin toss between two verses.
    private static let landingLead: CGFloat = 6
    /// The margin above the first chapter — also where the first chapter
    /// sits below the scroll view's top when the book opens.
    private static let pageTop: CGFloat = 26

    private var book: BibleBook? { Bible.book(id: reading.bookID) }
    /// The room reads one version (A42).
    private var translation: TranslationID { model.words(room: room, reading: reading) }
    private var bookText: ScriptureBookText? {
        model.scripture.book(reading.bookID, translation: translation)
    }
    /// Chapters of a licensed translation, as they stream in (§16.8).
    @State private var remoteChapters: [Int: ScriptureChapter] = [:]

    private func chapterContent(_ n: Int) -> ScriptureChapter? {
        bookText?.chapter(n) ?? remoteChapters[n]
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(1...(book?.chapterCount ?? 1), id: \.self) { n in
                        chapterSection(n)
                            .id(n)
                        if n < (book?.chapterCount ?? 1) {
                            PassageEndView(
                                reading: reading,
                                chapter: n,
                                nextChapterTitle: book?.chapterHeading(n + 1) ?? "\(n + 1)",
                                // Under reduce motion the page does not fly a
                                // chapter's length: it is simply there (§11).
                                // Going on is your own move, and a follow
                                // ends before it.
                                onContinue: {
                                    endFollow()
                                    endLanding()
                                    withAnimation(RibbonMotion.settle(still: reduceMotion)) { proxy.scrollTo(n + 1, anchor: .top) }
                                },
                                onClose: close)
                            .id(PassageEndMark(chapter: n))
                        }
                    }
                    finishingSection
                }
                .padding(.top, Self.pageTop)
                // The measure: Scripture holds a readable line length on
                // any canvas — the reading surface is the product, and a
                // 150-character line is not reading.
                .readableColumn(maxWidth: 680)
                // The text alone fades for a step under reduce motion; the
                // presence form and the thread are not the page.
                .opacity(pageOpacity)
            }
            .scrollIndicators(.hidden)
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top
            } action: { _, offset in
                // The closing drag: pulled well past the top, the page
                // settles closed. Always duplicated by the Wave (§11).
                // Only a finger's pull closes — a momentum bounce doesn't.
                if offset < -90, fingerDown, !closing {
                    close()
                }
                watchForOwnScroll(offset)
                sayWhileScrolling()
                settleSoon()
            }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.containerSize.height
            } action: { _, height in
                if height > 0 {
                    viewportHeight = height
                    followState.viewportMeasured = true
                }
            }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentInsets.top
            } action: { _, top in
                topInset = top
            }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentInsets.bottom
            } action: { _, bottom in
                followState.bottomInset = bottom
            }
            .onScrollPhaseChange { oldPhase, newPhase in
                fingerDown = newPhase == .interacting || newPhase == .tracking
                followState.phase = newPhase
                // A gesture is a drag that scrolls, and nothing less. A
                // finger resting on the page — a tap on a verse, a note's
                // mark, a long press to lift — only holds the follow still
                // while it is there.
                if newPhase == .tracking, oldPhase == .idle { followState.restAtTouch = restingPlace() }
                if newPhase == .interacting, oldPhase != .interacting { gestureBegan() }
                if oldPhase == .interacting, newPhase != .interacting { gestureEnded() }
                if newPhase == .idle {
                    followState.restAtTouch = nil
                    settleSoon()
                }
            }
            .onAppear {
                let position = address(of: openAt)
                openedAt = model.myPosition(in: reading)
                followState.isOpen = isOpen
                // A named place is your own going somewhere, and a follow
                // still running from before ends here (§4.2) — or it would
                // carry the page off the verse it was sent to.
                if openAt != nil { endFollow() }
                // On the verse, not the top of its chapter (deviation 7,
                // I30), or on the card. The page is rising while this
                // happens, so the moves are made without animation: it
                // arrives already there.
                go(to: openAt, opening: true)
                recordFuel()
                if !model.readingQuietly {
                    // The channel is the room's and is already open; this is
                    // the book's half — saying you are in it (§4.2).
                    Task {
                        await model.presence.present(
                            position: position, scrollFraction: 0,
                            isIdle: false, following: model.followingPersonID, activity: true)
                    }
                }
                // The same fact, told to the server for the phones the
                // socket cannot reach (S19, S24). Quietly, it tells nobody.
                model.bookAppeared(reading)
            }
            .onDisappear {
                // Out of the book, still in the room: the line stays open so
                // the room keeps hearing about itself.
                Task { await model.presence.withdraw() }
                model.bookDisappeared(reading)
                // A follow does not outlive its page. Leaving the book is
                // the untrack, which says it.
                if model.followingPersonID != nil { model.followingPersonID = nil }
                followState.settleTask?.cancel()
                followState.settleTask = nil
            }
            .onChange(of: isOpen) { _, open in
                followState.isOpen = open
                // Let up from the pull: where the page is can be said now.
                if open { settleSoon() }
            }
            .onChange(of: model.channelOpens) { _, _ in
                // The room's line is back after the app was away. The
                // announcement it kept goes up with the join; this says
                // where the page is now, if that has changed since. Not
                // activity: this is asked on every return to the app, a
                // glance at Control Center included, and a still reader is
                // still. Coming back from a suspension is the join's to
                // count as being here.
                presentHere(activity: false)
            }
            .onChange(of: model.readingQuietly) { _, quietly in
                if quietly {
                    model.sayIveLeft()
                } else {
                    model.sayImReading()
                }
                let position = latestAddress ?? model.myPosition(in: reading)
                let fraction = followState.lastFraction
                let following = model.followingPersonID
                Task {
                    if quietly {
                        await model.presence.withdraw()
                    } else {
                        await model.presence.present(
                            position: position, scrollFraction: fraction,
                            isIdle: false, following: following, activity: true)
                    }
                }
                if !quietly { settleSoon() }
            }
            .onChange(of: scrollCommand) { _, command in
                if let command {
                    // A chapter chosen: it is somewhere else now, and a
                    // landing lets go.
                    endLanding()
                    withAnimation(RibbonMotion.settle(still: reduceMotion)) {
                        proxy.scrollTo(command, anchor: .top)
                    }
                    scrollCommand = nil
                }
            }
            .onChange(of: landingMove) { _, move in
                guard let move else { return }
                landingMove = nil
                followState.movingUntil = Date().addingTimeInterval(3)
                withAnimation(move.animated ? RibbonMotion.settle(still: reduceMotion) : nil) {
                    switch move.target {
                    case .chapter(let n, let anchor):
                        proxy.scrollTo(n, anchor: anchor)
                    case .mark:
                        proxy.scrollTo(LandingMark(), anchor: .top)
                    case .passageEnd(let n):
                        proxy.scrollTo(PassageEndMark(chapter: n), anchor: .top)
                    }
                } completion: {
                    // Straight away when nothing animated, at the end of the
                    // ease when something did.
                    followState.movingUntil = Date().addingTimeInterval(0.15)
                    landingMoved(move)
                }
            }
            .onChange(of: followStep) { _, step in
                if let step { take(step, with: proxy) }
            }
            .onChange(of: openAt) { _, target in
                // A named place asked for while the book is already open — a
                // notification tapped over the page (S19). This page used to
                // stay where it was. It goes there now as it would have
                // opened there: at once, the way Android has always taken
                // it. Going somewhere is your own move, so a follow ends
                // (§4.2).
                guard let target else { return }
                endFollow()
                go(to: target, opening: false)
            }
            .task(id: followKey) {
                await carryThePage(followKey)
            }
        }
        .overlay(alignment: .trailing) {
            if !room.isPaused {
                PresenceForm(
                    room: room, onFollow: follow,
                    onExpand: { open in followState.panelOpen = open })
                    .padding(.trailing, 0)
            }
        }
        .overlay(alignment: .topTrailing) {
            ZStack {
                if model.followingPersonID != nil {
                    FollowThread()
                        .transition(.opacity)
                }
            }
            // The thread is drawn in and let go of, never switched: it
            // fades both ways, reduce motion or not.
            .animation(RibbonMotion.arrive, value: model.followingPersonID != nil)
        }
        .overlay(alignment: .bottom) {
            bottomChrome
                // What the chrome covers is not what the page shows: the
                // bottom of your screen, for whoever follows you, is above it.
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                    followState.chromeHeight = height
                }
        }
        .overlay(alignment: .center) { highlightLabelOverlay }
        .room()
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showChapters) {
            ChaptersSheet(reading: reading, currentChapter: currentChapter) { chapter in
                showChapters = false
                // A chapter chosen is your own going somewhere: a follow ends
                // before the page moves.
                endFollow()
                scrollCommand = chapter
            }
        }
        .confirm(askDialog, dismissTitle: Copy.dontTellMe)
        .onChange(of: model.isOnline) { _, online in
            // Connectivity back: the chapters that would not come are
            // asked for again, once, without a tap.
            if online, !chapterFailed.isEmpty {
                for n in chapterFailed { chapterAttempts[n, default: 0] += 1 }
                chapterFailed = []
            }
        }
    }

    /// The chapter whose top has crossed the upper third — where you are.
    /// As the page reports it rather than as last saved: a page held where
    /// it was sent saves nothing until you move (I30), and the running head
    /// still has to name the chapter on the screen.
    private var currentChapter: Int {
        headChapter ?? model.myPosition(in: reading).chapter
    }

    /// "Tell you when Ruth leaves a note?" — §6.1's exact question, asked
    /// once. Two answers and no third: "Not now" only exists in apps that
    /// intend to ask again, and this one does not. On "Tell me" the system
    /// prompt follows; on "Don't" it never appears. Either way the app
    /// remembers that it asked.
    private var askDialog: Binding<ConfirmState?> {
        Binding(
            get: {
                guard askAboutNotifications, let name = model.whoTheAskIsAbout(in: room) else { return nil }
                return ConfirmState(question: Copy.tellYouWhen(name), choices: [
                    ConfirmChoice(Copy.tellMe) {
                        askAboutNotifications = false
                        Task { await model.askForNotifications() }
                    },
                ])
            },
            set: { state in
                if state == nil {
                    model.markAskedAboutNotifications()
                    askAboutNotifications = false
                }
            })
    }

    private func considerAsking() {
        if model.shouldAskAboutNotifications(in: room) { askAboutNotifications = true }
    }

    // MARK: Chapters

    @ViewBuilder
    private func chapterSection(_ n: Int) -> some View {
        if let chapter = chapterContent(n) {
            ZStack(alignment: .topLeading) {
                ChapterTextView(
                    chapter: chapter,
                    runningHead: book?.chapterHeading(n) ?? "\(reading.bookID) \(n)",
                    theme: ReadingTheme(
                        fontSize: model.settings.scriptureSize,
                        lineHeightMultiple: model.settings.lineHeightMultiple,
                        redLetter: model.settings.redLetter,
                        dynamicTypeSize: dynamicTypeSize),
                    marks: marks(chapter: n),
                    justMarked: justMarked,
                    lifted: liftedChapter == n ? lifted : nil,
                    openNote: openNote(in: n),
                    isFirstChapter: n == 1,
                    showMarginHint: !model.state.hasSeenMarginHint && n == 1,
                    handle: page(n),
                    onLayout: { layout in
                        chapterLayouts[n] = layout
                        continueLanding(in: n)
                        settleSoon()
                    },
                    onLongPressVerse: { verse in beginLift(chapter: n, verse: verse) },
                    onDragToVerse: { verse in extendLift(chapter: n, verse: verse) },
                    onDragEnded: {},
                    onTapVerse: { verse in tapVerse(chapter: n, verse: verse) },
                    onMarkDrawn: { justMarked = nil },
                    onNoteSlot: { y in noteSlotY[n] = y })

                gutterMarks(chapter: n)
                openNoteCard(chapter: n)
                if liftedChapter == n, lifted != nil, composer == .toolbar {
                    liftHandles(chapter: n)
                }
                if let mark = landingMark, mark.chapter == n {
                    // Nothing to see: a point in the chapter for the scroll
                    // view to aim at, set so that the verse's line comes to
                    // rest on the reading line (I30).
                    Color.clear
                        .frame(width: 1, height: 1)
                        .id(LandingMark())
                        .position(x: 0.5, y: mark.y + 0.5)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
                if let mark = followMark, mark.chapter == n {
                    // The follow's point to aim at: put at the top of the
                    // screen, it leaves the page as far on as the step asks.
                    // A rubber band's rest can be outside the chapter's own
                    // lines, below them or above its head; a point there is
                    // found all the same.
                    Color.clear
                        .frame(width: 1, height: 1)
                        .id(FollowMark())
                        .position(x: 0.5, y: mark.y + 0.5)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
            }
            .coordinateSpace(name: "chapter")
            .onGeometryChange(for: CGRect.self) { geometry in
                geometry.frame(in: .scrollView)
            } action: { frame in
                chapterFrames[n] = frame
                continueLanding(in: n)
                trackReading(chapter: n, frame: frame)
            }
            .onAppear { _ = chaptersOnPage.insert(n) }
            .onDisappear { _ = chaptersOnPage.remove(n) }
            .padding(.bottom, 8)
        } else if let licensed = TranslationRegistry.translation(for: translation), !licensed.isBundled {
            // A licensed translation's chapter, genuinely fetching (S02):
            // the running head appears and the body fades in — no
            // skeleton lines, which read as fake text. If it would not
            // come, the page says so where the words would be, and offers
            // the one thing that helps (A45).
            VStack(alignment: .leading, spacing: 18) {
                SmallCaps(
                    book?.chapterHeading(n) ?? "\(reading.bookID) \(n)", size: 14,
                    color: Palette.text.opacity(0.4))
                if chapterFailed.contains(n) {
                    Text(Copy.chapterWouldntCome(book?.name ?? reading.bookID))
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                    QuietControl(title: Copy.tryAgain) {
                        chapterFailed.remove(n)
                        chapterAttempts[n, default: 0] += 1
                    }
                } else {
                    Spacer().frame(height: 320)
                }
            }
            .padding(.leading, 36)
            .padding(.trailing, 26)
            .task(id: chapterAttempts[n, default: 0]) {
                let address = VerseAddress(bookID: reading.bookID, chapter: n, verse: 1)
                if let chapter = await model.scripture.ensureRemoteChapter(address, translation: licensed) {
                    withAnimation(RibbonMotion.arrive) {
                        remoteChapters[n] = chapter
                        chapterFailed.remove(n)
                    }
                } else if !Task.isCancelled {
                    withAnimation(RibbonMotion.arrive) { _ = chapterFailed.insert(n) }
                }
            }
        } else {
            // Text is local or it isn't shown (S02): with bundled
            // translations this is unreachable, but the state exists.
            Text(Copy.bookNotDownloaded(book?.name ?? reading.bookID))
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.muted)
                .padding(40)
        }
    }

    /// The page of a chapter, kept across re-evaluations.
    private func page(_ n: Int) -> ChapterPageHandle {
        if let existing = pages[n] { return existing }
        let handle = ChapterPageHandle()
        DispatchQueue.main.async { pages[n] = handle }
        return handle
    }

    /// Every mark on a chapter, verse by verse. A phrase's offsets are
    /// honoured only when they were measured in the version on this page
    /// (A41g); otherwise the whole verse, which is what the address alone
    /// promises.
    private func marks(chapter: Int) -> [VerseMark] {
        var result: [VerseMark] = []
        for highlight in model.highlights(in: reading, chapter: chapter) {
            let chars = highlight.range.chars(in: translation)
            for verse in highlight.range.verses {
                result.append(VerseMark(
                    id: highlight.id, verse: verse,
                    from: verse == highlight.range.startVerse ? chars.start : nil,
                    to: verse == highlight.range.endVerse ? chars.end : nil,
                    ink: highlight.ink, mine: highlight.authorID == model.me?.id))
            }
        }
        return result
    }

    // MARK: The handles (A41g)

    /// The two ends of the lift, draggable to a word's edge — and, for a
    /// finger that cannot drag, four actions each (§11).
    @ViewBuilder
    private func liftHandles(chapter: Int) -> some View {
        let layout = chapterLayouts[chapter] ?? ChapterLayout()
        if let start = layout.liftStart {
            SelectionHandle(
                label: Copy.whereTheMarkStarts,
                onDrag: { point in moveHandle(chapter: chapter, start: true, to: point) },
                onVerse: { forward in stepHandleVerse(chapter: chapter, start: true, forward: forward) },
                onWord: { forward in stepHandleWord(chapter: chapter, start: true, forward: forward) })
            .position(x: start.minX, y: start.maxY + 8)
        }
        if let end = layout.liftEnd {
            SelectionHandle(
                label: Copy.whereTheMarkEnds,
                onDrag: { point in moveHandle(chapter: chapter, start: false, to: point) },
                onVerse: { forward in stepHandleVerse(chapter: chapter, start: false, forward: forward) },
                onWord: { forward in stepHandleWord(chapter: chapter, start: false, forward: forward) })
            .position(x: end.maxX, y: end.maxY + 8)
        }
    }

    private func moveHandle(chapter: Int, start: Bool, to point: CGPoint) {
        guard let handle = pages[chapter], let current = lifted,
              let placed = handle.place(at: point)
        else { return }
        let edge = handle.wordEdge(verse: placed.verse, offset: placed.offset, forward: !start)
        setLift(chapter: chapter, start: start, verse: placed.verse, offset: edge, current: current)
    }

    private func stepHandleVerse(chapter: Int, start: Bool, forward: Bool) {
        guard let current = lifted else { return }
        let verse = (start ? current.startVerse : current.endVerse) + (forward ? 1 : -1)
        // A verse the page does not have — before the first or past the
        // last — is not a place a mark can go.
        guard verse >= 1, let page = pages[chapter], page.length(of: verse) > 0 else { return }
        setLift(chapter: chapter, start: start, verse: verse, offset: start ? 0 : page.length(of: verse), current: current)
    }

    private func stepHandleWord(chapter: Int, start: Bool, forward: Bool) {
        guard let handle = pages[chapter], let current = lifted else { return }
        let verse = start ? current.startVerse : current.endVerse
        let length = handle.length(of: verse)
        let offset = start ? (current.startChar ?? 0) : (current.endChar ?? length)
        setLift(chapter: chapter, start: start, verse: verse, offset: handle.wordStep(verse: verse, offset: offset, forward: forward), current: current)
    }

    /// One end moved. A whole verse is a whole verse: an end at its first
    /// or last letter is stored as nil, so the mark reads the same on a
    /// page in another version.
    private func setLift(chapter: Int, start: Bool, verse: Int, offset: Int, current: VerseRange) {
        let length = pages[chapter]?.length(of: verse) ?? 0
        let clamped = max(0, min(length, offset))
        var startVerse = current.startVerse, endVerse = current.endVerse
        var startChar = current.startChar, endChar = current.endChar
        if start {
            startVerse = verse
            startChar = clamped == 0 ? nil : clamped
        } else {
            endVerse = verse
            endChar = clamped >= length ? nil : clamped
        }
        let next = VerseRange(
            bookID: reading.bookID, chapter: chapter,
            startVerse: startVerse, endVerse: endVerse,
            startChar: startChar, endChar: endChar,
            charTranslation: (startChar == nil && endChar == nil) ? nil : translation)
        if next != current { lifted = next }
    }

    private func openNote(in chapter: Int) -> (verse: Int, height: CGFloat)? {
        guard let openNoteVerse, openNoteVerse.chapter == chapter else { return nil }
        return (openNoteVerse.verse, noteCardHeight)
    }

    // MARK: Gutter (left edge — notes only)

    @ViewBuilder
    private func gutterMarks(chapter: Int) -> some View {
        let layout = chapterLayouts[chapter] ?? ChapterLayout()
        let byVerse = Dictionary(grouping: model.notes(in: reading, chapter: chapter), by: \.verse.verse)
        ForEach(byVerse.keys.sorted(), id: \.self) { verse in
            if let y = layout.verseFirstLineY[verse], let stack = byVerse[verse] {
                GutterStack(notes: stack, roomID: room.id) {
                    toggleNote(at: VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse))
                }
                .position(x: 14, y: y)
                // A note opening above moves the verses under it down over
                // 400 ms (S04, I32), and their marks go with them rather
                // than arriving first.
                .animation(RibbonMotion.settle(still: reduceMotion), value: y)
            }
        }
    }

    private struct GutterStack: View {
        @Environment(AppModel.self) private var model
        let notes: [Note]
        let roomID: UUID
        var onTap: () -> Void

        var body: some View {
            VStack(spacing: 4) {
                // Up to three marks stack; beyond that, three plus a dot
                // triplet (§4.4). Announced by author, never by count (§11).
                ForEach(notes.prefix(3)) { note in
                    NoteMark(
                        kind: note.kind,
                        ink: model.membership(of: note.authorID, in: roomID)?.ink ?? model.lastUsedInk,
                        found: note.foundBy.contains(model.me?.id ?? UUID()),
                        mine: note.authorID == model.me?.id,
                        pending: note.isPending)
                }
                if notes.count > 3 {
                    HStack(spacing: 1.5) {
                        ForEach(0..<3, id: \.self) { _ in
                            Circle().fill(Palette.muted).frame(width: 2, height: 2)
                        }
                    }
                }
            }
            .contentShape(Rectangle().inset(by: -16))
            .onTapGesture(perform: onTap)
            .accessibilityElement()
            .accessibilityLabel(accessibilityLabel)
            .accessibilityAddTraits(.isButton)
        }

        private var accessibilityLabel: String {
            // §11, exactly: "Note from Ruth, verse 9, not yet found." A
            // stack announces by author and never by count.
            let names = notes.filter { $0.authorID != model.me?.id }.compactMap { model.person($0.authorID)?.name }
            let unfound = notes.contains { !$0.foundBy.contains(model.me?.id ?? UUID()) && $0.authorID != model.me?.id }
            return Copy.marginNotes(
                authors: Set(names).sorted(), verse: notes.first?.verse.verse ?? 0,
                several: notes.count > 1, unfound: unfound)
        }
    }

    @ViewBuilder
    private func openNoteCard(chapter: Int) -> some View {
        if let address = openNoteVerse, address.chapter == chapter,
           let slotY = noteSlotY[chapter] {
            let stack = model.notes(in: reading, chapter: chapter).filter { $0.verse.verse == address.verse }
            VStack(alignment: .leading, spacing: 4) {
                ForEach(stack) { note in
                    NoteCard(
                        note: note,
                        author: model.person(note.authorID),
                        authorInk: model.membership(of: note.authorID, in: room.id)?.ink ?? .clay,
                        onTakeBack: {
                            model.takeBack(note)
                            if stack.count <= 1 { closeNote() }
                        },
                        onEdit: {
                            editingNote = note
                            withAnimation(RibbonMotion.arrive) { composer = .write(note.verse) }
                        })
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 36)
            .padding(.trailing, 26)
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                if abs(height - noteCardHeight) > 1 { noteCardHeight = height }
            }
            .offset(y: slotY)
            .transition(.opacity)
        }
    }

    // MARK: Bottom chrome — the way out, or the composer

    @ViewBuilder
    private var bottomChrome: some View {
        switch composer {
        case .toolbar:
            if let lifted, let chapter = liftedChapter {
                LeaveToolbar(
                    room: room,
                    range: lifted,
                    roomPaused: room.isPaused,
                    onHighlight: { ink in
                        let made = model.addHighlight(lifted, ink: ink, in: reading)
                        justMarked = made?.id
                        clearLift()
                    },
                    // The toolbar gives way to what it opened: a
                    // cross-fade in the same place, rather than a cut.
                    onWrite: {
                        withAnimation(RibbonMotion.arrive) {
                            composer = .write(VerseAddress(bookID: reading.bookID, chapter: chapter, verse: lifted.startVerse))
                        }
                    },
                    onSpeak: {
                        withAnimation(RibbonMotion.arrive) {
                            composer = .speak(VerseAddress(bookID: reading.bookID, chapter: chapter, verse: lifted.startVerse))
                        }
                    })
                .padding(.bottom, 14)
            }
        case .write(let address):
            WriteComposer(
                verse: address,
                initialText: editingNote?.body ?? "",
                identity: editingNote?.id.uuidString ?? address.formatted,
                onSave: { body in
                    if let note = editingNote {
                        model.editWrittenNote(note, body: body)
                    } else {
                        model.leaveWrittenNote(body, at: address, in: reading)
                        considerAsking()
                    }
                    editingNote = nil
                    clearLift()
                },
                onCancel: { editingNote = nil; clearLift() })
            .padding(.bottom, 10)
            .transition(.opacity)
        case .speak(let address):
            SpeakControl(
                ink: model.inkForNewHighlight(in: room) ?? model.lastUsedInk,
                recorder: recorder,
                onKeep: { url, waveform in
                    model.leaveVoiceNote(audioURL: url, waveform: waveform, at: address, in: reading)
                    considerAsking()
                    clearLift()
                },
                onDismiss: clearLift)
            .padding(.horizontal, 40)
            .padding(.bottom, 14)
            .readableColumn()
            .transition(.opacity)
        case nil:
            VStack(spacing: 10) {
                // After a follow ends: the quiet offer back, for about two
                // minutes, then it forgets (§4.2).
                if let offer = followBackOffer,
                   Date() < offer.until,
                   model.followingPersonID == nil {
                    QuietControl(title: Copy.backToWhereYouWere) {
                        // Where you were is a verse, not the top of its
                        // chapter (I30).
                        land(at: offer.address, animated: true)
                        withAnimation(RibbonMotion.arrive) { followBackOffer = nil }
                    }
                    .transition(.opacity)
                    .task(id: offer.until) {
                        // It forgets on its own, when the two minutes are
                        // up — not whenever the page next happens to redraw.
                        try? await Task.sleep(for: .seconds(max(0, offer.until.timeIntervalSinceNow)))
                        guard !Task.isCancelled else { return }
                        withAnimation(RibbonMotion.arrive) { followBackOffer = nil }
                    }
                }
                // The way out: the Wave, ~20 pt, muted ivory, centred at
                // the bottom edge. Beside it, at the leading edge, the
                // running head as a pill: where you are, and the way to the
                // chapter list (A31). The glass capsules stay small; the
                // touch targets don't (44 pt minimum).
                ZStack {
                    Button(action: close) {
                        WaveMark(color: Palette.text.opacity(0.55))
                            .frame(width: 20, height: 20)
                            .padding(.horizontal, 26)
                            .padding(.vertical, 9)
                            .ribbonGlass(in: Capsule())
                            .frame(minWidth: 88, minHeight: 52)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .hoverEffect(.lift)
                    // Esc closes the book on a hardware keyboard.
                    .keyboardShortcut(.cancelAction)
                    .accessibilityLabel(Copy.closeTheBook)
                    HStack {
                        Button { showChapters = true } label: {
                            SmallCaps(book?.chapterHeading(currentChapter) ?? "", size: 12, color: Palette.text.opacity(0.7))
                                // Turning into the next chapter, the running
                                // head cross-fades and its capsule eases to
                                // the new width, rather than both jumping.
                                .contentTransition(.opacity)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 9)
                                .ribbonGlass(in: Capsule())
                                .animation(RibbonMotion.settle, value: currentChapter)
                                .frame(minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Copy.chapters)
                        .padding(.leading, 16)
                        Spacer()
                    }
                }
            }
            .padding(.bottom, 6)
            .animation(RibbonMotion.arrive, value: model.followingPersonID == nil)
        }
    }

    @ViewBuilder
    private var highlightLabelOverlay: some View {
        if let highlight = highlightLabel {
            // A small label naming who made it, and remove if it's yours
            // (S06).
            let mine = highlight.authorID == model.me?.id
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    InkDot(ink: highlight.ink)
                    Text(model.person(highlight.authorID)?.name ?? "")
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.text)
                }
                if mine {
                    // Small to read, a finger's width to take (§11): it was
                    // the height of its own letters.
                    Button {
                        model.removeHighlight(highlight)
                        withAnimation(RibbonMotion.arrive) { highlightLabel = nil }
                    } label: {
                        SmallCaps(Copy.remove, size: 12, color: Palette.muted)
                            .frame(minWidth: 88, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, mine ? 4 : 16)
            .ribbonGlass(in: RoundedRectangle(cornerRadius: 16))
            .onTapGesture { withAnimation(RibbonMotion.arrive) { highlightLabel = nil } }
            .task(id: highlight.id) {
                try? await Task.sleep(for: .seconds(2.6))
                withAnimation(RibbonMotion.arrive) { highlightLabel = nil }
            }
            .transition(.opacity)
        }
    }

    // MARK: The finishing sequence (§6.5)

    @ViewBuilder
    private var finishingSection: some View {
        // The one place that gets to feel like an event — and it still has
        // no confetti, no badge, and no number.
        VStack(spacing: 18) {
            Spacer().frame(height: 70)
            FireBecomesEmber(
                scale: reading.handiwork.scale, coalDepth: reading.handiwork.coalDepth,
                begin: didReachEnd,
                // `reading` is the book as it was opened: finished then
                // means it has already been through this once.
                alreadyAnEmber: reading.isFinished)
            Text(book?.name ?? "")
                .font(RibbonType.display(30))
                .foregroundStyle(Palette.text)
            SmallCaps(
                RibbonClock.emberRange(start: reading.startedAt, end: reading.finishedAt ?? Date()),
                size: 13)
            VStack(spacing: 16) {
                // Both close the book, and closing ends a follow.
                WayInButton(title: Copy.putItOnTheShelf) {
                    endFollow(telling: false)
                    onFinished()
                }
                QuietControl(title: Copy.startAnother) {
                    endFollow(telling: false)
                    onStartAnother()
                }
            }
            .padding(.horizontal, 60)
            .padding(.top, 16)
            Spacer().frame(height: 80)
        }
        .onGeometryChange(for: Bool.self) { proxy in
            // Finishing means reaching the end (§6.5), not a lazy stack
            // prefetching it: the sequence counts only once it is actually
            // inside the viewport.
            let viewportHeight = proxy.bounds(of: .scrollView)?.height ?? 800
            return proxy.frame(in: .scrollView).minY < viewportHeight * 0.85
        } action: { visible in
            guard visible, !didReachEnd else { return }
            didReachEnd = true
            if !reading.isFinished {
                model.finishReading(reading)
            }
        }
    }

    // MARK: Intents

    private func beginLift(chapter: Int, verse: Int) {
        withAnimation(RibbonMotion.arrive) {
            liftedChapter = chapter
            lifted = VerseRange(bookID: reading.bookID, chapter: chapter, startVerse: verse, endVerse: verse)
            composer = .toolbar
        }
    }

    private func extendLift(chapter: Int, verse: Int) {
        guard liftedChapter == chapter, let current = lifted else { return }
        let extended = VerseRange(
            bookID: reading.bookID, chapter: chapter,
            startVerse: min(current.startVerse, verse),
            endVerse: max(current.endVerse, verse))
        if extended != current {
            lifted = extended
        }
    }

    private func clearLift() {
        withAnimation(RibbonMotion.arrive) {
            lifted = nil
            liftedChapter = nil
            composer = nil
        }
    }

    private func tapVerse(chapter: Int, verse: Int) {
        // Tapping the text: dismiss the toolbar first; then notes; then a
        // highlight's label.
        if composer != nil {
            clearLift()
            return
        }
        let address = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        let stack = model.notes(in: reading, chapter: chapter).filter { $0.verse.verse == verse }
        if !stack.isEmpty {
            toggleNote(at: address)
            return
        }
        if let highlight = model.highlights(in: reading, chapter: chapter).first(where: { $0.range.verses.contains(verse) }) {
            withAnimation(RibbonMotion.arrive) { highlightLabel = highlight }
        }
    }

    private func toggleNote(at address: VerseAddress) {
        followState.noteMovedAt = Date()
        withAnimation(RibbonMotion.settle) {
            if openNoteVerse == address {
                closeNote()
            } else {
                // Stale geometry from the last open note would place this
                // one wrong for a frame.
                noteSlotY[address.chapter] = nil
                openNoteVerse = address
                var foundOne = false
                for note in model.notes(in: reading, chapter: address.chapter)
                where note.verse.verse == address.verse {
                    if note.authorID != model.me?.id { foundOne = true }
                    model.markFound(note)
                }
                if foundOne { considerAsking() }
            }
        }
    }

    private func closeNote() {
        followState.noteMovedAt = Date()
        withAnimation(RibbonMotion.settle) {
            openNoteVerse = nil
            noteSlotY = [:]
        }
    }

    private func follow(_ person: PresentPerson) {
        // The portrait of the person you already follow: this is the way to
        // stop (the web's "F: Follow, or stop following"). The way back to
        // where you were stays where it was.
        if model.followingPersonID == person.id {
            endFollow()
            return
        }
        // Tap a portrait to follow — no confirmation dialog (§4.2). Where
        // you were is where the page says, not the last throttled save of
        // it; and following someone else instead keeps the place you were
        // before either.
        if model.followingPersonID == nil {
            followBackOffer = (latestAddress ?? model.myPosition(in: reading), Date().addingTimeInterval(120))
        }
        // Joining someone who is reading is reading together: the tap feeds
        // the fire once, as your own scroll would. Being carried after it
        // does not.
        recordFuel(at: latestAddress)
        model.followingPersonID = person.id
        followEpoch += 1
        followState.band = .unused
        // "Ruth is with you" is the other end of this, and it only ever
        // appears because the follow travels: without this the flag was set
        // on this phone and never left it.
        presentHere(activity: true)
    }

    /// The one way a follow ends (§4.2): your own scroll after the rubber
    /// band, a chapter chosen, going on at a passage end, a named place,
    /// closing the book, their portrait tapped again, or a scroll VoiceOver
    /// or a keyboard made for you. No dialog, no words, no haptic — you just
    /// have your own scroll back, and for two minutes from now a quiet way
    /// back to where you were before it.
    ///
    /// `telling` is false when the book is closing: leaving it is the
    /// untrack, which says this and more.
    private func endFollow(telling: Bool = true) {
        guard model.followingPersonID != nil else { return }
        model.followingPersonID = nil
        followState.band = .unused
        if let offer = followBackOffer {
            withAnimation(RibbonMotion.arrive) {
                followBackOffer = (offer.address, Date().addingTimeInterval(120))
            }
        }
        // Being carried saved nothing on the way (§4.2): where the follow
        // left you is kept now, once.
        if let latestAddress, latestAddress != model.myPosition(in: reading) {
            model.savePosition(reading: reading, address: latestAddress)
        }
        if telling { presentHere(activity: true) }
    }

    /// Where the page is, said again through the channel's budget — which
    /// sends it only if it is news.
    private func presentHere(activity: Bool) {
        guard !model.readingQuietly else { return }
        let position = latestAddress ?? model.myPosition(in: reading)
        let fraction = followState.lastFraction
        let following = model.followingPersonID
        Task {
            await model.presence.present(
                position: position, scrollFraction: fraction,
                isIdle: false, following: following, activity: activity)
        }
    }

    // MARK: Following (§4.2)

    /// Following is a thread, not a jump: "Your scroll is theirs."
    ///
    /// One loop for as long as the follow lasts, a tick every quarter of a
    /// second. Each tick gives the guess whatever their phone has said
    /// since, asks it where they are reading now, finds that place on this
    /// page — set at this phone's width and size — and lets FollowCarriage
    /// decide: hold still while they read down the screen, step when they
    /// have read on, fly when the place isn't laid out here yet. It used to
    /// carry the page only when their chapter changed, and sat you at the
    /// top of it while they read on down.
    ///
    /// Nothing the loop keeps is state the body reads. Only a move itself
    /// redraws the page, once, when there is somewhere to go.
    private func carryThePage(_ key: FollowKey) async {
        guard let person = key.person, key.open, key.active else { return }
        let run = FollowRun(person: person)
        followState.run = run
        defer {
            if followState.run === run { followState.run = nil }
        }
        while !Task.isCancelled {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled, model.followingPersonID == person else { return }
            followTick(run, epoch: key.epoch)
        }
    }

    private func followTick(_ run: FollowRun, epoch: Int) {
        let now = Date()
        // Gone from the room: the follow holds, and the guess starts again
        // from whatever they say when they are back.
        guard let them = model.presentPeople.first(where: { $0.id == run.person }) else {
            run.forget(at: now)
            return
        }
        let chapterCount = book?.chapterCount ?? 1
        if let heard = model.heard(from: run.person), run.takes(heard) {
            run.fed = heard.report.received
            if heard.book == reading.bookID {
                feed(heard.report, fromPresence: heard.fromPresence, to: run, chapterCount: chapterCount)
            }
        } else if run.fed == nil, now.timeIntervalSince(run.startedAt) >= FollowRun.waitForTheirLine,
                  let there = them.position, there.bookID == reading.bookID {
            // Nothing yet this run can take. A line kept from well before it
            // began is where they were — an earlier follow's, from a phone
            // that stopped sending when it ended — so the page has waited a
            // moment for the one their phone sends on seeing you follow. It
            // has not come: their presence, where it says they are now,
            // will do until it does. Marked as taken from when the follow
            // began, so a line already on its way is still taken.
            run.fed = run.startedAt
            let report = ReadingReport(
                at: ReadingPoint(chapter: there.chapter, verse: there.verse), settled: true, received: now)
            feed(report, fromPresence: true, to: run, chapterCount: chapterCount)
        }
        // In another book, as far as their presence says: held.
        if let there = them.position, there.bookID != reading.bookID { return }
        guard let reported = run.estimate.reported else { return }
        let rulers = run.measure(around: reported.chapter, count: chapterCount, text: { chapterContent($0) })
        // "Here, but still": the guess stops where it is.
        if them.isIdle { run.estimate.hold(at: now, rulers: { rulers[$0] }) }
        guard let guess = run.estimate.point(at: now, rulers: { rulers[$0] }),
              !pageIsBusy(run, now: now)
        else { return }

        let viewport = viewportHeight
        let y = screenY(of: guess)
        if let stuckAt = run.stuckAt {
            guard let y, abs(y - stuckAt) >= viewport * 0.1 else { return }
            run.stuckAt = nil
        }
        // What their phone actually said, which no step lifts off the top
        // of the screen — unless it came from presence, which is always a
        // scroll behind.
        let reportedY = run.fromPresence ? nil : screenY(of: reported)
        let manner: FollowCarriage.Manner = UIAccessibility.isVoiceOverRunning
            ? .spoken
            : (UIAccessibility.isReduceMotionEnabled ? .calm : .moving)
        if manner != .spoken { run.spokenTo = nil }
        var wentBack = false
        if let back = run.estimate.wentBackAt {
            wentBack = run.backStepAt.map { back > $0 } ?? true
        }
        let move = FollowCarriage.move(
            y: y.map { Double($0) }, reported: reportedY.map { Double($0) },
            viewport: Double(viewport), minStep: Double(viewport * 0.06),
            realign: followState.realignedEpoch != epoch, wentBack: wentBack, manner: manner)
        // The first move of a follow may bring the guess to the line from
        // inside the band; that first decision is the move, even when it is
        // to stay — or a hair's drift later would be a one-point step.
        switch move {
        case .hold:
            followState.realignedEpoch = epoch
            return
        case .step(let distance):
            followState.realignedEpoch = epoch
            if distance < 0 { run.backStepAt = now }
            if manner == .spoken {
                // A screen reader moves only when their line has left the
                // screen, and then as a landing on the verse their phone
                // said — not on a guess — which reads itself out.
                speakTo(reported, run: run, now: now)
            } else {
                // A guess this page hasn't set out yet steps too, as far
                // as their own line on screen allows.
                stepPage(by: CGFloat(distance), guessAt: y, run: run, calm: manner == .calm)
            }
        case .fly:
            // Where a flight goes: the guess — or the place their phone
            // said, read aloud, and whenever the guess has run on into
            // another chapter. Sent to the next chapter's first words, the
            // page opened that chapter at its head while they were still
            // reading the end of this one.
            let target = manner == .spoken || guess.chapter != reported.chapter ? reported : guess
            // A flight barred until they say something new decides nothing:
            // the first decision is still to come when that chapter is set.
            guard run.noFlyTo != target.chapter else { return }
            followState.realignedEpoch = epoch
            if manner == .spoken {
                speakTo(target, run: run, now: now)
            } else {
                flyTo(target, run: run, now: now, speaking: false)
            }
        }
    }

    /// A word from their phone, given to the guess. Their own line, after
    /// only their presence, starts the guess afresh: a verse's first line,
    /// always a scroll behind, is no rest to learn a pace or a scroll from.
    /// And the page meets it once, from wherever presence left it: a
    /// rationed roster's verse can be half a screen from their line, and
    /// inside the band nothing would ever correct that.
    private func feed(_ report: ReadingReport, fromPresence: Bool, to run: FollowRun, chapterCount: Int) {
        if !fromPresence {
            if !run.heardTheirLine, run.estimate.reported != nil {
                run.estimate = ReadingEstimate()
                followState.realignedEpoch = nil
            }
            run.heardTheirLine = true
        }
        let news = !report.settled || !PagePoint.same(report.at, run.estimate.reported)
        let measured = run.measure(around: report.at.chapter, count: chapterCount, text: { chapterContent($0) })
        run.estimate.observe(report, rulers: { measured[$0] })
        run.fromPresence = fromPresence
        if news {
            run.stuckAt = nil
            run.noFlyTo = nil
            run.spokenTo = nil
        }
    }

    /// Under VoiceOver, a landing on the verse their phone said. The same
    /// verse is not landed on again until they say something new: a verse
    /// taller than the screen never brings their line onto it, and landing
    /// there again every tick pulled the voice back to the verse's start
    /// four times a second.
    private func speakTo(_ point: ReadingPoint, run: FollowRun, now: Date) {
        if let last = run.spokenTo, last.chapter == point.chapter, last.verse == point.verse { return }
        run.spokenTo = (chapter: point.chapter, verse: point.verse)
        flyTo(point, run: run, now: now, speaking: true)
    }

    /// Everything that keeps the page still for now, whatever the guess
    /// says: a finger on it, a scroll or a move of ours under way, the
    /// rubber band, and anything the reader is doing on the page — a verse
    /// lifted, the toolbar or a composer up, a note open or unfurling, the
    /// chapter list, the presence panel, a highlight's label, the book
    /// closing. The page never moves out from under what you are doing.
    private func pageIsBusy(_ run: FollowRun, now: Date) -> Bool {
        let state = followState
        if let landing, !landing.arrived {
            // A flight that has not arrived in three seconds is not going
            // to — a chapter that would not come, or was never downloaded.
            // It lets go, and that chapter is not tried again until they
            // say something new. Timed from when this loop first saw it if
            // the loop did not send it: one started before a restart, or
            // the page's own opening landing, stuck when the follow began —
            // or nothing would ever let go of it.
            let chapter = landing.address.chapter
            if run.flying?.chapter != chapter { run.flying = (chapter: chapter, since: now, began: now) }
            // A chapter whose words are still coming — a licensed version,
            // streamed — has not failed to come: its three seconds start
            // once they are here. A quarter of a minute in all, and it is
            // given up on all the same.
            if chapterContent(chapter) == nil, !chapterFailed.contains(chapter) {
                run.flying?.since = now
            }
            if let flying = run.flying,
               now.timeIntervalSince(flying.since) > 3 || now.timeIntervalSince(flying.began) > 15 {
                endLanding()
                run.noFlyTo = flying.chapter
                run.flying = nil
                // It left the page at the head of that chapter, which is no
                // place to hold: once the chapter is set, the first move
                // brings the guess to the landing line from wherever it is.
                followState.realignedEpoch = nil
            }
            return true
        }
        run.flying = nil
        return fingerDown || state.phase != .idle || now < state.movingUntil
            || state.bandInPlay || !state.viewportMeasured || closing
            || lifted != nil || composer != nil || openNoteVerse != nil
            || now.timeIntervalSince(state.noteMovedAt) < RibbonMotion.settleDuration + 0.1
            || showChapters || state.panelOpen || highlightLabel != nil
    }

    /// Where a point is on this screen, from the viewport's top — only if
    /// its chapter is built and typeset here, and nil otherwise, which is a
    /// flight rather than a step aimed at a chapter that isn't there.
    private func screenY(of point: ReadingPoint) -> CGFloat? {
        guard chaptersOnPage.contains(point.chapter),
              let layout = chapterLayouts[point.chapter],
              let frame = chapterFrames[point.chapter],
              let y = PagePoint.y(of: point, in: layout)
        else { return nil }
        return frame.minY + y
    }

    /// A step: the whole page moved on by `distance`, the way they move
    /// their own. The mark is set first and aimed at on the next turn of
    /// the main queue, as a landing's is — it has to be on the page before
    /// the scroll view can find it.
    private func stepPage(by distance: CGFloat, guessAt y: CGFloat?, run: FollowRun, calm: Bool) {
        // Put at the top of the screen — where `.top` really puts a view,
        // as the landing does — the mark leaves the page exactly `distance`
        // on from where it rests: measured from the chapter the screen's
        // top is in, not the guess's, and never held inside that chapter.
        // Held to the top of the guess's chapter, a step into the next one
        // brought its head all the way up, past their passage end and past
        // the line their phone had said.
        guard let rest = restingPlace(), let frame = chapterFrames[rest.chapter] else { return }
        followMark = FollowPlace(chapter: rest.chapter, y: rest.y + distance)
        run.step = (chapter: rest.chapter, top: frame.minY, guess: y)
        followState.movingUntil = Date().addingTimeInterval(3)
        let step = followState.nextStep(calm ? .fade : .ease)
        DispatchQueue.main.async { followStep = step }
    }

    /// The follow's move, made by the ScrollViewReader.
    private func take(_ step: FollowStep, with proxy: ScrollViewProxy) {
        switch step.way {
        case .ease:
            withAnimation(RibbonMotion.settle) {
                proxy.scrollTo(FollowMark(), anchor: .top)
            } completion: {
                followStepped()
            }
        case .fade:
            // Reduce motion: the text lets go, is there, and comes back — a
            // fade, which §11 turns movement into, and not a cut, which it
            // does not. Opacity, so the plain tokens.
            //
            // The jump comes a fade after the step was asked for, and the
            // page may no longer be the follow's by then: a finger came
            // down, the rubber band took it, "continue" ended the follow and
            // went on. Then it stays where the reader has it, and only the
            // text comes back.
            withAnimation(RibbonMotion.release) {
                pageOpacity = 0
            } completion: {
                let still = model.followingPersonID != nil && followState.phase == .idle
                    && !followState.bandInPlay && !fingerDown && !closing
                if still { proxy.scrollTo(FollowMark(), anchor: .top) }
                withAnimation(RibbonMotion.arrive) {
                    pageOpacity = 1
                } completion: {
                    followStepped(made: still)
                }
            }
        case .back:
            // The rubber band letting go: whatever the fling was still
            // doing, the page goes back where it was resting, on `release`
            // — a gesture let go of halfway, back where it was — and under
            // reduce motion it is simply back.
            withAnimation(RibbonMotion.release(still: reduceMotion)) {
                proxy.scrollTo(FollowMark(), anchor: .top)
            } completion: {
                bandReturned()
            }
        }
    }

    /// A step has come to rest. One that moved the page by less than a
    /// point — the foot of the book, a page that would not go — is not
    /// asked for again until the guess has moved on or they have said
    /// something new. A step that was never made (`made` false: the page
    /// was the reader's again by the time it could be) says nothing about
    /// whether the page would go.
    private func followStepped(made: Bool = true) {
        followState.movingUntil = Date().addingTimeInterval(0.15)
        guard let run = followState.run, let step = run.step else { return }
        run.step = nil
        guard made else { return }
        if let top = chapterFrames[step.chapter]?.minY, abs(top - step.top) >= 1 { return }
        run.stuckAt = step.guess ?? .infinity
    }

    /// A flight of the follow's own. The landing's page-fly, without its
    /// keeping of your place: being sent after someone is not your own
    /// going somewhere. Under reduce motion it is simply there (I22).
    private func flyTo(_ point: ReadingPoint, run: FollowRun, now: Date, speaking: Bool) {
        run.flying = (chapter: point.chapter, since: now, began: now)
        land(
            at: VerseAddress(bookID: reading.bookID, chapter: point.chapter, verse: point.verse),
            animated: true, keepingYourPlace: false, speaking: speaking)
    }

    // MARK: The rubber band

    /// A drag began while following. The first one of a follow is the
    /// rubber band (§4.2: "a gentle rubber-band on the first gesture so it
    /// never happens by accident"): it scrolls, and on letting go the page
    /// goes back where it was resting. Any later one — one that starts
    /// while the band is still going back included — ends the follow at
    /// its first movement, and the scroll is all yours. With nothing to go
    /// back to, the first one ends it.
    ///
    /// SwiftUI gives a scroll view no way to resist a finger, so the band
    /// scrolls freely under it and resists only by returning.
    private func gestureBegan() {
        let touched = followState.restAtTouch
        followState.restAtTouch = nil
        guard model.followingPersonID != nil else { return }
        guard followState.band == .unused, followedIsHere, let rest = touched ?? restingPlace() else {
            endFollow()
            return
        }
        followState.band = .held(rest: rest)
        // The band's own drag is you, and counts as being here.
        presentHere(activity: true)
    }

    /// The finger has let go of the band: back at once, whatever the fling
    /// had in mind.
    private func gestureEnded() {
        guard case .held(let rest) = followState.band else { return }
        followState.band = .returning
        followMark = rest
        followState.movingUntil = Date().addingTimeInterval(3)
        let step = followState.nextStep(.back)
        DispatchQueue.main.async { followStep = step }
    }

    private func bandReturned() {
        followState.movingUntil = Date().addingTimeInterval(0.15)
        if followState.band == .returning { followState.band = .spent }
    }

    /// The person you follow is in the room and in this book.
    private var followedIsHere: Bool {
        guard let id = model.followingPersonID,
              let them = model.presentPeople.first(where: { $0.id == id })
        else { return false }
        return them.position.map { $0.bookID == reading.bookID } ?? true
    }

    /// Where the page rests now, as a mark would find it again: the last
    /// chapter on the page whose top is at or above the top of the screen,
    /// and how far below its top the screen's is. That can be past the
    /// chapter's own lines — in the passage end under it, on the card —
    /// or, with no chapter above the top (the book's top margin), above
    /// the first chapter's head. The mark goes there all the same, and is
    /// never pulled back inside the chapter: it is a point, and put at the
    /// top of the screen it leaves the page exactly where it was, where a
    /// mark held to the chapter would bring the next chapter's head up.
    private func restingPlace() -> FollowPlace? {
        let top = scrollTop ?? topInset
        var rest: FollowPlace?
        for n in chaptersOnPage.sorted() {
            guard let frame = chapterFrames[n] else { continue }
            if frame.minY <= top || rest == nil {
                rest = FollowPlace(chapter: n, y: top - frame.minY)
            }
            if frame.minY > top { break }
        }
        return rest
    }

    /// A scroll nobody accounts for — not a finger's, not its momentum,
    /// not a move of ours — is still yours: VoiceOver's three-finger
    /// scroll, a keyboard's, a trackpad's. More than a quarter of a screen
    /// of it ends a follow, with no rubber band, because nobody does it by
    /// accident. A page laid out again does not move the offset, so a note
    /// opening or a chapter arriving is not taken for one.
    private func watchForOwnScroll(_ offset: CGFloat) {
        let state = followState
        let accounted = state.phase == .interacting || state.phase == .decelerating
            || state.phase == .tracking || Date() < state.movingUntil
            || (landing.map { !$0.arrived } ?? false)
        guard model.followingPersonID != nil, !accounted, let quiet = state.quietOffset else {
            state.quietOffset = offset
            return
        }
        if abs(offset - quiet) > viewportHeight * 0.25 {
            state.quietOffset = offset
            endFollow()
        }
    }

    // MARK: Being followed (§4.2)

    /// Where this page's reading line is, in the words both phones share,
    /// for whoever follows you: the point at the upper third, and the last
    /// thing you can actually see — above the home indicator and whatever
    /// the bottom chrome is covering. Measured from the page every time,
    /// never taken from where a landing is holding it. The channel keeps
    /// it and sends it only while somebody present follows you.
    private func sayWhereImReading(settled: Bool) {
        guard followState.isOpen, !model.readingQuietly, !closing, !followState.bandInPlay else { return }
        let line = viewportHeight * 0.3
        guard let at = pagePoint(at: line) else { return }
        let bottom = viewportHeight - followState.bottomInset - followState.chromeHeight
        var seen = pagePoint(at: max(line, bottom))
        // Never two chapters on: a short chapter wholly on screen ends
        // where the next one does.
        if let last = seen, last.chapter > at.chapter + 1 {
            seen = chapterLayouts[at.chapter + 1].flatMap { PagePoint.end(of: at.chapter + 1, in: $0) }
        }
        let end = seen
        let book = reading.bookID
        let carried = model.followingPersonID != nil
        Task {
            await model.presence.sendReading(
                book: book, at: at, end: end, settled: settled, carried: carried)
        }
    }

    /// A line at this height on the screen, as a point. Over a passage end
    /// or a card it is the chapter above, read to its end; over the
    /// finishing, the last chapter's.
    private func pagePoint(at line: CGFloat) -> ReadingPoint? {
        var above: (chapter: Int, layout: ChapterLayout)?
        for n in chaptersOnPage.sorted() {
            guard let frame = chapterFrames[n], let layout = chapterLayouts[n] else { continue }
            if line < frame.minY { break }
            if line < frame.maxY { return PagePoint.at(line - frame.minY, chapter: n, in: layout) }
            above = (chapter: n, layout: layout)
        }
        guard let above else { return nil }
        return PagePoint.end(of: above.chapter, in: above.layout)
    }

    /// Mid-scroll, once a second at most, while somebody follows you.
    private func sayWhileScrolling() {
        let state = followState
        guard state.phase != .idle, Date().timeIntervalSince(state.lastInFlight) >= 1,
              let me = model.me?.id,
              model.presentPeople.contains(where: { $0.followingPersonID == me })
        else { return }
        state.lastInFlight = Date()
        sayWhereImReading(settled: false)
    }

    /// The page at rest: a moment after the last movement of the scroll or
    /// of the layout, with no finger on it and nothing under way. However
    /// it came to rest — a fling, a jump with no animation, a landing, a
    /// note opening — its resting place is said, to presence (which a
    /// throttle that only ever fires as a scroll begins had never heard)
    /// and to whoever follows.
    private func settleSoon() {
        let state = followState
        state.settleDue = Date().addingTimeInterval(0.3)
        guard state.settleTask == nil else { return }
        state.settleTask = Task {
            while let due = state.settleDue, due > Date(), !Task.isCancelled {
                try? await Task.sleep(for: .seconds(max(0, due.timeIntervalSinceNow)))
            }
            state.settleTask = nil
            state.settleDue = nil
            guard !Task.isCancelled, state.phase == .idle, !fingerDown else { return }
            // Not activity: the scroll that got here already said so.
            presentHere(activity: false)
            sayWhereImReading(settled: true)
        }
    }

    // MARK: Landing on a verse (deviation 7, I30)

    /// The verse a place is at. A card's is the head of its chapter — the
    /// address a reader there is at, which is what presence says ("Mark 6").
    private func address(of place: ReadingPlace?) -> VerseAddress {
        switch place {
        case .verse(let address)?:
            return address
        case .card(let chapter)?:
            return VerseAddress(bookID: reading.bookID, chapter: chapter, verse: 1)
        case nil:
            return model.myPosition(in: reading)
        }
    }

    /// Sends the page to a place: its card, or its verse.
    private func go(to place: ReadingPlace?, opening: Bool) {
        if case .card(let chapter)? = place {
            landOnCard(of: chapter, opening: opening)
        } else {
            land(at: address(of: place), animated: false, opening: opening)
        }
    }

    /// Sends the page to the card at the foot of a chapter (§6.4). "The
    /// cards are open" is about the card, and it sits below the chapter's
    /// last verse: landing on the chapter's head put a whole chapter between
    /// the reader and what they had been told about. The passage end comes
    /// to the top of the screen. It is not a verse, so nothing is held —
    /// your place stays your own until you read on.
    ///
    /// The last chapter has no passage end: it ends in the finishing, and a
    /// card is never set there. A card named in it lands on its chapter.
    private func landOnCard(of chapter: Int, opening: Bool) {
        guard chapter >= 1, chapter < (book?.chapterCount ?? 1) else {
            land(
                at: VerseAddress(bookID: reading.bookID, chapter: max(1, chapter), verse: 1),
                animated: false, opening: opening)
            return
        }
        keepYourPlace()
        endLanding()
        landingMove = LandingMove(target: .passageEnd(chapter), animated: false)
    }

    /// Wherever the page is now is where you were reading, and a move is
    /// about to take it somewhere else: keep it, ahead of the throttle. A
    /// page held where it was sent has not been read from, and keeps
    /// nothing.
    private func keepYourPlace() {
        if landing == nil, let latestAddress, latestAddress != model.myPosition(in: reading) {
            model.savePosition(reading: reading, address: latestAddress)
        }
    }

    /// Sends the page to a verse.
    ///
    /// The verse's first line comes to rest just above the reading line —
    /// the upper third, the line `trackReading` reads your place from — so
    /// the page, measuring itself, finds the verse it was sent to. It used
    /// to land on the top of the verse's chapter, where the same measure
    /// found a verse a screen or more earlier, and saved that: every time the
    /// book was opened and closed, your place slid back to the head of its
    /// chapter.
    ///
    /// A verse near the head of its chapter does not move the page past the
    /// head. With the chapter's top at the top of the screen it is already
    /// above the line, and the chapter opens as a chapter.
    ///
    /// Animated, the page travels one way only. A chapter that is not on the
    /// page comes in at its top when the page is travelling down to it and
    /// at its foot travelling up, and the second move, to the line, is made
    /// only if it carries on the same way. A verse it would have to turn back
    /// for is already on the screen, and turning back is the overshoot §9.1
    /// forbids.
    ///
    /// A follow's flight uses the same moves, but it is not your own going
    /// somewhere, so it keeps nothing (`keepingYourPlace: false`).
    private func land(
        at address: VerseAddress, animated: Bool, opening: Bool = false,
        keepingYourPlace: Bool = true, speaking: Bool = false
    ) {
        guard address.bookID == reading.bookID else { return }
        if keepingYourPlace { keepYourPlace() }
        let from = latestAddress ?? model.myPosition(in: reading)
        latestAddress = address
        landingMark = nil
        let chapter = address.chapter
        if opening {
            // The page starts at the top of the book: the first chapter is
            // already here, and every other one is further down.
            if chapter <= 1 {
                landing = Landing(address: address, animated: false, approach: .bookTop)
            } else {
                landing = Landing(address: address, animated: false, approach: .travelling(down: true, done: false))
                landingMove = LandingMove(target: .chapter(chapter, .top), animated: false)
            }
        } else if chaptersOnPage.contains(chapter), chapterLayouts[chapter] != nil {
            landing = Landing(address: address, animated: animated, approach: .onPage, speaks: speaking, from: from)
            continueLanding(in: chapter)
        } else {
            let down = address >= from
            landing = Landing(
                address: address, animated: animated,
                approach: .travelling(down: down, done: false), speaks: speaking, from: from)
            landingMove = LandingMove(target: .chapter(chapter, down ? .top : .bottom), animated: animated)
        }
    }

    /// The second half of a landing, once the verse's chapter is on the
    /// page and typeset: the move to the line itself — or none, when the
    /// verse is already where it should be.
    private func continueLanding(in chapter: Int) {
        guard var landing, landing.address.chapter == chapter,
              !landing.placed, !landing.arrived,
              chaptersOnPage.contains(chapter),
              let layout = chapterLayouts[chapter],
              let frame = chapterFrames[chapter]
        else { return }
        // The first time, what `.top` means on this screen.
        switch landing.approach {
        case .onPage:
            break
        case .bookTop:
            // At rest, the first chapter sits the page's top margin below
            // where `.top` would put it.
            learnScrollTop(frame.minY - Self.pageTop)
        case .travelling(let down, let done):
            guard done else { return }
            // Just put there by `.top`: this is what `.top` means — except
            // in the last chapter, which can be too short for the page to
            // scroll its top all the way up.
            if down, chapter < (book?.chapterCount ?? 1) {
                learnScrollTop(frame.minY)
            }
        }
        guard let verseY = firstLine(of: landing.address.verse, in: layout) else {
            arrive()
            return
        }
        let line = viewportHeight * 0.3 - Self.landingLead
        let top = scrollTop ?? topInset
        // Far enough into the chapter that the mark, put at the top, leaves
        // the verse's line on the reading line — but never above the
        // chapter's own top: a verse near the head of its chapter is already
        // above the line with the chapter beginning at the top of the
        // screen, and the chapter opens as a chapter.
        let markY = max(0, verseY + top - line)
        // How far the page has to travel for it: down the book is positive.
        let distance = frame.minY - top + markY
        let goes: Bool
        switch landing.approach {
        case .bookTop:
            // Opened at the very top of the book, margin and all: it moves
            // only for a verse below the line.
            goes = frame.minY + verseY - line > 1
        case .travelling(let down, _) where landing.animated && !reduceMotion:
            goes = down ? distance > 1 : distance < -1
        default:
            goes = abs(distance) > 1
        }
        guard goes else {
            arrive()
            return
        }
        landingMark = (chapter, markY)
        landing.placed = true
        self.landing = landing
        let animated = landing.animated
        // The mark has to be on the page before it can be aimed at.
        DispatchQueue.main.async {
            landingMove = LandingMove(target: .mark, animated: animated)
        }
    }

    private func landingMoved(_ move: LandingMove) {
        guard let landing else { return }
        switch move.target {
        case .chapter(let chapter, _):
            guard chapter == landing.address.chapter,
                  case .travelling(let down, false) = landing.approach
            else { return }
            self.landing?.approach = .travelling(down: down, done: true)
            continueLanding(in: chapter)
        case .mark:
            guard landing.placed, !landing.arrived else { return }
            arrive()
        case .passageEnd:
            break
        }
    }

    /// There. From here the landing is a hold, against whatever
    /// `trackReading` next finds the page at rest on.
    private func arrive() {
        landing?.arrived = true
        landing?.line = nil
        // Under VoiceOver a follow's landing hands the listener the verse it
        // came to, which reads itself — no new words. Only when it took
        // them somewhere: a landing where the page already was, on the verse
        // it was already on, leaves the voice where it is.
        if let landing, landing.speaks, landing.moved || landing.address != landing.from,
           let element = pages[landing.address.chapter]?.accessibilityElement(forVerse: landing.address.verse) {
            UIAccessibility.post(notification: .layoutChanged, argument: element)
        }
    }

    /// The page is yours again.
    private func endLanding() {
        if landing != nil { landing = nil }
        if landingMark != nil { landingMark = nil }
    }

    /// The verse the page was sent to, for as long as it is still where you
    /// are; nil, and the page is yours.
    ///
    /// Without the hold, the page's own measure would decide where you are
    /// the moment it came to rest — and that measure is only as good as the
    /// landing's aim. A line out either way, and every open would move you a
    /// verse. Held, you are where you were sent until a scroll carries the
    /// reading line off the verse it came to rest on.
    private func heldLanding(against address: VerseAddress) -> VerseAddress? {
        guard let landing else { return nil }
        guard landing.arrived else {
            // Still on its way. A finger on the page takes it back mid-flight.
            if fingerDown {
                endLanding()
                return nil
            }
            return landing.address
        }
        guard let line = landing.line else {
            self.landing?.line = address
            return landing.address
        }
        if line == address { return landing.address }
        endLanding()
        return nil
    }

    /// What a scroll to `.top` was seen to mean, kept if it is a believable
    /// answer: no higher than the scroll view's own top, and above the
    /// reading line. Learned once, normally as the book opens, and kept for
    /// as long as the page is open.
    private func learnScrollTop(_ value: CGFloat) {
        guard scrollTop == nil, value >= -1, value < viewportHeight * 0.3 - Self.landingLead else { return }
        scrollTop = value
    }

    /// A verse's first line in its chapter — or, in a version without that
    /// verse (there are verses some translations leave out), the nearest one
    /// before it that the version has.
    private func firstLine(of verse: Int, in layout: ChapterLayout) -> CGFloat? {
        if let y = layout.verseFirstLineY[verse] { return y }
        return layout.verseFirstLineY.filter { $0.key < verse }.max { $0.key < $1.key }?.value
    }

    private func close() {
        guard !closing else { return }
        closing = true
        // Closing ends a follow. A page only ever carried by one was not
        // read past the hint, and fed nothing (§4.2: carried is not
        // reading).
        let carried = model.followingPersonID != nil
        endFollow(telling: false)
        if !carried {
            if !model.state.hasSeenMarginHint {
                model.markMarginHintSeen()
            }
            recordFuel()
        }
        // Where you stopped: the page's own last word on it, ahead of the
        // throttled save — unless the page is still held where it was sent,
        // which is not somewhere you read to (I30). Then it is your own
        // place, untouched.
        let here = landing == nil ? (latestAddress ?? model.myPosition(in: reading)) : model.myPosition(in: reading)
        if here != model.myPosition(in: reading) {
            model.savePosition(reading: reading, address: here)
        }
        // Closing the book leaves the ribbon where you were — only if you
        // moved, and never in a finished book (A30).
        if let openedAt, openedAt != here, !reading.isFinished {
            model.leaveTheRibbon(in: reading, at: here)
        }
        onClose()
    }

    private func trackReading(chapter: Int, frame: CGRect) {
        // The chapter whose top has crossed the upper third is where you
        // are.
        let threshold = viewportHeight * 0.3
        guard frame.minY < threshold, frame.maxY > threshold else { return }
        let yInChapter = threshold - frame.minY
        // The same measure the reading line is sent with: of verses that
        // begin on one line, the last.
        let verse = chapterLayouts[chapter].flatMap { PagePoint.at(yInChapter, chapter: chapter, in: $0)?.verse } ?? 1
        let measured = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        // A verse the page was sent to stays where you are until you move
        // off it (I30).
        let held = heldLanding(against: measured)
        let address = held ?? measured
        latestAddress = address
        if headChapter != address.chapter { headChapter = address.chapter }
        let fraction = max(0, min(1, Double(yInChapter / max(1, frame.height))))
        followState.lastFraction = fraction
        // Carried is not reading (§4.2). While a follow moves the page, the
        // movement is the follow's: your place is not saved on the way (the
        // follow's end keeps it, and so does closing), the fire is not fed,
        // and the stillness clock does not reset — a follower who never
        // touches the page goes "here, but still" like anyone else. The
        // place itself still travels, so "Mark 6" stays right.
        let carried = model.followingPersonID != nil
        // Position saves are cheap but not free — a scroll emits geometry
        // every frame, and the store persists on mutation.
        if Date().timeIntervalSince(lastPositionSave) > 2 {
            lastPositionSave = Date()
            // Being sent to a verse is not reading to it: your own place
            // waits until you do.
            if held == nil, !carried {
                model.savePosition(reading: reading, address: address)
            }
            if !model.readingQuietly {
                let following = model.followingPersonID
                Task {
                    await model.presence.present(
                        position: address, scrollFraction: fraction,
                        isIdle: false, following: following, activity: !carried)
                }
            }
        }
        if !carried, Date().timeIntervalSince(lastFuelRecord) > 25 {
            recordFuel(at: address)
        }
    }

    private func recordFuel(at address: VerseAddress? = nil) {
        lastFuelRecord = Date()
        model.recordReadingActivity(
            reading: reading,
            at: address ?? model.myPosition(in: reading))
    }
}

// S03 — the passage end: the one place with more than one thing to do.
// S03 — the passage end: the one place with more than one thing to do.
// Generous space, a hairline rule at the measure's width, the card (S08/S09),
// the continue control, and the Wave larger here as the deliberate close.
struct PassageEndView: View {
    @Environment(AppModel.self) private var model
    let reading: Reading
    let chapter: Int
    let nextChapterTitle: String
    var onContinue: () -> Void
    var onClose: () -> Void

    var body: some View {
        VStack(spacing: 26) {
            Spacer().frame(height: 34)
            HairlineRule()
                .padding(.leading, 36)
                .padding(.trailing, 26)

            if let room = model.room(of: reading) {
                let card = model.card(for: reading, chapter: chapter)
                if card.state != .setDown {
                    ReflectionCardView(card: card, reading: reading, room: room)
                        .padding(.horizontal, 24)
                        // Set down, it leaves without ceremony — which is
                        // not the same as leaving between two frames.
                        .transition(.opacity)
                }
            }

            Button(action: onContinue) {
                Text(nextChapterTitle)
                    .font(RibbonType.uiMedium(17))
                    .foregroundStyle(Palette.text)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle().inset(by: -8))
            }
            .buttonStyle(.plain)
            // The Wave is drawn at 28 pt; its touch target is not (the
            // pencil-only close on iPad was exactly this).
            Button(action: onClose) {
                WaveMark(color: Palette.text.opacity(0.45))
                    .frame(width: 28, height: 28)
                    .frame(width: 72, height: 52)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Copy.closeTheBook)
            Spacer().frame(height: 30)
        }
    }
}
