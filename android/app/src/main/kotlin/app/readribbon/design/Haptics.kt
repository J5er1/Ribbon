package app.readribbon.design

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Haptics (build book §9.3). §12.2 is explicit that Android uses
// VibrationEffect.Composition for the thinking-of-you tap, "the constants
// are too blunt" — the same reason the iOS build reaches past the feedback
// generators to Core Haptics. The intensities below are the iOS envelope's,
// primitive for primitive.
//
// The full list, and nothing else: someone arrives; a verse lifts;
// thinking-of-you sending (rising texture over the hold, completing on
// release); thinking-of-you receiving (the tap on the shoulder). Cards
// opening get nothing. No selection ticks, no success thumps, no error buzz.

class Haptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator?.takeIf { it.hasVibrator() }
    }.getOrNull()

    private val supportsComposition: Boolean = vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
    ) ?: false

    private fun play(build: VibrationEffect.Composition.() -> VibrationEffect.Composition) {
        val vibrator = vibrator ?: return
        // A missed haptic is silence, which is always acceptable here.
        runCatching {
            if (supportsComposition) {
                vibrator.vibrate(VibrationEffect.startComposition().build().compose())
            }
        }
    }

    /** Someone arrives in the book: one soft transient, low intensity. */
    fun someoneArrives() = play {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.35f)
    }

    /** The verse lifts on long-press — the moment it lifts, not on touch-down. */
    fun verseLifts() = play {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.45f)
    }

    /**
     * Thinking of you, sending: a rising texture over the ~700 ms hold.
     *
     * A Composition cannot be stopped partway the way a Core Haptics
     * continuous player can, so the rise is built as a ladder of low ticks
     * across the hold and [cancelThinkingOfYouHold] cancels the vibrator
     * outright. The felt result is the same: intensity climbing under the
     * finger, and nothing left running if the finger lifts early.
     */
    fun beginThinkingOfYouHold() = play {
        var composition = this
        val steps = 7
        repeat(steps) { step ->
            val scale = 0.10f + (0.28f - 0.10f) * (step / (steps - 1f))
            composition = composition.addPrimitive(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                scale,
                if (step == 0) 0 else 100,
            )
        }
        composition
    }

    /** The hold completed: a single sharp-soft transient on release. */
    fun completeThinkingOfYouHold() {
        cancelThinkingOfYouHold()
        play { addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f) }
    }

    fun cancelThinkingOfYouHold() {
        runCatching { vibrator?.cancel() }
    }

    /**
     * Thinking of you, receiving: one transient with a short decay — the tap
     * on the shoulder. The single fast thing in the product.
     */
    fun tapOnTheShoulder() = play {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.2f, 20)
    }
}
