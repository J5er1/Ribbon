import Foundation
import RibbonCore

/// Supabase Realtime presence and broadcast service over Phoenix Channels WebSocket (§4.2, §4.3).
///
/// Connects to `/realtime/v1/websocket`, joins the active room's topic, tracks presence,
/// and handles "thinking of you" broadcast events.
@MainActor
final class SupabaseRealtimePresenceService: PresenceService {
    let events: AsyncStream<PresenceEvent>
    private let continuation: AsyncStream<PresenceEvent>.Continuation

    private var webSocketTask: URLSessionWebSocketTask?
    private var heartbeatTimer: Timer?
    private var currentRoomID: UUID?
    private var currentPerson: Person?
    private var currentPosition: VerseAddress?
    private var currentScrollFraction: Double = 0
    private var currentIsIdle: Bool = false
    private var refCount: Int = 0

    /// Raw store of currently connected presence metas: [PersonIDString: [String: Any]]
    private var presenceStore: [String: [String: Any]] = [:]

    init() {
        (events, continuation) = AsyncStream.makeStream(of: PresenceEvent.self)
    }

    func join(roomID: UUID, person: Person) async {
        if currentRoomID == roomID && webSocketTask != nil {
            currentPerson = person
            return
        }
        await leave()

        currentRoomID = roomID
        currentPerson = person
        presenceStore.removeAll()

        guard var components = URLComponents(url: SupabaseConfig.url, resolvingAgainstBaseURL: false) else { return }
        components.scheme = SupabaseConfig.url.scheme == "https" ? "wss" : "ws"
        components.path = "/realtime/v1/websocket"
        components.queryItems = [
            URLQueryItem(name: "apikey", value: SupabaseConfig.publishableKey),
            URLQueryItem(name: "vsn", value: "1.0.0")
        ]

        guard let wsURL = components.url else { return }
        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: wsURL)
        self.webSocketTask = task
        task.resume()

