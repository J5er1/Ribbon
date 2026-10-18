import AuthenticationServices
import Foundation
import Security
import RibbonCore

// The sign-in thread (§6.10) and the room surface of sync: accounts,
// invites, joining, and the graph a room renders from — rooms, memberships,
// profiles, readings, fires, and the rolling fuel window. Notes, highlights
// and positions still live on-device only; they ride the full sync engine,
// which is the next piece of work (docs/deviations.md).
//
// The app stays local-first: everything here is best-effort and
// fire-and-forget from the UI's point of view. Nothing blocks reading.

/// What the join screen shows before joining (S16): a person, not a
/// product.
struct InvitePreview: Decodable {
    var inviterName: String?
    var roomName: String?
    var expired: Bool
    var full: Bool
}

/// The complete graph of rooms and reading content, as pulled from the backend.
struct RoomGraph {
    var rooms: [RemoteSync.RoomRow] = []
    var memberships: [RemoteSync.MembershipRow] = []
    var profiles: [RemoteSync.ProfileRow] = []
    var readings: [RemoteSync.ReadingRow] = []
    var fires: [RemoteSync.FireRow] = []
    var fuelEvents: [RemoteSync.FuelEventRow] = []
    var quietDays: [RemoteSync.QuietDayRow] = []
    var invites: [RemoteSync.InviteRow] = []
    var notes: [RemoteSync.NoteRow] = []
    var noteFounds: [RemoteSync.NoteFoundRow] = []
    var highlights: [RemoteSync.HighlightRow] = []
    var positions: [RemoteSync.PositionRow] = []
    var cards: [RemoteSync.CardRow] = []
    var cardAnswers: [RemoteSync.CardAnswerRow] = []
    var ribbons: [RemoteSync.RibbonRow] = []
    /// Strangers asking into these rooms (A72), with who is asking. Empty
    /// against a project without the table, which is every project until
    /// the migration lands.
    var knocks: [RemoteSync.KnockRow] = []
    /// Whether the notes and highlights above are the whole of what the
    /// backend holds, or a query failed on the way. Local rows are pruned
    /// against a complete answer only (ledger A36): an empty list from a
    /// failed fetch is not "everything was deleted".
    var notesComplete = false
    var highlightsComplete = false
}

@MainActor
final class RemoteSync {
    private let client = SupabaseClient()
    /// Mirrored from the client so synchronous UI checks don't await.
    private(set) var userID: UUID?
    private(set) var email: String?

    var isSignedIn: Bool { userID != nil }
    /// Signed in through the browser rather than Supabase's own auth: no
    /// passkey can be added to such a session (ledger A47).
    private(set) var signedInWithAuth0 = false
    /// Whether the project's auth has passkeys switched on. Nil until
    /// asked; sticky once known.
    private(set) var passkeysEnabled: Bool?

    /// Restores a persisted session, if one exists. Always returns a
    /// service — signed out is a state, not an absence.
    static func restore() async -> RemoteSync {
        let sync = RemoteSync()
        if let session = SessionKeychain.load() {
            await sync.client.restore(session)
            sync.userID = session.user.id
            sync.email = session.user.email
            sync.signedInWithAuth0 = session.isAuth0
        }
        return sync
    }

    /// Asks the project what its auth offers, once. A failure leaves the
    /// answer unknown, which the interface reads as "not yet".
    func learnWhatAuthOffers() async {
        guard passkeysEnabled == nil else { return }
        if let settings = try? await client.authSettings() {
            passkeysEnabled = settings.passkeysEnabled ?? false
        }
    }

    // MARK: - Sign-in: an emailed code, no passwords (§6.10)

    func sendCode(to email: String) async throws {
        try await client.sendCode(to: email)
    }

    func verify(email: String, code: String) async throws -> UUID {
        let session = try await client.verifyCode(email: email, code: code)
        SessionKeychain.save(session)
        userID = session.user.id
        self.email = session.user.email ?? email
        signedInWithAuth0 = false
        return session.user.id
    }

    // MARK: Passkeys (§6.10)

    /// Register a passkey for the account that is already signed in.
    func registerPasskey(anchor: ASPresentationAnchor) async throws {
        guard #available(iOS 16.0, *) else { throw Passkeys.Failure.cancelled }
        let challenge = try await client.passkeyRegistrationOptions()
        let credential = try await Passkeys().register(
            optionsJSON: challenge.optionsJSON, anchor: anchor)
        try await client.verifyPasskeyRegistration(
            challengeID: challenge.challengeID, credentialJSON: credential)
    }

    /// Sign in with a passkey. Nothing is typed and nothing is emailed: the
    /// authenticator names the account, and the session comes back with it.
    func signInWithPasskey(anchor: ASPresentationAnchor) async throws -> UUID {
        guard #available(iOS 16.0, *) else { throw Passkeys.Failure.cancelled }
        let challenge = try await client.passkeyAuthenticationOptions()
        let credential = try await Passkeys().assert(
            optionsJSON: challenge.optionsJSON, anchor: anchor)
        let session = try await client.verifyPasskeyAuthentication(
            challengeID: challenge.challengeID, credentialJSON: credential)
        SessionKeychain.save(session)
        userID = session.user.id
        email = session.user.email
        signedInWithAuth0 = false
        return session.user.id
    }

    /// Sign in with an Auth0-issued ID token. Sets the Supabase session, saves it
    /// to the keychain, and initializes the local user identity.
    func signInWithAuth0(
        idToken: String,
        userUUID: UUID,
        email: String?,
        refreshToken: String = ""
    ) async throws -> UUID {
        let session = await client.setAuth0Session(
            idToken: idToken,
            userUUID: userUUID,
            email: email,
            refreshToken: refreshToken
        )
        SessionKeychain.save(session)
        userID = session.user.id
        self.email = session.user.email ?? email
        signedInWithAuth0 = true
        return session.user.id
    }

