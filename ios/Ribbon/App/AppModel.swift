import Foundation
import SwiftUI
import RibbonCore

// The app's one store. Local-first: every mutation lands in AppState and is
// persisted; a remote backend (when configured and signed in) syncs the
// same objects later. Cold start renders from this state instantly — no
// splash, no skeleton (§05).

@MainActor
@Observable
final class AppModel {
    private(set) var state: AppState
    let store: LocalStore
    let scripture = ScriptureStore.shared
    let presence: PresenceService

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
        let model = AppModel(state: state, store: store, presence: LocalPresenceService())
        await model.loadPortraits()
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
    var isOnboarded: Bool { state.me != nil && currentRoom != nil }

    var currentRoom: Room? {
        state.rooms.first { $0.id == state.currentRoomID } ?? state.rooms.first
    }

    func person(_ id: UUID) -> Person? {
        id == state.me?.id ? state.me : state.people[id]
    }

    func portrait(_ id: UUID) -> UIImage? { portraits[id] }

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
        if names.isEmpty { return "Your room" }
        return names.joined(separator: " & ")
    }

    // MARK: - Onboarding & rooms

    func completeOnboarding(name: String, portraitData: Data?) async {
        // A person exists once, ever: re-running the thread must never
        // mint a second identity and orphan what the first one left.
        if state.me != nil {
            updateMe(name: name)
            if let portraitData {
                await setPortrait(portraitData)
            }
            if currentRoom == nil {
                createRoom(named: nil)
            }
            return
        }
        let personID = UUID()
        var portraitPath: String?
        if let portraitData {
            portraitPath = try? await store.writePortrait(portraitData, personID: personID)
            if let image = UIImage(data: portraitData) { portraits[personID] = image }
        }
        let person = Person(id: personID, name: name, portraitPath: portraitPath, translation: .bsb)
        state.me = person
        createRoom(named: nil)
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

    @discardableResult
    func createInvite(for room: Room) -> Invite {
        guard let me = state.me else { fatalError("invite before person") }
        // Reuse a live invite rather than minting link after link.
        if let existing = state.invites.first(where: { $0.roomID == room.id && $0.expiresAt > Date() }) {
            return existing
        }
        let invite = Invite(roomID: room.id, createdBy: me.id, createdAt: Date())
        state.invites.append(invite)
        persist()
        return invite
    }

    var roomIsFull: Bool {
        guard let room = currentRoom else { return false }
        return members(of: room).count >= Room.capacity
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
    }

    func pickInk(_ ink: Ink, in room: Room) {
        guard let me = state.me,
              let index = state.memberships.firstIndex(where: { $0.roomID == room.id && $0.personID == me.id })
        else { return }
        state.memberships[index].ink = ink
        persist()
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
        return reading
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
    }

    func activeQuietDay(in room: Room, at now: Date = Date()) -> QuietDay? {
        quietDays(for: room).first { $0.bankedInterval?.contains(now) == true }
    }

    /// Finishing a book (§6.5): the handiwork becomes an ember.
    func finishReading(_ reading: Reading) {
        guard let index = state.readings.firstIndex(where: { $0.id == reading.id }) else { return }
        state.readings[index].finishedAt = Date()
        persist()
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
        return note
    }

    @discardableResult
    func leaveVoiceNote(audioURL: URL, waveform: [Float], at verse: VerseAddress, in reading: Reading) -> Note {
        guard let me = state.me else { fatalError("note before person") }
        var note = Note(
            readingID: reading.id, authorID: me.id, verse: verse,
            kind: .voice, audioPath: audioURL.lastPathComponent,
            waveform: waveform, transcriptState: .pending, createdAt: Date())
        state.notes.append(note)
        recordReadingActivity(reading: reading, at: verse)
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
    }

    func editWrittenNote(_ note: Note, body: String) {
        guard note.authorID == state.me?.id,
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
    /// (S06).
    private(set) var lastUsedInk: Ink = .ochre

    func addHighlight(_ range: VerseRange, ink: Ink, in reading: Reading) {
        guard let me = state.me else { return }
        state.highlights.append(
            Highlight(readingID: reading.id, authorID: me.id, range: range, ink: ink, createdAt: Date()))
        lastUsedInk = ink
        recordReadingActivity(reading: reading, at: range.start)
        persist()
    }

    /// You cannot remove someone else's mark (S06).
    func removeHighlight(_ highlight: Highlight) {
        guard highlight.authorID == state.me?.id else { return }
        state.highlights.removeAll { $0.id == highlight.id }
        persist()
    }

    /// My ink for a highlight right now: my membership ink when the room is
    /// three or more, else free choice.
    func inkForNewHighlight(in room: Room) -> Ink? {
        if inkIsIdentity(in: room) {
            return myMembership(in: room)?.ink
        }
        return nil  // free palette; the toolbar offers all eight
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
    }

    func updateMe(name: String) {
        state.me?.name = name
        persist()
    }

    func markMarginHintSeen() {
        state.hasSeenMarginHint = true
        persist()
    }

    /// Account deletion (§6.8). The notes question is asked once, at
    /// deletion, and the answer travels with the remote delete when sync
    /// exists; locally both paths clear this device.
    func deleteAccount(keepNotesBehind: Bool) {
        // TODO(sync): pass keepNotesBehind to the backend's delete so notes
        // either stay for the room (default) or leave with the person.
        _ = keepNotesBehind
        state = AppState()
        portraits = [:]
        persist()
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
        }
    }
}
