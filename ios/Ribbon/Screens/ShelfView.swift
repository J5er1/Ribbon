import SwiftUI
import RibbonCore

// S10 — the shelf: every book this room has read, as embers on a shared
// baseline with a faint warm bloom beneath. No drawn shelf, no sorting, no
// filtering, no list view — a shelf you can sort is a database.

struct ShelfView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    let room: Room
    let readings: [Reading]
    var namespace: Namespace.ID
    /// Absent while a book is open — a room has one open reading at a
    /// time, and the chooser says so itself (§6.6).
    var onStartAnother: (() -> Void)?

    @State private var exportURL: URL?

    /// Cells fit the largest ember present, so Isaiah looks like Isaiah
    /// and never overlaps Ruth (S10 — embers never shrink below their true
    /// scale).
    private var columns: [GridItem] {
        let regular = sizeClass == .regular
        let widest = readings.map { EmberView.shelfWidth(for: $0.handiwork.scale, regular: regular) }.max() ?? 88
        return [GridItem(.adaptive(minimum: max(88, widest)), spacing: 18, alignment: .bottom)]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                ForEach(readings) { reading in
                    NavigationLink(value: reading.id) {
                        VStack(spacing: 6) {
                            EmberView(scale: reading.handiwork.scale, seed: reading.id)
                            SmallCaps(Bible.book(id: reading.bookID)?.name ?? reading.bookID, size: 12)
                        }
                        .frame(minHeight: 44)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .matchedTransitionSource(id: reading.id, in: namespace)
                    .accessibilityLabel(Copy.emberOf(Bible.book(id: reading.bookID)?.name ?? reading.bookID))
                }
            }
            .padding(.horizontal, 24)

            if readings.count == 1 {
                // The moment the keepsake idea first appears.
                SmallCaps(Copy.oneDayThisIsABook, size: 12)
                    .padding(.horizontal, 24)
            }

            HStack(spacing: 22) {
                if let onStartAnother {
                    QuietControl(title: Copy.startAnother, action: onStartAnother)
                }
                // Export (§13): any member, any time, as a readable file.
                if let exportURL {
                    ShareLink(item: exportURL) {
                        SmallCaps(Copy.exportTheShelf, size: 13, color: Palette.muted)
                            .frame(minHeight: 44)
                            .contentShape(Rectangle().inset(by: -8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Copy.exportTheShelf)
                }
            }
            .padding(.horizontal, 24)
        }
        .onAppear { exportURL = model.exportShelf(of: room) }
        .onChange(of: readings.count) { _, _ in exportURL = model.exportShelf(of: room) }
    }
}

// S11 — an ember: one finished book's complete record. Immutable, and the
// source of the printed keepsake.
struct EmberRecordScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let reading: Reading
    var onOpenVerse: (VerseAddress) -> Void
    var onReadAgain: (String) -> Void

    private var book: BibleBook? { Bible.book(id: reading.bookID) }
    private var notes: [Note] { model.notes(in: reading) }
    private var highlights: [Highlight] { model.highlights(in: reading) }
    private var openCards: [ReflectionCard] { model.cards(in: reading).filter { $0.state == .open } }
    private var room: Room? { model.state.rooms.first { $0.id == reading.roomID } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack {
                    BackControl { dismiss() }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.top, 8)
                VStack(spacing: 10) {
                    // The ember, large — drawn large, not scaled, so it
                    // stays crisp and sits above the name.
                    EmberView(scale: reading.handiwork.scale, seed: reading.id)
                        .scaleEffect(1.5)
                        .padding(.top, 34)
                        .padding(.bottom, 26)
                    Text(book?.name ?? reading.bookID)
                        .font(RibbonType.display(30))
                        .foregroundStyle(Palette.text)
                        .accessibilityAddTraits(.isHeader)
                    SmallCaps(
                        RibbonClock.emberRange(
                            start: reading.startedAt,
                            end: reading.finishedAt ?? reading.startedAt),
                        size: 13)
                    HStack(spacing: 2) {
                        // Who read it, as portraits — departed members
                        // included (S11) — and a portrait goes to its
                        // person (S12).
                        ForEach(model.everyone(in: reading.roomID)) { membership in
                            NavigationLink(value: PersonRoute(personID: membership.personID, roomID: reading.roomID)) {
                                PortraitView(
                                    person: model.person(membership.personID),
                                    ink: model.inkForDisplay(membership.personID, in: reading.roomID),
                                    size: 30,
                                    image: model.portrait(membership.personID))
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.top, 6)
                }
                .frame(maxWidth: .infinity)

                if notes.isEmpty && highlights.isEmpty && openCards.isEmpty {
                    // A book with no notes — possible and not a failure.
                    Text(Copy.straightThrough)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 12)
                }

                if !notes.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(notes) { note in
                            EmberNoteRow(note: note, roomID: reading.roomID, onOpenVerse: onOpenVerse)
                        }
                    }
                    .padding(.horizontal, 24)
                }

                if !highlights.isEmpty {
                    VStack(alignment: .leading, spacing: 14) {
                        ForEach(highlights) { highlight in
                            QuotedHighlight(highlight: highlight, room: room, onOpenVerse: onOpenVerse)
                        }
                    }
                    .padding(.horizontal, 24)
                }

                if !openCards.isEmpty {
                    VStack(alignment: .leading, spacing: 18) {
                        ForEach(openCards) { card in
                            OpenCardView(card: card, roomID: reading.roomID, animateTurn: false)
                        }
                    }
                    .padding(.horizontal, 24)
                }

                VStack(spacing: 14) {
                    if let room, !room.isDeparted, !room.isPaused {
                        QuietControl(title: Copy.readItAgain) {
                            onReadAgain(reading.bookID)
                        }
                    }
                    // "Make this a book" arrives with the printed keepsake
                    // (§15 horizon).
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 30)
            }
            .readableColumn()
        }
        .scrollIndicators(.hidden)
        .room()
    }
}

