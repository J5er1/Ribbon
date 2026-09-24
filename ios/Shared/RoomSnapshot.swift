import Foundation
import RibbonCore

// What the widgets draw (S24), handed from the app to its extension through
// the app group's container.
//
// The fire is the room's current reading's handiwork, whole — not a state
// frozen at the moment the app last ran. A fire cools on its own, hour by
// hour, and the widget's timeline asks the same engine the room does what
// the state is at each hour, so the home screen reports the fire the room
// would show without the app having to wake to say so. Nothing here counts
// anything: the engine's inputs are a handful of recent feedings, and what
// comes out is a state.

struct RoomSnapshot: Codable, Equatable {
    var roomID: UUID
    /// The open book's name, or nil when the room has none open. A room with
    /// no book open is drawn as the unlit ground and nothing else: an empty
    /// state is a reproach (§08), so the widget is absent rather than empty.
    var book: String?
    var handiwork: Handiwork?
    /// The room's quiet days, as the spans they bank the fire over.
    var banked: [DateInterval] = []

    static let groupID = "group.bible.ribbon.app"

    /// The group's container, or nil on a build without the entitlement —
    /// in which case there is simply nothing to draw from.
    static var container: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupID)
    }

    private static var file: URL? { container?.appending(path: "room.json") }

    static func read() -> RoomSnapshot? {
        guard let file, let data = try? Data(contentsOf: file) else { return nil }
        return try? JSONDecoder().decode(RoomSnapshot.self, from: data)
    }

    /// Whether anything changed; the caller reloads the widgets only then.
    @discardableResult
    func write() -> Bool {
        guard let file = Self.file else { return false }
        if Self.read() == self { return false }
        guard let data = try? JSONEncoder().encode(self) else { return false }
        return (try? data.write(to: file, options: .atomic)) != nil
    }

    static func clear() {
        guard let file else { return }
        try? FileManager.default.removeItem(at: file)
    }

    /// A small copy of a room member's portrait, for the Live Activity —
    /// which runs in the extension and cannot reach the app's own caches.
    static func portraitURL(for personID: String) -> URL? {
        container?.appending(path: "Portraits/\(personID.lowercased()).jpg")
    }
}
