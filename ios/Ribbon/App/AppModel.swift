import Foundation
import SwiftUI
import RibbonCore

// The app's one store. Local-first: every mutation lands in AppState and is
// persisted; a remote backend (when configured and signed in) syncs the
// same objects later. Cold start renders from this state instantly — no
// splash, no skeleton (§05).

/// The join a tapped invite link is waiting to run (S16).
struct PendingInvite: Identifiable, Equatable {
    let token: UUID
    /// "Not you?" signed the last person out: the join re-presents at the
    /// name, not at a second preview (S16).
    var resumesAtName = false
    var id: UUID { token }
}

@MainActor
@Observable
final class AppModel {
    private(set) var state: AppState
    let store: LocalStore
    let scripture = ScriptureStore.shared
    let presence: PresenceService
    let reachability = Reachability()
    /// The backend, when configured (SupabaseConfig.remoteEnabled). Nil
    /// means fully local — every remote call below is best-effort and
    /// nothing blocks reading.
    private(set) var remote: RemoteSync?
    /// Set by an opened invite link; RootView and onboarding watch it.
    var pendingInvite: PendingInvite?
    /// A reinstall's restore has the person back and is still bringing
    /// their rooms (§6.10): the room waits rather than minting a stray one.
    private(set) var restoringRooms = false

    /// Who is in the book right now (empty means the form is absent).
    private(set) var presentPeople: [PresentPerson] = []
    /// Per-session only (§4.2): read quietly is never remembered across
    /// launches — slipping in invisibly is a choice made each time.
    var readingQuietly = false {
        didSet { Task { await presenceVisibilityChanged() } }
    }
    /// The person being followed, if any.
    var followingPersonID: UUID?
    /// Where the followed person is now — the reading surface scrolls to
    /// it (§4.2: your scroll is theirs).
    private(set) var followTarget: VerseAddress?
    /// A tap on the shoulder just arrived (§4.3): the name, for one quiet
    /// line, and the haptic already played.
    private(set) var thinkingOfYouFrom: String?

    /// Portraits cache (person id → image).
    private var portraits: [UUID: UIImage] = [:]

    /// The one loaded model, for the environment's fallback (see
    /// `EnvironmentValues.appModel`). Written once, on the main actor, in
    /// `load()` — before any view exists to read it.
    nonisolated(unsafe) private(set) static var current: AppModel?

    init(state: AppState, store: LocalStore, presence: PresenceService) {
        self.state = state
        self.store = store
        self.presence = presence
    }

    static func load() async -> AppModel {
        let store = LocalStore()
        let state = await store.load()
        let model = AppModel(state: state, store: store, presence: LocalPresenceService())
        await model.loadPortraits()
        if SupabaseConfig.remoteEnabled {
            model.remote = await RemoteSync.restore()
        }
        model.retryPendingTranscripts()
        AppModel.current = model
        return model
    }

    private func persist() {
        let snapshot = state
        Task.detached(priority: .utility) { [store] in
            await store.save(snapshot)
        }
    }

    // MARK: - Me, rooms, membership

    var me: Person? { state.me }

    /// The rooms this person is in now, in the order they joined.
    var liveRooms: [Room] { state.rooms.filter { !$0.isDeparted } }
    /// Rooms this person left, kept for their shelves (§6.8).
    var departedRooms: [Room] { state.rooms.filter(\.isDeparted) }

    /// The room on screen: the chosen one, else the first live room. Never
    /// a departed room by default — after leaving the last room, the
    /// fresh-room-of-one branch takes over (§6.8 "reading continues").
    var currentRoom: Room? {
        state.rooms.first { $0.id == state.currentRoomID } ?? liveRooms.first
    }

    func person(_ id: UUID) -> Person? {
        id == state.me?.id ? state.me : state.people[id]
    }

    func portrait(_ id: UUID) -> UIImage? { portraits[id] }

    /// The people in the room now.
    func members(of room: Room) -> [Membership] {
        state.memberships
            .filter { $0.roomID == room.id && $0.isActive }
            .sorted { $0.joinedAt < $1.joinedAt }
    }

    /// Everyone who has ever been in the room, departed members included —
    /// an ember record shows who read the book (S11).
    func everyone(in roomID: UUID) -> [Membership] {
        state.memberships
            .filter { $0.roomID == roomID }
            .sorted { $0.joinedAt < $1.joinedAt }
    }

    func membership(of personID: UUID, in roomID: UUID) -> Membership? {
        state.memberships.first { $0.roomID == roomID && $0.personID == personID }
    }

    func myMembership(in room: Room) -> Membership? {
        guard let me = state.me else { return nil }
        return membership(of: me.id, in: room.id)
    }

    /// Ink semantics (§4.5): a room of two draws from the whole palette
    /// freely; at three or more, ink is identity. Identity is sticky: if
    /// the room drops back to two, ink stays assigned until someone asks
    /// for the free palette in settings.
    func inkIsIdentity(in room: Room) -> Bool {
        if members(of: room).count >= 3 { return true }
        guard let since = room.inkIdentitySince else { return false }
        if let restored = room.freePaletteRestoredAt, restored > since { return false }
        return true
    }

    /// A member of a room of three or more who hasn't picked an ink yet
    /// (§6.7) — the S01 row "An ink to pick", and the newcomer's step.
    func needsInkPick(in room: Room) -> Bool {
        inkIsIdentity(in: room) && myMembership(in: room)?.ink == nil
    }

    /// The ink a person's marks are drawn in: their membership ink where
    /// ink is identity, else a stable ink of their own (§03 — a monogram
    /// in their ink; §4.4 — marks in the author's ink). Never the viewer's.
    func inkForDisplay(_ personID: UUID, in roomID: UUID) -> Ink {
        membership(of: personID, in: roomID)?.ink ?? Ink.stable(for: personID)
    }

    /// Ink turns into identity the moment the room first becomes three
    /// (§4.5): remembered on the room so highlights from before keep their
    /// colors with the one-line explanation.
    private func noteInkTransition(in roomID: UUID) {
        guard let i = state.rooms.firstIndex(where: { $0.id == roomID }),
              state.rooms[i].inkIdentitySince == nil,
              state.memberships.filter({ $0.roomID == roomID && $0.isActive }).count >= 3
        else { return }
        state.rooms[i].inkIdentitySince = Date()
    }

    /// The free palette, back by request (§4.5).
    func restoreFreePalette(in room: Room) {
        guard let i = state.rooms.firstIndex(where: { $0.id == room.id }) else { return }
        state.rooms[i].freePaletteRestoredAt = Date()
        persist()
    }

    /// The display name of a room: its own, or the members' first names.
    /// A room of one is "Your room" — a room named after its only member
    /// reads as a mirror.
    func displayName(of room: Room) -> String {
        if let name = room.name, !name.isEmpty { return name }
        // A room you left keeps the names it had — "Your room" would be
        // a mirror on a shelf that was two people's.
        let roster = room.isDeparted ? everyone(in: room.id) : members(of: room)
        let names = roster.compactMap { person($0.personID)?.name.split(separator: " ").first }
        if names.count <= 1 { return Copy.yourRoom }
        return names.joined(separator: " & ")
    }

    /// What a new room will be called once people join — the prompt in
    /// the name field (S15).
    var derivedRoomNamePrompt: String {
        guard let me = state.me else { return Copy.roomName }
        return "\(firstName(me.name)) & …"
    }

    // MARK: - Onboarding & rooms

