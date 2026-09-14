import Foundation
import RibbonCore

/// The room's live line: Supabase Realtime over Phoenix Channels (§4.2, §4.3).
///
/// One socket, one channel — `realtime:room:<room id>` — and three things
/// travel on it:
///
///   * **Presence.** Who is in the book right now, where they are, whether
///     they have gone still, and who they are following. Announced only
///     while someone is actually reading: opening the channel says nothing.
///   * **Thinking of you.** The contentless tap (§4.3).
///   * **A change nudge.** "Something in this room moved" — no content, no
///     second copy of the truth, just a reason for the other phone to pull
///     now instead of at its next launch. This is what makes a join, a note
///     or a fed fire arrive while you are looking at the room rather than
///     after you have put it down.
///
/// The channel is **private**: the account's own access token goes up with
/// the join, and the server checks it against `realtime.messages` (see
/// 20260914120000_ribbon_realtime_room_channel.sql) so a room you are not
/// in refuses you. A project whose Realtime Authorization policies are not
/// in place yet refuses *every* private join, so the first refusal
/// downgrades this connection to a public one and says so — the socket
/// still works, it is simply no better guarded than it was before the
/// migration lands.
///
/// Every message this sends is built by hand here and by
/// `RoomChannelWire` on Android, and `RoomChannelWireTest` asserts the
/// shapes exactly — they are the contract between the two builds, not a
/// private choice. A topic spelled differently is not an error on either
/// side; it is one person who is plainly reading and simply never appears.
@MainActor
final class RoomChannel: PresenceService {
    let events: AsyncStream<PresenceEvent>
    private let continuation: AsyncStream<PresenceEvent>.Continuation

    /// The account's access token, asked for fresh on every (re)join —
    /// tokens are short-lived and a socket outlives them.
    private let accessToken: @Sendable () async -> String?

    init(accessToken: @escaping @Sendable () async -> String?) {
        self.accessToken = accessToken
        (events, continuation) = AsyncStream.makeStream(of: PresenceEvent.self)
    }

    // MARK: State

    private enum Phase { case closed, joining, joined }

    /// What this device is announcing about itself. Nil is the honest
    /// "not in the book" — the channel can be wide open and still say
    /// nothing about you.
    private struct Announcement {
        var position: VerseAddress?
        var scrollFraction: Double
        var isIdle: Bool
        var following: UUID?
    }

    private var socket: URLSessionWebSocketTask?
    private var roomID: UUID?
    private var person: Person?
    private var phase: Phase = .closed
    private var announcement: Announcement?
    private var presenceStore: [String: [String: Any]] = [:]

    /// Bumped on every open and close; every async continuation checks it
    /// before touching state, so a socket that is already gone cannot
    /// speak for the one that replaced it.
    private var generation = 0
    private var ref = 0
    private var joinRef: String?
    /// Downgraded to false once a server refuses the private join.
    private var wantsPrivate = true
    private var reconnectAttempt = 0
    private var pendingHeartbeats = 0
    /// A change that happened while the line was down, to send on arrival.
    private var pendingAnnounce = false
    /// Sender-side collapse for the tap (§4.3).
    private var lastTap: [UUID: Date] = [:]
    /// The last scroll or position update, for the idle threshold.
    private var lastActivity = Date()

    private var heartbeatTask: Task<Void, Never>?
    private var idleTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var pumpTask: Task<Void, Never>?

    /// ~4 minutes with no movement is "here, but still" (§4.2).
    private static let idleAfter: TimeInterval = 240
    private static let heartbeatEvery: Duration = .seconds(25)
    private static let tapCollapse: TimeInterval = 180

    // MARK: - PresenceService

    func connect(roomID: UUID, person: Person) async {
        if self.roomID == roomID, phase != .closed {
            self.person = person
            return
        }
        await disconnect()
        self.roomID = roomID
        self.person = person
        // A fresh room gets a fresh answer to the private question: the
        // migration may well have landed since the last refusal.
        wantsPrivate = true
        reconnectAttempt = 0
        await open()
    }

