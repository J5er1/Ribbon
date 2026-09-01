# TestFlight setup

This repo now has a minimal SwiftUI scaffold (`Ribbon/`) and a GitHub Actions
pipeline (`.github/workflows/testflight.yml`) that builds it and uploads to
TestFlight via Fastlane. The pipeline can't run until the Apple-side pieces
below exist and the secrets are added to GitHub. Nothing here has been
built/tested against a real Xcode toolchain (this environment doesn't have
one) — expect to debug the first CI run.

## What's a placeholder right now

- **Bundle ID**: `bible.ribbon.app`, set in `project.yml` and `fastlane/Appfile`.
  Change it everywhere (both files) if you register a different one.
- **App icon**: rasterized from `mark/ribbon-mark-on-black-square.svg` (chartreuse
  mark on near-black) just so the archive has a valid icon. The design brief
  (`ribbon-design-brief.md`, §5 "The mark") specifies the real icon should be
  Crimson/Lamp on the `#0A0806` ground — swap
  `Ribbon/Assets.xcassets/AppIcon.appiconset/icon-1024.png` for the final export
  once it exists.
- **App content**: a single "Ribbon / Read it together." screen — enough to
  compile and archive, not the product.

## One-time Apple-side setup

1. **Apple Developer Program membership** (org or individual, $99/yr), with
   admin access to [App Store Connect](https://appstoreconnect.apple.com).
2. **Register the bundle ID** in developer.apple.com → Certificates, IDs &
   Profiles → Identifiers → `+`. Use `bible.ribbon.app` or update the repo to
   match whatever you register.
3. **Create the app record** in App Store Connect → Apps → `+` → New App,
   using that bundle ID.
4. **Create an App Store Connect API key**: App Store Connect →
   Users and Access → Integrations → App Store Connect API → `+`. Role:
   App Manager (or Admin). Download the `.p8` key **once** (Apple won't let
   you re-download it) and note the Key ID and Issuer ID.
5. **Create a private repo for `match`** (fastlane's certificate/profile
   storage) — e.g. `ribbon-ios-certs`. It can be empty; `match` initializes it
   on first run. Keep it private, it will hold your signing identity.
6. **Find your Team ID**: developer.apple.com → Membership, or the
   App Store Connect API key page.

## GitHub secrets to add

Repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `TEAM_ID` | Apple Developer Team ID |
| `ASC_KEY_ID` | App Store Connect API key ID |
| `ASC_ISSUER_ID` | App Store Connect API issuer ID |
| `ASC_KEY_CONTENT` | The `.p8` key file contents, base64-encoded: `base64 -i AuthKey_XXXX.p8 \| pbcopy` |
| `MATCH_GIT_URL` | SSH or HTTPS URL of the private certs repo, e.g. `https://github.com/<org>/ribbon-ios-certs.git` |
| `MATCH_PASSWORD` | A passphrase you choose to encrypt items in the certs repo (save it somewhere safe — you'll need it again for any machine running `match`) |
| `MATCH_GIT_BASIC_AUTHORIZATION` | Only if the certs repo is HTTPS and private: base64 of `username:personal_access_token` with repo read access |

## First run

1. **Generate certs/profiles once, locally** (from a Mac, before CI can use
   `readonly` mode): install fastlane (`bundle install`), then run
   `bundle exec fastlane match appstore` with `APP_IDENTIFIER`, `TEAM_ID`,
   `MATCH_GIT_URL`, `MATCH_PASSWORD`, and the `ASC_*` env vars set locally.
   This creates and stores the distribution certificate + provisioning
   profile in the certs repo. CI only ever reads them (`readonly: true` in
   `fastlane/Matchfile`).
2. Push to `main`, or run the **TestFlight** workflow manually from the
   Actions tab (`workflow_dispatch`).
3. Watch the Actions run. First failures are almost always: wrong bundle ID
   between `project.yml`/`Appfile` and the one registered in Apple's portal,
   or a secret that wasn't set.
4. Once a build lands in App Store Connect → TestFlight, add internal
   testers (your team, available immediately) or external testers (submit
   for Beta App Review — first one takes ~24–48h, later builds are usually
   automatic unless you change permissions/encryption).

## Local development

```
brew install xcodegen
xcodegen generate
open Ribbon.xcodeproj
```

The generated `.xcodeproj` is gitignored — regenerate it from `project.yml`
after pulling changes.
