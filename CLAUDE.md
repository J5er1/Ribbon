# Ribbon

A shared Bible-reading app: iOS (Swift, `ios/`), Android (Kotlin, `android/`),
and a shared core written twice, case for case (`core/` in Swift,
`android/core/` in Kotlin). The backend is Supabase (`supabase/`).

## Rules that hold for every change

- **Every release a reader can notice says what it brought (A61, A65).** Add
  its entry at the head of `WhatsNew.releases` in both cores, with a new id,
  the day it ships, and its items; give each new `WhatsNewItem` its title and
  body in `Copy` on both phones, the release its own title, and a vignette —
  a small picture made of the page's own type and inks, in the idiom of the
  ones already in `WhatsNewScreen` on each platform. A change nobody can see
  adds nothing. The history in You lists every release, so an entry is never
  thrown away.
- **Both phones, both cores.** A behaviour lives in the cores when it can,
  ported case for case with the same tests on both; the two apps say the same
  words (`Copy.swift`, `Copy.kt`) in the same order.
- **The ledger.** A decision, a reversal or a deviation from the build book is
  written in `docs/deviations.md` as the next A-number (I-numbers for iOS-only
  notes), in the owner's words where they gave them. What still has to be
  done by hand, or seen on a phone, goes in `docs/still-to-do.md`.
