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
    /// One ember on your shelf (A66), by its reading: the book's record,
    /// inside You rather than over the room.
    case ember(UUID)
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
                        onDownloads: { path.append(MenuRoute.downloads) },
                        onWhatsNew: { path.append(MenuRoute.whatsNew) },
                        onEmber: { readingID in path.append(MenuRoute.ember(readingID)) })
                }
            }
            .navigationDestination(for: MenuRoute.self) { route in
                destination(route)
            }
            .navigationDestination(for: PersonRoute.self) { route in
                // An ember's record names who read it, as portraits, and a
                // portrait goes to its person (S12) — here too, now that a
                // record can be opened from your shelf (A66). Without this
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
        case .ember(let readingID):
            if let reading = model.state.readings.first(where: { $0.id == readingID }) {
                // The record the room's shelf opens (S11), with one thing
                // left off: reading the book again belongs to the room it
                // was read in, not to you (A66). A quoted verse opens the
                // book over its own room, the way a tapped notification
                // does — the menu goes, the room comes, the page opens.
                // A room you have left keeps its books on your shelf
                // (§6.8), but there is no room any more to open them in,
                // and a page opened over another room would be read and
                // written as that room's: its verses are quoted, not
                // offered.
                let opensVerses = model.room(reading.roomID) != nil
                EmberRecordScreen(
                    reading: reading,
                    onOpenVerse: opensVerses
                        ? { verse in
                            model.pendingDestination = .verse(roomID: reading.roomID, readingID: reading.id, verse: verse)
                        }
                        : nil)
            }
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
/// current one has a ribbon laid into it from the tile's top edge, as the
/// chosen version has (A66) — a shape, where it was a 2-point line of the
/// accent — and, because colour is never the only signal (§11), it is said
/// as selected too.
private struct RoomTile: View {
    @Environment(AppModel.self) private var model
    let room: Room
    var action: () -> Void

    var body: some View {
        let isCurrent = room.id == model.currentRoom?.id
        let reading = model.openReading(in: room)
        Button(action: action) {
            HStack(spacing: 12) {
                // Where the hairline was, kept as clear space, so the
                // room's name has not moved from where it was read.
                Color.clear
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
            // Hung at the leading side, clear of the name: twelve in from
            // the tile's edge, over the space the hairline left.
            .overlay(alignment: .topLeading) {
                ChoiceRibbon(laid: isCurrent, width: 8, length: 20)
                    .padding(.leading, 12)
            }
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

/// You, set as the front of a Bible (A66): your name on the flyleaf, a
/// ribbon for each room you read in, the books you have finished, then the
/// settings, and at the very end a colophon — what the book is, what it is
/// set in, and where its words come from.
private struct YouScreen: View {
    @Environment(AppModel.self) private var model
    var onClose: () -> Void
    var onText: () -> Void
    var onDownloads: () -> Void
    var onWhatsNew: () -> Void
    /// An ember on your shelf, by its reading.
    var onEmber: (UUID) -> Void

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

                YourRibbonsSection()

                // The shelf is there once there is something on it: an
                // empty shelf on the front page would be a space waiting
                // to be filled, and nothing here asks for anything.
                let embers = YourShelf.embers(readings: model.state.readings)
                if !embers.isEmpty {
                    YourShelfSection(embers: embers, onEmber: onEmber)
                }

                SettingsGroup(title: Copy.howYouRead) {
                    SettingRow(Copy.textAndTranslation, subtitle: Copy.textSub, action: onText)
                }

                SettingsGroup(title: Copy.thisPhone) {
                    SettingRow(Copy.downloads, subtitle: Copy.downloadsSub, action: onDownloads)
                    // Every release's pictures and words, read again (A65):
                    // about this phone's build, so with what it holds.
                    SettingRow(Copy.whatsNewRow, subtitle: Copy.whatsNewRowSub, action: onWhatsNew)
                }

                AccountSection()

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
                    .padding(.horizontal, RibbonShape.textInset)
                    .padding(.top, 8)
                }

                Colophon(version: appVersion)
            }
        }
        .confirm($deleting, dismissTitle: Copy.neverMind)
    }

    /// The version and its build. Every build of a version says the same
    /// version, and which build a phone has is the first thing a test on
    /// two phones needs to know.
    private var appVersion: String {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        return Copy.versionLine(build.isEmpty ? version : "\(version) (\(build))")
    }
}

/// Your ribbons (A66): one for each room you read in, hanging from a
/// hairline across the page the way a Bible's ribbons hang from its
/// binding, each left where that room left it. A ribbon is a place, never a
/// measure (A30): it says which chapter, not how far.
///
/// Touching one goes to that room by the road a tapped notification takes,
/// which closes the menu and closes any book open over another room.
private struct YourRibbonsSection: View {
    @Environment(AppModel.self) private var model
    /// Whether the ribbons have been laid in. Once each time You is opened —
    /// not again on the way back from a page it pushed, which it never left.
    @State private var laid = false

    /// No two neighbours the same length: two ribbons of slightly different
    /// length read as two people (brief §5), and six as six rooms rather
    /// than as a fringe.
    private static let lengths: [CGFloat] = [56, 48, 62, 52, 58, 46]
    private static let column: CGFloat = 92
    private static let ribbonWidth: CGFloat = 14
    /// How far behind its left-hand neighbour each ribbon is laid in.
    private static let stagger: Double = 0.08

    var body: some View {
        let rooms = model.state.rooms
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(Copy.yourRibbons)
                .padding(.horizontal, RibbonShape.textInset)
            ScrollView(.horizontal) {
                HStack(alignment: .top, spacing: 0) {
                    ForEach(Array(rooms.enumerated()), id: \.element.id) { index, room in
                        ribbon(room, index: index)
                    }
                }
                .padding(.horizontal, RibbonShape.textInset)
            }
            .scrollIndicators(.hidden)
            // The binding: the full width of the row, still while the
            // ribbons scroll under it, and drawn over their top edges so
            // each reads as tucked into it.
            .overlay(alignment: .top) {
                HairlineRule()
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
            Text(Copy.yourRibbonsFootnote)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, RibbonShape.textInset)
        }
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
        return Button {
            model.pendingDestination = .room(roomID: room.id)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                // Laid in from the binding one after another, on the curve
                // a note unfurls on; under reduce motion each only fades in
                // where it hangs (`HangingRibbon`).
                HangingRibbon(
                    color: ink?.color ?? Palette.chartreuse,
                    width: Self.ribbonWidth,
                    length: Self.lengths[index % Self.lengths.count],
                    laid: laid,
                    delay: delay,
                    motion: RibbonMotion.settle)
                VStack(alignment: .leading, spacing: 3) {
                    Text(name)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.text)
                        .lineLimit(2)
                    SmallCaps(lies, size: 11)
                        .lineLimit(2)
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
            .frame(width: Self.column, alignment: .topLeading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Copy.ribbonSpoken(name, lies, ink: ink?.displayName))
        .accessibilityHint(Copy.goesToThatRoom)
        .accessibilityAddTraits(.isButton)
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

/// The line every ember on your shelf sits on: the foot of the ember
/// itself, not of the words under it, so a book read alone (no second line)
/// sits level with one read in company (S10's shared baseline).
private enum EmberFloor: AlignmentID {
    static func defaultValue(in context: ViewDimensions) -> CGFloat {
        context[VerticalAlignment.bottom]
    }
}

private extension VerticalAlignment {
    static let emberFloor = VerticalAlignment(EmberFloor.self)
}

/// Your shelf (A66): every book you have finished, in every room you have
/// read in — the books of a room you have left among them, as leaving
/// promises (§6.8) — first finished first. Embers on one baseline with no
/// shelf drawn (S10), each with its book and who it was read with. Never a
/// count: a row of objects, not a tally.
private struct YourShelfSection: View {
    @Environment(AppModel.self) private var model
    var embers: [Reading]
    var onEmber: (UUID) -> Void

    /// Wide enough for "with the Thursday study" over two lines under the
    /// smallest ember; narrower than the largest, which it sits under.
    private static let words: CGFloat = 104

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(Copy.yourShelf)
                .padding(.horizontal, RibbonShape.textInset)
            ScrollView(.horizontal) {
                HStack(alignment: .emberFloor, spacing: 18) {
                    ForEach(embers) { reading in
                        ember(reading)
                    }
                }
                .padding(.horizontal, RibbonShape.textInset)
            }
            .scrollIndicators(.hidden)
        }
    }

    private func ember(_ reading: Reading) -> some View {
        let book = Bible.book(id: reading.bookID)?.name ?? reading.bookID
        let company = companyLine(of: reading)
        return Button {
            onEmber(reading.id)
        } label: {
            VStack(spacing: 6) {
                EmberView(scale: reading.handiwork.scale)
                    .alignmentGuide(.emberFloor) { dimensions in
                        dimensions[VerticalAlignment.bottom]
                    }
                VStack(spacing: 2) {
                    SmallCaps(book, size: 12, color: Palette.text.opacity(0.8))
                    if let company {
                        SmallCaps(company, size: 11)
                    }
                }
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: Self.words)
            }
            .contentShape(Rectangle())
        }
        // An ember takes a press the way a tile does, as on the room's
        // shelf.
        .buttonStyle(.pressable)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Copy.emberSpoken(book, company))
        .accessibilityAddTraits(.isButton)
    }

    /// Who the book was read with: first names, "and others" for the rest,
    /// or a named room of three or more by its name. A book read alone, or
    /// in a room you have since left, is said by nothing.
    private func companyLine(of reading: Reading) -> String? {
        let company = YourShelf.company(
            of: reading,
            rooms: model.state.rooms,
            memberships: model.state.memberships,
            me: model.me?.id)
        switch company {
        case .alone:
            return nil
        case .people(let ids, let andOthers):
            let names = ids.compactMap { model.person($0)?.name }.map { firstName($0) }
            if names.isEmpty && !andOthers { return nil }
            return Copy.shelfWith(names, andOthers: andOthers)
        case .room(let name):
            return Copy.shelfWithRoom(name)
        }
    }
}

/// The colophon (A66): the book's last page, where a book says what it is,
/// what it is set in, and whose words it borrows — the Wave, the version,
/// the typefaces, and the credit owed for the original words (A60). Set
/// small and centred, as a colophon is, after the last thing that can be
/// done here.
private struct Colophon: View {
    var version: String

    var body: some View {
        VStack(spacing: 10) {
            WaveMark(color: Palette.muted.opacity(0.6))
                .frame(width: 22, height: 22)
            SmallCaps(version, size: 11, color: Palette.muted.opacity(0.7))
            // Sentences, so set as sentences rather than in small caps.
            Group {
                Text(Copy.colophonSetIn)
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
