import SwiftUI
import RibbonCore

// S13 — the book chooser: starting a fire. Deliberately the most
// conventional screen in the app; this is navigation, not atmosphere. The
// drawn fire is the only length indicator — no word counts, no chapter
// counts, no reading-time estimates ("about 14 hours" is a commitment a
// person can fail).

/// The chooser's content — the sheet over the room, and the last step of
/// the thread (S17), are the same screen.
struct BookChooserContent: View {
    @Environment(\.appModel) private var model
    let room: Room
    /// The heading, when the chooser is a step rather than a sheet.
    var heading: String?
    /// The book, and where in it when a search hit named a verse (S23).
    var onChoose: (String, VerseAddress?) -> Void
    /// The way out, where a sheet needs one on a canvas with no swipe.
    var onClose: (() -> Void)?

    @State private var query = ""
    @State private var hits: [ScriptureStore.SearchHit] = []
    @State private var searched = ""
    @FocusState private var searchFocused: Bool

    private var translation: TranslationID { model.me?.translation ?? .bsb }
    private var onShelf: Set<String> { Set(model.shelf(of: room).map(\.bookID)) }
    private var open: Reading? { model.openReading(in: room) }
    private var setAside: [Reading] { model.setAsideReadings(in: room) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                HStack(alignment: .firstTextBaseline) {
                    if let heading {
                        Text(heading)
                            .font(RibbonType.display(22))
                            .foregroundStyle(Palette.text)
                            .accessibilityAddTraits(.isHeader)
                    }
                    Spacer()
                    if let onClose {
                        BackControl(title: Copy.close, action: onClose)
                    }
                }
                .padding(.top, heading == nil ? 8 : 20)

                searchField

                if let open, let book = Bible.book(id: open.bookID) {
                    // A book still going is set aside by picking another —
                    // said once, plainly, and never as a lapse (§6.6).
                    Text(Copy.setAsideLine(book.name))
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if query.trimmingCharacters(in: .whitespaces).count >= 2 {
                    searchResults
                } else {
                    if !setAside.isEmpty { setAsideRows }
                    starterRow
                    allBooks
                }
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 40)
            .readableColumn()
        }
        .scrollIndicators(.hidden)
        .scrollDismissesKeyboard(.interactively)
        .room()
        // Search runs off the render path, a beat after typing stops
        // (§08 loading: nothing visibly waits, and the list never stalls).
        .task(id: query) {
            let trimmed = query.trimmingCharacters(in: .whitespaces)
            guard trimmed.count >= 2 else {
                hits = []
                searched = ""
                return
            }
            try? await Task.sleep(for: .milliseconds(220))
            guard !Task.isCancelled else { return }
            let store = model.scripture
            let translation = translation
            let found = await Task.detached(priority: .userInitiated) {
                store.search(trimmed, translation: translation)
            }.value
            guard !Task.isCancelled else { return }
            hits = found
            searched = trimmed
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            RibbonTextField(prompt: Copy.search, text: $query, size: 16)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .focused($searchFocused)
            if !query.isEmpty {
                BackControl(title: Copy.clear) {
                    query = ""
                    searchFocused = false
                }
            }
        }
    }

    private var setAsideRows: some View {
        VStack(alignment: .leading, spacing: 4) {
            SectionHeader(Copy.stillGoing)
            ForEach(setAside) { reading in
                if let book = Bible.book(id: reading.bookID) {
                    bookRow(book, tag: Copy.setAside)
                }
            }
        }
    }

    private var starterRow: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(Copy.goodPlacesToStart)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .bottom, spacing: 12) {
                    ForEach(Bible.goodPlacesToStart, id: \.self) { id in
                        if let book = Bible.book(id: id) {
                            starterCard(book)
                        }
                    }
                }
            }
        }
    }

    private func starterCard(_ book: BibleBook) -> some View {
        Button {
            onChoose(book.id, nil)
        } label: {
            VStack(spacing: 8) {
                ZStack(alignment: .bottomTrailing) {
                    CampfireGlyph(state: .steady, scale: book.scale, height: 26)
                    if onShelf.contains(book.id) {
                        EmberGlyph()
                            .offset(x: 6, y: 2)
                    }
                }
                Text(book.name)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .frame(minWidth: 104, minHeight: 100)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(bookLabel(book))
    }

    private var allBooks: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(Bible.sections, id: \.section) { section, books in
                VStack(alignment: .leading, spacing: 0) {
                    SectionHeader(section.rawValue)
                    ForEach(books) { book in
                        bookRow(book, tag: nil)
                    }
                }
            }
        }
    }

    private func bookRow(_ book: BibleBook, tag: String?) -> some View {
        Button {
            // Already on the shelf: choosing it again starts a fresh fire
            // and a second ember (S13). Set aside: it resumes.
            onChoose(book.id, nil)
        } label: {
            HStack(spacing: 12) {
                Text(book.name)
                    .font(RibbonType.ui(16))
                    .foregroundStyle(Palette.text)
                if onShelf.contains(book.id) {
                    EmberGlyph()
                }
                if let tag {
                    SmallCaps(tag, size: 11)
                }
                Spacer()
                CampfireGlyph(state: .steady, scale: book.scale, height: 18)
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(bookLabel(book) + (tag.map { ", \($0)" } ?? ""))
    }

    /// The label says what the drawn fire says — the book, and whether it
    /// is on the shelf. Never a size word that reads as a measure.
    private func bookLabel(_ book: BibleBook) -> String {
        book.name + (onShelf.contains(book.id) ? ", \(Copy.onTheShelf)" : "")
    }

    @ViewBuilder
    private var searchResults: some View {
        if searched.isEmpty {
            // The beat before results: the list stays (S13).
            allBooks
        } else if hits.isEmpty {
            VStack(alignment: .leading, spacing: 18) {
                Text(Copy.nothingMatches)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                // The list stays visible beneath (S13).
                allBooks
            }
        } else {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(Array(hits.enumerated()), id: \.offset) { _, hit in
                    switch hit {
                    case .reference(let address), .verse(let address, _):
                        Button {
                            onChoose(address.bookID, address)
                        } label: {
                            VStack(alignment: .leading, spacing: 3) {
                                SmallCaps(address.formatted, size: 12, color: Palette.text.opacity(0.8))
                                if case .verse(_, let text) = hit {
                                    Text(text)
                                        .font(RibbonType.scripture(14))
                                        .foregroundStyle(Palette.muted)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.leading)
                                }
                            }
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(address.formatted)
                    case .book(let id):
                        if let book = Bible.book(id: id) {
                            bookRow(book, tag: nil)
                        }
                    }
                }
            }
        }
    }
}

/// The small ember beside a book already on the shelf (S13).
private struct EmberGlyph: View {
    var body: some View {
        Circle()
            .fill(
                RadialGradient(
                    colors: [Palette.flameCore.opacity(0.95), Palette.coal, Palette.coalDim],
                    center: .init(x: 0.4, y: 0.4), startRadius: 0, endRadius: 6))
            .frame(width: 10, height: 10)
            .accessibilityHidden(true)
    }
}

/// The chooser as a sheet over the room.
struct BookChooserSheet: View {
    @Environment(\.dismiss) private var dismiss
    let room: Room
    var onChoose: (String, VerseAddress?) -> Void

    var body: some View {
        BookChooserContent(room: room, heading: nil, onChoose: onChoose, onClose: { dismiss() })
            .presentationBackground(Palette.ground)
    }
}
