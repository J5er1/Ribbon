import ActivityKit
import Foundation
import RibbonCore
import UIKit
import WidgetKit

// The app's half of S24: what the widgets draw from, and the Live Activity
// the server starts.
//
// **The widget** reads a snapshot of the current room's fire from the app
// group's container (RoomSnapshot). The app writes it whenever the state it
// is made from changes, and reloads the widgets only when the snapshot
// actually differs — a reload is a budget the system keeps.
//
// **The Live Activity** is started by push, on this phone, when someone
// else in the room opens the book (the server's `i_am_reading`). The system
// wakes the app to hand over the activity's own token, which is how the
// server can keep it current and end it when they leave; this file passes
// the tokens on. It also tidies up after the server: an activity whose
// reader the room's presence no longer shows is ended here rather than left
// claiming a presence nobody can vouch for (§4.2).

@MainActor
enum LiveReading {
    /// The token that lets the server start a Live Activity here, hex. Nil
    /// when Live Activities are off for Ribbon in Settings — the server then
    /// says "Ruth is reading Mark" as an ordinary notification instead.
    private(set) static var startToken: String?

    private static var watching = false

    /// Start listening, once, before launch finishes: a push-started
    /// activity wakes the app, and its token arrives only to whoever is
    /// already listening.
    static func watch() {
        guard !watching else { return }
        watching = true
        Task {
            for await data in Activity<ReadingActivityAttributes>.pushToStartTokenUpdates {
                let hex = data.map { String(format: "%02x", $0) }.joined()
                guard hex != startToken else { continue }
                startToken = hex
                Push.tokenChanged?()
            }
        }
        for activity in Activity<ReadingActivityAttributes>.activities {
            follow(activity)
        }
        Task {
            for await activity in Activity<ReadingActivityAttributes>.activityUpdates {
                follow(activity)
            }
        }
    }

    /// Whether the server may start one here at all.
    static var startTokenIfAllowed: String? {
        ActivityAuthorizationInfo().areActivitiesEnabled ? startToken : nil
    }

    private static func follow(_ activity: Activity<ReadingActivityAttributes>) {
        let room = activity.attributes.roomID
        let reader = activity.attributes.readerID
        Task {
            for await data in activity.pushTokenUpdates {
                let hex = data.map { String(format: "%02x", $0) }.joined()
                await report(token: hex, room: room, reader: reader)
            }
        }
        Task {
            for await state in activity.activityStateUpdates where state == .dismissed || state == .ended {
                await forget(room: room, reader: reader)
            }
        }
    }

    /// Hand the activity's token to the server. The app may have been woken
    /// for exactly this and have no model yet; the session is all it needs.
    private static func report(token: String, room: String, reader: String) async {
        guard let device = Push.deviceToken,
              let roomID = UUID(uuidString: room), let readerID = UUID(uuidString: reader)
        else { return }
        let remote: RemoteSync
        if let live = AppSession.model?.remote {
            remote = live
        } else {
            remote = await RemoteSync.restore()
        }
        guard remote.isSignedIn else { return }
        try? await remote.registerLiveActivity(device: device, room: roomID, reader: readerID, token: token)
    }

    private static func forget(room: String, reader: String) async {
        guard let device = Push.deviceToken,
              let roomID = UUID(uuidString: room), let readerID = UUID(uuidString: reader),
              let remote = AppSession.model?.remote, remote.isSignedIn
        else { return }
        await remote.forgetLiveActivity(device: device, room: roomID, reader: readerID)
    }

    /// The room's presence is the truth while the app can hear it. Anyone
    /// whose Live Activity is showing and who is not in the book any more —
    /// a phone that died mid-chapter never says it left — is taken down,
    /// once the activity has had a moment to be true.
    static func reconcile(room: UUID, present: Set<UUID>) {
        let roomID = room.uuidString.lowercased()
        for activity in Activity<ReadingActivityAttributes>.activities
        where activity.attributes.roomID.lowercased() == roomID {
            guard let reader = UUID(uuidString: activity.attributes.readerID),
                  !present.contains(reader)
            else { continue }
            let content = activity.content
            // Every start and every heartbeat sets the stale date twenty
            // minutes out, so one more than eighteen minutes away means the
            // last word from their phone is under two minutes old — and the
            // roster may simply not have caught up with them yet.
            if let stale = content.staleDate, stale > Date().addingTimeInterval(18 * 60) {
                continue
            }
            Task { await activity.end(content, dismissalPolicy: .immediate) }
        }
    }

    // MARK: - The widget

    /// Write what the widget draws, and reload it only if that changed.
    static func updateWidget(room: Room?, reading: Reading?, banked: [DateInterval]) {
        guard let room else {
            RoomSnapshot.clear()
            WidgetCenter.shared.reloadAllTimelines()
            return
        }
        let snapshot = RoomSnapshot(
            roomID: room.id,
            book: reading.flatMap { Bible.book(id: $0.bookID)?.name },
            handiwork: reading?.handiwork,
            banked: banked)
        if snapshot.write() {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    /// Which image each kept copy was made from, so a face is copied again
    /// only when it changed.
    private static var kept: [UUID: ObjectIdentifier] = [:]

    /// A small copy of each face in the room, for the Live Activity to draw.
    static func keepPortraits(_ portraits: [UUID: UIImage]) {
        guard let container = RoomSnapshot.container else { return }
        let folder = container.appending(path: "Portraits")
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        for (personID, image) in portraits where kept[personID] != ObjectIdentifier(image) {
            guard let url = RoomSnapshot.portraitURL(for: personID.uuidString) else { continue }
            kept[personID] = ObjectIdentifier(image)
            let side: CGFloat = 96
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let small = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format)
                .image { _ in
                    let scale = max(side / image.size.width, side / image.size.height)
                    let drawn = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                    image.draw(in: CGRect(
                        x: (side - drawn.width) / 2, y: (side - drawn.height) / 2,
                        width: drawn.width, height: drawn.height))
                }
            if let data = small.jpegData(compressionQuality: 0.8) {
                try? data.write(to: url, options: .atomic)
            }
        }
    }
}
