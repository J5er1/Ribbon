# Deviations and open calls

The build book asks that every knowing departure be written down as one, so
a future reviewer can tell an intention from an accident. These are the
departures in the current build, with reasoning attached. Argue with the
reasoning.

## From the build book

1. **The app icon is the true Cesso R.** — resolved. The shipped
   `AppIcon.png` (and the masters in `mark/ribbon-icon-cesso-2048.png`
   and `mark/ribbon-wordmark-cesso.png`) were rendered from the real
   Cesso Regular under the project owner's Adobe Fonts entitlement (web
   kit `sqw7zeu`, family css name `cesso`): the letterforms were rendered
   in a headless browser at 2× and downsampled — no font file is
   embedded or committed, only the rasters, which is the permitted
   logo-image use. To regenerate: fetch `https://use.typekit.net/sqw7zeu.css`,
   download its woff2 transiently, render "R." at 620px on a 1024pt
   `#0B0B0A` canvas (ivory `#F3F0E6` R, chartreuse `#D6E45C` period,
   `margin-left: 0.02em`), screenshot at 2×, downscale. The woff2 must
   never be committed. `make_icon()` in `tools/make_assets.py` remains as
   the entitlement-free fallback (Literata stand-in).

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

10. **Remote sync is dark** (`SupabaseConfig.remoteEnabled = false`). The
    schema is applied to the live project and the typed client exists;
    the sign-in thread (emailed code, §6.10) and the sync engine are the
    next piece of work. The app is local-first by design either way.

11. **Phase-two surfaces are absent, per §15**: cards (S08/S09 — model and
    schema exist, no UI), notifications delivery (S19 stores per-room
    switches locally; there is no push infrastructure yet), widgets and
    Live Activity (S24), rooms of three-plus ink-transition moment
    (model supports it; the invitation row on S01 is not yet built),
    StoreKit (S22 shows the model's promise only), and the web
    invite/accept + web reading (S16/S26).

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
