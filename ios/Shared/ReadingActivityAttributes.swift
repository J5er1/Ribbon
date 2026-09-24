import ActivityKit
import Foundation

// "Ruth is reading Mark" (S24) — the Live Activity's shape, compiled into
// the app and into its widgets.
//
// The server starts it by push (supabase/functions/push), so the names here
// are a wire format as much as a type: the push's `attributes-type` names
// this struct, and its `attributes` and `content-state` are these fields,
// decoded with the system's default coders. Change one and change the other.

struct ReadingActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        /// The book they are in. Content rather than an attribute, because a
        /// room can start its next book while somebody is still reading.
        var book: String
    }

    var roomID: String
    var readerID: String
    /// Their first name, the way the room says it in passing.
    var readerName: String
}
