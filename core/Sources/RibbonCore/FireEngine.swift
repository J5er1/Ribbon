import Foundation

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

/// The only four states a fire has.
public enum FireState: String, Codable, Hashable, CaseIterable, Sendable {
    /// Just started, or just restarted after a long quiet. Small, low, a few
    /// licks, working at it. Also where a fire that subsides on its own
    /// comes to rest — its floor is catching, never banked: a fire nobody
    /// tended is not the same object as a fire somebody banked, and drawing
    /// them alike would turn an act of care into the look of neglect.
    case catching
    /// Someone has been reading. Full, active, irregular flicker.
    case burning
    /// The room has been reading together. Broad, even, a wide warm throw,
    /// almost calm. The only place in the product where togetherness is
    /// mechanically rewarded — and the return is a mood, not a bonus.
    case steady
    /// Deliberately kept low and warm. Coals under ash, a dim glow, no
    /// flame. Only entered when someone marks a quiet day. Banked is an
    /// act, never a lapse.
    case banked

    /// Small-caps state line under the fire (S01) and the screen-reader
    /// label ("The fire is steady." — never a percentage, §11).
    public var displayName: String { rawValue }
}

/// One person feeding the fire once. Fuel is reading: any member reading any
/// passage feeds the fire — it never matters who, or where in the book.
/// Events live only inside the rolling window and are discarded after (§13):
/// the cap is what makes a consistency report impossible.
public struct FuelEvent: Codable, Hashable, Sendable {
    public var personID: UUID
    public var at: Date

    public init(personID: UUID, at: Date) {
        self.personID = personID
        self.at = at
    }
}

/// Internal mechanics. "~36 hours" is deliberately not a day: a 24-hour
/// boundary manufactures a midnight deadline, and a deadline is a count you
/// can be late for. Someone who reads at 10 p.m. Monday and 9 a.m. Wednesday
/// never experiences a lapse.
///
/// Decision — the subsiding curve. The book says a fire without fuel "eases
/// down through the states over days," never goes out, never resets, and
/// rests at catching. The concrete curve here: a steady fire reads as
/// burning once its window closes and rests at catching after about five
/// days; a burning fire rests at catching after about four. These numbers
/// are tunable and deliberately vague-feeling; if they ever appear in copy,
/// that is a bug.
public struct FireTuning: Hashable, Sendable {
    /// The rolling activity window.
    public var fuelWindowHours: Double = 36
    /// Fuel arriving after a quiet this long lands the fire at catching —
    /// just restarted, working at it — rather than jumping straight to
    /// burning.
    public var longQuietHours: Double = 96
    /// A burning fire, unfed, comes to rest at catching after this long.
    public var burningRestsAtHours: Double = 96
    /// A steady fire, unfed, reads as burning after the window closes and
    /// comes to rest at catching after this long.
    public var steadyRestsAtHours: Double = 120
    /// While catching after a restart, a further feeding at least this much
    /// later lifts the fire to burning — one reading is a spark; returning
    /// to it is a fire.
    public var restartPromotionMinutes: Double = 15
    /// A person's repeat feedings within this many minutes collapse into
    /// one event, so a long session doesn't inflate the record.
    public var perPersonThrottleMinutes: Double = 15
    /// Feedings at least this far apart deepen the coal bed.
    public var coalCreditHours: Double = 4

    public static let standard = FireTuning()

    public init() {}
}

/// The campfire's persistent state. This struct is the whole of what is
/// remembered about a room's tending — by design there is nothing here from
/// which a consistency report could be assembled.
public struct Handiwork: Codable, Hashable, Sendable {
    /// Campfire at launch; mosaic, stained glass and the painting follow
    /// once the fire has proven the mechanic (§2.8).
    public enum Kind: String, Codable, Hashable, Sendable {
        case campfire
    }

    public var kind: Kind
    public var scale: FireScale
    /// The bed of coals: 0...1, asymptotic, monotonically deepening over a
    /// long read. Drives the width and warmth of the glow, nothing else.
    public private(set) var coalDepth: Double
    public private(set) var lastFuelAt: Date?
    /// Set while the fire is catching after a restart (or first light);
    /// cleared on promotion.
    public private(set) var restartAt: Date?
    /// The state the fire held at the moment of its last feeding. Subsiding
    /// eases down from here.
    public private(set) var stateAtLastFuel: FireState
    /// Fuel within the rolling window, pruned on every touch. Never larger
    /// than a few entries per member.
    public private(set) var recentFuel: [FuelEvent]
    private var lastCoalCreditAt: Date?

    public init(kind: Kind = .campfire, scale: FireScale) {
        self.kind = kind
        self.scale = scale
        self.coalDepth = 0
        self.lastFuelAt = nil
        self.restartAt = nil
        self.stateAtLastFuel = .catching
        self.recentFuel = []
        self.lastCoalCreditAt = nil
    }

