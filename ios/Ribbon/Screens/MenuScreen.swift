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
    /// What's new, read again (A65): every release, newest first.
    case whatsNew
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
    /// A room's shelf, written to a file and being handed on (A73).
    @State private var keeping: KeptShelf?
    /// What the menu's pushed pages grow out of (I45): an ember on You.
    @Namespace private var flowSpace

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
                            onJoinWithInvite: { path.append(MenuRoute.joinWithInvite) },
                            onFindARoom: { path.append(FindRoute.find) },
                            onPerson: { face in path.append(FindRoute.person(face)) },
                            onKeep: { closed in keep(closed) })
                    } else {
                        GrainBackground()
                    }
                case .you:
                    YouScreen(
                        onClose: { dismiss() },
                        onText: { path.append(MenuRoute.text) },
                        onDownloads: { path.append(MenuRoute.downloads) },
                        onNotifications: { path.append(MenuRoute.notifications) },
                        onWhatsNew: { path.append(MenuRoute.whatsNew) },
                        onBeingFound: { path.append(FindRoute.beingFound) })
                }
            }
            .background(EdgeSwipeBack())
            .navigationDestination(for: MenuRoute.self) { route in
                destination(route)
            }
            .navigationDestination(for: FindRoute.self) { route in
                finding(route)
            }
            .navigationDestination(for: PersonRoute.self) { route in
                // An ember's record names who read it, as portraits, and a
                // portrait goes to its person (S12) — here too, now that a
                // record can be opened from your shelf (A67). Without this
                // the faces in it would be links that go nowhere.
                if let personRoom = model.room(route.roomID) {
                    PersonScreen(
                        personID: route.personID,
                        room: personRoom,
                        onOpenVerse: { verse, readingID in
                            model.pendingDestination = .verse(roomID: personRoom.id, readingID: readingID, verse: verse)
                        })
                }
            }
        }
        .environment(\.flowSpace, flowSpace)
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
        // The shelf, handed on: the moment a room closes on this phone, and
        // whenever a closed room's shelf is asked for again (A73). With no
        // room left open, the menu has nothing under it and goes too.
        .sheet(item: $keeping, onDismiss: {
            if model.currentRoom == nil { dismiss() }
        }) { kept in
            ActivitySheet(items: [kept.url])
                .presentationDetents([.medium, .large])
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

    private func keep(_ room: Room) {
        if let url = model.shelfFile(for: room) { keeping = KeptShelf(url: url) }
    }

    /// Being found (A72): finding, a found room, a found person, and your
    /// own being found, each a page inside the menu.
    @ViewBuilder
    private func finding(_ route: FindRoute) -> some View {
        switch route {
        case .find:
            FindRoomScreen(
                onRoom: { room in path.append(FindRoute.room(room.id)) },
                onPerson: { face in path.append(FindRoute.person(face)) },
                onBeingFound: { path.append(FindRoute.beingFound) })
        case .room(let id):
            FoundRoomScreen(
                roomID: id,
                onPerson: { face in path.append(FindRoute.person(face)) },
                onBeingFound: { path.append(FindRoute.beingFound) })
            .id(id)
        case .person(let face):
            FoundPersonScreen(face: face, onRoom: { room in path.append(FindRoute.room(room.id)) })
                .id(face.id)
        case .beingFound:
            BeingFoundScreen()
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
        case .whatsNew:
            // The launch's screen with every release on it (A65), pushed
            // here rather than drawn over the room. Leaving goes back to
            // You and records nothing: whether a launch shows the screen is
            // the model's decision, and reading it again never touches it.
            WhatsNewScreen(history: WhatsNew.releases) {
                if !path.isEmpty { path.removeLast() }
            }
            .toolbar(.hidden, for: .navigationBar)
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
    var onFindARoom: () -> Void
    var onPerson: (PublicFace) -> Void
    /// A room's shelf to hand on: one that has just closed, or an older one.
    var onKeep: (Room) -> Void

    @State private var showInkPicker = false

    /// The room, as a page of its own (I47), set as You is: your rooms'
    /// ribbons hanging from the top of the screen, each one a room to go to;
    /// the room's title page, its name and its people; what it asks of you;
    /// how it is set, as sentences; its own controls; and the way to other
    /// rooms. The invite and the way out are at the foot, on the glass.
    var body: some View {
        GeometryReader { outer in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    YourRibbonsSection(hungFrom: outer.safeAreaInsets.top, onRoom: onSwitch)

                    RoomTitlePage(room: room)
                        .padding(.horizontal, RibbonShape.textInset)
                        .padding(.top, 36)

                    // Asks into this room (A72), first: somebody is waiting
                    // at the door, and every answer is here.
                    VStack(spacing: 12) {
                        ForEach(model.knocks(on: room)) { knock in
                            KnockTile(knock: knock, onPerson: onPerson)
                                .transition(.opacity)
                        }
                        if model.isFull(room) {
                            // S15's full state, said where the invite was.
                            SettingNote(Copy.roomHoldsTwelve)
                        }
                    }
                    .padding(.top, 28)

                    RoomSettingsLines(
                        room: room,
                        onNotifications: onNotifications, onPlan: onPlan,
                        onInk: { showInkPicker = true })
                        .padding(.horizontal, RibbonShape.textInset)
                        .padding(.top, 8)

                    RoomControls(room: room, onLeft: onClose, onClosed: onKeep)
                        .padding(.top, 32)

                    // Other rooms: the ribbons above are the ones you are
                    // in; these are the ways to more.
                    // On one line where they fit, one under another where
                    // they do not.
                    ViewThatFits(in: .horizontal) {
                        HStack(spacing: 18) { moreRooms }
                        VStack(alignment: .leading, spacing: 0) { moreRooms }
                    }
                    .padding(.horizontal, RibbonShape.textInset)
                    .padding(.top, 20)

                    ClosedRoomsSection(onKeep: onKeep)
                        .padding(.top, 28)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, RibbonShape.screenMargin)
                .padding(.bottom, 40)
                .readableColumn()
                .animation(RibbonMotion.arrive, value: model.knocks(on: room).map(\.id))
            }
            .ignoresSafeArea(edges: .top)
            // What scrolls up goes softly under the clock, where the page
            // runs on under it for the ribbons to hang from.
            .overlay(alignment: .top) {
                LinearGradient(
                    colors: [Palette.ground, Palette.ground.opacity(0.85), .clear],
                    startPoint: .top, endPoint: .bottom)
                    .frame(height: outer.safeAreaInsets.top + 18)
                    .ignoresSafeArea(edges: .top)
                    .allowsHitTesting(false)
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom, spacing: 0) { foot }
        }
        .environment(\.tileFinish, .glass)
        .room()
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showInkPicker) {
            InkPickerSheet(room: room)
        }
    }

    /// The ways to more rooms than these.
    @ViewBuilder
    private var moreRooms: some View {
        QuietControl(title: Copy.startARoomControl, action: onStartARoom)
        // A join goes through the backend and cannot happen without one. No
        // dead control (§6.1).
        if model.remote != nil {
            QuietControl(title: Copy.joinWithAnInvite, action: onJoinWithInvite)
        }
        // Finding needs an account to ask with (A72).
        if model.discoveryAvailable {
            QuietControl(title: Copy.findARoom, action: onFindARoom)
        }
    }

    /// The foot: the room's one invitation, and the way out, where the thumb
    /// is.
    private var foot: some View {
        HStack {
            // The link resolves through the backend, so without one there
            // is nothing to hand out and no control for it; a full room says
            // so on the page.
            if model.remote != nil && !model.isFull(room) {
                FootButton(title: Copy.inviteSomeone, accent: true, action: onInvite)
            }
            Spacer()
            FootButton(title: Copy.close, action: onClose)
                .keyboardShortcut(.cancelAction)
        }
        .padding(.horizontal, RibbonShape.screenMargin)
        .padding(.top, 20)
        .padding(.bottom, 6)
        .background(alignment: .top) { LeatherPlate() }
    }
}

