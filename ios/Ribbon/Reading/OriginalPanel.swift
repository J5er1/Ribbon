import SwiftUI
import RibbonCore

// The original, on the page (A60, §7). Not a sheet and not glass over the
// verse: a panel in the bottom chrome, on the composers' paper, cross-faded
// in from the leave toolbar the way write and speak are. The selection
// stays above it with its handles live, and the panel says whatever the
// handles now hold. A tap in the text leaves it, as it leaves a composer,
// and the selection goes with it (A62).
//
// Above the toolbar, while a verse is held, a quieter line says the held
// word in the original (§7.5) and is the same door into the panel.

/// The line over the toolbar: `λόγος · logos · Word`, muted, one line.
struct OriginalLineView: View {
    let line: OriginalLine
    var onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            said
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
                .lineLimit(1)
                .truncationMode(.tail)
                .padding(.horizontal, 20)
                // Small to read, a finger's height to take (§11).
                .frame(maxWidth: 420, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Copy.originalWordSpoken(line.translit, line.rendering ?? "", ""))
        .accessibilityHint(Copy.originalLineAction)
    }

    /// The words in their own face, how to say them in italic, and — for
    /// one word — what your version says for it, between middle dots.
    private var said: Text {
        let word = Text(line.original)
            .font(RibbonType.original(17, hebrew: line.isHebrew))
        let translit = Text(line.translit)
            .font(RibbonType.scriptureItalic(14))
        if let rendering = line.rendering {
            return Text("\(word) · \(translit) · \(Text(rendering))")
        }
        return Text("\(word) · \(translit)")
    }
}

