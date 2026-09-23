import SwiftUI
import RibbonCore

// S02 — the surface everything else exists to protect. No top bar, no back
// button, no toolbar until you ask for one. Two ways out, both at the
// bottom: the Wave mark, and a downward drag from scroll-top that settles
// like a book closing.

struct ReadingScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let room: Room
    let reading: Reading
    /// A named place to open at (a waiting row's note, a quoted verse) —
    /// nil opens at your own position.
    var openAt: VerseAddress?
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
    /// were" for about two minutes, then forgets (§4.2).
    @State private var followBackOffer: (address: VerseAddress, until: Date)?
    /// Ignore self-originated (programmatic) scrolls when deciding whether
    /// a scroll of your own breaks a follow.
    @State private var programmaticScrollUntil = Date.distantPast
    /// Asks the ScrollViewReader to go somewhere, from outside its closure.
    @State private var scrollCommand: Int?
    /// Who the page has been carried for, and to which chapter. The roster
    /// says the same thing every tick, and `myPosition` lags behind it by a
    /// throttle — so without this the page would be yanked back to the top
    /// of a chapter they are still reading down, once a tick.
    @State private var carriedTo: (person: UUID, chapter: Int)?

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
    }

    private struct LandingMove: Equatable {
        enum Target: Equatable {
            case chapter(Int, UnitPoint)
            case mark
        }
        var target: Target
        var animated: Bool
    }

    /// The landing's mark, as the ScrollViewReader knows it.
    private struct LandingMark: Hashable {}

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
                                onContinue: {
                                    endLanding()
                                    withAnimation(RibbonMotion.settle(still: reduceMotion)) { proxy.scrollTo(n + 1, anchor: .top) }
                                },
                                onClose: close)
                        }
                    }
                    finishingSection
                }
                .padding(.top, Self.pageTop)
                // The measure: Scripture holds a readable line length on
                // any canvas — the reading surface is the product, and a
                // 150-character line is not reading.
                .readableColumn(maxWidth: 680)
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
            }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.containerSize.height
            } action: { _, height in
                if height > 0 { viewportHeight = height }
            }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentInsets.top
            } action: { _, top in
                topInset = top
            }
            .onScrollPhaseChange { _, newPhase in
                fingerDown = newPhase == .interacting || newPhase == .tracking
            }
            .onAppear {
                let position = openAt ?? model.myPosition(in: reading)
                openedAt = model.myPosition(in: reading)
                // A named place is your own going somewhere, and a follow
                // still running from before ends here (§4.2) — or the
                // roster's next tick would carry the page off the verse it
                // was sent to.
                if openAt != nil { model.followingPersonID = nil }
                // On the verse, not the top of its chapter (deviation 7,
                // I30). The page is rising while this happens, so the moves
                // are made without animation: it arrives already there.
                land(at: position, animated: false, opening: true)
                recordFuel()
                if !model.readingQuietly {
                    // The channel is the room's and is already open; this is
                    // the book's half — saying you are in it (§4.2).
                    Task {
                        await model.presence.present(
                            position: position, scrollFraction: 0,
                            isIdle: false, following: model.followingPersonID)
                    }
                }
            }
            .onDisappear {
                // Out of the book, still in the room: the line stays open so
                // the room keeps hearing about itself.
                Task { await model.presence.withdraw() }
            }
            .onChange(of: model.readingQuietly) { _, quietly in
                Task {
                    if quietly {
                        await model.presence.withdraw()
                    } else {
                        await model.presence.present(
                            position: model.myPosition(in: reading), scrollFraction: 0,
                            isIdle: false, following: model.followingPersonID)
                    }
                }
            }
            .onChange(of: scrollCommand) { _, command in
                if let command {
                    // A chapter chosen, or a follow carrying the page: either
                    // way it is somewhere else now, and a landing lets go.
                    endLanding()
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    withAnimation(RibbonMotion.settle(still: reduceMotion)) {
                        proxy.scrollTo(command, anchor: .top)
                    }
                    scrollCommand = nil
                }
            }
            .onChange(of: landingMove) { _, move in
                guard let move else { return }
                landingMove = nil
                programmaticScrollUntil = Date().addingTimeInterval(1.5)
                withAnimation(move.animated ? RibbonMotion.settle(still: reduceMotion) : nil) {
                    switch move.target {
                    case .chapter(let n, let anchor):
                        proxy.scrollTo(n, anchor: anchor)
                    case .mark:
                        proxy.scrollTo(LandingMark(), anchor: .top)
                    }
                } completion: {
                    // Straight away when nothing animated, at the end of the
                    // ease when something did.
                    landingMoved(move)
                }
            }
            .onChange(of: openAt) { _, target in
                // A named place asked for while the book is already open — a
                // notification tapped over the page (S19). This page used to
                // stay where it was. It goes there now as it would have
                // opened there: at once, the way Android has always taken
                // it. Going somewhere is your own move, so a follow ends, as
                // a scroll of your own would end it (§4.2).
                guard let target else { return }
                model.followingPersonID = nil
                land(at: target, animated: false)
            }
            .onChange(of: model.presentPeople) { _, roster in
                followAlong(roster)
            }
        }
        .overlay(alignment: .trailing) {
            if !room.isPaused {
                PresenceForm(room: room, onFollow: follow)
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
        .overlay(alignment: .bottom) { bottomChrome }
        .overlay(alignment: .center) { highlightLabelOverlay }
        .room()
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showChapters) {
            ChaptersSheet(reading: reading, currentChapter: currentChapter) { chapter in
                showChapters = false
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
                WayInButton(title: Copy.putItOnTheShelf) {
                    onFinished()
                }
                QuietControl(title: Copy.startAnother) {
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
        withAnimation(RibbonMotion.settle) {
            openNoteVerse = nil
            noteSlotY = [:]
        }
    }

    private func follow(_ person: PresentPerson) {
        // Tap a portrait to follow — a page-fly, no confirmation dialog
        // (§4.2). Where you were is where the page says, not the last
        // throttled save of it.
        followBackOffer = (latestAddress ?? model.myPosition(in: reading), Date().addingTimeInterval(120))
        model.followingPersonID = person.id
        if let position = person.position {
            carriedTo = (person.id, position.chapter)
            scrollCommand = position.chapter
        }
        // "Ruth is with you" is the other end of this, and it only ever
        // appears because the follow travels: without this the flag was set
        // on this phone and never left it.
        if !model.readingQuietly {
            Task {
                await model.presence.present(
                    position: model.myPosition(in: reading), scrollFraction: 0,
                    isIdle: false, following: person.id)
            }
        }
    }

    /// Following is a thread, not a jump (§4.2).
    ///
    /// Tapping a portrait moved the page once and then let go: they read on,
    /// and you sat where they had been, still called a follower by the
    /// thread at the top and by their own "Ruth is with you". The page has
    /// to keep up, or the word means nothing.
    ///
    /// Through `scrollCommand`, not the proxy, for two reasons. It eases —
    /// the page carries you, it does not cut. And it opens the same grace
    /// window a tap does: without it the app's own move would read as a
    /// scroll of your own in `trackReading`, and the follow would cut itself
    /// on the first page they turned.
    ///
    /// Only their chapter, and only when it changes. Their scroll within a
    /// chapter is a finer signal than this page can honestly answer, and a
    /// command per roster tick would be the page twitching under a reader.
    private func followAlong(_ roster: [PresentPerson]) {
        guard let followed = model.followingPersonID,
              let them = roster.first(where: { $0.id == followed }),
              let there = them.position,
              // A room reads one book at a time, but a roster can still
              // carry somebody who has moved on to another one — and their
              // chapter 3 is not this book's.
              there.bookID == reading.bookID
        else { return }
        if let carriedTo, carriedTo.person == followed, carriedTo.chapter == there.chapter {
            return
        }
        carriedTo = (followed, there.chapter)
        scrollCommand = there.chapter
    }

    // MARK: Landing on a verse (deviation 7, I30)

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
    private func land(at address: VerseAddress, animated: Bool, opening: Bool = false) {
        guard address.bookID == reading.bookID else { return }
        // Wherever the page is now is where you were reading, and this is
        // about to take it somewhere else: keep it, ahead of the throttle.
        if landing == nil, let latestAddress, latestAddress != model.myPosition(in: reading) {
            model.savePosition(reading: reading, address: latestAddress)
        }
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
            landing = Landing(address: address, animated: animated, approach: .onPage)
            continueLanding(in: chapter)
        } else {
            let down = address >= from
            landing = Landing(address: address, animated: animated, approach: .travelling(down: down, done: false))
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
        }
    }

    /// There. From here the landing is a hold, against whatever
    /// `trackReading` next finds the page at rest on.
    private func arrive() {
        landing?.arrived = true
        landing?.line = nil
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
        if !model.state.hasSeenMarginHint {
            model.markMarginHintSeen()
        }
        recordFuel()
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
        // Any scroll of your own breaks the follow — no modal, no "stop
        // following?", you just have your own scroll back (§4.2).
        if model.followingPersonID != nil, Date() > programmaticScrollUntil {
            model.followingPersonID = nil
        }
        let layout = chapterLayouts[chapter]
        let yInChapter = threshold - frame.minY
        let verse = layout?.verseFirstLineY
            .filter { $0.value <= yInChapter }
            .max { $0.value < $1.value }?.key ?? 1
        let measured = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        // A verse the page was sent to stays where you are until you move
        // off it (I30).
        let held = heldLanding(against: measured)
        let address = held ?? measured
        latestAddress = address
        if headChapter != address.chapter { headChapter = address.chapter }
        // Position saves are cheap but not free — a scroll emits geometry
        // every frame, and the store persists on mutation.
        if Date().timeIntervalSince(lastPositionSave) > 2 {
            lastPositionSave = Date()
            // Being sent to a verse is not reading to it: your own place
            // waits until you do.
            if held == nil {
                model.savePosition(reading: reading, address: address)
            }
            if !model.readingQuietly {
                let fraction = max(0, min(1, Double(yInChapter / max(1, frame.height))))
                Task {
                    await model.presence.present(
                        position: address, scrollFraction: fraction,
                        isIdle: false, following: model.followingPersonID)
                }
            }
        }
        if Date().timeIntervalSince(lastFuelRecord) > 25 {
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
