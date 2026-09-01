import SwiftUI
import RibbonCore

// S02 — the surface everything else exists to protect. No top bar, no back
// button, no toolbar until you ask for one. Two ways out, both at the
// bottom: the Wave mark, and a downward drag from scroll-top that settles
// like a book closing.

struct ReadingScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let room: Room
    let reading: Reading
    /// A named place to open at (a waiting row's note, a quoted verse) —
    /// nil opens at your own position.
    var openAt: VerseAddress?
    var onClose: () -> Void
    var onFinished: () -> Void

    // Composition state
    @State private var lifted: VerseRange?
    @State private var liftedChapter: Int?
    @State private var composer: ComposerState?
    @State private var recorder = VoiceRecorder()
    @State private var editingNote: Note?

    // Open note (one at a time; a stack opens whole)
    @State private var openNoteVerse: VerseAddress?
    @State private var noteCardHeight: CGFloat = 120
    @State private var noteSlotY: [Int: CGFloat] = [:]

    // Layout & tracking
    @State private var chapterLayouts: [Int: ChapterLayout] = [:]
    @State private var chapterFrames: [Int: CGRect] = [:]
    @State private var closing = false
    @State private var fingerDown = false
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

    enum ComposerState: Equatable {
        case toolbar
        case write(VerseAddress)
        case speak(VerseAddress)
    }

    private var book: BibleBook? { Bible.book(id: reading.bookID) }
    private var translation: TranslationID { model.me?.translation ?? .bsb }
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
                                nextChapterTitle: book?.chapterHeading(n + 1) ?? "\(n + 1)",
                                onContinue: { withAnimation(RibbonMotion.settle) { proxy.scrollTo(n + 1, anchor: .top) } },
                                onClose: close)
                        }
                    }
                    finishingSection
                }
                .padding(.top, 26)
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
            .onScrollPhaseChange { _, newPhase in
                fingerDown = newPhase == .interacting || newPhase == .tracking
            }
            .onAppear {
                let position = openAt ?? model.myPosition(in: reading)
                if position.chapter > 1 {
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    proxy.scrollTo(position.chapter, anchor: .top)
                }
                recordFuel()
            }
            .onChange(of: scrollCommand) { _, command in
                if let command {
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    withAnimation(RibbonMotion.settle) {
                        proxy.scrollTo(command, anchor: .top)
                    }
                    scrollCommand = nil
                }
            }
        }
        .overlay(alignment: .trailing) {
            if !room.isPaused {
                PresenceForm(room: room, onFollow: follow)
                    .padding(.trailing, 0)
            }
        }
        .overlay(alignment: .topTrailing) {
            if model.followingPersonID != nil { FollowThread() }
        }
        .overlay(alignment: .bottom) { bottomChrome }
        .overlay(alignment: .center) { highlightLabelOverlay }
        .room()
        .preferredColorScheme(.dark)
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
                    verseInks: verseInks(chapter: n),
                    liftedVerses: liftedChapter == n ? lifted.map { $0.verses } : nil,
                    openNote: openNote(in: n),
                    isFirstChapter: n == 1,
                    showMarginHint: !model.state.hasSeenMarginHint && n == 1,
                    onLayout: { chapterLayouts[n] = $0 },
                    onLongPressVerse: { verse in beginLift(chapter: n, verse: verse) },
                    onDragToVerse: { verse in extendLift(chapter: n, verse: verse) },
                    onDragEnded: {},
                    onTapVerse: { verse in tapVerse(chapter: n, verse: verse) },
                    onNoteSlot: { y in noteSlotY[n] = y })

                gutterMarks(chapter: n)
                openNoteCard(chapter: n)
            }
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
            // skeleton lines, which read as fake text.
            VStack(alignment: .leading) {
                SmallCaps(
                    book?.chapterHeading(n) ?? "\(reading.bookID) \(n)", size: 14,
                    color: Palette.text.opacity(0.4))
                Spacer().frame(height: 320)
            }
            .padding(.leading, 36)
            .task {
                let address = VerseAddress(bookID: reading.bookID, chapter: n, verse: 1)
                if let chapter = await model.scripture.ensureRemoteChapter(address, translation: licensed) {
                    withAnimation(RibbonMotion.arrive) {
                        remoteChapters[n] = chapter
                    }
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

    private func verseInks(chapter: Int) -> [Int: [Ink]] {
        var result: [Int: [Ink]] = [:]
        for highlight in model.highlights(in: reading, chapter: chapter) {
            for verse in highlight.range.verses {
                result[verse, default: []].append(highlight.ink)
            }
        }
        return result
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
            .contentShape(Rectangle().inset(by: -10))
            .onTapGesture(perform: onTap)
            .accessibilityElement()
            .accessibilityLabel(accessibilityLabel)
            .accessibilityAddTraits(.isButton)
        }

        private var accessibilityLabel: String {
            // §11, exactly: "Note from Ruth, verse 9, not yet found." A
            // stack announces by author and never by count.
            let names = notes.compactMap { model.person($0.authorID)?.name }
            let unfound = notes.contains { !$0.foundBy.contains(model.me?.id ?? UUID()) && $0.authorID != model.me?.id }
            let who = names.isEmpty ? "you" : Set(names).sorted().joined(separator: " and ")
            let noun = notes.count == 1 ? "Note" : "Notes"
            return "\(noun) from \(who), verse \(notes.first?.verse.verse ?? 0)\(unfound ? ", not yet found" : "")"
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
                        onEdit: { editingNote = note; composer = .write(note.verse) })
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
                    range: VerseRange(
                        bookID: reading.bookID, chapter: chapter,
                        startVerse: lifted.startVerse, endVerse: lifted.endVerse),
                    roomPaused: room.isPaused,
                    onHighlight: { ink in
                        model.addHighlight(
                            VerseRange(bookID: reading.bookID, chapter: chapter,
                                       startVerse: lifted.startVerse, endVerse: lifted.endVerse),
                            ink: ink, in: reading)
                        clearLift()
                    },
                    onWrite: { composer = .write(VerseAddress(bookID: reading.bookID, chapter: chapter, verse: lifted.startVerse)) },
                    onSpeak: { composer = .speak(VerseAddress(bookID: reading.bookID, chapter: chapter, verse: lifted.startVerse)) })
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
                    }
                    editingNote = nil
                    clearLift()
                },
                onCancel: { editingNote = nil; clearLift() })
            .padding(.bottom, 10)
        case .speak(let address):
            SpeakControl(
                ink: model.inkForNewHighlight(in: room) ?? model.lastUsedInk,
                recorder: recorder,
                onKeep: { url, waveform in
                    model.leaveVoiceNote(audioURL: url, waveform: waveform, at: address, in: reading)
                    clearLift()
                },
                onDismiss: clearLift)
            .padding(.horizontal, 40)
            .padding(.bottom, 14)
        case nil:
            VStack(spacing: 10) {
                // After a follow ends: the quiet offer back, for about two
                // minutes, then it forgets (§4.2).
                if let offer = followBackOffer,
                   Date() < offer.until,
                   model.followingPersonID == nil {
                    QuietControl(title: Copy.backToWhereYouWere) {
                        scrollCommand = offer.address.chapter
                        followBackOffer = nil
                    }
                }
                // The way out: the Wave, ~20 pt, muted ivory, centred at
                // the bottom edge. Nothing else down there.
                Button(action: close) {
                    WaveMark(color: Palette.text.opacity(0.55))
                        .frame(width: 20, height: 20)
                        .padding(.horizontal, 26)
                        .padding(.vertical, 9)
                        .ribbonGlass(in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Copy.closeTheBook)
            }
            .padding(.bottom, 6)
        }
    }

    @ViewBuilder
    private var highlightLabelOverlay: some View {
        if let highlight = highlightLabel {
            // A small label naming who made it, and remove if it's yours
            // (S06).
            VStack(spacing: 10) {
                HStack(spacing: 8) {
                    InkDot(ink: highlight.ink)
                    Text(model.person(highlight.authorID)?.name ?? "")
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.text)
                }
                if highlight.authorID == model.me?.id {
                    Button {
                        model.removeHighlight(highlight)
                        highlightLabel = nil
                    } label: {
                        SmallCaps(Copy.remove, size: 12, color: Palette.muted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
            .ribbonGlass(in: RoundedRectangle(cornerRadius: 16))
            .onTapGesture { highlightLabel = nil }
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
            FireBecomesEmber(scale: reading.handiwork.scale, coalDepth: reading.handiwork.coalDepth)
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
                    onFinished()
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
                for note in model.notes(in: reading, chapter: address.chapter)
                where note.verse.verse == address.verse {
                    model.markFound(note)
                }
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
        // (§4.2). With no live presence roster this is unreachable; the
        // mechanics are here for when the socket is.
        followBackOffer = (model.myPosition(in: reading), Date().addingTimeInterval(120))
        model.followingPersonID = person.id
        if let position = person.position {
            scrollCommand = position.chapter
        }
    }

    private func close() {
        guard !closing else { return }
        closing = true
        if !model.state.hasSeenMarginHint {
            model.markMarginHintSeen()
        }
        recordFuel()
        onClose()
    }

    private func trackReading(chapter: Int, frame: CGRect) {
        // The chapter whose top has crossed the upper third is where you
        // are.
        guard frame.minY < 240, frame.maxY > 240 else { return }
        // Any scroll of your own breaks the follow — no modal, no "stop
        // following?", you just have your own scroll back (§4.2).
        if model.followingPersonID != nil, Date() > programmaticScrollUntil {
            model.followingPersonID = nil
        }
        let layout = chapterLayouts[chapter]
        let yInChapter = 240 - frame.minY
        let verse = layout?.verseFirstLineY
            .filter { $0.value <= yInChapter }
            .max { $0.value < $1.value }?.key ?? 1
        let address = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        // Position saves are cheap but not free — a scroll emits geometry
        // every frame, and the store persists on mutation.
        if Date().timeIntervalSince(lastPositionSave) > 2 {
            lastPositionSave = Date()
            model.savePosition(reading: reading, address: address)
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
// Generous space, a hairline rule at the measure's width, the continue
// control, and the Wave larger here as the deliberate close. (Cards sit
// here when they arrive in phase two.)
struct PassageEndView: View {
    let nextChapterTitle: String
    var onContinue: () -> Void
    var onClose: () -> Void

    var body: some View {
        VStack(spacing: 26) {
            Spacer().frame(height: 34)
            HairlineRule()
                .padding(.leading, 36)
                .padding(.trailing, 26)
            Button(action: onContinue) {
                Text(nextChapterTitle)
                    .font(RibbonType.uiMedium(17))
                    .foregroundStyle(Palette.text)
            }
            .buttonStyle(.plain)
            Button(action: onClose) {
                WaveMark(color: Palette.text.opacity(0.45))
                    .frame(width: 28, height: 28)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Copy.closeTheBook)
            Spacer().frame(height: 30)
        }
    }
}