    func signOut() async {
        await client.signOut()
        SessionKeychain.clear()
        userID = nil
        email = nil
        signedInWithAuth0 = false
    }

    /// A token fit for the Realtime socket (§4.2).
    ///
    /// A socket outlives a token — Ribbon's are open for as long as the room
    /// is on screen — and Realtime refuses an expired one outright rather
    /// than asking for a new one. So the token is checked before every join
    /// and refreshed when it is within a minute of the end; a channel that
    /// cannot be authenticated is a room that silently stops being live.
    func realtimeToken() async -> String? {
        guard let token = await client.currentSession?.accessToken else { return nil }
        guard Self.expiresSoon(token) else { return token }
        try? await client.refresh()
        guard let refreshed = await client.currentSession else { return nil }
        SessionKeychain.save(refreshed)
        userID = refreshed.user.id
        // Still dead — a refresh token that was itself revoked, or an Auth0
        // session with none. Joining with no token at all is better than
        // joining with one the server will reject: the channel comes up
        // public and the room is live, where the other way it is nothing.
        return Self.expiresSoon(refreshed.accessToken) ? nil : refreshed.accessToken
    }

    /// The `exp` claim, read without a JWT library: the payload is the
    /// middle base64url segment and its expiry is the only field this needs.
    /// An unreadable token is treated as fine — the server is the authority
    /// on that, and guessing wrong here would refresh on every join.
    static func expiresSoon(_ token: String, within: TimeInterval = 60) -> Bool {
        let parts = token.split(separator: ".")
        guard parts.count == 3 else { return false }
        var payload = String(parts[1])
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        payload += String(repeating: "=", count: (4 - payload.count % 4) % 4)
        guard let data = Data(base64Encoded: payload),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let exp = (json["exp"] as? NSNumber)?.doubleValue
        else { return false }
        return Date(timeIntervalSince1970: exp).timeIntervalSinceNow < within
    }

    // MARK: - Invites and joining (S16)

    func invitePreview(token: UUID) async throws -> InvitePreview? {
        let data = try await client.rpcAnon(
            "invite_preview", body: ["invite_token": token.uuidString.lowercased()])
        let rows = try SupabaseClient.decoder.decode([InvitePreview].self, from: data)
        return rows.first
    }

    /// Joins the invite's room; returns its id. The database enforces
    /// expiry and the six-person ceiling.
    func acceptInvite(token: UUID) async throws -> UUID {
        let data = try await withAuthRetry {
            try await self.client.rpc(
                "accept_invite", body: ["invite_token": token.uuidString.lowercased()])
        }
        return try JSONDecoder().decode(UUID.self, from: data)
    }

    // MARK: - Push notifications (S19)

    /// This phone, as it holds itself: the token, every room's switches,
    /// the quiet hours and the zone they are kept in. All of it, every time
    /// — it is small, and a partial update is a second way to be wrong.
    func registerPushDevice(
        token: String, environment: String, zone: String,
        quietFrom: Int, quietUntil: Int,
        rooms: [UUID: RoomNotificationPrefs], liveStart: String?
    ) async throws {
        var prefs: [String: Any] = [:]
        for (roomID, room) in rooms {
            prefs[roomID.uuidString.lowercased()] = [
                "notesLeft": room.notesLeft,
                "cardsOpen": room.cardsOpen,
                "inTheBook": room.whenTheyOpenTheBook,
                "thinkingOfYou": room.thinkingOfYou,
            ]
        }
        var arguments: [String: Any] = [
            "device_token": token,
            "device_platform": "ios",
            "apns_environment": environment,
            "zone": zone,
            "quiet_from": quietFrom,
            "quiet_until": quietUntil,
            "room_prefs": prefs,
            "live_start": NSNull(),
        ]
        if let liveStart { arguments["live_start"] = liveStart }
        let body = try JSONSerialization.data(withJSONObject: arguments)
        _ = try await withAuthRetry {
            try await self.client.rpc("register_push_device", json: body)
        }
    }

    /// Signing out takes this phone off the server's list.
    func forgetPushDevice(token: String) async {
        _ = try? await withAuthRetry {
            try await self.client.rpc("forget_push_device", body: ["device_token": token])
        }
    }

    /// The book is open (§4.2) — said on opening and every ten minutes
    /// after, never while reading quietly. The server turns an arrival into
    /// "Ruth is reading Mark" for whoever asked to hear it.
    func iAmReading(room: UUID, reading: UUID) async throws {
        _ = try await withAuthRetry {
            try await self.client.rpc("i_am_reading", body: [
                "room": room.uuidString.lowercased(),
                "reading": reading.uuidString.lowercased(),
            ])
        }
    }

    func iHaveLeft(room: UUID) async throws {
        _ = try await withAuthRetry {
            try await self.client.rpc("i_have_left", body: ["room": room.uuidString.lowercased()])
        }
    }

    /// A Live Activity the server started here (S24) has handed over the
    /// token that can keep it current and end it.
    func registerLiveActivity(device: String, room: UUID, reader: UUID, token: String) async throws {
        _ = try await withAuthRetry {
            try await self.client.rpc("register_live_activity", body: [
                "device_token": device,
                "room": room.uuidString.lowercased(),
                "reader": reader.uuidString.lowercased(),
                "activity_token": token,
            ])
        }
    }

    /// The person took it down, or the app did: stop keeping it current.
    func forgetLiveActivity(device: String, room: UUID, reader: UUID) async {
        _ = try? await withAuthRetry {
            try await self.client.rpc("forget_live_activity", body: [
                "device_token": device,
                "room": room.uuidString.lowercased(),
                "reader": reader.uuidString.lowercased(),
            ])
        }
    }

    /// Thinking of you (§4.3), for a phone the room's socket cannot reach.
    func thinkOf(room: UUID, person: UUID) async throws {
        _ = try await withAuthRetry {
            try await self.client.rpc("think_of", body: [
                "room": room.uuidString.lowercased(),
                "person": person.uuidString.lowercased(),
            ])
        }
    }

