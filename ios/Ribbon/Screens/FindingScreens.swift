import SwiftUI
import RibbonCore

// Being found (A72): finding an open room, asking into it, a found
// person, and your own being found. Pushed inside the menu, as pages.
//
// Nothing here is a number. A found room says its name or its people, its
// line, the book it is reading and its fire — never how many are in it —
// and an ask is answered by somebody letting you in, or by nothing at all.

/// The finding screens the menu pushes.
enum FindRoute: Hashable {
    case find
    case room(UUID)
    case person(PublicFace)
    case beingFound
}

/// What a found room is called: its own name, or the people in it who
/// chose to be found.
func foundRoomName(_ room: OpenRoom) -> String {
    room.shownName ?? Copy.roomOf(room.firstNames)
}

// MARK: - Find a room

struct FindRoomScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var onRoom: (OpenRoom) -> Void
    var onPerson: (PublicFace) -> Void
    var onBeingFound: () -> Void

    @State private var query = ""
    @State private var rooms: [OpenRoom] = []
    @State private var people: [PublicFace] = []
    /// An answer has come back at least once, so an empty one means empty.
    @State private var looked = false
    @State private var failed = false
    @State private var looking: Task<Void, Never>?

    var body: some View {
        RibbonScreen(title: Copy.findARoom, lede: Copy.findARoomLede, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 28) {
                CentredTextField(
                    text: $query, prompt: Copy.findPrompt, submitLabel: .search, centred: false,
                    onSubmit: { look(after: .zero) })
                .onChange(of: query) { _, _ in look(after: .milliseconds(350)) }

                if !model.beingFound.findable {
                    SettingsGroup(footnote: Copy.askNeedsBeingFound) {
                        SettingRow(Copy.beingFound, subtitle: Copy.beingFoundSub, action: onBeingFound)
                    }
                }

                if failed {
                    SettingNote(Copy.serverUnreachable)
                } else if looked && rooms.isEmpty && people.isEmpty {
                    SettingNote(OwnLine.clean(query) == nil ? Copy.noRoomIsOpenYet : Copy.nothingOpenMatches)
                }

                if !rooms.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        SectionLabel(Copy.openRooms)
                            .padding(.horizontal, RibbonShape.textInset)
                        VStack(spacing: RibbonShape.seam) {
                            ForEach(rooms) { room in
                                FoundRoomTile(room: room) { onRoom(room) }
                            }
                        }
                    }
                }

                if !people.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        SectionLabel(Copy.people)
                            .padding(.horizontal, RibbonShape.textInset)
                        VStack(spacing: RibbonShape.seam) {
                            ForEach(people) { face in
                                FoundPersonTile(face: face) { onPerson(face) }
                            }
                        }
                    }
                }
            }
            .animation(RibbonMotion.arrive, value: rooms.map(\.id))
            .animation(RibbonMotion.arrive, value: people.map(\.id))
            .animation(RibbonMotion.arrive, value: failed)
        }
        .task { await lookNow() }
    }

    /// Looks once the typing stops, so each letter is not its own search.
    private func look(after pause: Duration) {
        looking?.cancel()
        looking = Task {
            if pause > .zero { try? await Task.sleep(for: pause) }
            guard !Task.isCancelled else { return }
            await lookNow()
        }
    }

    private func lookNow() async {
        let asked = query
        do {
            async let foundRooms = model.findRooms(matching: asked)
            async let foundPeople = model.findPeople(matching: asked)
            let (r, p) = try await (foundRooms, foundPeople)
            // A slower answer to an older question does not replace a newer one.
            guard asked == query else { return }
            rooms = r
            people = p
            failed = false
            for face in r.flatMap(\.faces) + p { model.lookAt(face) }
        } catch is CancellationError {
            return
        } catch {
            guard asked == query else { return }
            failed = true
        }
        looked = true
    }
}

/// A found room as a tile: its name or its people, its line, faces, the
/// book, and the fire. The same paper as a room of your own.
struct FoundRoomTile: View {
    @Environment(AppModel.self) private var model
    let room: OpenRoom
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(foundRoomName(room))
                        .font(RibbonType.ui(17))
                        .foregroundStyle(Palette.text)
                    if let line = room.line {
                        Text(line)
                            .font(RibbonType.ui(14))
                            .foregroundStyle(Palette.muted)
                            .lineLimit(2)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    HStack(spacing: 8) {
                        FoundFaces(faces: room.faces, size: 18)
                        if let id = room.bookID, let book = Bible.book(id: id) {
                            SmallCaps(book.name, size: 11)
                        }
                    }
                }
                Spacer()
                if room.asked {
                    SmallCaps(Copy.asked, size: 11)
                }
                if let state = room.fireState(at: Date()), let scale = room.fire?.scale {
                    CampfireGlyph(state: state, scale: scale, height: 22)
                        .accessibilityRepresentation { Text(Copy.fireIs(state.displayName)) }
                }
            }
            .padding(.horizontal, RibbonShape.textInset)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .contentShape(Rectangle())
            .paper(.row)
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .combine)
    }
}

