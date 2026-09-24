import SwiftUI
import RibbonCore

// Following, on the page (§4.2): "Your scroll is theirs."
//
// The phone being followed says where its reading line is — the verse, how
// far through it, and the last thing on its screen. The phone following
// turns that into a place on its own page, which is set at its own width
// and its own size, and moves there the way the other person moves their
// own: held still while they read down the screen, carried several lines
// at once when they have read on. RibbonCore makes the guess and decides
// the move; this file is the part that has to know what a page looks like.

/// Where a line falls on a typeset chapter — the one measure both phones
/// take, so that a word said by one lands on the same word on the other.
///
/// Every height here is in the chapter's own space: 0 at its top, as
/// `ChapterLayout.verseFirstLineY` has it.
///
/// There is no test target for the app, so the cases are written down
/// here instead. A line exactly on a verse's first line is that verse at
/// part 0. Verses that begin on the same line (a paragraph of prose often
/// starts two or three) are the last of them, and the next verse is the
/// first one with a line strictly lower — never a verse on the same line,
/// which would divide by nothing. Two lines closer than a point apart are
/// part 0 rather than a division by a sliver. The last verse runs to the
/// foot of the chapter. A line above the first verse — in the running head
/// — is the first verse at part 0.
enum PagePoint {
    /// The point a line `y` down a chapter falls on. Nil for a chapter with
    /// no verses laid out.
    static func at(_ y: CGFloat, chapter: Int, in layout: ChapterLayout) -> ReadingPoint? {
        let lines = inPageOrder(layout)
        guard let first = lines.first, y.isFinite else { return nil }
        guard let here = lines.last(where: { $0.y <= y }) else {
            return ReadingPoint(chapter: chapter, verse: first.verse, part: 0)
        }
        let next = lines.first(where: { $0.y > here.y })?.y ?? layout.height
        let gap = Double(next - here.y)
        let part = gap < 1 ? 0 : Double(y - here.y) / gap
        return ReadingPoint(chapter: chapter, verse: here.verse, part: clamped(part))
    }

    /// The chapter read to its end: where a line resting on the passage end
    /// below it — or on the finishing, below the last — is.
    static func end(of chapter: Int, in layout: ChapterLayout) -> ReadingPoint? {
        inPageOrder(layout).last.map { ReadingPoint(chapter: chapter, verse: $0.verse, part: 1) }
    }

    /// How far down its chapter a point sits: the verse's first line, and
    /// the part of the way to the next line below it. A verse this version
    /// leaves out is where the one before it ends.
    static func y(of point: ReadingPoint, in layout: ChapterLayout) -> CGFloat? {
        let lines = inPageOrder(layout)
        guard let first = lines.first else { return nil }
        let top: CGFloat
        var part = CGFloat(clamped(point.part))
        if let y = layout.verseFirstLineY[point.verse] {
            top = y
        } else if let before = lines.filter({ $0.verse < point.verse }).max(by: { $0.verse < $1.verse }) {
            top = before.y
            part = 1
        } else {
            return first.y
        }
        let next = lines.first(where: { $0.y > top })?.y ?? max(layout.height, top)
        return top + part * (next - top)
    }

    /// Near enough the same place to be the same place — the measure the
    /// guess uses for a repeat.
    static func same(_ a: ReadingPoint, _ b: ReadingPoint?) -> Bool {
        guard let b else { return false }
        return a.chapter == b.chapter && a.verse == b.verse && abs(a.part - b.part) < 0.02
    }

    /// Finite and inside [0, 1]. A NaN is caught before it is clamped:
    /// clamped first, it stays a NaN.
    static func clamped(_ part: Double) -> Double {
        part.isFinite ? min(1, max(0, part)) : 0
    }

    /// Each verse and its first line, top to bottom — and, for verses that
    /// begin on the same line, in the order they are numbered.
    private static func inPageOrder(_ layout: ChapterLayout) -> [(verse: Int, y: CGFloat)] {
        layout.verseFirstLineY
            .map { (verse: $0.key, y: $0.value) }
            .sorted { $0.y == $1.y ? $0.verse < $1.verse : $0.y < $1.y }
    }
}

/// The follow's mark: a point in a chapter for the scroll view to aim at,
/// as the landing has its own. Placed so that putting it at the top of the
/// screen moves the page exactly as far as the step asks — or, for the
/// rubber band, brings it back exactly where it rested, which can be
/// outside the chapter's own lines (`y` below its foot, or above its head).
struct FollowPlace: Equatable {
    var chapter: Int
    var y: CGFloat
}

