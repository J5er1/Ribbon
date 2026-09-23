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
    /// Where the book opened, so closing knows whether you moved (A30).
    @State private var openedAt: VerseAddress?
    /// Where you are right now, as the page reports it — ahead of the
    /// throttled save, so the ribbon is left where you were and not where
    /// the last save was.
    @State private var latestAddress: VerseAddress?
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

    enum ComposerState: Equatable {
        case toolbar
        case write(VerseAddress)
        case speak(VerseAddress)
    }

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
                                onContinue: { withAnimation(RibbonMotion.settle(still: reduceMotion)) { proxy.scrollTo(n + 1, anchor: .top) } },
                                onClose: close)
                        }
                    }
                    finishingSection
                }
                .padding(.top, 26)
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
            .onScrollPhaseChange { _, newPhase in
                fingerDown = newPhase == .interacting || newPhase == .tracking
            }
            .onAppear {
                let position = openAt ?? model.myPosition(in: reading)
                openedAt = position
                if position.chapter > 1 {
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    proxy.scrollTo(position.chapter, anchor: .top)
                }
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
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    withAnimation(RibbonMotion.settle(still: reduceMotion)) {
                        proxy.scrollTo(command, anchor: .top)
                    }
                    scrollCommand = nil
                }
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
    private var currentChapter: Int {
        model.myPosition(in: reading).chapter
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
                    onLayout: { chapterLayouts[n] = $0 },
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
            }
            .coordinateSpace(name: "chapter")
            .onGeometryChange(for: CGRect.self) { geometry in
                geometry.frame(in: .scrollView)
            } action: { frame in
                chapterFrames[n] = frame
                trackReading(chapter: n, frame: frame)
            }
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
                        scrollCommand = offer.address.chapter
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
        // (§4.2).
        followBackOffer = (model.myPosition(in: reading), Date().addingTimeInterval(120))
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

    private func close() {
        guard !closing else { return }
        closing = true
        if !model.state.hasSeenMarginHint {
            model.markMarginHintSeen()
        }
        recordFuel()
        // Closing the book leaves the ribbon where you were — only if you
        // moved, and never in a finished book (A30).
        let here = latestAddress ?? model.myPosition(in: reading)
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
        let address = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        latestAddress = address
        // Position saves are cheap but not free — a scroll emits geometry
        // every frame, and the store persists on mutation.
        if Date().timeIntervalSince(lastPositionSave) > 2 {
            lastPositionSave = Date()
            model.savePosition(reading: reading, address: address)
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