/// A control on the leather foot (I47): its words in letterpress small caps
/// on a panel pressed into the cover; the accent for the one thing the page
/// asks of you.
private struct FootButton: View {
    var title: String
    var accent = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            SmallCaps(title, size: 13, color: accent ? Palette.chartreuse : Palette.text.opacity(0.88))
                .shadow(color: .black.opacity(0.7), radius: 0, y: -1)
                .padding(.horizontal, 22)
                .frame(height: 44)
                .background(Pressed(shape: Capsule()))
                .contentShape(Capsule())
        }
        .buttonStyle(.pressable)
    }
}

/// Deleting your account (I47): the owner asked for *"a dim red button with
/// a icon"*. Red, but dim — the one red on the page, and never loud; the
/// question it asks is still asked before anything happens (§6.8).
private struct DeleteAccountButton: View {
    var action: () -> Void
    private static let red = Color(hex: "C4574B")

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: "trash")
                    .font(.system(size: 14, weight: .medium))
                SmallCaps(Copy.deleteAccount, size: 13, color: Self.red.opacity(0.9))
            }
            .foregroundStyle(Self.red.opacity(0.9))
            .shadow(color: .black.opacity(0.6), radius: 0, y: -1)
            .padding(.horizontal, 18)
            .frame(height: 44)
            .background(Capsule().fill(Self.red.opacity(0.11)))
            .overlay(Capsule().strokeBorder(Self.red.opacity(0.35), lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(.pressable)
    }
}

