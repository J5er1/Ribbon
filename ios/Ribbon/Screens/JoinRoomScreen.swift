import SwiftUI
import PhotosUI
import RibbonCore

// S16 — accepting an invite. The screen shows a person, not a product:
// who is inviting, the room's name, one Join control. Account creation
// (§6.10 — an emailed code, no passwords) happens here when it has to,
// because joining is the first moment an account is genuinely needed.
// Signed in as someone else: the flow says who it would join as and
// offers to switch; it never silently joins.

struct JoinFlow: View {
    @Environment(\.appModel) private var model
    @Environment(\.dismiss) private var dismiss
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
        case account
        case joining
        case ink(UUID)      // the room, when the join made it three (§6.7)
        case dead(String)   // expired, full, unreachable — the line to show
    }

    @State private var phase: Phase = .loading
    @State private var preview: InvitePreview?
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @State private var portraitData: Data?
    @State private var joining = false
    /// Set down mid-join (the sheet swiped away): the join completes —
    /// they did join — but arriving must not happen underneath them.
    @State private var wasSetDown = false
    @State private var loadAttempt = 0
    @FocusState private var nameFocused: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                topControls
                Spacer(minLength: 40)
                switch phase {
                case .loading:
                    // A held beat, not a spinner — bounded: the preview
                    // answers fast or the dead line takes its place.
                    Color.clear.frame(height: 20)
                case .preview:
                    previewStep
                case .name:
                    nameStep
                case .account:
                    accountStep
                case .joining:
                    Color.clear.frame(height: 20)
                case .ink(let roomID):
                    inkStep(roomID)
                case .dead(let line):
                    deadStep(line)
                }
                Spacer(minLength: 80)
            }
            .frame(maxWidth: 420)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 520)
        }
        .scrollIndicators(.hidden)
        .scrollDismissesKeyboard(.interactively)
        .room()
        .task(id: loadAttempt) { await loadPreview() }
        .onDisappear { wasSetDown = true }
        .animation(RibbonMotion.settle, value: phase)
    }

    // MARK: Steps

    @ViewBuilder
    private var topControls: some View {
        HStack {
            Spacer()
            if onStartInstead == nil {
                // Presented over the room: a way out that isn't the swipe.
                BackControl(title: Copy.close) { dismiss() }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }

    private var previewStep: some View {
        VStack(spacing: 20) {
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
            if model.isSignedIn, let me = model.me {
                // Signed in as someone else? Say who this would join as;
                // offer to switch; never silently join (S16).
                VStack(spacing: 6) {
                    SmallCaps(Copy.joiningAs(firstName(me.name)), size: 12)
                    QuietControl(title: Copy.notYou) {
                        Task {
                            await model.signOutAndForgetThisPerson()
                            // The thread continues as a new person (S16).
                            // Over the room, the room went with the
                            // person and the thread re-presents this join
                            // — at the name, not at a second preview.
                            model.pendingInvite = PendingInvite(token: token, resumesAtName: true)
                            withAnimation(RibbonMotion.settle) { phase = .name }
                        }
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
            PortraitPicker(item: $portraitItem, data: $portraitData)
            Text(Copy.portraitReason)
                .font(RibbonType.ui(15))
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
            RibbonTextField(prompt: Copy.yourName, text: $name, centered: true, size: 20)
                .textContentType(.givenName)
                .focused($nameFocused)
                .padding(.horizontal, 40)
                .submitLabel(.done)
                .onSubmit(advanceFromName)
            WayInButton(title: Copy.thatsMe) { advanceFromName() }
                .padding(.horizontal, 80)
                .disabled(nameIsEmpty)
                .opacity(nameIsEmpty ? 0.3 : 1)
        }
        .onAppear { nameFocused = true }
    }

    private var nameIsEmpty: Bool { name.trimmingCharacters(in: .whitespaces).isEmpty }

    private var accountStep: some View {
        AccountStep(
            reason: Copy.emailReasonJoiner,
            onSignedIn: { join() },
            skipTitle: onStartInstead == nil ? nil : Copy.startARoomInstead,
            onSkip: onStartInstead)
        .padding(.horizontal, 40)
    }

    /// The newcomer picks from what's left, during their onboarding (§6.7).
    @ViewBuilder
    private func inkStep(_ roomID: UUID) -> some View {
        if let room = model.state.rooms.first(where: { $0.id == roomID }) {
            VStack(spacing: 18) {
                SmallCaps(Copy.yourInk, size: 13)
                Text(Copy.inkReason)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                InkPickerRow(room: room) { arrive(in: roomID) }
            }
        } else {
            Color.clear.onAppear { arrive(in: roomID) }
        }
    }

    private func deadStep(_ line: String) -> some View {
        VStack(spacing: 18) {
            Text(line)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 44)
            if line == Copy.serverUnreachable {
                QuietControl(title: Copy.tryAgain) {
                    phase = .loading
                    loadAttempt += 1
                }
            }
            if let onStartInstead {
                QuietControl(title: Copy.startARoomInstead, action: onStartInstead)
            }
        }
    }

    // MARK: Movement

    private func loadPreview() async {
        guard phase == .loading else { return }
        guard let remote = model.remote else {
            phase = .dead(Copy.serverUnreachable)
            return
        }
        do {
            // Bounded: a hesitant partner is never held on a blank screen
            // for a minute (S16, §08).
            let found = try await withThrowingTaskGroup(of: InvitePreview?.self) { group in
                group.addTask { try await remote.invitePreview(token: token) }
                group.addTask {
                    try await Task.sleep(for: .seconds(12))
                    throw SupabaseError.http(0, "timeout")
                }
                let first = try await group.next()
                group.cancelAll()
                return first ?? nil
            }
            guard let found else {
                phase = .dead(Copy.inviteExpired)
                return
            }
            preview = found
            if found.expired {
                phase = .dead(Copy.inviteExpired)
            } else if found.full {
                phase = .dead(Copy.roomFullForJoiner)
            } else if model.me == nil, model.pendingInvite?.token == token,
                      model.pendingInvite?.resumesAtName == true {
                // "Not you?" already accepted this invite once.
                phase = .name
            } else {
                phase = .preview
            }
        } catch {
            phase = .dead(Copy.serverUnreachable)
        }
    }

    private func advanceFromPreview() {
        if model.me == nil {
            phase = .name
        } else if !model.isSignedIn {
            phase = .account
        } else {
            join()
        }
    }

    private func advanceFromName() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        nameFocused = false
        Task {
            await model.completeOnboarding(name: trimmed, portraitData: portraitData, startRoom: false)
            if model.isSignedIn {
                join()
            } else {
                phase = .account
            }
        }
    }

    private func join() {
        guard !joining else { return }
        joining = true
        phase = .joining
        Task {
            defer { joining = false }
            do {
                let roomID = try await model.joinRoom(inviteToken: token)
                guard !wasSetDown else { return }
                if let room = model.state.rooms.first(where: { $0.id == roomID }), model.needsInkPick(in: room) {
                    phase = .ink(roomID)
                } else {
                    arrive(in: roomID)
                }
            } catch {
                phase = .dead(deadLine(for: error))
            }
        }
    }

    private func arrive(in roomID: UUID) {
        // The pending token is forgotten here, not the moment the join
        // lands: over the room it is the sheet's item, and clearing it
        // early would dismiss the sheet under the ink step.
        model.pendingInvite = nil
        // A room of one minted moments ago by "Start a room" and never
        // read in has no reason to linger in the rooms sheet.
        model.discardEmptyRoomOfOne(except: roomID)
        model.switchRoom(to: roomID)
        onDone()
    }

    /// The database names what happened; the screen says it plainly.
    private func deadLine(for error: Error) -> String {
        if case SupabaseError.http(_, let body) = error {
            if body.contains("room_full") { return Copy.roomFullForJoiner }
            if body.contains("invite_expired") { return Copy.inviteExpired }
        }
        return Copy.serverUnreachable
    }
}
