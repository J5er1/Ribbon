import Foundation
import UserNotifications
import RibbonCore

// Notifications (S19, §10.3, ledger A34/A39). There are six, and there will
// never be a seventh that is about absence. Every one is gated three ways
// before it is posted: the room's own switch, the reader's quiet hours, and
// whether the room is already on screen — a phone in your hand does not
// need to be told what it is showing you. No badge, ever: a badge is a
// count, and Law 2 holds on the home screen too.
//
// Nothing here asks for permission. The one ask is the model's, made once
// and in context (`shouldAskAboutNotifications`), and `ask()` is only what
// the model calls when the person says "Tell me".

enum NotificationKind: String, CaseIterable, Sendable {
    case notesLeft
    case cardsOpen
    case inTheBook
    case thinkingOfYou
    case bookFinished
}

/// Where a tapped notification lands. Carried in the notification's own
/// user info and read back out of it, so a tap on a two-day-old post still
/// knows the room it was about.
enum Destination: Hashable, Sendable {
    case verse(roomID: UUID, readingID: UUID, verse: VerseAddress)
    case cards(roomID: UUID, readingID: UUID, chapter: Int)
    case room(roomID: UUID)

    var roomID: UUID {
        switch self {
        case .verse(let roomID, _, _), .cards(let roomID, _, _), .room(let roomID): return roomID
        }
    }

    var userInfo: [String: String] {
        switch self {
        case .verse(let roomID, let readingID, let verse):
            return [
                "kind": "verse", "room": roomID.uuidString, "reading": readingID.uuidString,
                "book": verse.bookID, "chapter": String(verse.chapter), "verse": String(verse.verse),
            ]
        case .cards(let roomID, let readingID, let chapter):
            return ["kind": "cards", "room": roomID.uuidString, "reading": readingID.uuidString, "chapter": String(chapter)]
        case .room(let roomID):
            return ["kind": "room", "room": roomID.uuidString]
        }
    }

    init?(userInfo: [AnyHashable: Any]) {
        guard let kind = userInfo["kind"] as? String,
              let roomString = userInfo["room"] as? String, let roomID = UUID(uuidString: roomString)
        else { return nil }
        switch kind {
        case "verse":
            guard let readingString = userInfo["reading"] as? String, let readingID = UUID(uuidString: readingString),
                  let book = userInfo["book"] as? String,
                  let chapter = (userInfo["chapter"] as? String).flatMap(Int.init),
                  let verse = (userInfo["verse"] as? String).flatMap(Int.init)
            else { return nil }
            self = .verse(roomID: roomID, readingID: readingID, verse: VerseAddress(bookID: book, chapter: chapter, verse: verse))
        case "cards":
            guard let readingString = userInfo["reading"] as? String, let readingID = UUID(uuidString: readingString),
                  let chapter = (userInfo["chapter"] as? String).flatMap(Int.init)
            else { return nil }
            self = .cards(roomID: roomID, readingID: readingID, chapter: chapter)
        case "room":
            self = .room(roomID: roomID)
        default:
            return nil
        }
    }
}

@MainActor
enum Notifications {
    /// Whether the system will show what this app posts. Cached, because
    /// the question is asynchronous and the callers are not; refreshed on
    /// every foreground and after the one ask.
    private(set) static var allowed = false