    /// `startRoom: false` is the joiner's path (S16): the person exists
    /// first, the room they land in is the one the invite names.
    func completeOnboarding(name: String, portraitData: Data?, startRoom: Bool = true) async {
        // A person exists once, ever: re-running the thread must never
        // mint a second identity and orphan what the first one left.
        if state.me != nil {
            updateMe(name: name)
            if let portraitData {
                await setPortrait(portraitData)
            }
            if startRoom, currentRoom == nil {
                createRoom(named: nil)
            }
            return
        }
        // The Keychain outlives the app: after a reinstall the session is
        // already signed in while local state is empty. The person must
        // then be the account — a random id here would fail every RLS
        // check and orphan the account's rooms.
        let personID = remote?.userID ?? UUID()
        var portraitPath: String?
        if let portraitData {
            portraitPath = try? await store.writePortrait(portraitData, personID: personID)
            if let image = UIImage(data: portraitData) { portraits[personID] = image }
        }
        let person = Person(id: personID, name: name, portraitPath: portraitPath, translation: .bsb)
        state.me = person
        if isSignedIn { state.boundAccountID = personID }
        persist()
        if isSignedIn {
            // Reinstall: the account's rooms and profile come back —
            // before any fresh room is minted, so an account that already
            // has rooms doesn't gain an empty stray one.
            await reconcileOwnProfile()
            await refreshFromRemote()
            await pushLocalGraph()
        }
        if startRoom, liveRooms.isEmpty {
            createRoom(named: nil)
        }
        persist()
    }

