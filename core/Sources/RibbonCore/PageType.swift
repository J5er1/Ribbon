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
// so. The three steps are kept, as the named stops a slider passes and as
// what a file written before the sliders still opens on. The sliders
// themselves store whole numbers on scales (A69): hundredths of the
// multiple, axis units, thousandths of an em, points. Never a Double,
// which drifts on a grid; and every value is held to its scale on the way
// in, so a scale can be retuned without rewriting anyone's saved settings.

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

    // MARK: Scales (A69)

    /// Line spacing in hundredths of the multiple: thirteen places, evenly
    /// spaced along the track whatever the gaps between them, through
    /// Close, Book and Open.
    public static let lineHeightScale = PageScale(
        [145, 150, 155, 160, 165, 172, 178, 184, 190, 195, 200, 205, 210], book: 172)
    /// The old stops, Close, Book and Open, in hundredths: what a slider
    /// names aloud, and what is written beside a value for an older build.
    public static let lineSpacingNamed = [155, 172, 190]
    /// Weight on Literata's axis, before Bold Text. Its top is Heavier, and
    /// stays short of the differing words' Medium (A62).
    public static let weightScale = PageScale(
        [350, 360, 370, 380, 390, 400, 410, 420, 430, 440, 450, 460, 470], book: 400)
    /// Room between the letters, in thousandths of an em. Book is none.
    public static let letterSpacingScale = PageScale(
        [0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60], book: 0)
    /// Points a side, added outside the gutter and inside the trailing edge,
    /// never over either. Book is none.
    public static let marginScale = PageScale(
        [0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48], book: 0)

    // MARK: Size by place

    /// The size slider's places, 16 to 28 in halves. Size keeps its Double
    /// key: a half is exact.
    public static let sizePositions = 25

    /// The place on the size slider nearest a size. A size past either end
    /// is held there.
    public static func sizeIndex(of size: Double) -> Int {
        guard !size.isNaN else { return 0 }
        let held = min(max(size, sizeRange.lowerBound), sizeRange.upperBound)
        return Int(((held - sizeRange.lowerBound) / sizeStep).rounded())
    }

    /// The size at a place on the slider. A place past either end is held
    /// there.
    public static func size(at index: Int) -> Double {
        sizeRange.lowerBound + sizeStep * Double(min(max(index, 0), sizePositions - 1))
    }

    // MARK: Old steps and new values

    /// The line spacing a file asks for, in hundredths: the slider's value
    /// held to the scale, or, in a file from before the sliders, its step's
    /// stop. So an old Open page is still 190.
    public static func lineHeightHundredths(saved: Int?, legacyStep: Int) -> Int {
        saved.map(lineHeightScale.held) ?? lineSpacingNamed[stop(legacyStep, in: lineSpacingNamed.count)]
    }

    /// The multiple for a value in hundredths, held to the scale. 172 is
    /// 1.72, the same number as the literal.
    public static func lineHeightMultiple(hundredths: Int) -> Double {
        Double(lineHeightScale.held(hundredths)) / 100
    }

    /// The weight a file asks for on Literata's axis, before Bold Text: the
    /// slider's value held to the scale, or an older file's step.
    public static func weight(saved: Int?, legacyStep: Int) -> Int {
        saved.map(weightScale.held) ?? weights[stop(legacyStep, in: weights.count)]
    }

    /// The old step nearest a line spacing, written beside it so that a
    /// build from before the sliders opens on nearly the same page. Exactly
    /// halfway between two stops, Book.
    public static func lineSpacingStep(forHundredths hundredths: Int) -> Int {
        lineHeightScale.nearestNamed(hundredths, named: lineSpacingNamed).index
    }

    /// The old step nearest a weight, as for line spacing.
    public static func weightStep(forWeight weight: Int) -> Int {
        weightScale.nearestNamed(weight, named: weights).index
    }

    // MARK: Faces (A69)

    /// A size set in a face: so much larger or smaller that its x-height is
    /// Literata's, and 19 looks like 19 whichever face the page is in. In
    /// Literata it is the size itself.
    public static func pointSize(_ size: Double, face: PageFace) -> Double {
        size * face.sizeMatch
    }

    /// A weight in Literata's units, Bold Text already added, drawn in a
    /// face: moved by the face's own Book so that its letters carry
    /// Literata's colour, and held to what the face can draw. In Literata
    /// it is the weight itself.
    public static func faceWeight(_ weight: Int, face: PageFace) -> Int {
        let drawn = weight + face.book - PageFaces.literata.book
        return min(max(drawn, face.weightRange.lowerBound), face.weightRange.upperBound)
    }

    /// The line-height multiple iOS must give a face for its lines to fall
    /// as far apart as Literata's at the same setting. NSParagraphStyle
    /// multiplies the face's own line, and each face's line is its own;
    /// this divides it back out. In Literata it is the multiple itself.
    /// Android multiplies the em Literata would be set at, so its pitch
    /// holds across faces already, and it does not ask.
    public static func naturalLineMultiple(_ multiple: Double, face: PageFace) -> Double {
        multiple * (PageFaces.literata.naturalLine / (face.sizeMatch * face.naturalLine))
    }

    /// What SwiftUI's `lineSpacing` must add to a face's natural line for
    /// the iPhone's page window to fall as its page does. `size` is
    /// Literata's equivalent, after Dynamic Type.
    public static func extraLeading(size: Double, multiple: Double, face: PageFace) -> Double {
        size * (PageFaces.literata.naturalLine * multiple - face.sizeMatch * face.naturalLine)
    }

    // MARK: Margins (A69)

    /// The narrowest the words may be, in ems of the size.
    public static let minimumColumnEms = 13.0

    /// The margin a page gives: what was asked, unless the words would be
    /// left narrower than thirteen ems, when it gives way to them, to none
    /// at the largest sizes. `textWidth` is the words' width with no margin;
    /// `size` is Literata's equivalent after Dynamic Type or font scale,
    /// since a matched face sets Literata's letters to a line.
    public static func margin(requested: Double, textWidth: Double, size: Double) -> Double {
        max(0, min(requested, (textWidth - minimumColumnEms * size) / 2))
    }

    // MARK: Letter spacing (A69)

    /// The room between letters in ems, held to the scale.
    public static func letterSpacingEm(_ thousandths: Int) -> Double {
        Double(letterSpacingScale.held(thousandths)) / 1000
    }

    private static func stop(_ step: Int, in count: Int) -> Int {
        min(max(step, 0), count - 1)
    }
}

