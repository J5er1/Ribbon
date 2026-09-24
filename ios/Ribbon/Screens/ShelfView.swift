import SwiftUI
import RibbonCore

// S10 — the shelf: every book this room has read, as embers on a shared
// baseline with a faint warm bloom beneath. No drawn shelf, no sorting, no
// filtering, no list view — a shelf you can sort is a database.

struct ShelfView: View {
    @Environment(AppModel.self) private var model
    let room: Room
    let readings: [Reading]
    var onStartAnother: () -> Void
    /// A note found, opened where it was left — in its own book (S23).
    var onOpenNote: (Reading, VerseAddress) -> Void

    @State private var query = ""
    @FocusState private var searchFocused: Bool

    private let columns = [GridItem(.adaptive(minimum: 88), spacing: 18, alignment: .bottom)]

    private var trimmedQuery: String { query.trimmingCharacters(in: .whitespaces) }
    /// Two characters is where a search begins (S13, S23).
    private var searching: Bool { trimmedQuery.count >= 2 }

    var body: some View {
        let found = searching ? model.notes(in: room, matching: query) : []
        VStack(alignment: .leading, spacing: 20) {
            // Note search lives here, where what it searches lives (S23): not
            // a bar across the front door, but a field on the room's memory.
            CentredTextField(
                text: $query, prompt: Copy.findSomethingSaid, submitLabel: .search,
                centred: false, focus: $searchFocused)
                .autocorrectionDisabled()
                .padding(.horizontal, 24)

            if !found.isEmpty {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(found) { note in
                        foundRow(note)
                    }
                }
                .padding(.horizontal, 24)
                .transition(.opacity)
            } else {
                // Finding nothing says so over the shelf, which stays
                // beneath, as the chooser's list does (S13).
                if searching {
                    Text(Copy.nothingMatches)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                        .padding(.horizontal, 24)
                        .transition(.opacity)
                }
                shelf
                    .transition(.opacity)
            }
        }
        .animation(RibbonMotion.arrive, value: searching)
        .animation(RibbonMotion.arrive, value: found.map(\.id))
    }

    /// One note found: where it is and whose, then its words around what was
    /// searched for.
    private func foundRow(_ note: Note) -> some View {
        let said = note.body ?? note.transcript ?? ""
        let name = note.authorID == model.me?.id ? Copy.you : firstName(model.person(note.authorID)?.name ?? "")
        return Button {
            // The keyboard goes with the room: the book comes up over it.
            searchFocused = false
            if let reading = model.state.readings.first(where: { $0.id == note.readingID }) {
                onOpenNote(reading, note.verse)
            }
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                SmallCaps("\(note.verse.formatted) · \(name)", size: 12, color: Palette.text.opacity(0.8))
                Text(excerpt(said, around: trimmedQuery))
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Copy.noteFound(note.verse.formatted, by: name) + ". " + said)
        .accessibilityAddTraits(.isButton)
    }

    /// The words around the match, so one late in a long note is still on
    /// the two lines a row has: from the start of a word a little before it.
    private func excerpt(_ said: String, around query: String) -> String {
        guard let match = said.range(of: query, options: [.caseInsensitive, .diacriticInsensitive]),
              said.distance(from: said.startIndex, to: match.lowerBound) > 48
        else { return said }
        var start = said.index(match.lowerBound, offsetBy: -40)
        while start > said.startIndex, !said[said.index(before: start)].isWhitespace {
            start = said.index(before: start)
        }
        return "…" + said[start...]
    }

    /// The embers, the keepsake line and the way to another book.
    private var shelf: some View {
        VStack(alignment: .leading, spacing: 20) {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                ForEach(readings) { reading in
                    NavigationLink(value: reading.id) {
                        VStack(spacing: 6) {
                            EmberView(scale: reading.handiwork.scale)
                            SmallCaps(Bible.book(id: reading.bookID)?.name ?? reading.bookID, size: 12)
                        }
                        .contentShape(Rectangle())
                    }
                    // An ember takes a press the way a tile does.
                    .buttonStyle(.pressable)
                }
            }
            .padding(.horizontal, 24)

            if readings.count == 1 {
                // The moment the keepsake idea first appears.
                SmallCaps(Copy.oneDayThisIsABook, size: 12)
                    .padding(.horizontal, 24)
            }

            QuietControl(title: Copy.startAnother, action: onStartAnother)
                .padding(.horizontal, 24)
        }
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

    var body: some View {
        VStack(spacing: 0) {
            // The way back, pinned, in the room's own drawn chevron — the one
            // pushed screen that still wore the system's bar and its glyph,
            // where every other page the room pushes has this (A29).
            HStack(spacing: 0) {
                BackChevron { dismiss() }
                Spacer()
            }
            .padding(.horizontal, 8)
            .frame(height: 52)

            record
        }
        .room()
        .toolbar(.hidden, for: .navigationBar)
    }

    private var record: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(spacing: 10) {
                    EmberView(scale: reading.handiwork.scale)
                        .scaleEffect(1.6)
                        .padding(.top, 30)
                        .padding(.bottom, 16)
                    Text(book?.name ?? reading.bookID)
                        .font(RibbonType.display(30))
                        .foregroundStyle(Palette.text)
                    SmallCaps(
                        RibbonClock.emberRange(
                            start: reading.startedAt,
                            end: reading.finishedAt ?? reading.startedAt),
                        size: 13)
                    HStack(spacing: -6) {
                        // Who read it, as portraits — and a portrait goes
                        // to its person (S12).
                        ForEach(model.members(of: Room(id: reading.roomID, createdAt: .now))) { membership in
                            NavigationLink(value: PersonRoute(personID: membership.personID, roomID: reading.roomID)) {
                                PortraitView(
                                    person: model.person(membership.personID),
                                    ink: membership.ink,
                                    size: 30,
                                    image: model.portrait(membership.personID))
                            }
                            .buttonStyle(.pressable)
                        }
                    }
                    .padding(.top, 6)
                }
                .frame(maxWidth: .infinity)

                if notes.isEmpty && highlights.isEmpty {
                    // A book with no notes — possible and not a failure.
                    Text(Copy.straightThrough)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 12)
                }

                if !notes.isEmpty {
                    VStack(alignment: .leading, spacing: 16) {
                        ForEach(notes) { note in
                            EmberNoteRow(note: note, roomID: reading.roomID, onOpenVerse: onOpenVerse)
                        }
                    }
                    .padding(.horizontal, 24)
                }

                if !highlights.isEmpty {
                    VStack(alignment: .leading, spacing: 14) {
                        ForEach(highlights) { highlight in
                            QuotedHighlight(highlight: highlight, onOpenVerse: onOpenVerse)
                        }
                    }
                    .padding(.horizontal, 24)
                }

                VStack(spacing: 14) {
                    QuietControl(title: Copy.readItAgain) {
                        onReadAgain(reading.bookID)
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
                        ink: model.membership(of: note.authorID, in: roomID)?.ink ?? .clay,
                        found: true, mine: note.authorID == model.me?.id, pending: false)
                    SmallCaps(note.verse.formatted, size: 12, color: Palette.text.opacity(0.8))
                    Spacer()
                }
                // The whole row answers, a finger's height of it: only the
                // mark and the verse's letters did, and the space between
                // them — most of the row — did nothing.
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if open {
                NoteCard(
                    note: note,
                    author: model.person(note.authorID),
                    authorInk: model.membership(of: note.authorID, in: roomID)?.ink ?? .clay,
                    onTakeBack: {}, onEdit: {})
                .padding(.leading, 16)
                // Sharing: plain text only, and only your own words or the
                // verse itself — never someone else's note (S11).
                if note.authorID == model.me?.id, let body = note.body {
                    ShareLink(item: Copy.sharedNote(note.verse.formatted, body)) {
                        SmallCaps(Copy.share, size: 11)
                    }
                    .padding(.leading, 16)
                }
            }
        }
    }
}

private struct QuotedHighlight: View {
    @Environment(AppModel.self) private var model
    let highlight: Highlight
    var onOpenVerse: (VerseAddress) -> Void

    var body: some View {
        Button {
            onOpenVerse(highlight.range.start)
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                if let reading = model.state.readings.first(where: { $0.id == highlight.readingID }),
                   let text = model.scripture.verseText(highlight.range.start, translation: model.words(room: model.room(of: reading), reading: reading)) {
                    Text(text)
                        .font(RibbonType.scripture(15))
                        .foregroundStyle(Palette.text)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(10)
                        .background(highlight.ink.color.opacity(Palette.highlightWash), in: RoundedRectangle(cornerRadius: 6))
                }
                SmallCaps(highlight.range.formatted, size: 11)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