/// The room's title page (I47): its name as a book's title is set, and
/// under it its people — faces you can go to, and their names in italic,
/// with what the room is reading. Names, never a count.
private struct RoomTitlePage: View {
    @Environment(AppModel.self) private var model
    let room: Room

    var body: some View {
        let members = model.members(of: room)
        let present = room.isPaused ? Set<UUID>() : Set(model.presentPeople.filter { !$0.isIdle }.map(\.id))
        let names = members.compactMap { membership -> String? in
            membership.personID == model.me?.id ? Copy.you : model.person(membership.personID).map { firstName($0.name) }
        }
        let book = model.openReading(in: room).flatMap { Bible.book(id: $0.bookID)?.name }
        VStack(alignment: .leading, spacing: 14) {
            Text(model.displayName(of: room))
                .font(RibbonType.display(36))
                .foregroundStyle(Palette.text)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            HStack(spacing: 12) {
                HStack(spacing: -8) {
                    ForEach(members) { membership in
                        NavigationLink(value: PersonRoute(personID: membership.personID, roomID: room.id)) {
                            PortraitView(
                                person: model.person(membership.personID),
                                ink: membership.ink, size: 34,
                                image: model.portrait(membership.personID))
                            .overlay(Circle().stroke(Palette.ground, lineWidth: 1.5))
                            .background(
                                Circle()
                                    .stroke(Palette.chartreuse, lineWidth: 1.5)
                                    .padding(-3)
                                    .opacity(present.contains(membership.personID) ? 1 : 0))
                        }
                        .buttonStyle(.pressable)
                        .accessibilityLabel(model.person(membership.personID)?.name ?? "")
                    }
                }
                if let line = Copy.roomTitleLine(names, reading: book) {
                    Text(line)
                        .font(RibbonType.scriptureItalic(15))
                        .foregroundStyle(Palette.text.opacity(0.66))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }
}

/// How the room is set, said as You's colophon says your pages (I47): each
/// setting a word underlined in the accent, opening where it is set.
private struct RoomSettingsLines: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onNotifications: () -> Void
    var onPlan: () -> Void
    var onInk: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            sentence(Copy.roomTold)
            sentence(Copy.roomPlan)
            // Ink is who you are from three people up (§4.5); below that
            // the room draws from the whole palette and there is nothing to
            // choose.
            if model.inkIsIdentity(in: room), let ink = model.myMembership(in: room)?.ink {
                sentence(Copy.roomInk(ink.displayName))
            }
        }
        .environment(\.openURL, OpenURLAction { url in
            switch url.host() {
            case "notifications": onNotifications()
            case "plan": onPlan()
            case "ink": onInk()
            default: return .discarded
            }
            return .handled
        })
    }

    private func sentence(_ markdown: String) -> some View {
        var text = (try? AttributedString(markdown: markdown)) ?? AttributedString(markdown)
        for run in text.runs where run.link != nil {
            text[run.range].underlineStyle = Text.LineStyle(pattern: .solid, color: Palette.chartreuse.opacity(0.6))
            text[run.range].foregroundColor = Palette.text
        }
        return Text(text)
            .font(RibbonType.scripture(17))
            .foregroundStyle(Palette.text.opacity(0.8))
            .lineSpacing(5)
            .tint(Palette.text)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// The current room's own controls: its name, being found, the way out.
/// Your ink is a word in the room's sentences (I47).
private struct RoomControls: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var onLeft: () -> Void
    /// The room closed on this request (A73): its shelf is handed on.
    var onClosed: (Room) -> Void

    @State private var editingRoomName = false
    @State private var closingQuestion: ConfirmState?
    @State private var closingLine: String?
    @State private var roomName = ""
    @State private var editingRoomLine = false
    @State private var roomLine = ""
    @FocusState private var roomLineFocused: Bool
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
            }
            .animation(RibbonMotion.settle, value: editingRoomName)

