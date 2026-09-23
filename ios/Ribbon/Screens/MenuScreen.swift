import SwiftUI
import PhotosUI
import RibbonCore

// S14 + S18 — the menu, as two screens (ledger A29).
//
// The room's name, top-left, opens *the room*: who is in it, what it tells
// you, and the rooms you are in. Your face, top-right, opens *You*: how you
// read, what this phone holds, and your account. Two questions, two doors,
// two screens — where one long menu used to answer both by scrolling.
//
// Both are pages, not sheets: a pinned bar with only the way out, a display
// title on the page, a lede under it, and tiles. No icons anywhere. Every
// row is paper; every group is a stack of paper with seams; everything that
// undoes is a quiet control at the foot.

/// Where the menu opens. The room's name and your own portrait are two
/// different questions, and they arrive at two different screens.
enum MenuEntry: String, Identifiable {
    /// The room's name, top-left: the room, and your rooms.
    case rooms
    /// Your portrait, top-right: You.
    case you

    var id: String { rawValue }
}

/// The screens the menu pushes. The four settings screens are S19–S22;
/// the join is the door for a link this phone could not tap.
private enum MenuRoute: Hashable {
    case text
    case notifications
    case downloads
    case plan
    case joinWithInvite
    case join(UUID)
}

/// A room whose invite is being handed out.
private struct InviteTarget: Identifiable {
    let room: Room
    var id: UUID { room.id }
}

struct MenuScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let entry: MenuEntry
    /// Switching rooms; the room being left may have a book open over it.
    var onSwitch: (UUID) -> Void

    @State private var path = NavigationPath()
    @State private var showNewRoom = false
    @State private var inviting: InviteTarget?
    @State private var closeAfterInviting = false
    /// A room just made, waiting for the naming sheet to finish going away
    /// before its invite is handed out.
    @State private var stagedRoom: Room?

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                switch entry {
                case .rooms:
                    if let room = model.currentRoom {
                        RoomMenuScreen(
                            room: room,
                            onClose: { dismiss() },
                            onInvite: { closeAfterInviting = false; inviting = InviteTarget(room: room) },
                            onNotifications: { path.append(MenuRoute.notifications) },
                            onPlan: { path.append(MenuRoute.plan) },
                            onSwitch: { roomID in
                                onSwitch(roomID)
                                model.switchRoom(to: roomID)
                                dismiss()
                            },
                            onStartARoom: { showNewRoom = true },
                            onJoinWithInvite: { path.append(MenuRoute.joinWithInvite) })
                    } else {
                        GrainBackground()
                    }
                case .you:
                    YouScreen(
                        onClose: { dismiss() },
                        onText: { path.append(MenuRoute.text) },
                        onDownloads: { path.append(MenuRoute.downloads) })
                }
            }
            .navigationDestination(for: MenuRoute.self) { route in
                destination(route)
            }
        }
        .sheet(isPresented: $showNewRoom, onDismiss: {
            // Naming and inviting are two steps that should feel like one
            // (S15) — the invite follows the naming, both over the menu, and
            // the menu closes behind them. After it, though, not during:
            // presenting the second sheet while the first is still going
            // drops it. So the room is staged, and the invite opens on the
            // naming sheet's own dismissal.
            guard let room = stagedRoom else { return }
            stagedRoom = nil
            closeAfterInviting = true
            inviting = InviteTarget(room: room)
        }) {
            NewRoomSheet { room in stagedRoom = room }
        }
        .sheet(item: $inviting, onDismiss: {
            if closeAfterInviting {
                closeAfterInviting = false
                dismiss()
            }
        }) { target in
            InviteSheet(room: target.room)
                .presentationDetents([.medium])
        }
    }

    @ViewBuilder
    private func destination(_ route: MenuRoute) -> some View {
        switch route {
        case .text:
            TextSettingsScreen()
        case .notifications:
            NotificationSettingsScreen()
        case .downloads:
            DownloadsScreen()
        case .plan:
            PlanScreen()
        case .joinWithInvite:
            JoinWithInviteScreen { token in
                path.append(MenuRoute.join(token))
            }
        case .join(let token):
            // The same S16 thread a tapped link runs, pushed rather than
            // presented — so the join is inside the menu it was started from.
            JoinFlow(
                token: token,
                onDone: { dismiss() },
                // A dead invite here goes back to the field rather than out
                // of the menu — "ask for a new one" and paste the new one.
                wayOut: Copy.back)
            .id(token)
            .toolbar(.hidden, for: .navigationBar)
        }
    }
}

