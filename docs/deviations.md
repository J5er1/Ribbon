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

9. **Following, live presence, and thinking-of-you are built against
   `PresenceService`, whose only shipping implementation is the honest
   local one (nobody is ever present).** The Supabase Realtime channel
   client is the next backend step. The UI states exist: reading quietly,
   idle, the follow thread, being-followed ("Ruth is with you", once, with
   the tucked portrait), follow-break on your own scroll, and the
   two-minute back-to-where-you-were offer. Not built: the rubber-band on
   the first self-scroll of a follow (the first scroll breaks it
   directly), and the page-fly transition (a plain settle scroll stands
   in).

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

10. **The sign-in thread is wired; the sync engine is half-lit.**
    (September 2026.) `SupabaseConfig.remoteEnabled = true`: accounts are
    an emailed code (§6.10, `RemoteSync` + Keychain session), invites are
    registered with the backend when handed out, links open the app
    (universal link `readribbon.app/i/…` + `ribbon://` fallback, with a
    paste-the-link fallback in onboarding), `accept_invite` runs from the
    S16 join flow, and the **room surface** syncs both ways — rooms,
    memberships, profiles + portraits, readings, fires, the rolling fuel
    window, quiet days — so a couple on two phones sees one room, one
    fire, one shelf, and steady can genuinely happen. Still local-only:
    notes, highlights, positions, note-founds, and voice audio (their
    sync, with pending marks and storage transfer, is the full engine —
    the next piece of work), and live presence (deviation 9). On first
    sign-in the local person adopts the account's id everywhere
    (`adoptRemoteIdentity`); fuel-window person ids age out on their own
    rather than being rewritten. Three honest edges: a portrait travels
    to a device once (a *changed* face doesn't refresh a device that has
    one — no version rides the profile row yet); the S16 join preview
    shows the inviter's *name* but not yet their portrait ("their
    portrait" per the book) — the portraits bucket is membership-gated
    and the joiner is anonymous, so the face needs a token-gated edge
    function or a signed URL in `invite_preview`, which rides the next
    backend pass; and deleting an account deletes the profile row and
    everything it cascades, while the bare auth user (an email, nothing
    else) waits for a service-role function with the full engine.

11. **Phase-two surfaces are absent, per §15**: cards (S08/S09 — model and
    schema exist, no UI), notifications delivery (S19 stores per-room
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

## Android (phase three)

The Android build is a second implementation of one product, not a second
product. These are its knowing departures — from the build book where it
names Android (§12.2), and from the iOS app where the two platforms could
not honestly be made identical. Everything §12.2 asks for that is *not*
listed here is simply built: Expressive shapes and damped spring physics,
no FAB, no bottom navigation, no visible loading, predictive back,
mandatory edge-to-edge, Alegreya Sans SC as a real small-caps face rather
than a textTransform, and dynamic colour declined.

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

A11. **`ReflectionCard.answers` will not round-trip between the two
    apps.** Swift's `JSONEncoder` writes a dictionary with a non-String key
    as a flat alternating array; kotlinx-serialization writes a JSON
    object, because `Uuid`'s descriptor is a string primitive. This is
    latent — cards are phase two (§15) and `answers` is serialized nowhere
    yet — and it is listed so that whoever lights the cards up fixes the
    shape on both sides at once rather than discovering it in a room.

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
