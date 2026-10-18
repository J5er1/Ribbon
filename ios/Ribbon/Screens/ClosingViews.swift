import SwiftUI
import UIKit
import RibbonCore

// Closing a room (§6.8, A73): the shelf handed on as a file, and the rooms
// that have closed, each with its shelf still there to keep.

/// A shelf written to a file, waiting to be handed on.
struct KeptShelf: Identifiable {
    let url: URL
    var id: URL { url }
}

/// The system's share sheet for a file. SwiftUI's ShareLink is a control
/// the person taps; this is the same sheet, opened by the app the moment a
/// room closes, so the shelf is offered then rather than left to be found.
struct ActivitySheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// The rooms that have closed, under your rooms: each one's shelf, to keep.
struct ClosedRoomsSection: View {
    @Environment(AppModel.self) private var model
    var onKeep: (Room) -> Void

    var body: some View {
        let closed = model.closedRooms
        if !closed.isEmpty {
            SettingsGroup(title: Copy.closedRooms) {
                ForEach(closed) { room in
                    SettingRow(model.displayName(of: room), subtitle: Copy.keepTheShelfSub, value: Copy.keepTheShelf) {
                        onKeep(room)
                    }
                }
            }
        }
    }
}