/// The panel (§7.2): the heading, the words under the selection in
/// original order with each one's detail a tap away, and how the room's
/// other versions say the same words.
struct OriginalPanel: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let selection: OriginalSelection
    let room: Room
    /// The version on this page — yours.
    let translation: TranslationID
    /// This page's own text, verse by verse: what you selected, exactly.
    let pageTexts: [Int: String]
    /// Where its poetic lines are glued, so a line reads with its break.
    let pageBreaks: [Int: [Int]]
    /// A little over half the screen; past that the panel scrolls.
    let maxHeight: CGFloat
    /// The word to open on: the one held, when the panel came from the line.
    var opening: OriginalSelection.WordID?
    var onClose: () -> Void

    @State private var openWord: OriginalSelection.WordID?
    @State private var contentHeight: CGFloat = 0
    /// Strong's dictionary and the grammar's long forms, once read off the
    /// main thread. Until then a word shows without them; the dictionary is
    /// over a megabyte, and the panel must not wait on it to open.
    @State private var lexicon: Lexicon?
    @State private var parsings: Parsings?
    /// Licensed versions read by others here, streamed for this panel.
    @State private var streamed: [TranslationID: ScriptureChapter] = [:]
    /// Under VoiceOver, opening the panel hands the listener its heading.
    @AccessibilityFocusState private var headingFocused: Bool

    var body: some View {
        ScrollView {
            content
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { height in
                    if abs(height - contentHeight) > 0.5 { contentHeight = height }
                }
        }
        .scrollIndicators(.automatic)
        .scrollBounceBehavior(.basedOnSize)
        // As tall as what it says, up to the ceiling; then it scrolls.
        .frame(height: min(max(contentHeight, 1), maxHeight))
        .paper(.card)
        .padding(.horizontal, 16)
        .readableColumn()
        // The way a composer is left, for a reader who cannot tap the text.
        .accessibilityAction(.escape) { onClose() }
        .onAppear {
            if openWord == nil { openWord = opening }
        }
        .task {
            // The dictionary and the grammar are read once, the first time
            // anybody opens this, and not on the main thread.
            let store = model.original
            let read = await Task.detached(priority: .userInitiated) {
                (store.lexicon, store.parsings)
            }.value
            lexicon = read.0
            parsings = read.1
        }
        .task(id: streamKey) { await stream() }
        .task {
            // After the cross-fade, so the focus lands on what is there.
            try? await Task.sleep(for: .seconds(RibbonMotion.arriveDuration))
            if UIAccessibility.isVoiceOverRunning { headingFocused = true }
        }
    }

    private var isHebrew: Bool { selection.language != .greek }

    private var content: some View {
        let section = roomSection
        return VStack(alignment: .leading, spacing: 16) {
            SmallCaps(Copy.originalHeading(selection.language, selection.range.formatted), size: 12)
                .accessibilityAddTraits(.isHeader)
                .accessibilityFocused($headingFocused)
            ForEach(selection.verses) { verse in
                verseWords(verse)
            }
            if selection.hasWholeFallback {
                Text(Copy.originalWholeVerse(versionName(translation)))
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if section != .omitted {
                inThisRoom(section)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: The words

    @ViewBuilder
    private func verseWords(_ verse: OriginalSelection.Verse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // An address only when there is more than one verse to tell apart.
            if selection.spansVerses {
                SmallCaps(
                    VerseAddress(bookID: selection.bookID, chapter: selection.chapter, verse: verse.verse).formatted,
                    size: 11, color: Palette.muted.opacity(0.8))
            }
            // Hebrew and Aramaic read from the right: the first word is the
            // rightmost, and a row that wraps carries on from the right.
            WordFlow(rightToLeft: isHebrew) {
                ForEach(verse.words) { word in
                    wordColumn(word)
                }
            }
            if let open = openWord, let word = verse.words.first(where: { $0.id == open }) {
                wordDetail(word)
                    .transition(reduceMotion
                        ? AnyTransition.opacity
                        : AnyTransition.opacity.combined(with: .offset(y: -6)))
            }
        }
    }

    private func wordColumn(_ word: OriginalSelection.Word) -> some View {
        let open = openWord == word.id
        let hebrew = word.word.language(in: selection.bookID) != .greek
        return Button {
            // One open at a time; the open one again closes it. Settling,
            // as a note does — and under reduce motion, a fade.
            withAnimation(RibbonMotion.settle) {
                openWord = open ? nil : word.id
            }
        } label: {
            VStack(spacing: 3) {
                Text(word.word.text)
                    .font(RibbonType.original(22, hebrew: hebrew))
                    .foregroundStyle(Palette.text)
                    // Room above and below for the points, so a vowel
                    // under a letter is never clipped.
                    .padding(.vertical, hebrew ? 4 : 1)
                Text(word.word.translit)
                    .font(RibbonType.scriptureItalic(13))
                    .foregroundStyle(Palette.muted)
                Text(word.rendering ?? Copy.originalNotOnItsOwn)
                    .font(RibbonType.ui(13))
                    .foregroundStyle(word.rendering == nil ? Palette.muted.opacity(0.7) : Palette.text.opacity(0.75))
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background {
                if open {
                    RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Palette.raised)
                }
            }
            .frame(minWidth: 44, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // One element per word — a button is one already — read the way
        // it is said rather than in the original's own letters (§7.3).
        .accessibilityLabel(Copy.originalWordSpoken(
            word.word.translit, word.rendering ?? Copy.originalNotOnItsOwn, grammar(of: word) ?? ""))
        .accessibilityAddTraits(open ? [.isSelected] : [])
    }

    private func grammar(of word: OriginalSelection.Word) -> String? {
        guard let parse = word.word.parse else { return nil }
        return parsings?.describe(parse)
    }

    /// A word, opened: its dictionary form and how to say it, Strong's own
    /// definition, its grammar here, and the number — small, last.
    private func wordDetail(_ word: OriginalSelection.Word) -> some View {
        let entry = word.word.strongs.flatMap { lexicon?.entry($0) }
        let hebrew = word.word.language(in: selection.bookID) != .greek
        return VStack(alignment: .leading, spacing: 6) {
            if let entry {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(entry.lemma)
                        .font(RibbonType.original(19, hebrew: hebrew))
                        .foregroundStyle(Palette.text)
                        .padding(.vertical, hebrew ? 3 : 0)
                    Text(entry.translit)
                        .font(RibbonType.scriptureItalic(14))
                        .foregroundStyle(Palette.muted)
                }
                if !entry.definition.isEmpty {
                    Text(entry.definition)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.text.opacity(0.85))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            if let grammar = grammar(of: word) {
                Text(grammar)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let strongs = word.word.strongs {
                SmallCaps(Copy.originalStrongs(strongs), size: 11, color: Palette.muted.opacity(0.8))
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.raised.opacity(0.6), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    // MARK: In this room

    /// Everyone else in the room, and the version each reads.
    private var roomPeople: [RoomPerson] {
        model.members(of: room)
            .filter { $0.personID != model.me?.id }
            .compactMap { model.person($0.personID) }
            .map { RoomPerson(id: $0.id, name: $0.name, version: $0.translation) }
    }

    /// How the room's versions say the selection, grouped by what they say
    /// (A62, §13.3) — worked out in `RoomSection`, drawn here.
    private var roomSection: RoomSection {
        RoomSection.of(
            yours: translation,
            me: model.me?.id,
            others: roomPeople,
            saying: { says(in: $0) },
            versionName: { versionName($0) },
            notOnThisPhone: Copy.originalNotOnThisPhone)
    }

    /// A phrase is set large, as it is on the page; a whole verse, or more
    /// than one, a step smaller so the blocks stay a few lines each.
    private var roomWordsSize: CGFloat {
        !selection.spansVerses && selection.verses.contains(where: \.isPartial) ? 19 : 16
    }

    /// Only where the room reads more than one version: a room of one
    /// version has nothing to compare, and says nothing (the caller leaves
    /// it out). Blocks arrive on `settle`, and at once under reduce motion.
    private func inThisRoom(_ section: RoomSection) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            switch section {
            case .omitted:
                EmptyView()
            case .agrees(let agreement):
                // No heading, and the words are not said a third time: the
                // page and the words above already say them. The others'
                // faces, then the one line.
                HStack(alignment: .center, spacing: 10) {
                    if !agreement.faces.isEmpty {
                        RoomFaces(readers: agreement.faces, room: room)
                    }
                    Text(agreement.line)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(agreement.spoken)
                .transition(.opacity)
            case .blocks(let blocks):
                SmallCaps(Copy.originalInThisRoom, size: 12)
                    .accessibilityAddTraits(.isHeader)
                    .transition(.opacity)
                ForEach(blocks) { block in
                    roomBlock(block)
                        .transition(.opacity)
                }
            }
        }
        .padding(.top, 4)
        .animation(RibbonMotion.settle(still: reduceMotion), value: Self.shape(of: section))
    }

    /// What changing makes the section move: which blocks there are, not
    /// the words inside them as the handles go.
    private static func shape(of section: RoomSection) -> [String] {
        switch section {
        case .omitted: return []
        case .agrees: return ["agrees"]
        case .blocks(let blocks): return blocks.map(\.id)
        }
    }

    /// One thing said: the versions that say it in small caps, the words in
    /// Literata, and who reads it. Yours has no card — its label says it is
    /// yours. One element to VoiceOver: the words, the versions, every name.
    private func roomBlock(_ block: RoomBlock) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            SmallCaps(block.label, size: 11, color: Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            roomWords(block)
                .fixedSize(horizontal: false, vertical: true)
            if !block.readers.isEmpty {
                HStack(alignment: .center, spacing: 10) {
                    RoomFaces(readers: block.faces, room: room)
                    SmallCaps(block.names, size: 11, color: Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(block.spoken)
    }

    /// Yours at full strength. Another rendering with the words yours does
    /// not use at full strength and Medium, and the words it shares a step
    /// back — weight as well as tone. A whole verse standing in is muted
    /// throughout, as it always was; a version not here says so.
    private func roomWords(_ block: RoomBlock) -> Text {
        let size = roomWordsSize
        switch block.kind {
        case .notOnThisPhone:
            return Text(block.words)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
        case .yours:
            return Text(block.words)
                .font(RibbonType.scripture(size))
                .foregroundStyle(Palette.text)
        case .other:
            guard !block.isWholeVerse else {
                return Text(block.words)
                    .font(RibbonType.scripture(size))
                    .foregroundStyle(Palette.muted)
            }
            let shared: Color = Palette.text.opacity(0.7)
            let differs: Color = Palette.text
            var words = AttributedString()
            for run in block.runs {
                var piece = AttributedString(run.text)
                piece.foregroundColor = run.differs ? differs : shared
                if run.differs { piece.font = RibbonType.scriptureMedium(size) }
                words += piece
            }
            return Text(words)
                .font(RibbonType.scripture(size))
        }
    }

    /// One version's words for the selection, from its text and links as
    /// this phone holds them; nil when its text is not here.
    private func says(in version: TranslationID) -> [OriginalSaying]? {
        if version == translation {
            return selection.says(texts: pageTexts, breaks: pageBreaks, links: nil, exact: true)
        }
        guard let chapter = chapter(in: version) else { return nil }
        let links = model.original.links(
            version, bookID: selection.bookID, chapter: selection.chapter, readerChapter: chapter)
        return selection.says(
            texts: chapter.ownTexts(), breaks: chapter.ownSpanBreaks(), links: links, exact: false)
    }

    /// Another version's chapter: a bundled one always, a licensed one once
    /// it has streamed here.
    private func chapter(in version: TranslationID) -> ScriptureChapter? {
        if TranslationRegistry.isBundled(version) {
            return model.scripture.book(selection.bookID, translation: version)?.chapter(selection.chapter)
        }
        return streamed[version].flatMap { $0.n == selection.chapter ? $0 : nil }
    }

    private func versionName(_ version: TranslationID) -> String {
        TranslationRegistry.translation(for: version)?.displayName ?? version.rawValue.uppercased()
    }

    // MARK: Licensed versions

    private var licensedWanted: [Translation] {
        var seen: Set<TranslationID> = [translation]
        return roomPeople
            .map(\.version)
            .filter { seen.insert($0).inserted }
            .compactMap { TranslationRegistry.translation(for: $0) }
            .filter { !$0.isBundled && $0.isConfigured }
    }

    private var streamKey: String {
        licensedWanted.map(\.id.rawValue).joined(separator: ",") + "/\(selection.bookID)/\(selection.chapter)"
    }

    /// The licensed versions others here read: from what this phone already
    /// holds, or through the same path the page streams its own by, when
    /// there is a connection. What will not come says so.
    private func stream() async {
        let address = VerseAddress(bookID: selection.bookID, chapter: selection.chapter, verse: 1)
        for licensed in licensedWanted where streamed[licensed.id]?.n != selection.chapter {
            if let held = model.scripture.cachedRemoteChapter(address, translation: licensed) {
                streamed[licensed.id] = held
                continue
            }
            guard model.isOnline else { continue }
            if let chapter = await model.scripture.ensureRemoteChapter(address, translation: licensed) {
                guard !Task.isCancelled else { return }
                withAnimation(RibbonMotion.arrive) { streamed[licensed.id] = chapter }
            }
        }
    }
}

/// A few faces, overlapping, first on top (A62): the app's portrait — a
/// monogram in the person's ink where there is no picture — each cut from
/// the one under it by a ring of the panel's own paper and a hairline. Not
/// a shadow, not glass. The row's names are beside it, not in it.
struct RoomFaces: View {
    @Environment(AppModel.self) private var model
    let readers: [RoomReader]
    let room: Room

    static let face: CGFloat = 30
    static let step: CGFloat = 21

    var body: some View {
        ZStack(alignment: .leading) {
            ForEach(Array(readers.enumerated()), id: \.element.id) { index, reader in
                PortraitView(
                    person: model.person(reader.id),
                    ink: model.membership(of: reader.id, in: room.id)?.ink,
                    size: Self.face - 4,
                    image: model.portrait(reader.id))
                .overlay(Circle().strokeBorder(Palette.rule, lineWidth: 1))
                .padding(2)
                .background(Circle().fill(Palette.surface))
                .offset(x: Self.step * CGFloat(index))
                .zIndex(Double(readers.count - index))
            }
        }
        .frame(
            width: Self.face + Self.step * CGFloat(max(0, readers.count - 1)),
            height: Self.face,
            alignment: .leading)
        .accessibilityHidden(true)
    }
}

/// Word columns set like words: as many to a row as fit, the next row under
/// it. Right to left for Hebrew and Aramaic, decided here rather than by
/// the environment, so the order does not depend on how the system mirrors
/// a custom layout.
struct WordFlow: Layout {
    var rightToLeft = false
    var spacing: CGFloat = 2
    var lineSpacing: CGFloat = 8
    /// The widest a column is set before its words wrap — a phrase a
    /// version says for one word can be long.
    var widest: CGFloat = 150

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let rows = rows(of: subviews, in: width)
        let height = rows.reduce(0) { $0 + $1.height } + lineSpacing * CGFloat(max(0, rows.count - 1))
        let used = rows.map(\.width).max() ?? 0
        return CGSize(width: width.isFinite ? width : used, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(of: subviews, in: bounds.width) {
            var x = rightToLeft ? bounds.maxX : bounds.minX
            for item in row.items {
                if rightToLeft { x -= item.size.width }
                subviews[item.index].place(
                    at: CGPoint(x: x, y: y), anchor: .topLeading, proposal: ProposedViewSize(item.size))
                x += rightToLeft ? -spacing : item.size.width + spacing
            }
            y += row.height + lineSpacing
        }
    }

    private struct Row {
        var items: [(index: Int, size: CGSize)] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func rows(of subviews: Subviews, in width: CGFloat) -> [Row] {
        let cap = width.isFinite ? min(widest, width) : widest
        var rows: [Row] = []
        var row = Row()
        for index in subviews.indices {
            var size = subviews[index].sizeThatFits(.unspecified)
            if size.width > cap {
                size = subviews[index].sizeThatFits(ProposedViewSize(width: cap, height: nil))
            }
            let needed = row.items.isEmpty ? size.width : row.width + spacing + size.width
            if !row.items.isEmpty, needed > width {
                rows.append(row)
                row = Row()
            }
            row.width = row.items.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.items.append((index: index, size: size))
        }
        if !row.items.isEmpty { rows.append(row) }
        return rows
    }
}
