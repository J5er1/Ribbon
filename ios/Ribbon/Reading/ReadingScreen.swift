import SwiftUI
import RibbonCore

// S02 — the surface everything else exists to protect. No top bar, no back
// button, no toolbar until you ask for one. Two ways out, both at the
// bottom: the Wave mark, and a downward drag from scroll-top that settles
// like a book closing.

struct ReadingScreen: View {
    @Environment(\.appModel) private var model
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    let room: Room
    let reading: Reading
    /// A named place to open at (a waiting row's note, a quoted verse) —
    /// nil opens at your own position.
    var openAt: VerseAddress?
    /// Open at a chapter's end — the card there (S08/S09).
    var openAtPassageEnd: Int?
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
    @State private var showInkPicker = false

    // Open note (one at a time; a stack opens whole)
    @State private var openNoteVerse: VerseAddress?
    @State private var noteCardHeight: CGFloat = 120
    @State private var noteSlotY: [Int: CGFloat] = [:]

    // Layout & tracking
    @State private var chapterLayouts: [Int: ChapterLayout] = [:]
    @State private var chapterFrames: [Int: CGRect] = [:]
    @State private var closing = false
    @State private var fingerDown = false
    @State private var pulledPastTop = false
    /// The live viewport height — "the upper third" must mean this
    /// screen's third, not a phone's (a hardcoded 240 misplaces the
    /// position by half a screen on a 13" iPad).
    @State private var viewportHeight: CGFloat = 800
    @State private var contentOffset: CGFloat = 0
    @State private var lastFuelRecord = Date.distantPast
    @State private var lastPositionSave = Date.distantPast
    @State private var lastScrollAt = Date()
    @State private var hasScrolled = false
    /// The one-time hint was scrolled past (S02) — not merely seen.
    @State private var hintPassed = false
    @State private var didReachEnd = false
    @State private var finishingVisible = false
    /// The whole book fits its screen (2 John on an iPad): nothing can be
    /// scrolled, so looking at it for a while is reaching the end.
    @State private var contentFits = false
    /// After a follow ends, the form quietly offers "back to where you
    /// were" for about two minutes, then forgets (§4.2).
    @State private var followBackOffer: (address: VerseAddress, until: Date)?
    @State private var offerTick = Date()
    /// Ignore self-originated (programmatic) scrolls when deciding whether
    /// a scroll of your own breaks a follow.
    @State private var programmaticScrollUntil = Date.distantPast
    /// Asks the ScrollViewReader to go to a chapter, from outside its
    /// closure; the verse, if any, settles once the chapter has laid out.
    @State private var scrollCommand: Int?
    @State private var pendingVerse: VerseAddress?
    @State private var scrollPosition = ScrollPosition()
    /// The chapter the book opened at: the one-time hint lives there
    /// (§6.1), whatever chapter that is.
    @State private var firstOpenedChapter = 1
    @State private var presenceExpanded = false

    enum ComposerState: Equatable {
        case toolbar
        case write(VerseAddress)
    }

    private var book: BibleBook? { Bible.book(id: reading.bookID) }
    private var chapterCount: Int { book?.chapterCount ?? 1 }
    private var translation: TranslationID { model.me?.translation ?? .bsb }
    private var bookText: ScriptureBookText? {
        model.scripture.book(reading.bookID, translation: translation)
    }
    private var licensed: Translation? {
        guard let t = TranslationRegistry.translation(for: translation), !t.isBundled else { return nil }
        return t
    }
    /// Chapters of a licensed translation, as they stream in (§16.8).
    @State private var remoteChapters: [Int: ScriptureChapter] = [:]
    /// Chapters that couldn't stream: the Berean text stands in, with a
    /// line and a way to try again (S25, §2.5).
    @State private var remoteFailed: Set<Int> = []
    @State private var remoteAttempt = 0

    private func chapterContent(_ n: Int) -> ScriptureChapter? {
        bookText?.chapter(n) ?? remoteChapters[n]
    }

