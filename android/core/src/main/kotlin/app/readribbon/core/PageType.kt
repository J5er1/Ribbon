package app.readribbon.core

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

    private fun stop(step: Int, count: Int): Int = step.coerceIn(0, count - 1)
}
