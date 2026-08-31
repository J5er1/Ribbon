# Ribbon backend

Live project: `ribbon` (`noyccfkaotuvhhaoccck`, us-east-1) in the Aeaura
Supabase org. Both migrations in `migrations/` are applied.

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

Auth is email OTP (no passwords). The iOS client (`SupabaseClient.swift`)
speaks to auth, PostgREST, and storage; Realtime presence channels carry
the live presence roster and thinking-of-you.
