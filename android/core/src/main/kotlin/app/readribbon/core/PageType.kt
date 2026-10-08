package app.readribbon.core

import kotlin.math.abs
import kotlin.math.roundToInt

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
// A port of core/Sources/RibbonCore/PageType.swift, case for case.

object PageType {
    // Line spacing

    /**
     * S20's three steps of leading: Close, Book, Open. Book is S02's
     * "generous leading (1.72)".
     */
    val lineHeightMultiples: List<Double> = listOf(1.55, 1.72, 1.9)

    /**
     * The leading for a step. A step outside the table is held at its
     * nearer end, so a saved setting this build does not know still sets a
     * page.
     */
    fun lineHeightMultiple(step: Int): Double = lineHeightMultiples[stop(step, lineHeightMultiples.size)]

    // Weight

    /**
     * Lighter, Book, Heavier, on Literata's own weight axis: the face is
     * variable from 200 to 900, so every stop is drawn, never thickened.
     * Book is 400, the page today. Heavier stops at 470, short of Medium
     * (500), because Medium is how the original panel says a word differs
     * between versions, weight as well as strength and never colour alone
     * (A62); on a Heavier page those words must still stand out.
     */
    val weights: List<Int> = listOf(350, 400, 470)

    /**
     * What the system's Bold Text adds to the chosen stop. It asks for more
     * ink, and the page answers with the axis rather than a synthesized
     * bold; half of a full bold's step, because a whole chapter set bold
     * reads as emphasis from end to end. Book with Bold Text is 550.
     */
    const val boldTextWeight = 150

    /** The weight for a step, with Bold Text or without. */
    fun weight(step: Int, boldText: Boolean): Int =
        weights[stop(step, weights.size)] + if (boldText) boldTextWeight else 0

    // Verse numbers

    /** S02's verse numbers, "at ~45% opacity": there without shouting. */
    const val quietVerseNumberAlpha = 0.45

    /**
     * Clearer, for finding a verse at a glance. In ivory on the ground the
     * quiet numbers are about 4:1, and these are past 7:1. Only the ink
     * changes; nothing on the page moves.
     */
    const val clearVerseNumberAlpha = 0.70

    fun verseNumberAlpha(clear: Boolean): Double = if (clear) clearVerseNumberAlpha else quietVerseNumberAlpha

    // Size

    /** Scripture's size in points before font scale, which still scales it on top (§11). */
    const val defaultSize = 19.0

    /** The slider's step: half a point. */
    const val sizeStep = 0.5

    /**
     * It used to stop at 24. A reader who wants the page larger without
     * making every screen larger now has four more points. Swift has one
     * `sizeRange`; a Material slider wants its two ends.
     */
    const val sizeMin = 16.0
    const val sizeMax = 28.0
    val sizeRange: ClosedFloatingPointRange<Double> = sizeMin..sizeMax

    // Defaults

    /** Book. */
    const val defaultLineSpacingStep = 1

    /** Book. */
    const val defaultWeightStep = 1

    // Scales (A69)

    /**
     * Line spacing in hundredths of the multiple: thirteen places, evenly
     * spaced along the track whatever the gaps between them, through
     * Close, Book and Open.
     */
    val lineHeightScale = PageScale(listOf(145, 150, 155, 160, 165, 172, 178, 184, 190, 195, 200, 205, 210), book = 172)

    /**
     * The old stops, Close, Book and Open, in hundredths: what a slider
     * names aloud, and what is written beside a value for an older build.
     */
    val lineSpacingNamed: List<Int> = listOf(155, 172, 190)

    /**
     * Weight on Literata's axis, before Bold Text. Its top is Heavier, and
     * stays short of the differing words' Medium (A62).
     */
    val weightScale = PageScale(listOf(350, 360, 370, 380, 390, 400, 410, 420, 430, 440, 450, 460, 470), book = 400)

