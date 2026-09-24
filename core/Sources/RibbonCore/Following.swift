import Foundation

// Following (build book §4.2): "Your scroll is theirs."
//
// A follower's phone hears where the person they follow is reading only now
// and then — when a scroll of theirs comes to rest, a sample a second while
// they scroll, and a keepalive when nothing moves. People read a still
// screen top to bottom and scroll a few lines at a time, so between those
// words their eyes are somewhere further down than the last one said. This
// file is the guess at where: the last word, carried on at the reader's own
// pace, and never further than the scroll they would make next — nobody
// reads past the place they would have scrolled from.
//
// It measures in words, not points. Two phones set the same chapter at
// different widths and sizes; a word is the same word on both.
//
// §13 — the pace this learns is a reading speed, and a reading speed is
// exactly what the product never collects. It is inferred here, on the
// follower's phone, held in memory for as long as the follow lasts, and
// never persisted, sent, logged or shown. Nothing in this file may ever
// surface it: there is no number anywhere in following.

/// Finer than a verse: the verse, and how far through it the line is —
/// 0 at its first line, 1 where the next verse begins.
public struct ReadingPoint: Codable, Hashable, Sendable {
    public var chapter: Int
    public var verse: Int
    public var part: Double

    public init(chapter: Int, verse: Int, part: Double = 0) {
        self.chapter = chapter
        self.verse = verse
        self.part = part
    }
}

/// How long each verse of a chapter is, in words — the ruler a guess is
/// measured along. A psalm's title comes before its first verse and is not
/// on it: nobody is "in" a title for long, and a presence line never says so.
public struct ChapterRuler: Hashable, Sendable {
    public let chapter: Int
    /// The verses in page order.
    public let verses: [Int]
    /// Each verse's length in words, never less than one.
    public let words: [Int]
    private let starts: [Double]

    public init(chapter: Int, verses: [Int], words: [Int]) {
        let count = min(verses.count, words.count)
        self.chapter = chapter
        self.verses = Array(verses.prefix(count))
        self.words = words.prefix(count).map { max(1, $0) }
        var starts: [Double] = []
        var total = 0.0
        for length in self.words {
            starts.append(total)
            total += Double(length)
        }
        self.starts = starts
    }

    /// One pass over the chapter's blocks: a span with a verse number
    /// begins that verse, and every span after it belongs to it until the
    /// next. Words are counted the way the book table counts them — split on
    /// whitespace. Nil for a chapter with no verses in it.
    public init?(measuring chapter: ScriptureChapter) {
        var verses: [Int] = []
        var words: [Int] = []
        var current: Int?
        for block in chapter.blocks {
            for span in block.x {
                if let v = span.v {
                    // A verse the page comes back to is still that verse.
                    if let index = verses.firstIndex(of: v) {
                        current = index
                    } else {
                        verses.append(v)
                        words.append(0)
                        current = verses.count - 1
                    }
                }
                guard let current else { continue }
                words[current] += span.t.split(whereSeparator: \.isWhitespace).count
            }
        }
        guard !verses.isEmpty else { return nil }
        self.init(chapter: chapter.n, verses: verses, words: words)
    }

    /// Every word in the chapter.
    public var length: Double {
        guard let last = starts.last, let words = words.last else { return 0 }
        return last + Double(words)
    }

    /// Words from the head of the chapter to the point. A verse this
    /// version leaves out is where the verse before it ends.
    public func offset(of point: ReadingPoint) -> Double {
        if let index = verses.firstIndex(of: point.verse) {
            return starts[index] + min(max(point.part, 0), 1) * Double(words[index])
        }
        guard let before = verses.lastIndex(where: { $0 < point.verse }) else { return 0 }
        return starts[before] + Double(words[before])
    }

    /// The point so many words into the chapter, held inside it.
    public func point(at offset: Double) -> ReadingPoint {
        guard !verses.isEmpty else { return ReadingPoint(chapter: chapter, verse: 1) }
        let clamped = min(max(offset, 0), length)
        let index = starts.lastIndex(where: { $0 <= clamped }) ?? 0
        let part = min(1, (clamped - starts[index]) / Double(words[index]))
        return ReadingPoint(chapter: chapter, verse: verses[index], part: part)
    }
}


/// One word from the phone of the person followed: where their reading line
/// is, how far down their own screen goes, and whether they had stopped.
/// Stamped when it arrived here — nothing on the wire carries a time.
public struct ReadingReport: Hashable, Sendable {
    public var at: ReadingPoint
    /// The last thing on their screen, if they said.
    public var end: ReadingPoint?
    /// Sent because the scroll came to rest, rather than in the middle of it.
    public var settled: Bool
    /// Their page was being carried by a follow of their own: it is where
    /// the page is, not where they read to, and nothing is guessed from it.
    public var carried: Bool
    public var received: Date

