import SwiftUI
import UIKit

// The four faces (brand brief §9):
//   Scripture & reading — Literata. Must never feel like an interface.
//   Interface — Alegreya Sans.
//   Metadata — Alegreya Sans SC: true small caps, the way a book sets a
//   running head. No monospace anywhere; everything a mono face would have
//   carried goes into small caps instead.
//   Display — Cesso in the brand; Cesso is an Adobe face that cannot be
//   embedded (build book §12.1, open question §16.12), so in-app display
//   type is Literata standing in. Documented in docs/deviations.md.

enum RibbonType {
    static let literata = "Literata"
    static let sans = "Alegreya Sans"
    static let sansSC = "Alegreya Sans SC"

    /// Scripture body — sized by the reader (S20), scaled by Dynamic Type.
    static func scripture(_ size: CGFloat) -> Font {
        .custom(literata, size: size, relativeTo: .body)
    }

    /// Literata at Medium, for the words another version here says that
    /// yours does not (A62): weight as well as strength, so the difference
    /// is never carried by tone alone. The face is variable; Medium is a
    /// point on its weight axis, not a second file.
    static func scriptureMedium(_ size: CGFloat) -> Font {
        .custom(literata, size: size, relativeTo: .body).weight(.medium)
    }

    /// Literata's italic, for a word's transliteration (A60): how to say it,
    /// set apart from what it means. The italic face is registered beside
    /// the roman, so the family finds it.
    static func scriptureItalic(_ size: CGFloat) -> Font {
        .custom(literata, size: size, relativeTo: .body).italic()
    }

    /// The original words themselves (A60): Greek in Literata, which has
    /// polytonic Greek; Hebrew and Aramaic in Noto Serif Hebrew, which has
    /// the vowel points Literata lacks.
    static func original(_ size: CGFloat, hebrew: Bool) -> Font {
        .custom(hebrew ? hebrewFace : literata, size: size, relativeTo: .body)
    }

    static let hebrewFace = "Noto Serif Hebrew"

    /// Display — book names, the finishing line. (Literata standing in for
    /// Cesso.)
    static func display(_ size: CGFloat) -> Font {
        .custom(literata, size: size, relativeTo: .largeTitle)
    }

    /// Interface text.
    static func ui(_ size: CGFloat = 17) -> Font {
        .custom(sans, size: size, relativeTo: .body)
    }

    static func uiMedium(_ size: CGFloat = 17) -> Font {
        .custom("AlegreyaSans-Medium", size: size, relativeTo: .body)
    }

    /// True small caps. Set strings in normal sentence case; the face does
    /// the capitalization work.
    static func smallCaps(_ size: CGFloat = 13) -> Font {
        .custom(sansSC, size: size, relativeTo: .footnote)
    }

    // UIKit faces for the reading surface (TextKit), scaled through
    // UIFontMetrics so the page follows Dynamic Type all the way through
    // AX5 — nothing in the reading surface uses a fixed point size (§11).
    static func uiScripture(_ size: CGFloat) -> UIFont {
        let base = UIFont(name: "Literata", size: size)
            ?? UIFont(name: "Literata-Regular", size: size)
            ?? .systemFont(ofSize: size, weight: .regular)
        return UIFontMetrics(forTextStyle: .body).scaledFont(for: base)
    }

    static func uiSmallCaps(_ size: CGFloat) -> UIFont {
        let base = UIFont(name: "AlegreyaSansSC-Regular", size: size)
            ?? UIFont(name: "Alegreya Sans SC", size: size)
            ?? .systemFont(ofSize: size, weight: .medium)
        return UIFontMetrics(forTextStyle: .footnote).scaledFont(for: base)
    }

    static func uiSans(_ size: CGFloat) -> UIFont {
        let base = UIFont(name: "AlegreyaSans-Regular", size: size)
            ?? UIFont(name: "Alegreya Sans", size: size)
            ?? .systemFont(ofSize: size)
        return UIFontMetrics(forTextStyle: .body).scaledFont(for: base)
    }
}

/// A line of small caps metadata — the running-head voice used all over the
/// app: room names, states, relative times, quiet controls.
struct SmallCaps: View {
    var text: String
    var size: CGFloat = 13
    var color: Color = Palette.muted

    init(_ text: String, size: CGFloat = 13, color: Color = Palette.muted) {
        self.text = text
        self.size = size
        self.color = color
    }

    var body: some View {
        Text(text)
            .font(RibbonType.smallCaps(size))
            .kerning(size * 0.075)
            .foregroundStyle(color)
    }
}