// MARK: - The room (S14/S15/S19/S22)

private struct RoomMenuScreen: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onClose: () -> Void
    var onInvite: () -> Void
    var onNotifications: () -> Void
    var onPlan: () -> Void
    var onSwitch: (UUID) -> Void
    var onStartARoom: () -> Void
    var onJoinWithInvite: () -> Void

    var body: some View {
        RibbonScreen(
            title: model.displayName(of: room), lede: Copy.roomLede,
            actions: {
                QuietControl(title: Copy.close, action: onClose)
                    .keyboardShortcut(.cancelAction)
                    .padding(.trailing, 8)
            }
        ) {
            VStack(alignment: .leading, spacing: 28) {
                if model.isFull(room) {
                    // S15's full state, said where the invite would have been.
                    SettingNote(Copy.roomHoldsSix)
                } else if model.remote != nil {
                    // The link resolves through the backend, so without one
                    // there is nothing to hand out and no row for it.
                    SettingRow(Copy.inviteSomeone, subtitle: Copy.inviteSend, action: onInvite)
                }

                SettingsGroup {
                    SettingRow(Copy.notifications, subtitle: Copy.notificationsSub, action: onNotifications)
                    SettingRow(Copy.plan, subtitle: Copy.planSub, action: onPlan)
                }

                RoomControls(room: room, onLeft: onClose)

                VStack(alignment: .leading, spacing: 10) {
                    SectionLabel(Copy.yourRooms)
                        .padding(.horizontal, RibbonShape.textInset)
                    VStack(spacing: RibbonShape.seam) {
                        ForEach(model.state.rooms) { other in
                            RoomTile(room: other) { onSwitch(other.id) }
                        }
                    }
                    SettingsGroup {
                        SettingRow(Copy.startARoomControl, action: onStartARoom)
                        // A join goes through the backend and cannot happen
                        // without one. No dead control (§6.1).
                        if model.remote != nil {
                            SettingRow(Copy.joinWithAnInvite, action: onJoinWithInvite)
                        }
                    }
                    .padding(.top, 6)
                }
            }
        }
    }
}

/// One room: its name, who is in it, what it is reading, and its fire. The
/// current one is marked with a chartreuse hairline — and, because colour is
/// never the only signal (§11), said as selected too.
private struct RoomTile: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var action: () -> Void

    var body: some View {
        let isCurrent = room.id == model.currentRoom?.id
        let reading = model.openReading(in: room)
        Button(action: action) {
            HStack(spacing: 12) {
                Rectangle()
                    .fill(isCurrent ? Palette.chartreuse : .clear)
                    .frame(width: 2, height: 34)
                VStack(alignment: .leading, spacing: 4) {
                    Text(model.displayName(of: room))
                        .font(RibbonType.ui(17))
                        .foregroundStyle(Palette.text)
                    HStack(spacing: 8) {
                        HStack(spacing: -5) {
                            ForEach(model.members(of: room)) { membership in
                                PortraitView(
                                    person: model.person(membership.personID),
                                    ink: membership.ink,
                                    size: 18,
                                    image: model.portrait(membership.personID))
                            }
                        }
                        if let reading, let book = Bible.book(id: reading.bookID) {
                            SmallCaps(book.name, size: 11)
                        }
                    }
                }
                Spacer()
                if room.isPaused {
                    SmallCaps(Copy.paused, size: 11)
                }
                if let reading {
                    // A paused room's fire is drawn in whatever state it
                    // actually holds — never banked by a lapse (S14).
                    let state = model.fireState(of: reading)
                    CampfireGlyph(state: state, scale: reading.handiwork.scale, height: 22)
                        .accessibilityRepresentation { Text(Copy.fireIs(state.displayName)) }
                }
            }
            .padding(.horizontal, RibbonShape.textInset - 6)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .contentShape(Rectangle())
            .paper(.row)
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isCurrent ? [.isSelected] : [])
    }
}

