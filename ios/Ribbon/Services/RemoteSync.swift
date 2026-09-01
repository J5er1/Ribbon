import Foundation
import Security
import RibbonCore

// The sign-in thread (§6.10) and the room surface of sync: accounts,
// invites, joining, and the graph a room renders from — rooms, memberships,
// profiles, readings, fires, and the rolling fuel window. Notes, highlights
// and positions still live on-device only; they ride the full sync engine,
// which is the next piece of work (docs/deviations.md).
//
// The app stays local-first: everything here is best-effort and
// fire-and-forget from the UI's point of view. Nothing blocks reading.

/// What the join screen shows before joining (S16): a person, not a
/// product.
struct InvitePreview: Decodable {
    var inviterName: String?
    var roomName: String?
    var expired: Bool
    var full: Bool
}

/// The room-surface graph, as pulled from the backend.
struct RoomGraph {
    var rooms: [RemoteSync.RoomRow] = []
    var memberships: [RemoteSync.MembershipRow] = []
    var profiles: [RemoteSync.ProfileRow] = []
    var readings: [RemoteSync.ReadingRow] = []
    var fires: [RemoteSync.FireRow] = []
    var fuelEvents: [RemoteSync.FuelEventRow] = []
    var quietDays: [RemoteSync.QuietDayRow] = []
}

@MainActor
final class RemoteSync {
    private let client = SupabaseClient()
    /// Mirrored from the client so synchronous UI checks don't await.
    private(set) var userID: UUID?
    private(set) var email: String?

    var isSignedIn: Bool { userID != nil }

    /// Restores a persisted session, if one exists. Always returns a
    /// service — signed out is a state, not an absence.
    static func restore() async -> RemoteSync {
        let sync = RemoteSync()
        if let session = SessionKeychain.load() {
            await sync.client.restore(session)
            sync.userID = session.user.id
            sync.email = session.user.email
        }
        return sync
    }

    // MARK: - Sign-in: an emailed code, no passwords (§6.10)

    func sendCode(to email: String) async throws {
        try await client.sendCode(to: email)
    }

    func verify(email: String, code: String) async throws -> UUID {
        let session = try await client.verifyCode(email: email, code: code)
        SessionKeychain.save(session)
        userID = session.user.id
        self.email = session.user.email ?? email
        return session.user.id
    }

    func signOut() async {
        await client.signOut()
        SessionKeychain.clear()
        userID = nil
        email = nil
    }

    // MARK: - Invites and joining (S16)

    func invitePreview(token: UUID) async throws -> InvitePreview? {
        let data = try await client.rpcAnon(
            "invite_preview", body: ["invite_token": token.uuidString.lowercased()])
        let rows = try SupabaseClient.decoder.decode([InvitePreview].self, from: data)
        return rows.first
    }

    /// Joins the invite's room; returns its id. The database enforces
    /// expiry and the six-person ceiling.
    func acceptInvite(token: UUID) async throws -> UUID {
        let data = try await withAuthRetry {
            try await self.client.rpc(
                "accept_invite", body: ["invite_token": token.uuidString.lowercased()])
        }
        return try JSONDecoder().decode(UUID.self, from: data)
    }

    // MARK: - Push: the room surface this device knows

    func push(profile: Person, portraitData: Data?) async throws {
        if let portraitData {
            try? await withAuthRetry {
                try await self.client.uploadPortrait(personID: profile.id, data: portraitData)
            }
        }
        // The remote path is deterministic — <person id>.jpg — so a name
        // edit never clears a portrait uploaded earlier.
        let row = ProfileRow(
            id: profile.id, name: profile.name,
            portraitPath: profile.portraitPath == nil
                ? nil : "\(profile.id.uuidString.lowercased()).jpg",
            translation: profile.translation.rawValue)
        try await withAuthRetry {
            try await self.client.upsert(into: "profiles", rows: [row])
        }
    }

