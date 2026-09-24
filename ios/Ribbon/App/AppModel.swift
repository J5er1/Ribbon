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
    var id: UUID { token }
}

@MainActor
@Observable
final class AppModel {
    private(set) var state: AppState
    let store: LocalStore
    let scripture = ScriptureStore.shared
    let presence: PresenceService
    /// The backend, when configured (SupabaseConfig.remoteEnabled). Nil
    /// means fully local — every remote call below is best-effort and
    /// nothing blocks reading.
    private(set) var remote: RemoteSync?
    /// Set by an opened invite link; RootView and onboarding watch it.
    var pendingInvite: PendingInvite?
    /// Where a tapped notification wants to land (S19). RootView takes it
    /// and clears it.
    var pendingDestination: Destination?
    /// The room on screen right now, or nil while the app is away — the
    /// third gate on a notification: a phone in your hand is not told what
    /// it is showing you.
    var visibleRoomID: UUID?

    /// Whether the phone has a way out. Read by the fire, which dims.
    private let connectivity = Connectivity()
    var isOnline: Bool { connectivity.online }

    /// Who is in the book right now (empty means the form is absent).
    private(set) var presentPeople: [PresentPerson] = []
    /// Per-session only (§4.2): read quietly is never remembered across
    /// launches — slipping in invisibly is a choice made each time.
    var readingQuietly = false
    /// The person being followed, if any.
    var followingPersonID: UUID?

    /// Portraits cache (person id → image).
    private var portraits: [UUID: UIImage] = [:]

    init(state: AppState, store: LocalStore, presence: PresenceService) {
        self.state = state
        self.store = store
        self.presence = presence
    }

    static func load() async -> AppModel {
        await load(forBackgroundPull: false)
    }

    /// `forBackgroundPull` is a wake while the app is away (S19): the store
    /// and the account, and nothing that needs a screen — no faces, no
    /// channel, no listeners.
    static func load(forBackgroundPull: Bool) async -> AppModel {
        let store = LocalStore()
        let state = await store.load()
        // The backend comes first: the room's live channel authenticates with
        // the account's own token, so it cannot be built before there is an
        // account to ask.
        let remote = SupabaseConfig.remoteEnabled ? await RemoteSync.restore() : nil
        let presence: PresenceService
        if let remote {
            presence = RoomChannel(accessToken: { [weak remote] in
                guard let remote else { return nil }
                return await remote.realtimeToken()
            })
        } else {
            presence = LocalPresenceService()
        }
        let model = AppModel(state: state, store: store, presence: presence)
        model.remote = remote
        if forBackgroundPull {
            // What the merge may post depends on this; stale, it posts
            // nothing and the watermark moves past the rows anyway.
            await Notifications.refreshAllowed()
            return model
        }
        await model.loadPortraits()
        model.startListeningToPresence()
        // Started, not waited for. Opening the room's socket needs the
        // account's token, and `realtimeToken()` refreshes an expired one
        // over the network — a round trip with no bound the launch can
        // afford. Awaited here it gated the whole of `load()`, and the app
        // shows nothing but the launch mark until `load()` returns, so a
        // slow or unreachable backend held the first frame until the system
        // gave up on the launch and killed the app without a word.
        //
        // Nothing here needs it: the listener above is already attached, so
        // the roster arrives whenever the socket does, and the room renders
        // from local state regardless (§6.10). Same shape as the auth
        // question below.
        Task { await model.openRoomChannel() }
        await Notifications.refreshAllowed()
        NotificationRouter.shared.deliver = { [weak model] destination in
            model?.pendingDestination = destination
        }
        // The token arrives from APNs whenever it likes; each arrival is
        // another registration, with everything else this phone holds.
        Push.tokenChanged = { [weak model] in
            Task { await model?.registerForPush() }
        }
        Task { await remote?.learnWhatAuthOffers() }
        if remote?.isSignedIn == true { RoomWatch.start() }
        Task { await model.registerForPush() }
        return model
    }

    private func startListeningToPresence() {
        Task { [weak self] in
            guard let self else { return }
            for await event in self.presence.events {
                switch event {
                case .roster(let people):
                    self.someoneOpenedTheBook(people)
                    self.presentPeople = people
                case .thinkingOfYou(let fromName):
                    self.thinkingOfYouArrived(fromName)
                case .roomChanged(let roomID):
                    self.roomChangedRemotely(roomID)
                }
            }
        }
    }

    // MARK: - What presence tells the phone (S19)

    /// Who was in the book at the last roster, so an arrival is a
    /// difference and not a roster.
    private var wasReading: Set<UUID> = []
    /// The first roster after a connect or a room change is a baseline,
    /// not an arrival: the people in it were already reading before this
    /// phone was listening.
    private var haveARoster = false

    /// "When they open the book" (§10.3): from the roster, and only while
    /// the room's channel is up — which is to say, only while this phone is
    /// in the room. The switch is off by default, per room, because it is
    /// the killer feature for couples and the creepiest one for a study.
    private func someoneOpenedTheBook(_ people: [PresentPerson]) {
        let now = Set(people.map(\.id))
        let arrived = now.subtracting(wasReading)
        wasReading = now
        guard haveARoster else {
            haveARoster = true
            return
        }
        guard !arrived.isEmpty, let room = currentRoom, let reading = openReading(in: room),
              let book = Bible.book(id: reading.bookID)?.name
        else { return }
        let me = state.me?.id
        guard let newcomer = arrived.first(where: { $0 != me }), let name = person(newcomer)?.name else { return }
        // While the server is delivering, it says this — to this phone and to
        // every other one — and saying it here too would be saying it twice.
        guard !Push.delivering, Notifications.shouldPost(
            kind: .inTheBook, roomID: room.id, prefs: notificationPrefs(for: room),
            settings: state.settings, visibleRoomID: visibleRoomID)
        else { return }
        Notifications.post(
            id: Notifications.id(roomID: room.id, kind: .inTheBook), kind: .inTheBook,
            line: Copy.notifReading(firstName(name), book), to: .room(roomID: room.id))
    }

    /// Thinking of you (§4.3): a touch first, and only then a name. In
    /// quiet hours the touch still arrives — silently — because it is a
    /// touch, not a sentence (S19); the name does not.
    private func thinkingOfYouArrived(_ fromName: String) {
        guard let room = currentRoom, notificationPrefs(for: room).thinkingOfYou else { return }
        Haptics.shared.tapOnTheShoulder()
        // The touch is the socket's to give; the name, while the server is
        // delivering, is the push's.
        guard !state.settings.isQuiet(), !Push.delivering else { return }
        Notifications.post(
            id: Notifications.id(roomID: room.id, kind: .thinkingOfYou), kind: .thinkingOfYou,
            line: Copy.notifThinkingOfYou(firstName(fromName)), to: .room(roomID: room.id))
    }

    // MARK: - The room's live line (§4.2)

    /// A pull the socket asked for, coalesced. Several people leaving notes
    /// at once is one catch-up, not five; and the short wait lets a burst
    /// (a join, then the joiner's profile, then their membership) land as
    /// one arrival rather than three half-built ones.
    private var catchUpTask: Task<Void, Never>?