    /** Room between the letters, in thousandths of an em. Book is none. */
    val letterSpacingScale = PageScale(listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60), book = 0)

    /**
     * Points a side, added outside the gutter and inside the trailing edge,
     * never over either. Book is none.
     */
    val marginScale = PageScale(listOf(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48), book = 0)

    // Size by place

    /**
     * The size slider's places, 16 to 28 in halves. Size keeps its Double
     * key: a half is exact.
     */
    const val sizePositions = 25

    /**
     * The place on the size slider nearest a size. A size past either end
     * is held there.
     */
    fun sizeIndex(of: Double): Int {
        if (of.isNaN()) return 0
        return ((of.coerceIn(sizeMin, sizeMax) - sizeMin) / sizeStep).roundToInt()
    }

    /**
     * The size at a place on the slider. A place past either end is held
     * there.
     */
    fun size(at: Int): Double = sizeMin + sizeStep * at.coerceIn(0, sizePositions - 1)

    // Old steps and new values

    /**
     * The line spacing a file asks for, in hundredths: the slider's value
     * held to the scale, or, in a file from before the sliders, its step's
     * stop. So an old Open page is still 190.
     */
    fun lineHeightHundredths(saved: Int?, legacyStep: Int): Int =
        saved?.let(lineHeightScale::held) ?: lineSpacingNamed[stop(legacyStep, lineSpacingNamed.size)]

    /**
     * The multiple for a value in hundredths, held to the scale. 172 is
     * 1.72, the same number as the literal. Swift overloads
     * `lineHeightMultiple(hundredths:)`; Kotlin cannot beside
     * `lineHeightMultiple(step)`, which takes an Int too.
     */
    fun lineHeightMultipleOf(hundredths: Int): Double = lineHeightScale.held(hundredths).toDouble() / 100

    /**
     * The weight a file asks for on Literata's axis, before Bold Text: the
     * slider's value held to the scale, or an older file's step.
     */
    fun weight(saved: Int?, legacyStep: Int): Int =
        saved?.let(weightScale::held) ?: weights[stop(legacyStep, weights.size)]

    /**
     * The old step nearest a line spacing, written beside it so that a
     * build from before the sliders opens on nearly the same page. Exactly
     * halfway between two stops, Book.
     */
    fun lineSpacingStep(forHundredths: Int): Int = lineHeightScale.nearestNamed(forHundredths, lineSpacingNamed).index

    /** The old step nearest a weight, as for line spacing. */
    fun weightStep(forWeight: Int): Int = weightScale.nearestNamed(forWeight, weights).index

    // Faces (A69)

    /**
     * A size set in a face: so much larger or smaller that its x-height is
     * Literata's, and 19 looks like 19 whichever face the page is in. In
     * Literata it is the size itself.
     */
    fun pointSize(size: Double, face: PageFace): Double = size * face.sizeMatch

    /**
     * A weight in Literata's units, Bold Text already added, drawn in a
     * face: moved by the face's own Book so that its letters carry
     * Literata's colour, and held to what the face can draw. In Literata
     * it is the weight itself.
     */
    fun faceWeight(weight: Int, face: PageFace): Int =
        (weight + face.book - PageFaces.literata.book).coerceIn(face.weightRange)

    /**
     * The line-height multiple iOS must give a face for its lines to fall
     * as far apart as Literata's at the same setting. NSParagraphStyle
     * multiplies the face's own line, and each face's line is its own;
     * this divides it back out. In Literata it is the multiple itself.
     * Android multiplies the em Literata would be set at, so its pitch
     * holds across faces already, and it does not ask.
     */
    fun naturalLineMultiple(multiple: Double, face: PageFace): Double =
        multiple * (PageFaces.literata.naturalLine / (face.sizeMatch * face.naturalLine))

    /**
     * What SwiftUI's `lineSpacing` must add to a face's natural line for
     * the iPhone's page window to fall as its page does. `size` is
     * Literata's equivalent, after Dynamic Type.
     */
    fun extraLeading(size: Double, multiple: Double, face: PageFace): Double =
        size * (PageFaces.literata.naturalLine * multiple - face.sizeMatch * face.naturalLine)

    // Margins (A69)

    /** The narrowest the words may be, in ems of the size. */
    const val minimumColumnEms = 13.0

    /**
     * The margin a page gives: what was asked, unless the words would be
     * left narrower than thirteen ems, when it gives way to them, to none
     * at the largest sizes. `textWidth` is the words' width with no margin;
     * `size` is Literata's equivalent after Dynamic Type or font scale,
     * since a matched face sets Literata's letters to a line.
     */
    fun margin(requested: Double, textWidth: Double, size: Double): Double =
        maxOf(0.0, minOf(requested, (textWidth - minimumColumnEms * size) / 2))

    // Letter spacing (A69)

    /** The room between letters in ems, held to the scale. */
    fun letterSpacingEm(thousandths: Int): Double = letterSpacingScale.held(thousandths).toDouble() / 1000

    private fun stop(step: Int, count: Int): Int = step.coerceIn(0, count - 1)
}