/// A found person as a tile: face, name, and their line.
struct FoundPersonTile: View {
    @Environment(AppModel.self) private var model
    let face: PublicFace
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                PortraitView(
                    person: Person(id: face.id, name: face.name), ink: nil, size: 36,
                    image: model.face(of: face))
                    .accessibilityHidden(true)
                RowText(title: face.name, subtitle: face.line)
                Spacer(minLength: 8)
                Chevron()
            }
            .padding(.horizontal, RibbonShape.textInset)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: RibbonShape.rowHeight, alignment: .leading)
            .contentShape(Rectangle())
            .paper(.row)
        }
        .buttonStyle(.pressable)
        .accessibilityElement(children: .combine)
        .onAppear { model.lookAt(face) }
    }
}

/// The faces of a found room's findable people, overlapped as a room's are.
private struct FoundFaces: View {
    @Environment(AppModel.self) private var model
    let faces: [PublicFace]
    var size: CGFloat

    var body: some View {
        HStack(spacing: -5) {
            ForEach(faces) { face in
                PortraitView(
                    person: Person(id: face.id, name: face.name), ink: nil, size: size,
                    image: model.face(of: face))
            }
        }
        .accessibilityHidden(true)
    }
}

// MARK: - A found room

struct FoundRoomScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let roomID: UUID
    var onPerson: (PublicFace) -> Void
    var onBeingFound: () -> Void

    private enum Phase: Equatable { case loading, gone, shown }
    @State private var phase: Phase = .loading
    @State private var room: OpenRoom?
    @State private var composing = false
    @State private var said = ""
    @State private var sending = false
    @State private var refusal: String?
    @State private var reporting: ConfirmState?
    @State private var reported = false
    @FocusState private var saidFocused: Bool

    var body: some View {
        RibbonScreen(
            title: room.map(foundRoomName) ?? Copy.findARoom,
            lede: room?.line,
            onBack: { dismiss() }
        ) {
            VStack(alignment: .leading, spacing: 28) {
                switch phase {
                case .loading:
                    SmallCaps(Copy.wordmark, size: 12, color: Palette.muted.opacity(0.6))
                        .padding(.horizontal, RibbonShape.textInset)
                case .gone:
                    SettingNote(refusal ?? Copy.roomIsNotOpen)
                case .shown:
                    if let room { shown(room) }
                }
            }
            .animation(RibbonMotion.settle, value: phase)
            .animation(RibbonMotion.arrive, value: composing)
            .animation(RibbonMotion.arrive, value: room?.asked)
            .animation(RibbonMotion.arrive, value: refusal)
        }
        .confirm($reporting, dismissTitle: Copy.neverMind)
        .task { await load() }
    }

    @ViewBuilder
    private func shown(_ room: OpenRoom) -> some View {
        if let id = room.bookID, let book = Bible.book(id: id) {
            HStack(spacing: 12) {
                RowText(title: Copy.readingBook(book.name))
                Spacer(minLength: 8)
                if let state = room.fireState(at: Date()), let scale = room.fire?.scale {
                    CampfireGlyph(state: state, scale: scale, height: 26)
                        .accessibilityRepresentation { Text(Copy.fireIs(state.displayName)) }
                }
            }
            .padding(.horizontal, RibbonShape.textInset)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: RibbonShape.rowHeight, alignment: .leading)
            .tile()
            .accessibilityElement(children: .combine)
        }

        if !room.faces.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                SectionLabel(Copy.people)
                    .padding(.horizontal, RibbonShape.textInset)
                VStack(spacing: RibbonShape.seam) {
                    ForEach(room.faces) { face in
                        FoundPersonTile(face: face) { onPerson(face) }
                    }
                }
            }
        }

        asking(room)

        if let refusal {
            Text(refusal)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, RibbonShape.textInset)
                .transition(.opacity)
        }

        if reported {
            Text(Copy.reported)
                .font(RibbonType.ui(14))
                .foregroundStyle(Palette.muted)
                .padding(.horizontal, RibbonShape.textInset)
        } else {
            QuietControl(title: Copy.reportThisRoom) {
                reporting = ConfirmState(question: Copy.reportQuestion, choices: [
                    ConfirmChoice(Copy.report) { report(room) },
                ])
            }
            .padding(.horizontal, RibbonShape.textInset)
        }
    }

    @ViewBuilder
    private func asking(_ room: OpenRoom) -> some View {
        if room.asked {
            SettingsGroup(footnote: Copy.youAsked) {
                SettingRow(Copy.takeBackYourAsk, chevron: false) { takeBack(room) }
            }
        } else if room.full {
            SettingNote(Copy.foundRoomIsFull)
        } else if !model.beingFound.findable {
            SettingsGroup(footnote: Copy.askNeedsBeingFound) {
                SettingRow(Copy.beingFound, subtitle: Copy.beingFoundSub, action: onBeingFound)
            }
        } else if composing {
            VStack(spacing: 14) {
                CentredTextField(
                    text: $said, prompt: Copy.askLinePrompt, submitLabel: .send, centred: false,
                    focus: $saidFocused, onSubmit: { send(room) })
                WayInButton(title: Copy.ask) { send(room) }
                    .padding(.horizontal, 60)
                    .disabled(sending)
            }
            .onAppear { saidFocused = true }
            .transition(.opacity)
        } else {
            WayInButton(title: Copy.askToJoin) { composing = true }
                .padding(.horizontal, 60)
                .transition(.opacity)
        }
    }

    private func load() async {
        do {
            if let found = try await model.openRoom(roomID) {
                room = found
                for face in found.faces { model.lookAt(face) }
                withAnimation(RibbonMotion.arrive) { phase = .shown }
            } else {
                phase = .gone
            }
        } catch {
            refusal = Copy.serverUnreachable
            phase = .gone
        }
    }

    private func send(_ room: OpenRoom) {
        guard !sending else { return }
        sending = true
        refusal = nil
        Task {
            defer { sending = false }
            do {
                try await model.askToJoin(room, saying: said)
                saidFocused = false
                composing = false
                said = ""
                self.room?.asked = true
            } catch {
                refusal = AppModel.askRefusal(error)
            }
        }
    }

    private func takeBack(_ room: OpenRoom) {
        refusal = nil
        Task {
            do {
                try await model.takeBackAsk(room)
                self.room?.asked = false
            } catch {
                refusal = Copy.serverUnreachable
            }
        }
    }

    private func report(_ room: OpenRoom) {
        reporting = nil
        Task {
            do {
                try await model.report(room: room.id)
                reported = true
            } catch {
                refusal = Copy.serverUnreachable
            }
        }
    }
}