    func disconnect() async {
        generation += 1
        heartbeatTask?.cancel(); heartbeatTask = nil
        idleTask?.cancel(); idleTask = nil
        reconnectTask?.cancel(); reconnectTask = nil
        pumpTask?.cancel(); pumpTask = nil
        if phase == .joined, let topic = topic {
            send([
                "topic": topic, "event": "phx_leave",
                "payload": [String: Any](), "ref": nextRef(),
            ])
        }
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
        phase = .closed
        joinRef = nil
        roomID = nil
        person = nil
        announcement = nil
        presenceStore.removeAll()
        pendingHeartbeats = 0
        continuation.yield(.roster([]))
    }

    func present(
        position: VerseAddress?, scrollFraction: Double,
        isIdle: Bool, following: UUID?
    ) async {
        let wasAnnouncing = announcement != nil
        announcement = Announcement(
            position: position, scrollFraction: scrollFraction,
            isIdle: isIdle, following: following)
        if !isIdle { lastActivity = Date() }
        if !wasAnnouncing { startIdleWatch() }
        sendTrack()
    }

    func withdraw() async {
        guard announcement != nil else { return }
        announcement = nil
        idleTask?.cancel(); idleTask = nil
        guard phase == .joined, let topic else { return }
        send([
            "topic": topic,
            "event": "presence",
            "payload": [
                "type": "presence", "event": "untrack", "payload": [String: Any](),
            ] as [String: Any],
            "ref": nextRef(),
        ])
    }

    func sendThinkingOfYou(to personID: UUID) async {
        if let last = lastTap[personID], Date().timeIntervalSince(last) < Self.tapCollapse {
            return
        }
        lastTap[personID] = Date()
        guard let me = person else { return }
        broadcast("thinking_of_you", payload: [
            "fromName": me.name,
            "toPersonID": personID.uuidString.lowercased(),
        ])
    }

    func announceChange() async {
        guard let roomID else { return }
        guard phase == .joined else {
            pendingAnnounce = true
            return
        }
        broadcast("room_changed", payload: ["roomID": roomID.uuidString.lowercased()])
    }

    // MARK: - The socket

    private var topic: String? {
        roomID.map { "realtime:room:\($0.uuidString.lowercased())" }
    }

    private func open() async {
        guard roomID != nil, let person else { return }
        generation += 1
        let mine = generation

        guard var components = URLComponents(
            url: SupabaseConfig.url, resolvingAgainstBaseURL: false)
        else { return }
        components.scheme = SupabaseConfig.url.scheme == "https" ? "wss" : "ws"
        components.path = "/realtime/v1/websocket"
        components.queryItems = [
            URLQueryItem(name: "apikey", value: SupabaseConfig.publishableKey),
            URLQueryItem(name: "vsn", value: "1.0.0"),
        ]
        guard let url = components.url else { return }

        let task = URLSession.shared.webSocketTask(with: url)
        socket = task
        phase = .joining
        pendingHeartbeats = 0
        task.resume()

        pumpTask = Task { [weak self] in await self?.pump(task, generation: mine) }
        await sendJoin(personID: person.id, generation: mine)
        startHeartbeat(generation: mine)
    }

    private func sendJoin(personID: UUID, generation mine: Int) async {
        let token = await accessToken()
        guard mine == generation, let topic else { return }
        let reference = nextRef()
        joinRef = reference
        var payload: [String: Any] = [
            "config": [
                "broadcast": ["ack": false, "self": false],
                // `enabled` is what opts this channel into presence at all
                // on current Realtime; the key makes the roster person-keyed
                // rather than socket-keyed, so two devices are one person.
                "presence": [
                    "key": personID.uuidString.lowercased(), "enabled": true,
                ] as [String: Any],
                "postgres_changes": [[String: Any]](),
                "private": wantsPrivate,
            ] as [String: Any]
        ]
        if let token { payload["access_token"] = token }
        send([
            "topic": topic, "event": "phx_join", "payload": payload,
            "ref": reference, "join_ref": reference,
        ])
    }

    private func pump(_ task: URLSessionWebSocketTask, generation mine: Int) async {
        while mine == generation, !Task.isCancelled {
            do {
                let message = try await task.receive()
                guard mine == generation else { return }
                pendingHeartbeats = 0
                handle(message)
            } catch {
                guard mine == generation else { return }
                scheduleReconnect()
                return
            }
        }
    }