    public init(
        at: ReadingPoint, end: ReadingPoint? = nil, settled: Bool, carried: Bool = false, received: Date
    ) {
        self.at = at
        self.end = end
        self.settled = settled
        self.carried = carried
        self.received = received
    }
}

/// Internal mechanics, never surfaced.
public struct FollowingTuning: Hashable, Sendable {
    /// The pace a guess starts from, in words a second: a little under the
    /// 238 a minute adults read at silently (Brysbaert 2019), and over the
    /// 183 of reading aloud.
    public var startingPace: Double = 3.6
    /// How much a sitting's evidence has to outweigh the starting pace, as
    /// seconds of reading it counts for.
    public var startingWeight: Double = 45
    public var slowestPace: Double = 1.2
    public var fastestPace: Double = 9
    /// A pace drifts over a sitting; older evidence fades on this scale.
    public var paceMemory: TimeInterval = 240
    /// Two rests closer together than this are a fidget, not reading.
    public var shortestStretch: TimeInterval = 3
    /// More words than this between two rests is going somewhere, not
    /// reading there.
    public var longestStretch: Double = 400
    /// A stretch read slower or faster than these multiples of the pace so
    /// far was a pause or a skim, and says nothing about the pace.
    public var slowerThan: Double = 0.3
    public var fasterThan: Double = 3
    /// Nobody finishes the very last line their screen shows.
    public var endMargin: Double = 3
    /// A guess runs on no further than this share of the scroll they
    /// usually make. People scroll when their eyes near the bottom of the
    /// part of the screen they like to read in, so the next scroll is the
    /// honest limit — and a guess that runs on to the bottom of the screen
    /// during a pause is a page carried past them, then brought back.
    public var shareOfTheirScroll: Double = 0.75
    /// Before a scroll of theirs has been seen: this share of what their
    /// screen shows below the reading line.
    public var shareOfTheirScreen: Double = 0.5
    /// How much each new scroll counts toward the one they usually make.
    public var scrollMemory: Double = 0.3
    /// How far past their line a guess may run when they didn't say where
    /// their screen ends (an older app): about two verses.
    public var leadWithoutEnd: Double = 60
    /// The reading line sits under the top third of a screen, so what a
    /// screen shows below it is this share of the whole.
    public var belowTheLine: Double = 0.7
    /// A screen, in words, when they didn't say where theirs ends.
    public var screenWithoutEnd: Double = 100
    /// A report this share of their screen behind where they last came to
    /// rest is them going back, not a scroll catching up.
    public var goingBack: Double = 0.2
    /// Close enough to the same place to be the same place.
    public var samePart: Double = 0.02

    public init() {}
}

/// The guess at where someone is reading, between the words their phone
/// sends. A value: the page that follows holds one for as long as the
/// follow lasts, and throws it away with it.
public struct ReadingEstimate: Hashable, Sendable {
    public let tuning: FollowingTuning
    /// Their pace, in words a second — in memory only (§13).
    public private(set) var pace: Double
    /// When a report last put them well behind where they had come to rest
    /// — a look back, which is theirs to make and the page's to follow.
    public private(set) var wentBackAt: Date?
    /// The latest word, which the guess runs on from.
    private var latest: ReadingReport?
    /// The latest word sent at rest and not carried, which a pace is
    /// learned against.
    private var lastRest: ReadingReport?
    /// The guess as it stood when a scroll of theirs was first seen in
    /// flight. The first sample of a scroll is where the last one ended,
    /// behind a guess that has been reading on since; it must not pull the
    /// guess back.
    private var beforeTheScroll: ReadingPoint?
    /// The size of the scroll they usually make, in words.
    private var usualScroll: Double?
    /// Where they were when they went still, if they have.
    private var heldAt: ReadingPoint?
    private var paceSeconds: Double = 0
    private var paceWords: Double = 0

    public init(tuning: FollowingTuning = FollowingTuning()) {
        self.tuning = tuning
        self.pace = tuning.startingPace
    }

    /// The place their phone last reported — their line itself, not the
    /// guess run on from it.
    public var reported: ReadingPoint? { latest?.at }