    static func refreshAllowed() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        allowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
    }

    /// The system's own prompt, shown only after the person said "Tell me"
    /// to the app's question. Alerts and sound; never a badge.
    @discardableResult
    static func ask() async -> Bool {
        let granted = (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false
        allowed = granted
        return granted
    }

    /// The three gates, in order: the room's switch, the reader's quiet
    /// hours, and whether the room is on screen. A finished book and a
    /// touch on the shoulder go through the last gate regardless — one is
    /// once a book and the other is a touch, not a sentence.
    static func shouldPost(
        kind: NotificationKind, roomID: UUID, prefs: RoomNotificationPrefs,
        settings: AppSettings, visibleRoomID: UUID?, now: Date = Date()
    ) -> Bool {
        let wanted: Bool
        switch kind {
        case .notesLeft: wanted = prefs.notesLeft
        case .cardsOpen: wanted = prefs.cardsOpen
        case .inTheBook: wanted = prefs.whenTheyOpenTheBook
        case .thinkingOfYou: wanted = prefs.thinkingOfYou
        case .bookFinished: wanted = true
        }
        guard wanted else { return false }
        guard !settings.isQuiet(at: now) else { return false }
        switch kind {
        case .bookFinished, .thinkingOfYou: return true
        default: return roomID != visibleRoomID
        }
    }

    /// One post. The line is the whole of it: no title over a note, no
    /// preview of its body, no sound of its own beyond the system's.
    static func post(id: String, kind: NotificationKind, line: String, title: String? = nil, to destination: Destination) {
        guard allowed else { return }
        let content = UNMutableNotificationContent()
        if let title {
            content.title = title
            content.body = line
        } else {
            content.title = line
        }
        content.userInfo = destination.userInfo
        content.threadIdentifier = destination.roomID.uuidString
        content.interruptionLevel = .active
        content.sound = .default
        let request = UNNotificationRequest(identifier: id, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    static func forget(ids: [String]) {
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ids)
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
    }

    /// Stable per room and kind, so a second note from the same person
    /// replaces the first post rather than stacking beside it.
    static func id(roomID: UUID, kind: NotificationKind, author: UUID? = nil) -> String {
        var id = "\(roomID.uuidString).\(kind.rawValue)"
        if let author { id += ".\(author.uuidString)" }
        return id
    }
}

/// Where a tapped notification goes. The centre's delegate has to exist
/// before the app finishes launching, and the model does not, so the
/// destination waits here until something is listening.
final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate, @unchecked Sendable {
    static let shared = NotificationRouter()

    /// Set by the app once the model is loaded.
    @MainActor var deliver: ((Destination) -> Void)? {
        didSet {
            if let waiting, let deliver {
                self.waiting = nil
                deliver(waiting)
            }
        }
    }
    @MainActor private var waiting: Destination?

    func install() {
        UNUserNotificationCenter.current().delegate = self
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter, willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        // What the phone posts itself has already been through the three
        // gates. A push has been through two — the switch and the quiet
        // hours, judged by the server — and the third is only knowable
        // here: a phone in your hand is not told what it is showing you.
        // A finished book and a touch on the shoulder pass it regardless,
        // as they do in `shouldPost`. Never a badge.
        let info = notification.request.content.userInfo
        guard let kind = (info["notify"] as? String).flatMap(NotificationKind.init(rawValue:)),
              let room = (info["room"] as? String).flatMap(UUID.init(uuidString:))
        else { return [.banner, .sound] }
        switch kind {
        case .notesLeft, .cardsOpen, .inTheBook:
            let visible = await MainActor.run { AppSession.model?.visibleRoomID }
            return room == visible ? [] : [.banner, .sound]
        case .thinkingOfYou, .bookFinished:
            return [.banner, .sound]
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse
    ) async {
        guard let destination = Destination(userInfo: response.notification.request.content.userInfo) else { return }
        await MainActor.run {
            if let deliver {
                deliver(destination)
            } else {
                waiting = destination
            }
        }
    }
}

extension AppSettings {
    /// Quiet hours (S19), wrap-aware: 22:00–06:00 spans midnight. Equal
    /// ends mean no quiet hours at all rather than all of them.
    static func isQuiet(minute: Int, start: Int, end: Int) -> Bool {
        if start == end { return false }
        if start < end { return minute >= start && minute < end }
        return minute >= start || minute < end
    }

    func isQuiet(at date: Date = Date(), calendar: Calendar = .current) -> Bool {
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        let minute = (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
        return Self.isQuiet(minute: minute, start: quietHoursStart, end: quietHoursEnd)
    }
}

/// The line for notes left (§10.3). Two strings and no third: one note
/// names the verse, because the address is the invitation to go; several
/// name only the person, because listing them would be a count in prose.
func notesLeftLine(name: String, verse: VerseAddress?, several: Bool) -> String {
    guard !several, let verse else { return Copy.notifNotesLeft(firstName(name)) }
    return Copy.notifNoteLeft(firstName(name), verse.formatted)
}
