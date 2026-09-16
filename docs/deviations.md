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

6. **The note unfurl opens instantly, then the card settles in.** S04
   wants the line height to open over 400 ms. TextKit exclusion paths
   don't animate; animating them per-frame during layout is jank. The
   carve appears, and the card fades/settles over 400 ms. Revisit on
   device.

7. **Position restore is chapter-plus-verse tracked, chapter-anchored on
   open.** Reading reopens at your chapter; scroll-to-exact-verse is a
   device-tuning pass (`ScrollViewReader` needs laid-out geometry to
   target a verse's line).

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

11. **Phase-two surfaces remaining:**
    Cards (S08/S09) are now implemented on iOS and Android at the passage
    end. Remaining phase-two items: notifications delivery (S19 stores per-room
    switches locally; there is no push infrastructure yet), widgets and
    Live Activity (S24), rooms of three-plus ink-transition moment
    (model supports it; the invitation row on S01 is not yet built),
    StoreKit (S22 shows the model's promise only), and the web *join*
    (S16's browser half — the app-side join is built, deviation 10; the
    web page still previews and reads only).

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

A32. **The cards took the pass the rest of the app had already had.**
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

A33. **Notifications exist.** S19 is the screen the build book calls "the
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

A34. **S12 joins the rest of the app, and says something when there is
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

A34a. **The identity tile on You says what it is for, and the name it holds
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

    And the tile gained a sentence. It was the only tile on the screen that
    said nothing about itself, while the three rows beneath it each carry one
    — which A23 calls the half of that pass that mattered most. Neither of its
    two targets was drawn as a control: the portrait's affordance existed only
    as a content description and the name's only as a click label, both
    invisible on the screen S18 calls the place your identity lives.

A35. **Taking something back now takes it back.** The app has had "take
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

A36. **The invite path, end to end.** S15 says "the link is the whole
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

A36a. **The room has S01's third waiting row.** S01's anatomy has always read
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

A37. **The cuts that were left.** §9.1 opens "everything breathes rather
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

A38. **The word "streak" was shipping, on screen.** It is the first entry
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
    limit is stated in `RoomWatch`'s header and in A33.

A38a. **The gestures had no tap equivalents, on the app's central act.** §11
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

A39. **The seams.** The states a polish pass is judged by, and the easiest
    to leave half-made.

    **A voice note left offline never reached anybody.** The written path was
    queued and replayed on every foreground (A35); the voice path was left out
    of both halves, so a note recorded on a train drew its pending hairline
    (§4.4) and waited for a push that was never attempted again. Worse, the
    replay loop pushed the row without the file — `RemoteSync.push` only
    uploads audio when it is handed one — so even a queued voice note would
    have arrived as a waveform on somebody else's phone with nothing behind
    it. S25's "note failed to send" and §6.10's "notes queue with hairline
    marks" both describe a queue; half of one existed.

    **The background worker leaked a whole app every fifteen minutes.**
    `AppModel` is a `ViewModel`, and one built outside a `ViewModelStore`
    never has `onCleared` called — so A33's worker left an orphaned
    `ConnectivityManager` callback (A26 says it "has to be unregistered"), a
    live Realtime websocket with its own heartbeat and reconnect loop, and an
    uncancelled scope behind it on every run, forever. It also ran a GitHub
    update check each time. There is a `shutDown()` now, and
    `load(forBackgroundPull = true)` skips the three launch-time side effects
    a pull that exists to post a notification has no use for. A33's own note
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

A39a. **Two repairs that were themselves defects, and the queue that was
    still missing.** An adversarial pass over this work found three things.

    **The presence form's new semantics closed the panel it opened.** A38a
    gave the lozenge the action §11 asks for by merging the Box the gestures
    sit on — and that Box wraps the *expanded panel* as well. Compose's merge
    swallows descendant merge roots, which is the rule `NoteCard` states in
    this codebase, so with the panel open every control inside it — each
    row's follow and thinking-of-you actions, and "read quietly" — collapsed
    into one unactionable label. The merge is attached only while the form is
    collapsed now; the open panel's children speak for themselves and
    predictive back closes it.

    **The ember record's new way back scrolled away.** A38a put a chevron on
    the screen that had none, inside the scrolling column — which is verbatim
    the defect A34 had just removed from S12, named in that entry's own
    words. It is outside the scroll now.

    Both are the same lesson twice: a repair copied from a fix is not the
    fix, and the second half of each of those entries was the half that
    mattered.

    **Three offline mutations still had no queue.** A35 gave one to note
    pushes, note deletes and highlight deletes; A36 gave one to invites; A39
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
  bundled book (S23's scripture search); note search on the shelf is
  phase-aligned with cards and not yet built.
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
