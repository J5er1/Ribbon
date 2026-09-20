import AuthenticationServices
import Foundation
import UIKit

// §6.10: "Sign-in is a passkey where available, an emailed code otherwise."
// This is the passkey half — the ceremony, run by the platform.
//
// Supabase Auth hands out a challenge and the W3C options that go with it;
// AuthenticationServices puts the Face ID sheet on the screen and returns a
// signed credential; the signed credential goes back and, for a sign-in, a
// session comes out. Nothing about the account is typed, and on a new phone
// nothing about it is even asked: passkeys here are discoverable, so the
// authenticator names the account itself.
//
// It works only where the domain says it does. `readribbon.app` must serve a
// `webcredentials` section in its apple-app-site-association (web/build.mjs
// emits it), the app must claim `webcredentials:readribbon.app` in its
// entitlement, and the project must have passkeys turned on with that same
// bare domain as the relying party. Until all three are true this offers
// nothing and says nothing — the emailed code is the way in, as it always
// was, and a passkey is only ever an addition to it.

/// The ceremony, and the two shapes it produces.
///
/// A `@MainActor` object rather than free functions because
/// `ASAuthorizationController` needs a presentation anchor and a delegate,
/// and both are the window's business.
@MainActor
final class Passkeys: NSObject {

    /// Whether this device can be asked at all. Below iOS 16 there is no
    /// platform authenticator to ask, and there is no control to draw.
    static var isAvailable: Bool {
        if #available(iOS 16.0, *) { return true }
        return false
    }

    enum Failure: Error {
        /// The person dismissed the sheet, or no credential was offered.
        /// Never an error surface (§25): a passkey declined is not a
        /// failure, it is a person choosing the other way in.
        case cancelled
        /// The options the server sent were not the shape they must be.
        case malformedOptions
        /// The authenticator returned something this build can't encode.
        case malformedCredential
    }

    private var anchor: ASPresentationAnchor?
    private var pending: CheckedContinuation<ASAuthorization, Error>?

    // MARK: The two ceremonies

    /// Make a passkey for this account. The response is the WebAuthn JSON
    /// the verify endpoint expects.
    @available(iOS 16.0, *)
    func register(optionsJSON: Data, anchor: ASPresentationAnchor) async throws -> Data {
        guard
            let options = try? JSONSerialization.jsonObject(with: optionsJSON) as? [String: Any],
            let relyingParty = options["rp"] as? [String: Any],
            let rpID = relyingParty["id"] as? String,
            let challenge = base64URLDecoded(options["challenge"]),
            let user = options["user"] as? [String: Any],
            let userID = base64URLDecoded(user["id"]),
            let name = user["name"] as? String
        else { throw Failure.malformedOptions }

        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(
            relyingPartyIdentifier: rpID)
        let request = provider.createCredentialRegistrationRequest(
            challenge: challenge, name: name, userID: userID)

        let authorization = try await perform([request], anchor: anchor)
        guard
            let credential = authorization.credential
                as? ASAuthorizationPlatformPublicKeyCredentialRegistration,
            let attestation = credential.rawAttestationObject
        else { throw Failure.malformedCredential }

        return try JSONSerialization.data(withJSONObject: [
            "id": base64URLEncoded(credential.credentialID),
            "rawId": base64URLEncoded(credential.credentialID),
            "type": "public-key",
            "response": [
                "clientDataJSON": base64URLEncoded(credential.rawClientDataJSON),
                "attestationObject": base64URLEncoded(attestation),
            ],
        ])
    }

    /// Sign in with a passkey already on this device or in the person's
    /// keychain. Discoverable: no account is named going in.
    @available(iOS 16.0, *)
    func assert(optionsJSON: Data, anchor: ASPresentationAnchor) async throws -> Data {
        guard
            let options = try? JSONSerialization.jsonObject(with: optionsJSON) as? [String: Any],
            let rpID = options["rpId"] as? String,
            let challenge = base64URLDecoded(options["challenge"])
        else { throw Failure.malformedOptions }

        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(
            relyingPartyIdentifier: rpID)
        let request = provider.createCredentialAssertionRequest(challenge: challenge)

        let authorization = try await perform([request], anchor: anchor)
        guard
            let credential = authorization.credential
                as? ASAuthorizationPlatformPublicKeyCredentialAssertion
        else { throw Failure.malformedCredential }

        var response: [String: Any] = [
            "clientDataJSON": base64URLEncoded(credential.rawClientDataJSON),
            "authenticatorData": base64URLEncoded(credential.rawAuthenticatorData),
            "signature": base64URLEncoded(credential.signature),
        ]
        if let handle = credential.userID {
            response["userHandle"] = base64URLEncoded(handle)
        }
        return try JSONSerialization.data(withJSONObject: [
            "id": base64URLEncoded(credential.credentialID),
            "rawId": base64URLEncoded(credential.credentialID),
            "type": "public-key",
            "response": response,
        ])
    }

