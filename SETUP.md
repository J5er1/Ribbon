# TestFlight setup

This repo has a real app at `ios/Ribbon.xcodeproj` (see the root `README.md`
for the full layout). This doc covers the GitHub Actions pipeline
(`.github/workflows/testflight.yml`) that builds it and uploads to
TestFlight via Fastlane. The pipeline can't run until the Apple-side pieces
below exist and the secrets are added to GitHub — none of that can be done
from an agent session, it needs your Apple Developer account.

## What was added for CI, specifically

- `ios/Ribbon.xcodeproj/xcshareddata/xcschemes/Ribbon.xcscheme` — a shared
  scheme. The project didn't have one committed (only an unshared,
  locally-autocreated scheme, which doesn't exist on a fresh CI checkout),
  so `xcodebuild`/`fastlane` had nothing to build against. This doesn't
  change how the project behaves locally.
- `fastlane/` (`Appfile`, `Matchfile`, `Fastfile`) — builds `ios/Ribbon.xcodeproj`
  scheme `Ribbon` and uploads to TestFlight. Signing is `Automatic` in the
  checked-in project (fine for local development); the `beta` lane overrides
  it to `Manual` at build time via `xcargs`, pointed at a `match`-managed
  provisioning profile, without touching the project file.
- `.github/workflows/testflight.yml` — runs the `beta` lane on push to
  `main` or on demand (`workflow_dispatch`).

## One-time Apple-side setup

1. **Apple Developer Program membership** (org or individual, $99/yr), with
   admin access to [App Store Connect](https://appstoreconnect.apple.com).
2. **Register the bundle ID** `bible.ribbon.app` in developer.apple.com →
   Certificates, IDs & Profiles → Identifiers → `+` (skip if already
   registered).
3. **Create the app record** in App Store Connect → Apps → `+` → New App,
   using that bundle ID.
4. **Create an App Store Connect API key**: App Store Connect →
   Users and Access → Integrations → App Store Connect API → `+`. Role:
   App Manager (or Admin). Download the `.p8` key **once** (Apple won't let
   you re-download it) and note the Key ID and Issuer ID.
5. **Create a private repo for `match`** (fastlane's certificate/profile
   storage) — e.g. `ribbon-ios-certs`. It can be empty; `match` initializes
   it on first run. Keep it private, it will hold your signing identity.
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

1. **Generate certs/profiles once, locally, from a Mac** (before CI can use
   `match`'s default readonly-in-CI behavior): install fastlane
   (`bundle install`), then run `bundle exec fastlane match appstore` from
   the repo root with `APP_IDENTIFIER`, `TEAM_ID`, `MATCH_GIT_URL`,
   `MATCH_PASSWORD`, and the `ASC_*` env vars set locally. This creates and
   stores the distribution certificate + provisioning profile in the certs
   repo. `setup_ci` in the `beta` lane makes match read-only automatically
   when `CI` is set, so the GitHub Actions run only ever reads what you
   created here.
2. Push to `main`, or run the **TestFlight** workflow manually from the
   Actions tab (`workflow_dispatch`).
3. Watch the Actions run. This has not been through a real `xcodebuild` —
   it was authored and syntax-checked without macOS/Xcode available, so
   expect to debug the first pass. Likely first issues: the runner's
   default Xcode not matching the project's iOS 26 deployment target (pin
   `xcode-version` in the workflow if `latest-stable` isn't new enough), or
   a missing/misnamed secret.
4. Once a build lands in App Store Connect → TestFlight, add internal
   testers (your team, available immediately) or external testers (submit
   for Beta App Review — first one takes ~24–48h, later builds are usually
   automatic unless you change permissions/encryption).

## Local development

Nothing changes here — same as the root `README.md`: open
`ios/Ribbon.xcodeproj` in Xcode 26+, select your team under Signing, and
run.
