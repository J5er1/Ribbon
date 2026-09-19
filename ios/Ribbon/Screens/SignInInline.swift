import SwiftUI
import RibbonCore

// The sign-in thread, inline (§6.10): a passkey where there is one, an
// emailed code otherwise, no passwords. Small enough to sit inside whatever
// surface needs an account — the invite sheet (a link only works signed in),
// onboarding's invite step, and the menu. Never a wall: every host keeps its
// own quiet way past.
//
// The passkey is offered above the field rather than instead of it. It is
// one tap and no email round-trip, and on a phone that has never seen this
// account it still knows which account it is — but it exists only where the
// domain, the entitlement and the project all agree (Passkeys.swift), so the
// field underneath is the thread that always works.

struct SignInInline: View {
    @Environment(AppModel.self) private var model
    /// Called once the session exists and the local graph has synced.
    var onSignedIn: () -> Void = {}
    var onCancel: (() -> Void)?

    enum Phase: Equatable { case email, code }
    @State private var phase: Phase = .email
    @State private var email = ""
    @State private var code = ""
    @State private var errorLine: String?
    @State private var busy = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 16) {
            if phase == .email {
                // What the button does, not whose service it is (A46): a
                // browser sign-in where the build has one, a passkey where
                // the platform and the project both do, and the emailed
                // code underneath either — the thread that always works.
                if model.auth0Available {
                    WayInButton(title: Copy.signInInABrowser) { signInWithAuth0() }
                        .padding(.horizontal, 30)
                }
                if model.passkeysAvailable {
                    QuietControl(title: Copy.useAPasskey) { signInWithPasskey() }
                }
            }
            switch phase {
            case .email:
                CentredTextField(
                    text: $email, prompt: Copy.yourEmail, submitLabel: .send,
                    keyboard: .emailAddress, contentType: .emailAddress, onSubmit: sendCode)
                .focused($focused)
                WayInButton(title: Copy.sendTheCode) { sendCode() }
                    .padding(.horizontal, 40)
                    .disabled(!email.contains("@"))
                    .opacity(email.contains("@") ? 1 : 0.3)
            case .code:
                Text(Copy.codeOnItsWay)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                CentredTextField(
                    text: $code, prompt: Copy.theCode, submitLabel: .done,
                    keyboard: .numberPad, contentType: .oneTimeCode, onSubmit: verify)
                .focused($focused)
                WayInButton(title: Copy.signIn) { verify() }
                    .padding(.horizontal, 40)
                    .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty)
                    .opacity(code.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
                QuietControl(title: Copy.sendANewCode) { sendCode() }
            }
            if let errorLine {
                Text(errorLine)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
            }
            if let onCancel {
                QuietControl(title: Copy.neverMind, action: onCancel)
            }
        }
        .onAppear { focused = true }
        .onChange(of: phase) { _, _ in focused = true }
    }

    private func signInWithAuth0() {
        guard !busy else { return }
        busy = true
        errorLine = nil
        Task {
            defer { busy = false }
            do {
                try await model.signInWithAuth0()
                onSignedIn()
            } catch Auth0Error.cancelled {
                // User dismissed the browser sheet; silent (§6.10).
            } catch {
                errorLine = Copy.auth0DidntWork
            }
        }
    }

    private func signInWithPasskey() {
        guard !busy else { return }
        busy = true
        errorLine = nil
        Task {
            defer { busy = false }
            do {
                try await model.signInWithPasskey()
                onSignedIn()
            } catch Passkeys.Failure.cancelled {
                // Not an error: they looked at the sheet and chose the
                // email field instead. Say nothing.
            } catch {
                errorLine = Copy.passkeyDidntWork
            }
        }
    }

    private func sendCode() {
        let address = email.trimmingCharacters(in: .whitespaces)
        guard address.contains("@"), !busy else { return }
        busy = true
        errorLine = nil
        Task {
            defer { busy = false }
            do {
                try await model.sendSignInCode(to: address)
                code = ""
                withAnimation(RibbonMotion.settle) { phase = .code }
            } catch {
                errorLine = Copy.serverUnreachable
            }
        }
    }

    private func verify() {
        let entered = code.trimmingCharacters(in: .whitespaces)
        guard !entered.isEmpty, !busy else { return }
        busy = true
        errorLine = nil
        Task {
            defer { busy = false }
            do {
                try await model.verifySignInCode(
                    email: email.trimmingCharacters(in: .whitespaces), code: entered)
                onSignedIn()
            } catch SupabaseError.http(let status, _) where (400..<500).contains(status) {
                // The server looked at the code and said no.
                errorLine = Copy.signInCodeWrong
            } catch {
                // Anything else is the network, and "that code didn't work"
                // would send them typing it again into the same silence
                // (A44).
                errorLine = Copy.serverUnreachable
            }
        }
    }
}
