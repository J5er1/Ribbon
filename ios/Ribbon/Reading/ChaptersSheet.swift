import SwiftUI
import RibbonCore

// The chapter list (ledger A31): a sheet on the ground with the book's
// name, the ribbon if it is somewhere worth going, and the chapters as a
// grid of numbers. The one you are in is a tile of paper, and its number
// alone is in the full ink; the rest are bare, their numbers quiet, as
// Android has always set them (I44) — paper on the ground is a faint edge
// on its own. Every number is in lining figures, so the grid sits level
// (A74). Where the ribbon is, a hairline in the accent at the tile's foot.
// Addresses, never scores: nothing here says how far along anybody is.

struct ChaptersSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let reading: Reading
    let currentChapter: Int
    var onGo: (Int) -> Void

    private let columns = [GridItem(.adaptive(minimum: 56), spacing: 8)]

    var body: some View {
        let book = Bible.book(id: reading.bookID)
        let ribbon = model.ribbonWorthOffering(in: reading)
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text(book?.name ?? reading.bookID)
                    .font(RibbonType.display(28))
                    .foregroundStyle(Palette.text)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.top, 28)

                if let ribbon, let book {
                    let mine = model.me?.id == ribbon.personID
                    let reference = "\(book.chapterHeading(ribbon.chapter)):\(ribbon.verse)"
                    Button {
                        onGo(ribbon.chapter)
                    } label: {
                        HStack(spacing: 12) {
                            Text(mine
                                ? Copy.youLeftTheRibbonAt(reference)
                                : Copy.ribbonIsAt(model.person(ribbon.personID).map { firstName($0.name) }, reference))
                                .font(RibbonType.ui(15))
                                .foregroundStyle(Palette.text)
                                .multilineTextAlignment(.leading)
                            Spacer()
                            SmallCaps(Copy.goThere, size: 12, color: Palette.chartreuse)
                        }
                        .padding(.horizontal, RibbonShape.textInset)
                        .padding(.vertical, 12)
                        .frame(minHeight: RibbonShape.rowHeight)
                        .contentShape(Rectangle())
                        .paper(.row)
                    }
                    .buttonStyle(.pressable)
                }

                SectionLabel(Copy.chapters)

                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(1...(book?.chapterCount ?? 1), id: \.self) { n in
                        ChapterTile(
                            number: n,
                            heading: book?.chapterHeading(n) ?? "\(n)",
                            here: n == currentChapter,
                            ribbon: ribbon?.chapter == n
                        ) { onGo(n) }
                    }
                }
            }
            .padding(.horizontal, RibbonShape.screenMargin)
            .padding(.bottom, 40)
            .readableColumn()
        }
        .scrollIndicators(.hidden)
        .room()
        .presentationBackground(Palette.ground)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.hidden)
    }
}

private struct ChapterTile: View {
    let number: Int
    let heading: String
    let here: Bool
    let ribbon: Bool
    var action: () -> Void
    /// Read so that a type-size change sets the figures again: their face
    /// is UIKit's, scaled when it is made (A74).
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        Button(action: action) {
            if here {
                face.paper(.small)
            } else {
                // Bare: the paper is only under the chapter you are in.
                face
            }
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(label)
        .accessibilityAddTraits(here ? [.isSelected] : [])
    }

    private var face: some View {
        VStack(spacing: 0) {
            // The figures level and in step (A74); the chapter you are in
            // in the full ink and the rest quiet, so where you are is said
            // twice, by the number as well as by its tile (I44).
            Text(String(number))
                .font(RibbonType.figures(17, at: dynamicTypeSize))
                .foregroundStyle(here ? Palette.text : Palette.muted)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
            // The ribbon: a hairline in the accent at the tile's foot.
            Rectangle()
                .fill(ribbon ? Palette.chartreuse : .clear)
                .frame(width: 16, height: 2)
                .padding(.bottom, 6)
        }
        .frame(minHeight: 56)
        .contentShape(Rectangle())
    }

    private var label: String {
        switch (here, ribbon) {
        case (true, true): return Copy.chapterYouAreHereWithRibbon(heading)
        case (true, false): return Copy.chapterYouAreHere(heading)
        case (false, true): return Copy.chapterHasRibbon(heading)
        case (false, false): return heading
        }
    }
}