/// One shape for every slider on the page (A69): a short list of whole
/// numbers in the scale's unit, ascending, with Book among them. The track
/// gives each the same room, whatever the gaps between them. A value from
/// anywhere else, a damaged file or a build with wider ends, is held to the
/// nearest of them.
public struct PageScale: Hashable, Sendable {
    /// Ascending and distinct.
    public let values: [Int]
    /// Today's page: one of `values`.
    public let book: Int

    public init(_ values: [Int], book: Int) {
        self.values = values
        self.book = book
    }

    public var count: Int { values.count }

    public var bookIndex: Int { index(of: book) }

    /// The index of the value nearest v. Exactly halfway between two values, the one on Book's side wins.
    public func index(of v: Int) -> Int {
        let v = heldToTheEnds(v)
        var nearest = 0
        for i in values.indices.dropFirst() where closer(values[i], than: values[nearest], to: v) {
            nearest = i
        }
        return nearest
    }

    /// The value at an index; an index past either end is held there.
    public func value(at index: Int) -> Int {
        values[min(max(index, 0), count - 1)]
    }

    /// v held to the scale: values[index(of: v)].
    public func held(_ v: Int) -> Int {
        values[index(of: v)]
    }

    /// Where v sits along the track: Double(index(of: v)) / Double(count - 1).
    public func fraction(of v: Int) -> Double {
        count > 1 ? Double(index(of: v)) / Double(count - 1) : 0
    }

    /// The index a point on the track lands on: clamp fraction to 0...1 (NaN → 0), then round(fraction × (count − 1)).
    public func index(at fraction: Double) -> Int {
        guard !fraction.isNaN else { return 0 }
        return Int((min(max(fraction, 0), 1) * Double(count - 1)).rounded())
    }

    /// The nearest of `named` (ascending, each on the scale) to v — a tie goes to Book —
    /// and how many positions v sits from it (negative = below it).
    public func nearestNamed(_ v: Int, named: [Int]) -> NamedReading {
        let held = heldToTheEnds(v)
        var nearest = 0
        for k in named.indices.dropFirst() where closer(named[k], than: named[nearest], to: held) {
            nearest = k
        }
        return NamedReading(index: nearest, offset: index(of: v) - index(of: named[nearest]))
    }

    /// v held between the first value and the last, so that no distance
    /// below can overflow, however wild the number a file holds.
    private func heldToTheEnds(_ v: Int) -> Int {
        min(max(v, values[0]), values[count - 1])
    }

