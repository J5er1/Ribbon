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
    var isAuth0: Bool = false

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case user
        case isAuth0 = "is_auth0"
    }

    init(accessToken: String, refreshToken: String, user: SupabaseUser, isAuth0: Bool = false) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.user = user
        self.isAuth0 = isAuth0
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accessToken = try container.decode(String.self, forKey: .accessToken)
        refreshToken = try container.decode(String.self, forKey: .refreshToken)
        user = try container.decode(SupabaseUser.self, forKey: .user)
        isAuth0 = try container.decodeIfPresent(Bool.self, forKey: .isAuth0) ?? false
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

    /// Sets an authenticated session using an Auth0-issued ID token.
    /// Supabase validates this token using Auth0's OIDC Discovery JWKS.
    func setAuth0Session(idToken: String, userUUID: UUID, email: String? = nil, refreshToken: String = "") -> SupabaseSession {
        let session = SupabaseSession(
            accessToken: idToken,
            refreshToken: refreshToken,
            user: SupabaseUser(id: userUUID, email: email),
            isAuth0: true
        )
        self.session = session
        return session
    }

    // MARK: Passkeys (§6.10 — "a passkey where available")
    //
    // Supabase Auth's own WebAuthn support, through its two-step API: the
    // server hands out a challenge and the WebAuthn options that go with it,
    // the platform runs the ceremony, and the signed result comes back here.
    // The options and the credential are passed through as JSON rather than
    // modelled: they are the W3C shapes, they are the platform's to read and
    // to produce, and anything this client understood about them would only
    // be a second place for them to be wrong.

    /// A challenge, and the WebAuthn options that belong to it.
    ///
    /// The options travel as their own JSON rather than as a parsed
    /// dictionary: this is an actor and the ceremony is on the main one, and
    /// a `[String: Any]` is not something that may cross between them. Bytes
    /// are, and bytes are what both ends actually want.
    struct PasskeyChallenge: Sendable {
        let challengeID: String
        /// `PublicKeyCredentialCreationOptions` or
        /// `PublicKeyCredentialRequestOptions`, with every ArrayBuffer field
        /// base64url-encoded.
        let optionsJSON: Data
    }

    /// Start registering a passkey for the account that is signed in.
    func passkeyRegistrationOptions() async throws -> PasskeyChallenge {
        try await passkeyChallenge(path: "auth/v1/passkeys/registration/options", authenticated: true)
    }

    /// Finish registering. The account keeps the passkey; the session is
    /// unchanged, because it was already signed in.
    func verifyPasskeyRegistration(challengeID: String, credentialJSON: Data) async throws {
        _ = try await postPasskey(
            path: "auth/v1/passkeys/registration/verify",
            challengeID: challengeID, credentialJSON: credentialJSON, authenticated: true)
    }

    /// Start signing in with a passkey. Discoverable credentials: no email
    /// is asked for, because the authenticator already knows which account
    /// this is.
    func passkeyAuthenticationOptions() async throws -> PasskeyChallenge {
        try await passkeyChallenge(
            path: "auth/v1/passkeys/authentication/options", authenticated: false)
    }

    /// Finish signing in. This is the call that returns a session.
    func verifyPasskeyAuthentication(
        challengeID: String, credentialJSON: Data
    ) async throws -> SupabaseSession {
        let data = try await postPasskey(
            path: "auth/v1/passkeys/authentication/verify",
            challengeID: challengeID, credentialJSON: credentialJSON, authenticated: false)
        let session = try JSONDecoder().decode(SupabaseSession.self, from: data)
        self.session = session
        return session
    }

    private func passkeyChallenge(path: String, authenticated: Bool) async throws -> PasskeyChallenge {
        var request = URLRequest(url: base.appending(path: path))
        request.httpMethod = "POST"
        if authenticated {
            try apply(headers: &request)
        } else {
            request.setValue(key, forHTTPHeaderField: "apikey")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        let data = try await run(request)
        guard
            let body = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let challengeID = body["challenge_id"] as? String,
            let options = body["options"] as? [String: Any],
            let optionsJSON = try? JSONSerialization.data(withJSONObject: options)
        else {
            throw SupabaseError.http(0, String(data: data, encoding: .utf8) ?? "")
        }
        return PasskeyChallenge(challengeID: challengeID, optionsJSON: optionsJSON)
    }

    private func postPasskey(
        path: String, challengeID: String, credentialJSON: Data, authenticated: Bool
    ) async throws -> Data {
        var request = URLRequest(url: base.appending(path: path))
        request.httpMethod = "POST"
        if authenticated {
            try apply(headers: &request)
        } else {
            request.setValue(key, forHTTPHeaderField: "apikey")
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        // Parsed and re-encoded inside the actor, so the `Any` never leaves
        // this method.
        let credential = try JSONSerialization.jsonObject(with: credentialJSON)
        request.httpBody = try JSONSerialization.data(
            withJSONObject: ["challenge_id": challengeID, "credential": credential])
        return try await run(request)
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
        guard var current = session else { throw SupabaseError.notSignedIn }
        if current.isAuth0 {
            guard !current.refreshToken.isEmpty else { return }
            let refreshed = try await Auth0Service.refreshTokens(refreshToken: current.refreshToken)
            current.accessToken = refreshed.idToken
            if !refreshed.refreshToken.isEmpty {
                current.refreshToken = refreshed.refreshToken
            }
            self.session = current
            return
        }
        let data = try await post(
            path: "auth/v1/token",
            query: [URLQueryItem(name: "grant_type", value: "refresh_token")],
            body: ["refresh_token": current.refreshToken],
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

    /// What a conditional portrait download found.
    enum PortraitFetch {
        /// The server's copy is the copy this device already has.
        case unchanged
        /// A different face, and the tag to offer next time.
        case changed(Data, etag: String?)
        /// No portrait behind the row — deleted, or never uploaded.
        case missing
    }

    /// The face, asked for conditionally.
    ///
    /// The object's path is `<person id>.jpg` and never changes, so nothing
    /// in the profile row can say the bytes behind it did. The object's own
    /// tag can: offer the one this device stored, and a 304 with no body is
    /// the server saying the face is still the face.
    func downloadPortrait(personID: UUID, ifNoneMatch: String?) async throws -> PortraitFetch {
        let path = "storage/v1/object/authenticated/portraits/\(personID.uuidString.lowercased()).jpg"
        var request = URLRequest(url: base.appending(path: path))
        try apply(headers: &request)
        if let ifNoneMatch {
            request.setValue(ifNoneMatch, forHTTPHeaderField: "If-None-Match")
        }
        // URLSession answers a conditional request out of its own cache when
        // it can, which would hand back 200 and the old bytes and hide the
        // 304 this whole mechanism turns on.
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw SupabaseError.http(0, "")
        }
        switch http.statusCode {
        case 304:
            return .unchanged
        case 404:
            return .missing
        case 200..<300:
            return .changed(data, etag: http.value(forHTTPHeaderField: "ETag"))
        default:
            throw SupabaseError.http(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
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
