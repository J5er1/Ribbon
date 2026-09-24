import SwiftUI
import PhotosUI
import RibbonCore

// S16 — accepting an invite. The screen shows a person, not a product:
// who is inviting, the room's name, one Join control. Account creation
// (§6.10 — no passwords) happens here when it has to, because joining is
// the first moment an account is genuinely needed — and it happens through
// `SignInInline`, the app's one sign-in thread, so a joiner's account is the
// same kind of account as everyone else's in the room.

struct JoinFlow: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let token: UUID
    /// Joined; the room is current. The caller decides what "arriving"
    /// looks like.
    var onDone: () -> Void
    /// Onboarding only: the quiet way out to starting a room of your own.
    var onStartInstead: (() -> Void)?
    /// What the dead end's way out is called. Presented over the room it
    /// closes; pushed inside the menu it goes back one step instead — to the
    /// paste field, which is exactly where "ask for a new one" lands.
    var wayOut: String = Copy.close

    enum Phase: Equatable {
        case loading
        case preview
        case name
        /// The one sign-in thread (§6.10), whatever it is on this build —
        /// this screen does not get its own.
        case signIn
        case joining
        case dead(String)   // expired, full, unreachable — the line to show
    }

    @State private var phase: Phase = .loading
    @State private var preview: InvitePreview?
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @State private var portraitData: Data?
    /// Set down mid-join (the sheet swiped away): the join completes —
    /// they did join — but arriving must not happen underneath them.
    @State private var wasSetDown = false
    /// "Join as someone else": the person this phone already is, declined
    /// for this room. The name step then runs as it would for a stranger.
    @State private var asSomeoneElse = false
    /// The name is on its way to the model: one person per tap, however
    /// quick the second tap is.
    @State private var committing = false
    @FocusState private var nameFocused: Bool

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            switch phase {
            case .loading:
                // A held beat, not a spinner. The preview answers fast or
                // the dead line takes its place.
                SmallCaps(Copy.wordmark, size: 12, color: Palette.muted.opacity(0.6))
            case .preview:
                previewStep
            case .name:
                nameStep
            case .signIn:
                signInStep
            case .joining:
                SmallCaps(Copy.joining, size: 12, color: Palette.muted)
            case .dead(let line):
                VStack(spacing: 18) {
                    Text(line)
                        .font(RibbonType.ui(17))
                        .foregroundStyle(Palette.text)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 44)
                    if let onStartInstead {
                        QuietControl(title: Copy.startARoomInstead, action: onStartInstead)
                    } else {
                        // Presented over the room: a dead end still needs
                        // its own way out, not only the swipe.
                        QuietControl(title: wayOut) { dismiss() }
                    }
                }
            }
            Spacer()
            Spacer()
        }
        // Every step of the join gives way to the next by cross-fade —
        // including the dead end and "Joining", which used to cut in.
        .animation(RibbonMotion.settle, value: phase)
        .frame(maxWidth: 420)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .room()
        .task { await loadPreview() }
        .onDisappear { wasSetDown = true }
    }

    // MARK: Steps

    private var previewStep: some View {
        VStack(spacing: 20) {
            // The inviter's face is not on this phone yet; their monogram
            // stands in, so the invitation is from a person and not a
            // product.
            if let inviter = preview?.inviterName, !inviter.isEmpty {
                PortraitView(person: Person(name: inviter), ink: nil, size: 64)
                    .accessibilityHidden(true)
            }
            Text(inviteLine)
                .font(RibbonType.display(24))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
                .accessibilityAddTraits(.isHeader)
            if let roomName = preview?.roomName, !roomName.isEmpty {
                SmallCaps(roomName, size: 13)
            }
            WayInButton(title: Copy.join) { advanceFromPreview() }
                .padding(.horizontal, 80)
                .padding(.top, 8)
            if let me = model.me, !asSomeoneElse {
                // Joining as the person this phone already is (A37): the
                // name is stated and can be declined, never re-asked.
                VStack(spacing: 6) {
                    Text(Copy.joiningAs(me.name))
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                    QuietControl(title: Copy.joinAsSomeoneElse) {
                        asSomeoneElse = true
                        name = ""
                        withAnimation(RibbonMotion.settle) { phase = .name }
                    }
                }
            }
            if let onStartInstead {
                QuietControl(title: Copy.startARoomInstead, action: onStartInstead)
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
                .animation(RibbonMotion.arrive, value: portraitData)
            }
            .buttonStyle(.pressable)
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
            CentredTextField(text: $name, prompt: Copy.yourName, submitLabel: .done, contentType: .name, focus: $nameFocused, onSubmit: advanceFromName)
                .padding(.horizontal, 40)
            WayInButton(title: Copy.thatsMe) { advanceFromName() }
                .padding(.horizontal, 80)
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                .opacity(name.trimmingCharacters(in: .whitespaces).isEmpty ? 0.3 : 1)
                .animation(RibbonMotion.arrive, value: name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .onAppear { nameFocused = true }
    }

    /// Joining is the first moment an account is genuinely needed, so this
    /// is where it happens — and it is the same sign-in the rest of the app
    /// offers (Auth0 where the build has it, a passkey where the platform
    /// does, an emailed code underneath either). A second, hand-rolled
    /// email-and-code pair here would quietly mint a *different* kind of
    /// account from everyone else's, and the room would seat a stranger.
    private var signInStep: some View {
        VStack(spacing: 20) {
            Text(Copy.accountReason)
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            SignInInline(onSignedIn: { join() })
                .padding(.horizontal, 24)
            if let onStartInstead {
                // Never a step without a way out.
                QuietControl(title: Copy.startARoomInstead, action: onStartInstead)
            }
        }
    }

    // MARK: Movement

    private func loadPreview() async {
        guard let remote = model.remote else {
            phase = .dead(Copy.serverUnreachable)
            return
        }
        var attempts = 0
        while attempts < 3 {
            do {
                if let found = try await remote.invitePreview(token: token) {
                    preview = found
                    if found.expired {
                        phase = .dead(Copy.inviteExpired)
                    } else if found.full {
                        phase = .dead(Copy.roomFullForJoiner)
                    } else {
                        withAnimation(RibbonMotion.arrive) { phase = .preview }
                    }
                    return
                }
            } catch {
                phase = .dead(Copy.serverUnreachable)
                return
            }
            attempts += 1
            if attempts < 3 {
                try? await Task.sleep(nanoseconds: 1_200_000_000)
            }
        }
        phase = .dead(Copy.inviteNotFound)
    }

    private func advanceFromPreview() {
        withAnimation(RibbonMotion.settle) {
            if model.me == nil {
                phase = .name
            } else if !model.isSignedIn {
                phase = .signIn
            } else {
                phase = .joining
            }
        }
        if phase == .joining { join() }
    }

    private func advanceFromName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, !committing else { return }
        committing = true
        Task {
            defer { committing = false }
            if asSomeoneElse, model.me != nil {
                // A different person on the same phone: the name and face
                // change, the account underneath does not — an account is
                // one person, and joining somebody's room as their spouse
                // from their phone is the same person with another name.
                model.updateMe(name: trimmed)
                if let portraitData { await model.setPortrait(portraitData) }
            } else {
                await model.completeOnboarding(name: trimmed, portraitData: portraitData, startRoom: false)
            }
            withAnimation(RibbonMotion.settle) {
                phase = model.isSignedIn ? .joining : .signIn
            }
            if phase == .joining { join() }
        }
    }

    private func join() {
        phase = .joining
        Task {
            do {
                let roomID = try await model.joinRoom(inviteToken: token)
                // Only if it is still the one this flow is about: a second
                // link tapped while this join was in flight has already
                // replaced it, and clearing that would throw away an invite
                // nobody has answered yet.
                if model.pendingInvite?.token == token { model.pendingInvite = nil }
                guard !wasSetDown else { return }
                model.switchRoom(to: roomID)
                onDone()
            } catch SupabaseError.notSignedIn {
                // A session this phone thought it had is gone (A37): the
                // sign-in step, not a dead end — the invite is still good.
                withAnimation(RibbonMotion.settle) { phase = .signIn }
            } catch SupabaseError.http(401, _) {
                withAnimation(RibbonMotion.settle) { phase = .signIn }
            } catch {
                phase = .dead(deadLine(for: error))
            }
        }
    }

    /// The database names what happened; the screen says it plainly.
    private func deadLine(for error: Error) -> String {
        if case SupabaseError.http(_, let body) = error {
            if body.contains("room_full") { return Copy.roomFullForJoiner }
            if body.contains("invite_expired") { return Copy.inviteExpired }
            if body.contains("invite_not_found") { return Copy.inviteNotFound }
        }
        return Copy.serverUnreachable
    }
}