            // Being found (A72): your yes, and the room's line for the
            // people who find it. Who has not said yes is never named.
            if model.discoveryAvailable {
                SettingsGroup {
                    SettingSwitch(Copy.letThisRoomBeFound, subtitle: opennessLine, isOn: Binding(
                        get: { model.myMembership(in: room)?.wouldOpen ?? false },
                        set: { model.setWouldOpen($0, in: room) }))
                    if editingRoomLine {
                        TextField(
                            "", text: $roomLine,
                            prompt: Text(Copy.roomLinePrompt).foregroundStyle(Palette.muted))
                            .font(RibbonType.ui(17))
                            .foregroundStyle(Palette.text)
                            .focused($roomLineFocused)
                            .submitLabel(.done)
                            .onSubmit(commitRoomLine)
                            .onChange(of: roomLineFocused) { _, focused in
                                if !focused { commitRoomLine() }
                            }
                            .padding(.horizontal, RibbonShape.textInset)
                            .frame(maxWidth: .infinity, minHeight: RibbonShape.rowHeight, alignment: .leading)
                            .tile()
                            .onAppear { roomLineFocused = true }
                            .transition(.opacity)
                    } else {
                        SettingRow(Copy.aLineAboutThisRoom, subtitle: room.line, chevron: false) {
                            roomLine = room.line ?? ""
                            editingRoomLine = true
                        }
                        .transition(.opacity)
                    }
                }
                .padding(.top, 18)
                .animation(RibbonMotion.settle, value: editingRoomLine)
            }
            // The way out is quiet, never emphasised, and never hidden.
            QuietControl(title: Copy.leaveThisRoom) { leaving = .confirm }
                .padding(.horizontal, RibbonShape.textInset)

