# Still to do

What is built but not yet switched on, and what it is waiting for. Each item
says what happens until it is done, so nothing here is a silent failure.

## 1. A mark follows its words — the migration, applied on 4 October

`supabase/migrations/20261002120000_ribbon_a_mark_follows_its_words.sql`
is on the live project (recorded as `20261004042514`): `highlights` has
`start_words`, `end_words` and `words_source`, and all four checks are
validated (docs/deviations.md A60, I37). It was rehearsed there in a
transaction that was rolled back: a phrase with its words, a mark across two
verses and an old client's whole-verse row were accepted; words without a
source, two sets inside one verse, a negative position, an empty set, a null
inside a set and an empty source were refused. Nothing was left behind, and
the one existing mark — a whole verse — was untouched.

So the builds that write the words can ship. Older builds never name the
columns and are unaffected.

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

Builds before A63 fell back to a public channel when a private join was
refused, and stayed there until the room was opened again — and a private
and a public channel with the same name do not hear each other. From A63 no
build joins public: a refused join tries the private one again. Once every
phone is on such a build, turn off public channels in the project's Realtime
settings, so a room's channel can only ever be private. Builds from before 14
September (#14) join public only and will not see anyone on a current build.

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

And what A64 changed, on the same two phones:

- Follow someone and leave both phones alone: the follower's screen stays
  on while they read, and sleeps once they are idle or gone.
- Turn the reader's phone to airplane mode for fifteen seconds and back:
  the follower keeps moving afterwards. Do the same with a second device
  signed in as the follower, opened and closed.
- Follow a reader still on an older Android build: if the page stalls,
  within about thirty seconds it should move again.
- Both phones on the New King James: "LORD" reads LORD in Psalm 3, and the
  copyright line sits under each chapter. One on the New King James, the
  other on the Berean: following lands on the same words.

What's new (A65): update over an older build and open the app — the screen
shows "Staying on the same page" with its four pictures moving. Then You →
What's new: both releases, newest first, each under its day; Done, back and
Esc go back to You, and the next launch does not show it again.

The front of the book (A67, I40), on both phones — compiled by CI, never
yet run on a phone:

- Your face → You: under your name, a ribbon for each room you are in,
  hanging from a hairline, of different lengths, each in your ink there
  (or the accent in a room of two). Under each, the room and "Mark 4", the
  book, or "between books". Touch one: the menu closes on that room.
- Finish a book, or open You on a phone that has one: its ember is under
  your ribbons with who you read it with. Touch it: its record opens inside
  You, with no "Read it again"; touch a verse in it: the book opens there,
  in its own room. Leave a room that has an ember: the ember stays, with no
  name under it.
- At the foot, the colophon: the Wave, the version, "Set in Literata and
  Alegreya Sans.", the credit. On Android, touching the version still
  checks for an update.
- Text: each version shows the verse you are at in its own words; a
  licensed one only once its chapter has come. Move the size: the page
  under it grows, with its verse numbers. The World English in a Gospel,
  red letter on: the words of Jesus turn red in the preview.
- The chosen version, the room you are in, and onboarding's choice are
  marked by a ribbon laid into the top of the tile. In a room of three, the
  ink picker's inks are ribbons, yours the longest.
- Notifications in a room of two: their faces over the switches, "When
  Ruth opens the book", and the sentence the phone will say under "Notes
  left for you". Quiet hours: drag either end of the band; tap the band
  and the nearer end comes there; set both ends to the same time and it
  says "No quiet hours".
- A scroll that starts on the band, or on iPhone on the size, moves the
  page and changes nothing. A drag sideways still moves them.
- Text, opened with no book open on a phone just started: the versions'
  verses fade in under their names, rather than jumping in; with reduce
  motion on, they still fade.
- At the largest text size: the room names under the ribbons and the
  books under the embers wrap rather than ending in "…"; on iPhone the
  three hours under the band do not run into each other.
- Android with a keyboard: Tab reaches each end of the band, which is
  ringed, and the arrows move it a quarter hour.
- With VoiceOver and TalkBack: each end of the band moves a quarter hour
  at a time and says its time; each ribbon says its room, its place and
  your ink there; the chosen version is said as selected.
- iPhone only (I40): the switches, the size and the band are drawn. With
  VoiceOver a switch is still a switch, on or off, and the size moves by
  half a point and says "19 point".
- What's new: update over an older build and open the app — "The front of
  the book", five pictures moving; reduce motion holds each still.

A mark following its words (A60, I37) — compiled on both platforms by CI,
never yet run on a phone:

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

## 7. The New King James's usage reporting, and its proxy

API.Bible asks apps to report each chapter shown (FUMS) with the
`meta.fumsToken` it returns, and to show the edition's copyright. The
copyright line is shown under every licensed chapter (A64); the usage
report is not sent. It sends a device and session identifier to API.Bible,
so it wants a decision, and a reading of the licence terms, before it is
built.

`supabase/functions/bible-proxy` now answers `cache-control: private,
no-store`. Redeploy it for that to apply; the apps no longer rely on it.

