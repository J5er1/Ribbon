import Foundation
import RibbonCore

// Presence (§4.2): the live signal is ephemeral and socket-only. Nothing
// here persists — the roster is what the socket says right now, and when
// the socket is gone, absence is the honest rendering of absence.

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
}

@MainActor
protocol PresenceService: AnyObject {
    /// Join a room's presence channel. Reading quietly joins nothing —
    /// others see nothing at all.
    func join(roomID: UUID, person: Person) async
    func leave() async
    func update(position: VerseAddress?, scrollFraction: Double, isIdle: Bool) async
    /// The contentless signal (§4.3). Repeats inside a few minutes collapse
    /// into one delivery.
    func sendThinkingOfYou(to personID: UUID) async
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

    func join(roomID: UUID, person: Person) async {}
    func leave() async {}
    func update(position: VerseAddress?, scrollFraction: Double, isIdle: Bool) async {}
    func sendThinkingOfYou(to personID: UUID) async {}
}
