# Ribbon backend

Live project: `ribbon` (`noyccfkaotuvhhaoccck`, us-east-1) in the Aeaura
Supabase org. Apply `migrations/` in filename order — they are timestamped
and each one is idempotent, so re-running the set is safe. The newest,
`20261002120000_ribbon_a_mark_follows_its_words.sql`, was applied on 4
October 2026; see the note on marks below.

What the schema enforces structurally (see the comments in the SQL):

- **No history.** Fuel events live inside a rolling window and are pruned
  hourly by pg_cron; positions and the last-read stamp overwrite in place.
  There is nothing to assemble a consistency report from (§13).
- **No read receipts.** `note_founds` is readable only by the finder; a
  note's author cannot query who found it (§6.3).
- **Paused rooms read, never write.** Insert policies on notes,
  highlights, fuel and voice-note storage check `room_is_paused`;
  selects never do (§2.5, §14.3).
- **Joining is a capability.** Invites accept through
  `accept_invite(token)`; `invite_preview(token)` is deliberately
  anon-callable so the S16 screen can show who is inviting before any
  account exists.
- **The room's live channel is a room's, not the world's.**
  `20260914120000_ribbon_realtime_room_channel.sql` puts the same
  membership rule on `realtime.messages` that every table already has, so
  a private join to `room:<room id>` is refused for a room you are not
  in. Presence is the most intimate signal the product has (§4.2); it is
  not the one thing RLS skips. (Applied on 25 September 2026;
  `docs/still-to-do.md` has the story.)

  **This migration is load-bearing and has to be applied.** Until it is,
  both clients fall back to a *public* channel on their first refused
  join — they keep working, and they say so in the log — which means a
  room's presence and its thinking-of-you taps are readable by anyone
  holding the publishable key that ships inside the app. Apply it, then
  restart the app: the private join is retried every time a room is
  opened, so nothing else needs doing.

- **Push is a fact in the database first (S19, S24).**
  `20260924010000_ribbon_push.sql` adds the phones (`push_devices`: a
  token, the per-room switches, quiet hours and time zone the phone holds),
  the Live Activities running on them (`live_activities`), and an outbox.
  Triggers on notes, cards and readings — and the phone's own calls,
  `i_am_reading`, `i_have_left` and `think_of` — write outbox rows, and
  every insert wakes the `push` function over pg_net. The function asks
  `push_claim()` who should hear what and delivers it through APNs or FCM;
  it takes nothing from its caller, so it is deployed with JWT
  verification off. Presence-like rows are gone five minutes after they
  are sent and the rest after a day: nothing here becomes a history (§13).

  It delivers once its secrets are set (Edge Functions → Secrets, or
  `supabase secrets set ... --project-ref noyccfkaotuvhhaoccck`):

  | Secret | What it is |
  | --- | --- |
  | `APNS_KEY_ID` | The APNs auth key's id (Keys, in the Apple developer portal) |
  | `APNS_TEAM_ID` | The team the key belongs to |
  | `APNS_PRIVATE_KEY` | The `.p8` file's contents, whole |
  | `APNS_TOPIC` | Optional; defaults to `bible.ribbon.app` |
  | `FCM_SERVICE_ACCOUNT` | The Firebase service account JSON, whole |

  `GET /functions/v1/push` answers `{"ios": …, "android": …}` — whether
  each is configured. The apps ask it, and a phone keeps posting its own
  notifications until the server can say them for it, so nothing is lost
  before the keys exist and nothing is said twice after.

- **A mark follows its words** (docs/deviations.md A60).
  `20261002120000_ribbon_a_mark_follows_its_words.sql` adds three nullable
  columns to `highlights`: `start_words` and `end_words`, the positions of
  the Hebrew, Aramaic or Greek words a phrase mark covers at each end, and
  `words_source`, the numbering they count in. A mark on whole verses
  writes none of them. Its checks say positions need their source, are
  whole numbers at least zero, come as one set inside one verse, and that
  the source is a short key. No policy changes. `rooms.translation` and
  `readings.translation` stay although nothing current reads them for
  display, because shipped clients still write them. Applied to the live
  project on 4 October 2026, and rehearsed there in a rolled-back
  transaction (docs/still-to-do.md §1).

  **Apply it before the builds that write the columns ship.** PostgREST
  refuses a write naming a column the table does not have, so until it is
  applied every phrase mark from those builds fails to push
  (`docs/still-to-do.md`, item 1).

Auth is Auth0 where the build is configured for it (see `auth0/`), with
Supabase's own emailed code underneath; there are no passwords either way,
and every surface that needs an account goes through the one sign-in
control so a joiner's account is the same kind of account as everyone
else's in the room. The clients (`SupabaseClient.swift`,
`SupabaseClient.kt`) speak to auth, PostgREST, and storage; `RoomChannel`
holds one authenticated Realtime channel per room, carrying the presence
roster, thinking-of-you, and the contentless nudge that tells the other
phones to pull.

`email-templates/otp.html` is the on-brand sign-in email — paste it into
the dashboard at Authentication → Emails → Magic Link, the template
Supabase sends for `signInWithOtp`. See its header comment for the
reasoning.
