import SwiftUI
import WidgetKit

// Ribbon's widgets (S24): the fire on the home screen and the lock screen,
// and "Ruth is reading Mark" while she is.
//
// "No numbers — this is the surface most likely to have a count smuggled
// into it, and it is the one where a count would be seen most often." So
// every view in this extension draws a state, a name, a book, or a face, and
// nothing that answers how much or how often. There is no numeric badge
// anywhere, on any platform, under any circumstance.

@main
struct RibbonWidgets: WidgetBundle {
    var body: some Widget {
        FireWidget()
        ReadingLiveActivity()
    }
}

/// The few words this extension says, in the app's voice (Copy.swift).
enum WidgetCopy {
    static let fireName = "The fire"
    static let fireDescription = "The room's fire, and the book it is reading."
    static func fireIs(_ state: String) -> String { "The fire is \(state)." }
    static func reading(_ name: String, _ book: String) -> String { "\(name) is reading \(book)" }
    /// A Live Activity whose reader's phone stopped saying so: true about
    /// then, and not a claim about now (§4.2).
    static func wasReading(_ name: String, _ book: String) -> String { "\(name) was reading \(book)" }
}

/// The two faces the extension carries, by the names the app uses.
enum WidgetType {
    static func smallCaps(_ size: CGFloat) -> Font { .custom("Alegreya Sans SC", size: size) }
    static func ui(_ size: CGFloat) -> Font { .custom("Alegreya Sans", size: size) }
}

/// ribbon://room/<id>: a tap on a widget opens the room it is about.
func roomURL(_ roomID: String) -> URL? {
    URL(string: "ribbon://room/\(roomID.lowercased())")
}