    func push(room: Room) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "rooms", rows: [
                RoomRow(id: room.id, name: room.name, isPaused: room.isPaused, createdAt: room.createdAt)
            ])
        }
    }

    func push(membership: Membership) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "memberships",
                rows: [MembershipRow(
                    id: membership.id, roomId: membership.roomID,
                    personId: membership.personID,
                    ink: membership.ink?.rawValue, joinedAt: membership.joinedAt)],
                onConflict: "room_id,person_id")
        }
    }

    func push(invite: Invite) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "invites", rows: [
                InviteRow(
                    id: invite.id, roomId: invite.roomID, createdBy: invite.createdBy,
                    createdAt: invite.createdAt, expiresAt: invite.expiresAt)
            ])
        }
    }

    func push(reading: Reading) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "readings", rows: [
                ReadingRow(
                    id: reading.id, roomId: reading.roomID, bookId: reading.bookID,
                    scale: reading.handiwork.scale.rawValue,
                    startedAt: reading.startedAt, finishedAt: reading.finishedAt)
            ])
        }
        try await withAuthRetry {
            try await self.client.upsert(into: "fires", rows: [
                FireRow(
                    readingId: reading.id,
                    coalDepth: reading.handiwork.coalDepth,
                    lastFuelAt: reading.handiwork.lastFuelAt,
                    restartAt: reading.handiwork.restartAt,
                    stateAtLastFuel: reading.handiwork.stateAtLastFuel.rawValue)
            ])
        }
    }

    /// One reader's feeding, into the rolling window the schema prunes.
    /// Duplicates are harmless — the engine counts distinct people, never
    /// events.
    func push(fuel: FuelEvent, readingID: UUID) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "fuel_events", rows: [
                FuelEventRow(id: UUID(), readingId: readingID, personId: fuel.personID, at: fuel.at)
            ])
        }
    }

    /// Leaving a room (§6.8): the membership goes; notes and highlights
    /// stay by design (their sync rides the full engine later).
    func deleteMembership(roomID: UUID, personID: UUID) async throws {
        try await withAuthRetry {
            try await self.client.delete(from: "memberships", query: [
                URLQueryItem(name: "room_id", value: "eq.\(roomID.uuidString.lowercased())"),
                URLQueryItem(name: "person_id", value: "eq.\(personID.uuidString.lowercased())"),
            ])
        }
    }

    /// A marked quiet day banks the fire for the room — on every device.
    func push(quietDay: QuietDay) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "quiet_days",
                rows: [QuietDayRow(
                    id: quietDay.id, roomId: quietDay.roomID, personId: quietDay.personID,
                    localDate: quietDay.localDate, timeZone: quietDay.timeZoneID,
                    markedAt: quietDay.markedAt)],
                onConflict: "room_id,person_id,local_date")
        }
    }

    // MARK: - Pull: every room I'm in

    func pullRooms() async throws -> RoomGraph {
        guard let userID else { return RoomGraph() }
        var graph = RoomGraph()

        let mine: [MembershipRow] = try await withAuthRetry {
            try await self.client.select(
                [MembershipRow].self, from: "memberships",
                query: [URLQueryItem(name: "person_id", value: "eq.\(userID.uuidString.lowercased())")])
        }
        let roomIDs = mine.map(\.roomId)
        guard !roomIDs.isEmpty else { return graph }
        let roomList = "in.(\(roomIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"

        graph.rooms = try await withAuthRetry {
            try await self.client.select(
                [RoomRow].self, from: "rooms",
                query: [URLQueryItem(name: "id", value: roomList)])
        }
        graph.memberships = try await withAuthRetry {
            try await self.client.select(
                [MembershipRow].self, from: "memberships",
                query: [URLQueryItem(name: "room_id", value: roomList)])
        }
        let personIDs = Set(graph.memberships.map(\.personId))
        if !personIDs.isEmpty {
            let personList = "in.(\(personIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
            graph.profiles = try await withAuthRetry {
                try await self.client.select(
                    [ProfileRow].self, from: "profiles",
                    query: [URLQueryItem(name: "id", value: personList)])
            }
        }
        graph.readings = try await withAuthRetry {
            try await self.client.select(
                [ReadingRow].self, from: "readings",
                query: [URLQueryItem(name: "room_id", value: roomList)])
        }
        graph.quietDays = try await withAuthRetry {
            try await self.client.select(
                [QuietDayRow].self, from: "quiet_days",
                query: [URLQueryItem(name: "room_id", value: roomList)])
        }
        let readingIDs = graph.readings.map(\.id)
        if !readingIDs.isEmpty {
            let readingList = "in.(\(readingIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
            graph.fires = try await withAuthRetry {
                try await self.client.select(
                    [FireRow].self, from: "fires",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }
            graph.fuelEvents = try await withAuthRetry {
                try await self.client.select(
                    [FuelEventRow].self, from: "fuel_events",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }
        }
        return graph
    }

    func fetchPortrait(personID: UUID) async -> Data? {
        try? await withAuthRetry {
            try await self.client.downloadPortrait(personID: personID)
        }
    }

    // MARK: - Plumbing

    /// Access tokens are short-lived; a 401 means refresh and retry once.
    private func withAuthRetry<T>(_ work: () async throws -> T) async throws -> T {
        do {
            return try await work()
        } catch SupabaseError.http(401, _) {
            try await client.refresh()
            if let refreshed = await client.currentSession {
                SessionKeychain.save(refreshed)
                userID = refreshed.user.id
            }
            return try await work()
        }
    }

    // MARK: - Rows (PostgREST shapes; snake_case via the client's coders)

    struct ProfileRow: Codable {
        var id: UUID
        var name: String
        var portraitPath: String?
        var translation: String
    }

    struct RoomRow: Codable {
        var id: UUID
        var name: String?
        var isPaused: Bool
        var createdAt: Date
    }

    struct MembershipRow: Codable {
        var id: UUID
        var roomId: UUID
        var personId: UUID
        var ink: String?
        var joinedAt: Date
    }

    struct InviteRow: Codable {
        var id: UUID
        var roomId: UUID
        var createdBy: UUID
        var createdAt: Date
        var expiresAt: Date
    }

    struct ReadingRow: Codable {
        var id: UUID
        var roomId: UUID
        var bookId: String
        var scale: String
        var startedAt: Date
        var finishedAt: Date?
    }

    struct FireRow: Codable {
        var readingId: UUID
        var coalDepth: Double
        var lastFuelAt: Date?
        var restartAt: Date?
        var stateAtLastFuel: String
    }

    struct FuelEventRow: Codable {
        var id: UUID
        var readingId: UUID
        var personId: UUID
        var at: Date
    }

    /// local_date is a bare Postgres date ("2026-09-01") — a String here,
    /// matching QuietDay.localDate; the timestamp decoder would refuse it.
    struct QuietDayRow: Codable {
        var id: UUID
        var roomId: UUID
        var personId: UUID
        var localDate: String
        var timeZone: String
        var markedAt: Date
    }
}

// MARK: - Session persistence

/// The session is a credential, so it lives in the Keychain, not in
/// state.json.
private enum SessionKeychain {
    private static let service = "app.ribbon.supabase-session"
    private static let account = "session"

    static func save(_ session: SupabaseSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    static func load() -> SupabaseSession? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return try? JSONDecoder().decode(SupabaseSession.self, from: data)
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
