import SwiftUI
import PhotosUI
import RibbonCore

// S14 + S18, made one screen: the menu.
//
// The book gives the rooms a sheet of their own (S14) and puts You behind it
// (S18); deviation 13 had already pulled You out to a second sheet, off the
// room's portrait. That left two half-height sheets, each a flat pile of
// controls with a heading on none of them — and, because neither of them was
// ever about an invite you had been *sent*, no way at all to accept one for a
// second room once you had a room of your own. The tapped link was the whole
// mechanism, and a link that landed in an email on a laptop had nowhere to go.
//
// This is the two of them made one full-screen menu, in named sections, with
// the two doors that were missing: **Invite someone**, for the room you are
// already in, and **Join with an invite**, for a room somebody has asked you
// into. Recorded in docs/deviations.md 14.
//
// Hierarchy is carried by three things and no others: a small-caps head over
// each section with a hairline under it; ivory 17 pt rows for what you go to
// or do; quiet muted small caps for what undoes (leave a room, delete an
// account). No chevrons, no disclosure triangles, no grouped inset table —
// this is still a room, not a Settings app.

/// Where the menu opens. The room's name and your own portrait are two
/// different questions, and they arrive at two different places on one
/// screen.
enum MenuEntry: String, Identifiable {
    /// The room's name, top-left: the rooms.
    case rooms
    /// Your portrait, top-right: You. Deviation 13 promised settings one tap
    /// from the room, and one tap is what this still is — the menu opens
    /// already scrolled to your own section.
    case you

    var id: String { rawValue }
}

/// The screens the menu pushes. The four settings screens are S19–S22
/// unchanged; the join is the new door.
private enum MenuRoute: Hashable {
    case text
    case notifications
    case downloads
    case plan
    case joinWithInvite
    case join(UUID)
}

/// The one place the menu can be asked to open scrolled to.
private enum MenuAnchor: Hashable {
    case you
}

/// A room whose invite is being handed out, and whether it was just made.
private struct InviteTarget: Identifiable {
    let room: Room
    /// A room made a moment ago: closing its invite should leave you in the
    /// new room rather than back in the menu (S15 — naming and inviting are
    /// two steps that should feel like one, and the third step is being
    /// there).
    let isNew: Bool

    var id: UUID { room.id }
}

