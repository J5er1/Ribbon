import Foundation

// The page, the way you read it (A68). S20 gave the reader three things to
// set, size, spacing and red letter, because they are "about eyes" (A42).
// The owner asked for more, and the page now also takes a weight, a new
// line for every verse, and clearer verse numbers. Set nothing and the
// page is the page it has always been: every default below is today's.
//
// This file is the numbers and nothing else. The step tables used to be
// written out twice, once in each app's settings; they live here so that
// both phones set the same page from the same choice, and a test can say
// so. Each choice is kept as a step or a switch rather than as the number
// it stands for, so that a table can be retuned without rewriting anyone's
// saved settings.

public enum PageType {
    // MARK: Line spacing

    /// S20's three steps of leading: Close, Book, Open. Book is S02's
    /// "generous leading (1.72)".
    public static let lineHeightMultiples: [Double] = [1.55, 1.72, 1.9]

    /// The leading for a step. A step outside the table is held at its
    /// nearer end, so a saved setting this build does not know still sets
    /// a page.
    public static func lineHeightMultiple(step: Int) -> Double {
        lineHeightMultiples[stop(step, in: lineHeightMultiples.count)]
    }

    // MARK: Weight

    /// Lighter, Book, Heavier, on Literata's own weight axis: the face is
    /// variable from 200 to 900, so every stop is drawn, never thickened.
    /// Book is 400, the page today. Heavier stops at 470, short of Medium
    /// (500), because Medium is how the original panel says a word differs
    /// between versions, weight as well as strength and never colour alone
    /// (A62); on a Heavier page those words must still stand out.
    public static let weights: [Int] = [350, 400, 470]

    /// What the system's Bold Text adds to the chosen stop. It asks for more
    /// ink, and the page answers with the axis rather than a synthesized
    /// bold; half of a full bold's step, because a whole chapter set bold
    /// reads as emphasis from end to end. Book with Bold Text is 550.
    public static let boldTextWeight = 150

    /// The weight for a step, with Bold Text or without.
    public static func weight(step: Int, boldText: Bool) -> Int {
        weights[stop(step, in: weights.count)] + (boldText ? boldTextWeight : 0)
    }

    // MARK: Verse numbers

    /// S02's verse numbers, "at ~45% opacity": there without shouting.
    public static let quietVerseNumberAlpha = 0.45
    /// Clearer, for finding a verse at a glance. In ivory on the ground the
    /// quiet numbers are about 4:1, and these are past 7:1. Only the ink
    /// changes; nothing on the page moves.
    public static let clearVerseNumberAlpha = 0.70

    public static func verseNumberAlpha(clear: Bool) -> Double {
        clear ? clearVerseNumberAlpha : quietVerseNumberAlpha
    }

    // MARK: Size

    /// Scripture's size in points before Dynamic Type, which still scales
    /// it on top (§11).
    public static let defaultSize = 19.0
    /// The slider's step: half a point.
    public static let sizeStep = 0.5
    /// It used to stop at 24. A reader who wants the page larger without
    /// making every screen larger now has four more points.
    public static let sizeRange: ClosedRange<Double> = 16...28

    // MARK: Defaults

    /// Book.
    public static let defaultLineSpacingStep = 1
    /// Book.
    public static let defaultWeightStep = 1

    private static func stop(_ step: Int, in count: Int) -> Int {
        min(max(step, 0), count - 1)
    }
}
