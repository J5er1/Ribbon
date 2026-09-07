@file:OptIn(ExperimentalUuidApi::class)

package app.readribbon.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

// The campfire (build book §4.1). It has exactly four states and no numbers.
//
// Law 2 — a fire reports a state, never a count. Nothing in this file may
// ever surface a number to the interface: no percentages, no tallies, no
// "hours since". The tuning constants below are internal mechanics and are
// never surfaced in copy, in a tooltip, or in support docs (§4.1 — nobody
// in the room should ever know there was a threshold at all).
//
// Law 4 — the coal bed remembers, the flame reports the moment. The fire's
// size is fixed by the book at the start and never changes. Its state is
// recent activity only. The bed of coals beneath is the one thing that may
// grow, because nobody can look at a bed of coals and compute how much is
// left.

/** The only four states a fire has. */
@Serializable
enum class FireState {
    /**
     * Just started, or just restarted after a long quiet. Small, low, a few
     * licks, working at it. Also where a fire that subsides on its own
     * comes to rest — its floor is catching, never banked: a fire nobody
     * tended is not the same object as a fire somebody banked, and drawing
     * them alike would turn an act of care into the look of neglect.
     */
    catching,

    /** Someone has been reading. Full, active, irregular flicker. */
    burning,

    /**
     * The room has been reading together. Broad, even, a wide warm throw,
     * almost calm. The only place in the product where togetherness is
     * mechanically rewarded — and the return is a mood, not a bonus.
     */
    steady,

    /**
     * Deliberately kept low and warm. Coals under ash, a dim glow, no
     * flame. Only entered when someone marks a quiet day. Banked is an
     * act, never a lapse.
     */
    banked;

    /**
     * Small-caps state line under the fire (S01) and the screen-reader
     * label ("The fire is steady." — never a percentage, §11).
     */
    val displayName: String get() = name
}

/**
 * One person feeding the fire once. Fuel is reading: any member reading any
 * passage feeds the fire — it never matters who, or where in the book.
 * Events live only inside the rolling window and are discarded after (§13):
 * the cap is what makes a consistency report impossible.
 */
@Serializable
data class FuelEvent(
    val personID: Uuid,
    val at: Instant
)

/**
 * Internal mechanics. "~36 hours" is deliberately not a day: a 24-hour
 * boundary manufactures a midnight deadline, and a deadline is a count you
 * can be late for. Someone who reads at 10 p.m. Monday and 9 a.m. Wednesday
 * never experiences a lapse.
 *
 * Decision — the subsiding curve. The book says a fire without fuel "eases
 * down through the states over days," never goes out, never resets, and
 * rests at catching. The concrete curve here: a steady fire reads as
 * burning once its window closes and rests at catching after about five
 * days; a burning fire rests at catching after about four. These numbers
 * are tunable and deliberately vague-feeling; if they ever appear in copy,
 * that is a bug.
 */
data class FireTuning(
    /** The rolling activity window. */
    val fuelWindowHours: Double = 36.0,
    /**
     * Fuel arriving after a quiet this long lands the fire at catching —
     * just restarted, working at it — rather than jumping straight to
     * burning.
     */
    val longQuietHours: Double = 96.0,
    /** A burning fire, unfed, comes to rest at catching after this long. */
    val burningRestsAtHours: Double = 96.0,
    /**
     * A steady fire, unfed, reads as burning after the window closes and
     * comes to rest at catching after this long.
     */
    val steadyRestsAtHours: Double = 120.0,
    /**
     * While catching after a restart, a further feeding at least this much
     * later lifts the fire to burning — one reading is a spark; returning
     * to it is a fire.
     */
    val restartPromotionMinutes: Double = 15.0,
    /**
     * A person's repeat feedings within this many minutes collapse into
     * one event, so a long session doesn't inflate the record.
     */
    val perPersonThrottleMinutes: Double = 15.0,
    /** Feedings at least this far apart deepen the coal bed. */
    val coalCreditHours: Double = 4.0
) {
    companion object {
        val standard = FireTuning()
    }
}