struct MenuScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let entry: MenuEntry

    @State private var path = NavigationPath()
    @State private var showNewRoom = false
    @State private var inviting: InviteTarget?
    @State private var closeAfterInviting = false
    @State private var confirmDelete = false

    var body: some View {
        NavigationStack(path: $path) {
            root
                // The root draws its own way out, so the empty bar goes. The
                // pushed screens keep theirs — the system chevron is the back
                // control on iOS, and Law 5 gives the platform the chrome.
                .toolbarVisibility(.hidden, for: .navigationBar)
                .navigationDestination(for: MenuRoute.self) { route in
                    destination(route)
                }
        }
        .sheet(isPresented: $showNewRoom) {
            // Naming and inviting are two steps that should feel like one
            // (S15) — the invite follows the naming, both over the menu, and
            // the menu closes behind them.
            NewRoomSheet { room in
                closeAfterInviting = true
                inviting = InviteTarget(room: room, isNew: true)
            }
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
        .confirmationDialog(
            // §6.8: the "leave your notes behind?" question, asked once, at
            // deletion. Leaving them is never not the default.
            Copy.leaveNotesQuestion, isPresented: $confirmDelete, titleVisibility: .visible
        ) {
            // The menu closes first, the way leaving a room does: the
            // person it was about is gone, and a menu left standing would
            // re-present itself over the fresh room the app makes next.
            Button(Copy.deleteAndLeaveThem, role: .destructive) {
                dismiss()
                model.deleteAccount(keepNotesBehind: true)
            }
            Button(Copy.deleteAndTakeThemBack, role: .destructive) {
                dismiss()
                model.deleteAccount(keepNotesBehind: false)
            }
        }
    }

    // MARK: The menu itself

    private var root: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 34) {
                    header
                    roomsSection
                    if let room = model.currentRoom {
                        thisRoomSection(room)
                    }
                    youSection
                        .id(MenuAnchor.you)
                    accountSection
                    version
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 44)
                .readableColumn()
            }
            .scrollIndicators(.hidden)
            .room()
            .onAppear {
                // Opened from the portrait: land on your own section rather
                // than making you scroll past the rooms to reach it. No
                // animation — this is where the menu opened, not somewhere it
                // travelled to. One turn of the run loop, because the anchor
                // has to be laid out before it can be scrolled to.
                guard entry == .you else { return }
                DispatchQueue.main.async {
                    proxy.scrollTo(MenuAnchor.you, anchor: .top)
                }
            }
        }
    }

    /// The way out. A full-screen cover has no swipe of its own, so the
    /// control is drawn — and Esc closes it on a hardware keyboard, as it
    /// closes the book (deviation 12).
    private var header: some View {
        HStack {
            Spacer()
            QuietControl(title: Copy.close) { dismiss() }
                .keyboardShortcut(.cancelAction)
        }
        .padding(.top, 4)
    }

    // MARK: Rooms (S14)

    private var roomsSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHead(Copy.rooms)
            ForEach(model.state.rooms) { room in
                MenuRoomRow(room: room) {
                    // Tap a room → switch, the menu closes, the room screen
                    // cross-fades (S14).
                    withAnimation(RibbonMotion.arrive) { model.switchRoom(to: room.id) }
                    dismiss()
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                MenuRow(title: Copy.startARoomControl) { showNewRoom = true }
                MenuRow(title: Copy.joinWithAnInvite) { path.append(MenuRoute.joinWithInvite) }
            }
            .padding(.top, 6)
        }
    }

    // MARK: This room (S12/S15 — the controls for the room you are in)

    @ViewBuilder
    private func thisRoomSection(_ room: Room) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHead(Copy.thisRoom, detail: model.displayName(of: room))
            if model.isFull(room) {
                // S15's full state, said where the invite would have been.
                Text(Copy.roomHoldsSix)
                    .font(RibbonType.ui(15))
                    .foregroundStyle(Palette.muted)
                    .padding(.vertical, 10)
            } else {
                MenuRow(title: Copy.inviteSomeone) {
                    closeAfterInviting = false
                    inviting = InviteTarget(room: room, isNew: false)
                }
            }
            RoomControls(room: room, onLeft: { dismiss() })
        }
    }

    // MARK: You (S18)

    private var youSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHead(Copy.you)
            YouIdentityRow()
                .padding(.vertical, 6)
            MenuRow(title: Copy.textAndTranslation) { path.append(MenuRoute.text) }
            MenuRow(title: Copy.notifications) { path.append(MenuRoute.notifications) }
            MenuRow(title: Copy.downloads) { path.append(MenuRoute.downloads) }
            MenuRow(title: Copy.plan) { path.append(MenuRoute.plan) }
        }
    }

    // MARK: Account (§6.10)

    private var accountSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHead(Copy.account)
            AccountControls()
            QuietControl(title: Copy.deleteAccount) { confirmDelete = true }
        }
    }

    private var version: some View {
        SmallCaps(appVersion, size: 11, color: Palette.muted.opacity(0.7))
    }

    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        return Copy.versionLine(version)
    }

    // MARK: The pushed screens

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
            // presented — so the join is inside the menu it was started from
            // and nothing has to be handed across two presentations.
            JoinFlow(
                token: token,
                onDone: {
                    // Joined, and `joinRoom` has already made it the current
                    // room: the menu gets out of the way so you arrive in it.
                    dismiss()
                },
                // A dead invite here goes back to the field rather than out
                // of the menu — "ask for a new one" and paste the new one.
                wayOut: Copy.back)
            .id(token)
        }
    }
}

// MARK: - Section furniture

/// A section's head: small caps, a hairline under it, and — for the room you
/// are in — the room's own name beside it, so "This room" is never a
/// question.
private struct SectionHead: View {
    let title: String
    var detail: String?

    init(_ title: String, detail: String? = nil) {
        self.title = title
        self.detail = detail
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                SmallCaps(title, size: 12, color: Palette.text.opacity(0.75))
                if let detail {
                    SmallCaps(detail, size: 12, color: Palette.muted)
                }
            }
            // A head is drawn as a head and has to be announced as one: a
            // rotor of headings is how a screen reader skims a menu, and
            // without the trait the sections are four unlabelled piles
            // again (§11).
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            HairlineRule()
        }
        .padding(.bottom, 6)
    }
}

/// One row of the menu: a thing you go to, or a thing you do. Ivory, 17 pt,
/// and never shorter than a finger (§11, deviation 12).
private struct MenuRow: View {
    let title: String
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text)
                Spacer()
            }
            .frame(minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
    }
}