/// One move the follow asks the scroll view for. Its own number, so that
/// two identical moves in a row are still two moves.
struct FollowStep: Equatable {
    enum Way: Equatable {
        /// A step, eased.
        case ease
        /// A step under reduce motion: the text fades, is there, and comes
        /// back — never a cut, and never a travel (§11).
        case fade
        /// The rubber band letting go: back where the page was resting.
        case back
    }
    var way: Way
    var seq: Int
}

/// What the page keeps between ticks, both as a follower and as someone
/// followed — a reference the body never reads, so writing to it redraws
/// nothing. A tick four times a second costs a tick, not a page (and not
/// the VoiceOver element under the listener's finger).
@MainActor
final class FollowLoopState {
    /// The first gesture of a follow (§4.2's "gentle rubber-band on the
    /// first gesture so it never happens by accident").
    enum Band: Equatable {
        /// Not used yet in this follow.
        case unused
        /// The first gesture, under way: where the page was resting when
        /// it began.
        case held(rest: FollowPlace)
        /// Going back there.
        case returning
        /// Used. The next gesture is the reader's own.
        case spent
    }

    // The scroll, as it last reported itself.
    var phase: ScrollPhase = .idle
    var bottomInset: CGFloat = 0
    /// The bottom chrome — the Wave, the offer back, a composer — which
    /// hides what is under it.
    var chromeHeight: CGFloat = 0
    var viewportMeasured = false
    var isOpen = false
    var panelOpen = false
    /// When a note last opened or closed: its carve moves the lines for
    /// as long as `settle` takes.
    var noteMovedAt = Date.distantPast

    /// A move of ours is under way until this — set as it is asked for and
    /// again, a moment on, once it has landed, so the geometry it causes is
    /// known to be ours when it arrives.
    var movingUntil = Date.distantPast
    /// Where the page was at the last scroll that was accounted for: a
    /// finger's, or ours. A scroll nobody accounts for — VoiceOver's, a
    /// keyboard's, a trackpad's — is measured from here.
    var quietOffset: CGFloat?
    var stepSeq = 0

    var band = Band.unused
    /// Where the page was resting when a finger came down on it — before
    /// the slop a drag has to cross to become one has moved it.
    var restAtTouch: FollowPlace?
    /// The follow whose first move has been made — the one move allowed to
    /// bring the guess to the line from inside the band.
    var realignedEpoch: Int?

    // As someone followed.
    var settleDue: Date?
    var settleTask: Task<Void, Never>?
    var lastInFlight = Date.distantPast
    var lastFraction: Double = 0

    /// The loop running now, for the moves it started to report back to.
    var run: FollowRun?

    var bandInPlay: Bool {
        switch band {
        case .held, .returning: return true
        case .unused, .spent: return false
        }
    }

    func nextStep(_ way: FollowStep.Way) -> FollowStep {
        stepSeq += 1
        return FollowStep(way: way, seq: stepSeq)
    }
}

/// One run of the follow loop: the guess, and what it has learned about
/// the page. Thrown away with the loop — the pace in the guess is a
/// reading speed, and lives only as long as the follow (§13).
@MainActor
final class FollowRun {
    let person: UUID
    var estimate = ReadingEstimate()
    /// Each chapter measured in words, once.
    var rulers: [Int: ChapterRuler] = [:]
    /// The arrival of the last report the guess was given.
    var fed: Date?
    /// The guess is running on from their presence alone.
    var fromPresence = false
    /// When the page last stepped back.
    var backStepAt: Date?
    /// Where the guess stood when a step moved nothing — the foot of the
    /// book, or a page that would not go. Held there until the guess moves
    /// a tenth of a screen or they say something new.
    var stuckAt: CGFloat?
    /// A step on its way: where its chapter stood, and the guess, when it
    /// set off.
    var step: (chapter: Int, top: CGFloat, guess: CGFloat)?
    /// A flight of the follow's own on its way.
    var flying: (chapter: Int, since: Date)?
    /// A chapter a flight could not reach. Not tried again until they say
    /// something new.
    var noFlyTo: Int?

    init(person: UUID) {
        self.person = person
    }

    /// They have left the room. The follow stays, and the guess starts
    /// again from whatever they say when they are back.
    func forget() {
        estimate = ReadingEstimate()
        fed = nil
        fromPresence = false
        stuckAt = nil
        noFlyTo = nil
    }

    /// The rulers the guess can need around a chapter — the one before it
    /// for a look back, and two after for reading on into the next.
    func measure(around chapter: Int, count: Int, text: (Int) -> ScriptureChapter?) -> [Int: ChapterRuler] {
        for n in max(1, chapter - 1)...max(1, chapter + 2) where n <= count && rulers[n] == nil {
            if let chapterText = text(n), let ruler = ChapterRuler(measuring: chapterText) {
                rulers[n] = ruler
            }
        }
        return rulers
    }
}
