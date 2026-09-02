import SwiftUI
import RibbonCore

// The account moment (§6.10): an email, then the emailed code, no
// passwords — and never a wall. It appears inside whichever path needs it
// (the invite step, the join flow, You), with the one true reason stated
// for that host, a primary control, and a quiet way past where the host
// has one. Errors say what happened and what didn't (S25).

struct AccountStep: View {
    @Environment(\.appModel) private var model
    /// The one reason an account is being asked for here.
    var reason: String
    /// What the code's primary control says — "Continue" in a thread,
    /// "Sign in" in settings.
    var primaryTitle: String = Copy.continueControl
    /// Called once the session exists and the local graph has synced.
    var onSignedIn: () -> Void = {}
    /// The quiet way past, labelled by the host. Nil means the host has
    /// its own.
    var skipTitle: String?
    var onSkip: (() -> Void)?

    enum Phase: Equatable { case email, code }
    enum Field { case email, code }

    @State private var phase: Phase = .email
    @State private var email = ""
    @State private var code = ""
    @State private var errorLine: String?
    @State private var busy = false
    @FocusState private var focused: Field?

    private var address: String { email.trimmingCharacters(in: .whitespaces) }
    private var emailLooksRight: Bool { address.contains("@") && address.contains(".") }

    var body: some View {
        VStack(spacing: 18) {
            switch phase {
            case .email:
                Text(reason)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)
                RibbonTextField(prompt: Copy.yourEmail, text: $email, centered: true)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .focused($focused, equals: .email)
                    .submitLabel(.send)
                    .onSubmit(sendCode)
                WayInButton(title: Copy.sendTheCode) { sendCode() }
                    .disabled(!emailLooksRight || busy)
                    .opacity(!emailLooksRight ? 0.3 : (busy ? 0.6 : 1))
            case .code:
                Text(Copy.codeOnItsWay(address))
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 8)
                RibbonTextField(prompt: Copy.theCode, text: $code, centered: true, size: 22)
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($focused, equals: .code)
                    .submitLabel(.done)
                    .onSubmit(verify)
                WayInButton(title: primaryTitle) { verify() }
                    .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty || busy)
                    .opacity(code.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : (busy ? 0.6 : 1))
                HStack(spacing: 22) {
                    QuietControl(title: Copy.sendANewCode) { sendCode() }
                    QuietControl(title: Copy.useADifferentEmail) {
                        errorLine = nil
                        code = ""
                        withAnimation(RibbonMotion.settle) { phase = .email }
                    }
                }
            }
            if let errorLine {
                Text(errorLine)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .transition(.opacity)
            }
            if let skipTitle, let onSkip {
                QuietControl(title: skipTitle, action: onSkip)
            }
        }
        .onAppear { focused = .email }
        .onChange(of: phase) { _, new in focused = new == .email ? .email : .code }
    }

    private func sendCode() {
        guard emailLooksRight, !busy else { return }
        busy = true
        errorLine = nil
        Task {
            defer { busy = false }
            do {
                try await model.sendSignInCode(to: address)
                code = ""
                withAnimation(RibbonMotion.settle) { phase = .code }
            } catch let error as SupabaseError where error.isRateLimited {
                errorLine = Copy.tooManyCodes
            } catch let error as SupabaseError where error.isRefusal {
                errorLine = Copy.emailDidntTake
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
                try await model.verifySignInCode(email: address, code: entered)
                onSignedIn()
            } catch SupabaseError.differentAccount {
                errorLine = Copy.differentAccountHere
            } catch let error as SupabaseError where error.isRefusal {
                errorLine = Copy.signInCodeWrong
            } catch {
                errorLine = Copy.serverUnreachable
            }
        }
    }
}
