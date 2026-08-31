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

    private let columns = [GridItem(.adaptive(minimum: 88), spacing: 18, alignment: .bottom)]

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 26) {
                ForEach(readings) { reading in
                    NavigationLink(value: reading.id) {
                        VStack(spacing: 6) {
                            EmberView(scale: reading.handiwork.scale)
                            SmallCaps(Bible.book(id: reading.bookID)?.name ?? reading.bookID, size: 12)
                        }
                    }
                    .buttonStyle(.plain)
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
    let reading: Reading
    var onOpenVerse: (VerseAddress) -> Void
    var onReadAgain: (String) -> Void

    private var book: BibleBook? { Bible.book(id: reading.bookID) }
    private var notes: [Note] { model.notes(in: reading) }
    private var highlights: [Highlight] { model.highlights(in: reading) }

    var body: some View {
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
                        ForEach(model.members(of: Room(id: reading.roomID, createdAt: .now))) { membership in
                            PortraitView(
                                person: model.person(membership.personID),
                                ink: membership.ink,
                                size: 30,
                                image: model.portrait(membership.personID))
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
        }
        .scrollIndicators(.hidden)
        .room()
        .toolbarVisibility(.hidden, for: .navigationBar)
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
                    ShareLink(item: "\(note.verse.formatted) — \(body)") {
                        SmallCaps("share", size: 11)
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
                if let me = model.me,
                   let text = model.scripture.verseText(highlight.range.start, translation: me.translation) {
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
        }
        .buttonStyle(.plain)
    }
}
