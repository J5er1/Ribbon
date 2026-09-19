import Foundation
import Network

// Whether the phone has a way out (ledger A45). One fact, read by one thing:
// the fire is dimmed while there is no network, because presence is off
// and the fire is the room's one live object. Nothing else branches on it
// — the app is local-first, and everything that can be done offline is.

@MainActor
@Observable
final class Connectivity {
    private(set) var online = true
    private let monitor = NWPathMonitor()

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            let satisfied = path.status == .satisfied
            Task { @MainActor [weak self] in self?.online = satisfied }
        }
        monitor.start(queue: DispatchQueue(label: "app.ribbon.connectivity"))
    }

    func stop() {
        monitor.cancel()
    }
}