    private func roomChangedRemotely(_ roomID: UUID) {
        guard roomID == currentRoom?.id else { return }
        catchUpTask?.cancel()
        catchUpTask = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(600))
            guard !Task.isCancelled, let self else { return }
            self.catchUpTask = nil
            await self.refreshFromRemote()
        }
    }

    /// Open — or move — the room's channel. Safe to call whenever the room,
    /// the person or the account changes; it is a no-op when the channel is
    /// already where it should be.
    func openRoomChannel() async {
        guard let room = currentRoom, let me = state.me, isSignedIn else {
            await presence.disconnect()
            return
        }
        await presence.connect(roomID: room.id, person: me)
    }

    /// The app going away. The socket goes with it: a phone in a pocket is
    /// not present, and saying otherwise is the one lie presence must never
    /// tell (§4.2).
    func closeRoomChannel() async {
        catchUpTask?.cancel()
        catchUpTask = nil
        // The next roster is a baseline again.
        wasReading = []
        haveARoster = false
        await presence.disconnect()
    }

    /// Something this device changed that the room renders from. The other
    /// phones hear about it now rather than at their next foreground.
    ///
    /// Every remote write the *room* renders from goes through here, so a
    /// new kind of content cannot quietly forget to be live. Positions and
    /// note-founds deliberately do not: a position is already carried by
    /// presence, several times a minute, and who found a note is the one
    /// thing the room is never told (§6.3).
    private func pushing(_ work: @escaping @MainActor () async -> Void) {
        Task { [weak self] in
            await work()
            await self?.presence.announceChange()
        }
    }

    private func persist() {
        let snapshot = state
        Task.detached(priority: .utility) { [store] in
            await store.save(snapshot)
        }
    }

    // MARK: - Me, rooms, membership

    var me: Person? { state.me }
    var isOnboarded: Bool { state.me != nil && currentRoom != nil }

    var currentRoom: Room? {
        state.rooms.first { $0.id == state.currentRoomID } ?? state.rooms.first
    }

    func person(_ id: UUID) -> Person? {
        id == state.me?.id ? state.me : state.people[id]
    }

    func portrait(_ id: UUID) -> UIImage? { portraits[id] }

    func room(_ id: UUID) -> Room? {
        state.rooms.first { $0.id == id }
    }

    func room(of reading: Reading) -> Room? {
        room(reading.roomID)
    }

    func members(of room: Room) -> [Membership] {
        state.memberships
            .filter { $0.roomID == room.id }
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
    /// freely; at three or more, ink is identity.
    func inkIsIdentity(in room: Room) -> Bool {
        members(of: room).count >= 3
    }

    /// The display name of a room: its own, or the members' first names.
    func displayName(of room: Room) -> String {
        if let name = room.name, !name.isEmpty { return name }
        let names = members(of: room).compactMap { person($0.personID)?.name.split(separator: " ").first }
        if names.isEmpty { return Copy.yourRoom }
        return names.joined(separator: " & ")
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
        persist()
        if isSignedIn {
            // Reinstall: the account's rooms and profile come back —
            // before any fresh room is minted, so an account that already
            // has rooms doesn't gain an empty stray one.
            await reconcileOwnProfile()
            await refreshFromRemote()
            await pushLocalGraph()
        }
        if startRoom, currentRoom == nil {
            createRoom(named: nil)
        }
        persist()
    }

    @discardableResult
    func createRoom(named name: String?) -> Room {
        guard let me = state.me else { fatalError("room before person") }
        let room = Room(name: name, createdAt: Date(), translation: me.translation)
        state.rooms.append(room)
        state.memberships.append(Membership(roomID: room.id, personID: me.id, joinedAt: Date()))
        state.currentRoomID = room.id
        persist()
        return room
    }

    func switchRoom(to roomID: UUID) {
        state.currentRoomID = roomID
        followingPersonID = nil
        presentPeople = []
        wasReading = []
        haveARoster = false
        if visibleRoomID != nil { visibleRoomID = roomID }
        persist()
        Task { [weak self] in await self?.openRoomChannel() }
    }

    /// The room reads one version (ledger A42). Changing it moves the open
    /// reading with it; a finished one keeps the version it was read in.
    func setRoomTranslation(_ translation: TranslationID, in room: Room) {
        guard room.translation != translation,
              let i = state.rooms.firstIndex(where: { $0.id == room.id })
        else { return }
        state.rooms[i].translation = translation
        for j in state.readings.indices where state.readings[j].roomID == room.id && !state.readings[j].isFinished {
            state.readings[j].translation = translation
        }
        persist()
        if let remote, remote.isSignedIn {
            pendingRoomPushes.insert(room.id)
            pushing { [weak self] in
                guard let self, let current = self.state.rooms.first(where: { $0.id == room.id }) else { return }
                if (try? await remote.push(room: current)) != nil {
                    self.pendingRoomPushes.remove(room.id)
                }
            }
            for reading in state.readings where reading.roomID == room.id && !reading.isFinished {
                pushReadingRemote(reading)
            }
        }
    }

    /// The words on the page: the reading's version, else the room's.
    func words(room: Room?, reading: Reading?) -> TranslationID {
        reading?.translation ?? room?.translation ?? .bsb
    }

    /// Pushes everything the backend needs for an invite link to resolve:
    /// the creator's profile, the room, their membership, and the invite itself.
    func pushInvite(_ invite: Invite, for room: Room) async throws {
        guard let remote, remote.isSignedIn, let me = state.me else { return }
        var portraitData: Data?
        if let path = me.portraitPath {
            portraitData = try? Data(contentsOf: await store.portraitFileURL(path))
        }
        try await remote.push(profile: me, portraitData: portraitData)
        try await remote.push(room: room)
        if let membership = myMembership(in: room) {
            try await remote.push(membership: membership)
        }
        // Only ever my own. `invites_update` is the creator's, so pushing a
        // link somebody else minted is a 403 — and pointless besides: it is
        // here because the pull brought it back from the row it already has.
        guard invite.createdBy == me.id else { return }
        try await remote.push(invite: invite)
    }

    /// The push, and the memory of whether it landed (A37): an invite the
    /// backend has not acknowledged is kept out of merge's prune, across
    /// launches, because the link may already be in somebody's thread.
    private func pushInviteIfNeeded(_ invite: Invite, for room: Room) async {
        do {
            try await pushInvite(invite, for: room)
            state.invitesNotYetPushed.remove(invite.id)
            persist()
        } catch {
            print("[AppModel] pushInvite failed for room \(room.id): \(error)")
        }
    }

    /// The share sheet was opened on this invite: it has left the phone.
    /// Minting is not sending, and "The invite is still out" is about a
    /// link that was handed out (S15).
    func inviteWasHandedOut(_ invite: Invite) {
        guard !state.invitesHandedOut.contains(invite.id) else { return }
        state.invitesHandedOut.insert(invite.id)
        persist()
    }

    /// A live invite somebody could still arrive on: one that has not
    /// expired, and that either somebody else minted or this phone actually
    /// sent.
    func hasLiveInvite(_ room: Room, now: Date = Date()) -> Bool {
        let me = state.me?.id
        return state.invites.contains { invite in
            invite.roomID == room.id && invite.expiresAt > now
                && (invite.createdBy != me || state.invitesHandedOut.contains(invite.id))
        }
    }

    /// Whether the hearth should keep a seat open (ledger A48): a room of
    /// one always expects somebody; a fuller room only while a link is out.
    func somebodyIsExpected(_ room: Room, now: Date = Date()) -> Bool {
        if room.isPaused || isFull(room) { return false }
        if members(of: room).count <= 1 { return true }
        return hasLiveInvite(room, now: now)
    }

    @discardableResult
    func createInvite(for room: Room) -> Invite {
        guard let me = state.me else { fatalError("invite before person") }
        // Reuse a live invite rather than minting link after link — and
        // since the pull now brings a room's invites back, "live" includes
        // the one another member already sent out. A room has one link, not
        // one per phone. Mine first, so the common case never needs anyone
        // else's row.
        let live = state.invites.filter { $0.roomID == room.id && $0.expiresAt > Date() }
        let invite: Invite
        if let existing = live.first(where: { $0.createdBy == me.id }) ?? live.first {
            invite = existing
        } else {
            invite = Invite(roomID: room.id, createdBy: me.id, createdAt: Date())
            state.invites.append(invite)
            persist()
        }
        // The link only works once the backend knows it — push it (and the
        // room, in case this room predates sign-in) whenever it's handed
        // out.
        if let remote, remote.isSignedIn {
            state.invitesNotYetPushed.insert(invite.id)
            persist()
            Task { [weak self] in await self?.pushInviteIfNeeded(invite, for: room) }
        }
        return invite
    }

    func isFull(_ room: Room) -> Bool {
        members(of: room).count >= Room.capacity
    }

    // What this phone said that the backend has not heard yet (ledger A36,
    // A40). Each set lives in `AppState` and is persisted on every change,
    // replayed at the top of `refreshFromRemote`, and read by `merge` so a
    // pull cannot undo what is still on its way. Rooms whose rename or
    // version change hasn't landed: merge() must not let a stale pull
    // revert an edit that was never pushed.
    private var pendingRoomPushes: Set<UUID> {
        get { state.pendingRoomPushes }
        set { state.pendingRoomPushes = newValue; persist() }
    }
    private var pendingNoteDeletes: [UUID: PendingNoteDelete] {
        get { state.pendingNoteDeletes }
        set { state.pendingNoteDeletes = newValue; persist() }
    }
    private var pendingNotePushes: Set<UUID> {
        get { state.pendingNotePushes }
        set { state.pendingNotePushes = newValue; persist() }
    }
    private var pendingHighlightDeletes: Set<UUID> {
        get { state.pendingHighlightDeletes }
        set { state.pendingHighlightDeletes = newValue; persist() }
    }
    /// Writes that carry their own row and nothing merge could revert: a
    /// quiet day, a highlight, a card answer, the ribbon. Keyed, so saying
    /// the same thing twice replaces rather than repeats.
    private var unsaid: [UUID: PendingWrite] {
        get { state.unsaid }
        set { state.unsaid = newValue; persist() }
    }

    /// Highlights still on their way up: the prune must not take them.
    private var pendingHighlightPushes: Set<UUID> {
        Set(state.unsaid.values.compactMap { write in
            if case .highlight(let id) = write { return id }
            return nil
        })
    }

    private func sayItAgainIfNeeded(_ key: UUID, _ write: PendingWrite) {
        guard let remote, remote.isSignedIn else { return }
        unsaid[key] = write
        pushing { [weak self] in
            guard let self else { return }
            if (try? await self.perform(write, on: remote)) != nil {
                self.unsaid.removeValue(forKey: key)
            }
        }
    }

    /// One unsaid write, said. The row is whatever state holds now; a row
    /// that is gone has nothing left to say and counts as said.
    private func perform(_ write: PendingWrite, on remote: RemoteSync) async throws {
        switch write {
        case .quietDay(let id):
            guard let day = state.quietDays.first(where: { $0.id == id }) else { return }
            try await remote.push(quietDay: day)
        case .highlight(let id):
            guard let highlight = state.highlights.first(where: { $0.id == id }) else { return }
            try await remote.push(highlight: highlight)
        case .cardAnswer(let cardID):
            guard let card = state.cards.first(where: { $0.id == cardID }),
                  let me = state.me, let answer = card.answers[me.id]
            else { return }
            try await remote.push(cardAnswer: (cardID: card.id, personID: me.id, body: answer))
            try await remote.push(card: card)
        case .ribbon(let readingID):
            guard let ribbon = state.ribbons.first(where: { $0.readingID == readingID }) else { return }
            try await remote.push(ribbon: ribbon)
        }
    }

    /// Naming a room after the fact (S15's naming half, reachable later).
    func renameRoom(_ room: Room, to name: String?) {
        guard let i = state.rooms.firstIndex(where: { $0.id == room.id }) else { return }
        let trimmed = name?.trimmingCharacters(in: .whitespaces)
        state.rooms[i].name = (trimmed?.isEmpty ?? true) ? nil : trimmed
        persist()
        if let remote, remote.isSignedIn {
            let updated = state.rooms[i]
            pendingRoomPushes.insert(updated.id)
            pushing { [weak self] in
                if (try? await remote.push(room: updated)) != nil {
                    self?.pendingRoomPushes.remove(updated.id)
                }
            }
        }
    }

    /// Leaving (§6.8): one confirmation, plainly worded, no guilt. Notes
    /// default to staying — they were left for the other person.
    func leaveRoom(_ room: Room, keepNotesBehind: Bool) {
        guard let me = state.me else { return }
        let readingIDs = Set(state.readings.filter { $0.roomID == room.id }.map(\.id))
        let mine = keepNotesBehind ? [] : state.notes.filter { readingIDs.contains($0.readingID) && $0.authorID == me.id }
        if !keepNotesBehind {
            state.notes.removeAll { readingIDs.contains($0.readingID) && $0.authorID == me.id }
        }
        // Highlights stay, always — a mark on a shared page, not a
        // possession.
        state.memberships.removeAll { $0.roomID == room.id && $0.personID == me.id }
        state.rooms.removeAll { $0.id == room.id }  // local copy of a departed room
        state.currentRoomID = state.rooms.first?.id
        persist()
        if let remote, remote.isSignedIn {
            let roomID = room.id
            let personID = me.id
            for note in mine {
                pendingNoteDeletes[note.id] = PendingNoteDelete(readingID: note.readingID, voice: note.kind == .voice)
            }
            Task { [weak self] in
                // Taking the notes back has to happen while the membership
                // still exists: the notes' own policy is the member's.
                for note in mine {
                    if (try? await remote.deleteNote(id: note.id, readingID: note.readingID, voice: note.kind == .voice)) != nil {
                        self?.pendingNoteDeletes.removeValue(forKey: note.id)
                    }
                }
                // The nudge goes first, and it has to: once the membership
                // row is gone the channel's own policy refuses this device,
                // and the room would hear nothing at all. The others pull a
                // beat later, by which time the delete has landed — and
                // their next foreground is the backstop if it hasn't.
                await self?.presence.announceChange()
                try? await remote.deleteMembership(roomID: roomID, personID: personID)
                // The room this device is looking at has changed; the line
                // follows it.
                await self?.openRoomChannel()
            }
        } else {
            Task { [weak self] in await self?.openRoomChannel() }
        }
    }

    func pickInk(_ ink: Ink, in room: Room) {
        guard let me = state.me,
              let index = state.memberships.firstIndex(where: { $0.roomID == room.id && $0.personID == me.id })
        else { return }
        state.memberships[index].ink = ink
        persist()
        if let remote, remote.isSignedIn {
            let membership = state.memberships[index]
            let roomID = room.id
            let personID = me.id
            pushing {
                try? await remote.push(membership: membership)
                // Remembered beside the membership, so that leaving —
                // which deletes the membership — does not also delete the
                // choice (§6.10).
                await remote.rememberInk(ink, roomID: roomID, personID: personID)
            }
        }
    }

    /// Coming back to a room you were in before: put your own ink on again.
    ///
    /// §6.10 asks that a re-invited person's ink and notes reattach rather
    /// than duplicating. The notes always did — they are keyed by the
    /// author's account id. The ink could not, because it lives on the
    /// membership row and leaving deletes it; `room_inks` is the memory that
    /// outlives it, and this is where it is put back on.
    ///
    /// Never over somebody else: in a room of three or more ink *is*
    /// identity (§4.5), so a colour that has since been taken stays taken and
    /// the room asks for a new one the way it always would.
    private func restoreInk(in roomID: UUID) async {
        guard let remote, remote.isSignedIn, let me = state.me,
              let room = state.rooms.first(where: { $0.id == roomID }),
              let index = state.memberships.firstIndex(where: {
                  $0.roomID == roomID && $0.personID == me.id
              }),
              state.memberships[index].ink == nil,
              let remembered = await remote.rememberedInk(roomID: roomID, personID: me.id)
        else { return }
        let taken = Set(members(of: room).compactMap(\.ink))
        guard !taken.contains(remembered) else { return }
        pickInk(remembered, in: room)
    }

    // MARK: - Readings and the fire

    /// The room's one open reading.
    func openReading(in room: Room) -> Reading? {
        state.readings.first { $0.roomID == room.id && !$0.isFinished }
    }

    /// The shelf: every finished reading, oldest first (S10).
    func shelf(of room: Room) -> [Reading] {
        state.readings
            .filter { $0.roomID == room.id && $0.isFinished }
            .sorted { ($0.finishedAt ?? .distantPast) < ($1.finishedAt ?? .distantPast) }
    }

    @discardableResult
    func startReading(bookID: String, in room: Room) -> Reading {
        let scale = Bible.book(id: bookID)?.scale ?? .medium
        let reading = Reading(
            roomID: room.id, bookID: bookID, startedAt: Date(),
            handiwork: Handiwork(scale: scale), translation: room.translation)
        state.readings.append(reading)
        persist()
        pushReadingRemote(reading)
        return reading
    }

    private func pushReadingRemote(_ reading: Reading) {
        guard let remote, remote.isSignedIn else { return }
        pushing { try? await remote.push(reading: reading) }
    }

    func quietDays(for room: Room) -> [QuietDay] {
        state.quietDays.filter { $0.roomID == room.id }
    }

    func fireState(of reading: Reading, at now: Date = Date()) -> FireState {
        guard let room = state.rooms.first(where: { $0.id == reading.roomID }) else { return .catching }
        return reading.handiwork.state(at: now, bankedIntervals: quietDays(for: room).bankedIntervals)
    }

    /// Fuel is reading (§4.1). Called as Scripture scrolls under the
    /// reader; the engine's own throttle makes frequency harmless.
    func recordReadingActivity(reading: Reading, at address: VerseAddress) {
        guard let me = state.me,
              let index = state.readings.firstIndex(where: { $0.id == reading.id }),
              let room = state.rooms.first(where: { $0.id == reading.roomID })
        else { return }
        let banked = quietDays(for: room).bankedIntervals
        state.readings[index].handiwork.feed(by: me.id, at: Date(), bankedIntervals: banked)
        savePosition(reading: reading, address: address)
        persist()
        // The other phone learns of this feeding through the rolling
        // window — steady needs to know two people fed the same fire. One
        // push per credited event, and the fire row rides along.
        if let remote, remote.isSignedIn,
           let event = state.readings[index].handiwork.recentFuel.last,
           event.personID == me.id, event.at != lastPushedFuelAt {
            lastPushedFuelAt = event.at
            let updated = state.readings[index]
            pushing {
                // The reading row first: a fuel event landing before its
                // reading exists fails the foreign key and is lost.
                try? await remote.push(reading: updated)
                try? await remote.push(fuel: event, readingID: updated.id)
            }
        }
    }

    private var lastPushedFuelAt = Date.distantPast

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
        persist()
        if let remote, remote.isSignedIn {
            Task { try? await remote.push(position: position) }
        }
    }

    /// Where I am in a reading — mine, not the room's (§03).
    func myPosition(in reading: Reading) -> VerseAddress {
        guard let me = state.me,
              let position = state.positions.first(where: { $0.readingID == reading.id && $0.personID == me.id })
        else { return VerseAddress(bookID: reading.bookID, chapter: 1, verse: 1) }
        return VerseAddress(bookID: reading.bookID, chapter: position.chapter, verse: position.verse)
    }

    // MARK: The ribbon (ledger A30)

    func ribbon(in reading: Reading) -> Ribbon? {
        state.ribbons.first { $0.readingID == reading.id }
    }

    /// Closing the book leaves the ribbon where you were. One per reading,
    /// last placed wins; placing it where it already is says nothing.
    func leaveTheRibbon(in reading: Reading, at address: VerseAddress) {
        guard let me = state.me, !reading.isFinished else { return }
        let placed = Ribbon(readingID: reading.id, personID: me.id, chapter: address.chapter, verse: address.verse, placedAt: Date())
        if let existing = ribbon(in: reading),
           existing.chapter == placed.chapter, existing.verse == placed.verse, existing.personID == placed.personID {
            return
        }
        state.ribbons.removeAll { $0.readingID == reading.id }
        state.ribbons.append(placed)
        persist()
        if let remote, remote.isSignedIn {
            sayItAgainIfNeeded(reading.id, .ribbon(readingID: reading.id))
        }
    }

    /// The ribbon, if it is somewhere other than where you are. Offered,
    /// never applied; and never in the same sentence as your own place.
    func ribbonWorthOffering(in reading: Reading) -> Ribbon? {
        guard !reading.isFinished, let ribbon = ribbon(in: reading) else { return nil }
        let mine = myPosition(in: reading)
        if ribbon.chapter == mine.chapter && ribbon.verse == mine.verse { return nil }
        return ribbon
    }

    /// The room's last activity line ("Ruth read this morning") — from the
    /// one last-read stamp the rolling record keeps (§13). The line is a
    /// person, so tapping it goes to them (S12).
    func lastReader(in room: Room) -> (personID: UUID, line: String)? {
        guard let reading = openReading(in: room) ?? shelf(of: room).last,
              let lastFuel = reading.handiwork.lastFuelAt
        else { return nil }
        guard let feeder = reading.handiwork.recentFuel.last.map(\.personID),
              let person = person(feeder), person.id != state.me?.id
        else { return nil }
        return (person.id, Copy.readRecently(firstName(person.name), RibbonClock.phrase(for: lastFuel)))
    }

    /// Marking a quiet day (§4.7) banks the fire for the room. The room
    /// sees who did it — an act of care, performed in public.
    func markQuietDay(in room: Room) {
        guard let me = state.me else { return }
        let day = QuietDay(roomID: room.id, personID: me.id, markedAt: Date(), timeZone: .current)
        guard !state.quietDays.contains(where: {
            $0.roomID == room.id && $0.personID == me.id && $0.localDate == day.localDate
        }) else { return }
        state.quietDays.append(day)
        persist()
        sayItAgainIfNeeded(day.id, .quietDay(day.id))
    }

    func activeQuietDay(in room: Room, at now: Date = Date()) -> QuietDay? {
        quietDays(for: room).first { $0.bankedInterval?.contains(now) == true }
    }

    /// Finishing a book (§6.5): the handiwork becomes an ember.
    func finishReading(_ reading: Reading) {
        guard let index = state.readings.firstIndex(where: { $0.id == reading.id }) else { return }
        state.readings[index].finishedAt = Date()
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

    /// What someone said, found (S23): every note the room has left, in
    /// every reading it has done — the open one included, because the shelf
    /// is the room's whole memory and not only its finished books. Written
    /// notes by their words, voice notes by their transcripts; any case, any
    /// accent.
    ///
    /// A note left for you and not yet found is not searched. Its words are
    /// the verse's to give you (§6.3), and the room already lists it waiting.
    ///
    /// The open book first, then the shelf from the latest ember back; inside
    /// a book, verse order. A search begins at two characters, as the
    /// chooser's does (S13). Everything here is on the phone, so it works
    /// offline and says nothing about what is not (S23).
    func notes(in room: Room, matching query: String) -> [Note] {
        let query = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard query.count >= 2, let me = state.me else { return [] }
        let readings = [openReading(in: room)].compactMap { $0 } + shelf(of: room).reversed()
        return readings.flatMap { reading in
            notes(in: reading).filter { note in
                guard note.authorID == me.id || note.foundBy.contains(me.id) else { return false }
                return (note.body ?? note.transcript)?.localizedStandardContains(query) == true
            }
        }
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
            kind: .written, body: body, createdAt: Date(), isPending: true)
        state.notes.append(note)
        recordReadingActivity(reading: reading, at: verse)
        persist()
        if let remote, remote.isSignedIn {
            let noteToPush = note
            pushing { [weak self] in
                if (try? await remote.push(note: noteToPush, fileURL: nil)) != nil {
                    self?.markNoteSent(noteToPush.id)
                }
            }
        }
        return note
    }

    @discardableResult
    func leaveVoiceNote(audioURL: URL, waveform: [Float], at verse: VerseAddress, in reading: Reading) -> Note {
        guard let me = state.me else { fatalError("note before person") }
        let note = Note(
            readingID: reading.id, authorID: me.id, verse: verse,
            kind: .voice, audioPath: audioURL.lastPathComponent,
            waveform: waveform, transcriptState: .pending, createdAt: Date(), isPending: true)
        state.notes.append(note)
        recordReadingActivity(reading: reading, at: verse)
        persist()
        let noteID = note.id
        Task {
            let transcript = await Transcriber.transcribe(url: audioURL)
            self.setTranscript(noteID: noteID, transcript: transcript)
        }
        if let remote, remote.isSignedIn {
            let noteToPush = note
            pushing { [weak self] in
                if (try? await remote.push(note: noteToPush, fileURL: audioURL)) != nil {
                    self?.markNoteSent(noteToPush.id)
                }
            }
        }
        return note
    }

    func markNoteSent(_ noteID: UUID) {
        guard let index = state.notes.firstIndex(where: { $0.id == noteID }) else { return }
        state.notes[index].isPending = false
        persist()
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
              let index = state.notes.firstIndex(where: { $0.id == note.id })
        else { return }
        state.notes[index].foundBy.insert(me.id)
        persist()
        if let remote, remote.isSignedIn {
            let noteID = note.id
            let personID = me.id
            Task { try? await remote.push(noteFoundID: noteID, personID: personID) }
        }
    }

    /// Take back your own note: the mark and the note vanish with no
    /// tombstone (S04).
    func takeBack(_ note: Note) {
        guard note.authorID == state.me?.id else { return }
        if let path = note.audioPath {
            Task { try? FileManager.default.removeItem(at: await store.audioFileURL(path)) }
        }
        state.notes.removeAll { $0.id == note.id }
        persist()
        if let remote, remote.isSignedIn {
            let voice = note.kind == .voice
            pendingNoteDeletes[note.id] = PendingNoteDelete(readingID: note.readingID, voice: voice)
            pushing { [weak self] in
                if (try? await remote.deleteNote(id: note.id, readingID: note.readingID, voice: voice)) != nil {
                    self?.pendingNoteDeletes.removeValue(forKey: note.id)
                }
            }
        }
    }

    func editWrittenNote(_ note: Note, body: String) {
        guard note.authorID == state.me?.id,
              let index = state.notes.firstIndex(where: { $0.id == note.id })
        else { return }
        state.notes[index].body = body
        state.notes[index].isPending = true
        persist()
        if let remote, remote.isSignedIn {
            let updated = state.notes[index]
            pendingNotePushes.insert(updated.id)
            pushing { [weak self] in
                if (try? await remote.push(note: updated, fileURL: nil)) != nil {
                    self?.pendingNotePushes.remove(updated.id)
                    self?.markNoteSent(updated.id)
                }
            }
        }
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
    /// (S06).
    private(set) var lastUsedInk: Ink = .ochre

    @discardableResult
    func addHighlight(_ range: VerseRange, ink: Ink, in reading: Reading) -> Highlight? {
        guard let me = state.me else { return nil }
        let highlight = Highlight(readingID: reading.id, authorID: me.id, range: range, ink: ink, createdAt: Date())
        state.highlights.append(highlight)
        lastUsedInk = ink
        recordReadingActivity(reading: reading, at: range.start)
        persist()
        sayItAgainIfNeeded(highlight.id, .highlight(highlight.id))
        return highlight
    }

    /// You cannot remove someone else's mark (S06).
    func removeHighlight(_ highlight: Highlight) {
        guard highlight.authorID == state.me?.id else { return }
        state.highlights.removeAll { $0.id == highlight.id }
        persist()
        if let remote, remote.isSignedIn {
            let id = highlight.id
            pendingHighlightDeletes.insert(id)
            pushing { [weak self] in
                if (try? await remote.deleteHighlight(id: id)) != nil {
                    self?.pendingHighlightDeletes.remove(id)
                }
            }
        }
    }

    /// My ink for a highlight right now: my membership ink when the room is
    /// three or more, else free choice.
    func inkForNewHighlight(in room: Room) -> Ink? {
        if inkIsIdentity(in: room) {
            return myMembership(in: room)?.ink
        }
        return nil  // free palette; the toolbar offers all eight
    }

    // MARK: - Reflection Cards (§4.6, S08, S09)

    func card(for reading: Reading, chapter: Int) -> ReflectionCard {
        if let existing = state.cards.first(where: { $0.readingID == reading.id && $0.chapter == chapter }) {
            return existing
        }
        let prompt = ReflectionPrompts.prompt(for: chapter)
        let newCard = ReflectionCard(readingID: reading.id, chapter: chapter, question: prompt, state: .sealed)
        state.cards.append(newCard)
        persist()
        if let remote, remote.isSignedIn {
            pushing { try? await remote.push(card: newCard) }
        }
        return newCard
    }

    func answerCard(_ card: ReflectionCard, answer: String, in room: Room) {
        guard let me = state.me,
              let index = state.cards.firstIndex(where: { $0.id == card.id })
        else { return }
        state.cards[index].answers[me.id] = answer

        let roomMembers = members(of: room)
        let allAnswered = !roomMembers.isEmpty && roomMembers.allSatisfy { member in
            state.cards[index].answers[member.personID] != nil
        }
        if allAnswered {
            state.cards[index].state = .open
            if state.cards[index].openedAt == nil {
                state.cards[index].openedAt = Date()
            }
        }
        let updated = state.cards[index]
        persist()
        sayItAgainIfNeeded(updated.id, .cardAnswer(cardID: updated.id))
    }

    func setDownCard(_ card: ReflectionCard) {
        guard let index = state.cards.firstIndex(where: { $0.id == card.id }) else { return }
        state.cards[index].state = .setDown
        let updated = state.cards[index]
        persist()
        if let remote, remote.isSignedIn {
            pushing { try? await remote.push(card: updated) }
        }
    }

    // MARK: - Settings

    var settings: AppSettings { state.settings }

    func updateSettings(_ transform: (inout AppSettings) -> Void) {
        let before = state.settings
        transform(&state.settings)
        persist()
        if state.settings.quietHoursStart != before.quietHoursStart
            || state.settings.quietHoursEnd != before.quietHoursEnd {
            pushSettingsChanged()
        }
    }

    func notificationPrefs(for room: Room) -> RoomNotificationPrefs {
        state.settings.roomNotifications[room.id] ?? RoomNotificationPrefs()
    }

    func setNotificationPrefs(_ prefs: RoomNotificationPrefs, for room: Room) {
        state.settings.roomNotifications[room.id] = prefs
        persist()
        pushSettingsChanged()
    }

    /// What the translation picker offers: the bundled two always, plus
    /// the licensed editions (NKJV, NIV, NASB 1995 — §16.8, decided).
    /// Streaming needs no account: the proxy is public-read behind the
    /// publishable key, so licensed translations don't wait for sync.
    var availableTranslations: [Translation] {
        TranslationRegistry.bundled + TranslationRegistry.licensed.filter(\.isConfigured)
    }

    /// The person's own default, for the next room they start; the room
    /// on screen changes with it (A42), because the picker is one control.
    func setTranslation(_ translation: TranslationID) {
        state.me?.translation = translation
        persist()
        pushProfileRemote()
        if let room = currentRoom { setRoomTranslation(translation, in: room) }
    }

    func updateMe(name: String) {
        state.me?.name = name
        persist()
        pushProfileRemote()
    }

    private func pushProfileRemote(portraitData: Data? = nil) {
        guard let remote, remote.isSignedIn, let me = state.me else { return }
        pushing { try? await remote.push(profile: me, portraitData: portraitData) }
    }

    func markMarginHintSeen() {
        state.hasSeenMarginHint = true
        persist()
    }

    func markFirePulled() {
        guard !state.hasPulledTheFire else { return }
        state.hasPulledTheFire = true
        persist()
    }

    /// The one ask about notifications (§6.1, S19): not on first launch,
    /// never with the system prompt first — the first time there is
    /// somebody whose note there could be, and only once.
    func shouldAskAboutNotifications(in room: Room) -> Bool {
        !state.hasAskedAboutNotifications && !Notifications.allowed && members(of: room).count > 1
    }

    /// Whose name the ask says: whoever last left a note here, else the
    /// first other person in the room.
    func whoTheAskIsAbout(in room: Room) -> String? {
        let me = state.me?.id
        let others = members(of: room).map(\.personID).filter { $0 != me }
        guard let first = others.first else { return nil }
        let readingIDs = Set(state.readings.filter { $0.roomID == room.id }.map(\.id))
        let mostRecent = state.notes
            .filter { readingIDs.contains($0.readingID) && others.contains($0.authorID) }
            .max { $0.createdAt < $1.createdAt }?.authorID
        return person(mostRecent ?? first)?.name
    }

    func markAskedAboutNotifications() {
        guard !state.hasAskedAboutNotifications else { return }
        state.hasAskedAboutNotifications = true
        persist()
    }

    /// "Tell me": the system's own prompt, now and only now.
    func askForNotifications() async {
        markAskedAboutNotifications()
        await Notifications.ask()
        await registerForPush()
    }

    /// Account deletion (§6.8). The notes question is asked once, at
    /// deletion, and the answer travels with the remote delete when sync
    /// exists; locally both paths clear this device.
    func deleteAccount(keepNotesBehind: Bool) {
        // The notes go first if they are being taken back, then the person
        // is forgotten — the portrait deleted and the profile emptied to a
        // neutral name (A40). The profile row itself stays, because
        // deleting it would cascade through every note they chose to leave
        // behind. Highlights stay either way: a mark on a shared page is
        // not a possession (§6.8).
        let mine = (keepNotesBehind || state.me == nil) ? [] : state.notes.filter { $0.authorID == state.me?.id }
        // Every recording on this phone goes with the state that named it —
        // theirs as well as yours, whatever was chosen about the rows.
        let recordings = state.notes.compactMap(\.audioPath)
        if !recordings.isEmpty {
            Task { [store] in
                for path in recordings {
                    try? FileManager.default.removeItem(at: await store.audioFileURL(path))
                }
            }
        }
        if let remote, remote.isSignedIn {
            Task {
                for note in mine {
                    try? await remote.deleteNote(id: note.id, readingID: note.readingID, voice: note.kind == .voice)
                }
                await remote.forgetProfile(neutralName: Copy.someone)
                if let token = Push.deviceToken { await remote.forgetPushDevice(token: token) }
                Push.forgotten()
                await remote.signOut()
            }
        }
        state = AppState()
        portraits = [:]
        persist()
        RoomWatch.stop()
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
    /// even across the local-first-then-signed-in seam.
    func verifySignInCode(email: String, code: String) async throws {
        guard let remote else { throw SupabaseError.notSignedIn }
        let uid = try await remote.verify(email: email, code: code)
        if state.me == nil {
            // Signing in before this device has a person: a new phone, or a
            // reinstall the Keychain didn't outlive. Carrying your room
            // between phones is the whole reason an account exists (§6.10),
            // and the account's own profile *is* the person — minting a
            // second local identity here and merging it afterwards is how a
            // person ends up with two of themselves.
            await restorePerson(from: uid)
        }
        adoptRemoteIdentity(uid)
        await reconcileOwnProfile()
        // Pull before push: a room this account left on another device is
        // removed by the merge, so the push can't quietly re-join it.
        await refreshFromRemote()
        await pushLocalGraph()
        RoomWatch.start()
        await registerForPush()
    }

    /// The account's profile, made this device's person. Nil means an
    /// account with no profile yet — an email that was verified and never
    /// finished onboarding — and there is nothing to restore, so the caller
    /// asks for a name as it would have anyway.
    private func restorePerson(from uid: UUID) async {
        guard let remote, let row = try? await remote.fetchOwnProfile() else { return }
        state.me = Person(
            id: uid, name: row.name, portraitPath: nil,
            translation: TranslationID(rawValue: row.translation))
        persist()
        // The face is left to `reconcileOwnProfile`, which runs next and
        // asks the same question — fetching it here would download it twice.
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
        if row.portraitPath != nil { refreshPortrait(me.id) }
        persist()
    }

    /// Whether a passkey is worth offering here: the platform can run the
    /// ceremony, there is a backend to run it against, and that backend has
    /// passkeys switched on (A47). Where any is false the control is absent
    /// rather than dead (§6.1).
    var passkeysAvailable: Bool { remote != nil && Passkeys.isAvailable && remote?.passkeysEnabled == true }

    /// Adding one needs a Supabase session of its own: a browser sign-in
    /// has no account there to attach the passkey to.
    var canAddAPasskey: Bool { passkeysAvailable && isSignedIn && remote?.signedInWithAuth0 == false }

    /// Add a passkey to the account that is signed in (§6.10). An addition,
    /// never a replacement: the emailed code stays the way in.
    func registerPasskey() async throws {
        guard let remote, remote.isSignedIn, let anchor = keyWindowAnchor() else { return }
        try await remote.registerPasskey(anchor: anchor)
    }

    /// Sign in with a passkey. The whole of `verifySignInCode` after the
    /// code, because after the session it is the same thread: adopt the
    /// account, restore the person if this device has none, pull, push.
    var auth0Available: Bool {
        remote != nil && Auth0Config.isConfigured
    }

    func signInWithPasskey() async throws {
        guard let remote, let anchor = keyWindowAnchor() else { throw SupabaseError.notSignedIn }
        let uid = try await remote.signInWithPasskey(anchor: anchor)
        if state.me == nil {
            await restorePerson(from: uid)
        }
        adoptRemoteIdentity(uid)
        await reconcileOwnProfile()
        await refreshFromRemote()
        await pushLocalGraph()
        RoomWatch.start()
        await registerForPush()
    }

    /// Sign in using Auth0 Universal Login. Adopts the deterministic user UUID,
    /// restores/creates the profile, and starts real-time sync.
    func signInWithAuth0() async throws {
        guard let remote, let anchor = keyWindowAnchor() else { throw SupabaseError.notSignedIn }
        let user = try await Auth0Service.login(anchor: anchor)
        let uid = try await remote.signInWithAuth0(
            idToken: user.idToken,
            userUUID: user.userUUID,
            email: user.email,
            refreshToken: user.refreshToken
        )
        if state.me == nil {
            await restorePerson(from: uid)
        }
        adoptRemoteIdentity(uid)
        await reconcileOwnProfile()
        await refreshFromRemote()
        await pushLocalGraph()
        RoomWatch.start()
        await registerForPush()
    }

    func signOutRemote() async {
        await forgetPush()
        await remote?.signOut()
        RoomWatch.stop()
    }

    // MARK: - Push (S19)

    /// Tell the server about this phone — the token, every room's switches,
    /// the quiet hours and the zone they are kept in. At launch, on every
    /// return to the foreground (a new zone, a new token), after the one
    /// ask, and when a switch changes. Nothing is sent until notifications
    /// are allowed: a phone that said no is not on anybody's list.
    func registerForPush() async {
        guard let remote, remote.isSignedIn, Notifications.allowed else { return }
        Push.requestToken()
        await Push.learnWhetherTheServerDelivers()
        guard let token = Push.deviceToken else { return }
        var rooms: [UUID: RoomNotificationPrefs] = [:]
        for room in state.rooms { rooms[room.id] = notificationPrefs(for: room) }
        do {
            try await remote.registerPushDevice(
                token: token, environment: Push.environment,
                zone: TimeZone.current.identifier,
                quietFrom: state.settings.quietHoursStart,
                quietUntil: state.settings.quietHoursEnd,
                rooms: rooms, liveStart: nil)
            Push.registration(succeeded: true)
        } catch {
            Push.registration(succeeded: false)
        }
    }

    private var pushRegistration: Task<Void, Never>?

    /// A switch or the quiet hours changed. The server hears it once, a
    /// moment later, however many steps the stepper took on the way.
    private func pushSettingsChanged() {
        pushRegistration?.cancel()
        pushRegistration = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            await self?.registerForPush()
        }
    }

    private func forgetPush() async {
        if let token = Push.deviceToken { await remote?.forgetPushDevice(token: token) }
        Push.forgotten()
    }

    /// The book on screen, whatever presence is saying about it — so that
    /// coming back to the app can say it again.
    private var bookOnScreen: Reading?
    /// The heartbeat, and the room it is telling — which is the room the
    /// book was in, not whichever room is current by the time it stops.
    private var readingHeartbeat: (task: Task<Void, Never>, roomID: UUID, readingID: UUID)?

    /// The reading screen appeared. Presence is the socket's (§4.2); this is
    /// the same fact told to the server, for the phones the socket cannot
    /// reach — "Ruth is reading Mark", and the Live Activity (S24).
    func bookAppeared(_ reading: Reading) {
        bookOnScreen = reading
        sayImReading()
    }

    func bookDisappeared(_ reading: Reading) {
        if bookOnScreen?.id == reading.id { bookOnScreen = nil }
        sayIveLeft()
    }

    /// Said on opening and every ten minutes while the book stays open, and
    /// never while reading quietly, which is the whole of what reading
    /// quietly means. The repeats keep the Live Activity from going stale;
    /// only an arrival is ever said aloud.
    func sayImReading() {
        guard let reading = bookOnScreen, !readingQuietly, !reading.isFinished,
              let remote, remote.isSignedIn
        else { return }
        // Already saying it: a glance at Control Center is not an arrival.
        if readingHeartbeat?.readingID == reading.id { return }
        readingHeartbeat?.task.cancel()
        let heartbeat = Task {
            while !Task.isCancelled {
                try? await remote.iAmReading(room: reading.roomID, reading: reading.id)
                try? await Task.sleep(for: .seconds(600))
            }
        }
        readingHeartbeat = (heartbeat, reading.roomID, reading.id)
    }

    /// The book closed, reading turned quiet, or the app went away: the
    /// Live Activity on the other phones ends.
    func sayIveLeft() {
        guard let heartbeat = readingHeartbeat else { return }
        heartbeat.task.cancel()
        readingHeartbeat = nil
        guard let remote, remote.isSignedIn else { return }
        Task { try? await remote.iHaveLeft(room: heartbeat.roomID) }
    }

    /// Thinking of you (§4.3). The socket carries the touch to a phone in
    /// the room; the server carries the name to one that is not.
    func thinkOf(_ personID: UUID) {
        Task { await presence.sendThinkingOfYou(to: personID) }
        guard let remote, remote.isSignedIn, let room = currentRoom else { return }
        Task { try? await remote.thinkOf(room: room.id, person: personID) }
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
        for i in state.ribbons.indices where state.ribbons[i].personID == old {
            state.ribbons[i].personID = uid
        }
        for i in state.cards.indices {
            if let ans = state.cards[i].answers.removeValue(forKey: old) {
                state.cards[i].answers[uid] = ans
            }
        }
        persist()
    }

    /// Everything this device can honestly claim on the backend: my
    /// profile and portrait, my rooms and membership, live invites, the
    /// readings and their fires, my quiet days, notes, highlights, positions, and reflection cards.
    func pushLocalGraph() async {
        guard let remote, remote.isSignedIn, let me = state.me else { return }
        var portraitData: Data?
        if let path = me.portraitPath {
            portraitData = try? Data(contentsOf: await store.portraitFileURL(path))
        }
        try? await remote.push(profile: me, portraitData: portraitData)
        for room in state.rooms {
            try? await remote.push(room: room)
            if let mine = myMembership(in: room) {
                try? await remote.push(membership: mine)
            }
            for invite in state.invites
            where invite.roomID == room.id && invite.createdBy == me.id
                && invite.expiresAt > Date() {
                try? await remote.push(invite: invite)
            }
            for reading in state.readings where reading.roomID == room.id {
                try? await remote.push(reading: reading)
                for note in state.notes where note.readingID == reading.id && note.authorID == me.id {
                    var audioURL: URL?
                    if let audioPath = note.audioPath {
                        audioURL = await store.audioFileURL(audioPath)
                    }
                    try? await remote.push(note: note, fileURL: audioURL)
                }
                for hl in state.highlights where hl.readingID == reading.id && hl.authorID == me.id {
                    try? await remote.push(highlight: hl)
                }
                if let pos = state.positions.first(where: { $0.readingID == reading.id && $0.personID == me.id }) {
                    try? await remote.push(position: pos)
                }
                if let ribbon = state.ribbons.first(where: { $0.readingID == reading.id && $0.personID == me.id }) {
                    try? await remote.push(ribbon: ribbon)
                }
                for card in state.cards where card.readingID == reading.id {
                    try? await remote.push(card: card)
                    if let myAns = card.answers[me.id] {
                        try? await remote.push(cardAnswer: (cardID: card.id, personID: me.id, body: myAns))
                    }
                }
            }
            for day in quietDays(for: room) where day.personID == me.id {
                try? await remote.push(quietDay: day)
            }
        }
    }

    /// Pull every room I'm in and fold it into local state. Called on
    /// launch, on foreground, and after joining.
    @discardableResult
    func refreshFromRemote() async -> Arrivals {
        guard let remote, remote.isSignedIn else { return .none }
        // Everything this phone still owes the backend goes first, so the
        // pull can't revert it (A36).
        for roomID in Array(pendingRoomPushes) {
            if let room = state.rooms.first(where: { $0.id == roomID }),
               (try? await remote.push(room: room)) != nil {
                pendingRoomPushes.remove(roomID)
            }
        }
        for (noteID, shape) in pendingNoteDeletes {
            if (try? await remote.deleteNote(id: noteID, readingID: shape.readingID, voice: shape.voice)) != nil {
                pendingNoteDeletes.removeValue(forKey: noteID)
            }
        }
        for noteID in Array(pendingNotePushes) {
            guard let note = state.notes.first(where: { $0.id == noteID }) else {
                pendingNotePushes.remove(noteID)
                continue
            }
            var audioURL: URL?
            if let path = note.audioPath { audioURL = await store.audioFileURL(path) }
            if (try? await remote.push(note: note, fileURL: audioURL)) != nil {
                pendingNotePushes.remove(noteID)
                markNoteSent(noteID)
            }
        }
        for highlightID in Array(pendingHighlightDeletes) {
            if (try? await remote.deleteHighlight(id: highlightID)) != nil {
                pendingHighlightDeletes.remove(highlightID)
            }
        }
        for (key, write) in unsaid {
            if (try? await perform(write, on: remote)) != nil {
                unsaid.removeValue(forKey: key)
            }
        }
        for inviteID in Array(state.invitesNotYetPushed) {
            guard let invite = state.invites.first(where: { $0.id == inviteID }),
                  let room = state.rooms.first(where: { $0.id == invite.roomID })
            else {
                state.invitesNotYetPushed.remove(inviteID)
                persist()
                continue
            }
            await pushInviteIfNeeded(invite, for: room)
        }
        guard let graph = try? await remote.pullRooms() else { return .none }
        let landed = merge(graph)
        announce(landed)
        return landed
    }

    /// What a merge brought that somebody might be told about (S19).
    struct Arrivals {
        var notes: [UUID: [Note]] = [:]
        var cardsOpened: [UUID: [ReflectionCard]] = [:]
        var finished: [UUID: [Reading]] = [:]
        var isEmpty: Bool { notes.isEmpty && cardsOpened.isEmpty && finished.isEmpty }
        static let none = Arrivals()
    }

    /// The posts, gated (S19). Notes are grouped by author: one post per
    /// person, naming the verse for one note and only the person for
    /// several — never how many.
    private func announce(_ arrivals: Arrivals) {
        // While the server is delivering, every one of these has already
        // arrived as a push, the moment its row was written. The watermark
        // still moves; the phone just does not say it again.
        guard !arrivals.isEmpty, Notifications.allowed, !Push.delivering else { return }
        let settings = state.settings
        func gate(_ kind: NotificationKind, _ roomID: UUID) -> Bool {
            guard let room = state.rooms.first(where: { $0.id == roomID }) else { return false }
            return Notifications.shouldPost(
                kind: kind, roomID: roomID, prefs: notificationPrefs(for: room),
                settings: settings, visibleRoomID: visibleRoomID)
        }
        for (roomID, notes) in arrivals.notes where gate(.notesLeft, roomID) {
            for (authorID, theirs) in Dictionary(grouping: notes, by: \.authorID) {
                guard let name = person(authorID)?.name,
                      let newest = theirs.max(by: { $0.createdAt < $1.createdAt })
                else { continue }
                Notifications.post(
                    id: Notifications.id(roomID: roomID, kind: .notesLeft, author: authorID), kind: .notesLeft,
                    line: notesLeftLine(name: name, verse: newest.verse, several: theirs.count > 1),
                    to: .verse(roomID: roomID, readingID: newest.readingID, verse: newest.verse))
            }
        }
        for (roomID, cards) in arrivals.cardsOpened where gate(.cardsOpen, roomID) {
            guard let card = cards.first else { continue }
            Notifications.post(
                id: Notifications.id(roomID: roomID, kind: .cardsOpen), kind: .cardsOpen,
                line: Copy.notifCardsOpen,
                to: .cards(roomID: roomID, readingID: card.readingID, chapter: card.chapter))
        }
        for (roomID, readings) in arrivals.finished where gate(.bookFinished, roomID) {
            guard let reading = readings.last, let book = Bible.book(id: reading.bookID)?.name else { continue }
            Notifications.post(
                id: Notifications.id(roomID: roomID, kind: .bookFinished), kind: .bookFinished,
                line: Copy.notifFinished(book), title: Copy.aBookFinished,
                to: .room(roomID: roomID))
        }
    }

    /// The difference a merge made, above the watermark. A nil watermark
    /// is a first merge, and a first merge says nothing.
    private func arrivals(before: AppState, after next: AppState) -> Arrivals {
        guard let me = next.me?.id, let watermark = before.notifiedThrough else { return .none }
        func roomOf(_ readingID: UUID) -> UUID? { next.readings.first { $0.id == readingID }?.roomID }
        let knownNotes = Set(before.notes.map(\.id))
        var notes: [UUID: [Note]] = [:]
        for note in next.notes
        where !knownNotes.contains(note.id) && note.authorID != me && !note.foundBy.contains(me) && note.createdAt > watermark {
            if let roomID = roomOf(note.readingID) { notes[roomID, default: []].append(note) }
        }
        let wasOpen = Set(before.cards.filter { $0.state == .open }.map(\.id))
        var cards: [UUID: [ReflectionCard]] = [:]
        for card in next.cards
        where card.state == .open && !wasOpen.contains(card.id) && (card.openedAt.map { $0 > watermark } ?? false) {
            if let roomID = roomOf(card.readingID) { cards[roomID, default: []].append(card) }
        }
        let wasFinished = Set(before.readings.filter { $0.finishedAt != nil }.map(\.id))
        var finished: [UUID: [Reading]] = [:]
        for reading in next.readings
        where (reading.finishedAt.map { $0 > watermark } ?? false) && !wasFinished.contains(reading.id) {
            finished[reading.roomID, default: []].append(reading)
        }
        return Arrivals(notes: notes, cardsOpened: cards, finished: finished)
    }

    private static func newestRow(in state: AppState) -> Date? {
        [state.notes.map(\.createdAt).max(),
         state.cards.compactMap(\.openedAt).max(),
         state.readings.compactMap(\.finishedAt).max()]
            .compactMap { $0 }.max()
    }

    /// Accepting an invite (S16): join on the backend and pull the room.
    /// The caller decides whether to land in it — a join whose screen was
    /// set down mid-flight still joins, but must not switch the room
    /// underneath whatever the person is doing now.
    func joinRoom(inviteToken: UUID) async throws -> UUID {
        guard let remote, remote.isSignedIn else { throw SupabaseError.notSignedIn }
        let roomID = try await remote.acceptInvite(token: inviteToken)
        // The membership the function minted carries no ink and this
        // device's profile may be newer than the row the room can see, so
        // both go up before the pull that renders them.
        if let me = state.me {
            var portraitData: Data?
            if let path = me.portraitPath {
                portraitData = try? Data(contentsOf: await store.portraitFileURL(path))
            }
            try? await remote.push(profile: me, portraitData: portraitData)
        }
        await refreshFromRemote()
        await restoreInk(in: roomID)
        // Arriving is the news the room most wants: whoever invited you sees
        // you appear without putting their phone down and picking it up.
        await presence.announceChange()
        return roomID
    }

    func handleInviteURL(_ url: URL) {
        guard let token = Self.inviteToken(from: url) else { return }
        pendingInvite = PendingInvite(token: token)
    }

    /// https://readribbon.app/i/<token> or ribbon://i/<token>.
    static func inviteToken(from url: URL) -> UUID? {
        let parts = url.pathComponents.filter { $0 != "/" }
        let host = url.host()?.lowercased().replacingOccurrences(of: "www.", with: "")
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

    @discardableResult
    private func merge(_ graph: RoomGraph) -> Arrivals {
        guard let me = state.me else { return .none }
        let before = state

        for row in graph.rooms {
            let translation = row.translation.map(TranslationID.init(rawValue:))
            if let i = state.rooms.firstIndex(where: { $0.id == row.id }) {
                // Remote wins on the multi-author name and version — except
                // over a local change that hasn't landed there yet.
                if !pendingRoomPushes.contains(row.id) {
                    state.rooms[i].name = row.name
                    state.rooms[i].translation = translation ?? state.rooms[i].translation
                }
                state.rooms[i].isPaused = row.isPaused
            } else {
                state.rooms.append(
                    Room(id: row.id, name: row.name, createdAt: row.createdAt, isPaused: row.isPaused,
                         translation: translation ?? .bsb))
            }
        }

        for row in graph.memberships {
            let ink = row.ink.flatMap(Ink.init(rawValue:))
            if let i = state.memberships.firstIndex(where: {
                $0.roomID == row.roomId && $0.personID == row.personId
            }) {
                // My ink is authored here; everyone else's is authored
                // there.
                if row.personId != me.id { state.memberships[i].ink = ink }
                state.memberships[i].joinedAt = row.joinedAt
            } else {
                state.memberships.append(Membership(
                    id: row.id, roomID: row.roomId, personID: row.personId,
                    ink: ink, joinedAt: row.joinedAt))
            }
        }
        // Departures propagate: a membership the backend no longer has is
        // gone here too. Mine stays within a pulled room — leaving already
        // removed it locally, and a pull racing my own join must not undo
        // the join.
        let pulledRooms = Set(graph.rooms.map(\.id))
        state.memberships.removeAll { membership in
            membership.personID != me.id
                && pulledRooms.contains(membership.roomID)
                && !graph.memberships.contains {
                    $0.roomId == membership.roomID && $0.personId == membership.personID
                }
        }
        // My own departures, made on another device: a room the backend
        // shared with other people that no longer lists me doesn't come
        // back in the pull at all. A room of one stays — it may simply
        // never have been pushed.
        let departedRooms = state.rooms.filter { room in
            !pulledRooms.contains(room.id)
                && members(of: room).contains { $0.personID != me.id }
        }.map(\.id)
        if !departedRooms.isEmpty {
            let departed = Set(departedRooms)
            state.rooms.removeAll { departed.contains($0.id) }
            state.memberships.removeAll { departed.contains($0.roomID) }
            state.invites.removeAll { departed.contains($0.roomID) }
            if let current = state.currentRoomID, departed.contains(current) {
                state.currentRoomID = state.rooms.first?.id
            }
        }

        for row in graph.profiles {
            // Your own row is here too, and its face is asked about on the
            // same terms: a face changed on your other phone has to reach
            // this one, and only the object's tag can say that it did.
            if row.portraitPath != nil { refreshPortrait(row.id) }
            guard row.id != me.id else { continue }
            let translation = TranslationID(rawValue: row.translation)
            var person = state.people[row.id]
                ?? Person(id: row.id, name: row.name, translation: translation)
            person.name = row.name
            person.translation = translation
            state.people[row.id] = person
        }

        for row in graph.invites {
            // The link is the room's, not the device's: a live invite the
            // backend already has is the one this phone hands out too, so a
            // room does not accumulate a link per phone (S15).
            let invite = Invite(
                id: row.id, roomID: row.roomId, createdBy: row.createdBy,
                createdAt: row.createdAt, expiresAt: row.expiresAt)
            if let i = state.invites.firstIndex(where: { $0.id == row.id }) {
                state.invites[i] = invite
            } else {
                state.invites.append(invite)
            }
        }
        // An invite the backend no longer has (the room went, or it aged
        // out of a prune) must not go on being offered from here.
        let pulledInvites = Set(graph.invites.map(\.id))
        // Read out of `state` before the removal starts, never inside it:
        // `removeAll` holds `state` for writing for as long as the closure
        // runs, and reading `state` from within that closure is two
        // overlapping accesses to one property, which traps (A52).
        let notYetPushed = state.invitesNotYetPushed
        state.invites.removeAll { invite in
            pulledRooms.contains(invite.roomID)
                && !pulledInvites.contains(invite.id)
                && !notYetPushed.contains(invite.id)
        }

        for row in graph.quietDays {
            guard !state.quietDays.contains(where: {
                $0.roomID == row.roomId && $0.personID == row.personId && $0.localDate == row.localDate
            }) else { continue }
            state.quietDays.append(QuietDay(
                id: row.id, roomID: row.roomId, personID: row.personId,
                localDate: row.localDate, timeZoneID: row.timeZone, markedAt: row.markedAt))
        }

        let fires = Dictionary(uniqueKeysWithValues: graph.fires.map { ($0.readingId, $0) })
        let fuelByReading = Dictionary(grouping: graph.fuelEvents, by: \.readingId)
        for row in graph.readings {
            let events = (fuelByReading[row.id] ?? [])
                .map { FuelEvent(personID: $0.personId, at: $0.at) }
            if let i = state.readings.firstIndex(where: { $0.id == row.id }) {
                if state.readings[i].finishedAt == nil {
                    state.readings[i].finishedAt = row.finishedAt
                }
                state.readings[i].handiwork = Self.mergedHandiwork(
                    local: state.readings[i].handiwork, remote: fires[row.id], events: events)
                // An open reading follows its room's version; a finished
                // one keeps the one it was read in (A42).
                if !state.readings[i].isFinished, let translation = row.translation.map(TranslationID.init(rawValue:)) {
                    state.readings[i].translation = translation
                }
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
                    handiwork: handiwork,
                    translation: row.translation.map(TranslationID.init(rawValue:)) ?? .bsb))
            }
        }

        // Notes. A note this phone is still deleting is not brought back;
        // one it is still editing keeps the local body (A36).
        for row in graph.notes {
            let noteKind = NoteKind(rawValue: row.kind) ?? .written
            let verse = VerseAddress(bookID: row.bookId, chapter: row.chapter, verse: row.verse)
            let transcriptState: TranscriptState? = nil
            if pendingNoteDeletes[row.id] != nil { continue }
            if let i = state.notes.firstIndex(where: { $0.id == row.id }) {
                let mine = pendingNotePushes.contains(row.id)
                if !mine { state.notes[i].body = row.body ?? state.notes[i].body }
                state.notes[i].waveform = row.waveform ?? state.notes[i].waveform
                state.notes[i].transcript = row.transcript ?? state.notes[i].transcript
                state.notes[i].audioPath = row.audioPath ?? state.notes[i].audioPath
                state.notes[i].isPending = mine
            } else {
                let note = Note(
                    id: row.id,
                    readingID: row.readingId,
                    authorID: row.authorId,
                    verse: verse,
                    kind: noteKind,
                    body: row.body,
                    audioPath: row.audioPath,
                    waveform: row.waveform,
                    transcript: row.transcript,
                    transcriptState: transcriptState,
                    createdAt: row.createdAt,
                    foundBy: [],
                    isPending: false
                )
                state.notes.append(note)
                if noteKind == .voice, let audioPath = row.audioPath, let remote {
                    Task {
                        let localURL = await self.store.audioFileURL(audioPath)
                        if !FileManager.default.fileExists(atPath: localURL.path) {
                            try? await remote.downloadAudio(readingID: row.readingId, noteID: row.id, to: localURL)
                        }
                    }
                }
            }
        }

        // Note founds
        for row in graph.noteFounds {
            if let i = state.notes.firstIndex(where: { $0.id == row.noteId }) {
                state.notes[i].foundBy.insert(row.personId)
            }
        }
        // A note the backend no longer has — taken back on another phone —
        // goes here too, but only against a complete answer, and never one
        // still on its way up from this phone.
        if graph.notesComplete {
            let pulledReadings = Set(graph.readings.map(\.id))
            let pulledNotes = Set(graph.notes.map(\.id))
            let gone = state.notes.filter { note in
                pulledReadings.contains(note.readingID) && !pulledNotes.contains(note.id)
                    && !note.isPending && !pendingNotePushes.contains(note.id)
            }
            if !gone.isEmpty {
                let goneIDs = Set(gone.map(\.id))
                state.notes.removeAll { goneIDs.contains($0.id) }
                let paths = gone.compactMap(\.audioPath)
                if !paths.isEmpty {
                    Task { [store] in
                        for path in paths {
                            try? FileManager.default.removeItem(at: await store.audioFileURL(path))
                        }
                    }
                }
            }
        }

        // Highlights
        for row in graph.highlights {
            if !state.highlights.contains(where: { $0.id == row.id }) {
                let markedIn = row.charTranslation.map(TranslationID.init(rawValue:))
                let range = VerseRange(
                    bookID: row.bookId,
                    chapter: row.chapter,
                    startVerse: row.startVerse,
                    endVerse: row.endVerse,
                    startChar: markedIn == nil ? nil : row.startChar,
                    endChar: markedIn == nil ? nil : row.endChar,
                    charTranslation: markedIn
                )
                let ink = Ink(rawValue: row.ink) ?? .ochre
                state.highlights.append(Highlight(
                    id: row.id,
                    readingID: row.readingId,
                    authorID: row.authorId,
                    range: range,
                    ink: ink,
                    createdAt: row.createdAt
                ))
            }
        }
        if graph.highlightsComplete {
            let pulledReadings = Set(graph.readings.map(\.id))
            let pulledHighlights = Set(graph.highlights.map(\.id))
            // Both of these read `state` through their getters, so both are
            // taken before the removal rather than inside it (A52).
            let stillGoingUp = pendingHighlightPushes
            let beingTakenBack = pendingHighlightDeletes
            state.highlights.removeAll { highlight in
                pulledReadings.contains(highlight.readingID) && !pulledHighlights.contains(highlight.id)
                    && !beingTakenBack.contains(highlight.id)
                    && !stillGoingUp.contains(highlight.id)
            }
        }

        // The ribbon: last placed wins.
        for row in graph.ribbons {
            let incoming = Ribbon(readingID: row.readingId, personID: row.personId, chapter: row.chapter, verse: row.verse, placedAt: row.placedAt)
            if let i = state.ribbons.firstIndex(where: { $0.readingID == row.readingId }) {
                if row.placedAt > state.ribbons[i].placedAt { state.ribbons[i] = incoming }
            } else {
                state.ribbons.append(incoming)
            }
        }

        // Positions
        for row in graph.positions {
            if let i = state.positions.firstIndex(where: { $0.readingID == row.readingId && $0.personID == row.personId }) {
                if row.updatedAt > state.positions[i].updatedAt {
                    state.positions[i].chapter = row.chapter
                    state.positions[i].verse = row.verse
                    state.positions[i].updatedAt = row.updatedAt
                }
            } else {
                state.positions.append(ReadingPosition(
                    readingID: row.readingId,
                    personID: row.personId,
                    chapter: row.chapter,
                    verse: row.verse,
                    updatedAt: row.updatedAt
                ))
            }
        }

        // Reflection Cards & Answers
        let answersByCard = Dictionary(grouping: graph.cardAnswers, by: \.cardId)
        for row in graph.cards {
            var answers: [UUID: String] = [:]
            if let remoteAnswers = answersByCard[row.id] {
                for a in remoteAnswers {
                    answers[a.personId] = a.body
                }
            }
            let remoteState = CardState(rawValue: row.state) ?? .sealed
            if let i = state.cards.firstIndex(where: { $0.id == row.id }) {
                for (author, ans) in answers {
                    state.cards[i].answers[author] = ans
                }
                if remoteState == .open || remoteState == .setDown {
                    state.cards[i].state = remoteState
                }
                if row.openedAt != nil {
                    state.cards[i].openedAt = row.openedAt
                }
            } else {
                state.cards.append(ReflectionCard(
                    id: row.id,
                    readingID: row.readingId,
                    chapter: row.chapter,
                    question: row.question,
                    answers: answers,
                    state: remoteState,
                    openedAt: row.openedAt
                ))
            }
        }

        // Check if sealed cards have all answers
        for i in state.cards.indices where state.cards[i].state == .sealed {
            if let reading = state.readings.first(where: { $0.id == state.cards[i].readingID }),
               let room = state.rooms.first(where: { $0.id == reading.roomID }) {
                let roomMembers = members(of: room)
                if !roomMembers.isEmpty && roomMembers.allSatisfy({ state.cards[i].answers[$0.personID] != nil }) {
                    state.cards[i].state = .open
                    if state.cards[i].openedAt == nil {
                        state.cards[i].openedAt = Date()
                    }
                }
            }
        }

        // What arrived, then the watermark moves — whether or not anything
        // is said about it, so nothing is re-offered next time.
        let landed = arrivals(before: before, after: state)
        state.notifiedThrough = Self.newestRow(in: state) ?? state.notifiedThrough
        persist()
        return landed
    }

    /// Two devices fed the same fire: keep the later feeding's read of
    /// the state, the deeper bed, and the union of the window's fuel — so
    /// a fire fed by two people on two phones still finds its way to
    /// steady on the next feeding.
    private static func mergedHandiwork(
        local: Handiwork, remote row: RemoteSync.FireRow?, events: [FuelEvent]
    ) -> Handiwork {
        var union = Set(local.recentFuel)
        union.formUnion(events)
        let localLast = local.lastFuelAt ?? .distantPast
        let remoteLast = row?.lastFuelAt ?? .distantPast
        let laterIsRemote = row != nil && remoteLast > localLast
        let last = max(localLast, remoteLast)
        return Handiwork(
            scale: local.scale,
            coalDepth: max(local.coalDepth, row?.coalDepth ?? 0),
            lastFuelAt: last == .distantPast ? nil : last,
            restartAt: laterIsRemote ? row?.restartAt : local.restartAt,
            stateAtLastFuel: laterIsRemote
                ? (FireState(rawValue: row?.stateAtLastFuel ?? "") ?? .catching)
                : local.stateAtLastFuel,
            recentFuel: pruned(Array(union)))
    }

    private static func pruned(_ events: [FuelEvent]) -> [FuelEvent] {
        let cutoff = Date().addingTimeInterval(-FireTuning.standard.fuelWindowHours * 3600)
        return events.filter { $0.at >= cutoff }.sorted { $0.at < $1.at }
    }

    /// When each face was last asked about, this launch. A conditional GET
    /// is cheap — a 304 with no body — but it is still a request, and the
    /// room refreshes on every foreground.
    private var portraitCheckedAt: [UUID: Date] = [:]

    /// How long a face is taken on trust before it is asked about again. A
    /// room holds six; this is a handful of tiny requests an hour, and the
    /// product's own pace says a new face can take a few minutes to arrive.
    private static let portraitRecheckInterval: TimeInterval = 15 * 60

    /// Ask whether this person's face has changed, and take it if it has.
    ///
    /// The old rule was "fetch it once, if this device has nothing" — which
    /// is why a changed face never travelled: every device that had already
    /// seen the old one kept it forever. Nothing on the profile row can say
    /// the object changed (the path is `<person id>.jpg`, always), so the
    /// object's own tag is asked instead.
    private func refreshPortrait(_ personID: UUID) {
        guard let remote, remote.isSignedIn else { return }
        if let last = portraitCheckedAt[personID],
           Date().timeIntervalSince(last) < Self.portraitRecheckInterval {
            return
        }
        portraitCheckedAt[personID] = Date()
        Task {
            let found = await remote.fetchPortrait(
                personID: personID, ifNoneMatch: state.portraitETags[personID])
            guard case .changed(let data, let etag) = found else { return }
            guard let path = try? await store.writePortrait(data, personID: personID) else { return }
            if state.me?.id == personID {
                state.me?.portraitPath = path
            } else {
                state.people[personID]?.portraitPath = path
            }
            state.portraitETags[personID] = etag
            if let image = UIImage(data: data) { portraits[personID] = image }
            persist()
        }
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
            // This device is the truth for this face until the upload lands.
            // Without the hold-off a refresh a second later would fetch the
            // face being replaced and put it back.
            portraitCheckedAt[me.id] = Date()
            persist()
            pushProfileRemote(portraitData: data)
        }
    }
}
