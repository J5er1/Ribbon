import AuthenticationServices
import CommonCrypto
import Foundation
import UIKit

/// Result of an Auth0 authentication ceremony.
public struct Auth0User: Sendable {
    public let idToken: String
    public let userUUID: UUID
    public let email: String?
    public let refreshToken: String

    public init(idToken: String, userUUID: UUID, email: String?, refreshToken: String = "") {
        self.idToken = idToken
        self.userUUID = userUUID
        self.email = email
        self.refreshToken = refreshToken
    }
}

public enum Auth0Error: LocalizedError, Sendable {
    case notConfigured
    case cancelled
    case invalidCallback
    case tokenExchangeFailed(String)
    case malformedToken

    public var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Auth0 domain or client ID is not configured."
        case .cancelled:
            return "Authentication was cancelled."
        case .invalidCallback:
            return "Invalid callback URL received from Auth0."
        case .tokenExchangeFailed(let details):
            return "Failed to exchange authorization code: \(details)"
        case .malformedToken:
            return "Malformed ID token."
        }
    }
}

@MainActor
private final class WebAuthContextProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
    private let anchor: ASPresentationAnchor

    init(anchor: ASPresentationAnchor) {
        self.anchor = anchor
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        anchor
    }
}

/// Service managing Auth0 Universal Login authentication, PKCE flow,
/// and token parsing for Supabase integration.
public enum Auth0Service {
    public static let namespaceDNS = "6ba7b810-9dad-11d1-80b4-00c04fd430c8"