// MARK: - A found person

struct FoundPersonScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let face: PublicFace
    var onRoom: (OpenRoom) -> Void

    @State private var rooms: [OpenRoom] = []
    @State private var asking: ConfirmState?
    @State private var line: String?
    @State private var reported = false

    var body: some View {
        let blocked = model.blockedPeople.contains(face.id)
        RibbonScreen(title: face.name, lede: face.line, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 28) {
                PortraitView(
                    person: Person(id: face.id, name: face.name), ink: nil, size: 88,
                    image: model.face(of: face))
                    .accessibilityHidden(true)
                    .padding(.horizontal, RibbonShape.textInset)

                if !rooms.isEmpty && !blocked {
                    VStack(alignment: .leading, spacing: 10) {
                        SectionLabel(Copy.roomsTheyveOpened)
                            .padding(.horizontal, RibbonShape.textInset)
                        VStack(spacing: RibbonShape.seam) {
                            ForEach(rooms) { room in
                                FoundRoomTile(room: room) { onRoom(room) }
                            }
                        }
                    }
                }

                if let line {
                    Text(line)
                        .font(RibbonType.ui(14))
                        .foregroundStyle(Palette.muted)
                        .padding(.horizontal, RibbonShape.textInset)
                        .transition(.opacity)
                }

                VStack(alignment: .leading, spacing: 14) {
                    if blocked {
                        QuietControl(title: Copy.unblock) { unblock() }
                    } else if face.id != model.me?.id {
                        QuietControl(title: Copy.block) {
                            asking = ConfirmState(question: Copy.blockQuestion(face.name), choices: [
                                ConfirmChoice(Copy.block, destructive: true) { block() },
                            ])
                        }
                    }
                    if !reported && face.id != model.me?.id {
                        QuietControl(title: Copy.report) {
                            asking = ConfirmState(question: Copy.reportQuestion, choices: [
                                ConfirmChoice(Copy.report) { report() },
                            ])
                        }
                    }
                }
                .padding(.horizontal, RibbonShape.textInset)
            }
            .animation(RibbonMotion.arrive, value: rooms.map(\.id))
            .animation(RibbonMotion.arrive, value: line)
        }
        .confirm($asking, dismissTitle: Copy.neverMind)
        .task {
            model.lookAt(face)
            await model.loadBlocked()
            rooms = (try? await model.openRooms(of: face.id)) ?? []
        }
    }

    private func block() {
        asking = nil
        Task {
            do {
                try await model.block(face)
                rooms = []
            } catch {
                line = Copy.serverUnreachable
            }
        }
    }

    private func unblock() {
        Task {
            do {
                try await model.unblock(face.id)
                rooms = (try? await model.openRooms(of: face.id)) ?? []
            } catch {
                line = Copy.serverUnreachable
            }
        }
    }

    private func report() {
        asking = nil
        Task {
            do {
                try await model.report(person: face.id)
                reported = true
                line = Copy.reported
            } catch {
                line = Copy.serverUnreachable
            }
        }
    }
}