/// One room: its name, who is in it, what it is reading, and its fire.
///
/// The book asks for the name, the portraits and the fire (S14). The book's
/// name is added here: with more than one room the fires are the same object
/// drawn small, and what a room is *reading* is the thing that tells them
/// apart at a glance. A book's name is an address, not a score (Law 2).
private struct MenuRoomRow: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var action: () -> Void

    var body: some View {
        let isCurrent = room.id == model.currentRoom?.id
        let reading = model.openReading(in: room)
        Button(action: action) {
            HStack(spacing: 12) {
                // The current room marked with a chartreuse hairline — and,
                // because colour is never the only signal (§11), said as
                // "selected" to VoiceOver too.
                Rectangle()
                    .fill(isCurrent ? Palette.chartreuse : .clear)
                    .frame(width: 2, height: 34)
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.displayName(of: room))
                        .font(RibbonType.ui(16))
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
                        // The glyph hides itself everywhere else because it
                        // always sits beside the words it illustrates; on a
                        // room row there are no such words, so it says its
                        // own state — a state, never a number (Law 2, §11).
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(state.displayName)
                }
            }
            .frame(minHeight: 44)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .hoverEffect(.highlight)
        .accessibilityAddTraits(isCurrent ? [.isSelected] : [])
    }
}

// MARK: - You

/// Portrait and name, editable in place (S18) — presence is faces, so the
/// face can be added or changed here, not only at onboarding.
private struct YouIdentityRow: View {
    @Environment(AppModel.self) private var model

    @State private var editingName = false
    @State private var name = ""
    @State private var portraitItem: PhotosPickerItem?
    @FocusState private var nameFocused: Bool

    var body: some View {
        HStack(spacing: 14) {
            PhotosPicker(selection: $portraitItem, matching: .images) {
                PortraitView(
                    person: model.me, ink: nil, size: 56,
                    image: model.me.flatMap { model.portrait($0.id) })
            }
            .buttonStyle(.plain)
            // What the control does depends on whether there is a face
            // behind it, and it should not go on saying "add" to somebody
            // who has one.
            .accessibilityLabel(
                model.me.flatMap { model.portrait($0.id) } == nil
                    ? Copy.addAPortrait : Copy.changeYourPortrait)
            .onChange(of: portraitItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self),
                       let jpeg = downsampledJPEG(data) {
                        await model.setPortrait(jpeg)
                    }
                }
            }
            if editingName {
                TextField("", text: $name)
                    .font(RibbonType.ui(18))
                    .foregroundStyle(Palette.text)
                    .focused($nameFocused)
                    .onAppear { nameFocused = true }
                    .onSubmit {
                        let trimmed = name.trimmingCharacters(in: .whitespaces)
                        if !trimmed.isEmpty { model.updateMe(name: trimmed) }
                        editingName = false
                    }
            } else {
                Button {
                    name = model.me?.name ?? ""
                    editingName = true
                } label: {
                    // A short name draws a short word, and the word is the
                    // whole control: the target keeps its 44 pt in both
                    // directions so "Jo" is no harder to tap than "Jonathan"
                    // (§11, deviation 12). An empty name still has to be
                    // findable, so the placeholder stands in its place.
                    Text(model.me?.name ?? "")
                        .font(RibbonType.ui(18))
                        .foregroundStyle(Palette.text)
                        .frame(minWidth: 44, minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint(Copy.editsYourName)
            }
        }
    }
}