    /// Take a new word from their phone.
    public mutating func observe(_ report: ReadingReport, rulers: (Int) -> ChapterRuler?) {
        // Two roads bring words — the reading line and presence — and an
        // older word arriving second is not where they are now.
        if let latest, report.received < latest.received { return }
        if report.carried {
            latest = report
            beforeTheScroll = nil
            heldAt = nil
            return
        }
        if var latest, !latest.carried, report.settled, same(report.at, latest.at) {
            if latest.settled {
                // A repeat is not news. Someone reading down a still
                // screen sends the same place every so often, and taking
                // it as a new start would pull the guess back to the top
                // of what they are reading. Only the bottom of their
                // screen may have moved — a note opened, a size changed.
                if let end = report.end { latest.end = end }
                self.latest = latest
            } else {
                // A scroll seen in flight has come to rest here: reading
                // on from it starts now.
                if let rest = lastRest { learn(from: rest, to: report, rulers: rulers) }
                lastRest = report
                self.latest = report
                beforeTheScroll = nil
            }
            return
        }
        var goingBack = false
        if let rest = lastRest,
           let back = distance(from: rest.at, to: report.at, rulers: rulers),
           back < -tuning.goingBack * screen(of: rest, rulers: rulers) {
            wentBackAt = report.received
            goingBack = true
        }
        if !report.settled {
            if goingBack {
                beforeTheScroll = nil
            } else if latest.map({ $0.settled || $0.carried }) ?? false {
                beforeTheScroll = point(at: report.received, rulers: rulers)
            }
        }
        if report.settled {
            if let rest = lastRest { learn(from: rest, to: report, rulers: rulers) }
            lastRest = report
            beforeTheScroll = nil
        }
        latest = report
        heldAt = nil
    }

    /// They have gone still (§4.2's "here, but still"): the guess stops
    /// where it is until they move again.
    public mutating func hold(at now: Date, rulers: (Int) -> ChapterRuler?) {
        guard heldAt == nil else { return }
        heldAt = point(at: now, rulers: rulers)
    }

    /// Where they are most likely reading now. Nil before any word.
    public func point(at now: Date, rulers: (Int) -> ChapterRuler?) -> ReadingPoint? {
        guard let latest else { return nil }
        if let heldAt { return heldAt }
        if latest.carried { return latest.at }
        if !latest.settled {
            // In the middle of a scroll the page is where it is — unless
            // it is behind the guess and they are not going back, in which
            // case it is a scroll catching up with where they already are.
            guard let before = beforeTheScroll,
                  let ahead = distance(from: before, to: latest.at, rulers: rulers),
                  ahead < 0
            else { return latest.at }
            return before
        }
        // Without the chapter's words there is nothing to run on with.
        guard rulers(latest.at.chapter) != nil else { return latest.at }
        let elapsed = max(0, now.timeIntervalSince(latest.received))
        var room = tuning.leadWithoutEnd
        if let end = latest.end, let span = distance(from: latest.at, to: end, rulers: rulers) {
            let scroll = usualScroll ?? tuning.shareOfTheirScreen * span
            room = max(0, min(span - tuning.endMargin, tuning.shareOfTheirScroll * scroll))
        }
        return advance(latest.at, by: min(pace * elapsed, room), rulers: rulers)
    }

    // MARK: -

    private func same(_ a: ReadingPoint, _ b: ReadingPoint) -> Bool {
        a.chapter == b.chapter && a.verse == b.verse && abs(a.part - b.part) < tuning.samePart
    }

    /// Their whole screen, in words, from what a rest said about it.
    private func screen(of rest: ReadingReport, rulers: (Int) -> ChapterRuler?) -> Double {
        guard let end = rest.end, let below = distance(from: rest.at, to: end, rulers: rulers), below > 0
        else { return tuning.screenWithoutEnd }
        return below / tuning.belowTheLine
    }

    /// A stretch between two rests is reading if it went forward, not too
    /// far, and took long enough to mean something. Then it says how far
    /// they usually scroll — a pause before it included — and, if it went
    /// at a pace plausibly the same person's, how fast they read. Anything
    /// else — a fidget, a skim, a jump, a look back — says nothing.
    private mutating func learn(from start: ReadingReport, to end: ReadingReport, rulers: (Int) -> ChapterRuler?) {
        let seconds = end.received.timeIntervalSince(start.received)
        guard seconds >= tuning.shortestStretch,
              let words = distance(from: start.at, to: end.at, rulers: rulers),
              words > 0, words <= tuning.longestStretch
        else { return }
        // A reading scroll keeps some of what was read on screen; more
        // than a screen at once is going somewhere.
        if words <= screen(of: start, rulers: rulers) {
            usualScroll = usualScroll.map { $0 + tuning.scrollMemory * (words - $0) } ?? words
        }
        let rate = words / seconds
        guard rate >= pace * tuning.slowerThan, rate <= pace * tuning.fasterThan else { return }
        let fade = exp(-seconds / tuning.paceMemory)
        paceSeconds = paceSeconds * fade + seconds
        paceWords = paceWords * fade + words
        let learned = (paceWords + tuning.startingPace * tuning.startingWeight)
            / (paceSeconds + tuning.startingWeight)
        pace = min(max(learned, tuning.slowestPace), tuning.fastestPace)
    }

