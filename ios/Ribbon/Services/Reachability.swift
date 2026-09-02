import Foundation
import Network
import Observation

// Offline is a first-class case (§6.10, §08): no banner, presence gone,
// everything downloaded still works. The two things the room does with
// the fact are quiet — the fire dims by ~8% in its last known state, and
// a licensed book that hasn't streamed yet says it will finish on Wi-Fi
// instead of opening onto blank pages (S01).

@MainActor
@Observable
final class Reachability {
    private(set) var isOnline = true
    private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in self?.isOnline = online }
        }
        monitor.start(queue: DispatchQueue(label: "app.ribbon.reachability", qos: .utility))
    }
}