    // MARK: - Push: the room surface this device knows

    func push(profile: Person, portraitData: Data?) async throws {
        if let portraitData {
            try? await withAuthRetry {
                try await self.client.uploadPortrait(personID: profile.id, data: portraitData)
            }
        }
        // The remote path is deterministic — <person id>.jpg — so a name
        // edit never clears a portrait uploaded earlier.
        let row = ProfileRow(
            id: profile.id, name: profile.name,
            portraitPath: profile.portraitPath == nil
                ? nil : "\(profile.id.uuidString.lowercased()).jpg",
            translation: profile.translation.rawValue)
        try await withAuthRetry {
            try await self.client.upsert(into: "profiles", rows: [row])
        }
    }

    /// The memory of an ink you chose in a room (§6.10). Best-effort on
    /// purpose: it is a nicety, and a build talking to a project without the
    /// table yet must behave exactly as it did before.
    func rememberInk(_ ink: Ink, roomID: UUID, personID: UUID) async {
        try? await withAuthRetry {
            try await self.client.upsert(into: "room_inks", rows: [
                RoomInkRow(roomId: roomID, personId: personID, ink: ink.rawValue)
            ])
        }
    }

    /// The ink this account last chose in this room, if it ever chose one.
    func rememberedInk(roomID: UUID, personID: UUID) async -> Ink? {
        let rows: [RoomInkRow]? = try? await withAuthRetry {
            try await self.client.select(
                [RoomInkRow].self, from: "room_inks",
                query: [
                    URLQueryItem(name: "room_id", value: "eq.\(roomID.uuidString.lowercased())"),
                    URLQueryItem(name: "person_id", value: "eq.\(personID.uuidString.lowercased())"),
                ])
        }
        return rows?.first.flatMap { Ink(rawValue: $0.ink) }
    }

