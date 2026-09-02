import Foundation
import RibbonCore

// A small, typed client for the pieces of Supabase Ribbon uses: email-code
// auth (GoTrue), PostgREST reads/writes, and the Realtime presence socket.
// Deliberately hand-rolled rather than an SDK dependency: the surface we
// need is narrow, and every request it can make is visible in this file.
//
// Status: the transport below is complete and matches the schema in
// /supabase/migrations; the sync engine that drives it ships with the
// sign-in thread (SupabaseConfig.remoteEnabled). Nothing in the app blocks
// on it — local-first is the design, not a fallback (§13).

struct SupabaseSession: Codable {
    var accessToken: String
    var refreshToken: String
    var user: SupabaseUser

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case user
    }
}

struct SupabaseUser: Codable {
    var id: UUID
    var email: String?
}

enum SupabaseError: Error {
    case http(Int, String)
    case notSignedIn
    /// A second account on a device that already belongs to a person
    /// (§6.10, S16): never a silent re-attribution of what they left.
    case differentAccount

    /// The backend answered and said no (a wrong code, a malformed email, a
    /// rate limit) — as opposed to never being reached.
    var isRefusal: Bool {
        if case .http(let code, _) = self, (400..<500).contains(code) { return true }
        return false
    }

    var isRateLimited: Bool {
        if case .http(429, _) = self { return true }
        return false
    }
}

