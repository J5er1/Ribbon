import Foundation

/// How often a sign-in code may be asked for (§6.10).
///
/// The same window the backend keeps, kept locally so the app can say what
/// is happening instead of firing a request it knows will be refused. The
/// server is the enforcement — this is only the phone being honest about
/// what it already knows.
///
/// Two limits, because they do different jobs:
///
/// - **A minute between codes.** GoTrue's own `auth.email.max_frequency`.
///   It stops the double-tap and the impatient re-tap, which is most of the
///   traffic a resend button ever generates.
/// - **Six an hour.** The send-email hook's per-address ceiling. This is
///   the one that means a stranger typing your address into Ribbon cannot
///   bury your inbox, and it is why the hook exists at all — Supabase's own
///   limits are per project, so without it one address could burn everyone
///   else's allowance.
///
/// The hourly window deliberately slides from the oldest live send rather
/// than resetting on a fixed schedule, which lands on exactly the same
/// instant the server's window reopens: the server's window starts at the
/// first send in it, so "the oldest send falls out of the hour" and "the
/// server's window expires" are the same moment.
public struct SignInSendWindow: Equatable, Sendable {

    /// The floor between two codes.
    public static let minimumInterval: TimeInterval = 60
    /// The ceiling inside one window.
    public static let maxInWindow: Int = 6
    /// How long the window is.
    public static let windowLength: TimeInterval = 3600

    /// When codes were asked for, oldest first, pruned to the window.
    public private(set) var sends: [Date]

    /// A refusal the server handed back, which outranks the local model —
    /// another phone may have spent the window, or this one may have been
    /// asleep through part of it.
    public private(set) var serverHold: Date?

    public init(sends: [Date] = [], serverHold: Date? = nil) {
        self.sends = sends.sorted()
        self.serverHold = serverHold
    }

    /// Records that a code was asked for.
    public mutating func record(at moment: Date) {
        sends.append(moment)
        sends.sort()
        prune(now: moment)
    }

    /// Absorbs a server refusal — the `Retry-After` on a 429.
    public mutating func hold(until moment: Date) {
        if let existing = serverHold, existing > moment { return }
        serverHold = moment
    }

    /// The moment a code may next be asked for, or nil if that moment is
    /// now.
    public func nextSendAllowed(now: Date) -> Date? {
        // The most restrictive constraint wins, so this keeps the latest of
        // the candidates rather than the earliest.
        var notBefore: Date?

        func atLeast(_ moment: Date) {
            guard moment > now else { return }
            if let current = notBefore, current >= moment { return }
            notBefore = moment
        }

        if let serverHold { atLeast(serverHold) }

        let live = sends.filter { now.timeIntervalSince($0) < Self.windowLength }

        if let last = live.last {
            atLeast(last.addingTimeInterval(Self.minimumInterval))
        }
        if live.count >= Self.maxInWindow, let oldest = live.first {
            atLeast(oldest.addingTimeInterval(Self.windowLength))
        }

        return notBefore
    }

    /// Whether a code may be asked for right now.
    public func maySend(now: Date) -> Bool {
        nextSendAllowed(now: now) == nil
    }

    /// Whole seconds until the next code may be asked for; 0 when it may be
    /// asked for now. Rounded up, so a countdown never shows a number the
    /// button will not honour.
    public func secondsUntilNextSend(now: Date) -> Int {
        guard let next = nextSendAllowed(now: now) else { return 0 }
        return max(0, Int(next.timeIntervalSince(now).rounded(.up)))
    }

    /// True when the hourly ceiling is what is holding, rather than the
    /// minute between codes — a different thing to say to the reader.
    public func isHourlyLimit(now: Date) -> Bool {
        let live = sends.filter { now.timeIntervalSince($0) < Self.windowLength }
        return live.count >= Self.maxInWindow && !maySend(now: now)
    }

    private mutating func prune(now: Date) {
        sends.removeAll { now.timeIntervalSince($0) >= Self.windowLength }
        if let hold = serverHold, hold <= now { serverHold = nil }
    }
}