/**
 * One shape for every slider on the page (A69): a short list of whole
 * numbers in the scale's unit, ascending, with Book among them. The track
 * gives each the same room, whatever the gaps between them. A value from
 * anywhere else, a damaged file or a build with wider ends, is held to the
 * nearest of them.
 *
 * Swift's argument labels are this port's parameter names, so a call can
 * read as Swift's does: `index(of = v)`, `index(at = f)`.
 */
data class PageScale(
    /** Ascending and distinct. */
    val values: List<Int>,
    /** Today's page: one of `values`. */
    val book: Int,
) {
    val count: Int get() = values.size

    val bookIndex: Int get() = index(of = book)

    /** The index of the value nearest v. Exactly halfway between two values, the one on Book's side wins. */
    fun index(of: Int): Int {
        val v = heldToTheEnds(of)
        var nearest = 0
        for (i in 1 until count) {
            if (closer(values[i], values[nearest], v)) nearest = i
        }
        return nearest
    }

    /** The value at an index; an index past either end is held there. */
    fun value(at: Int): Int = values[at.coerceIn(0, count - 1)]

    /** v held to the scale: values[index(of: v)]. */
    fun held(v: Int): Int = values[index(of = v)]

    /** Where v sits along the track: Double(index(of: v)) / Double(count - 1). */
    fun fraction(of: Int): Double = if (count > 1) index(of = of).toDouble() / (count - 1).toDouble() else 0.0

    /** The index a point on the track lands on: clamp fraction to 0...1 (NaN → 0), then round(fraction × (count − 1)). */
    fun index(at: Double): Int {
        if (at.isNaN()) return 0
        return (at.coerceIn(0.0, 1.0) * (count - 1)).roundToInt()
    }

    /**
     * The nearest of `named` (ascending, each on the scale) to v — a tie goes to Book —
     * and how many positions v sits from it (negative = below it).
     */
    fun nearestNamed(v: Int, named: List<Int>): NamedReading {
        val held = heldToTheEnds(v)
        var nearest = 0
        for (k in 1 until named.size) {
            if (closer(named[k], named[nearest], held)) nearest = k
        }
        return NamedReading(index = nearest, offset = index(of = v) - index(of = named[nearest]))
    }

    /**
     * v held between the first value and the last, so that no distance
     * below can overflow, however wild the number a file holds.
     */
    private fun heldToTheEnds(v: Int): Int = v.coerceIn(values.first(), values.last())

    /** Whether a is nearer v than b is, a tie going to the one nearer Book. */
    private fun closer(a: Int, b: Int, v: Int): Boolean {
        val toA = abs(a - v)
        val toB = abs(b - v)
        return toA < toB || (toA == toB && abs(a - book) < abs(b - book))
    }
}

/**
 * Where a value sits among a slider's named stops: the nearest, and how
 * many places from it, below it when negative. What a screen reader is
 * told, and the old step written beside a new value.
 */
