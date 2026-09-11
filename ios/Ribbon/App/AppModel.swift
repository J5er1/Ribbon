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
        let store = LocalStore()
        let state = await store.load()
        let presence: PresenceService = SupabaseConfig.remoteEnabled ? SupabaseRealtimePresenceService() : LocalPresenceService()
        let model = AppModel(state: state, store: store, presence: presence)
        await model.loadPortraits()
        if SupabaseConfig.remoteEnabled {
            model.remote = await RemoteSync.restore()
        }
        model.startListeningToPresence()
        return model
    }

    private func startListeningToPresence() {
        Task { [weak self] in
            guard let self else { return }
            for await event in self.presence.events {
                switch event {
                case .roster(let people):
                    self.presentPeople = people
                case .thinkingOfYou(let fromName):
                    Haptics.shared.tapOnTheShoulder()
                }
            }
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
        let room = Room(name: name, createdAt: Date())
        state.rooms.append(room)
        state.memberships.append(Membership(roomID: room.id, personID: me.id, joinedAt: Date()))
        state.currentRoomID = room.id
        persist()
        return room
    }

    func switchRoom(to roomID: UUID) {
        state.currentRoomID = roomID
        followingPersonID = nil
        persist()
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
        try await remote.push(invite: invite)
    }

    @discardableResult
    func createInvite(for room: Room) -> Invite {
        guard let me = state.me else { fatalError("invite before person") }
        // Reuse a live invite rather than minting link after link.
        let invite: Invite
        if let existing = state.invites.first(where: { $0.roomID == room.id && $0.expiresAt > Date() }) {
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
            Task {
                do {
                    try await pushInvite(invite, for: room)
                } catch {
                    print("[AppModel] pushInvite failed for room \(room.id): \(error)")
                }
            }
        }
        return invite
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
                if (try? await remote.push(room: updated)) != nil {
                    pendingRenamePushes.remove(updated.id)
                }
            }
        }
    }

    /// Leaving (§6.8): one confirmation, plainly worded, no guilt. Notes
    /// default to staying — they were left for the other person.
    func leaveRoom(_ room: Room, keepNotesBehind: Bool) {
        guard let me = state.me else { return }
        let readingIDs = Set(state.readings.filter { $0.roomID == room.id }.map(\.id))
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
            Task { try? await remote.deleteMembership(roomID: roomID, personID: personID) }
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
            Task {
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
            handiwork: Handiwork(scale: scale))
        state.readings.append(reading)
        persist()
        pushReadingRemote(reading)
        return reading
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
            Task {
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
        let name = person.name.split(separator: " ").first.map(String.init) ?? person.name
        return (person.id, Copy.readRecently(name, RibbonClock.phrase(for: lastFuel)))
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
        if let remote, remote.isSignedIn {
            Task { try? await remote.push(quietDay: day) }
        }
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
            Task {
                if (try? await remote.push(note: noteToPush, fileURL: nil)) != nil {
                    await MainActor.run { self.markNoteSent(noteToPush.id) }
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
            Task {
                if (try? await remote.push(note: noteToPush, fileURL: audioURL)) != nil {
                    await MainActor.run { self.markNoteSent(noteToPush.id) }
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
            let noteID = note.id
            Task { try? await remote.deleteNote(id: noteID) }
        }
    }

    func editWrittenNote(_ note: Note, body: String) {
        guard note.authorID == state.me?.id,
              let index = state.notes.firstIndex(where: { $0.id == note.id })
        else { return }
        state.notes[index].body = body
        persist()
        if let remote, remote.isSignedIn {
            let updated = state.notes[index]
            Task { try? await remote.push(note: updated, fileURL: nil) }
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

    func addHighlight(_ range: VerseRange, ink: Ink, in reading: Reading) {
        guard let me = state.me else { return }
        let highlight = Highlight(readingID: reading.id, authorID: me.id, range: range, ink: ink, createdAt: Date())
        state.highlights.append(highlight)
        lastUsedInk = ink
        recordReadingActivity(reading: reading, at: range.start)
        persist()
        if let remote, remote.isSignedIn {
            Task { try? await remote.push(highlight: highlight) }
        }
    }

    /// You cannot remove someone else's mark (S06).
    func removeHighlight(_ highlight: Highlight) {
        guard highlight.authorID == state.me?.id else { return }
        state.highlights.removeAll { $0.id == highlight.id }
        persist()
        if let remote, remote.isSignedIn {
            let id = highlight.id
            Task { try? await remote.deleteHighlight(id: id) }
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
            Task { try? await remote.push(card: newCard) }
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
        if let remote, remote.isSignedIn {
            Task {
                try? await remote.push(cardAnswer: (cardID: updated.id, personID: me.id, body: answer))
                try? await remote.push(card: updated)
            }
        }
    }

    func setDownCard(_ card: ReflectionCard) {
        guard let index = state.cards.firstIndex(where: { $0.id == card.id }) else { return }
        state.cards[index].state = .setDown
        let updated = state.cards[index]
        persist()
        if let remote, remote.isSignedIn {
            Task { try? await remote.push(card: updated) }
        }
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
        state.hasSeenMarginHint = true
        persist()
    }

    /// Account deletion (§6.8). The notes question is asked once, at
    /// deletion, and the answer travels with the remote delete when sync
    /// exists; locally both paths clear this device.
    func deleteAccount(keepNotesBehind: Bool) {
        // The backend forgets the person: deleting the profile cascades
        // memberships, invites, fuel, quiet days and positions; shared
        // rooms and their content stay for the people still in them.
        // (Notes aren't remote yet, so the keep/take answer is local-only
        // until the full sync engine; the bare auth user — an email and
        // nothing else — needs a service-role function and rides along
        // then too.)
        _ = keepNotesBehind
        if let remote, remote.isSignedIn {
            Task {
                await remote.deleteAccountData()
                await remote.signOut()
            }
        }
        state = AppState()
        portraits = [:]
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
    /// ceremony, and there is a backend to run it against. Where either is
    /// false the control is absent rather than dead (§6.1).
    var passkeysAvailable: Bool { remote != nil && Passkeys.isAvailable }

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
    }

    func signOutRemote() async {
        await remote?.signOut()
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
            for invite in state.invites where invite.roomID == room.id && invite.expiresAt > Date() {
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
    func refreshFromRemote() async {
        guard let remote, remote.isSignedIn else { return }
        // An unpushed rename goes first, so the pull can't revert it.
        for roomID in Array(pendingRenamePushes) {
            if let room = state.rooms.first(where: { $0.id == roomID }),
               (try? await remote.push(room: room)) != nil {
                pendingRenamePushes.remove(roomID)
            }
        }
        guard let graph = try? await remote.pullRooms() else { return }
        merge(graph)
    }

    /// Accepting an invite (S16): join on the backend and pull the room.
    /// The caller decides whether to land in it — a join whose screen was
    /// set down mid-flight still joins, but must not switch the room
    /// underneath whatever the person is doing now.
    func joinRoom(inviteToken: UUID) async throws -> UUID {
        guard let remote, remote.isSignedIn else { throw SupabaseError.notSignedIn }
        let roomID = try await remote.acceptInvite(token: inviteToken)
        await refreshFromRemote()
        await restoreInk(in: roomID)
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

    private func merge(_ graph: RoomGraph) {
        guard let me = state.me else { return }

        for row in graph.rooms {
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
                    handiwork: handiwork))
            }
        }

        // Notes
        for row in graph.notes {
            let noteKind = NoteKind(rawValue: row.kind) ?? .written
            let verse = VerseAddress(bookID: row.bookId, chapter: row.chapter, verse: row.verse)
            let transcriptState: TranscriptState? = nil
            if let i = state.notes.firstIndex(where: { $0.id == row.id }) {
                state.notes[i].body = row.body ?? state.notes[i].body
                state.notes[i].waveform = row.waveform ?? state.notes[i].waveform
                state.notes[i].transcript = row.transcript ?? state.notes[i].transcript
                state.notes[i].transcriptState = state.notes[i].transcriptState
                state.notes[i].audioPath = row.audioPath ?? state.notes[i].audioPath
                state.notes[i].isPending = false
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

        // Highlights
        for row in graph.highlights {
            if !state.highlights.contains(where: { $0.id == row.id }) {
                let range = VerseRange(
                    bookID: row.bookId,
                    chapter: row.chapter,
                    startVerse: row.startVerse,
                    endVerse: row.endVerse
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

        persist()
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
