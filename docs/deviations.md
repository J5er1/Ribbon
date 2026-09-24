# Deviations and open calls

The build book asks that every knowing departure be written down as one, so
a future reviewer can tell an intention from an accident. These are the
departures in the current build, with reasoning attached. Argue with the
reasoning.

## From the build book

1. **The app icon is the Wave, not the R.** — changed by the owner's
   call (September 2026). The build book's §12.1 icon was the Cesso R.;
   the shipped `AppIcon.png` is now the mark itself — the W6 Wave,
   chartreuse on the unlit ground, the same composition as
   `mark/ribbon-mark-on-black-square.svg` — regenerated any time by
   `make_icon()` in `tools/make_assets.py` from the mark's own path
   data, no font entitlement involved. The Cesso masters
   (`mark/ribbon-icon-cesso-2048.png`, `mark/ribbon-wordmark-cesso.png`,
   rendered under the project owner's Adobe Fonts entitlement, recipe in
   this ledger's history) remain in `mark/` as the brand's lettermark
   and wordmark for surfaces that want type, e.g. the web pages.

2. **In-app display type is Literata standing in for Cesso.** The icon
   and wordmark are settled (above), but *live text* in Cesso inside the
   app would mean bundling the font, which is a different Adobe license —
   exactly open question §16.12. Everywhere the book says "in Cesso"
   (book name on the room screen, the finishing line, an ember record)
   currently sets Literata at display sizes via `RibbonType.display`.

3. **The fire-scale boundary is ~750 words, not ~5,000.** §4.1's stated
   threshold contradicts its own example column (Philippians, Ruth and
   James — all under 2,500 WEB words — are listed medium; every listed
   small book is under 650). The examples govern: the boundary sits in the
   natural gap of the real distribution (Obadiah 619 → Titus 904) so that
   exactly the named books land small. See `FireScale` in
   `core/Sources/RibbonCore/Bible.swift` and its tests.

4. **Voice transcription is on-device at launch.** §13 says server-side at
   launch and flags on-device as open question §16.5. This build starts
   with `SFSpeechRecognizer` on-device: better privacy answer, zero
   marginal cost, works offline. If quality disappoints, a server
   transcriber slots in behind `Transcriber` unchanged.

5. **Highlight washes bleed and blend, but edges are only slightly
   irregular.** §4.5 asks for irregular top and bottom edges "so it reads
   as ink soaking into paper." The TextKit background pass draws rounded,
   jittered, bleeding washes with true multiply color for overlaps; the
   full irregular-edge treatment (per-run noise outlines) is a refinement
   pass on device, not a redesign.

6. **The note unfurl opens over 400 ms.** It opened instantly: S04 wants
   the line height to open over 400 ms, TextKit exclusion paths don't
   animate, and animating one per frame means setting the chapter again
   every frame. It is no longer a deviation (September 2026; I32). The page
   is laid out once with the carve, and the lines it moved are drawn from
   where they were, easing home — the drawing animates, not the layout.
   Android's carve, an inline placeholder, has animated all along. Still
   worth measuring on a device.

7. **Position restore lands on the verse.** It was chapter-anchored:
   reading reopened at the head of your chapter, and scroll-to-exact-verse
   was left for a device-tuning pass because `ScrollViewReader` needs
   laid-out geometry to aim at a verse's line. It is no longer a deviation
   (September 2026; A54, I30). Every way into the book now lands the
   verse's first line on the reading line. The chapter anchor had turned
   out to cost more than precision: the page measured itself at the
   chapter's head and saved what it found, so opening the book and closing
   it again was enough to lose the verse. What still wants a device is the
   aim itself: how far above the line the verse rests, and the one number
   iOS reads off its own first landing (I30).

8. **Word-boundary highlight extension is not built.** S06's "word
   boundaries when dragged slowly" — the current drag snaps to verse
   boundaries only, which is also the stated default.

9. **Following, live presence, and thinking-of-you are backed by
   `RoomChannel` on iOS and Android.** (September 2026; rebuilt September
   14th — see 20.) One authenticated WebSocket per room, to Phoenix
   Channels at `realtime:room:<room id>`, carrying presence (where each
   reader is, whether they have gone still, who they are following),
   `thinking_of_you` broadcasts that fire the haptic tap on the shoulder
   (`Haptics.tapOnTheShoulder()`), and a contentless `room_changed` nudge
   (20). A local `LocalPresenceService` remains the honest backend when
   the app is built without one. The UI states (reading quietly, idle,
   follow thread, being followed, follow-break on self-scroll, and
   back-to-where-you-were offer) are live.

9a. **Your own S12 is reachable only where your portrait renders** (an
   ember record's who-read-it row; the presence line shows others, not
   you). S01's "tap a portrait → S12" is wired everywhere a portrait
   appears in a navigation context — presence-line portraits, the
   last-reader line, ember records — but a room-of-one has no surface
   showing your own portrait until its first ember. Change-ink and
   leave-room therefore have no route in a fresh room of one. Accepted for
   now: change-ink only matters at three members (when the invitation row
   ships with phase two's ink transition), and leaving your only room is
   an edge the book itself routes through room closing (phase two).

10. **The full content sync engine is live on iOS and Android.**
    (September 2026.) Notes (written and voice memos), note founds,
    highlights, reading positions, and reflection cards now sync
    bi-directionally across PostgREST and Supabase Storage (`voice-notes`
    bucket audio upload and background download). Local-first offline
    mutations queue with hairline `isPending` state and automatically
    push upon reconnection. Deletions and "take back" propagate remotely.

11. **Phase-two surfaces remaining:** Cards (S08/S09) are now implemented on
    iOS and Android at the passage end. Notifications are delivered by push
    now (I33, A56), and the widgets and Live Activity are built (I34, A57).
    Remaining phase-two items: rooms of three-plus ink-transition moment
    (model supports it; the invitation row on S01 is not yet built),
    StoreKit (S22 shows the model's promise only). The web join is built
    (deviation 22).

12. **iPad is a considered surface now, one readable column wide.** The
    book designs phone screens; the target includes iPad (all
    orientations there; the phone stays portrait-only). The September
    2026 pass: Scripture and every full-bleed screen hold a centered
    readable measure (`readableColumn`, ~620 pt; the reading page 680) so
    lines stay lines and the way-in stays a button; the fire draws 1.45×
    in a regular-width room — the frame is still fixed by the book's
    scale, and relative sizes are untouched (Law 4 governs what a fire
    may *become*, not what canvas it's drawn for); small sheets are
    `.fitted` instead of iPad's empty form-sheet default; the reading
    position's "upper third" derives from the live viewport instead of a
    phone-sized constant; Esc closes the book and ⌘↩ leaves a note on a
    hardware keyboard; pointer hover responds on the room's controls; and
    the copy's "this phone" reads "this iPad" by device idiom. Every
    quiet control and both Waves out of the book now meet the 44 pt touch
    minimum — on iPad the way out was effectively Pencil-only, which is
    how the defect was found.

13. **Settings are one tap from the room, by the owner's call.** S18
    buries You two taps deep (room name → rooms sheet → You) on purpose;
    the owner overruled it. Your portrait sits in the room header,
    top-right, and opens You directly; the rooms sheet's You row stays.
    You also now carries the current room's own controls — name it,
    change your ink, leave — because a fresh room of one had no route to
    them at all (the second half of deviation 9a, now closed).

    *Superseded in shape by 14*: the two sheets became one menu, so there
    is no rooms sheet with a You row in it any more. The promise this
    entry records — your account and settings, one tap from the room —
    is unchanged.

14. **S14 and S18 are one full-screen menu, and it has two doors the book
    never gave it.** — changed by the owner's call (September 2026). The
    book presents the rooms as a sheet from the room's name (S14) and
    puts You behind it (S18); deviation 13 had already pulled You out to
    a second sheet, off the room's portrait. What shipped was therefore
    two half-height sheets, each an unlabelled pile of controls, and
    between them two things a person could plainly want to do and simply
    could not:

    - **Accept an invite to a second room.** The link is the whole
      mechanism (S15) and a tapped link runs S16 from anywhere — but the
      *paste* field that catches a link which landed somewhere this phone
      can't tap it from lived on a page of onboarding, which nobody sees
      twice. Once you had a room, an invite in an email on a laptop had
      nowhere to go. `AppModel.inviteToken(fromPasted:)` existed and had
      exactly one caller.
    - **Invite anyone to a room of two.** The room screen offers the link
      only while a room of *one* still has its first invite out (S01's
      "The invite is still out."), and the invite sheet otherwise appears
      only in the seconds after a room is made (S15). A room holds six;
      there was no way from two to three.

    Both are now rows in the menu — *Join with an invite* under the
    rooms, *Invite someone* under the room you are in — and the join
    pushes the same S16 `JoinFlow` a tapped link runs, inside the menu,
    so nothing is handed across two presentations.

    The screen itself is full height (`.fullScreenCover`; on Android a
    layer over the room's stack with its own predictive back) and carries
    four named sections — **Rooms**, **This room**, **You**, **Account**
    — each under a small-caps head with a hairline beneath it. Emphasis
    is the hierarchy: ivory 17 pt rows for what you go to or do, quiet
    muted small caps for what undoes (leave a room, sign out, delete an
    account). The room's two header controls still ask two different
    questions and still answer in one tap — the name opens the menu at
    the top, your portrait opens it already scrolled to You — so
    deviation 13's promise is intact.

    Three things are added to a room's row that the book does not list:
    the name of the book that room is reading, `selected` state for a
    screen reader, and the fire's own state spoken beside the glyph. The
    first is an address, not a score (Law 2), and with more than one room
    it is the thing that tells two small fires apart. The second and
    third are both §11's rule that colour is never the only signal — the
    chartreuse hairline for which room you are in, and the fire itself,
    which hides its own semantics everywhere else because it always sits
    beside the words it illustrates, and here does not. Everything else S14 asks for is unchanged:
    tap a room to switch, no swipe on a row, no folders, no reordering,
    a paused room's fire drawn in the state it actually holds.

15. **What the menu's own review turned up, and what it left alone.**
    Five things an adversarial pass over 14 found and this build fixes,
    recorded because two of them are older than the menu and one was a
    regression the menu itself introduced:

    - **The pushed settings screens lost their top inset** when they
      stopped living inside a bottom sheet — the sheet had been clearing
      the status bar for them, and `SettingsScroll`'s own comment still
      said so. Each screen's first drawn thing is its back chevron, at
      y = 8..52 dp, under a status bar 24–48 dp tall. Fixed, and the
      comment corrected. (Android only; iOS's `NavigationStack` clears it.)
    - **S22's "Start the room again" was a chartreuse capsule with an
      empty body** on both platforms — a control that says exactly what
      happens and then does not do it, which is worse than no control and
      reads as a failure the app never names. The restore half needs
      billing (deviation 11), so the paused room now says the true thing
      it already has copy for (`roomPaused`) and offers the other half of
      S22's own anatomy, `manageInStore` — both of which had been sitting
      in `Copy` with zero call sites on either platform.
    - **Nothing in either app was announced as a heading.** The menu's
      four section heads and the six group labels on the pushed screens
      are drawn as heads and were read as plain text, so a rotor or a
      heading swipe had nothing to land on and reaching Account meant
      swiping past every room. `grep isHeader|heading()` returned nothing
      across both apps before this.
    - **Deleting your account left the menu standing** on iOS, over the
      fresh room the app makes next. It dismisses first now, the way
      leaving a room does, and `RootView` also clears the menu whenever
      the person goes — Android's menu is inside the branch that swaps
      out, so it had this structurally.
    - **Two controls said the wrong thing about themselves**: the
      portrait went on offering to "add a portrait" to somebody who had
      one, and your own name had no minimum tap width and no hint. Both
      are now conditional and 44 pt/dp in both directions.

    Deliberately left alone, all of them older than this change and none
    of them the menu's: ink is unreachable below three members and
    nothing assigns one when a room reaches three (so §4.5's "ink is
    identity" arrives silently unenforced, and
    `inksFromWhenTheRoomWasTwo` is still dead copy); presence never
    populates, so reading quietly, following and thinking-of-you have no
    reachable control (deviation 9); and the notification switches write
    preferences nothing reads (deviation 11). Each is a piece of work,
    not a menu bug.

16. **Onboarding has a third door: sign in — S17 and §6.10 disagreed,
    and §6.10 won.** — noticed by the owner (September 2026). S17's
    sequence is "start or accept", and both of its answers mint a *new*
    person. §6.10 says something the thread had no way to honour: "New
    device: sign in, rooms restore." There was no door at the one moment
    a person needs it — a second phone, or a reinstall the
    Keychain/keystore did not outlive. The only route back to your own
    rooms was to finish onboarding as a stranger, land in a stray room of
    one, and find *Sign in* at the bottom of the menu — which then
    adopted the account's id and pushed the stray room to it.

    So the who-step now offers *Sign in* as a third, quiet answer (absent
    when the build has no backend — no dead control), and
    `verifySignInCode` handles the case it could not before: with no local
    person, the account's own profile *becomes* the person
    (`restorePerson`), its rooms arrive with the pull, and onboarding is
    over without a name ever being asked for. An account with no profile
    — an email verified and abandoned — still falls through to the name
    step, and `completeOnboarding` already gives that person the auth id
    rather than a fresh one.

    What this does **not** fix, and what is still true of the identity
    model, all of it §6.10's and none of it new here:

    - ~~**A changed face never travels.**~~ Fixed below (17).
    - ~~**Re-invited is only half re-attached.**~~ Fixed below (18).
    - ~~**Sign-in is an emailed code only.**~~ The client half is built
      below (19); the project's switch is not ours to throw.
    - **A second device can still make a stray self.** Onboarding *before*
      signing in mints a local person and a room of one, and the sign-in
      that follows adopts the account's id and pushes that room to it. One
      email is still one account — `profiles.id` references
      `auth.users(id)` — but the local-first seam lets more than one local
      self reach it. The door above makes this avoidable, not impossible.

17. **A changed face travels, by asking the object rather than the row.**
    Deviation 10's first honest edge, closed. A portrait reached a device
    once and only once — `merge` fetched it when the device had nothing,
    and never again — so everyone who had already seen your old face kept
    it forever, and your own second phone did too. The remote object is
    `<person id>.jpg` and never renames, so nothing in the `profiles` row
    can say the bytes behind it changed.

    The object's own tag can. `downloadPortrait` now offers the ETag this
    device stored (`AppState.portraitETags`, persisted, so a relaunch
    re-downloads nothing) and reads three answers: 304 means the face is
    still the face, 404 means there is none, and 200 carries the new bytes
    and the new tag. Both clients switch caching off for that one request,
    because URLSession and HttpURLConnection would each answer it out of
    their own cache and hide the 304 the whole mechanism turns on.

    Two bounds keep it quiet. A face is taken on trust for 15 minutes
    before it is asked about again (a room holds six; this is a handful of
    empty responses an hour), and setting your own portrait starts that
    window on this device, so an upload still in flight is never overtaken
    by a fetch of the face it is replacing. No schema change, no migration,
    and a network failure reads as "unchanged" — the device keeps the face
    it has, which is what it would have done anyway.

18. **An ink outlives the membership it was chosen on.** §6.10's "lost
    access entirely" asks that a re-invited person's ink *and* notes
    reattach rather than duplicating. Notes always did: they are keyed by
    the author's id, which is the account's. Ink could not — it lives on
    the membership row on purpose (teal in one room, ochre in another,
    §4.5) and leaving deletes that row, so `accept_invite` seated you
    again with no colour. In a room of three or more, where ink *is*
    identity, that is a stranger arriving rather than the same person
    coming back.

    `supabase/migrations/20260907170000_ribbon_ink_outlives_membership.sql`
    adds `room_inks` — one row per person per room, keyed to `profiles`
    rather than to `memberships` precisely so it is there when the
    membership is not. Its RLS is `person_id = auth.uid()` in both
    directions: which ink someone once chose in a room they have left is
    not a thing the room gets to know, and the current ink of everyone
    present is already on their membership where the room can see it.

    The membership stays the truth for the *current* ink and every screen
    still reads it; this is only the memory of what was chosen. `pickInk`
    writes it, and `joinRoom` reads it after the pull and puts it back on —
    never over somebody else, so a colour taken in the meantime stays taken
    and the room asks for a new one the way it always would. Both writes
    are best-effort: a build talking to a project without the table yet
    behaves exactly as it did before, which is what makes it safe to ship
    the client before the migration is applied.

19. **Passkeys, the client half.** §6.10 asks for "a passkey where
    available, an emailed code otherwise". Only the code half existed.
    Supabase Auth now ships WebAuthn itself, and its two-step API is
    built for exactly this case — the server hands out a challenge and
    the W3C options, the platform runs the ceremony, the signed result
    goes back — so no edge function and no credential table of our own.

    Both clients call `/auth/v1/passkeys/{registration,authentication}/
    {options,verify}` directly, as they call everything else, and pass the
    options and the credential through as JSON without reading either.
    That is the point: those are the W3C shapes, they belong to the
    platform, and anything this code understood about them would only be
    a second place for them to be wrong. iOS reads the four fields
    `AuthenticationServices` needs and re-encodes the result;
    CredentialManager takes and returns the JSON verbatim, which is why
    the Android file is a third the length.

    A passkey is offered, never imposed. `Use a passkey` sits above the
    email field in the sign-in thread and `Add a passkey` under the
    account in the menu, both absent where there is no backend; a
    dismissed sheet says nothing at all, because declining a passkey is a
    person choosing the other way in, not a failure (§25). Sign-in is
    discoverable, so a new phone asks for nothing — not even an email —
    and lands in the same `restorePerson` thread the emailed code does
    (15).

    **It is off, and it cannot work until three things are done.** None
    of them can be done from the repo:

    1. **Turn passkeys on for the project.** `/auth/v1/settings` on
       `noyccfkaotuvhhaoccck` reports `passkeys_enabled: false`, and the
       options endpoint answers `passkey_disabled` — so the surface is
       there and the switch is off. It wants `passkey_enabled: true`,
       `webauthn_rp_id: readribbon.app`, a display name, and
       `webauthn_rp_origins` including the Android app origin
       (`android:apk-key-hash:<base64url SHA-256 of the signing cert>`).
       The RP ID is bound into every passkey ever made against it and
       cannot be changed later without invalidating all of them.
    2. **Serve the two association files.** `web/build.mjs` now emits a
       `webcredentials` section in the apple-app-site-association beside
       the existing `applinks`, and a `.well-known/assetlinks.json`
       carrying `delegate_permission/common.get_login_creds`. Both are
       templated: the first still needs `RIBBON_APPLE_TEAM_ID`, the
       second needs a new `RIBBON_ANDROID_CERT_SHA256`, and both warn at
       build time while they hold a placeholder.
    3. **Sign the Android app with a stable key.** There is none yet — CI
       signs each debug build with a throwaway key — so there is no
       fingerprint to put in `assetlinks.json` and no app origin to
       allow. Android passkeys are blocked on the release signing key,
       not on this code.

    Until then both controls are simply absent (`passkeysAvailable` is
    false without a backend) or answer `passkey_disabled`, and the
    emailed code is the way in exactly as before. **None of this thread
    has been exercised**: there is no device here, the project has the
    feature off, and the two `…/verify` paths are the documented
    `…/options` paths' siblings rather than paths the docs spell out. The
    first real run is the test.

20. **The room is live while you are looking at it, and the socket is
    the room's rather than the book's.** (September 2026.) The first cut
    of the Realtime client was joined from the reading screen only, so a
    room learned nothing until its next foreground: you could sit on S01
    while somebody accepted your invite, left a note and fed the fire,
    and see none of it. It also could not survive its own network: the
    reconnect path called `join(room:person:)`, whose first line returns
    early when the room is unchanged and the socket non-nil — which it
    always was, because the failed socket was still held. One dropped
    connection ended presence for the session, silently. And the two
    platforms never saw each other at all: Android keyed its topic and
    its presence ids off `Uuid.toHexString()`, which drops the dashes,
    so `realtime:room:1111…` and `realtime:room:11111111-2222-…` were two
    different rooms.

    `RoomChannel` (both platforms) replaces it:

    - **The channel is the room's.** It is open whenever the app is
      foregrounded on a room, and *presence is not announced by opening
      it* — `present(…)` says you are in the book, `withdraw()` says you
      are not, and the line stays up either way. Reading quietly still
      announces nothing at all.
    - **It is private.** The account's access token goes up with the
      join and Realtime checks it against `realtime.messages` RLS
      (`20260914120000_ribbon_realtime_room_channel.sql`), so a room you
      are not in refuses you. Before this the channel carried only the
      publishable key that ships inside the app: a stranger who guessed a
      room id could have watched its presence. Because the policies live
      in a migration that a given project may not have yet, a refused
      private join downgrades that connection to a public one and logs
      why, rather than leaving the room dead.
    - **A contentless change nudge.** Every remote write the room renders
      from goes through one seam (`pushing`) that pushes and then
      broadcasts `room_changed` — the room id and nothing else. The other
      phones coalesce it into one pull ~600 ms later. The database stays
      the only copy of the truth; positions and note-founds deliberately
      do not nudge (a position already rides presence, and who found a
      note is the one thing the room is never told, §6.3).
    - **It reconnects.** Exponential backoff with jitter, a heartbeat
      whose unanswered beats are themselves a failure signal, a
      generation counter so a dead socket cannot speak for its
      replacement, and a re-`track` on rejoin so a reader is not quietly
      withdrawn from a book they never left.
    - **Idle and following now travel.** §4.2's "here, but still" and
      "Ruth is with you" were both written and both dead: nothing ever
      set `isIdle`, and `followingPersonID` was sent as null on iOS and
      omitted on Android. The channel watches for ~4 minutes without
      movement, and following is carried in the presence meta.

    The wire format is the contract between the two builds, so it is one
    object (`RoomChannelWire`) with `RoomChannelWireTest` asserting every
    shape exactly; the Swift builds the same messages by hand beside a
    note pointing at it. **Not exercised against the live project**: this
    is a socket, there are no two phones here, and the migration has not
    been applied. The first real run is the test.

21. **Joining goes through the app's one sign-in thread.** S16's join
    screen carried its own email-and-code pair, straight to Supabase's
    own GoTrue — while every other surface offers `SignInInline`, which
    leads with Auth0 where the build has it (it does). A joiner therefore
    signed in by a different door from everybody else in the room, and
    got a different *kind* of account id for it: `public.current_user_id()`
    resolves a native Supabase subject to itself and an Auth0 subject to
    a UUIDv5 of it, so the two are not the same person and never become
    one. Both join screens now host `SignInInline` at that step.

22. **Joining in the browser (S16's web half).** "The link opens the web
    version of the same screen, and you can join and read immediately, in
    the browser, without installing anything." The invite page did the
    first line of that and stopped: who is inviting, and a button into an
    app the person did not have. It is the whole join now, the app's steps
    in the app's words — Join; an emailed code; a name and, if they like,
    a face; the room — and S16's states: expired, full, already a member
    (straight in), and signed in as someone else, which is offered as a
    choice ("You'll join as Ruth." / "Join as someone else") and never
    joined silently. The room it lands in is named as the app names it,
    shows the book, and has one way in: the book, opened at the room's
    ribbon. The app is offered underneath, once per browser, and never
    again.

    - **A face waits for the room.** The invite shows the inviter's
      initial, not their portrait: a link can be forwarded, and a face is
      the room's to see, which is what the portraits bucket's policy
      already says. The joiner's own face goes up with their name.
    - **The name comes before the seat.** A membership names a profile, so
      the person is written first and the invite accepted after — the
      order S16 gives anyway ("Join. Then name and portrait. Then you're in
      the room.").
    - **The browser signs in with the emailed code**, the thread that works
      everywhere, and so a browser joiner's account is the emailed-code
      kind. In the app, the same person is the same person by the same
      door: the emailed code, with the same address. The browser sign-in
      the app leads with (Auth0, deviation 21) would make them someone else,
      and the fix is on Auth0's side — a web application for readribbon.app
      — not the page's. It is on the list in `docs/still-to-do.md`.
    - **Reading is the pages that were already there**, pre-rendered and
      readable before any script runs. Presence, notes and the fire from a
      browser are §15's phase four ("full web reading"); phase one's web
      reading is the invite path's, which this is.
    - `web/invite.js`; every state is driven in a headless browser against
      a mocked backend, and the preview against the live one.

## Android (phase three)

The Android build is a second implementation of one product, not a second
product. These are its knowing departures — from the build book where it
names Android (§12.2), and from the iOS app where the two platforms could
not honestly be made identical. Everything §12.2 asks for that is *not*
listed here is simply built: Expressive shapes and damped spring physics,
no FAB, no bottom navigation, no visible loading, predictive back,
mandatory edge-to-edge, and Alegreya Sans SC as a real small-caps face
rather than a textTransform. Dynamic colour was declined and is now
taken — A18 — which is the largest single departure in this list.

A1. **minSdk is 33, not the literal 36.** §12.2 reads "Target: Android 16+
    (API 36)". The app compiles against and targets exactly that, but taken
    as an *install floor* it would ship to almost no phones.

    33 rather than lower, and the reason is §11 rather than convenience.
    Everything §12.2 asks for is already present at Android 12 — the
    Expressive shape and motion systems, edge-to-edge,
    `VibrationEffect.Composition` for the thinking-of-you tap — so 31 was
    the first floor this build used. But transcripts are mandatory, not a
    setting, and Android cannot transcribe a recorded file below API 33:
    `RecognizerIntent.EXTRA_AUDIO_SOURCE` is the only route from a
    finished recording to a transcript, and it arrived there. Shipping to
    Android 12 would have meant shipping a voice note no deaf member can
    read and nobody can find again in six months, on a whole OS version,
    with nothing in the interface admitting it. The floor moved instead
    (owner's call, September 2026). Predictive back is real at 33 too,
    which was the other loss at 31.

    `compileSdk` is 37 — not a product call, just the floor the current
    AndroidX libraries impose; `targetSdk` stays at the 36 the book pins,
    because that is the number that decides runtime behaviour.

A2. **RibbonCore is ported to Kotlin, not shared.** The alternative was
    Kotlin Multiplatform with one source set for both apps, which is the
    better long-run answer and a much larger change: it rewrites the iOS
    app's dependency on a package that is currently working and tested.
    The cost of a port is drift, so the port carries its own guard — all
    five Swift test suites are ported case-for-case, 35 cases with the same
    names, inputs and expected values, and both run in CI. If the fire
    curve or the ~750-word scale boundary ever diverges, a red build is
    where it surfaces rather than a couple's two phones disagreeing about
    their fire.

A3. **Scripture, the fonts and the grain are not copied into the repo a
    second time.** They live once under `ios/Ribbon/Resources` and a Gradle
    task syncs them into the Android assets at build time. Two copies of
    11 MB of Scripture would be two things to keep in step, and the second
    one would eventually be the stale one. The same reasoning extends to
    the Wave: `tools/make_assets.py` already owned the mark's path data,
    and now emits the Kotlin geometry and the Android adaptive icon from
    the same two paths, so neither app hand-copies the mark.

A4. **The launcher icon is a vector, and its monochrome slot is a
    different drawing.** The iOS icon is a rendered PNG; Android's adaptive
    icon is drawn from the mark's paths, so it stays crisp at every
    density. The knockout is the same erase stroke the SVG mask does —
    occlusion, not transparency — expressed as a ground-coloured stroke
    between the two fills. The themed-icon monochrome slot cannot use that
    trick, because the launcher tints the whole drawable: there the
    knockout is a real hole.

A5. **The ripple is replaced, not configured.** §12.2 asks for "a soft
    state layer at low opacity" and no bounded ripple over Scripture. A
    ripple with its radius turned down is still a ripple, and it still
    animates outward from the touch point, so this is a custom indication
    that draws a flat wash and never expands. Pressed 6%, focused 10%,
    hovered 4%.

A6. **Reduce motion is read from the animator duration scale.** Android
    has no single switch equivalent to iOS's Reduce Motion; turning
    animations off in accessibility or developer settings sets
    `ANIMATOR_DURATION_SCALE` to zero, and that is the signal a
    well-behaved app reads. §11 then applies exactly as on iOS: the fire
    holds a state instead of flickering, morphs become cross-fades, and the
    thinking-of-you fill becomes an instant state change with the haptic
    intact.

A7. **Session tokens are sealed with a hand-rolled keystore key.** iOS
    uses the Keychain, on the grounds that tokens are credentials rather
    than state. The Android equivalent would have been
    `EncryptedSharedPreferences`, but `androidx.security.crypto` is
    deprecated and on its way out, so the app does directly what that
    library wrapped: an AES-GCM key generated in the hardware-backed
    keystore, with only ciphertext in a preference file, excluded from
    backup and device transfer.

    **A real behavioural difference falls out of this**, and it is the good
    direction: a keystore key does not survive a reinstall, so on Android a
    reinstall signs you out. On iOS the Keychain outlives the app, which is
    why `adoptRemoteIdentity` exists — a reinstalled iPhone can be signed
    in with empty local state and must not mint a second person. Android
    never hits that particular seam; the ordinary sign-in path still needs
    the identity adoption, and it is ported.

A8. **"This phone" follows the hardware, not the window.** The copy's
    concrete noun is wrong on a tablet, so iOS switches it on interface
    idiom. Android has no idiom; the closest honest test is
    `smallestScreenWidthDp >= 600`, which is a property of the device
    rather than of the current window — so a phone in a freeform window is
    still a phone and a tablet in a narrow split is still a tablet, which
    is the behaviour idiom gives on iOS.

A9. **`QuietDay.bankedInterval` is stricter than Foundation's.** Given a
    corrupt stored date like `2024-13-01`, Foundation's lenient `Calendar`
    rolls it over to January 2025 and banks *that* day; kotlinx-datetime
    refuses, and the Kotlin returns null. Banking nothing is the better
    failure — a day that was never marked must not bank another day's
    hours — so the Kotlin is left as it is and the difference is recorded
    rather than reproduced.

A10. **`Handiwork` exposes as `var` what Swift marks `private(set)`.**
    Kotlin cannot attach a private setter to a primary-constructor
    property, and moving the fields into the class body would break both
    documented construction shapes and the `@Serializable` constructor. The
    invariants the Swift type enforces are therefore convention here, not
    compilation. Relatedly, collapsing Swift's two initialisers into one
    constructor means the normalising (`coalDepth` clamped, `banked`
    reduced to `catching`) also runs on the decode path, where Swift's
    synthesized `init(from:)` skips it — strictly *more* of the invariant
    the Swift documents, never less.

A11. **`ReflectionCard.answers` round-tripping resolved across platforms.**
    *(Resolved, September 2026).* Swift's default `JSONEncoder` originally
    wrote a dictionary with a non-String key (`[UUID: String]`) as a flat
    alternating array, whereas `kotlinx-serialization` writes a JSON object
    keyed by UUID string primitives. `ReflectionCard` in `RibbonCore` now uses
    custom `Codable` serialization that encodes `answers` as a string-keyed JSON
    object and decodes from either a JSON object or an alternating array,
    guaranteeing seamless round-tripping across iOS, Android, and PostgREST.

A12. **Transcription is on-device, and that is what set the floor.**
    Deviation 4 holds on both platforms: on Android it is
    `SpeechRecognizer.createOnDeviceSpeechRecognizer`, so the audio never
    leaves the phone.

    Android's recognizer listens to a *stream*, not to a file. The only
    way to hand it a finished recording is
    `RecognizerIntent.EXTRA_AUDIO_SOURCE`, which arrived in API 33 — so
    below that there is no route from a recorded note to a transcript at
    all. This build first shipped at minSdk 31 and rendered the honest
    failure ("No transcript for this one." with Try again) on Android 12
    and 12L, which was recorded here as an open question. It is closed:
    the floor moved to 33 (A1) rather than let a whole OS version take
    voice notes it could never transcribe. §11 does not treat a transcript
    as optional, and neither of the alternatives was better — transcribing
    live off the microphone puts two consumers on it at once, which is
    unreliable on real devices, and a server fallback would have spent the
    privacy answer to reach phones that are a small and shrinking share.

    Two mechanics survive that history and are worth knowing. The note is
    AAC in MPEG-4 and the recognizer wants raw 16-bit PCM, so it is
    decoded through MediaExtractor and MediaCodec into a cache scratch
    file that is deleted the moment recognition ends — a plain file rather
    than a pipe, because a pipe would block whenever the recognizer
    stopped reading. And the session runs under a time ceiling, because
    nothing in Android's contract promises a terminal callback for a
    session fed from a file, and a silent hang should become the same
    failure the caller already renders.

A13. **Four substitutions in the state spine, none of them behavioural.**
    `AppModel` is a `ViewModel` and Swift's `@Observable` becomes Compose
    snapshot state, which is the nearest equivalent and lets a composable
    read a field directly. Four details are worth knowing before reading
    the file:

    - `state` uses `neverEqualPolicy()`. `Handiwork` is a struct in Swift
      and a mutable class in the Kotlin core, so feeding a fire mutates an
      object that both the old and the new `AppState` point at. Under the
      default structural equality the two would compare equal and a
      feeding would never reach the screen.
    - `Reading.snapshot()` deep-copies the handiwork before a queued push,
      so the row that lands describes the fire as it was when the push was
      made — which is what Swift gets for free by capturing a struct.
    - `persist()` runs on a scope that is deliberately never cancelled,
      standing in for Swift's `Task.detached`, so a save started as the app
      goes away is not killed by the ViewModel clearing.
    - `RemoteSync` is main-thread-confined by convention rather than by
      `@MainActor`, and its keystore reads and writes are moved to the IO
      dispatcher — keystore crypto on Android's main thread is a StrictMode
      violation waiting to happen, where the Keychain calls it replaces are
      synchronous on the main actor.

    One shared-backend note that looks like a Kotlin artifact and is not:
    both clients omit a null optional from an upsert body rather than
    sending JSON null (Swift's synthesized `encodeIfPresent`,
    kotlinx-serialization's `explicitNulls = false`). So on *both*
    platforms an upsert never clears one of those columns back to null —
    removing a portrait locally does not blank `profiles.portrait_path`
    remotely. That is a real gap in the sync surface, it predates this
    port, and it is written down here because this is where it was noticed.

A14. **The note's carve is an inline placeholder, and it closes iOS
    deviation 6.** TextKit opens a hole in a page with an exclusion path,
    which is why the iOS build's unfurl appears instantly — exclusion paths
    do not animate, and animating them per frame during layout is jank.
    Compose has no exclusion path at all, but it has inline placeholders:
    the gap is a sized box inside the text itself, so the text reflows
    around a real hole *and the hole's height can animate*. S04's 400 ms
    open is therefore literal here and approximate on iOS.

    The cost is honest and worth naming: a placeholder breaks the
    paragraph, so the verse's last line ends at the carve rather than
    setting beside it. TextKit would have flowed the text around three
    sides of the hole.

    Two smaller ones in the same file. Compose's ParagraphStyle has no
    paragraph spacing, so §S02's stanza break and psalm-descriptor spacing
    are set as short spacer paragraphs of the exact measured height — same
    result, different mechanism. And the long-press threshold is the
    platform's own 500 ms rather than iOS's 450 ms, because a reader's
    muscle memory belongs to their phone, not to our number.

A15. **Three things the fire says differently, none of which change the
    picture.** `.plusLighter` becomes a per-draw `BlendMode.Plus`, because
    a Compose DrawScope has no mutable blend mode the way a SwiftUI
    GraphicsContext does. SwiftUI's layer blur — used on the flame sheath
    and the hot air — becomes a Skia mask filter on each path: Gaussian
    blur is linear and additive blending commutes, so blurring each tongue
    and adding is the same picture as adding and blurring the group, but a
    mask filter blurs the shape's coverage rather than its filled pixels,
    and Skia derives its sigma from the radius, so the softness is faithful
    in kind rather than to the pixel. The Canvas composites offscreen, which
    reproduces the two properties of a SwiftUI Canvas the additive passes
    actually depend on: accumulation happens in the fire's own buffer rather
    than over the room's grain, and the warm throw is clipped at the edge.

    One test differs on purpose. The fire draws larger in a "regular width"
    room; on Android that is measured from the *window* (600 dp, the
    Material medium breakpoint), not from the device — a tablet holding
    Ribbon in a narrow split pane is a small room. That is the opposite of
    A8's device noun, and deliberately so: the copy is about the hardware
    in your hand, the drawing is about the room it is drawn in.

A16. **The ember grows, but it is not the same object travelling.** S11's
    fire → ember record is the one place a system morph is exactly the
    right metaphor, and iOS gets it from a zoom navigation transition tied
    to the tapped view. Here the record's ember arrives at the shelf
    ember's drawn size and settles up to full size on the settle curve. It
    reads as the ember growing, and under reduce motion it is simply drawn
    at full size — but it plays on any entry to the record, not only on a
    tap from the shelf, because a true shared-element morph would have to
    be owned by the navigation host rather than by the screen.

A17. **The menu is a layer over the room, not a cover.** iOS presents the
    menu (deviation 14) with `.fullScreenCover`, which is a presentation of
    its own with a dismiss of its own. The nearest true thing here is what
    the book already is: a full-size layer drawn over the room's stack, in
    the same `Box`, composed last so its own predictive back is taken
    first. It arrives from the bottom of the screen and leaves the same
    way, which is what a cover does; under reduce motion both are a cut.
    Two consequences, both deliberate. The back gesture closes the menu,
    and peels the room in behind it exactly as it peels the book — iOS has
    no equivalent, and draws only the `Close` control, which Android draws
    too. And the menu's four settings screens plus its join live in a
    `NavHost` of the menu's own, registered *after* the close handler, so
    back pops a pushed screen first and only an unpushed menu closes.

A18. **Material You is on, unharmonised, and §12.2's refusal of it is
    overturned.** — the owner's call, September 2026. The build book is
    unusually direct about this one: "Ribbon opts out of dynamic color …
    a Ribbon tinted lavender because someone's wallpaper is lavender is
    not Ribbon. Ship a fixed color scheme and set `isDynamicColor = false`
    explicitly rather than by omission, so nobody turns it on later
    thinking it was an oversight." Nobody turned it on thinking it was an
    oversight. It was turned on deliberately, and the reasoning it was
    weighed against is worth keeping rather than quietly deleting: the
    argument for a fixed palette is an argument about the brand, and the
    argument against it is an argument about the person holding the phone.
    Material You is most of what the Android build was *for*, and a room
    that takes its colour from the wallpaper is a room in their house.

    **Unharmonised**, which was also the owner's call over the alternative
    of blending each role back toward chartreuse. The six room roles are
    read straight off `dynamicDarkColorScheme` with no correction:
    `surfaceContainerLowest` is the ground, an ordinary container is a
    card, a high container is a chip, `onSurface`/`onSurfaceVariant` are
    the two inks, `outlineVariant` is the rule, `primary` is the accent.
    A half-dynamic palette would look like neither thing.

    **Two things never move, and it is the same reason twice: they are
    objects in the room rather than the room itself.** The fire — §4.1's
    "single warm object", whose warmth is the product and not chrome; a
    blue fire is not a fire, and a warm fire in a cool room is a better
    picture than either alone. And the eight inks, which are identity
    (§4.5): repainting a highlight from a wallpaper would change whose it
    was. Smoke and ash go with the fire, because `FirePainter` is a pure
    function of a clock rather than a composition and cannot read a
    composition local anyway.

    **The way back is one switch**, in a new screen — Appearance, S26 —
    which is the only thing S18's "not here" list gains. Its "no accent
    picker: chartreuse is the brand's, not the user's" survives intact:
    the switch offers the wallpaper's colours or the brand's, and never a
    colour anyone chose by hand. It lives in its own two-value preference
    file rather than in `AppSettings`, because `AppState` is read off disk
    asynchronously and a theme that waited for it would paint one palette
    on the first frame and another a moment later — a repaint on the front
    door is exactly the instability S01 forbids.

    **The one correction that is made**, and it is not a matter of taste:
    a card has to be distinguishable from the ground, and some extracted
    schemes put their lowest and ordinary containers within a hair of each
    other. `ColorScheme.asRoom` takes the first tonal step that can
    actually be seen against the ground, and where no step can, the card
    draws a 1 dp edge in the palette's own `rule` instead of pretending a
    fill it does not have. Ribbon's own palette is permanently in that
    second case *by design* — `Brand.surface` on `Brand.ground` is 1.05:1
    and `Brand.raised` is 1.13:1, which is correct for a room meant to be
    nearly flat and useless as a card — so with the wallpaper declined a
    card is a *drawn* card rather than a filled one. That is the more
    bookish of the two answers anyway.

    Mechanically: `Palette`'s room roles became `@Composable` getters over
    `LocalRoomColours`. That was chosen over a plain rename so that the
    compiler would find every colour read outside a composition, which is
    every colour that could not have followed the wallpaper — it found
    eleven, all of them in draw lambdas, and each is now hoisted or passed.

A19. **The room is a hearth, not a column.** — the owner's call. S01's
    anatomy is unchanged in content and rearranged in kind: the room held
    everything the book asks for and read, in the owner's words, as barren,
    worst in the two states a new person actually sees. The diagnosis was
    not "too little on the screen"; it was that nothing on the screen was
    *grouped*, so six objects floating in a column looked like six objects
    rather than like a place.

    What changed. The page **greets you** by name, once, in the display
    face — three variants, first name, a full stop, no second sentence,
    and §12's rules are doing real work: an exclamation point, an emoji or
    a verse of the day would each make it the church bulletin §13 refuses.
    Presence became **a sentence you can read** — `personIsReading`,
    `personIsHereButStill` and `alsoHere` had existed since the port with
    no visible call site at all, so the warmest copy in the product was
    audible only to a screen reader. The fire, the book's name, its state
    and the way in became **one raised object** with the room's people
    seated around it, the fire standing in a recess of the room's own
    unlit ground with a hairline under it. And the **empty states carry
    something true**: first run offers the five books the chooser already
    calls good places to start, and a room still expecting somebody shows
    an open seat.

    Two departures from S01's letter, both deliberate. The seats draw
    **membership**, with presence as a ring around a seat (a whole ring
    reading, a half ring here-but-still) where the book says "portraits of
    whoever is in the book right now" — because the room of one that the
    book's version draws nothing at all for is exactly the barren case.
    And the seats are capped at six with no names, no overflow marker and
    nothing counted, in membership order, because the thing this must
    never become is the avatar row of a social network (§3).

    The **open seat is scoped to somebody actually being expected** — a
    room of one, or a live invite — and not to `members.size < capacity`.
    A couple with no intention of being three would otherwise be shown
    four empty chairs on their own front door, forever, with nobody asked:
    an empty state drawn as an object, and a reproach.

    Law 2 is untouched. Nothing here counts anything: not the people, not
    the notes, not the shelf, and nothing anywhere near the fire.

    One thing was tried and cut: a soft wash of the fire's own light on the
    floor beneath it. Two alpha falloffs under the hero object is a
    gradient by construction and a glow behind the one thing §7 and §13
    protect hardest. The recess and the hairline do the same work with no
    invented light source.

A20. **The fire opens the book, and the Wave closes it — by hand.** —
    the owner's call. S01 says "tap fire → nothing (deliberately inert; it
    is an object, not a button)". It is now the way in: take hold of it and
    pull, and the book rises under your thumb. The reasoning against was
    that a fire is an object rather than a control; the reasoning for is
    that a hearth is an object you can reach into, and a drag is not a
    button. The Wave at the foot of the book is the same handle in reverse.

    §11's rule that no way in or out of the book may be a gesture only is
    kept twice over: the fire carries a custom click action, the way-in
    capsule under it is unchanged, and the Wave's tap is exactly the tap it
    has been since S02 was written.

    What makes this more than a gesture bolted onto a transition: the book
    is no longer a transition at all. It used to arrive by fading in and
    leave by sliding out, as two unrelated `AnimatedContent` specs — which
    is why opening it and closing it never looked like one thing happening
    twice. There is now a single number (`BookSheet.progress`) that the
    page's offset, the room's recession behind it, and the hearth riding up
    under the finger all read, and that the drag and every tap drive
    alike. A gesture and an animation made of the same value cannot fall
    out of step.

    The room recedes under the rising book using exactly the numbers
    predictive back already uses to peel a screen *off* the room, run the
    other way — so opening the book and closing it are plainly the same
    movement, which is what a gesture has to be if it is going to be
    believed. Reduce motion (§11): both gestures still work and still open
    and close the book; the number jumps between its ends instead of
    travelling.

A21. **Springs, where a finger is involved.** §12.2 asks for "physics-based
    motion … damped, with damping near critical", and until now every
    token in `RibbonMotion` was a tween. A tween is right for a thing that
    simply changes — a word swapping under the fire, a cross-dissolve
    between rooms. It is wrong for a thing a finger is holding, because a
    drag has a velocity when it is let go of and a tween throws that away:
    the book would leave the finger's speed behind and travel at the
    curve's instead, which is the commonest way a gesture reads as cheap.
    The spring tokens (`cover`, `handled`, `touched`) are critically damped
    at 1.0 — physical, and they never cross the target, so §9.1's "no
    bounce, no spring overshoot" holds.

A22. **The app flows rather than cuts.** Every screen change was a
    substitution: the room faded and the menu slid over it, a settings row
    was replaced by a settings screen. All of it moved and none of it
    continued. One `SharedTransitionLayout` now spans the whole room stack
    with an `AnimatedVisibilityScope` per layer, so the pieces that exist
    on both sides of a change *are* the same piece and travel: your face in
    the room's corner and your face at the top of You; the room's fire and
    the small fire on its row in the menu; an ember on the shelf and the
    same ember on its record; a settings row's words and the heading of the
    screen it opens. Keys are built from ids in one place (`Flows`),
    because a shared element with a mistyped key is not an error — it is an
    element that silently stops flowing.

    **Three rules came out of building it, and the third cost the nicest
    two effects in the pass.** A key pairs exactly two halves, one leaving
    and one arriving. Words set differently on the two sides — a row's title
    at 17 sp and a screen's heading at 30 sp in the display face — go
    through `flowsAsWords` (`sharedBounds`) rather than `flows`
    (`sharedElement`), because one drawing carried between two frames
    stretches type on the way.

    And **a flow needs one of its halves to be on its way out**, which rules
    out every pairing between the room and the menu. The menu is a *layer
    over* the room rather than a replacement for it (A17, so predictive back
    can peel the room in behind it), so the room stays composed and visible
    underneath for as long as the menu is open: a shared key across that
    boundary has two permanently live halves and neither is leaving. The
    room's fire was going to shrink into its row in the menu and your face
    was going to travel up into You, and both were built before the
    architecture said no. They are gone. What flows is every NavHost push —
    a seat into that person's screen, an ember into its record, a settings
    row into the screen it opens — where exactly one side is always going.

A22a. **The person screen took the same pass.** It was the barren room's
    defect in its purest form — a portrait, a name, and then a column of
    bare references to notes you could not read — and the room's new seats
    make it far more reachable than it was. It now has a head, its notes are
    tiles, and a note shows its own words (or a voice note's transcript;
    never a duration, S04) **once it has been found in the margin, or if it
    is yours**. §6.3's whole beat is being found later, and a list that read
    every unfound note aloud would spend it before anybody opened the book;
    an unfound one gives its address, which is an invitation to go. It also
    draws a way back, which it never had — §11's tap equivalent, the same
    one every pushed settings screen carries.

A23. **The settings are tiles, and the hairlines under headings are
    gone.** — the owner's call ("the settings is very condensed, and I
    think it would look better in a completely new style"). The cause was
    that the app had no drawn container at all, so a screen could only be a
    column of sentences and the only lever was how much air to put between
    them: a lot is barren, a little is condensed, and there was no third
    option to reach for. There is one now — a group is a set of tiles on
    the ground, separated by a two-dp seam rather than by a rule.

    The half that matters most is not the shape: **almost every row gained
    a sentence**. A switch used to be four words on a bare ground and you
    were left to infer what it did; it now says the true small thing about
    what it does, in the app's own voice. And each screen gained a display
    title and one line saying what it is, where a pushed screen used to
    open on a 12 sp small-caps word.

    Six ruled lines under headings went with it, which is the church
    bulletin §13 forbids in its most literal form; the edge of a tile does
    that work instead. What keeps a screen of rounded rectangles from
    reading as somebody else's Settings app, which is the real risk: the
    paper grain is carried onto every tile, the corners are large, there
    are no icons anywhere, the undoing controls (sign out, leave, delete)
    stay off the tiles on the bare ground, and nothing is drawn that does
    not say something.

A24. **There is a look book, because nobody can see this app.** The CI
    environment has no emulator and the Android build has still never run
    on a physical device, so every layout decision in this pass would
    otherwise have been made blind. `LookBookTest` renders the room, the
    book and every settings screen from the app's real composables against
    a real `AppModel`, on both palettes, and writes them to
    `app/build/shots`; CI keeps them as an artifact. It asserts only that
    each screen composes and is not one flat colour — the failure that
    actually happens — because a pixel comparison on a screen under
    redesign is a test that has to be deleted every time the design is
    right. It is the first Robolectric test in the repo, which is the one
    cost: CI now fetches an `android-all` jar.

A25. **The front door took the same pass as the room — and the tour is an
    undocumented deviation from S17 that this ledger is now recording
    rather than resolving.** The room and the settings were rebuilt first
    and everything a hesitant partner sees *before* the room was left
    alone, which meant the friendliest part of the app was the part you
    reached last. Rendering it is what showed the state it was in:

    - The tour card that promises "a fire kept alive together" was drawing
      a **gradient circle over a bar**. The product has one central object
      and that was the card introducing it. It is `CampfireView` now — the
      room's own fire, in a well, for the reason the room's is in one: the
      fire throws ambient light across its whole canvas and clips it, which
      is a visible rectangle on bare ground.
    - The intent step set its three unchosen options in `muted` on a fill
      that cannot be seen, so **three of four choices read as disabled** on
      the screen that asks who you will read with. They are `text` in both
      states now, in the app's own group tiles, with the accent ring and
      the check carrying the selection between them.
    - **Three copies of one text field** — the name, the invite code and
      the sign-in code, which is every place in the app somebody types
      their way in. Two of the three had drifted; one had deviation 12's
      exact defect back (the 44 dp minimum on a wrapper rather than on the
      field's own decoration). All three drew the **caret inside the
      prompt**: "Your |name", on the first screen that asks you to type.
      One `CentredTextField` now, with the prompt beside the caret.
    - Onboarding's tour cards, its portrait well and the join's portrait
      well were each hand-rolling a background and a border, so the front
      door was the one place in the app that drew outlines on every
      wallpaper. They go through `paper` and `well`.

    **What is recorded and not changed:** S17 says onboarding is "a thread,
    not a screen. Four questions, no tour, no carousel" — and the file's own
    header says so too, above four tour cards and a six-step progress bar.
    That predates this pass and removing four screens is a product call, not
    a friendliness one. It is written down here because an undocumented
    deviation is the kind that gets mistaken for the design.

A26. **The offline fire is wired.** `CampfireView` has carried a `dimmed`
    parameter since it was written and **nothing ever passed it**, so S01's
    "fire renders in its last known state, dimmed by ~8%" was unreachable
    code. `Connectivity` (a default-network callback asking for
    `NET_CAPABILITY_VALIDATED`, so a router with no uplink counts as
    offline) hangs off `AppModel` and the room's fire reads it. That is the
    entire user-visible surface: no banner, no retry control, no queue
    count — §13 puts a connectivity banner on the never-ship list, and the
    presence half of S01's offline state needs no code at all, because
    presence comes off the wire and is already absent. Costs one normal
    install-time permission (`ACCESS_NETWORK_STATE`), no prompt.

A27. **The paused room stopped asking for something it withholds.** Its
    hearth was printing "Pick something to read together" over a room where
    picking a book is exactly the half a lapse holds, and its foot was
    offering to mark a quiet day directly under the line that had just said
    the room was paused — which §4.7 rules out by name ("never surfaced
    after a lapse, which would make it an apology"). The hearth holds its
    line and says nothing now, and S01's one row explains the rest.

A28. **The launch window carries the Wave.** §05 says there is no splash
    screen. The rule survives in the sense that matters — nothing is
    *inserted* — because Android 12 and later show a system splash on every
    cold start whether an app asks for one or not. It cannot be declined,
    only styled, so the choice was never "splash or no splash" but "our mark
    or the launcher icon on a grey plate". Owner's call: the mark, on the
    unlit ground, unfurling from the top as a ribbon let out of a book. Built
    from the same path data as the launcher icon so the two cannot drift, and
    it **rests open** — the resting state of a launch drawable is what shows
    when the animation does not run, and resting closed would turn an
    unsupported device or system animations-off into a blank window rather
    than a still mark. The splash is held by the store loading, as before; a
    480 ms floor is the one concession, so a warm launch cannot cut the
    unfurl off after three frames.

A29. **The menu is two screens: the room, and you.** It was one scroll with
    four sections, entered from two doors, which landed by calling `scrollTo`
    on arrival. The split falls along a line the data already draws:
    notifications are per room, the plan entitles a room, and the invite, the
    inks and leaving are all about this room — while your name, your
    translation, your text size, the wallpaper's colours and this phone's
    downloads are yours. The room's title is the room's own name, because a
    large Material title names the thing you are looking at and "Settings" is
    not a thing anybody is looking at.

    The settings screens also sit under Material's own large collapsing app
    bar now (`RibbonScreen` → `LargeTopAppBar` +
    `exitUntilCollapsedScrollBehavior`). The owner's note was that they did
    not look like Material You, and the tell was the top of the screen: a
    static display title that scrolled away with the content. Structure is
    Material's, paint is Ribbon's — A18's argument one level up. The
    expressive `LargeFlexibleTopAppBar` would have carried the lede in a
    subtitle slot and is internal in material3 1.4.0; the lede reads better
    as the first thing *in* the page anyway.

A30. **The ribbon: one place in the book, kept by the room.** Owner's call,
    and it sits deliberately close to a line §03 draws — *position is
    per-person, per-reading; there is no shared "where we are."* That rule is
    intact: `ReadingPosition` is untouched and the book still opens where
    **you** are (§6.2). What §03 refuses is a shared position that turns two
    people reading at different speeds into a problem to be solved, and this
    is not that. It is the ribbon in a shared Bible: somebody reads, closes
    the book, and the ribbon is where they stopped.

    Four things keep it from becoming a race, and none of them should be
    removed without reading this paragraph:

      - It is an **address, never a measure** — "Mark 4:9", the same kind of
        thing as an ember's date range, which §13 allows because it is "an
        address in time, not a duration". Nothing subtracts it from your
        position and nothing ever may.
      - It is **offered, never applied**. Moving it moves nobody. It is one
        quiet sentence on the room screen and a hairline under one number in
        the chapter list, both of which you have to look at.
      - It is **one object, not one per person.** A per-person ribbon would
        be a leaderboard with the numbers taken out.
      - **Nobody is behind**, because the app does not know: it holds one
        address and your own and never puts them in a sentence together.

    Placed by closing the book, because that is the whole of the act — a
    "mark this verse" control would turn a consequence of reading into a
    chore. Only when you actually moved: opening the book, glancing and
    closing it again would otherwise drag the room's ribbon backwards and
    quietly undo somebody else's. A finished reading keeps the ribbon it had;
    an ember is a record, and editing it afterwards is editing the past.

A31. **There is a chapter list, and the foot of the book is the way to it.**
    A book opens at your own position and is read forward, so reaching Mark
    10 from Mark 1 meant scrolling nine chapters. The build book never
    specified one because S02's page is deliberately bare and §6.6 puts
    navigation in the chooser — but the chooser *starts* a reading, it does
    not move inside one.

    So it is the chooser's own argument one level down: "the most
    conventional screen in the app, and it should stay that way — this is
    navigation, not atmosphere." A grid of numbers. No progress ring on each
    chapter, no ticks for what has been read, nothing shaded by how far
    anybody got — all of which are the counting Law 2 forbids, and all of
    which a chapter grid is the classic place to smuggle in. Exactly two
    things are marked and both are addresses: where you are (the tile raised
    out of the page) and where the ribbon is (a hairline at the tile's foot,
    which is what a ribbon looks like from outside a closed book).

    S02 says the way out is the Wave and "nothing else down there", and this
    is a deliberate second thing. It keeps its distance: the Wave still has
    the bottom edge to itself, and the control above it is the running head
    repeated — small caps, low contrast, saying where you are, which is what
    a running head does. It is a door only if you press it.

A32. **Three things the owner found by using it, and what each turned out to
    be.** Worth recording together, because none of them was a taste
    disagreement — each was a defect with a cause.

    - **The launch mark never animated.** The theme set androidx's *compat*
      splash attributes, and on API 31 and later the platform draws the
      splash from its own `android:windowSplashScreen*` slots. This app's
      minSdk is 33, so every device on earth fell through to the system
      default — the launcher icon on a plate — with the animated vector
      sitting unused in the APK. The `android:` prefix is the fix, and the
      look book now drives the real `AnimatedVectorDrawable` and asserts two
      frames 600 ms apart are different pictures: an `animated-vector` whose
      target names do not match its vector is not an error, it is a still
      picture that says nothing. The settle also had no pivot, so it was not
      a settle — the mark slid diagonally as it shrank.
    - **The way out of Scripture was finicky, and it was not a tuning
      problem.** S02 gives two ways out: the Wave, and a downward drag from
      scroll-top. The drag existed and *counted pixels* — it accumulated a
      running total, deliberately consumed nothing, and past 90 dp called
      `close()` outright. Nothing moved under the finger; the only feedback
      was the list's overscroll glow, and then the book simply went. An
      invisible threshold you cannot see approaching, cannot feel and cannot
      back out of, on the one gesture that should feel like closing a book.
      It predates the sheet: A20's whole argument is that the drag and the
      animation are one number, and this path was written before there was
      one — the Wave beside it was converted and this was not. It drives
      `BookSheet` now, so the page follows the finger from the first
      millimetre and a close caught halfway eases back. The 90 dp threshold
      is gone rather than retuned, because `RibbonMotion` already owns what
      "far enough" means and a second opinion in another file is how two
      halves of one gesture drift apart.
    - **You was a wide tile with a circle at one end.** Nothing said either
      half was a control — a portrait you can change and a name you can edit
      looked exactly like a portrait and a name. It is centred now, at the
      size of a face you can see rather than one being celebrated, with the
      line that says where they are seen and the small caps that say the
      face is a control. The sections below are named for what they are
      about — how you read, this phone, your account — rather than by which
      screen they open; "Reading" had Downloads in it, which is filing by
      convenience. The lede went with them: it listed the same three things
      the headings underneath already say.

      What keeps it from being a profile page, which §13 would not have:
      nothing is counted. No rooms joined, no books finished, no
      member-since, no badge. A face, a name, and one sentence.

A33. **The cards took the pass the rest of the app had already had.**
    `ReflectionCardView` was written before either design pass and neither
    reached it, so it was the last surface in the app still built out of
    the materials the app had abandoned: a hand-rolled 12 dp rectangle with
    a hairline border where everything else is on the shape scale and the
    paper grain (A23); four bare `clickable` lines of text, all of them well
    under the 44 dp floor (deviation 12); every word a literal rather than a
    line of `Copy`; and `"SET IT DOWN"` typed in capitals at a small-caps
    face, which is the textTransform §09 forbids, said out loud.

    Three of the repairs are not cosmetic.

    **The turn was never a turn.** S09 asks for "a slow turn over 480 ms,
    ease-out, no bounce", and §11 says that under reduce motion "the card
    turn becomes a fade" — a sentence that only means anything if there is a
    turn to reduce. There was only ever the fade, on both settings, from a
    raw `tween(480)` that also made this the one animation in the app with
    no reduce-motion path at all. The card turns now, on `RibbonMotion.open`
    (which *is* §9.1's 480 ms ease-out), swapping its face at the moment it
    is edge-on so neither side is ever read in a mirror; reduce motion snaps
    the angle and leaves §11's cross-fade, exactly as written. The angle is
    read inside the layer block, so the card turns without recomposing a
    word of what is written on it.

    **A half-typed answer was lost.** `answerDraft` was `remember`, and the
    keyboard resizing the window is enough to dispose that composition. It
    is `rememberSaveable` now. A card is a thing people think about before
    they type, which is the whole point of the mechanic.

    **The answers were set in the author's ink.** §S09 asks for "every
    answer, each with its author's portrait and ink", and the ink was being
    applied to the answer's *body* at 16 sp. The eight inks are cut for a
    24 % highlight wash and for a name at 12 sp; §11 asks for each to be
    verified for the text *under* the wash, and a paragraph of Moss on the
    surface is the one place the palette does not clear. The portrait's ring
    and the name carry whose it is — the settlement `NoteCard` already
    reached — and the answer is ivory, because an answer is something to
    read.

    Two smaller calls worth writing down. S08's "an answer field, open, no
    placeholder text beyond a single hairline" was first read as a *size*,
    72 dp of field with a rule under it, and on the page that is a hole:
    a question, a void, and a faint line a long way beneath it with nothing
    saying the void is where you write. The field takes the height of what
    is in it instead, one line when that is nothing, so the hairline comes up
    to meet the question — a line to write on is an invitation, a box of
    empty space is a gap in the page. And the `Answer` control is no longer
    drawn greyed-out over an empty field, which is a control that says
    exactly what happens and then does not do it (the defect S22's row was
    rewritten to remove); it arrives with the first thing typed.

    Setting a card down also used to take it out of the composition on the
    frame the tap landed. §4.6's "it leaves without ceremony" is about the
    absence of a dialog, not about the card vanishing from under the finger
    that retired it, and §9.1 has no cuts in it; it shrinks away on the
    settle token now, the way a note that has been taken back does.

    What is deliberately unchanged: a card still cannot be un-answered. §4.6
    gives the room one escape hatch and it is *set it down*, which retires
    the card for everybody; taking your own answer back would leave a card
    that can never open and nobody to say so, which is the debt the whole
    mechanic exists to avoid.

    One line of copy went with it. The notifications screen described the
    cards opening as "When you have both answered", which was wrong from
    three people up, and §2.3 holds six. It says "everyone" now, which is
    also the word the card's own waiting line uses.

    iOS carries every one of these defects in
    `ios/Ribbon/Reading/ReflectionCardView.swift` and is untouched on
    purpose: this pass is Android's, at the owner's direction. The copy
    constants are the shared half and port straight across when iOS takes
    its turn.

A34. **Notifications exist.** S19 is the screen the build book calls "the
    setting screen that decides whether people keep this app", and until now
    it decided nothing: nothing in the Android build had ever posted a
    notification. No channel, no `notify`, no small icon, no PendingIntent.
    `Copy` held all six of §10.3's strings and exactly one of them was read
    anywhere — as the text of an in-app waiting row. The four switches, the
    two quiet-hours rows and `RoomNotificationPrefs` were a settings screen
    for a feature that did not exist, which is deviation 15 in its most
    literal form.

    The whole of it is in `services/Notifications.kt`,
    `services/RoomWatch.kt`, and a handful of seams elsewhere. Six calls
    worth arguing with:

    **Channels are per kind, and S19 says "per room, not global".** A
    channel's importance belongs to the person and cannot be changed by the
    app once created, so four channels times six rooms is up to twenty-four
    rows in Android's settings for somebody to curate — and a room they
    leave would strand dead ones there forever. So the *platform's* grouping
    is by kind and Ribbon's own code does the per-room gating before a post
    ever reaches the platform. §12.2's Law 5 is the authority: the platform
    owns chrome, Ribbon owns content, and which rooms a person wants to hear
    from is content. The five channels take their names from the four switch
    titles verbatim plus "A book finished", so Android's settings page and
    Ribbon's say the same words about the same thing.

    **Law 2 leaks through the platform, not through our prose.** Every
    channel is created `setShowBadge(false)` and nothing calls `setNumber` —
    §13's "no red number badge on the app icon", in its Android form, refused
    once at the channel rather than remembered at every post. And §10.3's
    collapsed string ("Ruth left you a note", no verse) is *not* implemented
    as a notification group: Android writes its own summary with "+2 more" in
    it, which would be a count attached to reading, posted by the platform,
    in the last place anybody would look for a Law 2 breach. Several notes
    from one person in one room share an id and replace each other instead.

    **The watermark, and why a new phone is quiet.** `merge` used to publish
    one state and say nothing about what was new in it, so there was no event
    to post from — which is most of why there were no notifications. It
    returns an `Arrivals` now, diffed at the one moment both states are in
    hand. The guard that matters is `AppState.notifiedThrough`: on the very
    first merge on a device it is null, it is set to the newest row seen, and
    *nothing* is reported. Without it, signing in on a new phone (§6.10)
    would restore every room a person is in and post a notification for every
    note in it — several hundred, in one breath. It advances on every merge
    whether or not anything was posted, so a suppressed notification is not
    re-offered by the next one.

    **Asked in context, once.** §6.1: "after the first note is left or found
    — never at launch. In context: *Tell you when Ruth leaves a note?*" That
    is where it is asked and those are the words, with two answers and no
    "not now" — the shape that only exists in apps that intend to ask again.
    It is never raised in a room of one: there is no name to put in the
    question and nothing to promise, and a nameless version of it is the
    notification pre-prompt S17 forbids. `hasAskedAboutNotifications` keeps
    the same contract as the margin hint and the fire's gesture, for the same
    reason Android forces on us — `shouldShowRequestPermissionRationale`
    cannot tell never-asked from refused-for-good, which `AudioNotes` already
    had to work around for the microphone.

    **Quiet hours are honoured, and the arithmetic is not the obvious
    arithmetic.** The default window runs 10 p.m. to 6 a.m., so `start` is
    *after* `end`: `minute >= start && minute < end` is false for every
    minute of the default window and true for the whole of the day it exists
    to leave alone. That is the bug inverted rather than missing — it would
    have silenced 6:40 a.m. and 10:15 p.m., the two hours §1 says the app is
    actually opened in, and let everything through at 3 a.m. `isQuietAt` is
    written for the wrapping case and `QuietHoursTest` holds it, which is the
    one part of this feature a unit test can reach. Equal ends mean *never*,
    not a silent day. Inside quiet hours thinking-of-you is the one thing
    that arrives, and even then only as a haptic and only when
    `PowerManager.isInteractive` — S19's "as a haptic on an already-woken
    device", read literally.

    **What the background route honestly cannot carry.** There is no FCM and
    no foreground service. A foreground service means a permanent "Ribbon is
    connected" notification in the shade, which is chrome about the app's own
    plumbing and the opposite of §1's room; push means a Google dependency
    and a server-side sender, which would not be an Android-only change.
    So a 15-minute `WorkManager` job pulls and posts. Anything with a row
    behind it rides along — notes, cards, a finished book — up to fifteen
    minutes late, which for a note that was *left to be found later* is not a
    defect. Two things do not: "Ruth is reading Mark" is presence, socket-only
    and ephemeral, and a fifteen-minute-old version of "so you can read at the
    same time" is a lie, so it posts only while Ribbon is running; and
    "thinking of you" is a client-to-client broadcast with no row anywhere, so
    a tap sent to a closed phone is still lost. Fixing that needs a table on
    the shared backend and is not an Android-only change — it is the next
    piece of work on this feature, and it is written down here rather than
    left to be discovered.

    Two smaller things went in with it. A tapped notification now has
    somewhere to land: `AppModel.pendingDestination`, honoured by the room's
    stack, switching rooms first if it has to. It travels as extras on a
    private action rather than on the `ribbon://` scheme, which is exported
    and BROWSABLE — an invite token the model validates is one thing to let a
    web page hand us, and *somewhere to go* is another. And `visibleRoomID`
    lets a post stay quiet about something the room is already unfurling in
    place under the fire, which is what §6.3's "a notification, or nothing at
    all" means when the interface is already handling the beat well.

    The look book earned its keep here (A24): starting the worker from
    `Application.onCreate` took every screen in it down at once, because
    `WorkManager.getInstance` throws when its `androidx.startup` initializer
    has not run. That moved the call to where there is an account to watch
    for, which is where it belonged anyway — a person who has never signed in
    has nothing to pull, and waking their phone four times an hour to find
    that out is a battery cost with no feature behind it.

A35. **S12 joins the rest of the app, and says something when there is
    nothing.** The person screen was the last one standing on its own page
    furniture, and A22a is why: that pass gave it a head, tiles and a way
    back *before* `RibbonScreen` existed, and nothing came back for it.

    Two of the differences were defects rather than style. Its back chevron
    lived **inside the scrolling content**, so a person with more than a
    screenful of notes scrolled the only tap route back off the top of the
    screen — §11's motor rule with the system gesture as the sole survivor.
    And the name was a centred line rather than the screen's heading, so a
    screen reader got **no heading node for the thing the screen is about**.
    The rest followed: centre-aligned where every pushed screen is
    start-aligned, a 24 dp measure against everyone else's `ScreenMargin`, a
    hardcoded 60 dp spacer in place of the navigation-bar inset, and a list of
    notes drawn as eight identically-rounded tiles rather than one group with
    the group's own outer corners.

    Nothing in S12 asked for any of it. Its anatomy is a portrait, a name, an
    ink and a list, and a collapsing title holds all four.

    **Two deliberate differences from the settings screens, kept.** S12
    declines the lede: the one slot on the screen that invites a sentence
    about a person is exactly where a join date or a "last seen" would arrive,
    and S12's anatomy ends "nothing else". And its face sits on the bare
    ground where S18's identity tile puts its face on paper — here the face is
    the subject of the screen, there it is a control beside another control,
    and a tile is what a row of controls is for.

    **The empty screen now says something.** The notes block had no else
    branch, so on the first day of a new room — the moment the product is
    being judged — tapping the other person's face gave a portrait, a name,
    and blank ground. It reads as a screen that failed to load. One
    impersonal line stands there instead: *Nothing left in this room yet.* No
    head, because the head reads "What Ruth left" and over nothing that is a
    head naming the person who has not done the thing (§10.1); and no tile,
    because a drawn container announcing an absence is §4.2's placeholder
    mistake.

    **An unfound note says it is waiting.** A22a's call — an unfound note
    gives its address and not its words, because §6.3's beat is being found
    later — is right, but an address alone is only "an invitation to go" if
    the row says it is one. Without it an unfound note and a voice note whose
    transcription failed rendered identically, and this was the one place in
    the app a note's found state was spoken nowhere. It carries §11's own
    clause now, in the running-head voice, and the spoken label ends with it.

    Smaller things in the same pass: the ink line reads as *"Their ink,
    Crimson"* rather than the bare word "Crimson" floating between a name and
    a list (§11 — colour is never alone); "Change your ink" moved from the
    foot of the page to directly under the ink it changes, because it used to
    sit a screenful away and share a stack with "Leave this room", which made
    changing a colour look like the same class of act as leaving; and a person
    whose profile has not synced is "Someone" rather than an empty heading over
    a head reading "What  left".

    **`RibbonScreen` had a latent bug the empty screen found.**
    `readableColumn` ends in `wrapContentSize`, so a page was only ever as
    wide as its widest child — and a screen whose children all happen to be
    narrow centred itself and everything on it. The settings screens never
    showed it because a group of tiles fills the width; S12 with nothing left
    in it did, and the tell was a portrait that sat on the margin with one
    note and jumped to the middle with none. The content column fills its
    measure now. A24 again: only a picture catches that.

A35a. **The identity tile on You says what it is for, and the name it holds
    is saved by every way out of it.** S18's name field committed in exactly
    one place — the keyboard's Done key. Tapping the face beside it, tapping
    anything that took focus, pressing back, closing the menu or pushing a
    settings screen all threw the edit away with nothing said. S18 calls the
    name "editable in place", and an in-place edit only one soft-keyboard key
    can land is not one; it is also the most load-bearing field in the app,
    since the name is the one thing S17 will not let a person skip and it is
    what their partner sees on every seat and every note. It commits on the
    IME action, on losing focus, and on disposal. An empty name reverts
    silently — a person cannot delete their own name, and §10.1's unbothered
    interface does not scold them for trying.

    The field also had no label and no prompt, so cleared of its text it was
    a bare caret and an unlabelled edit box to a screen reader — the one
    typing surface in the app with neither, which is the class of defect A25
    fixed across onboarding's three fields. It draws `Your name` behind the
    caret, the way the room's own name field already did.

    The third half of this entry was written and then withdrawn, and the
    reason is worth keeping. The tile said nothing about itself while the
    three rows beneath it each carried a sentence — A23 calls that the half
    of its pass that mattered most — and neither of its two targets was drawn
    as a control: the portrait's affordance existed only as a content
    description, the name's only as a click label, both invisible on the
    screen S18 calls the place your identity lives. So it gained a sentence
    of its own, `IDENTITY_REASON`. A32's rebuild of You, which landed on
    `main` while this branch was open, answers the same complaint in a better
    shape — the small caps under the face saying the face is a control, and
    one centred line saying where a name and a face are seen — so the tile's
    own sentence would have been that line said twice. It is gone, and the
    string with it: an unused constant in `Copy.kt` is the defect A39 is
    about, and a merge is the usual way one gets there.

A36. **Taking something back now takes it back.** The app has had "take
    back", "remove" and "leave your notes behind?" since the beginning, and
    under them the deletions reached the backend and stopped. Five defects,
    which are one defect seen from five places.

    **A taken-back note never left the other person's phone.** `merge` was
    add-or-update for notes and add-only for highlights. Memberships are
    pruned there ("departures propagate") and invites are pruned ("an invite
    the backend no longer has must not go on being offered"); notes were not.
    So `takeBack` deleted the row remotely and nudged the other device to
    pull, the pull came back without it, the loop added nothing and removed
    nothing, and the note sat on the other person's phone permanently. S04
    says a taken-back note vanishes "with no tombstone", and deviation 10
    already claimed take-backs propagate.

    The prune has three guards and each one is load-bearing. `notesComplete`
    exists because the notes select is wrapped in a `runCatching` that
    returns an empty list on failure — pruning against that would delete
    every note in the room the first time one request timed out, which is a
    far worse bug than the one being fixed. Only readings the pull actually
    covered are considered. And a pending note — composed offline, not yet
    known to the backend (§4.4) — is never pruned. A pruned voice note takes
    its local recording with it.

    **Nothing was ever retried.** `takeBack` and `editWrittenNote` both
    wrapped their remote call in `runCatching` and forgot the outcome, and
    the only thing `refreshFromRemote` replayed was a rename. A take-back
    made offline was applied locally, never sent, and undone by the next
    successful pull. An offline edit was worse than lost: the merge takes
    `body = row.body ?: local`, so the server's old words silently overwrote
    the new ones. There are queues now, in the shape of `pendingRenamePushes`
    — deletes, edits and new notes — replayed before every pull, and the
    merge consults them: a pull that races a pending delete does not re-add
    the row, and one that races a pending edit does not take the server's
    body. An edit also carries §4.4's pending hairline now, which only a
    *new* note ever did.

    **"Take them back" on leaving a room was a local filter.** The rows
    stayed in Postgres, the recordings stayed in the bucket, and every other
    member's phone kept its copy — so the one answer §6.8 offers to somebody
    who wants their words back did nothing except hide them from the person
    who asked.

    **A taken-back voice note left the recording on the server.** The
    `notes` row went; the object at `voice-notes/<reading>/<note>.m4a` did
    not, and could not — the bucket had a read policy and a write policy and
    no delete policy at all, while the portraits bucket had gained one. So a
    person recorded a thought, thought better of it, took it back, and a
    recording of their voice stayed fetchable by everyone else in the room
    forever. That is the one place in the product where undoing something
    left the most personal version of it behind. New migration
    `20260916120000_ribbon_take_back_the_recording.sql`, scoped to the
    note's *author* rather than to the room — reading a note somebody left
    you does not entitle you to erase their voice. The object is deleted
    before the row, because the policy checks the row.

    **Deleting your account ignored the question it had just asked.**
    `deleteAccount(keepNotesBehind)` never read the parameter, and could not
    have honoured it: `deleteAccountData` deleted the profiles row, and both
    `notes.author_id` and `highlights.author_id` cascade from it, so *both*
    answers erased every note and every highlight the person had ever left.
    §6.8 says highlights "stay, always"; S11 needs a departed member's notes
    to render normally, with their portrait, and nothing marking them as
    gone. The profile is blanked rather than deleted now — name to the app's
    own word for somebody it has no profile for, portrait path to null, the
    portrait object deleted outright — so nothing personal survives and the
    rows that hang off it stand. Then the answer decides: leave them behind
    and nothing authored is touched, take them back and the notes go,
    recordings and all. Highlights are never deleted on either path.

    **This diverges from iOS on purpose, and it is the one entry here that a
    reviewer should push back on if they disagree.** iOS still deletes the
    profiles row and still cascades. The Android behaviour is what §6.8 and
    S11 describe and the iOS behaviour is not, so the divergence is iOS's to
    close rather than Android's to undo — but it is a difference in what
    account deletion *means* on one backend, and it should not sit here
    unnoticed. The new storage policy is additive and changes nothing for
    iOS, which simply never calls it.

    Two smaller ones in the same pass. An abandoned edit used to retarget the
    next note: `editingNote` was cleared on save and on cancel but not by
    `clearLift`, which is S05's own "dismissed by tapping anywhere in the
    text" — so opening your note, starting an edit and tapping the Scripture
    left it set, and the next verse you wrote at opened pre-filled with the
    old note's words and overwrote *that* note on save, leaving nothing at
    the verse you had picked. And the note menu offered "edit" on a voice
    note, which opened the written composer, empty, over a recording; typing
    into it set `body` on a note whose kind is still `voice`, which nothing
    ever reads — the words went to the server and were never seen again by
    anybody, including the person who wrote them. A recording is re-made the
    way it was made.

A37. **The invite path, end to end.** S15 says "the link is the whole
    mechanism", and five things were wrong with the whole mechanism.

    **A link minted while the network was down died.** `createInvite` fired
    the backend registration into a coroutine, logged a failure, and forgot
    it; nothing anywhere retried, and `pendingInvitePushes` is in memory, so
    the next launch had no record of it at all. `merge`'s invite prune then
    deleted the local invite *because* the backend did not have it — and the
    link the sender had already pasted into a message thread resolved to
    nothing, permanently, with nothing anywhere saying so. Two device-local
    sets in `AppState` now, persisted: one for links this phone has not
    managed to register, which the prune refuses to touch and which
    `refreshFromRemote` re-pushes on every resume, and one for links that
    actually left. No banner and no error line on the sheet — §6.10 is
    explicit that the app working is not news, and self-healing is the honest
    answer.

    **"The invite is still out" was shown to people who had invited nobody.**
    Both the onboarding step and the invite sheet mint a link the moment they
    appear, and `hasLiveInvite` asked only whether one existed — so anyone who
    had merely *seen* either screen was told, on the first morning of their
    room, that an invite was outstanding, with a control to send it again.
    Minting is not sending. `hasLiveInvite` requires that the link left this
    phone, or that it came from somebody else's (whose sending is not ours to
    see, and whose invite is the room's live link by definition). A chooser
    the person then backs out of still counts: Android only reports the chosen
    component through an `EXTRA_CHOSEN_COMPONENT` PendingIntent, and that
    machinery buys less honesty than it costs.

    **Opening the sheet pushed everything twice, including the portrait.**
    `createInvite` registers the link itself; both call sites then called
    `pushInvite` again on the next line, which re-read the sender's portrait
    off disk and re-uploaded the JPEG, on every appearance, swallowing its own
    failure more quietly than the first attempt did.

    **A joiner whose session had gone stale hit a wall.** `withAuthRetry`
    signs a person out when the refresh token is dead, and `joinRoom` throws
    `NotSignedIn` outright when there is no account — and both landed in the
    same dead end as an expired invite, whose only control is Close, shown to
    somebody who had just been signed out by the screen refusing them. The
    sign-in step is already in that file and already retries the join on
    success; a missing account goes there now.

    **The screen the book calls "the most important conversion surface in the
    product" showed no face.** S16's anatomy is "who invited you, their
    portrait, the room's name, and one control", and the file's own header
    says "the screen shows a person, not a product" — and it drew a sentence,
    a room name and a button. It draws the inviter's monogram now. **Not the
    real photograph, and that is a backend limit rather than a design
    choice**: the portraits bucket is readable only by co-members and a person
    holding an invite is not one yet. Reaching the real face needs an edge
    function that takes the token, validates it, and streams the portrait —
    worth doing, and not an Android-only change. A monogram in the right
    recess is a person; nothing at all is a form.

    S16's last state also did not exist: "signed in as someone else (offers to
    switch, does not silently join)". A tap on Join seated whoever the phone
    happened to be signed in as, without ever saying who, and the only route
    to another account was the sign-out control two taps deep in the menu. The
    preview says who it will be and offers the other door.

A37a. **The room has S01's third waiting row.** S01's anatomy has always read
    "notes left for you, cards open, **an ink to pick**", and the third one
    had never been drawn. §6.7 asks for it in so many words — when a room
    becomes three, "the two originals get an invitation on the room screen to
    pick an ink. Not a blocking dialog; it waits."

    It is the newcomer's side of the same beat that made it urgent. A
    first-time joiner's membership arrives with no ink at all — `restoreInk`
    only restores one the backend already remembers — so in a room where ink
    is identity (§4.5) every mark they made fell back to `?: Ink.clay`, which
    may already be somebody else's colour, and the reading screen handed them
    the whole free palette that only a room of two is supposed to have.

    The row waits exactly as the book says: no dialog, no badge, nothing
    blocking, and it goes the moment an ink is picked. Its mark is the one on
    that screen that is about a colour and cannot use one.

A38. **The cuts that were left.** §9.1 opens "everything breathes rather
    than snaps", and A21 and A22 did most of the work of making that true —
    but a sweep of every animation primitive in the tree found ten places
    where something still changed on one frame, and three of them were the
    loudest moments in the app.

    **The Wave blinked out as the long-press toolbar slid in over it.** The
    foot of the reading page draws three things into one bottom-aligned box:
    the toolbar, on a careful `AnimatedVisibility`, and — in a bare `when` —
    the composer, the running-head pill and the Wave. The instant a verse was
    long-pressed the Wave and the pill stopped composing and the toolbar rose
    over the hole they left. It is an `AnimatedContent` now, keyed on which
    of the four things is showing rather than on the composer itself, so
    typing does not restart the transition, and the leaving branch holds its
    last address the way the toolbar's range already did.

    **The fire changed state in one frame while the word under it took 400 ms
    to say the same thing.** `TheFire` cross-fades the fire's *name* on the
    settle token, under a comment arguing that "a word swapped on one frame
    under a fire that took its time getting there reads as a correction" —
    and the fire was not taking its time. It crosses now: two stacked passes
    inside the one offscreen layer, so the additive blending still
    accumulates in the fire's own buffer (A15), sharing time, scale, coal
    depth and seed so nothing moves except the flame. Going offline eased
    too, rather than stepping the fire 8 % darker and back on every flap of a
    bad connection.

    **The thinking-of-you ring cut at the exact moment the gesture
    succeeded.** A hold let go of early eased its ink back over 150 ms; a
    hold that completed ran `snapTo(0f)`. The gesture that failed left
    gracefully and the gesture that worked was the one that cut, which is the
    wrong way round and the one cut §9.1 would least forgive.

    The rest, in one line each. A seat's entrance was written and could never
    run — the transition state was seeded from the live roster, so on the
    composition where a newcomer's seat first exists it starts and ends true;
    it seeds from a snapshot now, the way the waiting rows already did, so
    launch is still silent (§05) and an arrival is an event (§6.7). The
    confirmation dialogs had no motion at all, and §6.8's two questions
    arrived by tearing each other down — they are one dialog whose words
    cross-fade now. The segmented control's pill slid on a spring while its
    three labels changed colour on one frame, which is exactly the argument
    its own comment makes against. The follow ring and the follow thread were
    plain conditionals. The starter shelf and the shelf blinked in and out
    while the hearth above them cross-faded. And the onboarding progress bar
    was the last raw `tween` in the tree: it borrowed the settle token's
    *duration* and nothing else, so with no easing argument it took Compose's
    ease-in-out — the only thing in the app moving on a curve §9.1's table
    does not contain — and never asked about reduce motion at all.

    Two of the ten were introduced by this very pass, which is worth
    recording: the card's set-down exit played over an empty box, because the
    content re-read the retired card and hit its own guard; and the "Android
    isn't passing these on" line called the settle token bare, so it was the
    one thing on the screen still moving for somebody who had asked nothing
    to. Both are the mistakes Motion.kt's header predicts — "it used to be
    written by hand in every file that animated anything, and one of them had
    forgotten to".

A39. **The word "streak" was shipping, on screen.** It is the first entry
    on §10.2's Never list and on the brief's §12, and `Copy.kt`'s own header
    says "Never used anywhere: streak" — which made the header false about the
    file it heads. It was in the fourth tour card: "No gamified streak
    counters or cold badge scores." Naming the competitor's mechanic puts a
    streak counter in the reader's head on the fourth screen of the product
    that exists to refuse it.

    The tour exists against S17's "four questions, no tour" by the owner's
    call (A25), and its words were never held to §10 the way the rest of the
    file is. Four rules were being broken on four cards: the Never-list word;
    a duration ("0:42") on a mock voice note, which is a count attached to
    reading on the one surface Law 2 guards hardest, twenty lines from a
    comment in the same file saying exactly that; three of the four bodies
    describing the product by *negation*, where §10.1 asks for the true small
    thing and §12 says grace is the interface being unbothered rather than
    reassuring; and the wrong nouns — a note is *left*, never pinned, because
    being found later is the beat (§11); "reflections" is the cards, which are
    a different object; and a burning fire is not "an ember", which is what
    you keep when a book is finished. The section's own comment named
    Duolingo as the model.

    Also gone: the app's only first-person-plural. "We couldn't find this
    invite" stood on the dead end of the join thread — the screen S16 says
    must show "a person, not a product" — and "we" summons a support desk
    onto it. Every other S25 line in the file names the thing that failed
    rather than the company that failed it.

    And §10.3's fourth notification now posts. "Ruth is reading Mark" had a
    switch on S19, a channel in Android's settings and no call site anywhere.
    It is the one of the six that cannot ride the background pull — presence
    is ephemeral and socket-only, and a quarter-hour-old "so you can read at
    the same time" is a lie — so it posts from the live roster, once per
    arrival rather than per heartbeat, and only while Ribbon is running. That
    limit is stated in `RoomWatch`'s header and in A34.

A39a. **The gestures had no tap equivalents, on the app's central act.** §11
    Motor is one sentence — "every gesture has a tap equivalent" — and four
    places did not keep it.

    **A verse.** The whole reading interaction is raw pointer input on the
    text: a long-press-drag lifts a verse into the toolbar, a tap opens what
    is at it. The text node is wiped and rebuilt as one accessibility node
    per verse — and each carried a label and nothing else. So the semantics
    tree exposed the verses as read-only strings and exposed no action at all
    for either gesture, which closed leaving a highlight, leaving a written
    note and leaving a voice note: everything §1 says the product is for.
    It closed them to anybody who cannot hold a press for the platform
    timeout and then drag, as well as to a screen reader. Both actions are on
    the node now, the lift with its own haptic (§9.3). Extending a range
    stays drag-only, which is honest — the toolbar acts on whatever is
    lifted, and one verse is the common case.

    **The presence lozenge** carried a sentence and no action, and the
    sentence was on a merge root that takes the focus for itself, so the node
    holding the gestures was never landed on — the exact mechanism
    `Hearth.kt` documents for the fire and fixed there by merging. Following
    the one person present, opening the panel, and "read quietly" — the only
    route to reading quietly anywhere in the app — were all closed.

    **The speak control's** two custom actions sat on an unmerged, unlabelled
    node whose descendants carry text, so by the same rule they existed only
    in source. It is a merge root now, and says what can happen rather than
    repeating "Release to leave it", which is an instruction for a finger
    that is not down.

    **The ember record** had no drawn way out at all — a root destination
    whose only exit was the system back gesture. It has a chevron now, drawn
    rather than through `RibbonScreen`, because the ember and the book's name
    both flow in from the shelf (A22) and a collapsing bar would take the
    name out of that pair.

    Two smaller ones: onboarding's back and sign-in controls were 36 dp —
    eight under the floor deviation 12 sets — unlabelled, and had their
    indication switched off, so they were undersized, silent and gave nothing
    back under a finger. They are the app's own `BackChevron` and
    `QuietControl` now, which is what they were hand-copies of. And three
    screen-reader labels spoke a person's full name where every visible
    surface says a first name — including the note card, whose label is the
    *only* place its author is named, and the gutter mark, whose own doc
    comment quotes §11's "Note from Ruth, verse 9, not yet found".

A40. **The seams.** The states a polish pass is judged by, and the easiest
    to leave half-made.

    **A voice note left offline never reached anybody.** The written path was
    queued and replayed on every foreground (A36); the voice path was left out
    of both halves, so a note recorded on a train drew its pending hairline
    (§4.4) and waited for a push that was never attempted again. Worse, the
    replay loop pushed the row without the file — `RemoteSync.push` only
    uploads audio when it is handed one — so even a queued voice note would
    have arrived as a waveform on somebody else's phone with nothing behind
    it. S25's "note failed to send" and §6.10's "notes queue with hairline
    marks" both describe a queue; half of one existed.

    **The background worker leaked a whole app every fifteen minutes.**
    `AppModel` is a `ViewModel`, and one built outside a `ViewModelStore`
    never has `onCleared` called — so A34's worker left an orphaned
    `ConnectivityManager` callback (A26 says it "has to be unregistered"), a
    live Realtime websocket with its own heartbeat and reconnect loop, and an
    uncancelled scope behind it on every run, forever. It also ran a GitHub
    update check each time. There is a `shutDown()` now, and
    `load(forBackgroundPull = true)` skips the three launch-time side effects
    a pull that exists to post a notification has no use for. A34's own note
    says this route must stay cheap; it was not.

    **A licensed translation offline was a blank page.** Every chapter of a
    non-bundled edition draws its running head and then fetches — and
    `ensureRemoteChapter` swallows every failure into a null, which the call
    site dropped with `?.let`. The effect's keys never changed, so it could
    never retry while the book was open, and nothing watched the network. All
    three licensed editions are configured and selectable in S20 today, so
    offline on NKJV was a heading over 320 dp of nothing with no line and no
    way forward. It names what happened, offers the one action that helps
    (§08), and re-keys on the connection so coming back online retries
    without a tap. Waiting stays wordless, because §08 forbids the indicator
    and a skeleton reads as fake text.

    **Sign-in blamed the person for a network failure.** `verify()` caught
    `Throwable` and said "That code didn't work" — to somebody whose phone had
    simply lost its connection. `sendCode` twenty lines above already told the
    two apart, and `SupabaseClient` reports an IOException as status 0, so
    both the distinction and the string existed. It defaults to the
    unreachable line now: an unknown failure is never a reason to accuse the
    person (§12).

    **A gradient glow behind the Wave, for the third time.** The first tour
    card drew the mark on a 160 dp radial gradient — a gradient hero and a
    glow behind the mark, two separate entries on §13's never-ship list and
    the exact thing §7 says about the icon. A19 cut a light-wash under the
    room's fire for this reason and A25 rebuilt the fourth tour card off a
    gradient circle; this card was missed by both. The mark sits in a `well`
    now, which is the honest way to make it read as held: a recess in the
    page, rather than light coming from nowhere.

A40a. **Two repairs that were themselves defects, and the queue that was
    still missing.** An adversarial pass over this work found three things.

    **The presence form's new semantics closed the panel it opened.** A39a
    gave the lozenge the action §11 asks for by merging the Box the gestures
    sit on — and that Box wraps the *expanded panel* as well. Compose's merge
    swallows descendant merge roots, which is the rule `NoteCard` states in
    this codebase, so with the panel open every control inside it — each
    row's follow and thinking-of-you actions, and "read quietly" — collapsed
    into one unactionable label. The merge is attached only while the form is
    collapsed now; the open panel's children speak for themselves and
    predictive back closes it.

    **The ember record's new way back scrolled away.** A39a put a chevron on
    the screen that had none, inside the scrolling column — which is verbatim
    the defect A35 had just removed from S12, named in that entry's own
    words. It is outside the scroll now.

    Both are the same lesson twice: a repair copied from a fix is not the
    fix, and the second half of each of those entries was the half that
    mattered.

    **Three offline mutations still had no queue.** A36 gave one to note
    pushes, note deletes and highlight deletes; A37 gave one to invites; A40
    gave one to voice notes. `addHighlight`, `markQuietDay` and — worst —
    `answerCard` pushed once through a bare `runCatching` and were forgotten.
    The card answer is the damaging one: `answerCard` decides whether a card
    opens from *local* state, so an answer given offline leaves the card
    sealed here and never reaches the backend, while the merge unions the
    local answer straight back in on every pull — so the device goes on
    believing it was recorded, nobody else ever sees it, and "This opens when
    everyone has answered" never comes true. The one object in the app
    explicitly blocked on everybody was the one whose answer had no queue. A
    highlight (S06) and a marked quiet day (§4.7, an act of care performed in
    public) were likewise invisible to the room for good.

    Also: `WayInButton` — the control that opens the book, the loudest thing
    in the app — never said `Role.Button`, so it announced as a line of text
    while `QuietControl` and `BackChevron` both said it.

A41. **The four cuts that were left, and the first of them is the product.**
    A38 swept the app's animation primitives and fixed ten places still
    changing on one frame. It found these four as well and did not fix them,
    because the pass it belonged to had five named subjects and these were
    not among them. They are the four, in the order they matter.

    **Somebody else's highlight arrived on a single frame.** This is the
    moment the whole product is for — the other person marks a verse and it
    turns up under your eyes, on the page you are already reading — and it
    happened the way a rendering glitch happens: a 24% wash simply *was*
    there, in the periphery, with nothing to say it had just come. §9.1 opens
    "everything breathes rather than blinks" and this was the blink, on the
    one surface §13 will not allow a spinner, a toast or a badge on, so the
    wash coming up *is* the whole notification.

    Taking one back was the same in reverse, and a second person marking a
    verse you had already marked stepped the colour straight to its deeper
    multiply. All three are now one animation: the page holds the washes it
    last settled on, and every wash eases from there to where it is now — up
    from nothing, down to nothing, or across from the old colour to the new.
    Deliberately not keyed to a clock, so a chapter you have just opened
    draws its highlights already there rather than fading a page of them in
    at you; an arrival is a thing that happens *while you are looking*, and
    everything else is just the page. `arrive`, not `settle`, because §9.1
    files presence appearing under the first and a highlight is somebody
    being present at a verse.

    The colour and alpha moved out of the draw and into composition to do it,
    which is where they always belonged: nothing about "what colour is this
    verse" needs the text layout. Only the rectangles do.

    **A note mark reported both of its own state changes as cuts.** The mark
    in the gutter exists to say two things, and said both on one frame.
    *Found*: you open somebody's note, the breath stops and the opacity drops
    — and because a breath is a moving value, where it dropped from depended
    on where in the four seconds you happened to tap, so the same act looked
    different every time. The breath is faded out now rather than switched
    off. *Landed*: a note written offline draws hairline and becomes solid the
    instant it reaches the server, which is the only sign this app gives that
    what you wrote is now somewhere other than this phone (§4.4 forbids the
    spinner, the toast and the retry button, and is right to). The ring
    thickens inward into the filled dot over a settle instead.

    The second one needed the mark redrawn as one shape: a filled dot is a
    ring whose stroke has opened all the way to the middle, so both kinds and
    both states are now ends of a single number rather than four pictures in a
    `when`. That is the difference between something that can be animated and
    something that can only be cross-faded.

    **The presence panel's rows popped.** Somebody arriving while the panel is
    open is the panel's entire subject, and it was the one thing on it that
    happened between two frames: a row appeared, everything under it jumped by
    its height, the panel changed size around them. Leaving was worse — a face
    you were looking at was simply not there.

    Each row opens and closes in its own space now. That needed a roster
    rather than the list: a list you iterate cannot animate a departure,
    because by the time you would animate it the departing person is already
    not in it. `rememberRoster` keeps a leaver for exactly as long as the exit
    lasts, in the place they were standing rather than at the end of the
    queue, and forgets them after. Somebody back before their exit finishes is
    simply here again.

    **The account section swapped three layouts with no transition.** Tapping
    "Sign in" replaced a control and a sentence with the whole inline form,
    and signing out replaced the form with them again — the section changing
    height under your thumb with nothing moving. What makes this one worth
    writing down is that the update card *directly below it* is the same shape
    and already carries the argument, in A-OTA's own words: "four cards ...
    each appearing and vanishing on the frame its state changed". The fix had
    been written, one section away, and not carried across. It is four named
    phases and an `AnimatedContent` now, on the same tokens.

A41a. **The rest of what the audit found: what the app does not say, and the
    two things it says that it should not.** A39a fixed §11's "every gesture
    has a tap equivalent" on the app's central act. Going back over the same
    ground with the other half of §11 — *say what a thing is* — turned up
    fifteen more, none of them hard, all of them invisible to anybody who can
    see the screen.

    **Five fields had no name.** A25 gave onboarding's three fields one shape,
    one target and one prompt-beside-the-caret, and A35a did the same on You;
    neither gave a field a *name*. Compose takes an accessible name from a
    label, and all five of these draw their prompt as a sibling `Text` in the
    decoration — so what a screen reader met was the word "Optional", or
    "Search", or nothing at all, and then an unlabelled edit box it had to
    guess the purpose of. The five: the note composer (S05 draws neither a
    label nor a prompt over it, so there was nothing on screen to borrow
    from — the app's central writing surface), the room name on the start
    sheet, the book search, the room name on You, and onboarding's shared
    `CentredTextField`, which is three fields including the one thing S17 will
    not let anybody skip. Every prompt is now cleared from the tree and the
    name is on the field, which is also one stop instead of two.

    **Six controls had no role.** The next chapter — the control that carries
    you out of a finished chapter and into the next one, the app's whole
    forward motion — was a `Text` in a tappable `Box`, announced as a line of
    type. So were the transcript disclosure (announced as the word
    "transcript": the thing, never the act, and no way to know which way it
    was pointing), the rows of a book search, "take back" in the composer, and
    the tap that sends the highlight label away early — which meant the only
    deliberate way to dismiss it did not exist unless you could see it. The
    eight ink swatches said `selected` with no role at all, so a screen reader
    named an ink and said nothing about it being one of eight with one taken;
    they are `RadioButton`s, which is what a one-of-many is.

    **Nothing in the app was a live region.** Two lines change because of
    something the person just did, with nothing taking focus and nothing else
    moving: the passkey result on You, and sign-in's only answer when it goes
    wrong. §13 forbids the toast and the alert, correctly, which makes these
    two lines the whole of what the app has to say — and it was saying them to
    nobody. §11's "colour is never alone" has a twin: a result is never
    silent. Both are `LiveRegionMode.Polite` now, and the passkey line only
    while it is a result rather than the standing explanation.

    **Two screens drew their own title and so had no heading.** Every pushed
    screen gets one from `RibbonScreen`'s top bar (A35's finding on S12). The
    chapter list and the finished chapter draw theirs by hand, so a screen
    reader navigating by heading found nothing on either.

    **The eight ink swatches were 34 dp across** — ten under the floor §11 and
    deviation 12 set and the app keeps everywhere else, and specifically on
    the eight-across case, where the columns touch, so a miss lands on the ink
    *next door* rather than on nothing. Marking a verse in the wrong person's
    colour is a worse failure than not marking it. They are 44 now. The
    argument for 34 had been that eight at 44 are wider than a phone — which
    is true, and is exactly why that row has scrolled since it was written:
    the trade was never width against reach, it was width against a scroll
    that was already there. `InkSwatch` lost the parameter with the last
    caller that wanted anything but the floor.

    **And two things that should not have been on screen at all.** The primary
    sign-in control read *Continue with Auth0*: the one place in the product
    where somebody about to read Scripture with their partner was shown the
    name of a vendor. The identity provider is a decision this app made, not
    something the reader has an account with or has heard of; §12's voice has
    no room for an infrastructure brand on the control that opens the app. It
    says what actually happens — a browser opens — and nothing else.
    `INKS_FROM_WHEN_THE_ROOM_WAS_TWO` was the second string in `Copy.kt`
    written and never read, which is the defect A39 is about; it is gone, and
    so is `ScreenTitle`, a design-system component written for pushed screens,
    superseded by `RibbonScreen`, and used by nothing. A design system with
    dead parts in it is a design system people stop trusting to be the answer.

A41b. **Highlighting, which nobody had ever looked at.** The reading surface
    had one picture in the look book and it was a clean page: no wash, no
    overlap, one note mark far down it. So the thing the product is *for* —
    two people marking the same chapter — had never been seen, and it was
    carrying four separate defects, three of them years old and one of them
    mine, from ten minutes earlier. Every one of them was obvious in the
    first picture. A24, again, and more expensively than usual.

    **An overlap came out dimmer than one ink.** §4.5 says two inks on one
    verse make a third colour, that the colours are never averaged, and that
    the overlap is the point. The code multiplied the ink values — and
    multiply is how two *pigments* combine on white paper, where each one
    subtracts. Ribbon's page is unlit ground at `0x0B0B0A` and a wash on it is
    translucent *light*. Multiplying two inks there makes a near-black
    pigment, so: crimson alone reads at 3.2× the ground's luminance, teal at
    4.0×, and the two together at **2.7×**. Raising the alpha — which the old
    ramp did, by 14% per extra ink, in the name of deepening — made it worse,
    because it moved the result further toward that near-black. Two people
    marking the same verse punched a hole in the page.

    They are screened now: `1 − (1−a)(1−b)`, multiply's mirror for light. It
    is symmetric, so the wash does not depend on which ink the loop happened
    to meet first — "we both marked this" is not an ordered fact — and
    crimson and teal make a warm bronze that is neither and brighter than
    both. Still one arithmetic fill rather than a `BlendMode` pass per ink,
    for the reason the original comment gives: a blend mode composites
    against what is already on the canvas, which here is the ground. The ramp
    is +5% per ink capped at 36%, which is what now keeps §4.5's "never a
    block of colour" true in a direction that climbs toward white: eight inks
    screened are very nearly white, and at 36% Scripture still reads over
    them at 5.3:1, against 8.7:1 for the two-person case the product is
    actually about.

    **It also departs from a line in the build book, which is why the numbers
    are here.** §4.5 says in terms: "Overlapping highlights blend — multiply,
    not stack". S06 says, of the same thing: "This is desirable and must
    survive both themes — **check every one of the 28 pairs against the ground
    before ship**." Those two instructions are in conflict on a dark ground,
    and the second is the one that settles it, because it is a measurement
    rather than a preference. Run for the first time, **all 28 pairs failed**:
    every overlap came out no brighter than one ink alone, crimson over teal
    at half the luminance of teal by itself. Under screen, none of the 28
    fail. A check that has to be done by hand before every ship is a check
    that gets done once, so it is `HighlightWashTest` now and runs on every
    build — all 28 pairs, Scripture's contrast at every depth from one ink to
    eight, and that an overlap is a third colour rather than one of its own
    inks.

    This is the one call in this pass worth overruling if the owner disagrees,
    because it contradicts a sentence of the book rather than filling a gap in
    it. iOS multiplies and is untouched; if this stands, it is the change iOS
    takes next.

    **A highlight on a wrapping verse drew one line and then slivers.**
    `enclosingRects` asked `getHorizontalPosition` for both ends of every
    line. That is right for an end *inside* a line and wrong for a line's own
    end: at a soft wrap the offset already belongs to the line below, so it
    answers with the next line's left margin, and on a hard break it lands on
    the break. Every line a verse covered in full therefore got a right edge
    out near the left margin, and `max(a, b)` collapsed the rect to a
    hairline in the indent. On poetry, where lines are short and indented, a
    highlight across four lines of the Psalms drew one line and three
    slivers. The line's own right edge is the answer when the verse runs past
    it; only a verse that *stops* part-way needs an offset.

    **Every wash was a stack of boxes, and the boxes fought each other.** One
    translucent rounded rectangle per line, which gave three faults at once.
    The rectangles bleed past the glyph box, so consecutive lines overlapped
    by twice the bleed and translucent over translucent is darker — a verse
    over three lines drew two dark stripes through itself, at exactly the
    places the eye crosses. Each rectangle was then nudged up or down by a
    stable hash "so it reads as ink soaking into paper", but moving a whole
    line box is not what soaking looks like, it is what a layout bug looks
    like; the rows staggered and the stripes moved with them. And the corner
    radius came from the same hash, so one line of a passage was rounder than
    the next.

    All three go away by unioning the line boxes into one path and filling it
    once: seams cannot darken when there is one fill, the interior corners
    vanish, and the shape that comes out is the shape of the words, stepping
    in and out at the ends of lines — which is the irregularity that was being
    simulated, and it is free. What is left of the hand is horizontal: the
    end of each line's wash overshoots by about half a millimetre on a stable
    hash, the way the end of a pen stroke does. Nothing vertical moves.

    **And it was a slab.** Scripture is set on generous leading, so a line's
    box is half again as tall as the letters standing in it; washing the whole
    box made a multi-line highlight one unbroken block with the words floating
    in the middle of it, which is a *selection*, not a mark. The wash hangs
    off the baseline now — over the capitals, under the tails, and no further
    — so it sits on the words the way a stroke does and the leading stays open
    between one line and the next. Clamped to the line box at both ends, so a
    tall capital or a long descender can never let one line's wash touch
    another's.

A41c. **The toolbar put its two verbs off the screen, and I did it.** A41a
    brought the eight ink swatches up from 34 dp to the 44 the rest of the app
    keeps. The whole bar scrolled as one, so the extra width pushed `write`
    and `speak` past the right-hand edge — and the eight-across case is
    precisely a room of two, which is the shape of room this product exists
    for. The ink is the thing you can already do by holding a verse; the two
    verbs are the reason the toolbar is there at all, and they were behind a
    scroll nobody is told about.

    Only the inks scroll now; the rule and the two words are pinned. At eight
    inks the eighth is a short slide away and both verbs are where they always
    are, and at one — a room of three or more — nothing moves at all. The
    floor was never the thing to trade: the row has scrolled since it was
    written, and the trade only looked like a trade in source.

    Four screens joined the look book to catch this and the entries above: the
    page in use, the toolbar, the shelf, and S16 — the screen the build book
    calls the most important conversion surface in the product, which had
    never been in a picture, which is how it kept a monogram where S16's
    anatomy names a portrait until somebody read the source (A37).

A41d. **A highlight you make yourself is drawn travelling.** A41b made the
    wash arrive rather than blink, and that was right for a highlight turning
    up from the other person's phone — it eases in where it lies, because
    nothing travelled across *your* page when somebody else marked their own
    copy, and drawing a stroke would be the app acting out something that did
    not happen.

    Your own is a different fact and deserved a different animation. It is
    the one act on this surface that is entirely yours and the only one the
    app can honestly show as the movement of a hand: you lifted a verse, you
    chose an ink, and the mark is *made*. It ran as the same 320 ms fade as
    everything else, so the most tactile moment in the product — §4.4 calls
    marking a verse the app's central act — felt like a state change.

    The wash is at full colour from the first frame and revealed along the
    words instead, in reading order, line by line and left to right within a
    line. Measured in ink laid down rather than in lines, so a verse of four
    words and a verse of four lines take the same time and travel at visibly
    different speeds, which is what a pen does. The clips are one per line and
    disjoint, so the shape is never filled over itself and a half-drawn stroke
    is exactly as dark as a finished one.

    The last ten dp of it runs out into nothing rather than ending on a hard
    vertical edge, because a hard edge travelling across Scripture is a *wipe
    transition* — the one part of this the eye reads as a screen doing
    something rather than as ink. `settle`, not `arrive`: a mark being made
    takes the time a hand takes, and it is not the same clock as something
    turning up.

    Under reduce motion both of these are what §11 says they are — the wash
    is simply there — because the tokens decide that in one place and neither
    of these animations writes the branch out itself.

    The look book holds the stroke half-way across, on a held clock. A still
    frame is the only way to check that it reveals along the words rather
    than wiping the block, and that is not a thing source can be read for.

A41e. **A selection could not be adjusted, at all.** S06's *Extending* reads:
    "Drag handles at both ends of the selection, snapping to verse boundaries
    by default and to word boundaries when dragged slowly." There were no
    handles. The one way to select more than a verse was to keep your finger
    down after the long press and drag, and the moment it lifted the selection
    was final.

    Overshoot by a verse — which is easy, because the thing under your thumb is
    the thing you cannot see — and there was no way back at all. Not a shorter
    way back: none. You marked it wrongly, tapped it, removed it, and started
    over, on the app's central act. That is the sort of gap that never shows up
    in a screenshot and is the first thing anybody hits using it.

    Two handles now, one at the first line of the first verse and one at the
    last line of the last. Not the corners of the box the selection fits
    inside: a verse that wraps is wider than its own last line, so a bounding
    box puts the tail handle at the end of the widest line, which on a
    selection ending mid-paragraph is somewhere in the middle of the *next*
    verse. The look book caught that in the first picture of them.

    They are 10 dp drawn in a 44 dp target (§11, deviation 12) and in the
    accent, because they are the app's own furniture rather than anybody's
    ink — §4.5 keeps chartreuse out of the eight and out of the reader's
    hands, and this is the same reasoning that puts the caret in it.

    Every drag has the tap equivalent §11 requires, as two custom actions on
    each handle — *a verse further on*, *a verse back*. A handle you can only
    drag is a handle that does not exist for half the people S06 was written
    for, and the last pass (A39a) found exactly this defect on the gesture
    that opens the toolbar in the first place.

    **What is deliberately not here:** S06's second clause, word boundaries on
    a slow drag. `VerseRange` holds a start verse and an end verse, so a
    sub-verse highlight has nowhere to be stored — it is a change to the shared
    model on both platforms and to the backend, not an Android drawing
    question. Written down rather than half-built.

A41f. **Your ink meeting theirs, which was drawn as theirs being wiped away.**
    A41d made your own highlight travel across the words. On bare Scripture
    that was the whole story; on a verse somebody else had already marked it
    was wrong in a way that inverted the meaning of the act.

    Only one wash is drawn per verse, and by the time the stroke started that
    wash had already become the mixture of the two inks. So at the head of the
    stroke, with nothing yet revealed, **their highlight was not on the page**
    — it came back from the left as the blend. Marking a verse alongside
    somebody, which is the one moment on this surface where the two of you are
    demonstrably in the same place, read as their mark being erased and
    replaced by yours.

    Their ink stays where it is now and the pen mixes it as it passes: ahead of
    the tip, their colour, untouched; behind it, the third colour the two inks
    make; and at the tip the one crosses into the other over about ten dp,
    which is what a wet stroke laid over a dry one does. The two regions are
    separate clips, so the colours are never composited over one another and
    the mixture stays the arithmetic one rather than one wash dimmed by
    another. Nothing flashes, nothing overshoots, nothing glows, and nothing is
    counted — §13 forbids all four and none of them is needed. It is the colour
    arriving, which is the whole point of two people reading the same chapter.

    **A race went with it.** What is already on the page was being read from
    the arrival animation's own "previous" state, which turns over the moment
    *that* animation ends — 320 ms against the stroke's 400 — so their colour
    would have dropped out from in front of the pen for the last fifth of the
    stroke. It is captured once, when the stroke starts, and held for its
    length.

    The look book holds the instant the pen touches down, because that is the
    frame that was empty. The travelling part is `theStrokeTravelling`.

A41g. **A mark on a phrase.** A41e built S06's two handles and stopped at its
    second clause — "snapping to verse boundaries by default and to word
    boundaries when dragged slowly" — because `VerseRange` held a start verse
    and an end verse and nothing finer, so a mark on part of a verse had
    nowhere to be stored. Which is to say it could not be made. The owner
    asked for it; here it is, and the model, the wire and the schema all move.

    **The model.** `VerseRange` gains `startChar` and `endChar`, offsets into
    the *verse's own text* at each end, and `charTranslation`, the translation
    they were measured in. All three are optional in the strong sense: a range
    of whole verses writes none of them and is byte-for-byte what it has
    always been, on the wire and in the table. The migration's columns are
    nullable for the same reason — iOS keeps making whole-verse marks and
    never has to learn.

    **Why the translation travels with the numbers.** Translation belongs to a
    *person* (S20 puts it in Text settings), not to a room or a reading, so
    two people in one room can be reading different words for the same verse
    and an offset into one is nonsense in the other. A reader whose
    translation does not match sees the whole verse marked. That is the honest
    half of what the mark knows: somebody marked something here. Pointing at
    words that are not on their page would be a lie, and hiding the mark would
    lose the fact.

    **The wash had to stop being per-verse.** Two people marking *different*
    phrases of one verse is the case this exists for, and with one wash per
    verse either mark would have coloured the whole of it — claiming §4.5's
    overlap across words only one person had touched. Every mark's two ends
    are a boundary now, the verse is cut at all of them, and each piece
    carries exactly the inks that cover it. So a verse can read crimson, then
    the third colour, then teal, which is what actually happened to it.

    A verse's text and its place on the page are also two different coordinate
    systems that do not run in step — a verse of poetry is several runs with a
    paragraph spacer appended between them — so every run now records both
    ends of the correspondence and a phrase resolves to however many page
    ranges it really occupies.

    **The handles snap to words, and that is S06's default too.** The
    slow-drag mode is deliberately not built. A mode you enter by accident,
    according to how fast your thumb happened to be moving, is not
    discoverable and not repeatable: you cannot aim at it, and the same
    gesture gives two answers. It would also be the only speed-sensitive
    control in an app whose whole argument is patience. Instead the handle at
    the start of a mark snaps back to the beginning of its word and the one at
    the end snaps forward to the end of its — which *is* verse-boundary
    snapping, because the first and last words of a verse are its edges. A
    handle dragged to either end gives exactly the whole verse and stores it
    as one, so the default S06 wants stays the easiest thing to hit while the
    precision it wants is always there rather than hiding behind a speed.

    Each handle carries four tap equivalents (§11) rather than two: a word
    either way, and a verse either way.

    **Still not here:** a sub-verse mark cannot be shown to somebody reading
    another translation as anything narrower than the verse. Doing better
    needs the marked words themselves stored and searched for in the reader's
    text, and Scripture under licence is not ours to copy into our own
    database to make a highlight prettier (§16.8). The whole verse is the
    right answer until that is not true.

A42. **A room reads one version.** Owner's call, and the first entry in this
    ledger that reverses a *named principle* rather than filling a gap in one.

    §2.6 is titled "Translation is a personal setting, not a room setting",
    and S20 repeats it as a rule. Its reasoning is one sentence: *"You read
    the Berean, Ruth reads the WEB, and her note still lands on verse 9
    because notes pin to verse addresses, not to text offsets."*

    That reasoning was sound for as long as it was true. A41g put a mark on a
    **phrase** into the product — a text offset — at the same owner's request,
    and an offset into the Berean means nothing in the WEB. A41g handled it
    the only way §2.6 allows: a mark on part of a verse, shown to somebody
    reading other words, widens to the whole verse. Which is honest, and is
    also the two of you looking at the same page and seeing different marks on
    it. One version per room is what makes a phrase mean the same words in
    both hands, and it is the simpler product besides.

    **What it costs, stated plainly.** §2.6 carries an authored detail: "when
    Ruth's note quotes the verse, it renders in Ruth's translation, in small
    type, so you see the words she was looking at", which S04's note anatomy
    repeats. That is gone, because the words she was looking at are now the
    words an inch above her note. The quote is not merely redundant now, it
    had become *wrong*: `Person.translation` is still written per person, and
    nothing updates a bystander's copy when somebody else changes the room's
    version, so the test "does the author's differ from mine" would have
    started firing on two people reading identical words and quoted a verse
    in a translation neither had open. It is removed rather than left to rot.

    §2.6 also looks forward: differently-versified translations become "a real
    engineering constraint" the day one is added. That constraint gets
    *smaller* here, not larger — a room on one version never has to map
    versification between two people mid-sentence.

    **Where it lives, and why two columns.** `rooms.translation` is what
    everyone in the room reads. `readings.translation` is what a given book
    was read in, and it exists because S11 calls a finished reading immutable
    and "the source of the printed keepsake". An open book follows the room; a
    finished one keeps its words. A room that changes version next year must
    not silently re-word a book it has already read, under notes left about
    those exact words.

    Changing it takes effect at once, including in a book already open, rather
    than waiting for the next one: a setting that appears to do nothing is a
    setting people press twice. Any member may change it — a room is not owned
    (§6.7).

    **What stayed personal.** Text size, line spacing and red letter. Those
    are about eyes, not about words, and a shared text size would be hostile
    to the person who needs a larger one (§11). The Text screen now says which
    half is which, under each group, because a setting that quietly changes
    what somebody else sees has to say so before it is touched.

    **`profiles.translation` is deliberately left in place.** iOS reads it and
    §2.6 is still true over there until somebody takes that pass. This is an
    Android-only change to a shared backend, and the honest shape of that is
    an added column rather than a moved one: both platforms keep working on
    one account, each right about itself. Android still writes the personal
    field so iOS sees something sane; it simply no longer sets a page from it.
    When iOS follows, the profile column can go — dropping it now would break
    a shipped client to tidy a schema.

    **A41g's `charTranslation` stays**, and is now what it should always have
    been: a rare guard rather than the everyday case. A phrase marked before
    the room changed version still widens to its whole verse instead of
    pointing at the wrong words.

    **Two defects in the first cut of this, found by re-reading the diff.**
    Both were about the same row reaching the server. The push captured the
    room *as it was at the moment of the change*, so a rename in the same
    breath would have been overwritten by the queued push putting the old name
    back — it reads the current room at push time now, which is what the
    rename path already did. And `merge()` let a pulled row set the version
    unconditionally, so a pull arriving before the push landed handed the old
    version back and the setting appeared to undo itself.

    `pendingRenamePushes` already existed for exactly that second defect, on
    the name, and had the right shape; it guards the whole room row now and is
    called `pendingRoomPushes`. One marker rather than two, because name and
    version travel in one row and a second marker would have been two half
    locks on one door.

A43. **The pull-up was a frame behind the finger.** Owner, on a Pixel 9 Pro
    XL: *"the performance of swiping up on the fire is not very good"* — and
    then, when asked nothing: *"so it's not exactly a low-end Samsung."* That
    second sentence is the finding. A phone like that does not struggle to
    move a rectangle, so the gesture was not expensive; it was late.

    **The cause.** `BookSheet` kept its one number in an `Animatable`, and
    `Animatable.snapTo` is a suspend function — it has to be, it takes the
    animation mutex. So the drag was written the only way that shape allows:

        internal fun drag(delta: Float) {
            val next = (pull.value + delta / travel).coerceIn(0f, 1f)
            scope.launch { pull.snapTo(next) }
        }

    `scope` is a `rememberCoroutineScope`, whose dispatcher is
    `AndroidUiDispatcher`. That dispatcher does not run a block where it was
    launched: it queues it and runs it at the next message-loop turn or the
    next choreographer frame, whichever comes first. A pointer event is
    delivered *inside* a frame, before that frame's draw — so the position
    landed after the frame it belonged to had already been drawn, and the book
    was one whole frame behind the thumb. Every frame, for the length of the
    pull. On a 120 Hz screen that is eight milliseconds that are never made
    up, plus a coroutine allocated and a mutex taken for each of the hundred
    or more touch samples a second the panel reports.

    A lag that is *constant* is exactly the kind that reads as bad
    performance rather than as lag: nothing drops, nothing hitches, the book
    is simply never quite where the finger is.

    **The fix.** The pull is a plain `mutableFloatStateOf` now, written
    synchronously from the pointer handler, and the settle is a top-level
    `animate` in a `Job` the sheet holds. Everything that reads `progress`
    already did so inside a `graphicsLayer` or a draw, so a drag invalidates
    drawing and nothing else — unchanged, and that part was right.

    What the animation mutex was quietly doing has to be done by hand: a new
    drag has to take the book off a settle that is still running. `engage()`
    and `drag()` both cancel the settle, and every path into a drag calls
    `engage()` first, so the second cancel is belt and braces. The callbacks
    still belong to the movement that actually finished, because a cancelled
    coroutine never reaches the line after `animate`.

    **The fire, while it was open.** The one thing in the app that is always
    moving redraws at 30 Hz (§4.1), and every frame it allocated: a native
    `Path` per coal, per fissure, per tongue, per oval; a native `Paint` and
    `BlurMaskFilter` per blurred draw; and — the largest of them — a boxed
    `Offset` per point of every outline, because a `List<Offset>` cannot hold
    a value class unboxed. That last one was on the order of four hundred
    objects a frame, fourteen thousand a second, on the thread that is also
    meant to be tracking a finger.

    All of it is kept scratch now: four paths, two pairs of `FloatArray`, a
    paint per blur radius, one layer paint for the state cross-fade. The
    arithmetic is untouched, so the picture is untouched.

    Shared mutable scratch has exactly one failure mode and it is a bad one,
    so `FireScratchTest` asserts the property that rules it out: **drawing is
    a function of its arguments** — same arguments, same picture, whatever was
    drawn before. The look book cannot test this, because the fire's breath
    seed is `Random.nextDouble()` by design and no two runs draw the same
    frame. Checked against a deliberately broken `reset()`.

    **One blur pass out of five was buying nothing.** A mask-filter blur is
    not a cheap draw: Skia renders the shape's coverage to an offscreen mask,
    blurs it, and draws through it, so each blurred path is its own small
    render pass. Five a frame — three sheath tongues and the two hot-air wisps
    — of which the wisps were softening the rim of an oval that is 3.5%
    opaque at its brightest. They are radial gradients now, which is what a
    blurred flat oval is trying to look like, and have no rim to soften.

    **What is left, and deliberately not done blind.** The sheath's three
    blurs remain, and the honest fix for them is the one SwiftUI uses and the
    code's own comment wishes for: record the sheath into a `GraphicsLayer`,
    hang a hardware `BlurEffect` on it and composite once, instead of blurring
    three paths separately. It is available at minSdk 33. It is not done here
    because it changes how the sheath *blends* — the fire accumulates
    additively inside one offscreen buffer, and a separate layer composites
    over that rather than adding into it — and that is a change to the one
    object on the home screen, made against a renderer no test in this repo
    executes. The same goes for the full-screen `alpha` in `peeled`, which
    forces a screen-sized offscreen buffer for every frame of an opening.
    Both want a profiler and a phone, in that order, and the owner has the
    phone. The two fixes above want neither.

A44. **"Preferences do not stay between updates of the app."** Owner's
    report. What follows is what was found, including the part that was not
    found, because a fix shipped under a cause nobody established is a guess
    wearing a commit message.

    **What was ruled out.** `filesDir` and `SharedPreferences` both survive an
    ordinary update; nothing in the app writes state before reading it
    (`AppModel.load` reads the store before it builds the model); there is one
    `LocalStore` in the process; the backup rules exclude only the sealed
    session blob. The leading theory was that a schema change had made an old
    `state.json` undecodable — `load()` answered a decode failure with a fresh
    `AppState()`, and the next `save()` a moment later wrote over the evidence,
    which is silent, total and looks exactly like "my settings went".
    `StateSurvivesAnUpdateTest` was written to prove it and **disproved it**:
    a hand-written file from before notifications, phrases and a room's
    version decodes with its settings intact, because every field added since
    launch carries a default.

    **No code-level cause was established.** The most likely remaining
    explanation is an install that wipes app data — a signing key that does
    not match the installed one forces an uninstall first, and an
    uninstall-reinstall takes `filesDir` with it. No code can prevent that,
    and saying so is more useful than shipping a change that pretends to.

    **What was fixed anyway, because it is wrong on its own terms.** The
    asymmetry in `state.json` is the interesting part: rooms, readings, notes,
    highlights, cards and people are all *caches* of the backend and come back
    on the next sync. The settings are the only thing in that file that
    nothing else in the world has a copy of. So a total reset does not look
    like a disaster — the app fills back in, nothing appears missing, and the
    single visible casualty is the settings. That is why this could happen
    more than once and be reported as a small thing.

    A decode failure now salvages what cannot be re-fetched instead of
    starting empty: the settings, the three §6.1 "asked once" flags, and both
    invite sets (an invite that never reached the backend exists only here,
    and A37's link may already be in somebody's message thread). Each field is
    read out of the raw JSON on its own, so one unreadable field cannot take
    the rest with it. `notifiedThrough` is deliberately *not* salvaged: null
    makes the next merge silent (S19), which after a reset is exactly right,
    because everything is about to arrive at once.

    And the broken file is kept at `state.json.unreadable` rather than
    overwritten. One copy, replaced each time. If this recurs there will
    finally be something to look at, which is the part that was missing the
    first time.

    **What would actually cover a wiped install**, and is not done here: the
    settings are a fact about a *person*, not a device, and `profiles` already
    carries one such fact (`translation`). A `profiles.settings` blob would
    make text size, spacing, red letter, quiet hours and the per-room
    notification switches follow the account onto a new phone, which is the
    only thing that survives `filesDir` being deleted. It is a migration and a
    sync path, and the owner's standing instruction for this pass is UI and UX
    first — *"we will wire everything later"* — so it is named here rather than
    taken.

A45. **The launch mark is the app's to draw, not the system's.** Owner:
    *"the splash screen with the Ribbon being animated doesn't work. It just
    kind of shows it and then fades to it."* Which is a mark that appears
    whole and then fades — the unfurl never running.

    **The drawable was not the problem, and that was established before
    anything was changed.** `theLaunchMarkMoves` already drove the real
    `AnimatedVectorDrawable`, started it, and asserted two frames 600 ms apart
    were different pictures. It passed. Inflated and drawn frame by frame, the
    clip band closed to nothing and opened again over the unfurl's 440 ms, so
    the vector, both animators and both target names were correct all along.

    What could not be established is why the platform declined to play it on
    that phone, and that is the finding. **The system splash is drawn by the
    system, from the app's theme, in another process, before the app exists.**
    There is nothing in it to see, to log, to test or to fix from here, and
    nothing that says the next phone behaves the same. A28 put the mark there
    on the reasoning that Android 12 shows a splash whether or not you ask, so
    the only choice is whose mark it carries. That reasoning still holds for
    the *ground*. It does not hold for anything that has to move.

    So `design/LaunchMark.kt` draws the mark on the app's own first frame:
    the same `splash_wave` vector, the same 440 ms unfurl and 1.04→1.0 settle,
    on the Compose clock, under `rememberReduceMotion()` like everything else
    (§11), and photographed in the look book part-way down and at rest — which
    a system window could never be.

    **What it costs, plainly.** The launch window now carries the unlit ground
    and nothing else, so on a cold start there is the ground alone for as long
    as the process takes to come up, where before there was a static mark.
    That is the trade: briefly only the ground and then a ribbon that comes
    down, against a mark that appears whole and never moves. The owner's
    report is that the second reads as broken.

    It does not *add* time, and §05 is about time. The old build held the
    splash open until the store had loaded **plus a 480 ms floor**
    (`MARK_FLOOR_MS`) so a warm launch could not cut the unfurl to three
    frames — and the unfurl was the thing that never ran, so that floor was
    480 ms of holding a still picture. Nothing holds the window now; it lasts
    until the app's first frame, which is the library's default. The mark's
    animation runs *while* the store comes off disk instead of after the
    window has already been held for it. The mark leaves when the room can be
    drawn **and** the ribbon has landed, whichever is later: leaving on the
    first alone cuts the animation, leaving on the second alone holds a room
    that was ready half a second ago.

    **`splash_ground.xml` is an empty vector, deliberately.** Naming no icon
    hands the slot back to the launcher icon on a plate, which is the one
    thing A28 set out to avoid. An empty vector is the only way to tell the
    platform "the ground, and leave the mark to us".

    `splash_wave_animated.xml` and its two animators are deleted rather than
    left unreferenced. They worked; nothing calls them; A39 is about exactly
    that.

A46. **The hearth's two directions, and a way out that could not be pulled.**
    Two of the owner's findings, which turned out to be the same finding.

    **The Wave.** *"The Ribbon icon at the bottom of the screen looks like
    there's some sort of interaction happening, but it doesn't work very
    well."* It worked exactly as written, and what was written could not be
    performed. `RibbonMotion.OPEN_COMMIT` is a fifth of `OPEN_TRAVEL`, which
    on a tall phone is about ninety dp of finger — a comfortable pull *upward
    from the fire*, which sits in the middle of the room. From the Wave, which
    sits at the foot of the page with the navigation bar under it, there is
    nowhere near ninety dp of glass left to drag through. So the distance test
    could never pass and only the flick could: the way out worked if you threw
    it and did nothing if you pulled it. The page following the finger and then
    springing back is the interaction being seen.

    The lesson in one line: **a threshold has to be measured against the
    screen the hand actually has.** `release` takes its commit as a parameter
    now. The fire keeps the fifth; the Wave gets forty-four dp — one touch
    target, deliberately short, because somebody who has taken hold of the
    thing labelled "close the book" and pulled it has already said what they
    want, and a handle that argues about how far is a handle that is in the
    way.

    The Wave also stopped eating the other direction. It was `draggable`,
    which claims a vertical gesture in *both* directions once slop is passed,
    and an upward drag on it had nowhere to go — the book is already fully
    open and `drag` clamps — so the gesture was swallowed to move nothing.
    A control that eats a drag and does nothing with it is the worst of both:
    not inert, and not working. It is hand-written now and claims downward
    only, exactly as the fire's handle already declined a downward one.

    **The fire, downward.** *"Dragging down from the fire should do something
    ... it should be in-depth room settings when you drag down from the fire
    rather than up."* It now opens the room's own screen — the one the room's
    name at the top-left has always opened.

    The reason it did nothing before is good and is kept: `opensTheBook`'s own
    comment records that `draggable` claimed both directions, so a thumb put
    on the fire and swiped down to scroll the room moved nothing at all while
    quietly building a whole reading screen and tearing it down again. So the
    downward pull is only taken **when the room is at the top of its scroll**,
    where a downward drag has nowhere else to go. Below that it is the
    scroll's, as before.

    The hearth leans with the finger — a square-root falloff onto a
    twenty-eight dp cap, so the first millimetre answers almost one to one and
    the last centimetre barely moves it. That is resistance, not travel: the
    hearth is not going anywhere. Without it this would be another gesture
    that appears to do nothing until it suddenly does, which is the complaint
    this entry started from.

    **The bug this nearly shipped with.** `opensTheRoom` shares a node with
    `opensTheBook`, and `onClick` is *one slot* in a node's semantics — a
    second one replaces the first rather than joining it. The app's front door
    would have quietly stopped working for a screen reader. The room's tap
    equivalent is a `CustomAccessibilityAction`, which sits beside the book's.
    `HearthGesturesTest` asserts both are there, along with each direction's
    threshold and the scroll's priority; none of it is visible in a
    screenshot, which is why it had never been caught.

    **On "dragged up".** The report says the way out of Scripture *"should be
    able to be dragged up to go back to the home screen"*. Down is what is
    built, and deliberately: the book rises from the bottom of the room to
    open (A20), so sending it back down is the same gesture in reverse, and
    one number drives both. The reading here is that the direction was never
    the complaint — the drag was simply impossible to complete, which is the
    defect above. If it still wants inverting on the phone it is one
    comparison.

A47. **The settings screen never stopped laying itself out.** Owner: *"when
    you're in your profile, going from Appearance, for example, tapping works
    very well. Actually, not fully. There's a ton of glitches and stuff."*

    It was not a transition that looked wrong. **Compose never went idle.**

    `LargeTopAppBar` has two titles, not one: a collapsed one in its top row
    and an expanded one in its bottom row, cross-faded as you scroll. It
    builds both out of the same `title` lambda, so every modifier on that
    `Text` was applied to **two live nodes**. For a colour or a font that is
    harmless. For the shared element that flows a settings row's words up into
    the heading (A20's `flowsAsWords`) it is not: two halves of one key, on
    one screen, with neither of them leaving, and a `RemeasureToBounds` bounds
    animation between them that has no fixed point to settle on. The screen
    went on recomposing and remeasuring for as long as it was given.

    Measured, not inferred. Opening Appearance from You inside the flow spins
    until the test harness gives up at sixty seconds; the identical navigation
    with `LocalFlowRoot` absent settles at once; removing the shared modifier
    from the bar's title settles at once. `scaleToBounds` in place of
    `RemeasureToBounds` does **not** fix it, which is what says the resize
    mode was never the problem — the duplicate key was.

    **Why it had never been caught.** No test in this repo had ever put
    `MenuScreen` inside a `SharedTransitionLayout`. The look book draws it on
    its own, so `LocalFlowRoot` was null, `flowsAsWords` degraded to `this` —
    which it does by design, so previews and tests can draw a screen outside a
    flow — and the transition that the complaint is about had never once run
    under test. Both *ends* of it were photographed and both were always
    right. A frame cannot show a layout pass that does not end.

    **The fix: the heading leaves the bar.** `TwoRowsTopAppBar` takes an
    `expanded` flag and would have solved this in one line; it is `internal`
    in material3, and nothing public exposes the distinction. So the bar keeps
    what only a bar can do — the way back, pinned and always reachable — and
    the heading moves into the page, directly above the lede, which already
    lives there for the reason `RibbonScreen` had already written down: it is
    content, and it scrolls away like content. One node, one key, nothing to
    disambiguate.

    What that costs: the heading no longer collapses into a small bar title on
    scroll. It scrolls away instead. On screens this short that is a fair
    trade for a screen that finishes drawing, and it aligns the heading with
    the margin the rest of the page uses, which the bar's own start padding
    never did.

    `SettingsFlowSettlesTest` asserts the thing that was false — that the app
    becomes idle after opening a settings screen, and after coming back — and
    the look book now photographs the middle of the transition as well as its
    ends, with the menu mounted the way `RibbonRoot` actually mounts it.

A48. **The passkey was never broken, and Your account was never designed.**
    Two of the owner's findings on one screen.

    **"The passkey area has never worked."** It could not have. The client
    code is correct — options in, ceremony, response back, exactly as GoTrue
    documents it — and the project has passkeys switched off:

        POST /auth/v1/passkeys/registration/options
        → 404 {"error_code":"passkey_disabled","msg":"Passkeys are disabled"}

    and the project's own public settings say so plainly:
    `GET /auth/v1/settings` → `"passkeys_enabled": false`. Measured against
    the real backend, not inferred.

    The defect that is the app's is what it did with that. `passkeysAvailable`
    was `remote != null` — a guess that a passkey is a *platform* capability,
    which every phone has. It is a *project* setting. So "Add a passkey" was
    offered to everybody signed in, raised the system's own credential sheet's
    worth of expectation, and answered "That passkey didn't work" every single
    time. §6.10 says "a passkey where available"; the app was never asking
    what was available.

    It asks now, once a launch, and the answer decides whether the control
    exists. A failure to ask leaves it null, which reads as *not yet known*
    rather than as no — an offline launch should not decide the question for
    the rest of the session — and a control that is absent until the app can
    say otherwise is the honest shape of not knowing. The day the switch is
    flipped in the dashboard the row appears on its own, with no release.

    **What the owner still has to do**, because no code can: turn Passkeys on
    for the project (Authentication → Sign In / Providers), and serve
    `readribbon.app/.well-known/assetlinks.json` naming this package and the
    SHA-256 of the *release* signing certificate — `web/build.mjs` already
    emits it from `RIBBON_ANDROID_CERT_SHA256`. Passkeys.kt's header has
    carried those two requirements since it was written. The owner's own
    suggestion, Auth0, is already wired (`signInWithAuth0`, Universal Login)
    and is the other route to the same place if the tenant is easier to turn
    on than the project; nothing here forecloses it.

    **"The Your Account section in the settings and the profile area isn't
    designed very well."** Both true, and for two different reasons.

    *The account* was the one section on You built out of loose parts — an
    address in small caps, two underlined words, a floating sentence — while
    Text, Appearance and Downloads directly above it were grouped rows with a
    title and a subtitle each. It did not look unfinished by accident: it was
    the only part of the screen that had never been given the rest of the
    screen's language. It is tiles now, in the same group, with the reason as
    the group's footnote and the passkey row between the address and the way
    out. `Delete account` stays quiet and stays outside the group, with air
    above it: §6.8's one destructive act does not get a tile, because a tile
    is an invitation.

    And **the heading no longer draws itself over nothing.** With no backend
    configured the section used to render its label and then an empty gap,
    with "Delete account" hanging underneath offering to delete an account
    that cannot exist. The section returns before any of that now — label,
    gap and control together — so there is nothing rather than a hole.

    *The profile* was a centred island: an 88 dp circle in the middle of the
    screen, a small-caps line under it, the name under that, a sentence under
    that — four things stacked on an axis nothing else on You uses, above
    three sections all flush with the margin, under a heading that is also
    flush with it. That is most of what reads as undesigned: not the pieces,
    the axis. The face still opens the screen and is still the largest thing
    on it; it stands beside the name now rather than above it, which is also
    how a person appears everywhere else in this app — a seat at the hearth, a
    row in the rooms sheet, the head of their own screen.

    The small-caps "Add a portrait" went with it. Beside the name rather than
    under the circle it was labelling a control that is plainly a face you can
    touch, and it was already cleared from the screen reader because the
    portrait carries the action. The sentence that stays does both jobs, and
    says "Tap to add one" only while there is no face.

    **`AppModel.remote` is `internal` rather than private**, for one reader:
    the look book, which is in this module and is the only way this section
    can be photographed at all. `RemoteSync` is not opened up — a signed-*in*
    shot would need `userID` and `email` prised open, and that would be
    production code existing for a photograph.

A49. **The passkey, once the switch was on.** A48 found why passkeys had never
    worked — the project had them off — and the owner turned them on. An
    end-to-end audit of the path that was now live found two things that would
    have kept it broken anyway, and two more that were nothing to do with
    passkeys at all.

    **"Use a passkey" was unreachable.** The guard read
    `if (model.passkeysAvailable && !model.auth0Available && activity != null)`,
    and `auth0Available` is `remote != null && Auth0Config.isConfigured` — this
    build ships a real Auth0 domain and client id, so `!auth0Available` was
    **false on every device**. The control was never once composed, and
    `AppModel.signInWithPasskey` had no reachable caller anywhere in the app. A
    person could add a passkey and then had no way at all to sign in with one.

    A passkey and a hosted login are not alternatives to each other, which is
    what that condition assumed. The passkey is the way back in on a phone that
    already knows you; the browser is the way in on one that does not. Both
    stand, with the emailed code behind them.

    **"Add a passkey" could not succeed for an Auth0 session.** Signing in
    through Auth0 stores the *Auth0 ID token* as the session's access token.
    That is right for PostgREST, where third-party auth is exactly what the
    token is for, and useless for `auth/v1/passkeys`, which is GoTrue's own:
    GoTrue verifies the bearer against its own signing key and resolves the
    subject to a row in `auth.users`, and an Auth0 token satisfies neither. So
    the register call could not succeed, no system sheet ever appeared, and the
    app answered "That passkey didn't work" — having never asked the person
    anything at all. Auth0 is the *first* way in this build offers, so this was
    the common case rather than the corner.

    `RemoteSync.signedInWithAuth0` mirrors which kind of session is held, kept
    true through every path that changes one and restored from the stored
    session on relaunch; `AppModel.canAddAPasskey` is what the row is drawn on.
    A control that cannot work is not drawn — the same rule A48 applied to the
    project setting, applied one level further in.

    **`PasskeyIsReachableTest` exists because nothing else could have caught
    either.** Both compile, both lint, and neither appears in a look book shot,
    because the frame a shot captures is one where the control is correctly
    absent. The only way to see it is to satisfy the condition the control
    claims to want and then look for the control. It fails when the old guard
    is restored — checked, not assumed. `AppModel.passkeysAvailable` has an
    `internal` setter for it, one visibility step for one reader in the same
    module.

    **Two things that were not about passkeys.**

    *The release build could not be produced.* `proguardFiles` names a
    `proguard-rules.pro` that did not exist, so `:app:assembleRelease` failed at
    `minifyReleaseWithR8` with "Supplied proguard configuration does not exist",
    and had done for as long as that line had been there. CI builds debug, so
    nothing ever ran it: the app could not be built for release and CI was
    green. The file exists now, and `:app:assembleRelease` is in the workflow,
    which is the only durable fix — release is the only configuration that runs
    R8, so it is the only one that catches a keep rule a library stopped
    shipping.

    *And `assetlinks.json` was handing the release package the committed debug
    key.* `web/build.mjs` read `const releaseCerts = [DEBUG_KEYSTORE_SHA256]`,
    with the real fingerprint merely pushed on after it. Both relations are
    delegated there and one of them is `common.get_login_creds` — so the site
    was telling Android that an app calling itself `app.readribbon` and signed
    with a key whose private half is in this repository may be handed this
    domain's saved credentials and passkeys. The release entry is the release
    key's alone now, and is omitted entirely rather than written with an empty
    fingerprint list when there is no release key to name: an entry matching no
    certificate is not a safer entry, it is a malformed one, and a verifier
    that choked on it would take the debug entry down with it.

    **What is still the owner's, and it is bigger than it looked.** The Android
    WebAuthn origin (`android:apk-key-hash:…`, derived from the signing
    certificate) has to be in the project's Relying Party Origins, because an
    Android ceremony's collected client data carries that rather than an https
    origin — without it the server rejects a credential the person has already
    authenticated for.

    The origin that works today is the **debug** key's, and an adversarial
    re-read of this change's own diff found why that is not a developer detail.
    `web/vercel.json` redirects `/apk` to a GitHub release of
    `app-debug.apk`, and the in-app updater points at the same file: **the
    debug build is the distribution channel.** So the app everybody runs is
    signed with `android/app/debug.keystore`, whose private half is committed
    in this repository — and the `app.readribbon.debug` entry in
    `assetlinks.json` delegates `common.get_login_creds` to it. This domain's
    saved passkeys are trusted to a key anyone can download.

    The first draft of this entry's own comment said the opposite — "a debug
    build is a thing a developer sideloads onto their own phone; a release
    build is what other people install" — which is what made the asymmetric
    fix look complete. Removing the delegation would close the hole and take
    passkeys away from every real user in the same stroke, which is not a call
    a build script gets to make, so it is a switch
    (`RIBBON_DEBUG_LOGIN_CREDS`) whose default is today's behaviour and whose
    warning names the actual remedy: **sign the distributed APK with a key that
    is not in the repository.** Everything else here is downstream of that one
    thing.

    **And it may be the answer A44 could not find.** A44 looked for why
    settings vanish "between updates of the app" and concluded the likeliest
    cause was an install that wipes data, without being able to name the
    mechanism. Here it is: a build installed from Android Studio is signed with
    *that machine's* debug key, and the downloaded APK with the committed one.
    Swapping between them is a signature change, so the install is refused
    until the old app is uninstalled — and an uninstall takes `filesDir` with
    it. The CI comment that should have said so claimed the runner used "a
    throwaway debug key ... a new key each run", which is not true and is
    corrected here.

    **What the adversarial re-read of this diff caught, besides the above.**
    Four things, all of them mine.

    `learnWhatAuthOffers` wrote its answer unconditionally, and this change
    gave it a second caller — so a signed-out cold launch fires both before
    either lands, and a first request that succeeded followed by a second that
    failed wrote **null** over the `true` that had already arrived, taking the
    passkey control back off the screen. Answering is a one-way door now: a
    failure never overwrites an answer.

    The new failure line said "Nothing changed, and you are still signed in",
    and this change is what made both halves capable of being false. The
    credential is made on the authenticator *before* the verify call goes out,
    so losing the second leg leaves a passkey on the phone — something
    changed. And both legs now go through `withAuthRetry`, which signs the
    person out when a refresh is refused — so that sentence could be read
    aloud to somebody it had just signed out, over a screen still showing
    their email. It says only what is true in every case: it did not finish,
    and trying again is safe.

    **The live region announced nothing**, which is the most instructive of
    the four: a live region reports a *change to a node that already exists*,
    and it was hung on a `Text` that is composed for the first time at the
    moment there is something to say. A node that has just been created has no
    previous content to have changed from. It sits on a container that outlives
    the transition now, and `AnnouncedFootnoteTest` asserts the thing that
    matters and that no screenshot shows — that the region is already on the
    screen while there is still nothing to announce.

    And the first draft of `proguard-rules.pro` added two keeps for
    kotlinx.serialization with a justification that was not true of this app.
    Reading the merged configuration R8 actually ran settled it: the AGP
    default already keeps `InnerClasses` and the runtime annotations, and
    kotlinx.serialization ships its own rules. They are gone; what is left is
    the one line nothing else in that file supplies.

    **Smaller, in the same pass.** Both authenticated legs of registration go
    through `withAuthRetry` like every other authenticated call — losing the
    second leg to an expired token strands a credential on the authenticator
    that the account has never heard of. Both ceremonies launch on the model's
    scope rather than the composition's, because a rotation behind the system
    sheet used to do the same thing. `AppModel.registerPasskey` throws where it
    used to `return`, so a tap that did nothing stopped reporting "This phone
    can sign you in now." The failure line on You is its own sentence, because
    "the emailed code still does" offers a way in to somebody already in.
    `learnWhatAuthOffers` is asked again on resume, so one offline launch no
    longer removes the control for the life of the process. And the result line
    is announced again (`SettingsGroup(footnoteAnnounces = true)`) — A48 turned
    that section into tiles and dropped the `liveRegion` it used to carry,
    which was a regression this pass introduced and this pass undoes.

A50. **The first frame of the pull was reading a book off the disk.** Owner,
    after A43: the room-to-Scripture transition is better *"but not perfect"*.
    A43 fixed the drag — the one-frame input lag and the fire's per-frame
    allocations — and left the *first* frame of it untouched, because nothing
    had looked at what happens there.

    What happens there is that the page is composed. Taking hold of the fire
    calls `beginOpening`, which puts a `Reading` into `openReading`, which
    composes `ReadingScreen` for the first time — and near the top of it:

        val bookText = remember(reading.bookID, translation) {
            model.scripture.book(reading.bookID, translation)
        }

    `ScriptureStore.book` on a cold cache opens a JSON file out of the APK's
    assets and parses the whole book. Synchronously. Inside a composition. On
    the frame the finger starts moving.

    **Measured rather than assumed**, on a desktop JVM, which is the
    optimistic end of it: Mark is about 100 KB and Psalms 400 KB, and the
    first parse in a process pays the serializer's warm-up on top — 67 ms and
    81 ms against 0.02 ms once the book is cached. A phone's runtime is
    several times slower. Either way it is frames, at the moment somebody is
    watching most closely, and it is the shape of the complaint exactly: the
    drag is smooth and its first frame is not.

    The room has known which book is open since it drew, so it warms the cache
    from the IO dispatcher while nobody is touching anything. The cache is a
    `ConcurrentHashMap` and a parsed book is immutable, so the later call on
    the main thread becomes a map lookup. It is keyed on the book and the
    translation, so a room switch or a version change warms the new one, and
    cancelled with the screen.

    **The tell, if it is still not perfect**: this only bites on a *cold*
    cache. If the first pull after a fresh launch is now clean and later ones
    always were, this was it. If every pull is still rough, the remaining cost
    is the page's first *layout* — measuring a chapter of type — and that is a
    different fix.

    **And the cheap fix for the peel is not free**, which is worth recording
    so nobody tries it twice. The room recedes behind the rising book under
    `peeled`, which sets `alpha` on a full-screen layer — and alpha below 1 on
    a node with overlapping content makes HWUI allocate a screen-sized
    offscreen buffer for every frame of the gesture. `CompositingStrategy`
    `ModulateAlpha` avoids the buffer by applying alpha per draw operation
    instead, which is the textbook answer. Rendered both ways at the halfway
    point and compared: **84% of pixels differ, median channel delta 29 out of
    255, maximum 185.** That is not a compositing detail, it is a different
    picture — the room's cards stop being opaque over their own ground. The
    buffer stays.

A51. **The page is now built before the pull, not by it.** A50 named its own
    tell: if the first pull after a fresh launch came out clean and every pull
    was still rough, what remained was the page's first *layout*. It was, so
    this is that fix.

    **Measured first, and the split is the whole story.** Setting a
    thirty-verse chapter, on a desktop JVM under Robolectric:

    - building the chapter's `AnnotatedString` — `buildChapterPage`:
      **26.7 ms** the first time and **0.45–1.04 ms** after, so its first hit
      is JIT and it is otherwise free;
    - Compose measuring that string: **30–67 ms, every single time**;
    - and the first chapter in a process: **about 1.1 s** all in on this
      machine, which is the typeface load and the JIT and Skia's caches, all
      paid once.

    So the cost was never the reading and it is not the string either — it is
    setting a whole chapter of Literata. That cannot be made cheap here: the
    chapter is deliberately one `AnnotatedString` in one `BasicText`, because
    the washes, the note's carve and verse hit-testing all read one
    `TextLayoutResult` (see the head of `ChapterText.kt`), so it cannot be
    measured a visible line at a time. And a text measure cannot leave the
    main thread. Caching the built string off-thread — the obvious echo of
    A50 — would buy about a millisecond of the sixty.

    So it is done **early** instead of quickly. `beginOpening` fires on the
    first millimetre of the pull and used to be the first moment any of
    Scripture existed; its own docstring already said *"compose the book under
    the room so a pull has something to raise"*, which was the right idea one
    millimetre too late. The room now arrives, and once it has — delayed by
    `ARRIVE_MS` so the cost lands in the quiet *after* the arrival rather than
    during it, since moving a hitch from one animation onto another is not a
    fix — the book the fire would raise is built underneath it, wholly off the
    bottom of the screen at `progress == 0`. By the time a thumb touches the
    fire the chapter is measured and the pull is a translation of a layer that
    already exists. It is the same reading, so it is the same `key`: the page
    that stood by is the page that rises, and closing the book puts it back on
    standby rather than throwing it away.

    **Which made "composed" and "being opened" two different things for the
    first time, and five things had been relying on them being one.** Four were
    found by reading the screen and reviewing the diff; one by a test that
    failed:

    - **`trackReading` is driven by layout**, and a standby page lays out. Its
      first measure would have saved a reading position, announced *"Jonathan
      is reading Mark"* to the whole room, and **fed the fire** — for a book
      still shut. The last is the worst: a fire reports a state (Law 2) and the
      state would have been a lie. This is the one the test caught, which is
      the argument for having written it. Gated on the page being *astir* — a
      finger on it, or the pull committed. Nothing is lost by waiting, because
      `readingChapter` already starts at the saved position, so the running
      head is right before it ever runs.
    - **Withdrawing from the book hung off disposal.** `onDispose { withdraw() }`
      was how the room stopped hearing you were in Mark, and the page is no
      longer disposed when the book closes. Moved to the un-commit, where it
      belongs; `withdraw` is idempotent, so a standby page's own pass through
      costs a no-op.
    - **The presence form spends a one-time announcement.** *"Ruth is with
      you"* is said once, four seconds after somebody arrives behind you, and
      then never again for that person. A standby page left running would have
      spent it while the book was shut. The **first attempt at this was wrong
      and is worth recording**: the form was wrapped in the gate, which would
      have composed it on the first millimetre of the pull — and the form owns
      the reading measure's trailing inset, which `readingMeasure` turns into
      `padding(end = inset)` on the chapter itself. That is a width change, so
      it would have re-measured the chapter on the one frame this entry exists
      to clear, undoing the fix by way of fixing something else. The gate is
      passed *into* the form instead, and holds back only the saying of it.
    - **A licensed chapter is fetched over the network**, and a page nobody has
      touched has no business doing that. Gated on *astir*, which is the first
      millimetre of the pull — exactly the moment it fetched from before, when
      that was also the moment the page first existed. Note that a licensed
      chapter therefore gets no pre-measure: there is nothing to set until the
      fetch lands. Bundled translations — which is all of them today — get it.
    - **Escape closed a book that was not open.** The page carries
      `onPreviewKeyEvent` for the hardware `Esc` that iOS gets from
      `.keyboardShortcut(.cancelAction)`. With the page present before the book
      is, Escape pressed in the room reached a shut book's close path, latched
      `closing` — which is only released by a *commit*, so it stayed latched —
      and swallowed the key from whatever should have had it. Gated on *astir*.

    **And the opening scroll had to be rekeyed.** It was `LaunchedEffect(Unit)`
    — sound when the page was born at the instant it was asked for. A standby
    page is composed with no target and settles on your own position, and the
    target for a tapped notification or a quoted verse arrives *after* it, so
    under `Unit` that target was never read and the page opened in the wrong
    place. Keyed on `openAt` now, and comparing against where the list already
    is rather than against chapter 1, because a pre-positioned page can be
    asked to go back to the first chapter as well as forward.

    **What it costs**: one screen's composition and one chapter's text layout
    held for as long as the reader is in a room. That is the trade, and it is
    the right way round — the memory is idle, the milliseconds were not.

A52. **The invite the backend never heard of.** Owner, on two Auth0
    accounts, both iOS: every link answered "That invite isn't there any
    more. Ask for a new one."

    It was telling the truth, and it was not the half of it. `rooms`,
    `memberships` and `invites` were **all empty**. Four profiles, and
    nothing else: for an Auth0 account every write after `profiles` had been
    failing since the day Auth0 was switched on, silently, because
    `pushInviteIfNeeded` logs and forgets and §6.10 says the app working is
    not news.

    **The blocker is `rooms_select`, of all things.** It reads
    `is_member(id)`. Every PostgREST write carries a RETURNING clause, and
    Postgres applies the SELECT policy to the row a write returns — so
    creating a room meant being refused sight of the row you had just
    written. The membership that would satisfy `is_member` is pushed *after*
    the room, so it never resolved. `INSERT` passes and `INSERT ... RETURNING`
    does not, which is why it read as a write permission fault and was not
    one. A room with no members holds nobody's content and no invite can
    point at it, so it is now visible to whoever is making it — the rule
    `memberships_insert` already used for a room's first member.

    **Two more underneath it**, each enough on its own. `memberships_insert`
    asked `NOT EXISTS (SELECT 1 FROM memberships …)` *inside a policy on
    memberships*, so seating the first member was 42P17 infinite recursion —
    shipped by the migration named for fixing memberships RLS. And
    `ribbons_insert`/`ribbons_update` still called `auth.uid()`, which casts
    the subject to uuid and throws 22P02 on `google-oauth2|…`; the Auth0
    pass updated every other table and missed that one, because `ribbons`
    was added by a migration that never reached `migrations/`.

    All three are policy faults. Nothing in either client had to change, and
    the first fix attempted here — sending `ignore-duplicates` so the upsert
    would not touch `rooms_update` — was wrong twice over: the check that
    fails is the SELECT policy, and it is applied to `DO NOTHING` just the
    same. Reproduced against the live project before and after, as the
    `authenticated` role with a real Auth0 subject, and rolled back.

A53. **Following did not follow, and your own name did not look like
    yours to change.** Owner, reading: *"it doesn't automatically follow on
    click smoothly — can we do that with easing?"* Two things, one of them
    the word not meaning what it says.

    **A follow was a jump.** Tapping a portrait moved the page once, to the
    chapter they were in at that instant, and then let go of them entirely.
    They read on; you sat where they had been — still wearing the thread at
    the top of the page, still announcing yourself as following, still
    raising "Ruth is with you" on their phone. Everything said follow and
    nothing followed. The roster carries their position and arrives on every
    tick; the page simply never read it after the first.

    It does now, and it does it the way a tap already did — through
    `scrollCommand` on Swift and `goToChapter` on Kotlin, so the move eases
    rather than cuts *and* opens the grace window. That second half is not a
    nicety: any scroll of your own breaks a follow (§4.2), and a page that
    moved itself without claiming the grace would have read its own move as
    yours and cut the thread on the first page they turned.

    Two bounds keep it from twitching. Only their **chapter** — their scroll
    within one is a finer signal than a page can honestly answer — and only
    when it **changes**, tracked as the chapter this page has already been
    carried to rather than against your own saved position, which lags behind
    the roster by a throttle and would have let a steady roster yank the page
    back to a chapter top once a tick.

    **And the name.** S18 says the name is "editable in place", and it was —
    in the room's own display face, 26pt, beside your portrait, with no
    paper, no rule and nothing else on the screen saying a field was what it
    was. It read as a heading. The way to your own name was a tap nobody had
    a reason to make, and the answer to "how do I change my name" was that
    you already could. A hairline under it, brightening under the caret, is
    the smallest thing that says otherwise; the in-place edit S18 asks for is
    untouched.

A54. **The book opens on your verse, not the head of its chapter.**
    Deviation 7 left verse-level restore for a device, and it was a defect
    rather than an imprecision. §6.2: "Reading opens at your position." §6.3:
    "Tap → reading opens at that verse, the mark breathing." Every way into
    the book — your own place, a waiting row's note, the ribbon, a quoted
    verse, a tapped notification, "back to where you were" — scrolled to the
    head of the verse's chapter. Then the page measured where it was, and the
    verse under the reading line, a few verses into the chapter, went into
    your position on the first pass. Opening the book and closing it again
    was enough to lose the verse; and since your place had moved, closing
    left the room's ribbon there too — the glance `openedAt` exists to make
    harmless.

    **Landing.** `landOn` puts the verse's first line 6 dp above the reading
    line: the upper third, the same line `trackReading` reads your place
    from, so the page measuring itself finds the verse it was sent to. Just
    above rather than on it, because a verse sitting exactly on the line is a
    coin toss between two. `scrollToItem` takes an offset into the item and
    the list knows its own padding, so it is one exact move once the
    chapter's lines are known; a chapter not yet typeset is brought on first
    and waited for. A verse near the head of its chapter is already above the
    line with the chapter at the top of the screen, and opens that way: the
    page does not scroll back past the chapter's head to put it on the line.
    A verse the room's version leaves out lands on the nearest verse before
    it.

    **Holding.** The landed verse is where you are until a scroll carries the
    reading line off the verse it came to rest on; then the page's measure
    has it again. Without the hold the aim would have to be exact to the
    point, forever, or every open would move you a verse. With it, a line of
    aim costs a line of where the verse sits, never the verse. Held, the page
    saves no position, because being sent somewhere is not reading to it: a
    note looked at and closed leaves your place and the ribbon where they
    were. Presence still says where the page is. A finger on the page takes
    back a landing still on its way.

    **Three things it exposed.** `openedAt` was remembered once, and since
    A51 the page outlives the reading of it, so after the first read every
    later glance put the ribbon back where that read had stopped; it is taken
    again whenever the book opens. The follow-back offer remembered the last
    throttled save, and closing the book trusted one up to two seconds
    behind; both now use the page's own last word (`latestAddress`, which
    Swift already kept). And a named place opened with a follow still running
    from before was taken away again on the roster's next tick. A named place
    is your own going somewhere, and the follow ends, as your own scroll
    would end it.

    **The cards.** "The cards are open" opened at the head of the chapter
    whose card had opened — Android's own comment said the page had to land
    at the chapter's end for the card to be on screen, and then asked for
    its first verse — and the room's row of the same name opened at your
    own place. A card is not a verse: it sits below the chapter's last one
    (§4.6). So the place the book is sent to is a verse or a card
    (`ReadingPlace`), and a card brings its passage end to the top of the
    screen. The row goes to the card that opened last. Nothing is held,
    because nothing was read.

    **Not changed.** Following still carries the page by chapter: A53's
    reasoning holds, since their scroll inside a chapter is a finer signal
    than the page can answer without twitching. None of this has run on a
    device.

A55. **Note search, on the shelf (S23).** "Two searches, deliberately
    separate." Scripture search has lived in the chooser for some time; note
    search, which S23 puts on the shelf, had never been built. It is now, on
    both platforms.

    - **Where.** A field at the head of the shelf: the room's memory, not a
      bar across the front door. It is there when the shelf is — from the
      first finished book, since S10 has no shelf before one — and it covers
      every reading the room has done, the open one included.
    - **What it matches.** A note's words, or a voice note's transcript; any
      case, any accent (`localizedStandardContains` on Swift, a folded
      compare on Kotlin that does the same). Two characters begin a search,
      as in the chooser.
    - **What it will not find.** A note left for you and not yet found. Its
      words are the verse's to give you (§6.3), and the room already lists it
      waiting; a search box that read it out first would be a read receipt
      the other way round.
    - **What it shows.** The open book's notes first, then the shelf from the
      latest ember back, in verse order within each: the reference and who,
      then the words around the match. No count of anything. A row opens its
      own book at the note's verse, and the keyboard goes with the room.
      Finding nothing says so over the shelf, which stays.
    - **Offline.** Everything it searches is on the phone, so it works, and
      says nothing about the rest (S23).

    `NoteSearchTest` holds the rules.

A56. **Push, Android's end (S19; the shape is I33's).** RoomWatch's header
    said it plainly: a note left for you arrived fifteen minutes late at
    best, "Ruth is reading Mark" only while Ribbon was open, and a
    thinking-of-you sent to a closed app never. The backend sends now, and
    Android takes it as FCM data messages — never a notification message —
    so what arrives is posted by the same `Notifications.post` as what a
    pull finds: the same five channels, the same ids (a push and a pull
    about the same person replace each other), the same one line. The
    third gate, the room on screen, is applied on arrival; the switches
    and the quiet hours were applied by the server, in the phone's zone.

    - **One voice, never two**, exactly as on iOS: while the sender says it
      can reach FCM and this phone's registration went through, the app
      posts none of the six itself. The answer is kept in preferences, since
      the worker and the messaging service both run with no screen.
    - **"Ruth is reading Mark" stands while she reads.** It is S24's Live
      Activity in the only shape Android has for it: an ongoing line,
      silent after the arrival that said it aloud, kept current by her
      phone's heartbeat, taken down when she leaves — and gone by itself
      twenty-five minutes after the heartbeat stops, for a phone that died
      mid-chapter.
    - **Firebase is optional in the build.** Its values come out of
      `app/google-services.json` at build time, with no plugin; a build
      without the file has no Firebase at all and is the app it was before.
      Both packages — `app.readribbon` and `app.readribbon.debug` — go in
      the Firebase project, and the service account goes to the function as
      `FCM_SERVICE_ACCOUNT` (supabase/README.md).

A57. **The fire on the home screen (S24).** §12.2 names Glance for Android's
    widgets, "same content as iOS", and this is the content without the
    Glance: the fire is a painting, and a RemoteViews — which is what Glance
    compiles to — can hold a bitmap of a painting but not the painting, nor
    the room's small-caps face. So `FireWidget` paints one bitmap with the
    room's own `FirePainter`, held at one instant on the unlit ground, sets
    the book's name under it in Alegreya Sans SC, and hands the system that
    and a sentence for TalkBack ("The fire is burning. Mark."). A long name
    is set smaller rather than cut.

    - **It cools without the app**, as on iOS (I34): what the app leaves is
      the open reading's handiwork and the room's banked spans, and the
      state is worked out again from the room's own engine each time the
      system updates the widget — on the hour, at most.
    - **No book open is the ground and nothing on it** (§08).
    - **A tap opens the room**, by the same private intent a notification
      uses.
    - **The Live Activity's Android shape** is the ongoing "Ruth is reading
      Mark" line of A56, which is what Android 16 offers a thing that is
      true for as long as it is true; a home-screen widget would be a second
      surface saying the same sentence later.

## iOS (phase four): the second pass

Android took a design pass of its own (A18–A51) and the two platforms
drifted: one had a hearth, tiles, a ribbon and a chapter list, and the
other had the phase-one screens. This pass brings iOS level with it. Where
an Android entry describes the *product* decision — the greeting, the
ribbon, the room's one version, the notification gates — iOS now does the
same thing and this section does not repeat the argument; it records what
is different on iOS, and why. The Android numbers are cited so the two
halves of each decision can be read together.

I1. **The design system is the same system.** `Shapes.swift` carries the
    same radii (small 12, row 16, card 22, group 28, sheet 34), the same
    seam (2 pt), the same text inset (20) and row heights (60/76), and the
    same `inGroup` rule for a tile's corners inside a stack — the outer
    corners on the group, the inner ones small — as `Shapes.kt` (A20/A23).
    `Surfaces.swift` is `paper`/`well`: surface or ground, the grain over
    it, and a one-point rule at the edge, because surface and ground are
    1.05:1 apart on this palette and a tile cannot stop where it stops
    without one. One difference: a group hands its rows their corners
    through the environment (`tileShape`), so a row is the same code alone
    and in company, where Compose passes a shape parameter. No icons on any
    of these screens; the chevron is drawn.

I2. **Springs.** `RibbonMotion` gains the three critically damped springs
    A48 named — `cover` (stiffness 180), `handled` (400), `touched`
    (1500), damping 2√k — for the things a finger holds, and `release`
    (220 ms) for a gesture let go of. Every token now comes in two,
    `settle` and `settle(still:)`, so "under reduce motion this is a cut" is
    decided in one place. A pressed tile gives by 2.5% on `touched` and
    nothing overshoots (§9.1).

I3. **The hearth (A48).** The room screen is the greeting by the hour, who
    is here as one sentence (and a tap to that person), and the hearth: the
    seats in a row (38 pt faces in 44–48 pt targets, the accent ring for
    present, the top half-ring for idle, a dashed open seat when somebody
    is expected), the fire in a well of the ground, the way in, the ribbon
    offered under it, and — until it has been done once — "Pull the fire
    up to open the book." Left for you, the shelf and the quiet-day foot
    follow. The starter shelf shows on first run only.

    *One number, as on Android.* The root owns the pull; the hearth moves
    it. The moment the finger takes the fire the reading is built under the
    room's foot (the page before the pull, not by it — #30), and it rises
    on the pull's own number while the fire follows the finger (translation,
    a 10% swell, the hearth receding). Past a fifth of the travel or a
    480 pt/s flick the root carries the number to 1 on the `cover` spring
    and the page finishes the movement from wherever the finger left it;
    let go short of that, the fire returns on `handled` with the release
    velocity and the page goes back down with it. Pulling the fire down at
    the top of the room opens your rooms (28 pt of square-root lean, 96 pt
    to commit). Both gestures have a tap: the way-in capsule and the room's
    name, and both are VoiceOver actions on the fire. Under reduce motion
    nothing moves under the finger; the commit is by distance and the page
    arrives on the spring.

I4. **Two menus (A29).** The room's name opens the room; your face opens
    You. Each is a `RibbonScreen`: a pinned bar carrying only the way back
    (or Close), a display-30 title on the page, a lede under it, and tiles.
    The room holds Invite someone (with `inviteSend` as its subtitle, or the
    six-people line), Notifications and Plan with what they contain under
    their names, the room's own controls (its name as a field that
    cross-fades in place, your ink, the quiet way out), your rooms as paper
    rows, and Start a room / Join with an invite. You holds your face at 88
    beside your name as a display-26 field that commits when you are done,
    How you read (Text), This phone (Downloads), Your account, and the
    quiet delete. Notifications moved from You to the room because they are
    per room (A29). Appearance (A18) is not here: iOS has no Material You
    and the room keeps its own chartreuse, so there is nothing to switch.

I5. **Settings as tiles (A23).** Text is the room's translation as a choice
    group ("Everyone in this room reads this one.") and The page ("Yours
    alone. Nobody else's page moves.") with the size slider and its live
    preview in wells, line spacing as a drawn segmented control with a
    paper pill sliding on `touched`, and red letter as a switch. Every row
    carries the true small thing under its title (A23's subs).
    Notifications are one group per room (titled with the room, the book as
    detail), four switches with subs, then quiet hours as two rows opening
    a wheel in a well, and the footnote about the touch that still arrives.
    Downloads and Plan are one group each. When the OS is silencing the app
    after the one ask, Notifications says so in one line and offers
    Settings — never a second ask.

I6. **The ribbon (A30) and the chapter list (A31).** Closing the book leaves
    the ribbon where you were, if you moved and the book is not finished;
    it is pushed on `ribbons` (last placed wins) and merged the same way.
    The room offers it in one sentence under the way in; the chapter list —
    from the running-head pill at the foot of the page, beside the Wave —
    shows the book's name, the ribbon row with "Go there", and the chapters
    as a grid of numbers: the one you are in on paper, the rest bare, a
    16×2 accent hairline where the ribbon is. The ribbon and your own place
    are never in one sentence.

I7. **The room reads one version (A42).** `Room.translation` and
    `Reading.translation` arrive in RibbonCore with tolerant decoding (an
    older state file reads `bsb`), the rows carry them, `words(room:
    reading:)` is what every page, search, preview and quoted verse now
    asks, and the note card's quote in the author's translation is gone.
    `setTranslation` still writes the profile — the person's default for
    the next room — and changes the room on screen. Finished readings keep
    the version they were read in. Kotlin already had all of this; the
    Swift model now matches it, with tests.

I8. **A mark on a phrase (A41g), and the washes (A41b/d/f).** `VerseRange`
    gains `startChar`, `endChar`, `charTranslation`, normalised in the
    initialiser (a drag made upwards stores as one made downwards; the
    offsets turn over with their ends) and decoded leniently. The page
    honours offsets only when they were measured in the version on this
    page; anyone else sees the whole verses. Two handles (10 pt knobs in 44
    pt targets) sit under the first and last letters of a lift and drag to
    word edges; each has four VoiceOver actions — a verse or a word, either
    way — and the whole verse has "open what's here" and "leave something
    here" (§11).

    The wash is redrawn: one path per mark, filled once (no dark band at a
    line break, no staggered rows, one shape), hung off the baseline (0.88
    above, 0.28 below of the body size, clamped to the line box) rather
    than filling the leading, with a horizontal-only 0.6 pt end wobble on a
    stable hash. Overlaps are screened — 1−(1−a)(1−b) — a shade darker per
    extra ink (+5%) and never past 36%. A mark arriving from somebody else
    eases up on `arrive`; your own is revealed along its words on `settle`
    with a 10 pt soft tip, and over somebody else's mark their ink stands
    ahead of the tip and the mixture behind it. TextKit is not SwiftUI, so
    the drawing is driven by a display link for the length of the arrival
    and then stops. Under reduce motion every wash is simply there.

I9. **Notifications (A34/A39).** `UNUserNotificationCenter`, five kinds,
    three gates (`shouldPost`: the room's switch, quiet hours wrap-aware
    with equal ends meaning none, and the room on screen — except a
    finished book and a touch), no badge ever. The merge diffs against
    `notifiedThrough`; a nil watermark (a first sync) sets it and posts
    nothing; it advances on every merge. Notes are grouped by author (one
    post per person, naming the verse for one note and only the person for
    several), one post for the cards, one for a finished book. "When they
    open the book" comes from the roster while the room's channel is up;
    thinking of you is a touch first and a name second, and in quiet hours
    only the touch. A tap on a post lands on the room, the verse or the
    cards through `pendingDestination`, and a post that arrives before the
    window exists waits in `NotificationRouter`.

    The one ask (§6.1) is Ribbon's own dialog over the book — "Tell you
    when Ruth leaves a note?" with Tell me / Don't — raised the first time
    you leave a note or find one in a room of more than one, and only if
    the system has not already been asked. The system prompt follows Tell
    me and never precedes it.

    The room is checked on while the app is away by a `BGAppRefreshTask`
    (`bible.ribbon.app.roomwatch`, 15 minutes at the earliest), registered
    before launch finishes by an app delegate, started only once someone is
    signed in and cancelled on sign-out. iOS decides the real cadence; a
    phone that is never opened is checked less, which is right. A wake
    with no model builds one for the pull alone (no faces, no channel).

I10. **What this phone still owes the backend (A36/A40).** Room renames and
    version changes, note deletes (with the reading and whether it was a
    voice note, so the recording can go first), note edits, highlight
    deletes, and the "unsaid" list (a quiet day, a highlight, a card
    answer, the ribbon) are all replayed at the top of `refreshFromRemote`,
    and `merge` reads them: a pending delete is not brought back, a pending
    edit keeps its local body, a highlight still on its way up is not
    pruned, and notes and highlights are pruned against the backend only
    when the pull for them actually succeeded (`notesComplete` /
    `highlightsComplete`).

    *Persisted, where Android's are in memory.* Every one of these lives in
    `AppState` and is written on each change. Android keeps them in the
    model and loses them at process death; the invite set was the first to
    move into the store (A37) because a link already in somebody's thread
    was being pruned, and the same failure was waiting on the others — a
    take-back made offline and forgotten at relaunch came back on the next
    pull with the backend copy never deleted, and a highlight made offline
    was pruned by the next complete pull as if somebody had taken it back.
    The unsaid list stores the *intent* (which quiet day, which highlight,
    which card, which reading's ribbon) and looks the row up at replay, so a
    row that has since gone has nothing left to say.

I11. **Taking things back (A40/A40a).** A voice note taken back deletes the
    storage object first and the row after; a 404 on the object is not a
    reason to keep the row. Leaving with "take them back" deletes the notes
    while the membership still exists, then the membership. Deleting the
    account deletes the notes if asked, then *forgets the profile* —
    portrait object gone, name set to "Someone", no path — and never the
    profile row, because the row's cascade would take every note they chose
    to leave behind. Highlights are never deleted: a mark on a shared page
    is not a possession (§6.8).

I12. **Invites that actually left the phone (A37).** `hasLiveInvite` counts
    an invite you minted only once the share sheet was opened on it, so a
    room of one that has told nobody is not told "The invite is still out."
    The join screen shows the inviter's monogram, says "You'll join as
    Ruth." with "Join as someone else" for a phone that already has a
    person, and a session the phone thought it had that turns out to be
    gone sends the join to the sign-in step rather than a dead end.

I13. **The card turns (A33).** The reflection card turns over on `open`
    with the face swapped at the halfway point and un-mirrored; under
    reduce motion it cuts. The answer field has no prompt and no box — a
    hairline under the words and the cursor in your ink; Answer / Keep what
    I had appear only once something is typed or an edit is under way;
    answers are ivory, names in ink. Every literal on the card moved into
    `Copy`.

I14. **Front door (A25/A28).** The tour's copy no longer says "gamified
    streak counters" or shows "0:42" — the four cards say the true small
    thing about the product in its own words, and their pictures are the
    product's own objects: the Wave on the bare ground (no glow behind the
    mark, §13), a page with a presence lozenge, a note mark with a waveform
    in ink and no duration, the fire in a well. The intent step's four
    answers are one group of tiles, all in ivory. The progress bar's fill
    is on `settle` and still under reduce motion; its back control is the
    drawn chevron. Every text field in the product is one `CentredTextField`
    on paper. "Continue with Auth0" is "Continue in a browser" (A46). The
    launch window carries the Wave unfurling — a band widening 22→108 over
    440 ms, then the mark settling 1.04→1 over 560 ms — and leaves only
    when the model is loaded and the mark has settled.

I15. **Offline, and a chapter that would not come (A45).** `NWPathMonitor`
    → `isOnline`, read by exactly one thing: the fire dims. A licensed
    chapter that fails to stream says "Mark isn't on this phone yet." where
    the words would be with "Try again"; connectivity coming back re-asks
    once without a tap.

I16. **Sign-in failures are told apart (A44).** A 4xx on the code is "That
    code didn't work"; anything else is "Can't reach Ribbon right now",
    because sending someone back to retype a code into the same silence is
    the wrong instruction.

I17. **State salvage (A43).** A `state.json` this build cannot decode whole
    is kept beside the new one as `state.json.unreadable`, and the settings,
    the three asked-once flags and the two invite sets are read out of it
    field by field. `notifiedThrough` is deliberately not salvaged: after a
    reset the next merge should be silent.

I18. **Passkeys (A47).** `/auth/v1/settings` is read once (sticky) and "Use
    a passkey" appears only when the project has them on and the platform
    can run the ceremony — and no longer only when a browser sign-in is
    absent. "Add a passkey" needs a Supabase session of its own
    (`canAddAPasskey`), never a browser one. A registration that did not
    finish says so in one line and keeps the control.

I19. **Small things, each its own defect.** The note mark is one circle
    whose stroke morphs (a hairline pending, a ring written, a filled dot
    spoken) and whose breath fades out on found rather than stopping. The
    presence form holds a departed reader for one `arrive` so the lozenge
    fades reading the name. Only the inks scroll on the leave toolbar; write
    and speak are pinned, and every swatch is drawn at 20 and taken at 44.
    The Person screen is a `RibbonScreen`: the face at 96 on the bare
    ground, the ink row spoken as "Your ink, crimson", what they left as one
    group of tiles showing the words once found (or yours) and "not yet
    found" otherwise, the quiet way out at the foot, and one dialog whose
    question cross-fades from "Leave this room?" to "Leave your notes
    behind?". The ink picker's swatches are 44 pt targets with an animated
    ring. The `firstName` rule lives in `Copy` on both platforms.
    Confirmation questions are Ribbon's own card (`ConfirmDialog`), not the
    system sheet, because the words §6.8 and §6.1 specify are Ribbon's.

I20. **Not carried across, on purpose.** Appearance / Material You (A18,
    A21): no analogue on iOS. The predictive-back peel (A49): iOS has the
    interactive pop and the sheet drag, both the platform's. The look book
    (A24): screenshot tests want a simulator, which this pass did not
    have; the parity is in the ledger, not in pictures — see the questions
    at the foot of the pull request.

I21. **The fire's release had the wrong speed, pointed the wrong way.** The
    hearth's pull (I3) is one number from the finger to the page, and the
    number was sound; what it was handed at release was not.

    *The flick was measured as a distance.* `predictedEndTranslation` minus
    the translation is how much further a prediction says the finger would
    have gone — a length, compared against `openFling`, which is points per
    second (I3, A48). A flick had to be roughly twice as fast as the number
    says before it counted. It is `DragGesture.Value.velocity` now.

    *The spring was set off backwards.* SwiftUI takes a spring's starting
    speed relative to the distance it has left, positive towards the mark.
    It was given points per second over the travel — neither divided by the
    distance nor signed for it — so a finger still moving up as it let go
    sent the fire down fast, and one already coming back sent it up first.
    `RibbonMotion.cover(rate:towards:)` and `handled(rate:towards:)` take the
    rate and the distance left and do the division themselves, and cap the
    result below the spring's natural frequency: a critically damped spring
    started towards its mark faster than √k per unit of distance crosses the
    mark, which is the overshoot §9.1 forbids, reached through the one kind
    of animation built never to have it.

    *The commit threw the speed away.* "The root carries the pull the rest of
    the way, with the speed the finger let go at," said the comment, and the
    root set off from rest. It carries the speed now.

    *Let go short, the page vanished.* The fire animated itself back down,
    and the root then asked for the same value again. The second request
    changed nothing, so its completion ran at once and took the half-risen
    page out of the tree while the fire was still falling: the page did not
    go back down, it was simply gone. The root owns the release alone — one
    spring, fire and page together — and takes the page away when it has
    arrived, unless the fire has been taken hold of again in the meantime
    (`pullHold`: a quick re-grab lands inside the last release).

    And one movement from every door. Every other way into the book from the
    room came up on `cover`; from a pushed screen — a quoted verse on an
    ember, a note on somebody's page, "read it again" — it came up on
    `arrive`, a curve two thirds as long. `openBook` is the one door.

I22. **Reduce motion fades; it does not cut.** §11 is specific: "Morphs
    become cross-fades. The card turn becomes a fade." The `still:` tokens
    (I2) made every animated change under reduce motion a cut, including
    changes that never moved anything — a word cross-fading under the fire,
    a presence ring's opacity, the hairline under your name brightening. A
    cut is not a cross-fade, and it is the one thing §9.1 asks the room never
    to do.

    `Motion.swift` now says what `still:` is for: movement. A change that is
    only opacity or colour asks for the plain token and keeps its curve. And
    where movement was being cut, it becomes the fade §11 asks for:

    - The book fades in and out where it will be read rather than travelling
      a screen's height. It is no longer built under the room during a pull,
      since nothing moves under the finger (I3) and there is nothing to lift.
    - The presence form fades at its edge. The leave toolbar, the confirm
      card, a face taking its seat and the onboarding steps fade without the
      slide or the swell.
    - The card turn is a cross-fade (I23). The segmented control's pill fades
      from one stop to the next. The presence ring cross-fades between whole
      and half. A choice's check fades rather than drawing itself in.
    - A tile under a finger dims to 72% rather than giving. Held still, it
      used to answer a press with nothing at all.
    - The other way: a scroll that flies the page a chapter's length — to the
      person you follow, to a chapter from the list, on to the next — is now
      simply there. That flight is exactly the movement §11 is written for,
      and it was the one thing the reading surface still animated under it.

    Layout that shifts because something arrived or left (a waiting row, the
    quiet-hours wheel, the account section) still cuts under reduce motion,
    as before: the fade there would carry a slide with it.

I23. **The card turned the wrong face first.** The reflection card chose its
    face from `turn >= 0.5`, and `turn` is the state — 1 from the first frame
    of the turn, since that is where it is going. So the open face, drawn
    already mirrored, replaced the sealed one at once, and the first half of
    every turn showed the open card backwards with the sealed face nowhere.
    `CardTurn` is `Animatable`: it is handed the angle actually reached, frame
    by frame, and changes faces edge-on, which is what I13 says it does.
    Setting a card down, answering it and taking an answer back to edit now
    cross-fade; setting one down used to make the page jump up under the
    thumb that had just tapped it.

I24. **What still happened between two frames.** A38 and A41 swept Android
    for these; this is the same sweep on iOS.

    - A fire changing state — caught by the reading you have just closed,
      banked by a quiet day while you look at it — was redrawn as another
      fire. It cross-fades now, the two fires sharing their seed so they
      breathe in step and only the state differs.
    - The composer: the toolbar giving way to writing or speaking, editing a
      note, and back — all cross-fades. Dragged away from a recording, the
      waveform recedes and the line under it turns over on `release`.
    - The follow thread fades in and out. "Back to where you were" fades, and
      forgets on its own at two minutes; it only went when the page next
      happened to redraw.
    - The running head at the foot of the page cross-fades between chapters,
      its capsule easing to the new width.
    - The highlight label faded away only on its timeout; a tap and Remove
      now fade it too.
    - The room's "Left for you": notes and the invite settled in, but the
      cards opening and an ink to pick arrived on one frame, and the section
      itself always did.
    - A present reader going still now unwinds their ring to its crown
      (it jumped), and presence rings come and go on `arrive`, which is the
      curve §9.1 files presence appearing under.
    - Thinking of you: the filled ring rests a moment and then lets go. It
      vanished on the frame the hold succeeded, so the one gesture in the
      product with no words had no visible end either. Letting go short is
      on `release`, where it had a 150 ms curve no token names.
    - The ink picker dismissed on the same frame as the tap; the ring never
      moved. It settles round the choice, and then the sheet goes.
    - A choice's check draws itself in on `touched`; the words of the
      segmented control brighten as the pill arrives under them, not before.
    - The join's steps cross-fade, the dead end and "Joining" included. The
      sign-in error line fades, and the control lowers while a code is on its
      way — before, the only sign a tap had been heard was the network
      answering.
    - Portraits settle into their circles; "That's me" wakes with the first
      letter rather than switching on.
    - The way-in capsule, the invite's send button, embers on the shelf and
      the faces on an ember take a press as a tile does. They took none, or
      the system's dimming, beside tiles that gave.

I25. **The haptics §9.3 lists, and only those.** "Someone arrives: one soft
    transient, low intensity" — `someoneArrives()` has existed since phase
    one and nothing called it. The presence form plays it when somebody opens
    the book while you are reading; never for the people already there when
    you opened it, and never for a reader coming back inside the fade, who as
    far as the page is concerned never left. The other way: the intent step's
    tiles played a `UIImpactFeedbackGenerator` tick on every tap — a
    selection tick, which §9.3 names as unwanted, from a preset, which §12.1
    rules out. It is gone, and `Haptics.light()` with it.

I26. **Onboarding's thread moved one way.** Every step arrived from the right
    and left by the left, going back included, so back looked like on. The
    step leaving is drawn with the transition it last had, which means a
    direction held in view state reaches it one move late; `ThreadMove` reads
    the direction through a reference, at the moment the move is made. The
    progress bar belonged to each step and slid away with it, so its fill —
    the thing it is for — was never once seen to move. There is one bar now,
    standing still over the thread, the accent running along each segment
    (and back, going back). The mark dissolves into the first card in place
    rather than the card sliding in over it.

I27. **Places a finger could not land, and one tap that made two people.**
    The book chooser's rows were 34 points tall and answered only on the
    book's name and its fire, with a dead gap between them; the whole row
    answers now, at 44. An ember's notes and its quoted highlights were the
    same. Remove on a highlight's label, Read quietly, Try again under a
    transcript, Take back in the composer and Open Settings after a refused
    microphone were each the height of their own letters, and are 44 now.
    The ember record was the one screen the room pushes that still wore the
    system's navigation bar and its glyph; it has the drawn chevron (A29).

    "That's me", at onboarding and at a join, started a task per tap. With a
    portrait there is an await before the person exists, and a quick second
    tap inside it made a second person and orphaned the first. One tap, one
    person.

I28. **The fire becomes an ember in front of the reader, and once.** The
    finishing sequence began when the lazy page built it, which can be a
    screen below the fold, so a slow reader could arrive at an ember that had
    already settled without them. It begins now when the finishing is in
    view — the same test that finishes the book. And a book finished before
    it was opened this time ends on its ember rather than burning down again:
    §9.1 has fire → ember once per book.

I29. **Not changed then, and done since (I32): a note's line height opened at
    once.** S04 asks for 400 ms. Deviation 6 says why iOS did not — the carve
    is an exclusion path, and opening it per frame means TextKit laying out
    the chapter and SwiftUI measuring it every frame — and says to revisit on
    a device. This pass had no device either, so the card still settles into a
    carve that is already there, as before. The honest way to do it is to
    animate the drawing rather than the layout (lay out once, draw the lines
    under the carve lifted and let them down), and it wants measuring on a
    phone before it is trusted. Everything above was built by CI's simulator
    compile and reasoned through against the SwiftUI documentation, and none
    of it has yet been run on a device.

I30. **The book opens on your verse (A54).** The decision and the hold are
    A54's; what is different on iOS is the aim. A `ScrollViewReader` can
    only aim at a view, and a chapter is one text view, so the landing sets
    a point-sized mark inside the chapter — at the depth that, put at the top
    of the screen, leaves the verse's line on the reading line — and scrolls
    to that. A chapter not on the page is two moves: the chapter, then the
    mark, once the chapter has been typeset and said where its lines are.

    **The one number the page has to learn.** `scrollTo(_:anchor: .top)`
    lines a view up with the top of the scroll view's visible area. The page
    measures itself in the `.scrollView` space, and the documentation does
    not say where one sits in the other — the safe area, give or take. So
    the page reads it off its first landing, which always starts from a
    known place: the chapter just put there by `.top`, or the first chapter
    at rest less the page's top margin. The last chapter is not trusted for
    it, because it can be too short to scroll its top all the way up. Until
    then the top content inset stands in. This is the number a device should
    check first. The hold means a wrong one moves where the verse sits by a
    few points, never which verse is yours.

    **Travelling one way.** "Back to where you were" eases. A chapter that is
    not on the page comes in at its top when the page is travelling down to
    it and at its foot travelling up, and the move to the line is made only
    if it carries on the same way: a verse the page would have to turn back
    for is already on the screen, and turning back is the overshoot §9.1
    forbids. Every other landing is made without animation. The book is
    still rising when it opens, so it arrives already there.

    **What else was wrong on iOS.** A notification tapped while the book was
    open left the page where it was: the target was read in `onAppear` and
    never again. It lands now, at once, as it always has on Android. The
    reading page had no identity of its own, so a notification that opened
    another book over this one would have kept this book's typeset chapters;
    it takes one per book. The running head named the chapter of the last
    save, which a held page does not make; it names the chapter on the
    screen, written only when that changes, so a scroll does not re-read the
    page on every verse. And `openedAt` was where the page was sent rather
    than your own place (Android has always taken your own), so a note opened
    and closed left the ribbon near it.

I31. **Four small things the motion pass saw and left.** I29 closed on what
    was not done; these were smaller, and they are done now.

    - **A mark taken back vanished.** Android lowers a removed wash to
      nothing (A38); iOS dropped it between two frames. It lifts off the
      words now on `arrive`, and a piece of it that another mark still
      covers is that mark's, drawn at once — the rule Android keeps. A fade
      stays a fade under reduce motion (I22).
    - **A field said nothing about being typed in.** The shared text field
      sits on paper this dark, and the caret was the only sign of focus —
      and on the sign-in, with an address and then a code, no sign at all of
      which field was listening. Its edge brightens under the caret. The
      caller's hold on focus is passed in rather than laid over the field
      from outside, so one binding, not two, decides where focus is.
    - **Quiet hours' wheel did not say whose it was.** Two times share one
      wheel, which opens under the row it sets; that row's time lights while
      the wheel is open, and is marked selected for VoiceOver. Android opens
      a dialog for each time and never needed it.
    - **The presence form's hint was a string in a view, and the hold had no
      equivalent.** The hint is in `Copy`, as the tap's consequence ("Follows
      them"), and the hold is published as an action, "Thinking of you", as
      it always has been on Android (§11: every gesture has an equivalent
      that is not a gesture).

I32. **The note unfurls: the line height opens over 400 ms (S04).** I29 left
    this with a way to do it; this is that way. Opening a note carves space
    under its verse with a TextKit exclusion path, which cannot animate, and
    moving one frame by frame would set the chapter again every frame. So the
    chapter is set once, with the carve where it now is, and the drawing is
    what moves. The layout manager draws the glyphs the carve displaced — and
    their washes — from where they were, and they ease home over `settle`:
    opening, the lines under the verse slide down to make the gap as the
    card fades into it; closing, they rise into it as the card goes.

    - **What moves** is worked out by glyph, not by height on the page: the
      lines after the verse's last line (the next verse can begin on that
      line, and its words stay put), below the old carve, the new one, or
      both. Opening one note while another is open moves each stretch by its
      own difference.
    - **A card that measures itself** while it is still opening — it starts
      at a guessed height — carries the gap on from wherever it is drawn
      that frame rather than jumping it.
    - **The margin's marks** are placed from the chapter's layout, so they
      would have arrived first; they ease to their new places on the same
      token.
    - **Not moved:** the chapter's own height, which changes at once. The
      foot of a long chapter is off the screen while a note opens in the
      middle of it; a note open near a chapter's end will show the passage
      end below stepping rather than sliding. Under reduce motion the carve
      is a change of state, as on Android (§11). Worth measuring on a device
      before it is trusted: a display link redraws the chapter for 400 ms.

I33. **Push: the six arrive when they happen (S19).** Every notification was
    the phone's own work until now — a background pull whenever the system
    allowed one, fifteen minutes apart at best (A34), and nothing at all
    for the two things with no row behind them, "Ruth is reading Mark" and
    a thinking-of-you sent to a closed app. The backend can send now, and
    this is the shape of it, on both platforms (Android's end is A56).

    - **Every push is a fact in the database first.** A trigger on notes,
      cards and readings — or a call from the phone for reading, leaving
      and thinking of you — writes a row to `push_outbox`, and the insert
      wakes the `push` function. The database says who hears what
      (`push_claim`); the function only words it and delivers it. So the
      function needs no secret to be safe to call: anyone who finds it can
      only make it deliver what was waiting, once.
    - **The switches are the phone's, as the phone holds them.** Each phone
      registers its token with every room's four switches, its quiet hours
      and its time zone — at launch, on every return to the foreground, and
      a second after a switch changes. The server judges quiet hours in the
      phone's own zone, because a phone that is asleep cannot judge
      anything.
    - **One voice, never two.** While the sender says it can reach APNs and
      this phone's registration went through, the phone posts none of the
      six itself; the background pull still merges, and the watermark still
      moves. A phone that is not registered — no permission, no token, the
      key not set yet — keeps speaking for itself exactly as before. The
      last answer is remembered, because a background pull runs before
      anything could ask.
    - **The third gate is the phone's.** A note about the room on screen is
      not presented (`willPresent`): a phone in your hand is not told what
      it is showing you. A finished book and a touch pass, as they always
      have.
    - **"When they open the book"** is said on an *arrival*: the book opened
      after half an hour away from it. The phone says it is reading on
      opening and every ten minutes while it stays open, and that it has
      left when the book closes, reading turns quiet, or the app goes away.
      Reading quietly tells the server nothing. The server keeps no record
      of any of it beyond `last_read`, the one stamp per person per room
      §13 already allows, overwritten in place.
    - **Thinking of you:** the socket still carries the touch to a phone in
      the room; the server carries the name to one that is not. In quiet
      hours nothing is pushed — S19 lets the touch arrive silently "as a
      haptic on an already-woken device", which is the socket's to give.
    - **Several notes:** a second note from the same person inside half an
      hour replaces the first, without a sound, and names only the person
      (§10.3). It is the same collapse the phones have always done, told by
      the server now.
    - **Nothing becomes history.** "Reading", "left" and thinking-of-you
      rows go five minutes after they are sent; note, card and book rows,
      which only point at rows that exist anyway, go after a day.
    - **What it needs to be heard:** the APNs key as secrets on the
      function (`supabase/README.md`), and the Push Notifications
      capability on the App ID with the profiles made again. Until both
      exist nothing changes: the sender answers that it cannot reach
      APNs, and every phone keeps posting for itself.

I34. **The widgets and the Live Activity (S24).** A second target, the
    widget extension, carries all three of S24's surfaces, and nothing on
    any of them answers how much or how often.

    - **The small widget** is the room's fire at its state on the unlit
      ground, and the book's name in small caps. The fire is drawn by the
      room's own painter (`FirePainter`, moved into `ios/Shared` so both
      targets compile it), held at one instant: a widget is a still.
    - **The lock screen:** the fire alone in the circle; the book and its
      state inline, in the slot's own face.
    - **The fire cools without the app.** The app writes the room's open
      reading's handiwork to the app group's container — not a state, the
      thing a state is worked out from — and the widget's timeline asks the
      core's engine for the state an hour at a time, so the home screen
      reports what the room would. The app rewrites it when it goes away
      and after every pull, and reloads the widget only when it changed.
    - **No book open is the unlit ground and nothing on it.** §08: an empty
      state is a reproach, so the widget is absent rather than empty.
    - **"Ruth is reading Mark"** is a Live Activity started by push (the
      server's `i_am_reading`, I33) on the phones whose "When they open the
      book" is on — the switch it belongs to, and off by default for the
      reason S19 gives. Her portrait and the sentence; nothing else. It is
      kept current by her phone's ten-minute heartbeat and ended when she
      leaves. A phone that dies mid-chapter never says it left, so the line
      goes into the past tense once the heartbeat has stopped for twenty
      minutes — "Ruth was reading Mark" — rather than go on claiming a
      presence nobody can vouch for (§4.2); and when this phone can hear the
      room's presence itself, it takes down any activity whose reader is not
      in the book.
    - **The faces** the Live Activity draws are small copies the app keeps
      in the group's container, because the extension cannot reach the
      app's own cache. A tap on any of it opens the room
      (`ribbon://room/<id>`).
    - **What it needs:** the App Group `group.bible.ribbon.app` on both App
      IDs, a `bible.ribbon.app.widgets` App ID, and profiles made again with
      them (the TestFlight workflow's "refresh profiles"). Until then the
      simulator build is unaffected; a signed build is not, which is the
      trade the owner chose.

## Licensed translations (decided: API.Bible)

Open question §16.8 is now part-decided: **NKJV plus two undecided
versions will come through API.Bible.** The architecture is in place and
gated off until the account and licenses exist:

- `TranslationRegistry` (core) holds bundled and licensed translations;
  the database stores a raw key and never enumerates, so adding a
  translation is a registry edit, not a migration.
- `APIBibleContent` (core, tested) converts API.Bible's
  `content-type=json` chapters into the same page model the bundled
  translations use — the reading surface never knows where text came from.
- The `bible-proxy` Supabase Edge Function (deployed) holds the API key as
  a server secret; the app never carries it. It answers 503 until
  `API_BIBLE_KEY` is set, which the app reads as "not offered yet," so
  NKJV never appears as a dead row in S20.
- **The licensing tension, stated plainly:** "Scripture is never locked"
  (§2.5) and first-class offline (§6.10) hold *fully* for the bundled
  public-domain translations. Licensed text streams, and the device keeps
  only the book being read (`RemoteScripture.swift` enforces the prune) —
  their license, not our design. A reader who needs guaranteed-offline
  Scripture always has BSB and WEB, whole.
- Scripture *text* search for a licensed translation runs over the bundled
  Berean text (hits are addresses; an address opens in the reader's own
  translation). NKJV shares KJV versification; the handful of
  verse-presence differences behave exactly like the BSB/WEB ones already
  do.

**Lit up (September 2026):** the API.Bible account exists with the Open
Book plan, `API_BIBLE_KEY` is set as the function secret, and the three
licensed editions are decided and wired with their catalog ids — NKJV
(`63097d2a0a2f7db3-01`), NIV 2011 (`78a9f6124f344018-01`), NASB 1995
(`b8ee27bcd1cae43a-01`). All three verified fetching through the proxy;
all three carry words-of-Jesus markup. The converter was validated
against the live payloads (verse markers are tags named "verse" whose
own items repeat the number — the converter classifies by name, never by
type, and never walks a marker's subtree).

**Domain:** invite links point at `readribbon.app` — the domain the room
owns. The brief's `ribbon.bible` was not acquired; the brief stands as
written, this ledger records the difference.

## Small product calls made here

- **"Begin Mark" vs "Continue in Mark"**: the way-in reads Begin until you
  have a position in the book, then Continue. The book only specifies
  Continue; Begin avoids "continuing" a book you haven't opened.
- The **coal bed deepens asymptotically** (+5.5% of the remaining distance
  per credited feeding, at most one credit per 4 hours). Nobody can read
  progress off it, which is the point (Law 4).
- **Subsiding curve**: steady reads burning after the ~36 h window closes
  and rests at catching after ~120 h; burning rests at ~96 h. A restart
  after ~96 h of quiet lands at catching, and any further feeding ≥15 min
  later lifts it. All in `FireTuning`, none of it ever surfaced in copy.
- **Search field** in the chooser also searches Scripture text of every
  bundled book (S23's scripture search); note search lives on the shelf
  (A55).
- **Four motion tokens the book does not name**, all in
  `RibbonMotion` (Android) because Android has two kinds of motion §9.1
  never had to describe. The **peel** — 6% smaller, 24 dp down, 28% toward
  the ground — is the shape of a predictive-back gesture, shared by the
  book, the menu and the presence panel so that everything comes away from
  the room the same way; it was three copies of the same three numbers.
  `RELEASE_MS` (220) is a back gesture *let go of*, which is quicker than
  anything arriving because nothing is arriving — the screen is being put
  back where it already was. `PRESS_IN_MS` (90) and `PRESS_OUT_MS` (220)
  are the two halves of the state layer that stands in for the ripple
  (§12.2): up almost at once, because a finger is already on the glass,
  and down at leisure, because by then the fade is the only thing left
  saying the tap was received.