    /// Performs the Auth0 Universal Login flow using ASWebAuthenticationSession
    /// with PKCE (RFC 7636). Returns authenticated credentials.
    @MainActor
    public static func login(anchor: ASPresentationAnchor) async throws -> Auth0User {
        guard Auth0Config.isConfigured else {
            throw Auth0Error.notConfigured
        }

        let domain = Auth0Config.domain.trimmingCharacters(in: .whitespacesAndNewlines)
        let clientId = Auth0Config.clientId.trimmingCharacters(in: .whitespacesAndNewlines)
        let scheme = Auth0Config.scheme.trimmingCharacters(in: .whitespacesAndNewlines)
        let bundleId = Bundle.main.bundleIdentifier ?? "bible.ribbon.app"
        let redirectURI = "\(scheme)://\(domain)/ios/\(bundleId)/callback"

        let (verifier, challenge) = generatePKCE()
        let state = UUID().uuidString.lowercased()

        var components = URLComponents(string: "https://\(domain)/authorize")
        components?.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "redirect_uri", value: redirectURI),
            URLQueryItem(name: "scope", value: "openid profile email offline_access"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: state)
        ]

        guard let authURL = components?.url else {
            throw Auth0Error.notConfigured
        }

        let contextProvider = WebAuthContextProvider(anchor: anchor)

        let callbackURL: URL = try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authURL,
                callbackURLScheme: scheme
            ) { callbackURL, error in
                if let error = error as? ASWebAuthenticationSessionError, error.code == .canceledLogin {
                    continuation.resume(throwing: Auth0Error.cancelled)
                } else if let error {
                    continuation.resume(throwing: error)
                } else if let callbackURL {
                    continuation.resume(returning: callbackURL)
                } else {
                    continuation.resume(throwing: Auth0Error.cancelled)
                }
            }
            session.presentationContextProvider = contextProvider
            session.prefersEphemeralWebBrowserSession = false
            session.start()
        }

        guard let urlComponents = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false),
              let queryItems = urlComponents.queryItems else {
            throw Auth0Error.invalidCallback
        }

        if let errorParam = queryItems.first(where: { $0.name == "error" })?.value {
            let desc = queryItems.first(where: { $0.name == "error_description" })?.value ?? errorParam
            throw Auth0Error.tokenExchangeFailed(desc)
        }

        guard let code = queryItems.first(where: { $0.name == "code" })?.value,
              let returnedState = queryItems.first(where: { $0.name == "state" })?.value,
              returnedState == state else {
            throw Auth0Error.invalidCallback
        }

        // Exchange authorization code for tokens
        guard let tokenURL = URL(string: "https://\(domain)/oauth/token") else {
            throw Auth0Error.notConfigured
        }

        var request = URLRequest(url: tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let bodyDict: [String: String] = [
            "grant_type": "authorization_code",
            "client_id": clientId,
            "code_verifier": verifier,
            "code": code,
            "redirect_uri": redirectURI
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: bodyDict)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            let errorText = String(data: data, encoding: .utf8) ?? "HTTP status \((response as? HTTPURLResponse)?.statusCode ?? 0)"
            throw Auth0Error.tokenExchangeFailed(errorText)
        }

        guard let tokenJSON = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let idToken = tokenJSON["id_token"] as? String else {
            throw Auth0Error.tokenExchangeFailed("Missing id_token in token response")
        }

        let refreshToken = tokenJSON["refresh_token"] as? String ?? ""
        let (userUUID, email) = try parseIdToken(idToken)

        return Auth0User(
            idToken: idToken,
            userUUID: userUUID,
            email: email,
            refreshToken: refreshToken
        )
    }

    /// Refreshes the Auth0 session using a refresh token, returning a fresh ID token and new refresh token.
    public static func refreshTokens(refreshToken: String) async throws -> (idToken: String, refreshToken: String) {
        guard Auth0Config.isConfigured else { throw Auth0Error.notConfigured }

        let domain = Auth0Config.domain.trimmingCharacters(in: .whitespacesAndNewlines)
        let clientId = Auth0Config.clientId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let tokenURL = URL(string: "https://\(domain)/oauth/token") else {
            throw Auth0Error.notConfigured
        }

        var request = URLRequest(url: tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let bodyDict: [String: String] = [
            "grant_type": "refresh_token",
            "client_id": clientId,
            "refresh_token": refreshToken
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: bodyDict)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            let errorText = String(data: data, encoding: .utf8) ?? "HTTP status \((response as? HTTPURLResponse)?.statusCode ?? 0)"
            throw Auth0Error.tokenExchangeFailed(errorText)
        }

        guard let tokenJSON = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let idToken = tokenJSON["id_token"] as? String else {
            throw Auth0Error.tokenExchangeFailed("Missing id_token in refresh response")
        }

        let newRefreshToken = tokenJSON["refresh_token"] as? String ?? refreshToken
        return (idToken, newRefreshToken)
    }

    /// Parses an Auth0 ID token (JWT) without signature verification (which is verified by Supabase).
    /// Extracts `user_uuid` or calculates a deterministic RFC 4122 Version 5 UUID from `sub`.
    public static func parseIdToken(_ token: String) throws -> (userUUID: UUID, email: String?) {
        let parts = token.components(separatedBy: ".")
        guard parts.count >= 2 else { throw Auth0Error.malformedToken }

        var base64 = parts[1]
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 {
            base64.append("=")
        }

        guard let data = Data(base64Encoded: base64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw Auth0Error.malformedToken
        }

        let sub = json["sub"] as? String ?? ""
        let email = json["email"] as? String

        let userUUID: UUID
        if let customUUID = json["user_uuid"] as? String, let parsed = UUID(uuidString: customUUID) {
            userUUID = parsed
        } else {
            userUUID = computeUuidV5(name: sub)
        }

        return (userUUID, email)
    }

    /// Generates a deterministic RFC 4122 Version 5 UUID using SHA-1 and the standard DNS namespace.
    public static func computeUuidV5(name: String, namespace: String = namespaceDNS) -> UUID {
        guard let ns = UUID(uuidString: namespace) else { return UUID() }
        var nsBytes = ns.uuid
        var data = Data(bytes: &nsBytes, count: 16)
        if let nameData = name.data(using: .utf8) {
            data.append(nameData)
        }
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA1_DIGEST_LENGTH))
        data.withUnsafeBytes { buffer in
            _ = CC_SHA1(buffer.baseAddress, CC_LONG(buffer.count), &digest)
        }
        digest[6] = (digest[6] & 0x0F) | 0x50
        digest[8] = (digest[8] & 0x3F) | 0x80

        let uuidTuple: uuid_t = (
            digest[0], digest[1], digest[2], digest[3],
            digest[4], digest[5], digest[6], digest[7],
            digest[8], digest[9], digest[10], digest[11],
            digest[12], digest[13], digest[14], digest[15]
        )
        return UUID(uuid: uuidTuple)
    }

    private static func base64URLEncode(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .trimmingCharacters(in: CharacterSet(charactersIn: "="))
    }

    private static func generatePKCE() -> (verifier: String, challenge: String) {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let verifier = base64URLEncode(Data(bytes))

        let verifierData = verifier.data(using: .utf8) ?? Data()
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        verifierData.withUnsafeBytes { buffer in
            _ = CC_SHA256(buffer.baseAddress, CC_LONG(buffer.count), &digest)
        }
        let challenge = base64URLEncode(Data(digest))
        return (verifier, challenge)
    }
}