    /// Words from one point to another, when both chapters are measured and
    /// no further apart than neighbours.
    private func distance(from a: ReadingPoint, to b: ReadingPoint, rulers: (Int) -> ChapterRuler?) -> Double? {
        guard let from = rulers(a.chapter) else { return nil }
        if a.chapter == b.chapter {
            return from.offset(of: b) - from.offset(of: a)
        }
        if b.chapter == a.chapter + 1, let to = rulers(b.chapter) {
            return from.length - from.offset(of: a) + to.offset(of: b)
        }
        if b.chapter == a.chapter - 1, let to = rulers(b.chapter) {
            return -(to.length - to.offset(of: b) + from.offset(of: a))
        }
        return nil
    }

    /// So many words on from a point, into the next chapter when it is
    /// measured, and otherwise to the end of this one.
    private func advance(_ point: ReadingPoint, by words: Double, rulers: (Int) -> ChapterRuler?) -> ReadingPoint {
        guard let ruler = rulers(point.chapter) else { return point }
        let offset = ruler.offset(of: point) + words
        if offset > ruler.length, let next = rulers(point.chapter + 1) {
            return next.point(at: offset - ruler.length)
        }
        return ruler.point(at: offset)
    }
}

/// What the page that follows does next.
public enum FollowMove: Hashable, Sendable {
    /// Stay still: they are reading somewhere on this screen.
    case hold
    /// Move the page by this much, in the page's own units — down the page
    /// when positive.
    case step(by: Double)
    /// They are somewhere this page hasn't set out yet: go there.
    case fly
}

/// A page that follows is moved the way the person followed moves their
/// own: held still while they read down it, then carried several lines at
/// once when the guess leaves the upper half — "your scroll is theirs"
/// (§4.2). Still text read in steps beats text that glides (Kolers 1981;
/// Öquist & Lundin 2007), and a person's own page is still between their
/// scrolls.
public enum FollowCarriage {
    /// How the page may move for the person reading it.
    public enum Manner: Hashable, Sendable {
        /// Steps, easing.
        case moving
        /// Reduce motion: fewer, larger steps, each a fade rather than a
        /// travel (§11).
        case calm
        /// A screen reader is speaking the page: it moves only when their
        /// line has left the screen, so the voice is never pulled out from
        /// under the listener.
        case spoken
    }

    /// The line a guess is brought to: a little above the upper third a
    /// page reads its own place from, so a step shows what is coming.
    public static let landingLine = 0.25
    /// A guess further down the screen than this is carried up.
    public static let stepLine = 0.55
    /// Under reduce motion, further down still.
    public static let calmStepLine = 0.75
    /// A guess higher than this — they went back — is brought down.
    public static let topLine = 0.08
    /// No step lifts their own line above this: the page never runs ahead
    /// of what their phone actually said.
    public static let reportedLine = 0.08

    /// - Parameters:
    ///   - y: The guess's height on this screen, from the top of the
    ///     viewport, in the page's own units; nil when this page hasn't
    ///     laid out the place yet.
    ///   - reported: The height of the line their phone last reported, in
    ///     the same units; nil when it isn't laid out or came from an older
    ///     app's presence, which is always a scroll behind.
    ///   - viewport: The viewport's height, in the same units.
    ///   - minStep: A step smaller than this is not worth taking.
    ///   - realign: Bring the guess to the landing line even from inside
    ///     the band — the first move of a follow.
    ///   - wentBack: They have gone back since the page last moved.
    public static func move(
        y: Double?, reported: Double? = nil, viewport: Double, minStep: Double = 0,
        realign: Bool = false, wentBack: Bool = false, manner: Manner = .moving
    ) -> FollowMove {
        guard viewport > 0 else { return .fly }
        if manner == .spoken {
            guard let line = reported ?? y else { return .fly }
            if line >= 0, line <= viewport, !realign { return .hold }
            let distance = line - landingLine * viewport
            return abs(distance) < 1 ? .hold : .step(by: distance)
        }
        guard let y else { return .fly }
        let distance = y - landingLine * viewport
        if realign {
            return abs(distance) < 1 ? .hold : .step(by: distance)
        }
        let stepAt = manner == .calm ? calmStepLine : stepLine
        if y > stepAt * viewport {
            var step = distance
            if let reported { step = min(step, reported - reportedLine * viewport) }
            return step < max(minStep, 1) ? .hold : .step(by: step)
        }
        if y < topLine * viewport {
            // The guess never goes back on its own; only a report does. A
            // report behind a guess that ran on is the guess being wrong,
            // and turning the page back for it is the overshoot §9.1
            // forbids (I30). Their going back, or their line having left
            // the top of the screen, is theirs.
            guard wentBack || reported.map({ $0 < 0 }) ?? true else { return .hold }
            return .step(by: distance)
        }
        return .hold
    }
}
