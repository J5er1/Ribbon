import Foundation
import UIKit

// Push (S19): the phone's end of the server's half.
//
// Until the backend could send, every notification was this phone's own
// work — a background pull the system runs when it likes (RoomWatch), and
// nothing at all for "Ruth is reading Mark" or a thinking-of-you sent while
// the app was closed. Now the database writes a row for each of those
// things and the `push` function delivers it through APNs. This file is
// what the phone owes that arrangement: its token, its switches and quiet
// hours as it holds them, and the one rule that keeps the two halves from
// talking over each other.
//
// **One voice, never two.** While the server is delivering for this phone
// the phone posts none of the six itself (Notifications.swift): the same
// note announced by a push and again by the next background pull would be
// the app repeating itself about the one thing it should say once. Whether
// the server is delivering is asked, not assumed — the sender answers with
// whether its APNs key is set — and a phone whose registration did not go
// through keeps speaking for itself. The last answer is remembered, because
// a background pull runs before anything has had time to ask.

@MainActor
enum Push {
    /// This install's APNs token, hex. Nil until the system hands one over,
    /// and on a simulator or a build without the push entitlement, forever.
    private(set) static var deviceToken: String?

    /// Called when the token arrives or changes: the model registers again.
    static var tokenChanged: (() -> Void)?

    /// Whether the server is saying the six for this phone right now.
    /// Remembered across launches for the background pull's sake.
    private(set) static var delivering: Bool = UserDefaults.standard.bool(forKey: deliveringKey)

    private static let deliveringKey = "push.delivering"
    /// Whether the sender has an APNs key, asked once per launch.
    private static var transportLive: Bool?
    /// Whether this launch's registration reached the server.
    private static var registered = false

    /// Builds run from Xcode speak to APNs' sandbox; TestFlight and the
    /// store to production. The token belongs to one world or the other.
    static var environment: String {
        #if DEBUG
        "sandbox"
        #else
        "production"
        #endif
    }

    static func tokenArrived(_ data: Data) {
        let hex = data.map { String(format: "%02x", $0) }.joined()
        guard hex != deviceToken else { return }
        deviceToken = hex
        tokenChanged?()
    }

    /// Ask the system for a token. Repeated on every launch, as Apple asks:
    /// the token can change, and asking is how the new one arrives.
    static func requestToken() {
        UIApplication.shared.registerForRemoteNotifications()
    }

    static func registration(succeeded: Bool) {
        registered = succeeded
        settle()
    }

    /// The sender's own answer about whether it can reach APNs. Asked
    /// without an account: it says nothing about anybody.
    static func learnWhetherTheServerDelivers() async {
        guard transportLive == nil else { return }
        let url = SupabaseConfig.url.appending(path: "functions/v1/push")
        guard let (data, response) = try? await URLSession.shared.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200,
              let answer = try? JSONDecoder().decode(Transports.self, from: data)
        else { return }
        transportLive = answer.ios
        settle()
    }

    /// Signed out: the phone is off the server's list, and speaks for
    /// itself again at once.
    static func forgotten() {
        registered = false
        remember(false)
    }

    /// Until the sender has answered, the remembered answer stands — a
    /// launch is not a reason to start saying things twice.
    private static func settle() {
        guard let transportLive else { return }
        remember(registered && transportLive)
    }

    private static func remember(_ now: Bool) {
        guard now != delivering else { return }
        delivering = now
        UserDefaults.standard.set(now, forKey: deliveringKey)
    }

    private struct Transports: Decodable {
        var ios: Bool
    }
}
