import SwiftUI
import RibbonCore

// The sign-in thread, inline (§6.10): an email, then the emailed code, no
// passwords. Small enough to sit inside whatever surface needs an account
// — the invite sheet (a link only works signed in), onboarding's invite
// step, and You. Never a wall: every host keeps its own quiet way past.

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
            switch phase {
            case .email:
                TextField("", text: $email, prompt: Text(Copy.yourEmail).foregroundStyle(Palette.muted))
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focused)
                    .submitLabel(.send)
                    .onSubmit(sendCode)
                WayInButton(title: Copy.sendTheCode) { sendCode() }
                    .padding(.horizontal, 40)
                    .disabled(!email.contains("@"))
                    .opacity(email.contains("@") ? 1 : 0.3)
            case .code:
                Text(Copy.codeOnItsWay)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                TextField("", text: $code, prompt: Text(Copy.theCode).foregroundStyle(Palette.muted))
                    .font(RibbonType.ui(20))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focused)
                    .submitLabel(.done)
                    .onSubmit(verify)
                WayInButton(title: Copy.signIn) { verify() }
                    .padding(.horizontal, 40)
                    .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty)
                    .opacity(code.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
                QuietControl(title: "Send a new code") { sendCode() }
            }
            if let errorLine {
                Text(errorLine)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
            }
            if let onCancel {
                QuietControl(title: "Never mind", action: onCancel)
            }
        }
        .onAppear { focused = true }
        .onChange(of: phase) { _, _ in focused = true }
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
            } catch {
                errorLine = Copy.signInCodeWrong
            }
        }
    }
}
