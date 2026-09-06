# Ribbon

A quiet, shared place to read the Bible with the people you'd actually want
to read it with. *Read it together.*

This repo holds the brand documents, the iOS and Android apps, their
platform-independent cores, and the backend schema. The product is specified
by two documents — read them first, in this order:

1. `ribbon-design-brief.md` — the brand brief (voice, palette, vocabulary,
   the never-ship list).
2. `ribbon-build-book.html` — the build book: every screen (S01–S26),
   every state, every gesture, and the five laws. Where they disagree on
   the wordmark, palette, mark, or icon, the build book wins.

## Layout

```
core/        RibbonCore — Swift package, Foundation only.
             The object model, the fire engine (§4.1), time rules (§4.9),
             the 66-book table, Scripture data model. Unit-tested; runs
             anywhere Swift runs, including Linux CI.
ios/         The iOS app. Xcode 26+, SwiftUI, iOS 26, dark-only (dark is
             the product). Open ios/Ribbon.xcodeproj — no generators, no pods.
android/     The Android app (phase three). Jetpack Compose, Material 3
             Expressive, dark-only. :core is RibbonCore in Kotlin, checked
             against the Swift by the same test suites; :app is the app.
             Scripture, the fonts and the grain are not duplicated — they
             are synced out of ios/Ribbon/Resources at build time.
supabase/    Postgres schema + RLS for the live backend project
             ("ribbon" in the Aeaura org), applied via migrations.
tools/       Asset pipeline: USFX→JSON Scripture conversion, the paper
             grain, both app icons, and the Wave's geometry for both
             platforms. The mark's path data lives here once; neither app
             hand-copies it.
mark/        The Wave (W6) SVG studies.
docs/        docs/deviations.md — every knowing departure from the build
             book, with reasoning. Read before assuming a bug.
```

## Building the iOS app

Open `ios/Ribbon.xcodeproj` in Xcode 26 or newer, select your team under
Signing, and run. Everything is vendored: fonts (Literata, Alegreya Sans,
Alegreya Sans SC — all OFL), both launch translations (Berean Standard and
World English, public domain, one JSON per book), the grain, the icon.
There are no third-party package dependencies; the one local package is
`core/`.

Core tests: `cd core && swift test` (works on macOS or Linux).

## Building the Android app

Open `android/` in Android Studio, or from the command line:

```
cd android && ./gradlew :app:assembleDebug
```

Kotlin core tests: `cd android && ./gradlew :core:test`.

The Kotlin core is a port of the Swift one, and the guard against the two
drifting is that all five Swift test suites are ported case-for-case — same
names, same inputs, same expected values — and both run in CI. If the fire
curve or the fire-scale boundary ever diverges between the platforms, a red
build is where it should surface rather than a couple's two phones
disagreeing about their fire. `docs/deviations.md` §A is the Android ledger;
A1 explains why minSdk is 31 where §12.2 says API 36.

## Scripture data

`tools/usfx_to_json.py` converts the public-domain USFX sources from
ebible.org into per-book JSON preserving paragraphs, poetry indents, psalm
titles, and (WEB) red-letter markup, and regenerates the book table with
real word counts:

```
curl -O https://ebible.org/Scriptures/engwebp_usfx.zip   # World English
curl -O https://ebible.org/Scriptures/engbsb_usfx.zip    # Berean Standard
unzip -d engwebp_usfx engwebp_usfx.zip && unzip -d engbsb_usfx engbsb_usfx.zip
python3 tools/usfx_to_json.py .
```

## Backend

The live Supabase project is `ribbon` (`noyccfkaotuvhhaoccck`,
us-east-1). The schema in `supabase/migrations` is applied; row-level
security enforces the product's privacy posture structurally — see the
comments in the migration. The app is local-first and fully usable with
no account. The sign-in thread is wired (emailed code, `RemoteSync`):
invites register with the backend, invite links open the app and run the
S16 join flow, and the room surface — rooms, members, profiles,
readings, fires, quiet days — syncs both ways. Notes, highlights and
positions still travel with the full sync engine, which is the next
piece of work (`docs/deviations.md` §10). Universal links need
`RIBBON_APPLE_TEAM_ID` set in the Vercel project so the web build emits
a real `apple-app-site-association`.

## Licensed translations

NKJV and two undecided versions will arrive via API.Bible. The plumbing is
in place — translation registry in core, a tested converter for API.Bible
chapter JSON, the `bible-proxy` edge function holding the key server-side,
and a stream-and-cache-the-open-book policy on device (their license, not
our design; the bundled public-domain translations stay whole and
offline). It lights up when the API.Bible account + NKJV license exist:
set `API_BIBLE_KEY` as a function secret and fill in the edition's
bibleID in `TranslationRegistry`. Details in docs/deviations.md.

## What's built, what's next

Phase one (§15) is in place end to end on iOS: the room and its fire,
reading, notes (voice with on-device transcripts + written), ink and
highlights with real blending, quiet days, finishing a book, the shelf and
ember records, the chooser, onboarding, and settings — and a room of two is
reachable for real: accounts (emailed code), invites that open the app, the
S16 join flow, and two-way sync of the room surface.

**Phase three — Android — now stands beside it**, the same product against
the same backend: RibbonCore in Kotlin (checked against the Swift by the
same test suites), the design system, the campfire and the ember, the
reading surface with real ink blending, notes, presence, and every screen
through to the join flow and settings. It builds and its tests pass; it has
not yet been run on a physical device.

Still ahead on both platforms: the presence socket and the content half of
sync (notes, highlights, positions), and everything §15 puts in phase two.
`docs/deviations.md` is the honest ledger — §A is Android's.