        startHeartbeat()
        sendJoin(roomID: roomID, personID: person.id)
        startReceiving()
    }

    func leave() async {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil

        if let roomID = currentRoomID {
            sendLeave(roomID: roomID)
        }

        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
        currentRoomID = nil
        currentPerson = nil
        presenceStore.removeAll()
        continuation.yield(.roster([]))
    }

    func update(position: VerseAddress?, scrollFraction: Double, isIdle: Bool) async {
        currentPosition = position
        currentScrollFraction = scrollFraction
        currentIsIdle = isIdle

        guard let roomID = currentRoomID, let person = currentPerson, webSocketTask != nil else { return }
        sendTrack(roomID: roomID, person: person, position: position, scrollFraction: scrollFraction, isIdle: isIdle)
    }

    func sendThinkingOfYou(to personID: UUID) async {
        guard let roomID = currentRoomID, let me = currentPerson, webSocketTask != nil else { return }
        refCount += 1
        let topic = "realtime:room:\(roomID.uuidString.lowercased())"
        let msg: [String: Any] = [
            "topic": topic,
            "event": "broadcast",
            "payload": [
                "type": "broadcast",
                "event": "thinking_of_you",
                "payload": [
                    "fromName": me.name,
                    "toPersonID": personID.uuidString.lowercased()
                ]
            ],
            "ref": "toy-\(refCount)"
        ]
        send(msg)
    }

    // MARK: - Phoenix Wire Protocol

    private func sendJoin(roomID: UUID, personID: UUID) {
        refCount += 1
        let topic = "realtime:room:\(roomID.uuidString.lowercased())"
        let msg: [String: Any] = [
            "topic": topic,
            "event": "phx_join",
            "payload": [
                "config": [
                    "broadcast": ["ack": false, "self": false],
                    "presence": ["key": personID.uuidString.lowercased()]
                ]
            ],
            "ref": "join-\(refCount)"
        ]
        send(msg)
    }

    private func sendLeave(roomID: UUID) {
        refCount += 1
        let topic = "realtime:room:\(roomID.uuidString.lowercased())"
        let msg: [String: Any] = [
            "topic": topic,
            "event": "phx_leave",
            "payload": [:],
            "ref": "leave-\(refCount)"
        ]
        send(msg)
    }

    private func sendTrack(
        roomID: UUID,
        person: Person,
        position: VerseAddress?,
        scrollFraction: Double,
        isIdle: Bool
    ) {
        refCount += 1
        let topic = "realtime:room:\(roomID.uuidString.lowercased())"

        var posPayload: [String: Any]? = nil
        if let pos = position {
            posPayload = [
                "book": pos.bookID,
                "chapter": pos.chapter,
                "verse": pos.verse
            ]
        }

        let payload: [String: Any] = [
            "type": "presence",
            "event": "track",
            "payload": [
                "id": person.id.uuidString.lowercased(),
                "name": person.name,
                "position": posPayload as Any,
                "scrollFraction": scrollFraction,
                "isIdle": isIdle,
                "followingPersonID": NSNull()
            ]
        ]

        let msg: [String: Any] = [
            "topic": topic,
            "event": "presence",
            "payload": payload,
            "ref": "track-\(refCount)"
        ]
        send(msg)
    }

    private func startHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 25.0, repeats: true) { [weak self] _ in
            guard let self else { return }
            self.refCount += 1
            let hb: [String: Any] = [
                "topic": "phoenix",
                "event": "heartbeat",
                "payload": [:],
                "ref": "hb-\(self.refCount)"
            ]
            self.send(hb)
        }
    }

    private func send(_ dictionary: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dictionary),
              let string = String(data: data, encoding: .utf8),
              let task = webSocketTask else { return }
        task.send(.string(string)) { error in
            if error != nil {
                // Network error handled on receive pump
            }
        }
    }

    private func startReceiving() {
        guard let task = webSocketTask else { return }
        task.receive { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self, self.webSocketTask === task else { return }
                switch result {
                case .success(let message):
                    self.handleIncoming(message)
                    self.startReceiving()
                case .failure:
                    // Disconnected; retry join if still in room
                    if let roomID = self.currentRoomID, let person = self.currentPerson {
                        try? await Task.sleep(nanoseconds: 2_000_000_000)
                        if self.currentRoomID == roomID {
                            await self.join(roomID: roomID, person: person)
                        }
                    }
                }
            }
        }
    }

    private func handleIncoming(_ message: URLSessionWebSocketTask.Message) {
        let rawData: Data?
        switch message {
        case .string(let text):
            rawData = text.data(using: .utf8)
        case .data(let data):
            rawData = data
        @unknown default:
            rawData = nil
        }
        guard let rawData,
              let json = (try? JSONSerialization.jsonObject(with: rawData)) as? [String: Any],
              let event = json["event"] as? String
        else { return }

        switch event {
        case "phx_reply":
            // Check if reply was to join, then track initial presence
            if let ref = json["ref"] as? String, ref.hasPrefix("join-"),
               let roomID = currentRoomID, let person = currentPerson {
                sendTrack(
                    roomID: roomID,
                    person: person,
                    position: currentPosition,
                    scrollFraction: currentScrollFraction,
                    isIdle: currentIsIdle
                )
            }

        case "presence_state":
            if let payload = json["payload"] as? [String: [String: Any]] {
                presenceStore.removeAll()
                for (key, val) in payload {
                    if let metas = val["metas"] as? [[String: Any]], let first = metas.first {
                        presenceStore[key] = first
                    }
                }
                emitRoster()
            }

        case "presence_diff":
            if let payload = json["payload"] as? [String: Any] {
                if let leaves = payload["leaves"] as? [String: Any] {
                    for key in leaves.keys {
                        presenceStore.removeValue(forKey: key)
                    }
                }
                if let joins = payload["joins"] as? [String: Any] {
                    for (key, val) in joins {
                        if let dict = val as? [String: Any],
                           let metas = dict["metas"] as? [[String: Any]],
                           let first = metas.first {
                            presenceStore[key] = first
                        }
                    }
                }
                emitRoster()
            }

        case "broadcast":
            if let payload = json["payload"] as? [String: Any],
               let innerEvent = payload["event"] as? String,
               innerEvent == "thinking_of_you",
               let innerPayload = payload["payload"] as? [String: Any],
               let toIDStr = innerPayload["toPersonID"] as? String,
               let myID = currentPerson?.id.uuidString.lowercased(),
               toIDStr == myID,
               let fromName = innerPayload["fromName"] as? String {
                continuation.yield(.thinkingOfYou(fromName: fromName))
            }

        default:
            break
        }
    }

    private func emitRoster() {
        guard let myID = currentPerson?.id.uuidString.lowercased() else { return }
        var result: [PresentPerson] = []

        for (key, meta) in presenceStore {
            // Do not show self in presence line
            if key == myID { continue }

            guard let idStr = meta["id"] as? String ?? Optional(key),
                  let id = UUID(uuidString: idStr),
                  let name = meta["name"] as? String else { continue }

            var pos: VerseAddress? = nil
            if let posDict = meta["position"] as? [String: Any],
               let book = posDict["book"] as? String,
               let ch = posDict["chapter"] as? Int,
               let v = posDict["verse"] as? Int {
                pos = VerseAddress(bookID: book, chapter: ch, verse: v)
            }

            let scroll = (meta["scrollFraction"] as? NSNumber)?.doubleValue ?? 0
            let isIdle = (meta["isIdle"] as? Bool) ?? false
            var following: UUID? = nil
            if let followStr = meta["followingPersonID"] as? String {
                following = UUID(uuidString: followStr)
            }

            result.append(
                PresentPerson(
                    id: id,
                    name: name,
                    position: pos,
                    scrollFraction: scroll,
                    isIdle: isIdle,
                    followingPersonID: following
                )
            )
        }

        continuation.yield(.roster(result))
    }
}
