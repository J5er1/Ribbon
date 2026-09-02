import SwiftUI
import RibbonCore

// S15 — inviting. The link is the whole mechanism: no contact-list
// permission, no email field, no invite-by-username. Copy assumes one
// person. The link is registered with the backend before the share sheet
// opens — a link handed out first would tell a fast joiner it has expired
// (S16, S25) — and where the account is needed for that, it is asked for
// here, with the reason, and never as a wall.

/// The invite, as a step: the heading, the account moment if it's needed,
/// the share control once the link is real, and whatever the host offers
/// after — "Pick a book" in the thread, nothing in a sheet.
struct InviteStep: View {
    @Environment(AppModel.self) private var model
    let room: Room
    /// The host's primary control after the invite (the thread's "Pick a
    /// book"), if any.
    var after: (title: String, action: () -> Void)?
    /// The host's quiet way past ("Invite later", "Read on your own for
    /// now").
    var later: (title: String, action: () -> Void)?

    @State private var invite: Invite?
    @State private var registering = false
    @State private var line: String?
    @State private var shared = false

    private var needsAccount: Bool { model.remote != nil && !model.isSignedIn }

    var body: some View {
        VStack(spacing: 22) {
            if model.isFull(room) {
                Text(Copy.roomHoldsSix)
                    .font(RibbonType.ui(16))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            } else {
                Text(Copy.inviteSend)
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)

                if needsAccount {
                    AccountStep(
                        reason: Copy.emailReasonStarter,
                        onSignedIn: { Task { await register() } },
                        skipTitle: later?.title,
                        onSkip: later?.action)
                    .padding(.horizontal, 8)
                } else {
                    shareControl
                    if let line {
                        Text(line)
                            .font(RibbonType.ui(14))
                            .foregroundStyle(Palette.muted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }
                    if let after {
                        WayInButton(title: after.title, action: after.action)
                            .padding(.top, 6)
                    }
                    if let later, !shared {
                        QuietControl(title: later.title, action: later.action)
                    }
                }
            }
        }
        .task { await register() }
    }

    @ViewBuilder
    private var shareControl: some View {
        if let invite, model.isRegistered(invite) || line != nil {
            // The link is real (or honestly said not to be yet): into the
            // system share sheet it goes. The label is the app's own
            // capsule, so it reads as the one primary control it is.
            ShareLink(item: invite.url()) {
                PrimaryCapsuleLabel(title: shared ? Copy.sendItAgain : Copy.sendTheInvite)
            }
            .simultaneousGesture(TapGesture().onEnded { shared = true })
            .accessibilityLabel(shared ? Copy.sendItAgain : Copy.sendTheInvite)
        } else {
            // A held beat while the link registers — not a spinner (§08).
            PrimaryCapsuleLabel(title: Copy.sendTheInvite)
                .opacity(0.45)
                .accessibilityHidden(true)
        }
    }

    private func register() async {
        guard !model.isFull(room), !needsAccount, !registering else { return }
        registering = true
        defer { registering = false }
        do {
            invite = try await model.registerInvite(for: room)
            line = nil
        } catch {
            invite = model.invite(for: room)
            line = Copy.inviteNotRegisteredYet
        }
    }
}

/// The invite, as a sheet over the room ("Send it again", "Invite
/// someone").
struct InviteSheet: View {
    @Environment(\.dismiss) private var dismiss
    let room: Room

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                BackControl(title: Copy.close) { dismiss() }
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            Spacer()
            InviteStep(room: room)
                .padding(.horizontal, 24)
            Spacer()
        }
        .frame(maxWidth: 480)
        .frame(maxWidth: .infinity)
        .ribbonSheet(fitted: true)
        .presentationDetents([.medium])
    }
}

// S15's two steps that should feel like one — an optional name, then the
// invite — in one sheet, when starting an additional room from S14.
struct NewRoomSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var onCreated: (Room) -> Void

    @State private var name = ""
    @State private var room: Room?
    @FocusState private var nameFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack {
                Spacer()
                BackControl(title: Copy.close) { dismiss() }
            }
            if let room {
                InviteStep(
                    room: room,
                    later: (Copy.inviteLater, { dismiss() }))
                .frame(maxWidth: .infinity)
                .transition(.opacity)
            } else {
                SectionHeader(Copy.roomName)
                RibbonTextField(prompt: model.derivedRoomNamePrompt, text: $name, size: 18)
                    .focused($nameFocused)
                    .submitLabel(.done)
                    .onSubmit(create)
                WayInButton(title: Copy.startARoomControl, action: create)
            }
        }
        .padding(24)
        .padding(.top, 4)
        .padding(.bottom, 12)
        .frame(maxWidth: 480)
        .frame(maxWidth: .infinity)
        .animation(RibbonMotion.settle, value: room?.id)
        .ribbonSheet(fitted: true)
        .presentationDetents([.medium])
        .onAppear { nameFocused = true }
    }

    private func create() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        let created = model.createRoom(named: trimmed.isEmpty ? nil : trimmed)
        onCreated(created)
        withAnimation(RibbonMotion.settle) { room = created }
    }
}