// MARK: - Being found

struct BeingFoundScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var line = ""
    @FocusState private var lineFocused: Bool

    var body: some View {
        RibbonScreen(title: Copy.beingFound, lede: Copy.beingFoundLede, onBack: { dismiss() }) {
            VStack(alignment: .leading, spacing: 28) {
                if model.beingFound.canBeFindable {
                    SettingsGroup {
                        SettingSwitch(Copy.letPeopleFindYou, isOn: Binding(
                            get: { model.beingFound.findable },
                            set: { model.setFindable($0) }))
                    }
                } else {
                    SettingsGroup(footnote: Copy.thirteenOrOlderQuestion) {
                        SettingRow(Copy.iAmThirteenOrOlder, chevron: false) { model.sayThirteenOrOlder() }
                    }
                }

                VStack(alignment: .leading, spacing: 10) {
                    SectionLabel(Copy.yourLine)
                        .padding(.horizontal, RibbonShape.textInset)
                    CentredTextField(
                        text: $line, prompt: Copy.yourLinePrompt, centred: false,
                        focus: $lineFocused, onSubmit: commit)
                    .onChange(of: lineFocused) { _, focused in
                        if !focused { commit() }
                    }
                }

                let blocked = model.blockedList
                if !blocked.isEmpty {
                    SettingsGroup(title: Copy.peopleYouveBlocked) {
                        ForEach(blocked) { person in
                            SettingRow(person.name, value: Copy.unblock, chevron: false) {
                                Task { try? await model.unblock(person.id) }
                            }
                        }
                    }
                }
            }
            .animation(RibbonMotion.arrive, value: model.beingFound.canBeFindable)
            .animation(RibbonMotion.arrive, value: model.blockedPeople)
        }
        .onAppear { line = model.beingFound.line ?? "" }
        .onDisappear { commit() }
        .task { await model.loadBlocked() }
    }

    private func commit() {
        model.setOwnLine(line)
        line = model.beingFound.line ?? ""
    }
}

// MARK: - Asks, in the room's menu

/// Someone asking into this room: their face and what they said, and the
/// two answers. "Not now" tells them nothing; letting them in is how they
/// hear.
struct KnockTile: View {
    @Environment(AppModel.self) private var model
    let knock: Knock
    var onPerson: (PublicFace) -> Void

    @State private var answering = false
    @State private var refusal: String?

    var body: some View {
        SettingsGroup(footnote: refusal, footnoteAnnounces: true) {
            Button { onPerson(knock.person) } label: {
                HStack(alignment: .top, spacing: 12) {
                    PortraitView(
                        person: Person(id: knock.person.id, name: knock.person.name), ink: nil, size: 36,
                        image: model.face(of: knock.person))
                        .accessibilityHidden(true)
                    RowText(title: Copy.askedToJoin(knock.person.name), subtitle: knock.line ?? knock.person.line)
                    Spacer(minLength: 8)
                }
                .padding(.horizontal, RibbonShape.textInset)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .tile()
            }
            .buttonStyle(.pressable)
            .accessibilityElement(children: .combine)
            SettingRow(Copy.letThemIn, chevron: false) { letIn() }
            SettingRow(Copy.notNow, chevron: false) { model.setDown(knock) }
        }
        .disabled(answering)
        .onAppear { model.lookAt(knock.person) }
    }

    private func letIn() {
        answering = true
        refusal = nil
        Task {
            defer { answering = false }
            do {
                try await model.letIn(knock)
            } catch {
                refusal = AppModel.askRefusal(error)
            }
        }
    }
}