            // Closing (§6.8, A73): as quiet as leaving, asked once, and
            // never a retention offer. Who has not asked is never named.
            if model.closingAvailable {
                closing
                    .padding(.horizontal, RibbonShape.textInset)
                    .confirm($closingQuestion, dismissTitle: Copy.neverMind)
            }
        }
        .confirm(leaveDialog)
    }

    @ViewBuilder
    private var closing: some View {
        VStack(alignment: .leading, spacing: 6) {
            // A yes that is waiting is offered again once saying it closes
            // the room: it went quiet, or the last one said yes or left.
            if model.closing(of: room) == .waitingForOthers && !model.closesOnYourYes(room) {
                Text(Copy.youAskedToClose)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                QuietControl(title: Copy.takeItBack) { takeBackClosing() }
            } else {
                QuietControl(title: Copy.closeThisRoom) {
                    closingQuestion = ConfirmState(
                        question: model.closeQuestion(for: room),
                        choices: [ConfirmChoice(Copy.closeThisRoom, destructive: true) { askToClose() }])
                }
            }
            if let closingLine {
                Text(closingLine)
                    .font(RibbonType.ui(14))
                    .foregroundStyle(Palette.muted)
                    .transition(.opacity)
            }
        }
        .animation(RibbonMotion.arrive, value: model.closing(of: room))
    }

    private func askToClose() {
        closingQuestion = nil
        closingLine = nil
        let asked = room
        Task {
            do {
                if try await model.askToClose(asked) { onClosed(asked) }
            } catch {
                closingLine = Copy.serverUnreachable
            }
        }
    }

    private func takeBackClosing() {
        closingLine = nil
        Task {
            do {
                try await model.takeBackClosing(room)
            } catch {
                closingLine = Copy.serverUnreachable
            }
        }
    }

    private var opennessLine: String {
        switch model.openness(of: room) {
        case .closed: return Copy.roomOpensWhenEveryone
        case .waitingForOthers: return Copy.youSaidYesWaiting
        case .open: return Copy.roomIsOpen
        }
    }

    private func commitRoomLine() {
        guard editingRoomLine else { return }
        model.setLine(roomLine, of: room)
        editingRoomLine = false
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

/// You, set as the front of a Bible (A67), and since I47 as a page of its
/// own in the redesign's glass. Your ribbons hang from the top of the
/// screen, one for each room, as a Bible's hang from its binding; under
/// them a title page, your name and face; your shelf; and the colophon —
/// how your pages are set, said as sentences, each setting a word in them
/// to tap. The account, and the small print, after it. The way out is at
/// the foot, on the glass, where the thumb is.
private struct YouScreen: View {
    @Environment(AppModel.self) private var model
    var onClose: () -> Void
    var onText: () -> Void
    var onDownloads: () -> Void
    var onNotifications: () -> Void
    var onWhatsNew: () -> Void
    var onBeingFound: () -> Void
    @State private var deleting: ConfirmState?

    var body: some View {
        GeometryReader { _ in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // Your rooms' ribbons hang over your rooms now, on the
                    // rooms page (I47); this page begins with you.
                    YouIdentity()
                        .padding(.horizontal, RibbonShape.textInset)
                        .padding(.top, 40)


                    YourColophon(
                        version: buildNumber,
                        onText: onText, onDownloads: onDownloads,
                        onNotifications: onNotifications, onBeingFound: onBeingFound,
                        onWhatsNew: onWhatsNew)
                        .padding(.horizontal, RibbonShape.textInset)
                        .padding(.top, 44)

                    AccountSection()
                        .padding(.top, 40)

                    if model.remote != nil {
                        DeleteAccountButton {
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
                        .padding(.horizontal, RibbonShape.textInset)
                        .padding(.top, 16)
                    }

                    Colophon()
                        .padding(.top, 36)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, RibbonShape.screenMargin)
                .padding(.bottom, 40)
                .readableColumn()
            }
            .scrollEdgeEffectStyle(.soft, for: .top)
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom, spacing: 0) { foot }
        }
        .environment(\.tileFinish, .glass)
        .room()
        .toolbar(.hidden, for: .navigationBar)
        .confirm($deleting, dismissTitle: Copy.neverMind)
    }

    /// The foot: the version this phone has, and the way out.
    private var foot: some View {
        HStack {
            SmallCaps(appVersion, size: 11, color: Color(hex: "C9B48E").opacity(0.7))
                .shadow(color: .black.opacity(0.7), radius: 0, y: -1)
            Spacer()
            FootButton(title: Copy.close, action: onClose)
                .keyboardShortcut(.cancelAction)
        }
        .padding(.leading, RibbonShape.screenMargin + RibbonShape.textInset)
        .padding(.trailing, RibbonShape.screenMargin)
        .padding(.top, 20)
        .padding(.bottom, 6)
        .background(alignment: .top) { LeatherPlate() }
    }

    /// The version and its build. Every build of a version says the same
    /// version, and which build a phone has is the first thing a test on
    /// two phones needs to know.
    private var appVersion: String { Copy.versionLine(buildNumber) }

    private var buildNumber: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        return build.isEmpty ? version : "\(version) (\(build))"
    }
}

/// The colophon (I47): how your pages are set, as a book's last page says
/// how it was made — but yours to change. Each setting is a word in the
/// sentence, underlined in the accent, and tapping it opens where it is
/// set.
private struct YourColophon: View {
    @Environment(AppModel.self) private var model
    var version: String
    var onText: () -> Void
    var onDownloads: () -> Void
    var onNotifications: () -> Void
    var onBeingFound: () -> Void
    var onWhatsNew: () -> Void

