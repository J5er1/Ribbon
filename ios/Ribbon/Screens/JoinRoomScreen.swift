import SwiftUI
import PhotosUI
import RibbonCore

// S16 — accepting an invite. The screen shows a person, not a product:
// who is inviting, the room's name, one Join control. Account creation
// (§6.10 — an emailed code, no passwords) happens here when it has to,
// because joining is the first moment an account is genuinely needed.

struct JoinFlow: View {
    @Environment(AppModel.self) private var model
    let token: UUID
    /// Joined; the room is current. The caller decides what "arriving"
    /// looks like.
    var onDone: () -> Void
    /// Onboarding only: the quiet way out to starting a room of your own.
    var onStartInstead: (() -> Void)?

    enum Phase: Equatable {
        case loading
        case preview
        case name
        case email
        case code
        case joining
        case dead(String)   // expired, full, unreachable — the line to show
    }

    @State private var phase: Phase = .loading
    @State private var preview: InvitePreview?
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @State private var portraitData: Data?
    @State private var email = ""
    @State private var code = ""
    @State private var errorLine: String?
    @State private var sendingCode = false
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            switch phase {
            case .loading:
                // A held beat, not a spinner. The preview answers fast or
                // the dead line takes its place.
                SmallCaps("ribbon", size: 12, color: Palette.muted.opacity(0.6))
            case .preview:
                previewStep
            case .name:
                nameStep
            case .email:
                emailStep
            case .code:
                codeStep
            case .joining:
                SmallCaps("joining", size: 12, color: Palette.muted)
            case .dead(let line):
                VStack(spacing: 18) {
                    Text(line)
                        .font(RibbonType.ui(17))
                        .foregroundStyle(Palette.text)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 44)
                    if let onStartInstead {
                        QuietControl(title: "Start a room instead", action: onStartInstead)
                    }
                }
            }
            Spacer()
            Spacer()
        }
        .frame(maxWidth: 420)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .room()
        .task { await loadPreview() }
    }

    // MARK: Steps

    private var previewStep: some View {
        VStack(spacing: 20) {
            Text(inviteLine)
                .font(RibbonType.display(24))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            if let roomName = preview?.roomName, !roomName.isEmpty {
                SmallCaps(roomName, size: 13)
            }
            WayInButton(title: Copy.join) { advanceFromPreview() }
                .padding(.horizontal, 80)
                .padding(.top, 8)
            if let onStartInstead {
                QuietControl(title: "Start a room instead", action: onStartInstead)
            }
        }
    }

    private var inviteLine: String {
        if let inviter = preview?.inviterName, !inviter.isEmpty {
            return Copy.wantsToReadWithYou(firstName(inviter))
        }
        return Copy.someoneWantsToReadWithYou
    }

    private var nameStep: some View {
        VStack(spacing: 24) {
            PhotosPicker(selection: $portraitItem, matching: .images) {
                ZStack {
                    if let portraitData, let image = UIImage(data: portraitData) {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 96, height: 96)
                            .clipShape(Circle())
                    } else {
                        Circle()
                            .fill(Palette.surface)
                            .overlay(Circle().strokeBorder(Palette.rule, lineWidth: 1))
                            .frame(width: 96, height: 96)
                        SmallCaps(Copy.addAPortrait, size: 11)
                    }
                }
            }
            .onChange(of: portraitItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self) {
                        portraitData = downsampledJPEG(data)
                    }
                }
            }
            Text(Copy.portraitReason)
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.muted)
            TextField("", text: $name, prompt: Text(Copy.yourName).foregroundStyle(Palette.muted))
                .font(RibbonType.ui(20))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .focused($focused)
                .padding(.horizontal, 40)
                .submitLabel(.done)
                .onSubmit(advanceFromName)
            WayInButton(title: "That's me") { advanceFromName() }
                .padding(.horizontal, 80)
                .opacity(name.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
        }
        .onAppear { focused = true }
    }

    private var emailStep: some View {
        VStack(spacing: 20) {
            Text(Copy.accountReason)
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            TextField("", text: $email, prompt: Text(Copy.yourEmail).foregroundStyle(Palette.muted))
                .font(RibbonType.ui(18))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focused)
                .padding(.horizontal, 40)
                .submitLabel(.send)
                .onSubmit(sendCode)
            if let errorLine {
                Text(errorLine)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
            }
            WayInButton(title: Copy.sendTheCode) { sendCode() }
                .padding(.horizontal, 80)
                .opacity(email.contains("@") ? 1 : 0.3)
            if let onStartInstead {
                // Never a step without a way out.
                QuietControl(title: "Start a room instead", action: onStartInstead)
            }
        }
        .onAppear { focused = true }
    }

    private var codeStep: some View {
        VStack(spacing: 20) {
            Text(Copy.codeOnItsWay)
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            TextField("", text: $code, prompt: Text(Copy.theCode).foregroundStyle(Palette.muted))
                .font(RibbonType.ui(22))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($focused)
                .padding(.horizontal, 60)
            if let errorLine {
                Text(errorLine)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
            }
            WayInButton(title: Copy.join) { verifyAndJoin() }
                .padding(.horizontal, 80)
                .opacity(code.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
            QuietControl(title: "Send a new code") { sendCode() }
            if let onStartInstead {
                QuietControl(title: "Start a room instead", action: onStartInstead)
            }
        }
        .onAppear { focused = true }
    }

    // MARK: Movement

    private func loadPreview() async {
        guard let remote = model.remote else {
            phase = .dead(Copy.serverUnreachable)
            return
        }
        do {
            guard let found = try await remote.invitePreview(token: token) else {
                phase = .dead(Copy.inviteExpired)
                return
            }
            preview = found
            if found.expired {
                phase = .dead(Copy.inviteExpired)
            } else if found.full {
                phase = .dead(Copy.roomHoldsSix)
            } else {
                withAnimation(RibbonMotion.arrive) { phase = .preview }
            }
        } catch {
            phase = .dead(Copy.serverUnreachable)
        }
    }

    private func advanceFromPreview() {
        withAnimation(RibbonMotion.settle) {
            if model.me == nil {
                phase = .name
            } else if !model.isSignedIn {
                phase = .email
            } else {
                phase = .joining
            }
        }
        if phase == .joining { join() }
    }

    private func advanceFromName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        Task {
            await model.completeOnboarding(name: trimmed, portraitData: portraitData, startRoom: false)
            withAnimation(RibbonMotion.settle) {
                phase = model.isSignedIn ? .joining : .email
            }
            if phase == .joining { join() }
        }
    }

    private func sendCode() {
        let address = email.trimmingCharacters(in: .whitespaces)
        guard address.contains("@"), !sendingCode else { return }
        sendingCode = true
        errorLine = nil
        Task {
            defer { sendingCode = false }
            do {
                try await model.sendSignInCode(to: address)
                code = ""
                withAnimation(RibbonMotion.settle) { phase = .code }
            } catch {
                errorLine = Copy.serverUnreachable
            }
        }
    }

    private func verifyAndJoin() {
        let entered = code.trimmingCharacters(in: .whitespaces)
        guard !entered.isEmpty else { return }
        errorLine = nil
        Task {
            do {
                try await model.verifySignInCode(
                    email: email.trimmingCharacters(in: .whitespaces), code: entered)
            } catch {
                errorLine = Copy.signInCodeWrong
                return
            }
            join()
        }
    }

    private func join() {
        phase = .joining
        Task {
            do {
                _ = try await model.joinRoom(inviteToken: token)
                model.pendingInvite = nil
                onDone()
            } catch {
                phase = .dead(deadLine(for: error))
            }
        }
    }

    /// The database names what happened; the screen says it plainly.
    private func deadLine(for error: Error) -> String {
        if case SupabaseError.http(_, let body) = error {
            if body.contains("room_full") { return Copy.roomHoldsSix }
            if body.contains("invite_expired") { return Copy.inviteExpired }
        }
        return Copy.serverUnreachable
    }
}
