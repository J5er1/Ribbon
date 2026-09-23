import SwiftUI
import RibbonCore

// S13 — the book chooser: starting a fire. Deliberately the most
// conventional screen in the app; this is navigation, not atmosphere. The
// drawn fire is the only length indicator — no word counts, no chapter
// counts, no reading-time estimates ("about 14 hours" is a commitment a
// person can fail).

struct BookChooserSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let room: Room
    var onChoose: (String) -> Void

    @State private var query = ""
    /// What the last finished search found. Held rather than computed:
    /// searching reads every book on the phone, which is not something a
    /// view body may do (see `searchResults`).
    @State private var hits: [ScriptureStore.SearchHit] = []
    /// A search is out. The empty state waits for it, so that typing does
    /// not flash "Nothing matches" on the way to the hits.
    @State private var searching = false

    private var translation: TranslationID { model.words(room: room, reading: model.openReading(in: room)) }
    private var trimmedQuery: String { query.trimmingCharacters(in: .whitespaces) }
    /// Two characters is where the chooser turns into a search (S13).
    private var hasQuery: Bool { trimmedQuery.count >= 2 }
    private var onShelf: Set<String> {
        Set(model.shelf(of: room).map(\.bookID))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    searchField
                        .padding(.top, 16)

                    if hasQuery {
                        searchResults
                    } else {
                        starterRow
                        allBooks
                    }
                }
                .padding(.horizontal, 22)
                .padding(.bottom, 40)
            }
            .scrollIndicators(.hidden)
            .room()
            .toolbarVisibility(.hidden, for: .navigationBar)
        }
        .presentationBackground(Palette.ground)
        // The search runs here rather than in the body, off the main
        // thread, and the id restarts it — which is also how it is
        // cancelled, since the task from the previous keystroke is torn
        // down before this one begins.
        .task(id: "\(translation.rawValue)\u{1}\(trimmedQuery)") {
            guard hasQuery else {
                hits = []
                searching = false
                return
            }
            searching = true
            // A keystroke is not a search: a moment to finish the word,
            // which the next keystroke cancels before any book is opened.
            try? await Task.sleep(for: .milliseconds(200))
            guard !Task.isCancelled else { return }
            let found = await model.scripture.hits(matching: trimmedQuery, translation: translation)
            guard !Task.isCancelled else { return }
            hits = found
            searching = false
        }
    }

    private var searchField: some View {
        CentredTextField(text: $query, prompt: Copy.search, submitLabel: .search, centred: false)
            .autocorrectionDisabled()
    }

    private var starterRow: some View {
        VStack(alignment: .leading, spacing: 12) {
            SmallCaps(Copy.goodPlacesToStart, size: 12)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
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
            onChoose(book.id)
        } label: {
            VStack(spacing: 8) {
                CampfireGlyph(state: .burning, scale: book.scale, height: 30)
                Text(book.name)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
            }
            .frame(width: 108, height: 96)
            .contentShape(Rectangle())
            .tile()
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Copy.bookIsAFire(book.name, scale: book.scale.rawValue))
    }

    private var allBooks: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(Bible.sections, id: \.section) { section, books in
                VStack(alignment: .leading, spacing: 0) {
                    SmallCaps(section.rawValue, size: 12)
                        .padding(.bottom, 4)
                    ForEach(books) { book in
                        bookRow(book)
                    }
                }
            }
        }
    }

    private func bookRow(_ book: BibleBook) -> some View {
        Button {
            // Already on the shelf: choosing it again starts a fresh fire
            // and a second ember (S13).
            onChoose(book.id)
        } label: {
            HStack(spacing: 12) {
                Text(book.name)
                    .font(RibbonType.ui(16))
                    .foregroundStyle(Palette.text)
                Spacer()
                if onShelf.contains(book.id) {
                    EmberView(scale: .small)
                        .scaleEffect(0.5)
                        .frame(width: 26, height: 20)
                }
                CampfireGlyph(state: .burning, scale: book.scale, height: 20)
            }
            // The row answers across its width and a finger's height: it
            // was the name and the fire, with a dead gap between them, 34
            // points tall.
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Copy.bookIsAFire(book.name, scale: book.scale.rawValue, onShelf: onShelf.contains(book.id)))
    }

    @ViewBuilder
    private var searchResults: some View {
        if hits.isEmpty {
            VStack(alignment: .leading, spacing: 18) {
                // Nothing to say yet while the books are still being read:
                // "Nothing matches" is a finding, not a waiting state.
                if !searching {
                    Text(Copy.nothingMatches)
                        .font(RibbonType.ui(15))
                        .foregroundStyle(Palette.muted)
                }
                // The list stays visible beneath (S13).
                allBooks
            }
        } else {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(Array(hits.enumerated()), id: \.offset) { _, hit in
                    switch hit {
                    case .reference(let address), .verse(let address, _):
                        Button {
                            onChoose(address.bookID)
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
                    case .book(let id):
                        if let book = Bible.book(id: id) {
                            bookRow(book)
                        }
                    }
                }
            }
        }
    }
}
