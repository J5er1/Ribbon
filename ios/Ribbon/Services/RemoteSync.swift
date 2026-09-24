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
                    charTranslation: highlight.range.charTranslation?.rawValue
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
                rows: [CardRow(
                    id: card.id,
                    readingId: card.readingID,
                    chapter: card.chapter,
                    question: card.question,
                    state: card.state.rawValue,
                    openedAt: card.openedAt
                )],
                onConflict: "id")
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
        let roomList = "in.(\(roomIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"

        graph.rooms = try await withAuthRetry {
            try await self.client.select(
                [RoomRow].self, from: "rooms",
                query: [URLQueryItem(name: "id", value: roomList)])
        }
        graph.memberships = try await withAuthRetry {
            try await self.client.select(
                [MembershipRow].self, from: "memberships",
                query: [URLQueryItem(name: "room_id", value: roomList)])
        }
        let personIDs = Set(graph.memberships.map(\.personId))
        if !personIDs.isEmpty {
            let personList = "in.(\(personIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
            graph.profiles = try await withAuthRetry {
                try await self.client.select(
                    [ProfileRow].self, from: "profiles",
                    query: [URLQueryItem(name: "id", value: personList)])
            }
        }
        graph.readings = try await withAuthRetry {
            try await self.client.select(
                [ReadingRow].self, from: "readings",
                query: [URLQueryItem(name: "room_id", value: roomList)])
        }
        graph.quietDays = try await withAuthRetry {
            try await self.client.select(
                [QuietDayRow].self, from: "quiet_days",
                query: [URLQueryItem(name: "room_id", value: roomList)])
        }
        // The link is the whole mechanism (S15), and there should be one of
        // it per room: without this, a second device has no live invite to
        // find and mints another rather than re-offering the one that is
        // already out there.
        graph.invites = (try? await withAuthRetry {
            try await self.client.select(
                [InviteRow].self, from: "invites",
                query: [URLQueryItem(name: "room_id", value: roomList)])
        }) ?? []
        let readingIDs = graph.readings.map(\.id)
        if readingIDs.isEmpty {
            graph.notesComplete = true
            graph.highlightsComplete = true
        } else {
            let readingList = "in.(\(readingIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
            graph.fires = try await withAuthRetry {
                try await self.client.select(
                    [FireRow].self, from: "fires",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }
            graph.fuelEvents = try await withAuthRetry {
                try await self.client.select(
                    [FuelEventRow].self, from: "fuel_events",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }
            if let notes = try? await withAuthRetry({
                try await self.client.select(
                    [NoteRow].self, from: "notes",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }) {
                graph.notes = notes
                graph.notesComplete = true
            }
            let noteIDs = graph.notes.map(\.id)
            if !noteIDs.isEmpty {
                let noteList = "in.(\(noteIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
                graph.noteFounds = (try? await withAuthRetry {
                    try await self.client.select(
                        [NoteFoundRow].self, from: "note_founds",
                        query: [URLQueryItem(name: "note_id", value: noteList)])
                }) ?? []
            }
            if let highlights = try? await withAuthRetry({
                try await self.client.select(
                    [HighlightRow].self, from: "highlights",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }) {
                graph.highlights = highlights
                graph.highlightsComplete = true
            }
            graph.ribbons = (try? await withAuthRetry {
                try await self.client.select(
                    [RibbonRow].self, from: "ribbons",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }) ?? []
            graph.positions = (try? await withAuthRetry {
                try await self.client.select(
                    [PositionRow].self, from: "positions",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }) ?? []
            graph.cards = (try? await withAuthRetry {
                try await self.client.select(
                    [CardRow].self, from: "cards",
                    query: [URLQueryItem(name: "reading_id", value: readingList)])
            }) ?? []
            let cardIDs = graph.cards.map(\.id)
            if !cardIDs.isEmpty {
                let cardList = "in.(\(cardIDs.map { $0.uuidString.lowercased() }.joined(separator: ",")))"
                graph.cardAnswers = (try? await withAuthRetry {
                    try await self.client.select(
                        [CardAnswerRow].self, from: "card_answers",
                        query: [URLQueryItem(name: "card_id", value: cardList)])
                }) ?? []
            }
        }
        return graph
    }

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
    }

    struct MembershipRow: Codable {
        var id: UUID
        var roomId: UUID
        var personId: UUID
        var ink: String?
        var joinedAt: Date
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
