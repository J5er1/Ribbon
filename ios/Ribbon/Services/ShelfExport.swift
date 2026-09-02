import Foundation
import RibbonCore

// Export (§13): any member can export their room's whole shelf at any time,
// as a readable file, without asking anyone. Closing a room offers it
// first (§6.8). Plain Markdown: the books, their date ranges, every note in
// verse order with who left it, every highlight as its quoted verse, every
// card the room opened. Nothing here is a count of anyone.

struct ShelfExport {
    struct Person {
        var name: String
        var translation: TranslationID
    }

    var roomName: String
    var readings: [Reading]
    var notes: [Note]
    var highlights: [Highlight]
    var cards: [ReflectionCard]
    var people: [UUID: Person]
    /// The text of a verse in a translation, or nil.
    var verseText: (VerseAddress, TranslationID) -> String?

    func markdown(now: Date = Date()) -> String {
        var lines: [String] = []
        lines.append("# \(roomName)")
        lines.append("")
        lines.append("The shelf, as of \(RibbonClock.emberRange(start: now, end: now)).")
        lines.append("")

        let ordered = readings.sorted {
            ($0.finishedAt ?? $0.startedAt) < ($1.finishedAt ?? $1.startedAt)
        }
        for reading in ordered {
            let book = Bible.book(id: reading.bookID)
            let name = book?.name ?? reading.bookID
            lines.append("## \(name)")
            let range = RibbonClock.emberRange(
                start: reading.startedAt, end: reading.finishedAt ?? now)
            lines.append(reading.isFinished ? range : "\(range) — still going")
            lines.append("")

            let bookNotes = notes.filter { $0.readingID == reading.id }.sorted { $0.verse < $1.verse }
            if !bookNotes.isEmpty {
                lines.append("### Notes left")
                for note in bookNotes {
                    let who = people[note.authorID]?.name ?? "Someone"
                    let body: String
                    switch note.kind {
                    case .written: body = note.body ?? ""
                    case .voice: body = note.transcript.map { "(voice) \($0)" } ?? "(voice note)"
                    }
                    lines.append("- **\(note.verse.formatted)** — \(who): \(body)")
                }
                lines.append("")
            }

            let bookHighlights = highlights.filter { $0.readingID == reading.id }
                .sorted { $0.range.start < $1.range.start }
            if !bookHighlights.isEmpty {
                lines.append("### Highlights")
                for highlight in bookHighlights {
                    let who = people[highlight.authorID]
                    let translation = who?.translation ?? .bsb
                    let text = highlight.range.verses.compactMap { verse in
                        verseText(VerseAddress(bookID: highlight.range.bookID,
                                               chapter: highlight.range.chapter, verse: verse), translation)
                    }.joined(separator: " ")
                    lines.append("- **\(highlight.range.formatted)** (\(who?.name ?? "someone"), \(highlight.ink.displayName)): \(text)")
                }
                lines.append("")
            }

            let openCards = cards.filter { $0.readingID == reading.id && $0.state == .open }
                .sorted { $0.chapter < $1.chapter }
            if !openCards.isEmpty {
                lines.append("### Cards")
                for card in openCards {
                    lines.append("**\(book?.chapterHeading(card.chapter) ?? "\(card.chapter)")** — \(card.question)")
                    for (personID, answer) in card.answers.sorted(by: { ($0.value, $0.key.uuidString) < ($1.value, $1.key.uuidString) }) {
                        lines.append("- \(people[personID]?.name ?? "Someone"): \(answer)")
                    }
                    lines.append("")
                }
            }
        }
        return lines.joined(separator: "\n")
    }

    /// Writes the export beside the state, for the share sheet.
    func writeFile(to directory: URL) throws -> URL {
        let safeName = roomName.replacingOccurrences(of: "/", with: "-")
        let url = directory.appendingPathComponent("\(safeName) — shelf.md")
        try markdown().data(using: .utf8)?.write(to: url, options: .atomic)
        return url
    }
}
