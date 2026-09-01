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
    rather than being rewritten. Two honest edges: a portrait travels to
    a device once (a *changed* face doesn't refresh a device that has
    one — no version rides the profile row yet), and deleting an account
    deletes the profile row and everything it cascades, while the bare
    auth user (an email, nothing else) waits for a service-role function
    with the full engine.

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
