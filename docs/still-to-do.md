# Still to do

What is built but not yet switched on, and what it is waiting for. Each item
says what happens until it is done, so nothing here is a silent failure.

## 1. Notifications: built, not switched on

Push is built end to end and deployed: the database writes each of the six
as it happens, the `push` function on the live project delivers them, and
both apps register for them and stop posting their own once the server is
delivering (docs/deviations.md I33, A56). **Nothing is delivered by push
until the keys below exist.** Until then every phone keeps posting its own
notifications from the background pull, exactly as before — late, and
never for "Ruth is reading Mark" or a thinking-of-you sent to a closed app.

The same keys switch on the Live Activity (I34), which is started by push.

### Apple — iOS push, the Live Activity, the widget

In the developer portal (Certificates, Identifiers & Profiles):

1. **Identifiers → `bible.ribbon.app`**: turn on **Push Notifications** and
   **App Groups**.
2. **Identifiers → App Groups**: register `group.bible.ribbon.app`, and
   assign it to `bible.ribbon.app`.
3. **Identifiers → +**: register the widget's App ID,
   `bible.ribbon.app.widgets`, with **App Groups** on and the same group.
4. **Keys → +**: an **Apple Push Notifications service (APNs)** key.
   Download the `.p8` (it can be downloaded once) and note its Key ID and
   the Team ID.
5. **Supabase → Edge Functions → Secrets** (project `ribbon`):
   `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_PRIVATE_KEY` (the whole `.p8`).
6. **GitHub → Actions → TestFlight → Run workflow**, with **refresh
   profiles** ticked, once — so match makes both App Store profiles again
   with the new capabilities.

> Until steps 1–3 and 6 are done, **the TestFlight build on `main` fails at
> signing**: the app now asks for push and the App Group, and carries the
> widget extension, and the old profiles cover neither. The simulator build
> in CI is unaffected.

### Firebase — Android push

1. Create a Firebase project and add two Android apps: `app.readribbon`
   and `app.readribbon.debug` (the APK at readribbon.app/apk is the debug
   one).
2. Download `google-services.json` and commit it at
   `android/app/google-services.json`. The build reads it directly; there
   is no plugin to add.
3. **Project settings → Service accounts → Generate new private key**, and
   set the whole JSON as the Supabase secret `FCM_SERVICE_ACCOUNT`.

### How to know it is on

`https://noyccfkaotuvhhaoccck.supabase.co/functions/v1/push` answers
`{"ios": true, "android": true}` once both sets of keys are in (it says
`false` for each today). Then, on a phone: sign in, allow notifications
when the app asks, and have someone in the room leave a note.

## 2. The room's live channel is still public

`supabase/migrations/20260914120000_ribbon_realtime_room_channel.sql` has
never been applied to the live project: `realtime.messages` has row-level
security on and no policies, so every private join is refused and both apps
fall back to a public channel — which means a room's presence and its
thinking-of-you taps can be read by anyone who holds the publishable key
and guesses a room id. Applying the migration closes it; the apps retry the
private join every time a room opens, so nothing else changes. (Found while
deploying push; not applied without asking, because it changes how every
live room connects.)

## 3. One account, whichever door

The browser join (deviation 22) signs in with the emailed code. In the app,
the same person is the same account only through the same door — the
emailed code, with the same address; the browser sign-in the app leads with
(Auth0) makes a different account. The fix is an Auth0 application for
`readribbon.app` (callback, logout and web-origin URLs), after which the
invite page can use it too.

## 4. Email that reaches people outside the team

If invitees do not receive sign-in codes, Supabase is sending with its
built-in mailer, which delivers only to the project's own team and only a
few an hour. Set a custom SMTP provider under Authentication → Emails →
SMTP (Resend, Postmark and SES all work).

## 5. Seen on a real phone

Built and compiled, never yet watched on hardware: the note unfurl (I32 —
a display link redraws the chapter for 400 ms), the widgets and the Live
Activity (I34, A57), and the ongoing "is reading" line on Android (A56).