    /// Whether a is nearer v than b is, a tie going to the one nearer Book.
    private func closer(_ a: Int, than b: Int, to v: Int) -> Bool {
        let toA = abs(a - v), toB = abs(b - v)
        return toA < toB || (toA == toB && abs(a - book) < abs(b - book))
    }
}

/// Where a value sits among a slider's named stops: the nearest, and how
/// many places from it, below it when negative. What a screen reader is
/// told, and the old step written beside a new value.
public struct NamedReading: Hashable, Sendable {
    public let index: Int
    public let offset: Int

    public init(index: Int, offset: Int) {
        self.index = index
        self.offset = offset
    }
}

/// A face the page can be set in (A69). Only the page's: everything else
/// set in Literata stays in Literata.
public struct PageFace: Hashable, Sendable, Identifiable {
    /// Stored in settings; never renamed.
    public let id: String
    /// What a reader sees, and what the colophon says.
    public let name: String
    /// The file's family name (name ID 1).
    public let family: String
    /// The default instance (name ID 6).
    public let postScriptName: String
    /// The bundled file's name before its `[axes]` part. Android's asset is
    /// "fonts/\(fileStem).ttf".
    public let fileStem: String
    /// The weights the file draws.
    public let weightRange: ClosedRange<Int>
    /// The face's weight whose colour matches Literata's Book (400).
    public let book: Int
    /// Literata's x-height ÷ this face's: 19 here looks like 19 in Literata.
    public let sizeMatch: Double
    /// (hhea ascender − descender + lineGap) ÷ UPM.
    public let naturalLine: Double
    /// Whether the file has an optical-size axis.
    public let hasOpticalSize: Bool
}

/// The faces the page can be set in, measured with fontTools from the
/// google/fonts files: `book` is the weight whose ink matches Literata at
/// 400, `sizeMatch` the x-height ratio at an optical size of 19.
public enum PageFaces {
    /// The page's own face, and the default.
    public static let literata = PageFace(
        id: "literata", name: "Literata", family: "Literata", postScriptName: "Literata-Regular",
        fileStem: "Literata", weightRange: 200...900, book: 400, sizeMatch: 1.0, naturalLine: 1.485,
        hasOpticalSize: true)
    /// Adobe's text serif: crisper, with an optical size of its own, and a
    /// little darker, so its Book is drawn at 392.
    public static let sourceSerif = PageFace(
        id: "sourceSerif", name: "Source Serif", family: "Source Serif 4",
        postScriptName: "SourceSerif4Roman-Regular", fileStem: "SourceSerif4", weightRange: 200...900,
        book: 392, sizeMatch: 1.062, naturalLine: 1.371, hasOpticalSize: true)
    /// The old style readers know from printed Bibles. Its axis begins at
    /// 400, so Lighter is held there.
    public static let ebGaramond = PageFace(
        id: "ebGaramond", name: "EB Garamond", family: "EB Garamond", postScriptName: "EBGaramond-Regular",
        fileStem: "EBGaramond", weightRange: 400...800, book: 440, sizeMatch: 1.253, naturalLine: 1.305,
        hasOpticalSize: false)
    /// The book face Alegreya Sans was cut beside. Its axis also begins at
    /// 400.
    public static let alegreya = PageFace(
        id: "alegreya", name: "Alegreya", family: "Alegreya", postScriptName: "Alegreya-Regular",
        fileStem: "Alegreya", weightRange: 400...900, book: 426, sizeMatch: 1.113, naturalLine: 1.361,
        hasOpticalSize: false)
    /// The Braille Institute's face for low vision: a sans, offered as a
    /// reader's choice, never as the house's.
    public static let atkinson = PageFace(
        id: "atkinson", name: "Atkinson Hyperlegible", family: "Atkinson Hyperlegible Next",
        postScriptName: "AtkinsonHyperlegibleNext-Regular", fileStem: "AtkinsonHyperlegibleNext",
        weightRange: 200...800, book: 405, sizeMatch: 1.023, naturalLine: 1.300, hasOpticalSize: false)

    /// In this order; Literata first, always.
    public static let all: [PageFace] = [literata, sourceSerif, ebGaramond, alegreya, atkinson]

    /// The face a setting names. Nil, or a face this build does not have,
    /// is Literata, so a page is always set.
    public static func face(id: String?) -> PageFace {
        all.first { $0.id == id } ?? literata
    }
}