actor SupabaseClient {
    private let base: URL
    private let key: String
    private var session: SupabaseSession?

    init(base: URL = SupabaseConfig.url, key: String = SupabaseConfig.publishableKey) {
        self.base = base
        self.key = key
    }

    // MARK: Auth — an emailed code, no passwords (§6.10)

    private struct OTPBody: Encodable {
        let email: String
        let createUser: Bool
    }

    /// Sends the sign-in code. The email field never leaks into a
    /// third-party account list — this is first-party mail.
    func sendCode(to email: String) async throws {
        try await post(
            path: "auth/v1/otp",
            body: OTPBody(email: email, createUser: true),
            authenticated: false)
    }

    func verifyCode(email: String, code: String) async throws -> SupabaseSession {
        let data = try await post(
            path: "auth/v1/verify",
            body: ["type": "email", "email": email, "token": code],
            authenticated: false)
        let session = try JSONDecoder().decode(SupabaseSession.self, from: data)
        self.session = session
        return session
    }

    func restore(_ session: SupabaseSession) {
        self.session = session
    }

    func signOut() {
        session = nil
    }

    /// The live session, for persisting across launches (Keychain — the
    /// tokens are credentials, not state).
    var currentSession: SupabaseSession? { session }

    func refresh() async throws {
        guard let session else { throw SupabaseError.notSignedIn }
        let data = try await post(
            path: "auth/v1/token",
            query: [URLQueryItem(name: "grant_type", value: "refresh_token")],
            body: ["refresh_token": session.refreshToken],
            authenticated: false)
        self.session = try JSONDecoder().decode(SupabaseSession.self, from: data)
    }

    var userID: UUID? { session?.user.id }

    // MARK: PostgREST

    /// GET a table with PostgREST filters, decoded.
    func select<T: Decodable>(_ type: T.Type, from table: String, query: [URLQueryItem]) async throws -> T {
        var components = URLComponents(
            url: base.appending(path: "rest/v1/\(table)"), resolvingAgainstBaseURL: false)!
        components.queryItems = query
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        try apply(headers: &request)
        let data = try await run(request)
        return try Self.decoder.decode(T.self, from: data)
    }

    /// Upsert rows; last-write-wins per object is safe because objects are
    /// single-author (§13). `onConflict` names a unique constraint's
    /// columns when the merge key isn't the primary key (memberships merge
    /// on room_id,person_id — a joiner's row was minted server-side with
    /// its own id).
    func upsert(into table: String, rows: some Encodable, onConflict: String? = nil) async throws {
        var url = base.appending(path: "rest/v1/\(table)")
        if let onConflict {
            url = url.appending(queryItems: [URLQueryItem(name: "on_conflict", value: onConflict)])
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = try Self.encoder.encode(rows)
        try apply(headers: &request)
        request.setValue("resolution=merge-duplicates,return=minimal", forHTTPHeaderField: "Prefer")
        _ = try await run(request)
    }

    func delete(from table: String, query: [URLQueryItem]) async throws {
        var components = URLComponents(
            url: base.appending(path: "rest/v1/\(table)"), resolvingAgainstBaseURL: false)!
        components.queryItems = query
        var request = URLRequest(url: components.url!)
        request.httpMethod = "DELETE"
        try apply(headers: &request)
        _ = try await run(request)
    }

    /// Call a database function (used for accept_invite).
    func rpc(_ function: String, body: [String: String]) async throws -> Data {
        try await post(path: "rest/v1/rpc/\(function)", body: body, authenticated: true)
    }

    /// Call an anon-callable function (invite_preview — the join screen
    /// shows who is inviting before any account exists).
    func rpcAnon(_ function: String, body: [String: String]) async throws -> Data {
        try await post(path: "rest/v1/rpc/\(function)", body: body, authenticated: false)
    }

    // MARK: Storage — voice notes, room-scoped paths

    func uploadAudio(readingID: UUID, noteID: UUID, fileURL: URL) async throws {
        let path = "storage/v1/object/voice-notes/\(readingID.uuidString.lowercased())/\(noteID.uuidString.lowercased()).m4a"
        var request = URLRequest(url: base.appending(path: path))
        request.httpMethod = "POST"
        request.httpBody = try Data(contentsOf: fileURL)
        try apply(headers: &request)
        request.setValue("audio/mp4", forHTTPHeaderField: "Content-Type")
        _ = try await run(request)
    }

    func downloadAudio(readingID: UUID, noteID: UUID, to destination: URL) async throws {
        let path = "storage/v1/object/authenticated/voice-notes/\(readingID.uuidString.lowercased())/\(noteID.uuidString.lowercased()).m4a"
        var request = URLRequest(url: base.appending(path: path))
        try apply(headers: &request)
        let data = try await run(request)
        try data.write(to: destination, options: .atomic)
    }

    // MARK: Storage — portraits, one per person (presence is faces, §2.7)

    func uploadPortrait(personID: UUID, data: Data) async throws {
        let path = "storage/v1/object/portraits/\(personID.uuidString.lowercased()).jpg"
        var request = URLRequest(url: base.appending(path: path))
        request.httpMethod = "POST"
        request.httpBody = data
        try apply(headers: &request)
        request.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        request.setValue("true", forHTTPHeaderField: "x-upsert")
        _ = try await run(request)
    }

    func downloadPortrait(personID: UUID) async throws -> Data {
        let path = "storage/v1/object/authenticated/portraits/\(personID.uuidString.lowercased()).jpg"
        var request = URLRequest(url: base.appending(path: path))
        try apply(headers: &request)
        return try await run(request)
    }

    func deletePortrait(personID: UUID) async throws {
        let path = "storage/v1/object/portraits/\(personID.uuidString.lowercased()).jpg"
        var request = URLRequest(url: base.appending(path: path))
        request.httpMethod = "DELETE"
        try apply(headers: &request)
        _ = try await run(request)
    }

    // MARK: Plumbing

    @discardableResult
    private func post(
        path: String, query: [URLQueryItem] = [], body: some Encodable, authenticated: Bool
    ) async throws -> Data {
        var url = base.appending(path: path)
        if !query.isEmpty {
            url = url.appending(queryItems: query)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = try Self.encoder.encode(AnyEncodable(body))
        if authenticated {
            try apply(headers: &request)
        } else {
            request.setValue(key, forHTTPHeaderField: "apikey")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        return try await run(request)
    }

    private func apply(headers request: inout URLRequest) throws {
        request.setValue(key, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        guard let session else { throw SupabaseError.notSignedIn }
        request.setValue("Bearer \(session.accessToken)", forHTTPHeaderField: "Authorization")
    }

    private func run(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw SupabaseError.http(0, "")
        }
        guard (200..<300).contains(http.statusCode) else {
            throw SupabaseError.http(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
        return data
    }

    static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .iso8601
        e.keyEncodingStrategy = .convertToSnakeCase
        return e
    }()

    /// PostgREST timestamps carry fractional seconds, which the plain
    /// .iso8601 strategy refuses; GoTrue's don't. Accept both.
    /// nonisolated(unsafe) is honest here: ISO8601DateFormatter is
    /// documented thread-safe, and these are set once and never mutated.
    nonisolated(unsafe) private static let fractionalTimestamp: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
    nonisolated(unsafe) private static let wholeTimestamp: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .custom { decoder in
            let raw = try decoder.singleValueContainer().decode(String.self)
            if let date = fractionalTimestamp.date(from: raw) ?? wholeTimestamp.date(from: raw) {
                return date
            }
            throw DecodingError.dataCorrupted(.init(
                codingPath: decoder.codingPath,
                debugDescription: "Unrecognized timestamp: \(raw)"))
        }
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()
}

private struct AnyEncodable: Encodable {
    let value: Encodable
    init(_ value: Encodable) { self.value = value }
    func encode(to encoder: Encoder) throws { try value.encode(to: encoder) }
}