/// The current room's own controls: its name, your ink, the way out.
private struct RoomControls: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onLeft: () -> Void

    @State private var editingRoomName = false
    @State private var roomName = ""
    @State private var showInkPicker = false
    private enum Leaving { case confirm, notes }
    @State private var leaving: Leaving?
    @FocusState private var roomNameFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SettingsGroup {
                if editingRoomName {
                    TextField(
                        "", text: $roomName,
                        prompt: Text(Copy.roomName).foregroundStyle(Palette.muted))
                        .font(RibbonType.ui(17))
                        .foregroundStyle(Palette.text)
                        .focused($roomNameFocused)
                        .submitLabel(.done)
                        .onSubmit(commitRoomName)
                        .onChange(of: roomNameFocused) { _, focused in
                            if !focused { commitRoomName() }
                        }
                        .padding(.horizontal, RibbonShape.textInset)
                        .frame(maxWidth: .infinity, minHeight: RibbonShape.rowHeight, alignment: .leading)
                        .tile()
                        .onAppear { roomNameFocused = true }
                        .transition(.opacity)
                } else {
                    SettingRow(Copy.nameThisRoom, value: room.name, chevron: false) {
                        roomName = room.name ?? ""
                        editingRoomName = true
                    }
                    .transition(.opacity)
                }
                // Ink is identity from three people up (§4.5); below that
                // the room draws from the whole palette freely and there is
                // nothing to choose.
                if model.inkIsIdentity(in: room) {
                    SettingRow(Copy.changeYourInk, value: model.myMembership(in: room)?.ink?.displayName, chevron: false) {
                        showInkPicker = true
                    }
                }
            }
            .animation(RibbonMotion.settle, value: editingRoomName)
            // The way out is quiet, never emphasised, and never hidden.
            QuietControl(title: Copy.leaveThisRoom) { leaving = .confirm }
                .padding(.horizontal, RibbonShape.textInset)
        }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
        .confirm(leaveDialog)
    }

    private func commitRoomName() {
        guard editingRoomName else { return }
        model.renameRoom(room, to: roomName)
        editingRoomName = false
    }

    private var leaveDialog: Binding<ConfirmState?> {
        Binding(
            get: {
                switch leaving {
                case nil: return nil
                case .confirm:
                    return ConfirmState(question: Copy.leaveRoomConfirm, choices: [
                        ConfirmChoice(Copy.leaveThisRoom, destructive: true) { leaving = .notes },
                    ])
                case .notes:
                    return ConfirmState(question: Copy.leaveNotesQuestion, choices: [
                        ConfirmChoice(Copy.leaveThem) { leave(keepNotes: true) },
                        ConfirmChoice(Copy.takeThemBack, destructive: true) { leave(keepNotes: false) },
                    ])
                }
            },
            set: { if $0 == nil { leaving = nil } })
    }

    private func leave(keepNotes: Bool) {
        leaving = nil
        model.leaveRoom(room, keepNotesBehind: keepNotes)
        onLeft()
    }
}

// MARK: - You (S18)

private struct YouScreen: View {
    @Environment(AppModel.self) private var model
    var onClose: () -> Void
    var onText: () -> Void
    var onDownloads: () -> Void

    @State private var deleting: ConfirmState?

    var body: some View {
        RibbonScreen(
            title: Copy.you,
            actions: {
                QuietControl(title: Copy.close, action: onClose)
                    .keyboardShortcut(.cancelAction)
                    .padding(.trailing, 8)
            }
        ) {
            VStack(alignment: .leading, spacing: 28) {
                YouIdentity()

                SettingsGroup(title: Copy.howYouRead) {
                    SettingRow(Copy.textAndTranslation, subtitle: Copy.textSub, action: onText)
                }

                SettingsGroup(title: Copy.thisPhone) {
                    SettingRow(Copy.downloads, subtitle: Copy.downloadsSub, action: onDownloads)
                }

                AccountSection()

                VStack(alignment: .leading, spacing: 18) {
                    if model.remote != nil {
                        QuietControl(title: Copy.deleteAccount) {
                            // §6.8: the "leave your notes behind?" question,
                            // asked once, at deletion. Neither answer is the
                            // quiet one.
                            deleting = ConfirmState(question: Copy.leaveNotesQuestion, choices: [
                                ConfirmChoice(Copy.deleteAndLeaveThem, destructive: true) {
                                    onClose()
                                    model.deleteAccount(keepNotesBehind: true)
                                },
                                ConfirmChoice(Copy.deleteAndTakeThemBack, destructive: true) {
                                    onClose()
                                    model.deleteAccount(keepNotesBehind: false)
                                },
                            ])
                        }
                    }
                    SmallCaps(appVersion, size: 11, color: Palette.muted.opacity(0.7))
                }
                .padding(.horizontal, RibbonShape.textInset)
                .padding(.top, 8)
            }
        }
        .confirm($deleting, dismissTitle: Copy.neverMind)
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        return Copy.versionLine(version)
    }
}