    /// The reading is a record (a finished book reopened from its ember):
    /// its pages are read, never marked (S11).
    private var isRecord: Bool { reading.isFinished }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(1...chapterCount, id: \.self) { n in
                        chapterSection(n)
                            .id(n)
                        if n < chapterCount {
                            PassageEndView(
                                reading: reading,
                                chapter: n,
                                nextChapterTitle: book?.chapterHeading(n + 1) ?? "\(n + 1)",
                                onContinue: {
                                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                                    withAnimation(reduceMotion ? nil : RibbonMotion.settle) {
                                        proxy.scrollTo(n + 1, anchor: UnitPoint(x: 0, y: -0.04))
                                    }
                                },
                                onClose: close)
                            .id(passageEndID(n))
                        }
                    }
                    finishingSection
                }
                .scrollTargetLayout()
                .padding(.top, 26)
                // The measure: Scripture holds a readable line length on
                // any canvas — the reading surface is the product, and a
                // 150-character line is not reading. The panel insets it
                // rather than covering it (§12.1).
                .readableColumn(maxWidth: 680)
                .padding(.trailing, presenceExpanded ? (dynamicTypeSize.isAccessibilitySize ? 120 : 160) : 0)
                .animation(reduceMotion ? nil : RibbonMotion.open, value: presenceExpanded)
            }
            .scrollIndicators(.hidden)
            .scrollPosition($scrollPosition)
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top
            } action: { _, offset in
                contentOffset = offset
                // The closing drag: pulled well past the top, the page
                // settles closed once the finger lets go — never mid-pull.
                if offset < -90, fingerDown, !closing {
                    pulledPastTop = true
                }
                if fingerDown { hasScrolled = true; lastScrollAt = Date() }
                // The hint is dismissed by scrolling past it (S02): the
                // opened chapter's top has gone well above the viewport.
                if fingerDown, let frame = chapterFrames[firstOpenedChapter], frame.minY < -48 {
                    hintPassed = true
                }
            }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.containerSize.height
            } action: { _, height in
                if height > 0 { viewportHeight = height }
            }
            .onScrollGeometryChange(for: Bool.self) { geometry in
                geometry.contentSize.height <= geometry.containerSize.height
            } action: { _, fits in
                contentFits = fits
            }
            .onScrollPhaseChange { old, newPhase in
                fingerDown = newPhase == .interacting || newPhase == .tracking
                if old == .interacting, newPhase != .interacting, pulledPastTop {
                    pulledPastTop = false
                    close()
                }
            }
            .onAppear {
                let position = openAt ?? model.myPosition(in: reading)
                firstOpenedChapter = openAtPassageEnd ?? position.chapter
                if let end = openAtPassageEnd {
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    proxy.scrollTo(passageEndID(end), anchor: .top)
                } else if position.chapter > 1 || position.verse > 1 {
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    proxy.scrollTo(position.chapter, anchor: .top)
                    if position.verse > 1 { pendingVerse = position }
                }
                recordFuel()
                Task { await model.startPresence(in: room) }
            }
            .onChange(of: scrollCommand) { _, command in
                if let command {
                    programmaticScrollUntil = Date().addingTimeInterval(1.5)
                    withAnimation(reduceMotion ? nil : RibbonMotion.settle) {
                        proxy.scrollTo(command, anchor: .top)
                    }
                    scrollCommand = nil
                }
            }
        }
        .overlay(alignment: .trailing) {
            if !room.isPaused, !room.isDeparted, !isRecord {
                PresenceForm(room: room, expanded: $presenceExpanded, onFollow: follow)
            }
        }
        .overlay(alignment: .topTrailing) {
            if model.followingPersonID != nil { FollowThread() }
        }
        .overlay(alignment: .top) { thinkingOfYouLine }
        .overlay(alignment: .bottom) { bottomChrome }
        .room()
        .preferredColorScheme(.dark)
        .onDisappear {
            // Scrolled past: the hint has been seen (§6.1, S02). Merely
            // opening and closing the book leaves it for next time.
            if hintPassed { model.markMarginHintSeen() }
            Task { await model.stopPresence() }
        }
        .onChange(of: chapterLayouts) { _, _ in settlePendingVerse() }
        .onChange(of: model.followTarget) { _, target in
            // Your scroll is theirs (§4.2): the followed person moved.
            guard model.followingPersonID != nil, let target else { return }
            pendingVerse = target
            scrollCommand = target.chapter
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, !remoteFailed.isEmpty {
                // Back from elsewhere: the chapters that couldn't stream
                // try again on their own (S25 — resumes automatically).
                remoteFailed = []
                remoteAttempt += 1
            }
        }
        .task {
            // Idle: about four minutes with no scroll — here, but still
            // (§4.2). Checked on a slow clock; nothing here is a count.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(30))
                let idle = Date().timeIntervalSince(lastScrollAt) > 240
                await model.updatePresence(position: currentAddress(), scrollFraction: 0, isIdle: idle)
                offerTick = Date()
            }
        }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
    }

    private func passageEndID(_ chapter: Int) -> String { "end-\(chapter)" }

    // MARK: Chapters

    @ViewBuilder
    private func chapterSection(_ n: Int) -> some View {
        if let chapter = chapterContent(n) {
            chapterText(n, chapter, fallbackLine: nil)
        } else if let licensed, remoteFailed.contains(n), let fallback = model.scripture.chapter(
            VerseAddress(bookID: reading.bookID, chapter: n, verse: 1), translation: .bsb) {
            // The licensed chapter couldn't stream: the Berean stands in,
            // with a line and a way to try again. Scripture is never
            // locked (§2.5), and a page is never blank.
            chapterText(n, fallback, fallbackLine: Copy.bereanForNow(licensed.displayName))
        } else if let licensed {
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
            .task(id: remoteAttempt) {
                let address = VerseAddress(bookID: reading.bookID, chapter: n, verse: 1)
                if let chapter = await model.scripture.ensureRemoteChapter(address, translation: licensed) {
                    withAnimation(reduceMotion ? nil : RibbonMotion.arrive) {
                        remoteChapters[n] = chapter
                    }
                } else {
                    remoteFailed.insert(n)
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

    @ViewBuilder
    private func chapterText(_ n: Int, _ chapter: ScriptureChapter, fallbackLine: String?) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            if let fallbackLine {
                HStack(spacing: 14) {
                    SmallCaps(fallbackLine, size: 11)
                    QuietControl(title: Copy.tryAgain) {
                        remoteFailed.remove(n)
                        remoteAttempt += 1
                    }
                }
                .padding(.leading, 36)
            }
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
                    isFirstChapter: n == firstOpenedChapter,
                    showMarginHint: !model.state.hasSeenMarginHint && n == firstOpenedChapter && !isRecord && !room.isDeparted,
                    onLayout: { chapterLayouts[n] = $0 },
                    readOnly: isRecord || room.isDeparted,
                    onLongPressVerse: { verse in beginLift(chapter: n, verse: verse) },
                    onHighlightVerse: { verse in highlightVerse(chapter: n, verse: verse) },
                    onDragToVerse: { verse in extendLift(chapter: n, verse: verse) },
                    onDragEnded: {},
                    onTapVerse: { verse in tapVerse(chapter: n, verse: verse) },
                    onNoteSlot: { y in noteSlotY[n] = y })

                gutterMarks(chapter: n)
                openNoteCard(chapter: n)
            }
        }
        .onGeometryChange(for: CGRect.self) { geometry in
            geometry.frame(in: .scrollView)
        } action: { frame in
            chapterFrames[n] = frame
            trackReading(chapter: n, frame: frame)
        }
        .padding(.bottom, 8)
    }

    /// VoiceOver's "Highlight" action: the verse takes the ink a hold would
    /// offer first, with no toolbar in between (§11 motor).
    private func highlightVerse(chapter: Int, verse: Int) {
        guard !isRecord, !room.isDeparted, !room.isPaused else { return }
        let range = VerseRange(bookID: reading.bookID, chapter: chapter, startVerse: verse, endVerse: verse)
        model.addHighlight(range, ink: model.inkForNewHighlight(in: room) ?? model.lastUsedInk, in: reading)
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

    /// A named verse lands in the upper third once its chapter has laid
    /// out — a note jump lands on the note, not on the chapter's top
    /// (§6.3).
    private func settlePendingVerse() {
        guard let target = pendingVerse,
              let layout = chapterLayouts[target.chapter],
              let verseY = layout.verseFirstLineY[target.verse],
              let frame = chapterFrames[target.chapter]
        else { return }
        pendingVerse = nil
        let absoluteY = contentOffset + frame.minY + verseY - viewportHeight * 0.3
        programmaticScrollUntil = Date().addingTimeInterval(1.5)
        scrollPosition.scrollTo(y: max(0, absoluteY))
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
        @Environment(\.appModel) private var model
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
                        ink: model.inkForDisplay(note.authorID, in: roomID),
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
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .accessibilityElement()
            .accessibilityLabel(accessibilityLabel)
            .accessibilityAddTraits(.isButton)
        }

        private var accessibilityLabel: String {
            // §11, exactly: "Note from Ruth, verse 9, not yet found." A
            // stack announces by author and never by count; the kind is
            // said, since the shape can't be seen.
            let names = notes.compactMap { model.person($0.authorID)?.name }
            let unfound = notes.contains { !$0.foundBy.contains(model.me?.id ?? UUID()) && $0.authorID != model.me?.id }
            let who = names.isEmpty ? Copy.youLower : Copy.names(Set(names).sorted())
            let allVoice = notes.allSatisfy { $0.kind == .voice }
            let kind: String
            if notes.count == 1 {
                kind = allVoice ? Copy.voiceNoteKind : Copy.writtenNoteKind
            } else {
                kind = allVoice ? Copy.voiceNotesKind : Copy.writtenNotesKind
            }
            return Copy.noteFrom(kind, who, notes.first?.verse.verse ?? 0, unfound: unfound)
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
                        authorInk: model.inkForDisplay(note.authorID, in: room.id),
                        onTakeBack: (isRecord || note.authorID != model.me?.id) ? nil : {
                            model.takeBack(note)
                            if stack.count <= 1 { closeNote() }
                        },
                        onEdit: (isRecord || note.authorID != model.me?.id || note.kind != .written) ? nil : {
                            editingNote = note
                            composer = .write(note.verse)
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

    // MARK: Thinking of you, received (§4.3)

    @ViewBuilder
    private var thinkingOfYouLine: some View {
        if let name = model.thinkingOfYouFrom {
            SmallCaps(Copy.notifThinkingOfYou(firstName(name)), size: 12)
                .padding(.top, 8)
                .transition(.opacity)
                .animation(RibbonMotion.arrive, value: name)
        }
    }

    // MARK: Bottom chrome — the way out, or the composer

    @ViewBuilder
    private var bottomChrome: some View {
        switch composer {
        case .toolbar:
            if let lifted, let chapter = liftedChapter {
                let range = VerseRange(
                    bookID: reading.bookID, chapter: chapter,
                    startVerse: lifted.startVerse, endVerse: lifted.endVerse)
                LeaveToolbar(
                    room: room,
                    range: range,
                    roomPaused: room.isPaused,
                    recorder: recorder,
                    existingHighlight: model.highlights(in: reading, chapter: chapter)
                        .first { $0.range.verses.contains(lifted.startVerse) },
                    onHighlight: { ink in
                        model.addHighlight(range, ink: ink, in: reading)
                        clearLift()
                    },
                    onRemoveHighlight: { highlight in
                        model.removeHighlight(highlight)
                        clearLift()
                    },
                    onWrite: { composer = .write(range.start) },
                    onSpeakKept: { url, waveform in
                        model.leaveVoiceNote(audioURL: url, waveform: waveform, at: range.start, in: reading)
                        clearLift()
                    },
                    onPickInk: { showInkPicker = true })
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
                .readableColumn()
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
                    clearLift()
                },
                onCancel: clearLift)
            .padding(.bottom, 10)
        case nil:
            VStack(spacing: 10) {
                // After a follow ends: the quiet offer back, for about two
                // minutes, then it forgets (§4.2).
                if let offer = followBackOffer,
                   offerTick < offer.until,
                   model.followingPersonID == nil {
                    QuietControl(title: Copy.backToWhereYouWere) {
                        pendingVerse = offer.address
                        scrollCommand = offer.address.chapter
                        followBackOffer = nil
                    }
                }
                // The way out: the Wave, ~20 pt, muted ivory, centred at
                // the bottom edge. Nothing else down there. The glass
                // capsule stays small; the touch target doesn't — a
                // finger must be able to close the book (44 pt minimum).
                // A hold on it opens the presence panel, which is how
                // read quietly is reachable when you are alone (S07).
                // Not a Button: a hold that ended would fire a Button's
                // action on release and close the book it just opened.
                WaveMark(color: Palette.text.opacity(0.55))
                    .frame(width: 20, height: 20)
                    .padding(.horizontal, 26)
                    .padding(.vertical, 9)
                    .ribbonGlass(in: Capsule())
                    .frame(minWidth: 88, minHeight: 52)
                    .contentShape(Rectangle())
                    .onTapGesture(perform: close)
                    .onLongPressGesture(minimumDuration: 0.5) {
                        guard !isRecord, !room.isPaused, !room.isDeparted else { return }
                        withAnimation(RibbonMotion.open) { presenceExpanded = true }
                    }
                    .hoverEffect(.lift)
                    .accessibilityElement()
                    .accessibilityLabel(Copy.closeTheBook)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction { close() }
                    .accessibilityAction(named: Copy.readQuietly) {
                        guard !isRecord, !room.isPaused, !room.isDeparted else { return }
                        withAnimation(RibbonMotion.open) { presenceExpanded = true }
                    }
                // Esc closes the book on a hardware keyboard.
                Button(action: close) { EmptyView() }
                    .keyboardShortcut(.cancelAction)
                    .hidden()
                    .accessibilityHidden(true)
            }
            .padding(.bottom, 6)
        }
        if composer != nil {
            // Esc puts the toolbar or composer away before it closes the
            // book (§12.3, ported by deviation 12).
            Button(action: clearLift) { EmptyView() }
                .keyboardShortcut(.cancelAction)
                .hidden()
                .accessibilityHidden(true)
        }
    }

    // MARK: The finishing sequence (§6.5)

    @ViewBuilder
    private var finishingSection: some View {
        // The one place that gets to feel like an event — and it still has
        // no confetti, no badge, and no number. A finished book reopened
        // from its ember shows the ember, and no ceremony twice.
        VStack(spacing: 18) {
            Spacer().frame(height: 70)
            FireBecomesEmber(
                scale: reading.handiwork.scale, coalDepth: reading.handiwork.coalDepth,
                begins: finishingVisible, alreadyEmber: isRecord)
            Text(book?.name ?? "")
                .font(RibbonType.display(30))
                .foregroundStyle(Palette.text)
            SmallCaps(
                RibbonClock.emberRange(start: reading.startedAt, end: reading.finishedAt ?? Date()),
                size: 13)
            if isRecord {
                Text(Copy.bookIsFinished)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                QuietControl(title: Copy.closeTheBook, action: close)
                    .padding(.top, 8)
            } else {
                VStack(spacing: 16) {
                    WayInButton(title: Copy.putItOnTheShelf) {
                        finishNow()
                        onFinished()
                    }
                    if !room.isPaused {
                        QuietControl(title: Copy.startAnother) {
                            finishNow()
                            onStartAnother()
                        }
                    }
                }
                .padding(.horizontal, 60)
                .padding(.top, 16)
            }
            Spacer().frame(height: 80)
        }
        .onGeometryChange(for: Bool.self) { proxy in
            // Finishing means reaching the end (§6.5), not a lazy stack
            // prefetching it: the sequence counts only once it is actually
            // inside the viewport.
            let viewportHeight = proxy.bounds(of: .scrollView)?.height ?? 800
            return proxy.frame(in: .scrollView).minY < viewportHeight * 0.85
        } action: { visible in
            finishingVisible = visible
            guard visible, !isRecord, !didReachEnd else { return }
            considerFinishing()
        }
        .task(id: finishingVisible) {
            // A book that fits its screen (2 John on an iPad) can't be
            // scrolled: it counts as read once it has been looked at for
            // a while, not the instant it opened.
            guard finishingVisible, !isRecord, !didReachEnd else { return }
            try? await Task.sleep(for: .seconds(6))
            considerFinishing(force: true)
        }
    }

    private func considerFinishing(force: Bool = false) {
        guard finishingVisible, !isRecord, !didReachEnd else { return }
        // Reached by scrolling — or, for a book that fits its screen,
        // looked at for a while (deviation 20). A short last chapter
        // reopened at a saved position still has to be scrolled to.
        guard hasScrolled || (force && contentFits) else { return }
        finishNow()
    }

    private func finishNow() {
        guard !didReachEnd, !reading.isFinished else { return }
        didReachEnd = true
        model.finishReading(reading)
    }

    // MARK: Intents

    private func beginLift(chapter: Int, verse: Int) {
        guard !isRecord, !room.isDeparted else { return }
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
        // A cancelled edit must not become the next note's identity.
        editingNote = nil
    }

    private func tapVerse(chapter: Int, verse: Int) {
        // Tapping the text: dismiss the toolbar first; then notes.
        // A highlight's author lives in the toolbar now (hold the verse).
        if composer != nil {
            clearLift()
            return
        }
        let address = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        let stack = model.notes(in: reading, chapter: chapter).filter { $0.verse.verse == verse }
        if !stack.isEmpty {
            toggleNote(at: address)
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
        // (§4.2). Your scroll is theirs from here until you scroll.
        followBackOffer = (currentAddress(), Date().addingTimeInterval(120))
        model.follow(person)
        if let position = person.position {
            pendingVerse = position
            scrollCommand = position.chapter
        }
    }

    private func close() {
        guard !closing else { return }
        closing = true
        recordFuel()
        onClose()
    }

    /// Where the reader is right now, from the chapter under the upper
    /// third.
    private func currentAddress() -> VerseAddress {
        let threshold = viewportHeight * 0.3
        for (chapter, frame) in chapterFrames where frame.minY < threshold && frame.maxY > threshold {
            let yInChapter = threshold - frame.minY
            let verse = chapterLayouts[chapter]?.verseFirstLineY
                .filter { $0.value <= yInChapter }
                .max { $0.value < $1.value }?.key ?? 1
            return VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        }
        return model.myPosition(in: reading)
    }

    private func trackReading(chapter: Int, frame: CGRect) {
        // The chapter whose top has crossed the upper third is where you
        // are.
        let threshold = viewportHeight * 0.3
        guard frame.minY < threshold, frame.maxY > threshold else { return }
        // Any scroll of your own breaks the follow — no modal, no "stop
        // following?", you just have your own scroll back (§4.2). Only a
        // scroll: a note unfurling or a chapter laying out is not one.
        if model.followingPersonID != nil, fingerDown, Date() > programmaticScrollUntil {
            model.stopFollowing()
        }
        if chapter != firstOpenedChapter, hasScrolled {
            model.markMarginHintSeen()
        }
        guard !isRecord else { return }
        let layout = chapterLayouts[chapter]
        let yInChapter = threshold - frame.minY
        let verse = layout?.verseFirstLineY
            .filter { $0.value <= yInChapter }
            .max { $0.value < $1.value }?.key ?? 1
        let address = VerseAddress(bookID: reading.bookID, chapter: chapter, verse: verse)
        // Position saves are cheap but not free — a scroll emits geometry
        // every frame, and the store persists on mutation.
        if Date().timeIntervalSince(lastPositionSave) > 2 {
            lastPositionSave = Date()
            model.savePosition(reading: reading, address: address)
            let fraction = max(0, min(1, Double(yInChapter / max(1, frame.height))))
            Task { await model.updatePresence(position: address, scrollFraction: fraction, isIdle: false) }
        }
        if Date().timeIntervalSince(lastFuelRecord) > 25 {
            recordFuel(at: address)
        }
    }

    private func recordFuel(at address: VerseAddress? = nil) {
        guard !isRecord else { return }
        lastFuelRecord = Date()
        model.recordReadingActivity(
            reading: reading,
            at: address ?? model.myPosition(in: reading))
    }
}

// S03 — the passage end: the one place with more than one thing to do.
// Generous space — about three lines' worth — a hairline rule at the
// measure's width, the card if there is one (S08/S09), the continue
// control, and the Wave larger here as the deliberate close.
struct PassageEndView: View {
    let reading: Reading
    let chapter: Int
    let nextChapterTitle: String
    var onContinue: () -> Void
    var onClose: () -> Void

    var body: some View {
        VStack(spacing: 26) {
            Spacer().frame(height: 60)
            HairlineRule()
                .padding(.leading, 36)
                .padding(.trailing, 26)
            PassageCardSlot(reading: reading, chapter: chapter)
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
