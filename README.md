# Ribbon

A quiet, shared place to read the Bible with the people you'd actually want
to read it with. *Read it together.*

This repo holds the brand documents, the iOS app, its platform-independent
core, and the backend schema. The product is specified by two documents —
read them first, in this order:

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
ios/         The app. Xcode 26+, SwiftUI, iOS 26, dark-only (dark is the
             product). Open ios/Ribbon.xcodeproj — no generators, no pods.
supabase/    Postgres schema + RLS for the live backend project
             ("ribbon" in the Aeaura org), applied via migrations.
tools/       Asset pipeline: USFX→JSON Scripture conversion, the paper
             grain, the app icon, the Wave's Swift geometry.
mark/        The Wave (W6) SVG studies.
docs/        docs/deviations.md — every knowing departure from the build
             book, with reasoning. Read before assuming a bug.
```

## Building the app

Open `ios/Ribbon.xcodeproj` in Xcode 26 or newer, select your team under
Signing, and run. Everything is vendored: fonts (Literata, Alegreya Sans,
Alegreya Sans SC — all OFL), both launch translations (Berean Standard and
World English, public domain, one JSON per book), the grain, the icon.
There are no third-party package dependencies; the one local package is
`core/`.

Core tests: `cd core && swift test` (works on macOS or Linux).

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

Phase one (§15) is in place end to end: the room and its fire, reading,
notes (voice with on-device transcripts + written), ink and highlights
with real blending, quiet days, finishing a book, the shelf and ember
records, the chooser, onboarding, and settings — and a room of two is now
reachable for real: accounts (emailed code), invites that open the app,
the S16 join flow, and two-way sync of the room surface. The presence
socket and the content half of sync (notes, highlights, positions) are
the next piece of work. `docs/deviations.md` is the honest ledger.