data class NamedReading(val index: Int, val offset: Int)

/**
 * A face the page can be set in (A69). Only the page's: everything else
 * set in Literata stays in Literata.
 */
data class PageFace(
    /** Stored in settings; never renamed. */
    val id: String,
    /** What a reader sees, and what the colophon says. */
    val name: String,
    /** The file's family name (name ID 1). */
    val family: String,
    /** The default instance (name ID 6). */
    val postScriptName: String,
    /**
     * The bundled file's name before its `[axes]` part. Android's asset is
     * "fonts/${fileStem}.ttf".
     */
    val fileStem: String,
    /** The weights the file draws. */
    val weightRange: IntRange,
    /** The face's weight whose colour matches Literata's Book (400). */
    val book: Int,
    /** Literata's x-height ÷ this face's: 19 here looks like 19 in Literata. */
    val sizeMatch: Double,
    /** (hhea ascender − descender + lineGap) ÷ UPM. */
    val naturalLine: Double,
    /** Whether the file has an optical-size axis. */
    val hasOpticalSize: Boolean,
)

/**
 * The faces the page can be set in, measured with fontTools from the
 * google/fonts files: `book` is the weight whose ink matches Literata at
 * 400, `sizeMatch` the x-height ratio at an optical size of 19.
 */
object PageFaces {
    /** The page's own face, and the default. */
    val literata = PageFace(
        id = "literata", name = "Literata", family = "Literata", postScriptName = "Literata-Regular",
        fileStem = "Literata", weightRange = 200..900, book = 400, sizeMatch = 1.0, naturalLine = 1.485,
        hasOpticalSize = true,
    )

    /**
     * Adobe's text serif: crisper, with an optical size of its own, and a
     * little darker, so its Book is drawn at 392.
     */
    val sourceSerif = PageFace(
        id = "sourceSerif", name = "Source Serif", family = "Source Serif 4",
        postScriptName = "SourceSerif4Roman-Regular", fileStem = "SourceSerif4", weightRange = 200..900,
        book = 392, sizeMatch = 1.062, naturalLine = 1.371, hasOpticalSize = true,
    )

    /**
     * The old style readers know from printed Bibles. Its axis begins at
     * 400, so Lighter is held there.
     */
    val ebGaramond = PageFace(
        id = "ebGaramond", name = "EB Garamond", family = "EB Garamond", postScriptName = "EBGaramond-Regular",
        fileStem = "EBGaramond", weightRange = 400..800, book = 440, sizeMatch = 1.253, naturalLine = 1.305,
        hasOpticalSize = false,
    )

    /**
     * The book face Alegreya Sans was cut beside. Its axis also begins at
     * 400.
     */
    val alegreya = PageFace(
        id = "alegreya", name = "Alegreya", family = "Alegreya", postScriptName = "Alegreya-Regular",
        fileStem = "Alegreya", weightRange = 400..900, book = 426, sizeMatch = 1.113, naturalLine = 1.361,
        hasOpticalSize = false,
    )

    /**
     * The Braille Institute's face for low vision: a sans, offered as a
     * reader's choice, never as the house's.
     */
    val atkinson = PageFace(
        id = "atkinson", name = "Atkinson Hyperlegible", family = "Atkinson Hyperlegible Next",
        postScriptName = "AtkinsonHyperlegibleNext-Regular", fileStem = "AtkinsonHyperlegibleNext",
        weightRange = 200..800, book = 405, sizeMatch = 1.023, naturalLine = 1.300, hasOpticalSize = false,
    )

    /** In this order; Literata first, always. */
    val all: List<PageFace> = listOf(literata, sourceSerif, ebGaramond, alegreya, atkinson)

    /**
     * The face a setting names. Null, or a face this build does not have,
     * is Literata, so a page is always set.
     */
    fun face(id: String?): PageFace = all.firstOrNull { it.id == id } ?: literata
}
