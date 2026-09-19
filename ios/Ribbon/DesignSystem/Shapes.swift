import SwiftUI

// The shapes the room is built from (build book §12.1, ledger A20/A23).
//
// One family of radii, nested: a row inside a group takes the group's
// corner on the outside and the small one on the inside, so a stack of
// tiles reads as one object with seams in it rather than a list of pills.
// The same numbers on Android (`Shapes.kt`), so a tile is the same tile on
// either phone.

enum RibbonShape {
    static let small: CGFloat = 12
    static let row: CGFloat = 16
    static let card: CGFloat = 22
    static let group: CGFloat = 28
    static let sheet: CGFloat = 34

    /// The gap between two tiles in a group: a seam, not a gutter.
    static let seam: CGFloat = 2
    /// Where text starts inside a tile.
    static let textInset: CGFloat = 20
    /// A row with one line of text, and one with two.
    static let rowHeight: CGFloat = 60
    static let tallRowHeight: CGFloat = 76
    /// The page's own margin.
    static let screenMargin: CGFloat = 20

    /// How much a tile gives under a finger: enough to feel, never enough
    /// to read as a bounce.
    static let pressedScale: CGFloat = 0.975

    /// The corners for row `index` of `count` in a group. The first and last
    /// rows carry the group's corner where they meet the outside; every
    /// corner that meets a seam is the small one. A group of one is a card.
    static func inGroup(_ index: Int, of count: Int) -> TileShape {
        let first = index == 0
        let last = index == count - 1
        return TileShape(
            topLeading: first ? group : small,
            bottomLeading: last ? group : small,
            bottomTrailing: last ? group : small,
            topTrailing: first ? group : small)
    }
}

/// Four corners, as a tile is handed them by its group.
struct TileShape: Hashable {
    var topLeading: CGFloat
    var bottomLeading: CGFloat
    var bottomTrailing: CGFloat
    var topTrailing: CGFloat

    init(topLeading: CGFloat, bottomLeading: CGFloat, bottomTrailing: CGFloat, topTrailing: CGFloat) {
        self.topLeading = topLeading
        self.bottomLeading = bottomLeading
        self.bottomTrailing = bottomTrailing
        self.topTrailing = topTrailing
    }

    /// The same corner all round.
    init(_ radius: CGFloat) {
        self.init(topLeading: radius, bottomLeading: radius, bottomTrailing: radius, topTrailing: radius)
    }

    static let card = TileShape(RibbonShape.card)
    static let row = TileShape(RibbonShape.row)
    static let group = TileShape(RibbonShape.group)
    static let sheet = TileShape(RibbonShape.sheet)
    static let small = TileShape(RibbonShape.small)

    var shape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(
            topLeadingRadius: topLeading,
            bottomLeadingRadius: bottomLeading,
            bottomTrailingRadius: bottomTrailing,
            topTrailingRadius: topTrailing,
            style: .continuous)
    }
}

private struct TileShapeKey: EnvironmentKey {
    static let defaultValue: TileShape = .card
}

extension EnvironmentValues {
    /// The corners a tile draws itself with — set by the group it sits in,
    /// a card on its own.
    var tileShape: TileShape {
        get { self[TileShapeKey.self] }
        set { self[TileShapeKey.self] = newValue }
    }
}