/**
 * The campfire's persistent state. This struct is the whole of what is
 * remembered about a room's tending — by design there is nothing here from
 * which a consistency report could be assembled.
 */
@Serializable
data class Handiwork(
    var kind: Kind = Kind.campfire,
    var scale: FireScale,
    /**
     * The bed of coals: 0...1, asymptotic, monotonically deepening over a
     * long read. Drives the width and warmth of the glow, nothing else.
     */
    var coalDepth: Double = 0.0,
    var lastFuelAt: Instant? = null,
    /**
     * Set while the fire is catching after a restart (or first light);
     * cleared on promotion.
     */
    var restartAt: Instant? = null,
    /**
     * The state the fire held at the moment of its last feeding. Subsiding
     * eases down from here.
     */
    var stateAtLastFuel: FireState = FireState.catching,
    /**
     * Fuel within the rolling window, pruned on every touch. Never larger
     * than a few entries per member.
     */
    var recentFuel: List<FuelEvent> = emptyList()
) {
    /**
     * Campfire at launch; mosaic, stained glass and the painting follow
     * once the fire has proven the mechanic (§2.8).
     */
    @Serializable
    enum class Kind {
        campfire
    }

    // Swift's two initializers — the fresh `init(kind:scale:)` and the one
    // that rebuilds a stored fire — collapse into this single constructor,
    // because Kotlin default arguments already give both call shapes. The
    // normalizing that the second initializer did runs here, for both.
    // It also runs on the decode path, which Swift's synthesized
    // `init(from:)` bypasses — strictly more of the same invariant, never
    // less, so a row that somehow stored `banked` is read as catching here.
    //
    // Rebuilding a fire from the backend's stored row (sync). The engine's
    // own feeding rules produced these values on some device; this restores
    // them without re-deriving. `banked` is not restorable state — banking
    // lives on quiet days, never on the fire — so it clamps to catching.
    /**
     * When the coal bed was last credited. Private, as Swift keeps it.
     *
     * It still survives a save and reload, by two routes that agree.
     * kotlinx-serialization encodes every property with a backing field —
     * private and class-body ones included, the same reach Swift's
     * synthesized Codable has over a struct's stored properties — so the
     * store, which encodes defaults, writes it and reads it back. An encoder
     * that omits defaults drops it instead, and then this initializer
     * re-seeds it from `lastFuelAt`, which is what Swift's restoring
     * initializer does with a row from the backend. Both routes land on the
     * same credit schedule; HandiworkPersistenceTest holds both down.
     *
     * The one difference left is that a data class builds equals/hashCode
     * from its constructor properties only, so two Handiworks differing just
     * in this timestamp compare equal here and unequal on iOS. Nothing in
     * either app compares fires, so it is recorded rather than worked
     * around.
     */
    private var lastCoalCreditAt: Instant? = lastFuelAt

    init {
        coalDepth = coalDepth.coerceIn(0.0, 1.0)
        if (stateAtLastFuel == FireState.banked) {
            stateAtLastFuel = FireState.catching
        }
    }

    // MARK: - Reading the fire

    /**
     * The state a glance reports. [bankedIntervals] are the merged banked
     * spans from the room's quiet days — see [QuietDay.bankedInterval].
     */
    fun state(
        now: Instant,
        bankedIntervals: List<DateInterval> = emptyList(),
        tuning: FireTuning = FireTuning.standard
    ): FireState {
        // Banked wins while any quiet day covers this moment. Banking is an
        // act performed for the room; it is never inferred.
        if (bankedIntervals.any { it.contains(now) }) {
            return FireState.banked
        }
        val lastFuel = lastFuelAt ?: return FireState.catching

        // Quiet days pause subsiding: elapsed time excludes banked spans.
        val hours = effectiveHours(from = lastFuel, to = now, excluding = bankedIntervals)

        if (hours <= tuning.fuelWindowHours) {
            return stateAtLastFuel
        }
        // Subsiding is time. It never goes out, never resets, never says why.
        return when (stateAtLastFuel) {
            FireState.steady ->
                if (hours <= tuning.steadyRestsAtHours) FireState.burning else FireState.catching
            FireState.burning ->
                if (hours <= tuning.burningRestsAtHours) FireState.burning else FireState.catching
            FireState.catching, FireState.banked ->
                FireState.catching
        }
    }

    // MARK: - Feeding the fire

    /**
     * Record that a member read. Call on meaningful reading activity; the
     * per-person throttle makes over-calling harmless.
     */
    fun feed(
        personID: Uuid,
        now: Instant,
        bankedIntervals: List<DateInterval> = emptyList(),
        tuning: FireTuning = FireTuning.standard
    ) {
        // Collapse repeat feedings from one person inside the throttle.
        val last = recentFuel.lastOrNull { it.personID == personID }
        if (last != null && (now - last.at) < (tuning.perPersonThrottleMinutes * 60).seconds) {
            deepenCoals(now = now, tuning = tuning)
            return
        }

        val lastFuel = lastFuelAt
        val quietSince: Double = if (lastFuel != null) {
            effectiveHours(from = lastFuel, to = now, excluding = bankedIntervals)
        } else {
            Double.POSITIVE_INFINITY
        }

        prune(now = now, tuning = tuning)
        recentFuel = recentFuel + FuelEvent(personID = personID, at = now)

        if (quietSince > tuning.longQuietHours) {
            // First light, or fuel after a long quiet: the fire catches.
            stateAtLastFuel = FireState.catching
            restartAt = now
            recentFuel = listOf(FuelEvent(personID = personID, at = now))
        } else {
            val distinctFeeders = recentFuel.map { it.personID }.toSet().size
            val restart = restartAt
            if (distinctFeeders >= 2) {
                // Air is overlap: two or more feeding within the window
                // lifts the fire to steady.
                stateAtLastFuel = FireState.steady
                restartAt = null
            } else if (restart != null &&
                (now - restart) >= (tuning.restartPromotionMinutes * 60).seconds
            ) {
                stateAtLastFuel = FireState.burning
                restartAt = null
            } else if (restart != null) {
                stateAtLastFuel = FireState.catching
            } else {
                stateAtLastFuel = FireState.burning
            }
        }

        lastFuelAt = now
        deepenCoals(now = now, tuning = tuning)
    }

    private fun deepenCoals(now: Instant, tuning: FireTuning) {
        val last = lastCoalCreditAt
        if (last != null && (now - last) < (tuning.coalCreditHours * 3600).seconds) {
            return
        }
        // Asymptotic: the bed deepens forever without ever filling — there
        // is no "full," so there is nothing to compute progress against.
        coalDepth += (1 - coalDepth) * 0.055
        lastCoalCreditAt = now
    }

    private fun prune(now: Instant, tuning: FireTuning) {
        val cutoff = now - (tuning.fuelWindowHours * 3600).seconds
        recentFuel = recentFuel.filter { it.at >= cutoff }
    }

    // MARK: - Time, minus banked spans

    companion object {
        internal fun effectiveHours(
            from: Instant, to: Instant, excluding: List<DateInterval>
        ): Double {
            if (to <= from) return 0.0
            val whole = DateInterval(start = from, end = to)
            var excluded: Duration = Duration.ZERO
            for (interval in merged(excluding)) {
                val overlap = whole.intersection(interval)
                if (overlap != null) {
                    excluded += overlap.duration
                }
            }
            return (whole.duration - excluded).toDouble(DurationUnit.SECONDS) / 3600
        }

        internal fun merged(intervals: List<DateInterval>): List<DateInterval> {
            val sorted = intervals.sortedBy { it.start }
            val result: MutableList<DateInterval> = mutableListOf()
            for (interval in sorted) {
                val last = result.lastOrNull()
                if (last != null && interval.start <= last.end) {
                    val end = maxOf(last.end, interval.end)
                    result[result.size - 1] = DateInterval(start = last.start, end = end)
                } else {
                    result.add(interval)
                }
            }
            return result
        }
    }
}

/** The merged banked spans for a room's quiet days. */
val Iterable<QuietDay>.bankedIntervals: List<DateInterval>
    get() = Handiwork.merged(mapNotNull { it.bankedInterval })
