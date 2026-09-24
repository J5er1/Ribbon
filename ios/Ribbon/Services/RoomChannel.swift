import Foundation
import RibbonCore

/// The room's live line: Supabase Realtime over Phoenix Channels (§4.2, §4.3).
///
/// One socket, one channel — `realtime:room:<room id>` — and four things
/// travel on it:
///
///   * **Presence.** Who is in the book right now, where they are, whether
///     they have gone still, and who they are following. Announced only
///     while someone is actually reading: opening the channel says nothing.
///   * **The reading line.** Finer than presence — the verse, how far
///     through it, and the last thing on the screen — and only ever sent
///     while somebody present is following you (§4.2). Nothing on it is a
///     time: it is stamped where it arrives.
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
///
/// Presence is rationed. Supabase allows a client five tracks or untracks
/// in thirty seconds and closes the channel on the sixth — the person you
/// follow vanishes from your screen, and the live project's logs showed
/// that happening dozens of times a day. So every announcement goes
/// through one place, `reconcilePresence`, which says only what has
/// changed, keeps four sends a window for places and the fifth for what
/// cannot wait (leaving, appearing, or starting and ending a follow), and
/// folds everything else into one send when the window opens again.
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

    /// What the server last heard this connection say about you — the
    /// only thing a new announcement is compared with. A place alone is
    /// news; how far down the chapter the scroll is never is by itself.
    private struct Said: Equatable {
        var position: VerseAddress?
        var isIdle: Bool
        var following: UUID?
        var name: String
    }

    /// Where this page's reading line last was, kept for whoever starts
    /// following and for the keepalive.
    private struct LinePlace {
        var book: String
        var at: ReadingPoint
        var end: ReadingPoint?
        var settled: Bool
        var carried: Bool
    }

    private var socket: URLSessionWebSocketTask?
    private var roomID: UUID?
    private var person: Person?
    private var phase: Phase = .closed
    private var announcement: Announcement?
    private var presenceStore: [String: [String: Any]] = [:]
    /// Nil: this connection has said nothing about you, or has taken it
    /// back. A join starts it at nil — the server has never heard this
    /// socket — which is why the join always tracks.
    private var said: Said?
    /// When a track or untrack actually went out, for the last thirty
    /// seconds. Kept across reconnects: whether the server counts a limit
    /// per socket or per client, a window that forgot itself on every
    /// reconnect could be the one that trips it.
    ///
    /// On the continuous clock, as every interval here is: the wall clock
    /// can be set back — by hand, or by the network correcting a phone that
    /// ran fast — and sends it had recorded in the future never aged out,
    /// holding every place back for as long as the clock had jumped.
    private var presenceSends: [ContinuousClock.Instant] = []
    /// The one send waiting for the window to open again. It carries
    /// whatever the announcement is by then, not what it was.
    private var pendingPresence: Task<Void, Never>?
    /// The channel was closed for the app going away, and everything it
    /// was saying is kept for the join that brings it back.
    private var suspended = false
    private var reading: LinePlace?
    /// The people present who are following you right now.
    private var followers: Set<UUID> = []
    private var readingKeepalive: Task<Void, Never>?
    private var lastReadingSent: ContinuousClock.Instant?
    private var lastInFlightSent: ContinuousClock.Instant?
    /// This connection's name for itself on the reading line: eight random
    /// hex digits, new with every socket and never kept. Two phones of one
    /// person send two streams, and a follower sticks to one of them.
    private var source = RoomChannel.newSource()

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
    /// Supabase's presence limit: five tracks or untracks a client, a
    /// window. Four are for places; the fifth is held back for what must
    /// not wait behind them.
    private static let presenceWindow: Duration = .seconds(30)
    private static let presenceSendsForPlaces = 4
    private static let presenceSendsAtAll = 5
    /// A reading line that has not moved still says so this often, while
    /// somebody follows it — a still screen is somewhere, not nowhere.
    private static let readingKeepaliveEvery: Duration = .seconds(20)
    /// Every chapter and verse number a book has, with room to spare.
    private static let places = 1...999

    private nonisolated static func newSource() -> String {
        String(format: "%08x", UInt32.random(in: .min ... .max))
    }

    // MARK: - PresenceService

    func connect(roomID: UUID, person: Person) async {
        if self.roomID == roomID {
            self.person = person
            // Open, or on its way.
            guard phase == .closed else { return }
            // The same room, put down while the app was away or waiting
            // out a reconnect: it opens again as it was. Forgetting here
            // is what used to withdraw a reader who never left the book —
            // the app came back, the channel reopened, and it had nothing
            // left to say.
            reconnectTask?.cancel(); reconnectTask = nil
            reconnectAttempt = 0
            await open()
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
        close()
        roomID = nil
        person = nil
        announcement = nil
        reading = nil
        suspended = false
        continuation.yield(.roster([]))
    }

    /// The app has gone away (after its grace): the socket closes, and the
    /// room, the announcement and the reading line stay, for the join that
    /// brings them back. The roster does not. A reconnect is seconds and
    /// keeps it; a phone put away can be away for hours, and whoever was
    /// reading when it went may long since have closed the book. Until the
    /// line is back nobody is shown as here, as a disconnect has it.
    func suspend() async {
        guard roomID != nil else { return }
        close()
        suspended = true
        continuation.yield(.roster([]))
    }

    /// Everything a closed socket stops doing.
    private func close() {
        generation += 1
        heartbeatTask?.cancel(); heartbeatTask = nil
        idleTask?.cancel(); idleTask = nil
        reconnectTask?.cancel(); reconnectTask = nil
        pumpTask?.cancel(); pumpTask = nil
        pendingPresence?.cancel(); pendingPresence = nil
        readingKeepalive?.cancel(); readingKeepalive = nil
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
        said = nil
        followers = []
        presenceStore.removeAll()
        pendingHeartbeats = 0
    }

    func present(
        position: VerseAddress?, scrollFraction: Double,
        isIdle: Bool, following: UUID?, activity: Bool
    ) async {
        let wasAnnouncing = announcement != nil
        // A page carried by a follow is somewhere new without anybody
        // having moved it: it keeps whatever stillness it had.
        let still = isIdle
            || (!activity && Date().timeIntervalSince(lastActivity) >= Self.idleAfter)
        announcement = Announcement(
            position: position, scrollFraction: scrollFraction,
            isIdle: still, following: following)
        if activity, !isIdle { lastActivity = Date() }
        if !wasAnnouncing { startIdleWatch() }
        reconcilePresence()
    }

    func withdraw() async {
        // Out of the book: nothing more is said about where in it.
        reading = nil
        readingKeepalive?.cancel(); readingKeepalive = nil
        guard announcement != nil else { return }
        announcement = nil
        idleTask?.cancel(); idleTask = nil
        reconcilePresence()
    }

    func sendReading(
        book: String, at point: ReadingPoint, end: ReadingPoint?,
        settled: Bool, carried: Bool
    ) async {
        reading = LinePlace(book: book, at: point, end: end, settled: settled, carried: carried)
        keepReadingAlive()
        guard !followers.isEmpty else { return }
        if !settled {
            // A scroll in flight is sampled once a second at most; its
            // resting place follows it.
            let now = ContinuousClock.now
            if let last = lastInFlightSent, now - last < .seconds(1) { return }
            lastInFlightSent = now
        }
        sayReading()
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
        pendingPresence?.cancel(); pendingPresence = nil
        readingKeepalive?.cancel(); readingKeepalive = nil
        source = Self.newSource()

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
                self.reconcilePresence()
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
        // The next socket has heard nothing: its join says it all again.
        pendingPresence?.cancel(); pendingPresence = nil
        readingKeepalive?.cancel(); readingKeepalive = nil
        said = nil
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

    /// The one way presence leaves this phone. Compares what the
    /// announcement is now with what the server last heard, and sends the
    /// difference if the window has room for it — or once, later, when it
    /// does. `joining` is a fresh join: it always says everything, because
    /// appearing matters more than any limit.
    private func reconcilePresence(joining: Bool = false) {
        guard phase == .joined, topic != nil, let person else { return }
        let want = announcement.map {
            Said(position: $0.position, isIdle: $0.isIdle, following: $0.following, name: person.name)
        }
        // Nothing new, or nothing to take back from a line that never heard
        // anything.
        guard want != said else {
            pendingPresence?.cancel(); pendingPresence = nil
            return
        }
        let now = ContinuousClock.now
        presenceSends.removeAll { now - $0 >= Self.presenceWindow }
        // Leaving, appearing, and starting or ending a follow take the slot
        // held back for them: "Ruth is with you", and the end of it, travel
        // at once — and so does the book opened again a moment after it was
        // closed, which is not news to wait behind the scrolls before it.
        let urgent = want == nil || said == nil || want?.following != said?.following
        let allowed = urgent ? Self.presenceSendsAtAll : Self.presenceSendsForPlaces
        guard joining || presenceSends.count < allowed else {
            let oldest = presenceSends.min() ?? now
            waitForPresence(until: oldest + Self.presenceWindow + .milliseconds(250))
            return
        }
        pendingPresence?.cancel(); pendingPresence = nil
        let sent = want == nil ? sendUntrack() : sendTrack()
        if sent {
            said = want
            presenceSends.append(now)
        }
    }

    private func waitForPresence(until time: ContinuousClock.Instant) {
        guard pendingPresence == nil else { return }
        let mine = generation
        pendingPresence = Task { [weak self] in
            try? await Task.sleep(until: time, clock: .continuous)
            guard !Task.isCancelled, let self, mine == self.generation else { return }
            self.pendingPresence = nil
            self.reconcilePresence()
        }
    }

    private func sendUntrack() -> Bool {
        guard let topic else { return false }
        return send([
            "topic": topic,
            "event": "presence",
            "payload": [
                "type": "presence", "event": "untrack", "payload": [String: Any](),
            ] as [String: Any],
            "ref": nextRef(),
        ])
    }

    private func sendTrack() -> Bool {
        guard phase == .joined, let topic, let person, let announcement else { return false }
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
        return send([
            "topic": topic,
            "event": "presence",
            "payload": ["type": "presence", "event": "track", "payload": meta] as [String: Any],
            "ref": nextRef(),
        ])
    }

    /// The reading line, to whoever is following — only while somebody
    /// is, only while you are in the book, and never while reading
    /// quietly, which never announces and so never has anyone following.
    /// Sent as it was kept: a point taken in the middle of a scroll is
    /// still one, said again by the keepalive or to a new follower, and
    /// not a resting place for their guess to learn from.
    private func sayReading() {
        guard phase == .joined, announcement != nil, !followers.isEmpty,
              let person, let reading
        else { return }
        broadcast("reading", payload: ReadingWire.payload(
            personID: person.id, source: source, book: reading.book,
            at: reading.at, end: reading.end, settled: reading.settled, carried: reading.carried))
        lastReadingSent = ContinuousClock.now
    }

    /// While somebody follows, a still line says where it is every twenty
    /// seconds: a phone that has just started following, or lost a
    /// message, is never left guessing for long.
    private func keepReadingAlive() {
        guard !followers.isEmpty, phase == .joined, reading != nil else {
            readingKeepalive?.cancel(); readingKeepalive = nil
            return
        }
        guard readingKeepalive == nil else { return }
        let mine = generation
        readingKeepalive = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(5))
                guard !Task.isCancelled, let self, mine == self.generation else { return }
                if let last = self.lastReadingSent, ContinuousClock.now - last < Self.readingKeepaliveEvery { continue }
                self.sayReading()
            }
        }
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

    /// True once the frame is handed to an open socket — the only sends
    /// the presence window counts.
    @discardableResult
    private func send(_ message: [String: Any]) -> Bool {
        guard let socket,
              let data = try? JSONSerialization.data(withJSONObject: message),
              let text = String(data: data, encoding: .utf8)
        else { return false }
        socket.send(.string(text)) { _ in
            // A send failure surfaces on the receive pump, which is the one
            // place reconnection is decided.
        }
        return true
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
        case "system":
            handleSystem(json)
        default:
            break
        }
    }

    /// The server talking about the channel itself. Only what kind of news
    /// it is gets written down — never what it carried. A presence rate
    /// limit fills the window, so nothing more is tried for thirty seconds:
    /// the server has just said it would close the channel for it.
    private func handleSystem(_ json: [String: Any]) {
        let payload = json["payload"] as? [String: Any]
        let status = payload?["status"] as? String
        let kind = payload?["extension"] as? String
        print("[RoomChannel] system: \(kind ?? "-") \(status ?? "-")")
        if Self.isPresenceLimit(message: payload?["message"] as? String, kind: kind) {
            presenceSends = Array(repeating: ContinuousClock.now, count: Self.presenceSendsAtAll)
        }
    }

    /// Whether a `system` message is the server saying presence hit its
    /// limit — "Too many presence messages", or a rate limit that names
    /// presence. Matched loosely, because the wording is the server's to
    /// change, and only ever on the message, which is never logged. The
    /// same test as Android's `PresenceBudget.isPresenceLimit`: a limit on
    /// something else is not a reason to stop saying where you are.
    private static func isPresenceLimit(message: String?, kind: String?) -> Bool {
        guard let text = message?.lowercased() else { return false }
        let limited = (text.contains("rate") && text.contains("limit")) || text.contains("ratelimit")
            || text.contains("too many")
        let presence = text.contains("presence") || kind?.lowercased() == "presence"
        return limited && presence
    }

    private func handleReply(_ json: [String: Any]) {
        guard let reference = json["ref"] as? String, reference == joinRef else { return }
        let payload = json["payload"] as? [String: Any]
        let status = payload?["status"] as? String
        if status == "ok" {
            phase = .joined
            reconnectAttempt = 0
            said = nil
            // Whoever is following is news again to this socket: the
            // presence state that follows a join sends them the reading
            // line at once.
            followers = []
            if suspended {
                // Back from being away. Coming back to the book is being
                // here, not having been still for as long as the phone was
                // in a pocket.
                suspended = false
                lastActivity = Date()
                announcement?.isIdle = false
            }
            if announcement != nil, idleTask == nil { startIdleWatch() }
            // Whatever this device was already saying about itself goes up
            // again: a reconnect must not quietly withdraw a reader from a
            // room they never left.
            if announcement != nil { reconcilePresence(joining: true) }
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
        case "reading":
            // Taken apart defensively: anything missing or malformed is
            // dropped whole rather than guessed at.
            guard let raw = payload["payload"] as? [String: Any],
                  let heard = ReadingWire.heard(from: raw, received: Date()),
                  heard.personID != person?.id
            else { return }
            continuation.yield(.reading(
                personID: heard.personID, source: heard.source,
                book: heard.book, report: heard.report))
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

            // A place no book has — a chapter or verse outside 1...999, from
            // a phone that is broken or worse — is no place: the person is
            // still here, and where is simply not said.
            var position: VerseAddress?
            if let raw = meta["position"] as? [String: Any],
               let book = raw["book"] as? String,
               let chapter = (raw["chapter"] as? NSNumber)?.intValue, Self.places.contains(chapter),
               let verse = (raw["verse"] as? NSNumber)?.intValue, Self.places.contains(verse) {
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
        noticeFollowers(in: roster)
    }

    /// Somebody has just started following you: they hear where your line
    /// is now, not at your next scroll.
    private func noticeFollowers(in roster: [PresentPerson]) {
        guard let me = person?.id else { return }
        let now = Set(roster.filter { $0.followingPersonID == me }.map(\.id))
        let arrived = !now.subtracting(followers).isEmpty
        followers = now
        keepReadingAlive()
        if arrived { sayReading() }
    }
}

/// The `reading` broadcast, both ways (§4.2). Built by hand here and by
/// `RoomChannelWire` on Android, and the two agree key for key:
///
///     {"id", "source", "book", "chapter", "verse", "part",
///      "end": {"chapter", "verse", "part"}, "settled", "carried"}
///
/// `chapter` and `verse` are integers, `part` a number in [0, 1] to two
/// places, `end` left out when unknown and `carried` when false. Nothing on
/// it is a time, a rate or a duration (§13).
///
/// There is no test target for the app, so the cases Android's
/// RoomChannelWireTest pins down are held here by construction:
/// `testReadingCarriesAPointNeverATime` — a carried report with an end has
/// exactly the nine keys above, the end exactly its three, 0.4213 goes out
/// as 0.42; `testReadingOmitsAnAbsentEndAndCarried` — without either, the
/// seven; `testReadingNeverSendsANonFinitePart` — NaN goes out as 0, an
/// infinity as the end it runs past (+∞ as 1, −∞ as 0), 1.7 as 1, −0.3 as
/// 0, 0.126 as 0.13. That last one matters most here: JSONSerialization
/// meets a NaN with an Objective-C exception, which `try?` does not catch,
/// and the app would fall over on the reader's own scroll.
enum ReadingWire {
    struct Heard {
        var personID: UUID
        var source: String
        var book: String
        var report: ReadingReport
    }

    static func payload(
        personID: UUID, source: String, book: String,
        at: ReadingPoint, end: ReadingPoint?, settled: Bool, carried: Bool
    ) -> [String: Any] {
        var body: [String: Any] = [
            "id": personID.uuidString.lowercased(),
            "source": source,
            "book": book,
            "chapter": at.chapter,
            "verse": at.verse,
            "part": part(at.part),
            "settled": settled,
        ]
        if let end {
            body["end"] = [
                "chapter": end.chapter, "verse": end.verse, "part": part(end.part),
            ] as [String: Any]
        }
        if carried { body["carried"] = true }
        return body
    }

    /// A part as it may travel: finite, inside [0, 1], two places. A NaN is
    /// caught before anything else — clamped, it stays a NaN — and is the
    /// start of the verse; an infinity clamps to the end it runs past, as
    /// Android's `RoomChannelWire.part` has it.
    static func part(_ value: Double) -> Double {
        guard !value.isNaN else { return 0 }
        return (min(1, max(0, value)) * 100).rounded() / 100
    }

    /// Nil for anything that is not a whole, well-formed report, as
    /// Android reads it. A missing `settled` is a resting report, a missing
    /// `carried` is not carried, a missing `end` is unknown; everything
    /// else must be there. A key that is there must be the right shape — a
    /// flag true or false, an end an object — and anything else, `null`
    /// included, drops the report rather than being read as its default.
    static func heard(from body: [String: Any], received: Date) -> Heard? {
        guard let rawID = body["id"] as? String, let id = UUID(uuidString: rawID),
              let source = body["source"] as? String, !source.isEmpty,
              let book = body["book"] as? String, !book.isEmpty,
              let at = point(body)
        else { return nil }
        var end: ReadingPoint?
        if let rawEnd = body["end"] {
            guard let object = rawEnd as? [String: Any], let parsed = point(object) else { return nil }
            end = parsed
        }
        var settled = true
        if let raw = body["settled"] {
            guard let said = flag(raw) else { return nil }
            settled = said
        }
        var carried = false
        if let raw = body["carried"] {
            guard let said = flag(raw) else { return nil }
            carried = said
        }
        return Heard(
            personID: id, source: source, book: book,
            report: ReadingReport(at: at, end: end, settled: settled, carried: carried, received: received))
    }

    /// `chapter` and `verse` whole numbers — not true or false, which
    /// JSONSerialization also hands back as a number, and not a fraction,
    /// which would otherwise be cut down to one — and `part` a finite
    /// number that is not a flag either.
    private static func point(_ raw: [String: Any]) -> ReadingPoint? {
        guard let chapter = whole(raw["chapter"]), chapter >= 1,
              let verse = whole(raw["verse"]), verse >= 0,
              let number = raw["part"] as? NSNumber, !isFlag(number)
        else { return nil }
        let part = number.doubleValue
        guard part.isFinite else { return nil }
        return ReadingPoint(chapter: chapter, verse: verse, part: min(1, max(0, part)))
    }

    /// True or false as JSON has them, and nothing else.
    private static func flag(_ raw: Any) -> Bool? {
        guard let number = raw as? NSNumber, isFlag(number) else { return nil }
        return number.boolValue
    }

    /// A whole number as JSON has it: 6, not 6.7, and not `true`.
    private static func whole(_ raw: Any?) -> Int? {
        guard let number = raw as? NSNumber, !isFlag(number) else { return nil }
        return Int(exactly: number.doubleValue)
    }

    /// JSONSerialization's true and false are NSNumbers too; only their
    /// type tells them apart from 1 and 0.
    private static func isFlag(_ number: NSNumber) -> Bool {
        CFGetTypeID(number) == CFBooleanGetTypeID()
    }
}