private struct EmberNoteRow: View {
    @Environment(AppModel.self) private var model
    let note: Note
    let roomID: UUID
    var onOpenVerse: (VerseAddress) -> Void

    @State private var open = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                withAnimation(RibbonMotion.settle) { open.toggle() }
            } label: {
                HStack(spacing: 10) {
                    NoteMark(
                        kind: note.kind,
                        ink: model.inkForDisplay(note.authorID, in: roomID),
                        found: true, mine: note.authorID == model.me?.id, pending: false)
                    SmallCaps(note.verse.formatted, size: 12, color: Palette.text.opacity(0.8))
                    Spacer()
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(
                "\(note.kind == .voice ? Copy.voiceNoteKind : Copy.writtenNoteKind), \(note.verse.formatted), \(model.person(note.authorID)?.name ?? "")")
            if open {
                // The record is immutable: no edit, no take back here.
                NoteCard(
                    note: note,
                    author: model.person(note.authorID),
                    authorInk: model.inkForDisplay(note.authorID, in: roomID),
                    onTakeBack: nil, onEdit: nil)
                .padding(.leading, 16)
                HStack(spacing: 18) {
                    // Sharing: plain text only, and only your own words or
                    // the verse itself — never someone else's note (S11).
                    if note.authorID == model.me?.id, let text = note.body ?? note.transcript {
                        ShareLink(item: "\(note.verse.formatted) — \(text)") {
                            SmallCaps(Copy.share, size: 11)
                                .frame(minHeight: 44)
                        }
                        .accessibilityLabel(Copy.share)
                    }
                    if let me = model.me,
                       let verse = model.scripture.verseText(note.verse, translation: me.translation) {
                        ShareLink(item: "\(verse) — \(note.verse.formatted)") {
                            SmallCaps(Copy.shareTheVerse, size: 11)
                                .frame(minHeight: 44)
                        }
                        .accessibilityLabel(Copy.shareTheVerse)
                    }
                }
                .padding(.leading, 16)
            }
        }
    }
}

private struct QuotedHighlight: View {
    @Environment(AppModel.self) private var model
    let highlight: Highlight
    let room: Room?
    var onOpenVerse: (VerseAddress) -> Void

    var body: some View {
        Button {
            onOpenVerse(highlight.range.start)
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                if let me = model.me,
                   let text = model.scripture.rangeText(highlight.range, translation: me.translation) {
                    Text(text)
                        .font(RibbonType.scripture(15))
                        .foregroundStyle(Palette.text)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(10)
                        .background(highlight.ink.color.opacity(Palette.highlightWash), in: RoundedRectangle(cornerRadius: 6))
                }
                HStack(spacing: 10) {
                    SmallCaps(highlight.range.formatted, size: 11)
                    if let room, model.isFromWhenTheRoomWasTwo(highlight, in: room) {
                        SmallCaps(Copy.inksFromWhenTheRoomWasTwo, size: 11, color: Palette.muted.opacity(0.7))
                    }
                }
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(highlight.range.formatted), \(model.person(highlight.authorID)?.name ?? "")")
    }
}