    // MARK: Plumbing

    private func perform(
        _ requests: [ASAuthorizationRequest], anchor: ASPresentationAnchor
    ) async throws -> ASAuthorization {
        self.anchor = anchor
        return try await withCheckedThrowingContinuation { continuation in
            pending = continuation
            let controller = ASAuthorizationController(authorizationRequests: requests)
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    private func finish(_ result: Result<ASAuthorization, Error>) {
        // The delegate can be called more than once on some paths; the
        // continuation may be resumed exactly once.
        guard let continuation = pending else { return }
        pending = nil
        continuation.resume(with: result)
    }

    /// base64url in, `Data` out — the encoding every ArrayBuffer field in
    /// the WebAuthn options arrives in.
    private func base64URLDecoded(_ value: Any?) -> Data? {
        guard let string = value as? String else { return nil }
        var padded = string
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        padded += String(repeating: "=", count: (4 - padded.count % 4) % 4)
        return Data(base64Encoded: padded)
    }

    private func base64URLEncoded(_ data: Data) -> String {
        data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
}

extension Passkeys: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        // Synchronously, and without a Task: `ASAuthorization` is not
        // Sendable, so it may not be captured across one. The system calls
        // this delegate on the main thread, which is what is being assumed.
        MainActor.assumeIsolated { self.finish(.success(authorization)) }
    }

    nonisolated func authorizationController(
        controller: ASAuthorizationController, didCompleteWithError error: Error
    ) {
        // Cancelled and "no credentials" are the same thing to this app: the
        // person is not using a passkey right now, and the email field is
        // still there.
        let failure: Error
        if let authError = error as? ASAuthorizationError,
           authError.code == .canceled || authError.code == .unknown {
            failure = Failure.cancelled
        } else {
            failure = error
        }
        MainActor.assumeIsolated { self.finish(.failure(failure)) }
    }
}

extension Passkeys: ASAuthorizationControllerPresentationContextProviding {
    nonisolated func presentationAnchor(
        for controller: ASAuthorizationController
    ) -> ASPresentationAnchor {
        // `perform` sets the anchor before it builds the controller, and
        // every caller has already refused to start without one, so the
        // ceremony never actually gets past the first of these. What stands
        // behind it is the window the person is looking at, and then any
        // window the app has — never a window made up on the spot, which
        // could not have presented anything anyway. iOS 26 says as much by
        // deprecating the way to make one.
        MainActor.assumeIsolated { anchor ?? keyWindowAnchor() ?? anyWindowAnchor() }
    }
}

/// The window the sheet hangs off. SwiftUI does not hand one out, and the
/// ceremony needs one; this is the active scene's, which is the window the
/// person is looking at.
@MainActor
func keyWindowAnchor() -> ASPresentationAnchor? {
    UIApplication.shared.connectedScenes
        .compactMap { $0 as? UIWindowScene }
        .first { $0.activationState == .foregroundActive }?
        .keyWindow
}

/// The last resort behind `keyWindowAnchor()`: any window the app already
/// has, and failing that a window belonging to a scene it already has. An
/// app with neither is an app with nothing on screen, which is not a state
/// a passkey ceremony can be started from.
@MainActor
private func anyWindowAnchor() -> ASPresentationAnchor {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let inFront = scenes.first { $0.activationState == .foregroundActive }
    if let window = inFront?.windows.first ?? scenes.flatMap(\.windows).first {
        return window
    }
    if let scene = inFront ?? scenes.first {
        return ASPresentationAnchor(windowScene: scene)
    }
    // No scenes at all. Nothing could be presented from here whatever we
    // return, and a window is still owed: an empty one is a poor answer,
    // but trapping in its place would be a worse one.
    return ASPresentationAnchor(frame: .zero)
}
