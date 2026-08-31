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
    /// single-author (§13).
    func upsert(into table: String, rows: some Encodable) async throws {
        var request = URLRequest(url: base.appending(path: "rest/v1/\(table)"))
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

    /// Call a database function (used for accept_invite, close_room).
    func rpc(_ function: String, body: [String: String]) async throws -> Data {
        try await post(path: "rest/v1/rpc/\(function)", body: body, authenticated: true)
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

    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()
}

private struct AnyEncodable: Encodable {
    let value: Encodable
    init(_ value: Encodable) { self.value = value }
    func encode(to encoder: Encoder) throws { try value.encode(to: encoder) }
}