    var body: some View {
        let words = model.words(room: model.currentRoom)
        let version = TranslationRegistry.translation(for: words)?.fullName ?? words.displayName
        let size = Int(model.settings.scriptureSize.rounded())
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                SatinRibbon()
                    .frame(width: 8, height: 14)
                SmallCaps(Copy.colophon, size: 12)
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            // What a colophon is, for anybody who has not met one.
            Text(Copy.colophonLede)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.text.opacity(0.62))
                .fixedSize(horizontal: false, vertical: true)
                .padding(.bottom, 4)
            sentence(Copy.colophonPages(face: model.settings.face.name, size: size, version: version))
            sentence(Copy.colophonKept)
            sentence(Copy.colophonTold)
            if model.discoveryAvailable {
                sentence(Copy.colophonFound(model.beingFound.findable))
            }
            sentence(Copy.colophonNew(self.version))
        }
        .environment(\.openURL, OpenURLAction { url in
            switch url.host() {
            case "text": onText()
            case "downloads": onDownloads()
            case "notifications": onNotifications()
            case "found": onBeingFound()
            case "new": onWhatsNew()
            default: return .discarded
            }
            return .handled
        })
    }

    /// A sentence of the colophon, its links underlined in the accent.
    private func sentence(_ markdown: String) -> some View {
        var text = (try? AttributedString(markdown: markdown)) ?? AttributedString(markdown)
        for run in text.runs where run.link != nil {
            text[run.range].underlineStyle = Text.LineStyle(pattern: .solid, color: Palette.chartreuse.opacity(0.6))
            text[run.range].foregroundColor = Palette.text
        }
        return Text(text)
            .font(RibbonType.scripture(17))
            .foregroundStyle(Palette.text.opacity(0.8))
            .lineSpacing(5)
            .tint(Palette.text)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// Your ribbons (A67): one for each room you read in, hanging from a
/// hairline across the page the way a Bible's ribbons hang from its
/// binding, each left where that room left it. A ribbon is a place, never a
/// measure (A30): it says which chapter, not how far.
///
/// Touching one goes to that room by the road a tapped notification takes,
/// which closes the menu and closes any book open over another room.
private struct YourRibbonsSection: View {
    @Environment(AppModel.self) private var model
    /// How far above the page the screen's top edge is: the ribbons hang
    /// from it, under the clock (I47), rather than from a rule on the page.
    var hungFrom: CGFloat = 0
    /// A ribbon touched: that room (I47 — they hang over your rooms now,
    /// where they used to hang over You).
    var onRoom: (UUID) -> Void
    /// Whether the ribbons have been laid in. Once each time the page opens —
    /// not again on the way back from a page it pushed, which it never left.
    @State private var laid = false

    /// No two neighbours the same length: two ribbons of slightly different
    /// length read as two people (brief §5), and six as six rooms rather
    /// than as a fringe.
    private static let lengths: [CGFloat] = [56, 48, 62, 52, 58, 46]
    /// The room's words under each ribbon. Grows with the reader's type, so
    /// that at the largest sizes a name wraps rather than ending in an
    /// ellipsis; the row scrolls, so a wider column only scrolls further.
    /// The ribbons themselves are drawing, not type, and stay as they are.
    @ScaledMetric(relativeTo: .footnote) private var column: CGFloat = 92
    private static let ribbonWidth: CGFloat = 14
    /// How far behind its left-hand neighbour each ribbon is laid in.
    private static let stagger: Double = 0.08

    var body: some View {
        let rooms = model.state.rooms
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: 0) {
                ForEach(Array(rooms.enumerated()), id: \.element.id) { index, room in
                    ribbon(room, index: index)
                }
            }
            .padding(.horizontal, RibbonShape.textInset)
        }
        .scrollIndicators(.hidden)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Copy.yourRibbons)
        .onAppear { laid = true }
    }

    /// One room's ribbon, and under it the room and where its ribbon lies.
    /// In your ink there when ink is who you are in it, and in the accent
    /// where it is not — a room of two, where nobody's ink is theirs.
    private func ribbon(_ room: Room, index: Int) -> some View {
        let name = model.displayName(of: room)
        let lies = place(of: room)
        let ink = model.myMembership(in: room)?.ink
        let delay = Double(index) * Self.stagger
        let here = room.id == model.currentRoom?.id
        return Button {
            onRoom(room.id)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                // Laid in from the binding one after another, on the curve
                // a note unfurls on; under reduce motion each only fades in
                // where it hangs (`HangingRibbon`).
                HangingRibbon(
                    color: ink?.color ?? Palette.chartreuse,
                    width: Self.ribbonWidth,
                    length: hungFrom + Self.lengths[index % Self.lengths.count] + 40,
                    laid: laid,
                    delay: delay,
                    motion: RibbonMotion.settle)
                    // The room you are in hangs in full; the others a
                    // little back, and say so with their names too.
                    .opacity(here ? 1 : 0.55)
                VStack(alignment: .leading, spacing: 3) {
                    Text(name)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(here ? Palette.text : Palette.text.opacity(0.7))
                        .lineLimit(2)
                    HStack(spacing: 5) {
                        if let reading = model.openReading(in: room) {
                            CampfireGlyph(state: model.fireState(of: reading), scale: reading.handiwork.scale, height: 12)
                                .accessibilityHidden(true)
                        }
                        SmallCaps(lies, size: 11, color: Palette.text.opacity(here ? 0.7 : 0.55))
                            .lineLimit(2)
                    }
                }
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                // The words come with their ribbon. A change of light, so
                // it keeps its curve under reduce motion as well.
                .opacity(laid ? 1 : 0)
                .animation(RibbonMotion.arrive.delay(laid ? delay : 0), value: laid)
            }
            // Air on the trailing side, so one room's words stop short of
            // the next room's ribbon.
            .padding(.trailing, 12)
            .frame(width: column, alignment: .topLeading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Copy.ribbonSpoken(name, lies, ink: ink?.displayName))
        .accessibilityHint(Copy.goesToThatRoom)
        .accessibilityAddTraits(here ? [.isButton, .isSelected] : .isButton)
    }

    /// Where the room's ribbon lies (A30) as a chapter — "Mark 4" — or,
    /// before anyone has closed the book having moved, the book; and with no
    /// book open, between books.
    private func place(of room: Room) -> String {
        guard let reading = model.openReading(in: room) else { return Copy.betweenBooks }
        if let left = model.ribbon(in: reading) {
            return VerseAddress(bookID: reading.bookID, chapter: left.chapter, verse: left.verse).chapterFormatted
        }
        return Bible.book(id: reading.bookID)?.name ?? reading.bookID
    }
}

