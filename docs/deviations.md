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

14. **The thread ends in the book, and the account is asked where the
    link is made.** (September 2026 pass.) §6.1 orders the thread mark →
    start or accept → name and portrait → invite → book → read; the
    previous build ended at the invite and dropped the person on an empty
    room that asked "Pick something to read together" twice. Now the
    chooser is the thread's last step and choosing opens the book itself,
    so the room is met for the first time on closing it, with a fire in it.
    The account moment lives inside the invite step (the backend needs an
    authenticated creator before a link can work), phrased as its reason —
    "Your email first, so the link works on their phone and the room stays
    yours" — with "Read on your own for now" always beside it; the word
    "sign in" is never said to a person who has no account. The invite is
    registered with the backend *before* the share sheet opens; if it
    can't be, the line says so and the link still goes out (it registers on
    the next foreground). A reinstall with a live session restores the
    person from the account underneath the mark's 900 ms hold and lands in
    their room without a second name question (§6.10). Signed in as someone
    else on the join screen shows "joining as Ruth · Not you?" and never
    silently joins (S16).

15. **One open reading, and a book set aside.** §03 says a room has one
    open reading at a time; the book never says what happens when a room
    picks another book while one is still going. Starting another used to
    orphan the new reading. Now picking another book *sets the first
    aside*: it keeps its notes, highlights and positions, is not an ember,
    appears under "still going" at the top of the chooser, and resumes the
    moment it is picked again. The chooser says so in one line, and nothing
    ever counts or reproaches it. The moment rides the reading row
    (`set_aside_at`, migration 20260902012000).

16. **Leaving keeps the shelf.** "You'll keep the books on your shelf"
    (§6.8) was not true — leaving deleted the room locally. A left room is
    now kept as a departed room (`Room.leftAt`, `Membership.leftAt`): it sits
    under "rooms you've left" in S14 with only its shelf, is never pulled
    back in by a sync (the membership delete is replayed before every pull
    when it was made offline), and a departed member stays in an ember's
    "who read it" with their ink (S11). A room of one *closes* rather than
    leaves itself, with the shelf's export offered first; multi-member
    closing (everyone agrees, or 30 days plus one request) waits for the
    agreement flow. Export (§13) exists: a Markdown file of the room's whole
    shelf, from the shelf and from You.

17. **Cards are built, local-first, on the five starter books.** Phase two
    per §15, built now because the model and the passage end were ready:
    one Ribbon-authored question per chapter of Mark, Ruth, Philippians and
    John, a specific set for nineteen psalms and a six-question rotation for
    the rest (`CardQuestions`, core, voice pinned by tests) — open question
    §16.4 resolved as its first option. Sealed, answered, opening (480 ms
    turn, a fade under Reduce Motion), set down, the ember record, and the
    S01 row all follow S08/S09. Not yet synced: like notes, cards travel
    with the full sync engine (deviation 10), so a card in a room of two
    opens on this phone when both answers exist *here*; until then a
    partner's answer arrives with sync. The schema's `cards_insert` policy,
    a unique (reading, chapter) constraint and a server-side opener ride
    that same pass.

18. **Ink identity is remembered, and the newcomer picks.** §4.5's
    transition is built: the room records when it first became three
    (`Room.inkIdentitySince`); the two originals see "An ink to pick" on
    S01 until they choose; the newcomer picks from what's left as the last
    step of joining; highlights from before carry "These keep their colors"
    in the toolbar and the ember record; identity stays when the room drops
    back to two until someone asks for the whole palette in You. Where a
    person has no ink at all (every room of two) their marks and monogram
    take a stable ink derived from their id — the same on every phone,
    never a choice, never written to the membership — so a partner's mark
    is never drawn in the viewer's own last-used ink.

19. **Read quietly is reachable when alone.** S07 says the panel can't be
    opened while alone unless you are already reading quietly — which left
    no way to *start* reading quietly before anyone arrives, the one moment
    Law 3 needs it. A hold on the Wave (and a VoiceOver action on it) opens
    the panel with only the toggle; the closed shape sits fully on screen
    with a 44 pt target. The lozenge and the panel are one piece of glass
    morphing (`GlassEffectContainer`), and the measure insets while the
    panel is open so glass never lies over a verse (§12.1).

20. **A finish needs a reader.** The finishing sequence used to fire the
    moment its section entered the viewport, so a short book finished
    itself on open on an iPad and any book on one flick. It now counts once
    the reader has scrolled to it — or, for a book that fits its screen,
    once it has been looked at for a few seconds — and the become plays only
    on screen. A finished book reopened from its ember shows the ember, no
    ceremony twice, and takes no marks.

21. **Speak is press-and-hold on the toolbar itself.** S05's speak used to
    start recording on a tap, so a hesitation kept a note of silence. The
    recording now begins under the finger, the live waveform rises above
    the toolbar, release keeps it, drag away discards it, and a recording
    cut by a call is kept and offered. VoiceOver gets a start/stop action
    instead of the hold (§11 motor).

22. **Persistence decodes every field with decodeIfPresent.** Swift's
    synthesized Decodable throws on a missing key even for a property with
    a default, so any field added to `AppState` after a release would have
    turned every existing state file into an empty one — and silently wiped
    a person's notes, highlights and rooms on the next save. Every stored
    struct now decodes field by field, an unreadable file is kept beside
    the live one rather than overwritten, and the rule is written at the top
    of `LocalStore.swift`.

23. **The membership insert policy counted through RLS.** The "first
    member only" guard on `memberships_insert` counted the room's members
    with a subquery that row-level security filters — a non-member sees no
    rows, so the count was always zero for exactly the person it exists to
    stop: any signed-in user who learned a room's id could seat themselves.
    Migration 20260902010000 moves the count into a security-definer
    function. Apply it with the set-aside column migration.

24. **Notifications settings say they are kept for later.** S19's switches
    change nothing until push infrastructure exists (deviation 11). Rather
    than a settings screen that silently does nothing, one line says the
    choices are kept for when Ribbon can send them. Likewise Plan (S22)
    carries no inert "Start the room again" control; it says plans arrive
    with the store.

24a. **Mark a quiet day appears only while a fire is going.** S01 has it
    "always present, never emphasised"; with no open reading there is
    nothing to bank, and a banked day marked on a fire-less room would make
    the next book open on a fire that reads banked instead of catching. It
    is absent on first run and between books, and in a paused or departed
    room.

25. **"In the night" is gone.** §4.9 forbids a wall-clock disclosure; the
    phrase for a read before dawn revealed a 3 a.m. It is "this morning"
    now, and the presence line reads from one last-read stamp per person
    per room (§13) instead of the 36-hour fuel window, so "Ruth read
    Tuesday" can actually appear.

26. **A departed room can be forgotten.** §6.8 keeps the shelf of a room
    you've left, and it stays kept. On that room alone — never in the rooms
    sheet, never on a live room — a quiet "Forget this room" sits under
    "You left this room. The shelf stays.", behind the same one
    confirmation as closing a room of one, because the shelf goes with it.
    Nothing counts down to it and nothing suggests it.

27. **"Not you?" resumes the join at the name.** S16 has the flow continue
    as a new person (name → email → code → join). Signing the last person
    out empties the local person, which takes their room — and the join
    sheet over it — down with them; the thread then re-presents the same
    invite and, since it was accepted once already, opens at the name
    rather than at a second preview.

28. **"Send it again" follows the tap, not the send.** The system share
    sheet says nothing back about whether anything went out. The tap on
    "Send the invite" is the one signal there is, so the control reads
    "Send it again" after it — which is also what it does — and the quiet
    way past ("Invite later") stays visible either way.

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