    /// Rebuilding a fire from the backend's stored row (sync). The engine's
    /// own feeding rules produced these values on some device; this restores
    /// them without re-deriving. `banked` is not restorable state — banking
    /// lives on quiet days, never on the fire — so it clamps to catching.
    public init(
        kind: Kind = .campfire, scale: FireScale, coalDepth: Double,
        lastFuelAt: Date?, restartAt: Date?, stateAtLastFuel: FireState,
        recentFuel: [FuelEvent] = []
    ) {
        self.kind = kind
        self.scale = scale
        self.coalDepth = min(1, max(0, coalDepth))
        self.lastFuelAt = lastFuelAt
        self.restartAt = restartAt
        self.stateAtLastFuel = stateAtLastFuel == .banked ? .catching : stateAtLastFuel
        self.recentFuel = recentFuel
        self.lastCoalCreditAt = lastFuelAt
    }

    // MARK: - Reading the fire

    /// The state a glance reports. `bankedIntervals` are the merged banked
    /// spans from the room's quiet days — see `QuietDay.bankedInterval`.
    public func state(
        at now: Date,
        bankedIntervals: [DateInterval] = [],
        tuning: FireTuning = .standard
    ) -> FireState {
        // Banked wins while any quiet day covers this moment. Banking is an
        // act performed for the room; it is never inferred.
        if bankedIntervals.contains(where: { $0.contains(now) }) {
            return .banked
        }
        guard let lastFuelAt else { return .catching }

        // Quiet days pause subsiding: elapsed time excludes banked spans.
        let hours = Self.effectiveHours(from: lastFuelAt, to: now, excluding: bankedIntervals)

        if hours <= tuning.fuelWindowHours {
            return stateAtLastFuel
        }
        // Subsiding is time. It never goes out, never resets, never says why.
        switch stateAtLastFuel {
        case .steady:
            return hours <= tuning.steadyRestsAtHours ? .burning : .catching
        case .burning:
            return hours <= tuning.burningRestsAtHours ? .burning : .catching
        case .catching, .banked:
            return .catching
        }
    }

    // MARK: - Feeding the fire

    /// Record that a member read. Call on meaningful reading activity; the
    /// per-person throttle makes over-calling harmless.
    public mutating func feed(
        by personID: UUID,
        at now: Date,
        bankedIntervals: [DateInterval] = [],
        tuning: FireTuning = .standard
    ) {
        // Collapse repeat feedings from one person inside the throttle.
        if let last = recentFuel.last(where: { $0.personID == personID }),
           now.timeIntervalSince(last.at) < tuning.perPersonThrottleMinutes * 60 {
            deepenCoals(at: now, tuning: tuning)
            return
        }

        let quietSince: Double
        if let lastFuelAt {
            quietSince = Self.effectiveHours(from: lastFuelAt, to: now, excluding: bankedIntervals)
        } else {
            quietSince = .infinity
        }

        prune(before: now, tuning: tuning)
        recentFuel.append(FuelEvent(personID: personID, at: now))

        if quietSince > tuning.longQuietHours {
            // First light, or fuel after a long quiet: the fire catches.
            stateAtLastFuel = .catching
            restartAt = now
            recentFuel = [FuelEvent(personID: personID, at: now)]
        } else {
            let distinctFeeders = Set(recentFuel.map(\.personID)).count
            if distinctFeeders >= 2 {
                // Air is overlap: two or more feeding within the window
                // lifts the fire to steady.
                stateAtLastFuel = .steady
                restartAt = nil
            } else if let restartAt,
                      now.timeIntervalSince(restartAt) >= tuning.restartPromotionMinutes * 60 {
                stateAtLastFuel = .burning
                self.restartAt = nil
            } else if restartAt != nil {
                stateAtLastFuel = .catching
            } else {
                stateAtLastFuel = .burning
            }
        }

        lastFuelAt = now
        deepenCoals(at: now, tuning: tuning)
    }

    private mutating func deepenCoals(at now: Date, tuning: FireTuning) {
        if let last = lastCoalCreditAt,
           now.timeIntervalSince(last) < tuning.coalCreditHours * 3600 {
            return
        }
        // Asymptotic: the bed deepens forever without ever filling — there
        // is no "full," so there is nothing to compute progress against.
        coalDepth += (1 - coalDepth) * 0.055
        lastCoalCreditAt = now
    }

    private mutating func prune(before now: Date, tuning: FireTuning) {
        let cutoff = now.addingTimeInterval(-tuning.fuelWindowHours * 3600)
        recentFuel.removeAll { $0.at < cutoff }
    }

    // MARK: - Time, minus banked spans

    static func effectiveHours(
        from start: Date, to end: Date, excluding intervals: [DateInterval]
    ) -> Double {
        guard end > start else { return 0 }
        let whole = DateInterval(start: start, end: end)
        var excluded: TimeInterval = 0
        for interval in Self.merged(intervals) {
            if let overlap = whole.intersection(with: interval) {
                excluded += overlap.duration
            }
        }
        return (whole.duration - excluded) / 3600
    }

    static func merged(_ intervals: [DateInterval]) -> [DateInterval] {
        let sorted = intervals.sorted { $0.start < $1.start }
        var result: [DateInterval] = []
        for interval in sorted {
            if let last = result.last, interval.start <= last.end {
                let end = max(last.end, interval.end)
                result[result.count - 1] = DateInterval(start: last.start, end: end)
            } else {
                result.append(interval)
            }
        }
        return result
    }
}

public extension Collection<QuietDay> {
    /// The merged banked spans for a room's quiet days.
    var bankedIntervals: [DateInterval] {
        Handiwork.merged(compactMap(\.bankedInterval))
    }
}