    func push(room: Room) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "rooms", rows: [
                RoomRow(id: room.id, name: room.name, isPaused: room.isPaused, createdAt: room.createdAt, translation: room.translation.rawValue)
            ])
        }
    }

    func push(membership: Membership) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "memberships",
                rows: [MembershipRow(
                    id: membership.id, roomId: membership.roomID,
                    personId: membership.personID,
                    ink: membership.ink?.rawValue, joinedAt: membership.joinedAt)],
                onConflict: "room_id,person_id")
        }
    }

    func push(invite: Invite) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "invites", rows: [
                InviteRow(
                    id: invite.id, roomId: invite.roomID, createdBy: invite.createdBy,
                    createdAt: invite.createdAt, expiresAt: invite.expiresAt)
            ])
        }
    }

    func push(reading: Reading) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "readings", rows: [
                ReadingRow(
                    id: reading.id, roomId: reading.roomID, bookId: reading.bookID,
                    scale: reading.handiwork.scale.rawValue,
                    startedAt: reading.startedAt, finishedAt: reading.finishedAt,
                    translation: reading.translation.rawValue)
            ])
        }
        try await withAuthRetry {
            try await self.client.upsert(into: "fires", rows: [
                FireRow(
                    readingId: reading.id,
                    coalDepth: reading.handiwork.coalDepth,
                    lastFuelAt: reading.handiwork.lastFuelAt,
                    restartAt: reading.handiwork.restartAt,
                    stateAtLastFuel: reading.handiwork.stateAtLastFuel.rawValue)
            ])
        }
    }

    /// One reader's feeding, into the rolling window the schema prunes.
    /// Duplicates are harmless — the engine counts distinct people, never
    /// events.
    func push(fuel: FuelEvent, readingID: UUID) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "fuel_events", rows: [
                FuelEventRow(id: UUID(), readingId: readingID, personId: fuel.personID, at: fuel.at)
            ])
        }
    }

    /// Leaving a room (§6.8): the membership goes; notes and highlights
    /// stay by design (their sync rides the full engine later).
    func deleteMembership(roomID: UUID, personID: UUID) async throws {
        try await withAuthRetry {
            try await self.client.delete(from: "memberships", query: [
                URLQueryItem(name: "room_id", value: "eq.\(roomID.uuidString.lowercased())"),
                URLQueryItem(name: "person_id", value: "eq.\(personID.uuidString.lowercased())"),
            ])
        }
    }

    /// Account deletion (§6.8), the part that is the person's own: the
    /// portrait object goes, and the profile row is kept but emptied — a
    /// neutral name, no face — because deleting the row would cascade
    /// through every note they chose to leave behind (ledger A40). Which
    /// notes go is decided by the caller, before this.
    func forgetProfile(neutralName: String) async {
        guard let userID else { return }
        try? await withAuthRetry {
            try await self.client.deletePortrait(personID: userID)
        }
        try? await withAuthRetry {
            try await self.client.upsert(into: "profiles", rows: [
                ProfileRow(id: userID, name: neutralName, portraitPath: nil, translation: TranslationID.bsb.rawValue)
            ])
        }
        // Nobody can find someone who has gone (A72). Its own write, so a
        // project without the columns still empties the rest.
        try? await withAuthRetry {
            try await self.client.upsert(into: "profiles", rows: [
                BeingFoundRow(
                    id: userID, name: neutralName, translation: TranslationID.bsb.rawValue,
                    findable: false, line: nil, thirteenOrOlderAt: nil)
            ])
        }
    }

    /// Everything one person authored in one reading's notes — the
    /// "take them back" half of leaving or deleting (§6.8).
    func deleteOwnNotes(readingIDs: [UUID], personID: UUID) async throws {
        guard !readingIDs.isEmpty else { return }
        let list = "in.(\(readingIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
        try await withAuthRetry {
            try await self.client.delete(from: "notes", query: [
                URLQueryItem(name: "author_id", value: "eq.\(personID.uuidString.lowercased())"),
                URLQueryItem(name: "reading_id", value: list),
            ])
        }
    }

    /// A marked quiet day banks the fire for the room — on every device.
    func push(quietDay: QuietDay) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "quiet_days",
                rows: [QuietDayRow(
                    id: quietDay.id, roomId: quietDay.roomID, personId: quietDay.personID,
                    localDate: quietDay.localDate, timeZone: quietDay.timeZoneID,
                    markedAt: quietDay.markedAt)],
                onConflict: "room_id,person_id,local_date")
        }
    }

    // MARK: - Content Push (Notes, Highlights, Positions, Cards)

    func push(note: Note, fileURL: URL? = nil) async throws {
        if note.kind == .voice, let fileURL {
            try? await withAuthRetry {
                try await self.client.uploadAudio(readingID: note.readingID, noteID: note.id, fileURL: fileURL)
            }
        }
        try await withAuthRetry {
            try await self.client.upsert(
                into: "notes",
                rows: [NoteRow(
                    id: note.id,
                    readingId: note.readingID,
                    authorId: note.authorID,
                    bookId: note.verse.bookID,
                    chapter: note.verse.chapter,
                    verse: note.verse.verse,
                    kind: note.kind.rawValue,
                    body: note.body,
                    audioPath: note.audioPath,
                    waveform: note.waveform,
                    transcript: note.transcript,
                    createdAt: note.createdAt
                )],
                onConflict: "id")
        }
    }

    func push(noteFoundID: UUID, personID: UUID) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "note_founds",
                rows: [NoteFoundRow(noteId: noteFoundID, personId: personID, foundAt: Date())],
                onConflict: "note_id,person_id")
        }
    }

    func push(highlight: Highlight) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "highlights",
                rows: [HighlightRow(
                    id: highlight.id,
                    readingId: highlight.readingID,
                    authorId: highlight.authorID,
                    bookId: highlight.range.bookID,
                    chapter: highlight.range.chapter,
                    startVerse: highlight.range.startVerse,
                    endVerse: highlight.range.endVerse,
                    ink: highlight.ink.rawValue,
                    createdAt: highlight.createdAt,
                    startChar: highlight.range.startChar,
                    endChar: highlight.range.endChar,
                    charTranslation: highlight.range.charTranslation?.rawValue,
                    startWords: highlight.range.startWords,
                    endWords: highlight.range.endWords,
                    wordsSource: highlight.range.wordsSource
                )],
                onConflict: "id")
        }
    }

    func deleteHighlight(id: UUID) async throws {
        try await withAuthRetry {
            try await self.client.delete(
                from: "highlights",
                query: [URLQueryItem(name: "id", value: "eq.\(id.uuidString.lowercased())")])
        }
    }

    /// A note taken back (ledger A40a). The recording goes first: a row
    /// whose object outlives it is a voice nobody can find but everybody
    /// can still fetch. If the object will not go, the row stays too, and
    /// the whole thing is tried again later.
    func deleteNote(id: UUID, readingID: UUID, voice: Bool) async throws {
        if voice {
            do {
                try await withAuthRetry {
                    try await self.client.deleteAudio(readingID: readingID, noteID: id)
                }
            } catch SupabaseError.http(404, _) {
                // Already gone, or never uploaded: nothing to keep the row for.
            }
        }
        try await withAuthRetry {
            try await self.client.delete(
                from: "notes",
                query: [URLQueryItem(name: "id", value: "eq.\(id.uuidString.lowercased())")])
        }
    }

    /// The ribbon, placed (ledger A30). One per reading; whoever placed it
    /// last wins, on this device and on the server alike.
    func push(ribbon: Ribbon) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "ribbons",
                rows: [RibbonRow(
                    readingId: ribbon.readingID, personId: ribbon.personID,
                    chapter: ribbon.chapter, verse: ribbon.verse, placedAt: ribbon.placedAt)],
                onConflict: "reading_id")
        }
    }

    func push(position: ReadingPosition) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "positions",
                rows: [PositionRow(
                    readingId: position.readingID,
                    personId: position.personID,
                    chapter: position.chapter,
                    verse: position.verse,
                    updatedAt: position.updatedAt
                )],
                onConflict: "reading_id,person_id")
        }
    }

    func push(card: ReflectionCard) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "cards",
                rows: [CardRow(card)],
                onConflict: "id")
        }
    }

    /// The room's own question (A75), asked. False when someone in the room
    /// asked one at this chapter first: there is one of the room's at a
    /// time, and the server keeps it so.
    func ask(card: ReflectionCard) async throws -> Bool {
        do {
            try await withAuthRetry {
                try await self.client.insert(into: "cards", rows: [CardRow(card)])
            }
            return true
        } catch SupabaseError.http(409, let body) where body.contains("23505") {
            return false
        }
    }

    /// Whether the project knows cards the room writes, asked the way the
    /// other new tables are, so the control is never there before it works.
    func roomCardsAreThere() async -> Bool? {
        guard userID != nil else { return nil }
        do {
            _ = try await withAuthRetry {
                try await self.client.select(
                    [ByTheRoomRow].self, from: "cards",
                    query: [
                        URLQueryItem(name: "select", value: "by_the_room"),
                        URLQueryItem(name: "limit", value: "1"),
                    ])
            }
            return true
        } catch SupabaseError.http(let status, _) where (400..<500).contains(status) {
            return false
        } catch {
            return nil
        }
    }

    func push(cardAnswer: (cardID: UUID, personID: UUID, body: String)) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "card_answers",
                rows: [CardAnswerRow(
                    cardId: cardAnswer.cardID,
                    personId: cardAnswer.personID,
                    body: cardAnswer.body,
                    answeredAt: Date()
                )],
                onConflict: "card_id,person_id")
        }
    }

    func downloadAudio(readingID: UUID, noteID: UUID, to destination: URL) async throws {
        try await withAuthRetry {
            try await self.client.downloadAudio(readingID: readingID, noteID: noteID, to: destination)
        }
    }

    // MARK: - Being found (A72)

    /// Whether you can be found, and the line people see. Written whole,
    /// with the name and version a profile must carry, because an absent
    /// line has to reach the database as no line rather than as nothing
    /// said.
    func push(beingFound: BeingFound, profile: Person) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "profiles", rows: [
                BeingFoundRow(
                    id: profile.id, name: profile.name,
                    translation: profile.translation.rawValue,
                    findable: beingFound.findable,
                    line: OwnLine.clean(beingFound.line),
                    thirteenOrOlderAt: beingFound.saidThirteenAt)
            ])
        }
    }

    /// Whether the project has the tables finding runs on (A72): your own
    /// asks, read with nothing asked for. Nil when the network could not
    /// say; a build talking to a project without them shows none of it.
    func findingIsThere() async -> Bool? {
        guard let userID else { return nil }
        do {
            _ = try await withAuthRetry {
                try await self.client.select(
                    [KnockRow].self, from: "knocks",
                    query: [
                        URLQueryItem(name: "person_id", value: "eq.\(userID.uuidString.lowercased())"),
                        URLQueryItem(name: "limit", value: "1"),
                    ])
            }
            return true
        } catch SupabaseError.http(let status, _) where (400..<500).contains(status) {
            return false
        } catch {
            return nil
        }
    }

    /// Your yes to the room being found, or your no.
    func push(wouldOpen: Bool, membership: Membership) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "memberships",
                rows: [WouldOpenRow(
                    id: membership.id, roomId: membership.roomID, personId: membership.personID,
                    joinedAt: membership.joinedAt, wouldOpen: wouldOpen)],
                onConflict: "room_id,person_id")
        }
    }

    // MARK: - Closing a room (A73)

    /// Whether the project can close rooms: the column, read with nothing
    /// asked for. Nil when the network could not say.
    func closingIsThere() async -> Bool? {
        guard userID != nil else { return nil }
        do {
            _ = try await withAuthRetry {
                try await self.client.select(
                    [ClosedAtRow].self, from: "rooms",
                    query: [
                        URLQueryItem(name: "select", value: "closed_at"),
                        URLQueryItem(name: "limit", value: "1"),
                    ])
            }
            return true
        } catch SupabaseError.http(let status, _) where (400..<500).contains(status) {
            return false
        } catch {
            return nil
        }
    }

    /// Your request to close the room. True when it closed — now, or
    /// already — and false while somebody has not asked yet.
    func closeRoom(_ room: UUID) async throws -> Bool {
        let data = try await withAuthRetry {
            try await self.client.rpc("close_room", body: ["target_room": room.uuidString.lowercased()])
        }
        return try JSONDecoder().decode(String.self, from: data) == "closed"
    }

    /// Your request to close, taken back.
    func takeBackClosing(_ membership: Membership) async throws {
        try await withAuthRetry {
            try await self.client.upsert(
                into: "memberships",
                rows: [WouldCloseRow(
                    id: membership.id, roomId: membership.roomID, personId: membership.personID,
                    joinedAt: membership.joinedAt, wouldClose: false)],
                onConflict: "room_id,person_id")
        }
    }

    /// The room's one line, or none.
    func push(roomLine: String?, room: Room) async throws {
        try await withAuthRetry {
            try await self.client.upsert(into: "rooms", rows: [
                RoomLineRow(
                    id: room.id, isPaused: room.isPaused, createdAt: room.createdAt,
                    line: OwnLine.clean(roomLine))
            ])
        }
    }

    /// Open rooms: by what was typed (its words, and the books they could
    /// mean), by a person in them, or one room by its id.
    func findRooms(
        words: String? = nil, books: [String]? = nil, person: UUID? = nil, room: UUID? = nil
    ) async throws -> [OpenRoom] {
        var body: [String: Any] = [:]
        if let words = OwnLine.clean(words) { body["words"] = words }
        if let books, !books.isEmpty { body["books"] = books }
        if let person { body["person"] = person.uuidString.lowercased() }
        if let room { body["room"] = room.uuidString.lowercased() }
        let json = try JSONSerialization.data(withJSONObject: body)
        let data = try await withAuthRetry {
            try await self.client.rpc("find_rooms", json: json)
        }
        return try SupabaseClient.decoder.decode([FoundRoomRow].self, from: data).map(\.openRoom)
    }

    /// Findable people whose name has these words in it.
    func findPeople(words: String) async throws -> [PublicFace] {
        let data = try await withAuthRetry {
            try await self.client.rpc("find_people", body: ["words": words])
        }
        return try SupabaseClient.decoder.decode([FaceRow].self, from: data).map(\.face)
    }

    /// Asking into an open room, with a line if there is one. The database
    /// says why it would not, in a word the caller turns into a sentence.
    func askToJoin(room: UUID, said: String?) async throws {
        var body: [String: Any] = ["target_room": room.uuidString.lowercased()]
        if let said = OwnLine.clean(said, limit: OwnLine.askLimit) { body["said"] = said }
        let json = try JSONSerialization.data(withJSONObject: body)
        _ = try await withAuthRetry {
            try await self.client.rpc("ask_to_join", json: json)
        }
    }

    /// Taking your own ask back. Silent: the room is not told.
    func takeBackAsk(room: UUID) async throws {
        guard let userID else { return }
        try await withAuthRetry {
            try await self.client.delete(from: "knocks", query: [
                URLQueryItem(name: "room_id", value: "eq.\(room.uuidString.lowercased())"),
                URLQueryItem(name: "person_id", value: "eq.\(userID.uuidString.lowercased())"),
            ])
        }
    }

    /// Letting someone who asked into a room you are in.
    func letIn(room: UUID, person: UUID) async throws {
        _ = try await withAuthRetry {
            try await self.client.rpc("let_in", body: [
                "target_room": room.uuidString.lowercased(),
                "person": person.uuidString.lowercased(),
            ])
        }
    }

    /// "Not now": the ask is set down, and the asker is not told.
    func setDownAsk(room: UUID, person: UUID) async throws {
        try await withAuthRetry {
            try await self.client.delete(from: "knocks", query: [
                URLQueryItem(name: "room_id", value: "eq.\(room.uuidString.lowercased())"),
                URLQueryItem(name: "person_id", value: "eq.\(person.uuidString.lowercased())"),
            ])
        }
    }

    /// The people you have blocked. Who has blocked you is never asked.
    func blocked() async throws -> [UUID] {
        guard let userID else { return [] }
        let rows: [BlockRow] = try await withAuthRetry {
            try await self.client.select(
                [BlockRow].self, from: "blocks",
                query: [URLQueryItem(name: "blocker", value: "eq.\(userID.uuidString.lowercased())")])
        }
        return rows.map(\.blocked)
    }

    func block(_ person: UUID) async throws {
        guard let userID else { return }
        try await withAuthRetry {
            try await self.client.insert(into: "blocks", rows: [
                BlockRow(blocker: userID, blocked: person)
            ], ignoringDuplicates: true)
        }
    }

    func unblock(_ person: UUID) async throws {
        guard let userID else { return }
        try await withAuthRetry {
            try await self.client.delete(from: "blocks", query: [
                URLQueryItem(name: "blocker", value: "eq.\(userID.uuidString.lowercased())"),
                URLQueryItem(name: "blocked", value: "eq.\(person.uuidString.lowercased())"),
            ])
        }
    }

    /// A report, read by the people who run Ribbon and nobody else.
    func report(person: UUID?, room: UUID?) async throws {
        guard let userID else { return }
        try await withAuthRetry {
            try await self.client.insert(into: "reports", rows: [
                ReportRow(reporter: userID, personId: person, roomId: room)
            ])
        }
    }

    // MARK: - Pull: every room I'm in

    func pullRooms() async throws -> RoomGraph {
        guard let userID else { return RoomGraph() }
        var graph = RoomGraph()

        let mine: [MembershipRow] = try await withAuthRetry {
            try await self.client.select(
                [MembershipRow].self, from: "memberships",
                query: [URLQueryItem(name: "person_id", value: "eq.\(userID.uuidString.lowercased())")])
        }
        let roomIDs = mine.map(\.roomId)
        guard !roomIDs.isEmpty else { return graph }

        graph.rooms = try await selectAll(RoomRow.self, from: "rooms", where: "id", in: roomIDs)
        graph.memberships = try await selectAll(
            MembershipRow.self, from: "memberships", where: "room_id", in: roomIDs)
        let personIDs = Set(graph.memberships.map(\.personId))
        if !personIDs.isEmpty {
            graph.profiles = try await selectAll(ProfileRow.self, from: "profiles", where: "id", in: personIDs)
        }
        graph.readings = try await selectAll(ReadingRow.self, from: "readings", where: "room_id", in: roomIDs)
        graph.quietDays = try await selectAll(
            QuietDayRow.self, from: "quiet_days", where: "room_id", in: roomIDs)
        // The link is the whole mechanism (S15), and there should be one of
        // it per room: without this, a second device has no live invite to
        // find and mints another rather than re-offering the one that is
        // already out there.
        graph.invites = (try? await selectAll(InviteRow.self, from: "invites", where: "room_id", in: roomIDs)) ?? []
        // Asks into these rooms, each with the asker's face. Best-effort:
        // the table is new (A72), and a phone talking to a project without
        // it must pull exactly what it did before.
        graph.knocks = (try? await withAuthRetry {
            try await self.client.select(
                [KnockRow].self, from: "knocks",
                query: [
                    URLQueryItem(
                        name: "select",
                        value: "room_id,person_id,line,created_at,profiles(id,name,line,portrait_path)"),
                    URLQueryItem(
                        name: "room_id",
                        value: "in.(\(roomIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"),
                ])
        }) ?? []
        let readingIDs = graph.readings.map(\.id)
        if readingIDs.isEmpty {
            graph.notesComplete = true
            graph.highlightsComplete = true
        } else {
            graph.fires = try await selectAll(
                FireRow.self, from: "fires", where: "reading_id", in: readingIDs, orderedBy: ["reading_id"])
            graph.fuelEvents = try await selectAll(
                FuelEventRow.self, from: "fuel_events", where: "reading_id", in: readingIDs)
            // Complete only once every page of every request has come back:
            // the phone prunes its notes and marks against these (A36).
            if let notes = try? await selectAll(NoteRow.self, from: "notes", where: "reading_id", in: readingIDs) {
                graph.notes = notes
                graph.notesComplete = true
            }
            let noteIDs = graph.notes.map(\.id)
            if !noteIDs.isEmpty {
                graph.noteFounds = (try? await selectAll(
                    NoteFoundRow.self, from: "note_founds", where: "note_id", in: noteIDs,
                    orderedBy: ["note_id", "person_id"])) ?? []
            }
            if let highlights = try? await selectAll(
                HighlightRow.self, from: "highlights", where: "reading_id", in: readingIDs) {
                graph.highlights = highlights
                graph.highlightsComplete = true
            }
            graph.ribbons = (try? await selectAll(
                RibbonRow.self, from: "ribbons", where: "reading_id", in: readingIDs,
                orderedBy: ["reading_id"])) ?? []
            graph.positions = (try? await selectAll(
                PositionRow.self, from: "positions", where: "reading_id", in: readingIDs,
                orderedBy: ["reading_id", "person_id"])) ?? []
            graph.cards = (try? await selectAll(CardRow.self, from: "cards", where: "reading_id", in: readingIDs)) ?? []
            let cardIDs = graph.cards.map(\.id)
            if !cardIDs.isEmpty {
                graph.cardAnswers = (try? await selectAll(
                    CardAnswerRow.self, from: "card_answers", where: "card_id", in: cardIDs,
                    orderedBy: ["card_id", "person_id"])) ?? []
            }
        }
        return graph
    }

    /// Every row of `table` whose `column` is one of `ids`, every page of it
    /// (`SupabaseClient.selectAll`), a hundred and fifty ids to a request: a
    /// filter is written into the URL, and the gateway refuses one past about
    /// 16 KB — a few hundred notes' ids had come to that, and the founds for
    /// them were lost without a word.
    private func selectAll<T: Decodable>(
        _ type: T.Type, from table: String, where column: String, in ids: some Collection<UUID>,
        orderedBy key: [String] = ["id"]
    ) async throws -> [T] {
        let all = Array(ids)
        var rows: [T] = []
        for start in stride(from: 0, to: all.count, by: Self.idsPerRequest) {
            let chunk = all[start..<min(start + Self.idsPerRequest, all.count)]
            let list = "in.(\(chunk.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
            rows += try await withAuthRetry {
                try await self.client.selectAll(
                    T.self, from: table, query: [URLQueryItem(name: column, value: list)], orderedBy: key)
            }
        }
        return rows
    }

    static let idsPerRequest = 150

    /// The face, asked for conditionally (§2.7). A network failure reads as
    /// `.unchanged`: the device keeps the face it has, which is what it
    /// would have done anyway.
    func fetchPortrait(
        personID: UUID, ifNoneMatch: String?
    ) async -> SupabaseClient.PortraitFetch {
        let found = try? await withAuthRetry {
            try await self.client.downloadPortrait(personID: personID, ifNoneMatch: ifNoneMatch)
        }
        return found ?? .unchanged
    }

    /// The account's own profile row, if the account has one — the seam
    /// where a fresh device learns it belongs to an older person.
    func fetchOwnProfile() async throws -> ProfileRow? {
        guard let userID else { return nil }
        let rows: [ProfileRow] = try await withAuthRetry {
            try await self.client.select(
                [ProfileRow].self, from: "profiles",
                query: [URLQueryItem(name: "id", value: "eq.\(userID.uuidString.lowercased())")])
        }
        return rows.first
    }

    // MARK: - Plumbing

    /// Access tokens are short-lived; a 401 means refresh and retry once.
    /// A refresh the server itself refuses (revoked or rotated-away
    /// token) means this session is dead — clear it, so signed-out is a
    /// state the interface can see and offer sign-in for, never a
    /// permanent silent failure. A network failure clears nothing.
    private func withAuthRetry<T>(_ work: () async throws -> T) async throws -> T {
        do {
            return try await work()
        } catch SupabaseError.http(401, _) {
            do {
                try await client.refresh()
            } catch let error as SupabaseError {
                if case .http(let code, _) = error, (400..<500).contains(code) {
                    await signOut()
                }
                throw error
            }
            if let refreshed = await client.currentSession {
                SessionKeychain.save(refreshed)
                userID = refreshed.user.id
            }
            return try await work()
        }
    }

    // MARK: - Rows (PostgREST shapes; snake_case via the client's coders)

    struct ProfileRow: Codable {
        var id: UUID
        var name: String
        var portraitPath: String?
        var translation: String
        /// Being found (A72). Nil from a project without the columns, and
        /// never sent from here: an optional left nil is left out of the
        /// row, so a profile push cannot undo a choice made on this screen
        /// or another phone. `push(beingFound:)` writes them.
        var findable: Bool? = nil
        var line: String? = nil
        var thirteenOrOlderAt: Date? = nil
    }

    /// A profile's being-found fields, with the name and version a new
    /// profile row needs. Nil is written as null: no line is a line taken
    /// away.
    struct BeingFoundRow: Encodable {
        var id: UUID
        var name: String
        var translation: String
        var findable: Bool
        var line: String?
        var thirteenOrOlderAt: Date?

        enum CodingKeys: String, CodingKey {
            case id, name, translation, findable, line, thirteenOrOlderAt
        }

        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(id, forKey: .id)
            try c.encode(name, forKey: .name)
            try c.encode(translation, forKey: .translation)
            try c.encode(findable, forKey: .findable)
            try c.encode(line, forKey: .line)
            try c.encode(thirteenOrOlderAt, forKey: .thirteenOrOlderAt)
        }
    }

    struct ClosedAtRow: Decodable {
        var closedAt: Date?
    }

    struct WouldCloseRow: Encodable {
        var id: UUID
        var roomId: UUID
        var personId: UUID
        var joinedAt: Date
        var wouldClose: Bool
    }

    struct WouldOpenRow: Encodable {
        var id: UUID
        var roomId: UUID
        var personId: UUID
        var joinedAt: Date
        var wouldOpen: Bool
    }

    /// The room's line, with what a new room row needs. Nil is written as
    /// null, for the same reason as a profile's.
    struct RoomLineRow: Encodable {
        var id: UUID
        var isPaused: Bool
        var createdAt: Date
        var line: String?

        enum CodingKeys: String, CodingKey { case id, isPaused, createdAt, line }

        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(id, forKey: .id)
            try c.encode(isPaused, forKey: .isPaused)
            try c.encode(createdAt, forKey: .createdAt)
            try c.encode(line, forKey: .line)
        }
    }

    /// A face as `find_rooms`, `find_people` and an embedded profile give it.
    struct FaceRow: Decodable {
        var id: UUID
        var name: String
        var line: String?
        var portraitPath: String?

        var face: PublicFace { PublicFace(id: id, name: name, line: line, portraitPath: portraitPath) }
    }

    struct FoundRoomRow: Decodable {
        var roomId: UUID
        var name: String?
        var line: String?
        var full: Bool
        var asked: Bool
        var bookId: String?
        var scale: String?
        var coalDepth: Double?
        var lastFuelAt: Date?
        var restartAt: Date?
        var stateAtLastFuel: String?
        var faces: [FaceRow]

        var openRoom: OpenRoom {
            var fire: Handiwork?
            if let scale = scale.flatMap(FireScale.init(rawValue:)),
               let state = stateAtLastFuel.flatMap(FireState.init(rawValue:)) {
                fire = Handiwork(
                    scale: scale, coalDepth: coalDepth ?? 0, lastFuelAt: lastFuelAt,
                    restartAt: restartAt, stateAtLastFuel: state)
            }
            return OpenRoom(
                id: roomId, name: name, line: line, full: full, asked: asked,
                bookID: bookId, fire: fire, faces: faces.map(\.face))
        }
    }

    struct KnockRow: Decodable {
        var roomId: UUID
        var personId: UUID
        var line: String?
        var createdAt: Date
        /// The asker, as the room is allowed to see them. Nil when the
        /// profile is not visible — someone blocked, or a race with a
        /// deletion — and such an ask is not shown.
        var profiles: FaceRow?

        var knock: Knock? {
            guard let face = profiles?.face else { return nil }
            return Knock(roomID: roomId, person: face, line: line, askedAt: createdAt)
        }
    }

    struct BlockRow: Codable {
        var blocker: UUID
        var blocked: UUID
    }

    struct ReportRow: Encodable {
        var reporter: UUID
        var personId: UUID?
        var roomId: UUID?
    }

    struct RoomInkRow: Codable {
        var roomId: UUID
        var personId: UUID
        var ink: String
    }

    struct RoomRow: Codable {
        var id: UUID
        var name: String?
        var isPaused: Bool
        var createdAt: Date
        var translation: String?
        /// The room's line (A72). Read, never sent from here: see
        /// `push(roomLine:room:)`.
        var line: String? = nil
        /// When the room closed (A73). Read, never sent: only `close_room`
        /// closes a room.
        var closedAt: Date? = nil
    }

    struct MembershipRow: Codable {
        var id: UUID
        var roomId: UUID
        var personId: UUID
        var ink: String?
        var joinedAt: Date
        /// This person's yes to the room being found (A72). Read, never
        /// sent from here: see `push(wouldOpen:membership:)`.
        var wouldOpen: Bool? = nil
        /// This person's request to close the room (A73). Set by
        /// `closeRoom`, cleared by `takeBackClosing`.
        var wouldClose: Bool? = nil
    }

    struct InviteRow: Codable {
        var id: UUID
        var roomId: UUID
        var createdBy: UUID
        var createdAt: Date
        var expiresAt: Date
    }

    struct ReadingRow: Codable {
        var id: UUID
        var roomId: UUID
        var bookId: String
        var scale: String
        var startedAt: Date
        var finishedAt: Date?
        var translation: String?
    }

    struct FireRow: Codable {
        var readingId: UUID
        var coalDepth: Double
        var lastFuelAt: Date?
        var restartAt: Date?
        var stateAtLastFuel: String
    }

    struct FuelEventRow: Codable {
        var id: UUID
        var readingId: UUID
        var personId: UUID
        var at: Date
    }

    /// local_date is a bare Postgres date ("2026-09-01") — a String here,
    /// matching QuietDay.localDate; the timestamp decoder would refuse it.
    struct QuietDayRow: Codable {
        var id: UUID
        var roomId: UUID
        var personId: UUID
        var localDate: String
        var timeZone: String
        var markedAt: Date
    }

    struct NoteRow: Codable {
        var id: UUID
        var readingId: UUID
        var authorId: UUID
        var bookId: String
        var chapter: Int
        var verse: Int
        var kind: String
        var body: String?
        var audioPath: String?
        var waveform: [Float]?
        var transcript: String?
        var createdAt: Date
    }

    struct NoteFoundRow: Codable {
        var noteId: UUID
        var personId: UUID
        var foundAt: Date
    }

    struct HighlightRow: Codable {
        var id: UUID
        var readingId: UUID
        var authorId: UUID
        var bookId: String
        var chapter: Int
        var startVerse: Int
        var endVerse: Int
        var ink: String
        var createdAt: Date
        var startChar: Int?
        var endChar: Int?
        var charTranslation: String?
        /// The original words the mark covers at each partial end, and the
        /// numbering they count in (A60). Like the offsets, left out of the
        /// row when nil and nil when the server has none — a row from a
        /// build that never wrote them, or a project without the columns.
        var startWords: [Int]?
        var endWords: [Int]?
        var wordsSource: String?
    }

    struct RibbonRow: Codable {
        var readingId: UUID
        var personId: UUID
        var chapter: Int
        var verse: Int
        var placedAt: Date
    }

    struct PositionRow: Codable {
        var readingId: UUID
        var personId: UUID
        var chapter: Int
        var verse: Int
        var updatedAt: Date
    }

    struct CardRow: Codable {
        var id: UUID
        var readingId: UUID
        var chapter: Int
        var question: String
        var state: String
        var openedAt: Date?
        /// Only ever sent for the room's own card (A75); a project without
        /// the column never hears of it from a card of Ribbon's.
        var byTheRoom: Bool? = nil
        var writtenBy: UUID? = nil
    }

    struct ByTheRoomRow: Decodable {
        var byTheRoom: Bool?
    }

    struct CardAnswerRow: Codable {
        var cardId: UUID
        var personId: UUID
        var body: String
        var answeredAt: Date
    }
}

// MARK: - Session persistence

/// The session is a credential, so it lives in the Keychain, not in
/// state.json.
private enum SessionKeychain {
    private static let service = "app.ribbon.supabase-session"
    private static let account = "session"

    static func save(_ session: SupabaseSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }

    static func load() -> SupabaseSession? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data
        else { return nil }
        return try? JSONDecoder().decode(SupabaseSession.self, from: data)
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

extension RemoteSync.CardRow {
    /// A card as the server keeps it: its state as the table spells it, and
    /// whose it is when the room asked it (A75).
    init(_ card: ReflectionCard) {
        self.init(
            id: card.id, readingId: card.readingID, chapter: card.chapter, question: card.question,
            state: ChapterCards.stored(card.state), openedAt: card.openedAt,
            byTheRoom: card.byTheRoom ? true : nil, writtenBy: card.byTheRoom ? card.writtenBy : nil)
    }
}
