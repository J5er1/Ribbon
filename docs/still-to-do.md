# Still to do

What is built but not yet switched on, and what it is waiting for. Each item
says what happens until it is done, so nothing here is a silent failure.

## 1. A mark follows its words: the migration, before the apps

`supabase/migrations/20261002120000_ribbon_a_mark_follows_its_words.sql`
adds `start_words`, `end_words` and `words_source` to `highlights`
(docs/deviations.md A60, I37). **Apply it to the live project before any
build that writes them reaches a phone.** Those builds send the three
columns with every mark on part of a verse, and PostgREST refuses a write
that names a column the table does not have — so on an unmigrated project
every phrase mark from a new build fails to push. It stays on the phone
that made it, where nobody else in the room can see it, and the app's
later tries are refused the same way until the migration lands.
Whole-verse marks write none of the columns and are unaffected. Older
builds are unaffected either way: the columns are nullable and they never
name them.

It is idempotent and changes no policy. Run the file in the SQL editor of
project `ribbon` (`noyccfkaotuvhhaoccck`), then check that

```
select column_name from information_schema.columns
where table_name = 'highlights' and column_name like '%words%';
```

answers `start_words`, `end_words` and `words_source`. Ship the builds
after that, not before.

Nothing else here needs switching on: the original words, the definitions
and the links ship inside both apps, and a licensed version's links are
worked out on the phone. The one line of credit for them is already under
the version line on You.

## 2. Notifications: built, not switched on

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
>
> Today it stops sooner still: the lane has failed on every run since at
> least 11 September, three seconds in, because `ASC_KEY_ID` and
> `ASC_KEY_CONTENT` are not set as repository secrets. Xcode Cloud's Default
> workflow archives every merge to `main` on its own; whether it hands those
> builds to TestFlight is set in App Store Connect. The version line under
> You now says which build a phone has (I36).

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

## 3. The room's live channel — closed on 25 September

`realtime.messages` now carries the migration's two policies
(`ribbon_room_channel_read`, `ribbon_room_channel_write`), applied to the
live project on 25 September 2026: a private join is refused unless the
account is a member of the room, and both apps' private join is accepted for
members. They never landed before because the file's `alter table ... enable
row level security` needs the table's owner, which `postgres` is not on a
hosted project, and the block's catch-all swallowed that error and both
policies after it. The file now asks for that only where it is off, and no
longer swallows errors.

A phone that joined before the policies landed was refused, fell back to the
public channel, and stays there until the room is opened again — and a
private and a public channel with the same name do not hear each other. Close
and reopen the app on every phone once. Builds from before 14 September
(#14) join public only and will not see anyone on a current build.

## 4. One account, whichever door

The browser join (deviation 22) signs in with the emailed code. In the app,
the same person is the same account only through the same door — the
emailed code, with the same address; the browser sign-in the app leads with
(Auth0) makes a different account. The fix is an Auth0 application for
`readribbon.app` (callback, logout and web-origin URLs), after which the
invite page can use it too.

## 5. Email that reaches people outside the team

If invitees do not receive sign-in codes, Supabase is sending with its
built-in mailer, which delivers only to the project's own team and only a
few an hour. Set a custom SMTP provider under Authentication → Emails →
SMTP (Resend, Postmark and SES all work).

## 6. Seen on a real phone

Built and compiled, never yet watched on hardware: the note unfurl (I32 —
a display link redraws the chapter for 400 ms), the widgets and the Live
Activity (I34, A57), and the ongoing "is reading" line on Android (A56).

Following (A58, I35) most of all, with two phones in one room:

- Follow someone and read along: the page should hold while they read and
  move a few lines at a time, easing, never backwards unless they went back.
- Put their phone down for a minute: yours should not run on past them.
- Follow each other: neither page should move with nobody touching it.
- Scroll once while following: the page goes back to them. Scroll again:
  the follow ends, and "back to where you were" is offered.
- With VoiceOver or TalkBack on: the page moves only when their line leaves
  the screen, and a screen-reader scroll ends the follow.
- Answer a message and come back within fifteen seconds: nobody should
  have left the room.
- Follow someone resting at the end of a chapter, or on its card: your
  page shows the end of that chapter, not the next one's head (A59).
- Before any of it, check each phone's build: the version line under You
  says it on both (I36).
- Afterwards, the project's Realtime logs should show no new
  `ClientPresenceRateLimitReached`.

A mark following its words (A60, I37), which on iOS has not yet been
compiled — build it in Xcode first:

- Two phones on two versions, the Berean and the World English. Mark part
  of a verse on one: the other shows the same words in its own version,
  or the whole verse where they cannot be matched.
- Mark a phrase on Android, close the book, open it again: still a phrase
  (A60's toolbar defect).
- Hold a word: the verse lifts as always, and the line above the inks shows
  it in the Hebrew or Greek. Tap the line, then a word: its definition and
  grammar open under it. Hebrew reads right to left, and nothing is clipped.
- With a licensed version on one phone, open the panel: "In this room"
  shows its words once the chapter has come, and says so when it has not.
- Follow someone reading the other version: your page should land on the
  same words, not the same fraction of the verse.
- With VoiceOver or TalkBack: "the original words" on a verse opens the
  panel, and each word is read as its sound, its rendering and its grammar.
