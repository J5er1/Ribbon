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

## Android (phase three)

The Android build is a second implementation of one product, not a second
product. These are its knowing departures — from the build book where it
names Android (§12.2), and from the iOS app where the two platforms could
not honestly be made identical. Everything §12.2 asks for that is *not*
listed here is simply built: Expressive shapes and damped spring physics,
no FAB, no bottom navigation, no visible loading, predictive back,
mandatory edge-to-edge, Alegreya Sans SC as a real small-caps face rather
than a textTransform, and dynamic colour declined.

A1. **minSdk is 31, not the literal 36.** §12.2 reads "Target: Android 16+
    (API 36)". The app compiles against and targets exactly that, but taken
    as an *install floor* it would ship to almost no phones. Everything
    §12.2 actually asks for is present at Android 12: the Expressive shape
    and motion systems, edge-to-edge, and `VibrationEffect.Composition` for
    the thinking-of-you tap (API 30). Predictive back degrades to ordinary
    back below 33, which is the one visible loss. `compileSdk` is 37 —
    not a product call, just the floor the current AndroidX libraries
    impose; `targetSdk` stays at the 36 the book pins, because that is the
    number that decides runtime behaviour.

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

A12. **Voice notes get no transcript on Android 12 and 12L.** Deviation 4
    holds — transcription is on-device on both platforms, and on Android
    that is `SpeechRecognizer.createOnDeviceSpeechRecognizer`, so the audio
    never leaves the phone. But Android's recognizer listens to a *stream*,
    not to a file: the only way to hand it a finished recording is
    `RecognizerIntent.EXTRA_AUDIO_SOURCE`, which arrived in API 33. On 31
    and 32 there is no route from a recorded note to a transcript at all.
    Those two releases therefore get exactly what a failure gets — null,
    and the honest "No transcript for this one." with Try again — rather
    than a worse transcript or a silent absence.

    **This is unresolved, not settled.** §11 makes transcripts mandatory
    ("a voice-only note is unreadable to a deaf member and unfindable to
    everyone in six months"), and an OS version that structurally cannot
    produce one is a real hole in that promise. Three ways out, none taken
    yet because the choice is the owner's:

    - Raise minSdk to 33. §12.2 asked for 36 in the first place, so 33 is
      still far more permissive than the book, and it closes the hole
      completely.
    - Transcribe live, off the microphone, while the note is being
      recorded — works on every release, but it puts two consumers on the
      microphone at once, which is unreliable on some devices.
    - Fall back to a server transcriber on 31/32 only. The interface was
      built to allow exactly this (deviation 4), at the cost of the
      privacy answer on those devices.

    Everything below API 33 also needs `decodeToPcm`, which is carried
    regardless: the note is AAC in MPEG-4 and the recognizer wants raw
    16-bit PCM, so it is decoded into a cache scratch file that is deleted
    the moment recognition ends.

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