    private func startHeartbeat(generation mine: Int) {
        heartbeatTask?.cancel()
        heartbeatTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: Self.heartbeatEvery)
                guard !Task.isCancelled, let self, mine == self.generation else { return }
                // Three unanswered beats is a socket that is open on this
                // side and gone on the other — the exact failure a plain
                // `receive` never reports.
                if self.pendingHeartbeats >= 3 {
                    self.scheduleReconnect()
                    return
                }
                self.pendingHeartbeats += 1
                self.send([
                    "topic": "phoenix", "event": "heartbeat",
                    "payload": [String: Any](), "ref": self.nextRef(),
                ])
            }
        }
    }

    private func startIdleWatch() {
        idleTask?.cancel()
        idleTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(30))
                guard !Task.isCancelled, let self, var current = self.announcement else { return }
                let still = Date().timeIntervalSince(self.lastActivity) >= Self.idleAfter
                guard still != current.isIdle else { continue }
                current.isIdle = still
                self.announcement = current
                self.sendTrack()
            }
        }
    }

    /// Reconnect with a widening gap. The room does not blink while this
    /// happens — the roster it last knew stays on screen until the socket
    /// says otherwise, because a flapping network is not the same news as
    /// somebody leaving.
    private func scheduleReconnect() {
        guard roomID != nil, reconnectTask == nil else { return }
        phase = .closed
        joinRef = nil
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
        heartbeatTask?.cancel(); heartbeatTask = nil
        pumpTask?.cancel(); pumpTask = nil
        let attempt = reconnectAttempt
        reconnectAttempt = min(attempt + 1, 6)
        let backoff = min(pow(2.0, Double(attempt)), 30) + Double.random(in: 0...0.75)
        reconnectTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(backoff))
            guard !Task.isCancelled, let self else { return }
            self.reconnectTask = nil
            guard self.roomID != nil else { return }
            await self.open()
        }
    }

    // MARK: - Messages out

    private func nextRef() -> String {
        ref += 1
        return String(ref)
    }

    private func sendTrack() {
        guard phase == .joined, let topic, let person, let announcement else { return }
        var meta: [String: Any] = [
            "id": person.id.uuidString.lowercased(),
            "name": person.name,
            "scrollFraction": announcement.scrollFraction,
            "isIdle": announcement.isIdle,
        ]
        // Built conditionally: an Optional bridged into a JSON dictionary
        // is not serializable, and the whole message would be dropped
        // silently rather than sent without the key.
        if let position = announcement.position {
            meta["position"] = [
                "book": position.bookID,
                "chapter": position.chapter,
                "verse": position.verse,
            ] as [String: Any]
        }
        if let following = announcement.following {
            meta["followingPersonID"] = following.uuidString.lowercased()
        }
        send([
            "topic": topic,
            "event": "presence",
            "payload": ["type": "presence", "event": "track", "payload": meta] as [String: Any],
            "ref": nextRef(),
        ])
    }

    private func broadcast(_ event: String, payload: [String: Any]) {
        guard phase == .joined, let topic else { return }
        send([
            "topic": topic,
            "event": "broadcast",
            "payload": ["type": "broadcast", "event": event, "payload": payload] as [String: Any],
            "ref": nextRef(),
        ])
    }

    private func send(_ message: [String: Any]) {
        guard let socket,
              let data = try? JSONSerialization.data(withJSONObject: message),
              let text = String(data: data, encoding: .utf8)
        else { return }
        socket.send(.string(text)) { _ in
            // A send failure surfaces on the receive pump, which is the one
            // place reconnection is decided.
        }
    }

    // MARK: - Messages in

    private func handle(_ message: URLSessionWebSocketTask.Message) {
        let raw: Data?
        switch message {
        case .string(let text): raw = text.data(using: .utf8)
        case .data(let data): raw = data
        @unknown default: raw = nil
        }
        guard let raw,
              let json = (try? JSONSerialization.jsonObject(with: raw)) as? [String: Any],
              let event = json["event"] as? String
        else { return }

        switch event {
        case "phx_reply":
            handleReply(json)
        case "phx_error", "phx_close":
            // The channel itself went away (a token that aged out, a
            // server restart). Only ours matters.
            if json["topic"] as? String == topic { scheduleReconnect() }
        case "presence_state":
            guard let payload = json["payload"] as? [String: Any] else { return }
            presenceStore.removeAll()
            for (key, value) in payload {
                if let meta = firstMeta(value) { presenceStore[key] = meta }
            }
            emitRoster()
        case "presence_diff":
            guard let payload = json["payload"] as? [String: Any] else { return }
            if let leaves = payload["leaves"] as? [String: Any] {
                for key in leaves.keys { presenceStore.removeValue(forKey: key) }
            }
            if let joins = payload["joins"] as? [String: Any] {
                for (key, value) in joins {
                    if let meta = firstMeta(value) { presenceStore[key] = meta }
                }
            }
            emitRoster()
        case "broadcast":
            handleBroadcast(json)
        default:
            break
        }
    }

    private func handleReply(_ json: [String: Any]) {
        guard let reference = json["ref"] as? String, reference == joinRef else { return }
        let payload = json["payload"] as? [String: Any]
        let status = payload?["status"] as? String
        if status == "ok" {
            phase = .joined
            reconnectAttempt = 0
            // Whatever this device was already saying about itself goes up
            // again: a reconnect must not quietly withdraw a reader from a
            // room they never left.
            if announcement != nil { sendTrack() }
            if pendingAnnounce {
                pendingAnnounce = false
                Task { await announceChange() }
            }
            return
        }
        // A refused private join on a project whose Realtime Authorization
        // policies are not in place yet. Say so once, come back public, and
        // try the private join again the next time the room is opened.
        if wantsPrivate {
            wantsPrivate = false
            print("[RoomChannel] private join refused; falling back to a public channel. "
                + "Apply 20260914120000_ribbon_realtime_room_channel.sql to close it.")
            Task { [weak self] in
                guard let self, let person = self.person else { return }
                await self.sendJoin(personID: person.id, generation: self.generation)
            }
            return
        }
        scheduleReconnect()
    }

    private func handleBroadcast(_ json: [String: Any]) {
        guard let payload = json["payload"] as? [String: Any],
              let inner = payload["event"] as? String
        else { return }
        let body = payload["payload"] as? [String: Any] ?? [:]
        switch inner {
        case "thinking_of_you":
            guard let to = body["toPersonID"] as? String,
                  let me = person?.id.uuidString.lowercased(), to == me,
                  let from = body["fromName"] as? String
            else { return }
            continuation.yield(.thinkingOfYou(fromName: from))
        case "room_changed":
            guard let roomID else { return }
            // Our own broadcasts do not come back (`self: false`), so this
            // is always someone else's news.
            continuation.yield(.roomChanged(roomID: roomID))
        default:
            break
        }
    }

    private func firstMeta(_ value: Any) -> [String: Any]? {
        guard let dictionary = value as? [String: Any],
              let metas = dictionary["metas"] as? [[String: Any]]
        else { return nil }
        return metas.first
    }

    private func emitRoster() {
        guard let me = person?.id.uuidString.lowercased() else { return }
        var roster: [PresentPerson] = []
        for (key, meta) in presenceStore {
            // Your own portrait is never in the presence line (§4.2, S07).
            if key == me { continue }
            guard let id = UUID(uuidString: (meta["id"] as? String) ?? key),
                  id.uuidString.lowercased() != me
            else { continue }
            let name = (meta["name"] as? String) ?? ""
            guard !name.isEmpty else { continue }

            var position: VerseAddress?
            if let raw = meta["position"] as? [String: Any],
               let book = raw["book"] as? String,
               let chapter = (raw["chapter"] as? NSNumber)?.intValue,
               let verse = (raw["verse"] as? NSNumber)?.intValue {
                position = VerseAddress(bookID: book, chapter: chapter, verse: verse)
            }
            roster.append(PresentPerson(
                id: id,
                name: name,
                position: position,
                scrollFraction: (meta["scrollFraction"] as? NSNumber)?.doubleValue ?? 0,
                isIdle: (meta["isIdle"] as? NSNumber)?.boolValue ?? false,
                followingPersonID: (meta["followingPersonID"] as? String).flatMap(UUID.init(uuidString:))))
        }
        // A stable order: the line must not reshuffle itself every time
        // somebody scrolls.
        roster.sort { $0.id.uuidString < $1.id.uuidString }
        continuation.yield(.roster(roster))
    }
}
