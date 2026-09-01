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

    private var translation: TranslationID { model.me?.translation ?? .bsb }
    private var onShelf: Set<String> {
        Set(model.shelf(of: room).map(\.bookID))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    searchField
                        .padding(.top, 16)

                    if query.trimmingCharacters(in: .whitespaces).count >= 2 {
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
    }

    private var searchField: some View {
        TextField(Copy.search, text: $query)
            .font(RibbonType.ui(16))
            .foregroundStyle(Palette.text)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Palette.rule, lineWidth: 1))
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
                // 30 is what a large book draws at; a shorter one draws
                // shorter inside the same box, so the names stay in line.
                CampfireGlyph(state: .burning, scale: book.scale, height: 30)
                Text(book.name)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.text)
            }
            .frame(width: 104, height: 92)
            .background(Palette.surface, in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Palette.rule, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(book.name), a \(book.scale.rawValue) fire")
    }

    private var allBooks: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(Bible.sections, id: \.section) { section, books in
                VStack(alignment: .leading, spacing: 4) {
                    SmallCaps(section.rawValue, size: 12)
                        .padding(.bottom, 6)
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
                // The fire is the whole of what this row says about
                // length, so it has to be drawn at the book's scale.
                CampfireGlyph(state: .burning, scale: book.scale, height: 20)
            }
            .padding(.vertical, 7)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(
            "\(book.name), a \(book.scale.rawValue) fire" +
            (onShelf.contains(book.id) ? ", on your shelf" : ""))
    }

    @ViewBuilder
    private var searchResults: some View {
        let hits = model.scripture.search(query, translation: translation)
        if hits.isEmpty {
            VStack(alignment: .leading, spacing: 18) {
                Text(Copy.nothingMatches)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
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
