import Foundation

// Quiet hours, drawn as the night (A67). S19 set the two ends with two
// wheels, one under each row — the most borrowed-looking thing in the app.
// They are one band now: a day laid out from noon to noon, so that a night
// sits whole in the middle of it and never breaks at an edge, with the
// quiet stretch banked and a handle at each end of it.
//
// This file is the band's arithmetic and nothing else: where a minute of
// the day sits on it, which minute a finger is over, how far one step moves
// a handle, and which stretch to draw. Kept here so that both phones put a
// handle on the same place for the same time, and a test can say so.

/// A stretch of the band, as fractions of its width from its left edge.
public struct QuietSpan: Equatable, Hashable, Sendable {
    public var from: Double
    public var to: Double

    public init(from: Double, to: Double) {
        self.from = from
        self.to = to
    }
}

public enum QuietHoursBand {
    /// Minutes in a day.
    public static let day = 24 * 60
    /// Where the band begins: noon, so a night is whole in its middle.
    public static let origin = 12 * 60
    /// How far a handle moves: a quarter of an hour, by a finger and by a
    /// screen reader's swipe alike.
    public static let step = 15
    /// The hours marked under the band, left to right: six in the evening,
    /// midnight, six in the morning. A mark, not a scale — three times a
    /// person already knows where they are in a night.
    public static let marks = [18 * 60, 0, 6 * 60]

    /// Where a minute of the day sits on the band: 0 is noon, ½ is
    /// midnight, and the band ends just short of 1, a minute before the
    /// next noon.
    public static func position(of minute: Int) -> Double {
        Double(wrapped(minute - origin)) / Double(day)
    }

    /// The minute of the day under a point on the band, to the nearest
    /// step. A point past either end is held at that end. The far end is
    /// noon again, so it is read as the last step before it: a handle drawn
    /// hard to the right stops at a quarter to twelve rather than jumping
    /// back to the left.
    public static func minute(at position: Double, step: Int = QuietHoursBand.step) -> Int {
        let held = min(max(position, 0), 1)
        var minutes = Int((held * Double(day) / Double(step)).rounded()) * step
        if minutes >= day { minutes = day - step }
        return wrapped(origin + minutes)
    }

    /// A minute moved by whole steps along the band: a screen reader's
    /// increment and decrement. A time between steps lands on the nearest
    /// one first, so every move after it is a whole step. Like a finger, a
    /// step stops at the band's ends — noon on the left, a quarter to
    /// twelve on the right — rather than jumping from one to the other.
    public static func stepped(_ minute: Int, by steps: Int, step: Int = QuietHoursBand.step) -> Int {
        let last = day / step - 1
        let along = Int((Double(wrapped(minute - origin)) / Double(step)).rounded())
        let moved = min(max(along + steps, 0), last)
        return wrapped(origin + moved * step)
    }

    /// The stretches of the band to draw as quiet, left to right in the
    /// order they are reached from the start. One for a night. Two when the
    /// quiet hours reach across noon — the end of the band and its start.
    /// None when the two ends are the same minute, which is no quiet hours
    /// at all (S19, and `AppSettings.isQuiet`).
    public static func spans(start: Int, end: Int) -> [QuietSpan] {
        if wrapped(start) == wrapped(end) { return [] }
        let from = position(of: start)
        let to = position(of: end)
        if from < to { return [QuietSpan(from: from, to: to)] }
        return [QuietSpan(from: from, to: 1), QuietSpan(from: 0, to: to)].filter { $0.to > $0.from }
    }

    /// Any whole number of minutes, as a minute of one day.
    public static func wrapped(_ minute: Int) -> Int {
        ((minute % day) + day) % day
    }
}