    /// A reinstall, or a new phone (§6.10): the session is in the Keychain
    /// and the account has a profile. The person is rebuilt from it and
    /// their rooms pulled — no name question, no "welcome back". Returns
    /// false when there is nothing to restore, and the thread continues
    /// as a first run.
    /// Bounded: the thread's mark holds for the profile, never past the
    /// cap (§6.10, ≤ 3 s). A fetch that loses the race is cancelled with
    /// nothing written, so a late answer can never land over a name being
    /// typed. Once the person is back, their rooms follow behind — the
    /// room renders when they arrive (`restoringRooms`).
    func restoreFromAccountIfPossible(within cap: Duration) async -> Bool {
        guard state.me == nil, let remote, remote.isSignedIn, let uid = remote.userID else { return false }
        let row: RemoteSync.ProfileRow? = await withTaskGroup(of: RemoteSync.ProfileRow?.self) { group in
            group.addTask { try? await remote.fetchOwnProfile() }
            group.addTask {
                try? await Task.sleep(for: cap)
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
        guard let row, state.me == nil else { return false }
        let person = Person(
            id: uid, name: row.name, portraitPath: nil,
            translation: TranslationID(rawValue: row.translation))
        state.me = person
        state.boundAccountID = uid
        persist()
        if row.portraitPath != nil { fetchRemotePortrait(uid) }
        restoringRooms = true
        Task {
            await refreshFromRemote()
            if state.currentRoomID == nil { state.currentRoomID = liveRooms.first?.id }
            persist()
            restoringRooms = false
        }
        return true
    }

    @discardableResult
    func createRoom(named name: String?) -> Room {
        guard let me = state.me else { fatalError("room before person") }
        let room = Room(name: name, createdAt: Date())
        state.rooms.append(room)
        state.memberships.append(Membership(roomID: room.id, personID: me.id, joinedAt: Date()))
        state.currentRoomID = room.id
        persist()
        return room
    }

    /// A room of one that was never read in — minted by "Start a room" a
    /// moment before an invite link arrived. Nobody needs it in the rooms
    /// sheet (S14) as "Your room".
    func discardEmptyRoomOfOne(except keep: UUID?) {
        guard let me = state.me else { return }
        let empties = liveRooms.filter { room in
            room.id != keep
                && members(of: room).count == 1
                && !state.readings.contains { $0.roomID == room.id }
        }
        guard !empties.isEmpty else { return }
        let ids = Set(empties.map(\.id))
        state.rooms.removeAll { ids.contains($0.id) }
        state.memberships.removeAll { ids.contains($0.roomID) }
        state.invites.removeAll { ids.contains($0.roomID) }
        if let current = state.currentRoomID, ids.contains(current) {
            state.currentRoomID = keep ?? liveRooms.first?.id
        }
        persist()
        if let remote, remote.isSignedIn {
            for id in ids {
                let personID = me.id
                Task { try? await remote.deleteMembership(roomID: id, personID: personID) }
            }
        }
    }

    func switchRoom(to roomID: UUID) {
        state.currentRoomID = roomID
        followingPersonID = nil
        followTarget = nil
        persist()
    }

    // MARK: - Invites (S15)

    /// The live invite for a room, if one has been handed out.
    func liveInvite(for room: Room) -> Invite? {
        state.invites.first { $0.roomID == room.id && $0.expiresAt > Date() }
    }

    /// Whether the backend holds the invite — the link only works once it
    /// does (S16), so the share sheet waits for this.
    func isRegistered(_ invite: Invite) -> Bool {
        remote == nil || state.registeredInviteIDs.contains(invite.id)
    }

    /// The invite to hand out: the live one, or a fresh one. Reuses a live
    /// invite rather than minting link after link.
    func invite(for room: Room) -> Invite {
        guard let me = state.me else { fatalError("invite before person") }
        if let existing = liveInvite(for: room) { return existing }
        let invite = Invite(roomID: room.id, createdBy: me.id, createdAt: Date())
        state.invites.append(invite)
        persist()
        return invite
    }

    /// Registers the invite with the backend — the room and membership
    /// first, in case the room predates sign-in — and only then is the
    /// link worth sending. Throws when it can't; the caller says so.
    func registerInvite(for room: Room) async throws -> Invite {
        let invite = invite(for: room)
        guard let remote, remote.isSignedIn else {
            if remote == nil { return invite }
            throw SupabaseError.notSignedIn
        }
        if state.registeredInviteIDs.contains(invite.id) { return invite }
        try await remote.push(room: room, includeName: true)
        if let membership = myMembership(in: room) {
            try await remote.push(membership: membership)
        }
        try await remote.push(invite: invite)
        state.registeredInviteIDs.insert(invite.id)
        persist()
        return invite
    }

    /// Quietly retries any invite that never registered — on foreground,
    /// on sign-in.
    private func registerPendingInvites() async {
        guard let remote, remote.isSignedIn else { return }
        for invite in state.invites where invite.expiresAt > Date() && !state.registeredInviteIDs.contains(invite.id) {
            guard let room = state.rooms.first(where: { $0.id == invite.roomID }), !room.isDeparted else { continue }
            _ = try? await registerInvite(for: room)
        }
    }

    func isFull(_ room: Room) -> Bool {
        members(of: room).count >= Room.capacity
    }

    /// Rooms whose rename hasn't landed remotely — merge() must not let a
    /// stale pull revert an edit that was never pushed. In-memory only: a
    /// relaunch before the push lands re-exposes the edge, accepted for a
    /// rename.
    private var pendingRenamePushes: Set<UUID> = []

    /// Naming a room after the fact (S15's naming half, reachable later).
    func renameRoom(_ room: Room, to name: String?) {
        guard let i = state.rooms.firstIndex(where: { $0.id == room.id }) else { return }
        let trimmed = name?.trimmingCharacters(in: .whitespaces)
        state.rooms[i].name = (trimmed?.isEmpty ?? true) ? nil : trimmed
        persist()
        if let remote, remote.isSignedIn {
            let updated = state.rooms[i]
            pendingRenamePushes.insert(updated.id)
            Task {
                if (try? await remote.push(room: updated, includeName: true)) != nil {
                    pendingRenamePushes.remove(updated.id)
                }
            }
        }
    }

    // MARK: - Leaving and closing (§6.8)

    /// Leaving: one confirmation, plainly worded, no guilt. Notes default
    /// to staying — they were left for the other person. The room stays
    /// on this device as a departed room, so "You'll keep the books on
    /// your shelf" is true; it is never pulled back in.
    func leaveRoom(_ room: Room, keepNotesBehind: Bool) {
        guard let me = state.me else { return }
        let readingIDs = Set(state.readings.filter { $0.roomID == room.id }.map(\.id))
        if !keepNotesBehind {
            let mine = state.notes.filter { readingIDs.contains($0.readingID) && $0.authorID == me.id }
            let audio = mine.compactMap(\.audioPath)
            Task { await store.removeAudio(named: audio) }
            state.notes.removeAll { readingIDs.contains($0.readingID) && $0.authorID == me.id }
        }
        // Highlights stay, always — a mark on a shared page, not a
        // possession.
        if let i = state.memberships.firstIndex(where: { $0.roomID == room.id && $0.personID == me.id }) {
            state.memberships[i].leftAt = Date()
        }
        if let r = state.rooms.firstIndex(where: { $0.id == room.id }) {
            state.rooms[r].leftAt = Date()
        }
        state.invites.removeAll { $0.roomID == room.id }
        if state.currentRoomID == room.id {
            state.currentRoomID = liveRooms.first?.id
        }
        state.pendingDepartures.append(PendingDeparture(roomID: room.id, personID: me.id))
        persist()
        Task { await replayDepartures() }
    }

    /// Closing a room of one (§6.8): the shelf goes with it, so export is
    /// offered first by the screen. Multi-member closing waits for the
    /// agreement flow.
    func closeRoomOfOne(_ room: Room) {
        guard members(of: room).count <= 1 else { return }
        leaveRoom(room, keepNotesBehind: true)
        let readingIDs = Set(state.readings.filter { $0.roomID == room.id }.map(\.id))
        let audio = state.notes.filter { readingIDs.contains($0.readingID) }.compactMap(\.audioPath)
        Task { await store.removeAudio(named: audio) }
        state.notes.removeAll { readingIDs.contains($0.readingID) }
        state.highlights.removeAll { readingIDs.contains($0.readingID) }
        state.cards.removeAll { readingIDs.contains($0.readingID) }
        state.positions.removeAll { readingIDs.contains($0.readingID) }
        state.readings.removeAll { $0.roomID == room.id }
        state.rooms.removeAll { $0.id == room.id }
        state.memberships.removeAll { $0.roomID == room.id }
        persist()
    }

    /// Departures made offline are replayed before every pull, so a room
    /// you left can't be pulled back in (§6.8, §08 offline).
    private func replayDepartures() async {
        guard let remote, remote.isSignedIn else { return }
        for departure in state.pendingDepartures {
            if (try? await remote.deleteMembership(roomID: departure.roomID, personID: departure.personID)) != nil {
                state.pendingDepartures.removeAll { $0 == departure }
            }
        }
        persist()
    }

    /// Forgetting a departed room (its shelf included) — from the rooms
    /// sheet, deliberately.
    func forgetDepartedRoom(_ room: Room) {
        guard room.isDeparted else { return }
        let readingIDs = Set(state.readings.filter { $0.roomID == room.id }.map(\.id))
        let audio = state.notes.filter { readingIDs.contains($0.readingID) }.compactMap(\.audioPath)
        Task { await store.removeAudio(named: audio) }
        state.notes.removeAll { readingIDs.contains($0.readingID) }
        state.highlights.removeAll { readingIDs.contains($0.readingID) }
        state.cards.removeAll { readingIDs.contains($0.readingID) }
        state.positions.removeAll { readingIDs.contains($0.readingID) }
        state.readings.removeAll { $0.roomID == room.id }
        state.rooms.removeAll { $0.id == room.id }
        state.memberships.removeAll { $0.roomID == room.id }
        if state.currentRoomID == room.id { state.currentRoomID = liveRooms.first?.id }
        persist()
    }

    func pickInk(_ ink: Ink, in room: Room) {
        guard let me = state.me,
              let index = state.memberships.firstIndex(where: { $0.roomID == room.id && $0.personID == me.id })
        else { return }
        state.memberships[index].ink = ink
        persist()
        if let remote, remote.isSignedIn {
            let membership = state.memberships[index]
            Task { try? await remote.push(membership: membership) }
        }
    }

    // MARK: - Export (§13)

    /// The room's whole shelf as a readable file, for the share sheet.
    func exportShelf(of room: Room) -> URL? {
        let readingIDs = Set(state.readings.filter { $0.roomID == room.id }.map(\.id))
        var people: [UUID: ShelfExport.Person] = [:]
        for membership in everyone(in: room.id) {
            if let person = person(membership.personID) {
                people[person.id] = ShelfExport.Person(name: person.name, translation: person.translation)
            }
        }
        let export = ShelfExport(
            roomName: displayName(of: room),
            readings: state.readings.filter { $0.roomID == room.id },
            notes: state.notes.filter { readingIDs.contains($0.readingID) },
            highlights: state.highlights.filter { readingIDs.contains($0.readingID) },
            cards: state.cards.filter { readingIDs.contains($0.readingID) },
            people: people,
            verseText: { [scripture] address, translation in
                scripture.verseText(address, translation: translation)
            })
        let directory = FileManager.default.temporaryDirectory
        return try? export.writeFile(to: directory)
    }

    // MARK: - Readings and the fire

    /// The room's one open reading — the latest book that is neither
    /// finished nor set aside (§03).
    func openReading(in room: Room) -> Reading? {
        state.readings
            .filter { $0.roomID == room.id && $0.isOpen }
            .max { $0.startedAt < $1.startedAt }
    }

    /// Books the room set aside for another (§6.6): still going, still
    /// theirs, back in one tap from the chooser.
    func setAsideReadings(in room: Room) -> [Reading] {
        state.readings
            .filter { $0.roomID == room.id && $0.isSetAside }
            .sorted { ($0.setAsideAt ?? .distantPast) > ($1.setAsideAt ?? .distantPast) }
    }

    /// The shelf: every finished reading, oldest first (S10).
    func shelf(of room: Room) -> [Reading] {
        state.readings
            .filter { $0.roomID == room.id && $0.isFinished }
            .sorted { ($0.finishedAt ?? .distantPast) < ($1.finishedAt ?? .distantPast) }
    }

    /// The latest ember — what the room shows in the fire's place after a
    /// finish, until the next book starts (§6.5).
    func latestEmber(in room: Room) -> Reading? {
        shelf(of: room).last
    }

    /// Starting a fire (§6.6). A book still going is set aside — it keeps
    /// its notes and comes back from the chooser; a set-aside book picked
    /// again simply resumes, and anything else open steps aside for it.
    @discardableResult
    func startReading(bookID: String, in room: Room) -> Reading {
        let now = Date()
        for i in state.readings.indices where state.readings[i].roomID == room.id && state.readings[i].isOpen {
            state.readings[i].setAsideAt = now
            pushReadingRemote(state.readings[i])
        }
        if let i = state.readings.firstIndex(where: { $0.roomID == room.id && $0.isSetAside && $0.bookID == bookID }) {
            state.readings[i].setAsideAt = nil
            persist()
            pushReadingRemote(state.readings[i])
            return state.readings[i]
        }
        let scale = Bible.book(id: bookID)?.scale ?? .medium
        let reading = Reading(
            roomID: room.id, bookID: bookID, startedAt: now,
            handiwork: Handiwork(scale: scale))
        state.readings.append(reading)
        persist()
        pushReadingRemote(reading)
        return reading
    }

    /// Picking up a set-aside book again.
    @discardableResult
    func resumeReading(_ reading: Reading) -> Reading {
        guard let room = state.rooms.first(where: { $0.id == reading.roomID }) else { return reading }
        return startReading(bookID: reading.bookID, in: room)
    }

    private func pushReadingRemote(_ reading: Reading) {
        guard let remote, remote.isSignedIn else { return }
        Task { try? await remote.push(reading: reading) }
    }

    func quietDays(for room: Room) -> [QuietDay] {
        state.quietDays.filter { $0.roomID == room.id }
    }

    func fireState(of reading: Reading, at now: Date = Date()) -> FireState {
        guard let room = state.rooms.first(where: { $0.id == reading.roomID }) else { return .catching }
        return reading.handiwork.state(at: now, bankedIntervals: quietDays(for: room).bankedIntervals)
    }

    /// Fuel is reading (§4.1). Called as Scripture scrolls under the
    /// reader; the engine's own throttle makes frequency harmless. A
    /// finished book is a record, not a fire: it takes no fuel.
    func recordReadingActivity(reading: Reading, at address: VerseAddress) {
        guard let me = state.me,
              let index = state.readings.firstIndex(where: { $0.id == reading.id }),
              let room = state.rooms.first(where: { $0.id == reading.roomID }),
              !state.readings[index].isFinished, !room.isPaused, !room.isDeparted
        else { return }
        let banked = quietDays(for: room).bankedIntervals
        state.readings[index].handiwork.feed(by: me.id, at: Date(), bankedIntervals: banked)
        savePosition(reading: reading, address: address)
        stampLastRead(personID: me.id, in: room.id, at: Date())
        persist()
        // The other phone learns of this feeding through the rolling
        // window — steady needs to know two people fed the same fire. One
        // push per credited event, and the fire row rides along.
        if let remote, remote.isSignedIn,
           let event = state.readings[index].handiwork.recentFuel.last,
           event.personID == me.id, event.at != lastPushedFuelAt {
            lastPushedFuelAt = event.at
            let updated = state.readings[index]
            Task {
                // The reading row first: a fuel event landing before its
                // reading exists fails the foreign key and is lost.
                try? await remote.push(reading: updated)
                try? await remote.push(fuel: event, readingID: updated.id)
            }
        }
    }

    private var lastPushedFuelAt = Date.distantPast

    /// One last-read stamp per person per room (§13): rendered as "Ruth
    /// read Tuesday", never a clock time, and the only thing kept past the
    /// rolling window.
    private func stampLastRead(personID: UUID, in roomID: UUID, at date: Date) {
        var stamps = state.lastReadAt[roomID] ?? [:]
        if let existing = stamps[personID], existing >= date { return }
        stamps[personID] = date
        state.lastReadAt[roomID] = stamps
    }

    func savePosition(reading: Reading, address: VerseAddress) {
        guard let me = state.me else { return }
        let position = ReadingPosition(
            readingID: reading.id, personID: me.id,
            chapter: address.chapter, verse: address.verse, updatedAt: Date())
        if let index = state.positions.firstIndex(where: { $0.readingID == reading.id && $0.personID == me.id }) {
            state.positions[index] = position
        } else {
            state.positions.append(position)
        }
    }

    /// Where I am in a reading — mine, not the room's (§03).
    func myPosition(in reading: Reading) -> VerseAddress {
        guard let me = state.me,
              let position = state.positions.first(where: { $0.readingID == reading.id && $0.personID == me.id })
        else { return VerseAddress(bookID: reading.bookID, chapter: 1, verse: 1) }
        return VerseAddress(bookID: reading.bookID, chapter: position.chapter, verse: position.verse)
    }

    func hasPosition(in reading: Reading) -> Bool {
        guard let me = state.me else { return false }
        return state.positions.contains { $0.readingID == reading.id && $0.personID == me.id }
    }

    /// The room's last activity line ("Ruth read this morning") — the
    /// latest stamp among the *other* people in the room (§13). The line
    /// is a person, so tapping it goes to them (S12).
    func lastReader(in room: Room) -> (personID: UUID, line: String)? {
        guard let me = state.me else { return nil }
        let stamps = (state.lastReadAt[room.id] ?? [:]).filter { $0.key != me.id }
        guard let latest = stamps.max(by: { $0.value < $1.value }),
              let person = person(latest.key)
        else { return nil }
        return (person.id, Copy.readRecently(firstName(person.name), RibbonClock.phrase(for: latest.value)))
    }

    /// Marking a quiet day (§4.7) banks the fire for the room. The room
    /// sees who did it — an act of care, performed in public.
    func markQuietDay(in room: Room) {
        guard let me = state.me, !room.isPaused, !room.isDeparted else { return }
        let day = QuietDay(roomID: room.id, personID: me.id, markedAt: Date(), timeZone: .current)
        guard !state.quietDays.contains(where: {
            $0.roomID == room.id && $0.personID == me.id && $0.localDate == day.localDate
        }) else { return }
        state.quietDays.append(day)
        persist()
        if let remote, remote.isSignedIn {
            Task { try? await remote.push(quietDay: day) }
        }
    }

    func activeQuietDay(in room: Room, at now: Date = Date()) -> QuietDay? {
        quietDays(for: room).first { $0.bankedInterval?.contains(now) == true }
    }

    /// Finishing a book (§6.5): the handiwork becomes an ember.
    func finishReading(_ reading: Reading) {
        guard let index = state.readings.firstIndex(where: { $0.id == reading.id }),
              !state.readings[index].isFinished
        else { return }
        state.readings[index].finishedAt = Date()
        state.readings[index].setAsideAt = nil
        persist()
        pushReadingRemote(state.readings[index])
    }

    // MARK: - Notes

    func notes(in reading: Reading) -> [Note] {
        state.notes
            .filter { $0.readingID == reading.id }
            .sorted { $0.verse < $1.verse }
    }

    func notes(in reading: Reading, chapter: Int) -> [Note] {
        notes(in: reading).filter { $0.verse.chapter == chapter }
    }

    /// Notes left for me that I haven't found yet — the room's waiting rows
    /// (S01). Rows, never a count, never a badge.
    func waitingNotes(in room: Room) -> [Note] {
        guard let me = state.me, let reading = openReading(in: room) else { return [] }
        return notes(in: reading).filter { $0.authorID != me.id && !$0.foundBy.contains(me.id) }
    }

    @discardableResult
    func leaveWrittenNote(_ body: String, at verse: VerseAddress, in reading: Reading) -> Note {
        guard let me = state.me else { fatalError("note before person") }
        let note = Note(
            readingID: reading.id, authorID: me.id, verse: verse,
            kind: .written, body: body, createdAt: Date())
        state.notes.append(note)
        recordReadingActivity(reading: reading, at: verse)
        persist()
        return note
    }

    @discardableResult
    func leaveVoiceNote(audioURL: URL, waveform: [Float], at verse: VerseAddress, in reading: Reading) -> Note {
        guard let me = state.me else { fatalError("note before person") }
        let note = Note(
            readingID: reading.id, authorID: me.id, verse: verse,
            kind: .voice, audioPath: audioURL.lastPathComponent,
            waveform: waveform, transcriptState: .pending, createdAt: Date())
        state.notes.append(note)
        recordReadingActivity(reading: reading, at: verse)
        persist()
        let noteID = note.id
        Task {
            let transcript = await Transcriber.transcribe(url: audioURL)
            self.setTranscript(noteID: noteID, transcript: transcript)
        }
        return note
    }

    func retryTranscript(_ note: Note) {
        guard let path = note.audioPath else { return }
        setTranscriptState(noteID: note.id, state: .pending)
        Task {
            let url = await store.audioFileURL(path)
            let transcript = await Transcriber.transcribe(url: url)
            self.setTranscript(noteID: note.id, transcript: transcript)
        }
    }

    /// A transcript left pending when the app quit would read "Transcript
    /// coming" forever (S04) — it is picked up again on launch.
    private func retryPendingTranscripts() {
        for note in state.notes where note.kind == .voice && note.transcriptState == .pending {
            retryTranscript(note)
        }
    }

    /// Whether the phone can write transcripts at all — refused once, the
    /// line says so and routes to Settings instead of offering a retry
    /// that can never succeed (S25).
    var speechRecognitionRefused: Bool { Transcriber.isRefused }

    private func setTranscript(noteID: UUID, transcript: String?) {
        guard let index = state.notes.firstIndex(where: { $0.id == noteID }) else { return }
        state.notes[index].transcript = transcript
        state.notes[index].transcriptState = transcript == nil ? .failed : .ready
        persist()
    }

    private func setTranscriptState(noteID: UUID, state newState: TranscriptState) {
        guard let index = state.notes.firstIndex(where: { $0.id == noteID }) else { return }
        state.notes[index].transcriptState = newState
    }

    /// The mark settles to found. The author is never told (§6.3 — no read
    /// receipts).
    func markFound(_ note: Note) {
        guard let me = state.me, note.authorID != me.id,
              let index = state.notes.firstIndex(where: { $0.id == note.id }),
              !state.notes[index].foundBy.contains(me.id)
        else { return }
        state.notes[index].foundBy.insert(me.id)
        persist()
    }

    /// Take back your own note: the mark and the note vanish with no
    /// tombstone (S04).
    func takeBack(_ note: Note) {
        guard note.authorID == state.me?.id else { return }
        if let path = note.audioPath {
            Task { await store.removeAudio(named: [path]) }
        }
        state.notes.removeAll { $0.id == note.id }
        persist()
    }

    func editWrittenNote(_ note: Note, body: String) {
        guard note.authorID == state.me?.id, note.kind == .written,
              let index = state.notes.firstIndex(where: { $0.id == note.id })
        else { return }
        state.notes[index].body = body
        persist()
    }

    // MARK: - Highlights

    func highlights(in reading: Reading, chapter: Int) -> [Highlight] {
        state.highlights.filter { $0.readingID == reading.id && $0.range.chapter == chapter }
    }

    func highlights(in reading: Reading) -> [Highlight] {
        state.highlights
            .filter { $0.readingID == reading.id }
            .sorted { $0.range.start < $1.range.start }
    }

    /// The last ink I used — pre-selected so the common case is one tap
    /// (S06). Remembered across launches.
    var lastUsedInk: Ink { state.lastUsedInk ?? .ochre }

    /// Marking in ink. Your own highlight on exactly the same range takes
    /// the new ink rather than stacking a darker second wash.
    func addHighlight(_ range: VerseRange, ink: Ink, in reading: Reading) {
        guard let me = state.me else { return }
        if let i = state.highlights.firstIndex(where: {
            $0.readingID == reading.id && $0.authorID == me.id && $0.range == range
        }) {
            state.highlights[i].ink = ink
        } else {
            state.highlights.append(
                Highlight(readingID: reading.id, authorID: me.id, range: range, ink: ink, createdAt: Date()))
        }
        state.lastUsedInk = ink
        recordReadingActivity(reading: reading, at: range.start)
        persist()
    }

    /// You cannot remove someone else's mark (S06).
    func removeHighlight(_ highlight: Highlight) {
        guard highlight.authorID == state.me?.id else { return }
        state.highlights.removeAll { $0.id == highlight.id }
        persist()
    }

    /// My ink for a highlight right now: my membership ink when ink is
    /// identity, else free choice.
    func inkForNewHighlight(in room: Room) -> Ink? {
        if inkIsIdentity(in: room) {
            return myMembership(in: room)?.ink
        }
        return nil  // free palette; the toolbar offers all eight
    }

    /// A highlight from before the room became three keeps its color
    /// (§4.5) — the one-line explanation shows on it.
    func isFromWhenTheRoomWasTwo(_ highlight: Highlight, in room: Room) -> Bool {
        guard let since = room.inkIdentitySince, inkIsIdentity(in: room) else { return false }
        return highlight.createdAt < since
    }

    // MARK: - Cards (§4.6)

    func cards(in reading: Reading) -> [ReflectionCard] {
        state.cards.filter { $0.readingID == reading.id }.sorted { $0.chapter < $1.chapter }
    }

    /// The card at a chapter's end, if it has been minted (S03). Pure: a
    /// view body may ask.
    func card(for reading: Reading, chapter: Int) -> ReflectionCard? {
        state.cards.first { $0.readingID == reading.id && $0.chapter == chapter }
    }

    /// Whether the book carries a card at this chapter's end at all.
    func carriesCard(_ reading: Reading, chapter: Int) -> Bool {
        CardQuestions.question(bookID: reading.bookID, chapter: chapter) != nil
    }

    /// Minted the first time anyone reaches the passage end — from an
    /// appearance, never from a body (S08). A finished book mints nothing.
    func mintCardIfNeeded(for reading: Reading, chapter: Int) {
        guard !reading.isFinished, card(for: reading, chapter: chapter) == nil,
              let question = CardQuestions.question(bookID: reading.bookID, chapter: chapter)
        else { return }
        state.cards.append(ReflectionCard(readingID: reading.id, chapter: chapter, question: question))
        persist()
    }

    func myAnswer(on card: ReflectionCard) -> String? {
        guard let me = state.me else { return nil }
        return card.answers[me.id]
    }

    /// Answering keeps the card sealed until everyone has; a room of one
    /// opens on your answer — it can't wait for nobody (S08).
    func answer(_ card: ReflectionCard, text: String) {
        guard let me = state.me,
              let i = state.cards.firstIndex(where: { $0.id == card.id }),
              state.cards[i].state != .setDown
        else { return }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        state.cards[i].answers[me.id] = trimmed
        openCardIfComplete(at: i)
        persist()
    }

    /// Any member can retire a sealed card for the room. It leaves without
    /// ceremony and doesn't come back.
    func setDown(_ card: ReflectionCard) {
        guard let i = state.cards.firstIndex(where: { $0.id == card.id }),
              state.cards[i].state == .sealed
        else { return }
        state.cards[i].state = .setDown
        persist()
    }

    private func openCardIfComplete(at i: Int) {
        guard state.cards[i].state == .sealed,
              let reading = state.readings.first(where: { $0.id == state.cards[i].readingID }),
              let room = state.rooms.first(where: { $0.id == reading.roomID })
        else { return }
        let memberIDs = members(of: room).map(\.personID)
        guard !memberIDs.isEmpty, memberIDs.allSatisfy({ state.cards[i].answers[$0] != nil }) else { return }
        state.cards[i].state = .open
        state.cards[i].openedAt = Date()
    }

    /// Departures can complete a card (§6.8): the people who remain may all
    /// have answered.
    private func reconcileCards(in roomID: UUID) {
        let readingIDs = Set(state.readings.filter { $0.roomID == roomID }.map(\.id))
        for i in state.cards.indices where readingIDs.contains(state.cards[i].readingID) {
            openCardIfComplete(at: i)
        }
    }

    /// Cards that opened and this person hasn't yet seen open — the S01
    /// row "The cards are open".
    func waitingOpenCards(in room: Room) -> [ReflectionCard] {
        guard let reading = openReading(in: room) else { return [] }
        return cards(in: reading).filter { $0.state == .open && !state.seenOpenCardIDs.contains($0.id) }
    }

    func markCardSeen(_ card: ReflectionCard) {
        guard card.state == .open, !state.seenOpenCardIDs.contains(card.id) else { return }
        state.seenOpenCardIDs.insert(card.id)
        persist()
    }

    // MARK: - Settings

    var settings: AppSettings { state.settings }

    func updateSettings(_ transform: (inout AppSettings) -> Void) {
        transform(&state.settings)
        persist()
    }

    func notificationPrefs(for room: Room) -> RoomNotificationPrefs {
        state.settings.roomNotifications[room.id] ?? RoomNotificationPrefs()
    }

    func setNotificationPrefs(_ prefs: RoomNotificationPrefs, for room: Room) {
        state.settings.roomNotifications[room.id] = prefs
        persist()
    }

    /// What the translation picker offers: the bundled two always, plus
    /// the licensed editions (NKJV, NIV, NASB 1995 — §16.8, decided).
    /// Streaming needs no account: the proxy is public-read behind the
    /// publishable key, so licensed translations don't wait for sync.
    var availableTranslations: [Translation] {
        TranslationRegistry.bundled + TranslationRegistry.licensed.filter(\.isConfigured)
    }

    func setTranslation(_ translation: TranslationID) {
        state.me?.translation = translation
        persist()
        pushProfileRemote()
    }

    func updateMe(name: String) {
        state.me?.name = name
        persist()
        pushProfileRemote()
    }

    private func pushProfileRemote(portraitData: Data? = nil) {
        guard let remote, remote.isSignedIn, let me = state.me else { return }
        Task { try? await remote.push(profile: me, portraitData: portraitData) }
    }

    func markMarginHintSeen() {
        guard !state.hasSeenMarginHint else { return }
        state.hasSeenMarginHint = true
        persist()
    }

    /// Whether this person has left anything at all — the "leave your
    /// notes behind?" question is asked only of someone who has (§6.8).
    var hasLeftNotes: Bool {
        guard let me = state.me else { return false }
        return state.notes.contains { $0.authorID == me.id }
    }

    /// Account deletion (§6.8). The backend forgets the person first —
    /// the profile row cascades memberships, invites, fuel, quiet days
    /// and positions; shared rooms and what was left in them stay for the
    /// people still there. Only once that has happened does this device
    /// forget: deleting offline would otherwise wipe the phone and leave
    /// the account alive with nobody to say so. Throws when it can't reach
    /// the backend; nothing changes then.
    func deleteAccount(keepNotesBehind: Bool) async throws {
        // Notes aren't remote yet, so the keep/take answer is local-only
        // until the full sync engine; the bare auth user — an email and
        // nothing else — needs a service-role function and rides along
        // then too.
        _ = keepNotesBehind
        if let remote, remote.isSignedIn {
            try await remote.deleteAccountData()
            await remote.signOut()
        }
        state = AppState()
        portraits = [:]
        await store.removeAllFiles()
        persist()
    }

    // MARK: - The account and the room surface of sync (§6.10, S16)

    var isSignedIn: Bool { remote?.isSignedIn ?? false }
    var accountEmail: String? { remote?.email }

    func sendSignInCode(to email: String) async throws {
        guard let remote else { throw SupabaseError.notSignedIn }
        try await remote.sendCode(to: email)
    }

    /// Verifying the emailed code is account creation and sign-in both.
    /// The local person adopts the account's identity — one person, ever,
    /// even across the local-first-then-signed-in seam. A *different*
    /// account arriving on a device that already belongs to one is someone
    /// else: refused, never a re-attribution of what this person left.
    func verifySignInCode(email: String, code: String) async throws {
        guard let remote else { throw SupabaseError.notSignedIn }
        let uid = try await remote.verify(email: email, code: code)
        if let bound = state.boundAccountID, bound != uid, state.me != nil {
            await remote.signOut()
            throw SupabaseError.differentAccount
        }
        adoptRemoteIdentity(uid)
        state.boundAccountID = uid
        await reconcileOwnProfile()
        // Pull before push: a room this account left on another device is
        // removed by the merge, so the push can't quietly re-join it.
        await refreshFromRemote()
        await pushLocalGraph()
        await registerPendingInvites()
        persist()
    }

    /// The account is the elder truth: signing in on a fresh device must
    /// not upsert its just-typed defaults over the profile the room
    /// already knows. When the account has a profile, its name,
    /// translation and portrait win here; the push that follows then
    /// carries the reconciled values.
    private func reconcileOwnProfile() async {
        // try? flattens the optionals: nil is "no profile" and "couldn't
        // ask" alike, and both mean this device's values stand.
        guard let remote, let row = try? await remote.fetchOwnProfile(),
              var me = state.me
        else { return }
        me.name = row.name
        me.translation = TranslationID(rawValue: row.translation)
        state.me = me
        if me.portraitPath == nil, row.portraitPath != nil {
            fetchRemotePortrait(me.id)
        }
        persist()
    }

    func signOutRemote() async {
        await remote?.signOut()
    }

    /// Signing out of this account and continuing as someone else on this
    /// device (S16 — "signed in as someone else"): the phone forgets the
    /// person; their rooms are the account's and come back when it does.
    func signOutAndForgetThisPerson() async {
        await remote?.signOut()
        state = AppState()
        portraits = [:]
        await store.removeAllFiles()
        persist()
    }

    /// A person exists once, ever. Before sign-in their id was minted on
    /// this device; the account's id replaces it everywhere it appears.
    /// (The fuel window's person ids age out on their own within ~36 h —
    /// at worst a just-adopted fire counts its own reader twice, briefly.)
    private func adoptRemoteIdentity(_ uid: UUID) {
        guard var me = state.me, me.id != uid else { return }
        let old = me.id
        me.id = uid
        state.me = me
        if let image = portraits.removeValue(forKey: old) { portraits[uid] = image }
        for i in state.memberships.indices where state.memberships[i].personID == old {
            state.memberships[i].personID = uid
        }
        for i in state.notes.indices {
            if state.notes[i].authorID == old { state.notes[i].authorID = uid }
            if state.notes[i].foundBy.remove(old) != nil { state.notes[i].foundBy.insert(uid) }
        }
        for i in state.highlights.indices where state.highlights[i].authorID == old {
            state.highlights[i].authorID = uid
        }
        for i in state.positions.indices where state.positions[i].personID == old {
            state.positions[i].personID = uid
        }
        for i in state.quietDays.indices where state.quietDays[i].personID == old {
            state.quietDays[i].personID = uid
        }
        for i in state.invites.indices where state.invites[i].createdBy == old {
            state.invites[i].createdBy = uid
        }
        for i in state.cards.indices {
            if let answer = state.cards[i].answers.removeValue(forKey: old) {
                state.cards[i].answers[uid] = answer
            }
        }
        for roomID in state.lastReadAt.keys {
            if let stamp = state.lastReadAt[roomID]?.removeValue(forKey: old) {
                state.lastReadAt[roomID]?[uid] = stamp
            }
        }
        persist()
    }

    /// Everything this device can honestly claim on the backend: my
    /// profile and portrait, my rooms and membership, live invites, the
    /// readings and their fires, my quiet days. Idempotent upserts, so it
    /// doubles as the outbox: whatever failed to push last time lands on
    /// the next sign-in or foreground.
    func pushLocalGraph() async {
        guard let remote, remote.isSignedIn, let me = state.me else { return }
        var portraitData: Data?
        if let path = me.portraitPath {
            portraitData = try? Data(contentsOf: await store.portraitFileURL(path))
        }
        try? await remote.push(profile: me, portraitData: portraitData)
        for room in liveRooms {
            // The name rides only when it is this device's edit — a whole
            // stale row would clobber a partner's rename (§13).
            try? await remote.push(room: room, includeName: pendingRenamePushes.contains(room.id))
            if let mine = myMembership(in: room) {
                try? await remote.push(membership: mine)
            }
            for reading in state.readings where reading.roomID == room.id {
                try? await remote.push(reading: reading)
            }
            for day in quietDays(for: room) where day.personID == me.id {
                try? await remote.push(quietDay: day)
            }
        }
        await registerPendingInvites()
    }

    /// Pull every room I'm in and fold it into local state. Called on
    /// launch, on foreground, and after joining. Departures and the local
    /// graph go first, so the pull can't revert them.
    func refreshFromRemote() async {
        guard let remote, remote.isSignedIn else { return }
        await replayDepartures()
        // An unpushed rename goes first, so the pull can't revert it.
        for roomID in Array(pendingRenamePushes) {
            if let room = state.rooms.first(where: { $0.id == roomID }),
               (try? await remote.push(room: room, includeName: true)) != nil {
                pendingRenamePushes.remove(roomID)
            }
        }
        guard let graph = try? await remote.pullRooms() else { return }
        merge(graph)
    }

    /// The whole room surface, on foreground: what didn't push, pushes;
    /// then the pull.
    func foregroundSync() async {
        guard let remote, remote.isSignedIn else { return }
        await pushLocalGraph()
        await refreshFromRemote()
    }

    /// Accepting an invite (S16): join on the backend and pull the room.
    /// The caller decides whether to land in it — a join whose screen was
    /// set down mid-flight still joins, but must not switch the room
    /// underneath whatever the person is doing now. If the pull fails
    /// after the join, the room still exists here by id, so arriving
    /// never mints a stray empty room instead.
    func joinRoom(inviteToken: UUID) async throws -> UUID {
        guard let remote, remote.isSignedIn, let me = state.me else { throw SupabaseError.notSignedIn }
        let roomID = try await remote.acceptInvite(token: inviteToken)
        // A departure from this same room still waiting to replay would
        // delete the seat accept_invite just gave back.
        state.pendingDepartures.removeAll { $0.roomID == roomID }
        await refreshFromRemote()
        if !state.rooms.contains(where: { $0.id == roomID }) {
            state.rooms.append(Room(id: roomID, createdAt: Date()))
        }
        if let i = state.rooms.firstIndex(where: { $0.id == roomID }) {
            state.rooms[i].leftAt = nil
        }
        if let i = state.memberships.firstIndex(where: { $0.roomID == roomID && $0.personID == me.id }) {
            state.memberships[i].leftAt = nil
        } else {
            state.memberships.append(Membership(roomID: roomID, personID: me.id, joinedAt: Date()))
        }
        noteInkTransition(in: roomID)
        persist()
        return roomID
    }

    func handleInviteURL(_ url: URL) {
        guard let token = Self.inviteToken(from: url) else { return }
        pendingInvite = PendingInvite(token: token)
    }

    /// https://readribbon.app/i/<token> or ribbon://i/<token>.
    static func inviteToken(from url: URL) -> UUID? {
        let parts = url.pathComponents.filter { $0 != "/" }
        let host = url.host()?.lowercased()
        guard host == "readribbon.app" || url.scheme?.lowercased() == "ribbon" else { return nil }
        guard parts.first == "i" || host == "i", let last = parts.last else { return nil }
        return UUID(uuidString: last)
    }

    /// The onboarding paste field takes whatever they have — the link, or
    /// just the code out of it.
    static func inviteToken(fromPasted text: String) -> UUID? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URL(string: trimmed), let token = inviteToken(from: url) {
            return token
        }
        let pattern = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
        if let range = trimmed.range(of: pattern, options: .regularExpression) {
            return UUID(uuidString: String(trimmed[range]))
        }
        return nil
    }

    private func merge(_ graph: RoomGraph) {
        guard let me = state.me else { return }
        let departedLocally = Set(state.rooms.filter(\.isDeparted).map(\.id))

        for row in graph.rooms where !departedLocally.contains(row.id) {
            if let i = state.rooms.firstIndex(where: { $0.id == row.id }) {
                // Remote wins on the multi-author name — except over a
                // local rename that hasn't landed there yet.
                if !pendingRenamePushes.contains(row.id) {
                    state.rooms[i].name = row.name
                }
                state.rooms[i].isPaused = row.isPaused
            } else {
                state.rooms.append(
                    Room(id: row.id, name: row.name, createdAt: row.createdAt, isPaused: row.isPaused))
            }
        }

        for row in graph.memberships where !departedLocally.contains(row.roomId) {
            let ink = row.ink.flatMap(Ink.init(rawValue:))
            if let i = state.memberships.firstIndex(where: {
                $0.roomID == row.roomId && $0.personID == row.personId
            }) {
                // My ink is authored here; everyone else's is authored
                // there.
                if row.personId != me.id { state.memberships[i].ink = ink }
                state.memberships[i].joinedAt = row.joinedAt
                state.memberships[i].leftAt = nil
            } else {
                state.memberships.append(Membership(
                    id: row.id, roomID: row.roomId, personID: row.personId,
                    ink: ink, joinedAt: row.joinedAt))
            }
        }
        // Departures propagate: a membership the backend no longer has is
        // marked departed here — kept, so an ember record still shows who
        // read the book (S11). Mine stays within a pulled room — leaving
        // already marked it locally, and a pull racing my own join must
        // not undo the join.
        let pulledRooms = Set(graph.rooms.map(\.id))
        var roomsWithDepartures: Set<UUID> = []
        for i in state.memberships.indices {
            let membership = state.memberships[i]
            guard membership.personID != me.id, membership.isActive,
                  pulledRooms.contains(membership.roomID),
                  !graph.memberships.contains(where: {
                      $0.roomId == membership.roomID && $0.personId == membership.personID
                  })
            else { continue }
            state.memberships[i].leftAt = Date()
            roomsWithDepartures.insert(membership.roomID)
        }
        // My own departures, made on another device: a room the backend
        // shared with other people that no longer lists me doesn't come
        // back in the pull at all. Kept as a departed room, for its shelf.
        for i in state.rooms.indices {
            let room = state.rooms[i]
            guard !room.isDeparted, !pulledRooms.contains(room.id),
                  state.memberships.contains(where: { $0.roomID == room.id && $0.personID != me.id })
            else { continue }
            state.rooms[i].leftAt = Date()
            if let m = state.memberships.firstIndex(where: { $0.roomID == room.id && $0.personID == me.id }) {
                state.memberships[m].leftAt = Date()
            }
            if state.currentRoomID == room.id {
                state.currentRoomID = liveRooms.first?.id
            }
        }

        for row in graph.profiles where row.id != me.id {
            let translation = TranslationID(rawValue: row.translation)
            var person = state.people[row.id]
                ?? Person(id: row.id, name: row.name, translation: translation)
            person.name = row.name
            person.translation = translation
            state.people[row.id] = person
            if portraits[row.id] == nil, row.portraitPath != nil {
                fetchRemotePortrait(row.id)
            }
        }

        for row in graph.quietDays where !departedLocally.contains(row.roomId) {
            guard !state.quietDays.contains(where: {
                $0.roomID == row.roomId && $0.personID == row.personId && $0.localDate == row.localDate
            }) else { continue }
            state.quietDays.append(QuietDay(
                id: row.id, roomID: row.roomId, personID: row.personId,
                localDate: row.localDate, timeZoneID: row.timeZone, markedAt: row.markedAt))
        }

        let fires = Dictionary(uniqueKeysWithValues: graph.fires.map { ($0.readingId, $0) })
        let fuelByReading = Dictionary(grouping: graph.fuelEvents, by: \.readingId)
        for row in graph.readings where !departedLocally.contains(row.roomId) {
            let events = (fuelByReading[row.id] ?? [])
                .map { FuelEvent(personID: $0.personId, at: $0.at) }
            for event in events {
                stampLastRead(personID: event.personID, in: row.roomId, at: event.at)
            }
            if let i = state.readings.firstIndex(where: { $0.id == row.id }) {
                if state.readings[i].finishedAt == nil {
                    state.readings[i].finishedAt = row.finishedAt
                }
                // The set-aside moment rides the reading row (§03, §6.6):
                // the backend's word wins, so a resume on one phone clears
                // it on the other. The local graph is pushed before every
                // pull, so this device's own change is already there.
                if state.readings[i].finishedAt == nil {
                    state.readings[i].setAsideAt = row.setAsideAt
                }
                state.readings[i].handiwork = Self.mergedHandiwork(
                    local: state.readings[i].handiwork, remote: fires[row.id], events: events)
            } else {
                let scale = FireScale(rawValue: row.scale) ?? .medium
                var handiwork = Handiwork(scale: scale)
                if let fire = fires[row.id] {
                    handiwork = Handiwork(
                        scale: scale, coalDepth: fire.coalDepth,
                        lastFuelAt: fire.lastFuelAt, restartAt: fire.restartAt,
                        stateAtLastFuel: FireState(rawValue: fire.stateAtLastFuel) ?? .catching,
                        recentFuel: Self.pruned(events))
                }
                state.readings.append(Reading(
                    id: row.id, roomID: row.roomId, bookID: row.bookId,
                    startedAt: row.startedAt, finishedAt: row.finishedAt,
                    setAsideAt: row.setAsideAt, handiwork: handiwork))
            }
        }
        // One open reading per room (§03): after a race, the latest stays
        // open and the other is set aside, on both phones.
        for room in liveRooms {
            let open = state.readings.filter { $0.roomID == room.id && $0.isOpen }
                .sorted { $0.startedAt > $1.startedAt }
            for reading in open.dropFirst() {
                if let i = state.readings.firstIndex(where: { $0.id == reading.id }) {
                    state.readings[i].setAsideAt = Date()
                }
            }
            noteInkTransition(in: room.id)
        }
        for roomID in roomsWithDepartures { reconcileCards(in: roomID) }

        persist()
    }

    /// Two devices fed the same fire: keep the later feeding's read of
    /// the state, the deeper bed, and the union of the window's fuel — and
    /// if the union shows two people within the window, the fire is
    /// steady now, not a feeding late (§4.1).
    private static func mergedHandiwork(
        local: Handiwork, remote row: RemoteSync.FireRow?, events: [FuelEvent]
    ) -> Handiwork {
        var union = Set(local.recentFuel.map(Self.roundedToSecond))
        union.formUnion(events.map(Self.roundedToSecond))
        let recent = pruned(Array(union))
        let localLast = local.lastFuelAt ?? .distantPast
        let remoteLast = row?.lastFuelAt ?? .distantPast
        let laterIsRemote = row != nil && remoteLast > localLast
        let last = max(localLast, remoteLast)
        var stateAtLastFuel = laterIsRemote
            ? (FireState(rawValue: row?.stateAtLastFuel ?? "") ?? .catching)
            : local.stateAtLastFuel
        if stateAtLastFuel == .burning, Set(recent.map(\.personID)).count >= 2 {
            stateAtLastFuel = .steady
        }
        return Handiwork(
            scale: local.scale,
            coalDepth: max(local.coalDepth, row?.coalDepth ?? 0),
            lastFuelAt: last == .distantPast ? nil : last,
            restartAt: laterIsRemote ? row?.restartAt : local.restartAt,
            stateAtLastFuel: stateAtLastFuel,
            recentFuel: recent)
    }

    /// A timestamp round-trips through the backend without its fraction of
    /// a second; the same feeding must not count twice.
    private static func roundedToSecond(_ event: FuelEvent) -> FuelEvent {
        FuelEvent(personID: event.personID, at: Date(timeIntervalSince1970: event.at.timeIntervalSince1970.rounded()))
    }

    private static func pruned(_ events: [FuelEvent]) -> [FuelEvent] {
        let cutoff = Date().addingTimeInterval(-FireTuning.standard.fuelWindowHours * 3600)
        return events.filter { $0.at >= cutoff }.sorted { $0.at < $1.at }
    }

    private func fetchRemotePortrait(_ personID: UUID) {
        guard let remote else { return }
        Task {
            guard let data = await remote.fetchPortrait(personID: personID) else { return }
            if let path = try? await store.writePortrait(data, personID: personID) {
                if state.me?.id == personID {
                    state.me?.portraitPath = path
                } else {
                    state.people[personID]?.portraitPath = path
                }
                if let image = UIImage(data: data) { portraits[personID] = image }
                persist()
            }
        }
    }

    // MARK: - Presence (§4.2) — the pipeline, ready for the socket

    /// The room whose book is open on this device, if any.
    private var presentRoomID: UUID?
    private var presenceTask: Task<Void, Never>?
    private var backgroundGrace: Task<Void, Never>?

    /// The book opened: join the room's channel (unless reading quietly)
    /// and start listening. With the local service nothing ever arrives,
    /// and absence is the honest rendering of absence.
    func startPresence(in room: Room) async {
        presentRoomID = room.id
        if presenceTask == nil {
            presenceTask = Task { [weak self] in
                guard let self else { return }
                for await event in presence.events {
                    self.handle(event)
                }
            }
        }
        if !readingQuietly, let me = state.me, !room.isPaused {
            await presence.join(roomID: room.id, person: me)
        }
    }

    /// The book closed: leave, and forget everyone — and any follow.
    func stopPresence() async {
        presentRoomID = nil
        await presence.leave()
        presentPeople = []
        followingPersonID = nil
        followTarget = nil
    }

    func updatePresence(position: VerseAddress?, scrollFraction: Double, isIdle: Bool) async {
        guard presentRoomID != nil, !readingQuietly else { return }
        await presence.update(position: position, scrollFraction: scrollFraction, isIdle: isIdle)
    }

    private func presenceVisibilityChanged() async {
        guard let roomID = presentRoomID, let me = state.me else { return }
        if readingQuietly {
            await presence.leave()
        } else {
            await presence.join(roomID: roomID, person: me)
        }
    }

    /// Backgrounding removes you after a short grace, so a glance at a
    /// text message doesn't read as leaving (§4.2).
    func scenePhaseChanged(to phase: ScenePhase) {
        switch phase {
        case .background:
            backgroundGrace?.cancel()
            backgroundGrace = Task { [weak self] in
                try? await Task.sleep(for: .seconds(20))
                guard !Task.isCancelled, let self else { return }
                await self.presence.leave()
                self.presentPeople = []
            }
        case .active:
            backgroundGrace?.cancel()
            backgroundGrace = nil
            if let roomID = presentRoomID, let me = state.me, !readingQuietly {
                Task { await presence.join(roomID: roomID, person: me) }
            }
        default:
            break
        }
    }

    private func handle(_ event: PresenceEvent) {
        switch event {
        case .roster(let people):
            let before = Set(presentPeople.map(\.id))
            let arrivals = people.filter { $0.id != state.me?.id }
            // Someone arrives: one soft transient, low intensity (§9.3).
            if arrivals.contains(where: { !before.contains($0.id) }) {
                Haptics.shared.someoneArrives()
            }
            presentPeople = arrivals
            if let following = followingPersonID {
                if let person = people.first(where: { $0.id == following }) {
                    if let position = person.position, position != followTarget {
                        followTarget = position
                    }
                } else {
                    // They left; you have your own scroll back.
                    followingPersonID = nil
                    followTarget = nil
                }
            }
        case .thinkingOfYou(let name):
            Haptics.shared.tapOnTheShoulder()
            thinkingOfYouFrom = name
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(4))
                self?.thinkingOfYouFrom = nil
            }
        }
    }

    func follow(_ person: PresentPerson) {
        followingPersonID = person.id
        followTarget = person.position
    }

    func stopFollowing() {
        followingPersonID = nil
        followTarget = nil
    }

    // MARK: - Portraits

    private func loadPortraits() async {
        var all = Array(state.people.values)
        if let me = state.me { all.append(me) }
        for person in all {
            guard let path = person.portraitPath else { continue }
            let url = await store.portraitFileURL(path)
            if let data = try? Data(contentsOf: url), let image = UIImage(data: data) {
                portraits[person.id] = image
            }
        }
    }

    func setPortrait(_ data: Data) async {
        guard var me = state.me else { return }
        if let path = try? await store.writePortrait(data, personID: me.id) {
            me.portraitPath = path
            state.me = me
            if let image = UIImage(data: data) { portraits[me.id] = image }
            persist()
            pushProfileRemote(portraitData: data)
        }
    }
}

func firstName(_ name: String) -> String {
    name.split(separator: " ").first.map(String.init) ?? name
}

// MARK: - The model, from any view

/// The model reaches views through the environment, set once at the root.
/// A view SwiftUI hosts outside that subtree (a system-presented dialog,
/// an accessibility element, a transition snapshot — the Mac's
/// Designed-for-iPad host does this where iOS doesn't) would otherwise
/// find nothing and stop the app; the fallback is the loaded model, which
/// exists before any view does.
private struct AppModelKey: EnvironmentKey {
    static let defaultValue: AppModel? = nil
}

extension EnvironmentValues {
    var appModel: AppModel {
        get {
            if let model = self[AppModelKey.self] { return model }
            guard let model = AppModel.current else {
                fatalError("Ribbon: a view asked for the model before it was loaded.")
            }
            return model
        }
        set { self[AppModelKey.self] = newValue }
    }
}