/// The small print (A67): what the book is set in, and the credit owed for
/// the original words (A60). Set small and centred, after the last thing
/// that can be done here; the version is in the foot (I47).
private struct Colophon: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 10) {
            WaveMark(color: Palette.muted.opacity(0.6))
                .frame(width: 22, height: 22)
            // Sentences, so set as sentences rather than in small caps.
            Group {
                Text(Copy.colophonSetIn(model.settings.face.name))
                Text(Copy.originalCredit)
            }
            .font(RibbonType.ui(12))
            .foregroundStyle(Palette.muted.opacity(0.7))
            .fixedSize(horizontal: false, vertical: true)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, RibbonShape.textInset)
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
        // A title page (I47): your face in a double rule, and your name
        // as a book's title is set, the reason under it in italic.
        HStack(alignment: .center, spacing: 18) {
                PhotosPicker(selection: $portraitItem, matching: .images) {
                    PortraitView(person: me, ink: nil, size: 72, image: face)
                        .overlay(Circle().stroke(Palette.text.opacity(0.22), lineWidth: 1))
                        .padding(6)
                        .overlay(Circle().stroke(Palette.text.opacity(0.10), lineWidth: 1))
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
            VStack(alignment: .leading, spacing: 6) {
                TextField("", text: $name, prompt: Text(Copy.yourName).foregroundStyle(Palette.muted))
                    .font(RibbonType.display(36))
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
                Text(hasFace ? Copy.yourFaceReason : Copy.addAPortraitReason)
                    .font(RibbonType.scriptureItalic(14))
                    .foregroundStyle(Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
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
    /// The caller's hold on the caret, when it wants one — to raise the
    /// keyboard as a step arrives, or to put it away. The field keeps its
    /// own otherwise. Passed in rather than laid over the field from
    /// outside, so that one binding, not two, decides where focus is.
    var focus: FocusState<Bool>.Binding?
    var onSubmit: () -> Void = {}

    @FocusState private var ownFocus: Bool

    var body: some View {
        let focused = focus?.wrappedValue ?? ownFocus
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
            .focused(focus ?? $ownFocus)
            .padding(.horizontal, RibbonShape.textInset)
            .frame(maxWidth: .infinity, minHeight: 52)
            .paper(.row)
            // The field you are typing into says so. On paper this dark the
            // caret was the only sign — a blinking point, and on a screen
            // with two fields (an address, then a code) no sign at all of
            // which one was listening. Its edge brightens under the caret,
            // as the name's hairline does in the menu (A53).
            .overlay {
                TileShape.row.shape
                    .strokeBorder(Palette.text.opacity(focused ? 0.32 : 0), lineWidth: 1)
                    .allowsHitTesting(false)
            }
            .animation(RibbonMotion.arrive, value: focused)
            .accessibilityLabel(prompt)
    }
}
