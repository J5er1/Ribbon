import Foundation
import RibbonCore

// Presence (§4.2): the live signal is ephemeral and socket-only. Nothing
// here persists — the roster is what the socket says right now, and when
// the socket is gone, absence is the honest rendering of absence.
//
// The channel and the presence on it are two different things, and this is
// the seam where that distinction lives. The channel is the room's: it is
// open whenever you have the room on screen, because it is also how the
// room learns that someone joined it, left a note, or fed the fire without
// waiting for your next launch. Presence is the book's: it is announced
// only while you are actually reading, and never while reading quietly.

/// Someone in the book right now.
struct PresentPerson: Identifiable, Hashable, Sendable {
    var id: UUID
    var name: String
    /// Where they are — "Mark 6" in the expanded panel. An address, never
    /// a percentage.
    var position: VerseAddress?
    /// Scroll offset within their chapter, 0...1 — drives following only.
    var scrollFraction: Double
    /// ~4 minutes with no scroll: "here, but still." Dimmed, never removed.
    var isIdle: Bool
    /// Who they are following, if anyone — how "Ruth is with you" knows
    /// to appear (§4.2). Never a count of followers.
    var followingPersonID: UUID?
}

enum PresenceEvent: Sendable {
    case roster([PresentPerson])
    case thinkingOfYou(fromName: String)
    /// Somebody in this room changed something the room renders from. It
    /// carries no content on purpose — it is a nudge to pull, not a
    /// second, racing copy of the truth.
    case roomChanged(roomID: UUID)
}

@MainActor
protocol PresenceService: AnyObject {
    /// Open the room's channel. Opening it announces nothing: the room
    /// hears you when you are reading, and this is only the line being
    /// live.
    func connect(roomID: UUID, person: Person) async
    /// Close it. Called when the room goes off screen or the app goes away.
    func disconnect() async

    /// Announce yourself in the book, and keep the announcement current.
    /// Idempotent — the first call tracks, every later one updates.
    /// Reading quietly never calls it, so others see nothing at all.
    func present(
        position: VerseAddress?, scrollFraction: Double,
        isIdle: Bool, following: UUID?) async
    /// Stop announcing. The channel stays open — you are still in the
    /// room, you are simply not in the book.
    func withdraw() async

    /// The contentless signal (§4.3). Repeats inside a few minutes collapse
    /// into one delivery.
    func sendThinkingOfYou(to personID: UUID) async

    /// Tell the room that something it renders from has changed, so the
    /// other phones catch up now rather than at their next foreground.
    func announceChange() async

    var events: AsyncStream<PresenceEvent> { get }
}

/// Presence with no server: nobody else is ever here. The form is simply
/// absent, and nothing comments on that. This is the honest local backend,
/// not a simulation.
@MainActor
final class LocalPresenceService: PresenceService {
    let events: AsyncStream<PresenceEvent>
    private let continuation: AsyncStream<PresenceEvent>.Continuation

    init() {
        (events, continuation) = AsyncStream.makeStream(of: PresenceEvent.self)
    }

    func connect(roomID: UUID, person: Person) async {}
    func disconnect() async {}
    func present(
        position: VerseAddress?, scrollFraction: Double,
        isIdle: Bool, following: UUID?) async {}
    func withdraw() async {}
    func sendThinkingOfYou(to personID: UUID) async {}
    func announceChange() async {}
}
