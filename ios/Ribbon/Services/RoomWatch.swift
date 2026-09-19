import BackgroundTasks
import Foundation

// The room, checked on while the app is away (S19, ledger A34). Fifteen
// minutes is the floor the system allows; the system decides the rest, and
// a phone that is never opened is checked less often — which is right,
// because the notifications this pull produces are the only reason to do
// it. Started only once someone is signed in and stopped on sign-out, so a
// local-only install never wakes for nothing.

enum RoomWatch {
    static let identifier = "bible.ribbon.app.roomwatch"
    static let interval: TimeInterval = 15 * 60

    /// What a wake does. Set once, before the app finishes launching.
    @MainActor static var pull: (() async -> Void)?

    /// Registered before launch finishes — the system refuses a handler
    /// registered later.
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { return }
            let work = Task { @MainActor in
                await pull?()
                refresh.setTaskCompleted(success: true)
                schedule()
            }
            refresh.expirationHandler = {
                work.cancel()
                refresh.setTaskCompleted(success: false)
            }
        }
    }

    static func start() {
        schedule()
    }

    static func stop() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
    }

    private static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: identifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)
        try? BGTaskScheduler.shared.submit(request)
    }
}