/// Your face beside your name, both changeable in place (S18) — presence is
/// faces, so the face can be added or changed here, not only at onboarding.
/// The name is a field that looks like a title: tap it and type; it commits
/// when you are done and an empty name reverts.
private struct YouIdentity: View {
    @Environment(AppModel.self) private var model

    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @FocusState private var nameFocused: Bool

    var body: some View {
        // Read here, in the body, where the main actor is. The picker's
        // label is a closure the model may not be reached from, and the
        // face was being looked up twice besides.
        let me = model.me
        let face = me.flatMap { model.portrait($0.id) }
        let hasFace = face != nil
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 18) {
                PhotosPicker(selection: $portraitItem, matching: .images) {
                    PortraitView(person: me, ink: nil, size: 88, image: face)
                        .accessibilityHidden(true)
                }
                .buttonStyle(.pressable)
                // What the control does depends on whether there is a face
                // behind it, and it should not go on saying "add" to
                // somebody who has one.
                .accessibilityLabel(hasFace ? Copy.changeYourPortrait : Copy.addAPortrait)
                .onChange(of: portraitItem) { _, item in
                    Task {
                        if let data = try? await item?.loadTransferable(type: Data.self),
                           let jpeg = downsampledJPEG(data) {
                            await model.setPortrait(jpeg)
                        }
                    }
                }
                TextField("", text: $name, prompt: Text(Copy.yourName).foregroundStyle(Palette.muted))
                    .font(RibbonType.display(26))
                    .foregroundStyle(Palette.text)
                    .focused($nameFocused)
                    .submitLabel(.done)
                    .textInputAutocapitalization(.words)
                    .onSubmit(commit)
                    .onChange(of: nameFocused) { _, focused in
                        if !focused { commit() }
                    }
                    .frame(minHeight: 44)
                    // The one field in the app that wears no paper: your
                    // name, in the room's own display face, edited where it
                    // is written (S18). With nothing under it there was
                    // nothing to say it was a field at all — it read as a
                    // heading, and the way to your own name was a tap nobody
                    // had a reason to make. A hairline is the smallest thing
                    // that says this line is yours to change; it brightens
                    // under the caret and is otherwise as quiet as every
                    // other rule on the screen.
                    .overlay(alignment: .bottom) {
                        Rectangle()
                            .fill(nameFocused ? Palette.text : Palette.rule)
                            .frame(height: 1)
                            // A change of light: it fades under reduce
                            // motion as well.
                            .animation(RibbonMotion.arrive, value: nameFocused)
                    }
                    .accessibilityLabel(Copy.yourName)
                    .accessibilityHint(Copy.editsYourName)
            }
            Text(hasFace ? Copy.yourFaceReason : Copy.addAPortraitReason)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .onAppear { name = model.me?.name ?? "" }
        .onChange(of: model.me?.name) { _, now in
            if !nameFocused { name = now ?? "" }
        }
        .onDisappear { commit() }
    }

    private func commit() {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        if trimmed.isEmpty {
            name = model.me?.name ?? ""
        } else if trimmed != model.me?.name {
            model.updateMe(name: trimmed)
        }
    }
}