/// The current room's own controls: its name, your ink, the way out. These
/// lived only on your S12, which a fresh room of one couldn't reach
/// (deviations 9a); they moved to You (deviation 13) and now sit under the
/// room they are about.
private struct RoomControls: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onLeft: () -> Void

    @State private var editingRoomName = false
    @State private var roomName = ""
    @State private var showInkPicker = false
    @State private var confirmLeave = false
    @State private var askAboutNotes = false
    @FocusState private var roomNameFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if editingRoomName {
                TextField(
                    "", text: $roomName,
                    prompt: Text(Copy.roomName).foregroundStyle(Palette.muted))
                    .font(RibbonType.ui(17))
                    .foregroundStyle(Palette.text)
                    .frame(minHeight: 44)
                    .focused($roomNameFocused)
                    .onAppear { roomNameFocused = true }
                    .onSubmit {
                        model.renameRoom(room, to: roomName)
                        editingRoomName = false
                    }
            } else {
                MenuRow(title: Copy.nameThisRoom) {
                    roomName = room.name ?? ""
                    editingRoomName = true
                }
            }
            // Ink is identity from three people up (§4.5); below that the
            // room draws from the whole palette freely and there is nothing
            // to choose.
            if model.inkIsIdentity(in: room) {
                MenuRow(title: Copy.changeYourInk) { showInkPicker = true }
            }
            // The way out is quiet, never emphasised, and never hidden.
            QuietControl(title: Copy.leaveThisRoom) { confirmLeave = true }
        }
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
        .confirmationDialog(
            Copy.leaveRoomConfirm, isPresented: $confirmLeave, titleVisibility: .visible
        ) {
            Button(Copy.leaveThisRoom, role: .destructive) { askAboutNotes = true }
        }
        .confirmationDialog(
            Copy.leaveNotesQuestion, isPresented: $askAboutNotes, titleVisibility: .visible
        ) {
            // Leaving them is the default; taking them back is possible and
            // never the default (§6.8).
            Button(Copy.leaveThem) {
                model.leaveRoom(room, keepNotesBehind: true)
                onLeft()
            }
            Button(Copy.takeThemBack) {
                model.leaveRoom(room, keepNotesBehind: false)
                onLeft()
            }
        }
    }
}

/// The account (§6.10): an emailed code, no passwords. Signed out is a
/// state, not a nag — one quiet line, and the reason stated plainly.
private struct AccountControls: View {
    @Environment(AppModel.self) private var model

    @State private var signingIn = false
    @State private var passkeyLine: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if model.isSignedIn {
                if let address = model.accountEmail {
                    SmallCaps(address, size: 12)
                }
                // §6.10 wants a passkey where there is one. Offered here, on
                // the account, because that is what it belongs to — and only
                // ever added to the emailed code, never in place of it.
                if model.passkeysAvailable {
                    QuietControl(title: Copy.addAPasskey) { addPasskey() }
                    Text(passkeyLine ?? Copy.passkeyReason)
                        .font(RibbonType.ui(13))
                        .foregroundStyle(Palette.muted)
                }
                QuietControl(title: Copy.signOut) {
                    signingIn = false
                    Task { await model.signOutRemote() }
                }
            } else if model.remote == nil {
                // Remote is not configured in this build; no dead control.
                EmptyView()
            } else if signingIn {
                SignInInline(
                    onSignedIn: { signingIn = false },
                    onCancel: { signingIn = false })
            } else {
                QuietControl(title: Copy.signIn) { signingIn = true }
                Text(Copy.accountReason)
                    .font(RibbonType.ui(13))
                    .foregroundStyle(Palette.muted)
            }
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
                passkeyLine = Copy.passkeyDidntWork
            }
        }
    }
}

// MARK: - Joining a second room (S16, from the menu)

/// The door that was missing. A tapped invite link runs S16 by itself, from
/// anywhere — but a link that arrived in an email on a laptop, or in a
/// message thread this phone can't open, had nowhere to go once you already
/// had a room: the paste field lived on a page of onboarding nobody sees
/// twice. This is that field, kept.
///
/// It takes the link or the code out of it (`AppModel.inviteToken(fromPasted:)`),
/// and hands the token straight to the same join thread.
private struct JoinWithInviteScreen: View {
    var onToken: (UUID) -> Void

    @State private var pasted = ""
    @State private var missed = false

    var body: some View {
        VStack(spacing: 22) {
            Spacer()
            // The screen says its own name: it is pushed under a bar with no
            // title, and a screen nobody can name is a screen nobody can go
            // back to on purpose.
            SmallCaps(Copy.joinWithAnInvite, size: 12)
            Text(Copy.theLinkBringsYouIn)
                .font(RibbonType.ui(17))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
            TextField(
                "", text: $pasted,
                prompt: Text(Copy.pasteInvitePrompt).foregroundStyle(Palette.muted))
                .font(RibbonType.ui(16))
                .foregroundStyle(Palette.text)
                .multilineTextAlignment(.center)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Palette.surface, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Palette.rule, lineWidth: 1))
                .padding(.horizontal, 40)
                .submitLabel(.go)
                .onSubmit(accept)
                .onChange(of: pasted) { _, text in
                    // A pasted link is complete the moment it lands — don't
                    // make them find a go button (S17's field, kept).
                    missed = false
                    if AppModel.inviteToken(fromPasted: text) != nil { accept() }
                }
            if missed {
                Text(Copy.thatLinkIsntAnInvite)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
            }
            Spacer()
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .readableColumn()
        .room()
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
