package app.readribbon.core

import kotlin.math.abs

/**
 * Quiet reflection questions for chapters (§4.6).
 *
 * Written in Ribbon's voice: short, concrete, second person, answerable in a
 * sentence. A card offers one question per chapter to invite reflection
 * without demanding theology or turning reading into homework.
 */
object ReflectionPrompts {
    private val prompts = listOf(
        "What did you notice that the other one probably didn't?",
        "Which line or phrase held you the longest?",
        "What felt surprising, quiet, or unexpected here?",
        "If you kept only one sentence from this chapter, which one?",
        "What question does this chapter leave in your mind?",
        "Where did this feel closest to ordinary life?",
        "What did someone in this passage want, and what happened?",
        "Which moment in this chapter felt most alive to you?",
    )

    /**
     * Returns a deterministic, thoughtful prompt for the given chapter number.
     */
    fun prompt(chapter: Int): String {
        val index = abs(chapter - 1) % prompts.size
        return prompts[index]
    }
}