/// The account (§6.10): an emailed code, no passwords. Signed out is a
/// state, not a nag — one quiet line, and the reason stated plainly. Absent
/// entirely in a build with no backend (§6.1: no dead controls).
private struct AccountSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var signingIn = false
    @State private var passkeyLine: String?

    var body: some View {
        if model.remote != nil {
            Group {
                if model.isSignedIn {
                    SettingsGroup(
                        title: Copy.yourAccount,
                        footnote: model.canAddAPasskey ? (passkeyLine ?? Copy.passkeyReason) : nil,
                        footnoteAnnounces: true
                    ) {
                        if let address = model.accountEmail {
                            SettingValue(address, note: Copy.accountReason)
                        }
                        // §6.10 wants a passkey where there is one. Offered
                        // here, on the account, because that is what it
                        // belongs to — and only ever added to the emailed
                        // code, never in place of it.
                        if model.canAddAPasskey {
                            SettingRow(Copy.addAPasskey, chevron: false) { addPasskey() }
                        }
                        SettingRow(Copy.signOut, chevron: false) {
                            signingIn = false
                            Task { await model.signOutRemote() }
                        }
                    }
                } else if signingIn {
                    VStack(alignment: .leading, spacing: 10) {
                        SectionLabel(Copy.yourAccount)
                            .padding(.horizontal, RibbonShape.textInset)
                        SignInInline(
                            onSignedIn: { signingIn = false },
                            onCancel: { signingIn = false })
                        .padding(.horizontal, RibbonShape.textInset)
                        .padding(.vertical, 18)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .tile()
                    }
                } else {
                    SettingsGroup(title: Copy.yourAccount, footnote: Copy.accountReason) {
                        SettingRow(Copy.signIn, chevron: false) { signingIn = true }
                    }
                }
            }
            .animation(RibbonMotion.arrive(still: reduceMotion), value: model.isSignedIn)
            .animation(RibbonMotion.arrive(still: reduceMotion), value: signingIn)
        }
    }

    private func addPasskey() {
        passkeyLine = nil
        Task {
            do {
                try await model.registerPasskey()
                passkeyLine = Copy.passkeyAdded
            } catch Passkeys.Failure.cancelled {
                // Dismissed the sheet. Nothing happened, and nothing is said.
            } catch {
                passkeyLine = Copy.passkeyWasntAdded
            }
        }
    }
}

// MARK: - Joining a second room (S16, from the menu)

/// The door for a link this phone could not tap: one that arrived in an
/// email on a laptop, or in a thread this phone can't open. It takes the
/// link or the code out of it and hands the token to the same join thread.
private struct JoinWithInviteScreen: View {
    @Environment(\.dismiss) private var dismiss
    var onToken: (UUID) -> Void

    @State private var pasted = ""
    @State private var missed = false

    var body: some View {
        RibbonScreen(title: Copy.joinWithAnInvite, lede: Copy.theLinkBringsYouIn, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 14) {
                CentredTextField(
                    text: $pasted, prompt: Copy.pasteInvitePrompt,
                    submitLabel: .go, onSubmit: accept)
                .onChange(of: pasted) { _, text in
                    // A pasted link is complete the moment it lands — don't
                    // make them find a go button.
                    missed = false
                    if AppModel.inviteToken(fromPasted: text) != nil { accept() }
                }
                if missed {
                    Text(Copy.thatLinkIsntAnInvite)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                        .padding(.horizontal, RibbonShape.textInset)
                        .transition(.opacity)
                }
            }
            .animation(RibbonMotion.arrive, value: missed)
        }
    }

    private func accept() {
        guard let token = AppModel.inviteToken(fromPasted: pasted) else {
            if !pasted.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                missed = true
            }
            return
        }
        onToken(token)
    }
}

/// The one text field the product has: paper, a hairline edge, the prompt
/// in the muted colour, the text in ivory. Used for a name, a room's name,
/// an email, a code, a pasted link.
struct CentredTextField: View {
    @Binding var text: String
    var prompt: String
    var submitLabel: SubmitLabel = .done
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?
    var centred = true
    var onSubmit: () -> Void = {}

    var body: some View {
        TextField("", text: $text, prompt: Text(prompt).foregroundStyle(Palette.muted))
            .font(RibbonType.ui(17))
            .foregroundStyle(Palette.text)
            .multilineTextAlignment(centred ? .center : .leading)
            .keyboardType(keyboard)
            .textContentType(contentType)
            .textInputAutocapitalization(keyboard == .emailAddress || keyboard == .numberPad || keyboard == .URL ? .never : .words)
            .autocorrectionDisabled(keyboard != .default)
            .submitLabel(submitLabel)
            .onSubmit(onSubmit)
            .padding(.horizontal, RibbonShape.textInset)
            .frame(maxWidth: .infinity, minHeight: 52)
            .paper(.row)
            .accessibilityLabel(prompt)
    }
}
