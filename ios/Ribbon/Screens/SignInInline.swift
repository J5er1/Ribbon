import Combine
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

    /// What this phone knows about how often it has asked for a code. The
    /// server is the enforcement (GoTrue's minute, the send-email hook's
    /// six an hour); this is only so the button can tell the truth instead
    /// of firing a request that will come back 429. It starts empty on a
    /// fresh sheet, which is fine — a refusal from the server sets the hold
    /// either way.
    @State private var window = SignInSendWindow()
    @State private var now = Date()
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var waitSeconds: Int { window.secondsUntilNextSend(now: now) }
    private var mayAskAgain: Bool { waitSeconds == 0 }

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
                // An inert button says so rather than sitting there dead:
                // while the minute is running the control names the wait.
                if mayAskAgain {
                    QuietControl(title: "Send a new code") { sendCode() }
                } else {
                    SmallCaps("Another code in \(waitSeconds)s", size: 13, color: Palette.muted)
                        .frame(minHeight: 44)
                }
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
        .onReceive(tick) { moment in
            // Only while a countdown is actually running — otherwise this
            // view would redraw once a second for the whole sitting. The
            // tick that crosses zero still lands, so the button comes back.
            guard window.secondsUntilNextSend(now: now) > 0 else { return }
            now = moment
        }
    }

    private func sendCode() {
        let address = email.trimmingCharacters(in: .whitespaces)
        guard address.contains("@"), !busy else { return }
        now = Date()
        guard window.maySend(now: now) else {
            errorLine = window.isHourlyLimit(now: now)
                ? Copy.tooManyCodes
                : Copy.codeAlreadySent(seconds: waitSeconds)
            return
        }
        busy = true
        errorLine = nil
        Task {
            defer { busy = false }
            do {
                try await model.sendSignInCode(to: address)
                now = Date()
                window.record(at: now)
                code = ""
                withAnimation(RibbonMotion.settle) { phase = .code }
            } catch SupabaseError.rateLimited(let retryAfter) {
                // The server refused: either GoTrue's minute or the hook's
                // hourly ceiling. Take its word over the local model — this
                // phone may have been asleep, or another one may have spent
                // the window.
                now = Date()
                let wait = retryAfter ?? SignInSendWindow.minimumInterval
                window.hold(until: now.addingTimeInterval(wait))
                errorLine = wait > SignInSendWindow.minimumInterval
                    ? Copy.tooManyCodes
                    : Copy.codeAlreadySent(seconds: window.secondsUntilNextSend(now: now))
                // A refused code is still a code the reader is waiting for,
                // so the code field is where they should be either way.
                if phase == .email {
                    withAnimation(RibbonMotion.settle) { phase = .code }
                }
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
