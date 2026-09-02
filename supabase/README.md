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

## Sign-in mail, and why it doesn't go through Supabase

Supabase's stock limit is **two auth emails an hour**, and it will only
deliver to addresses on the project's team. Both are guards on *their*
shared relay rather than policy we chose, and both lift the moment we send
our own mail. `functions/send-email` is that: the `send_email` auth hook,
which takes delivery over entirely (with a hook enabled, SMTP is not used).

The hook is not just a way around the limit — it is what replaces it. The
limits it lifts were doing a real job, and everything in `[auth.rate_limit]`
is **project-wide**, so on its own a raised ceiling would let one address be
buried using the whole project's allowance. So the cooldown that matters is
per-address and lives in the backend, in `note_auth_email_send()`:

| Limit | Where | What it stops |
| --- | --- | --- |
| A minute between codes | `auth.email.max_frequency`, *and* the hook | The double-tap, the tight loop |
| Six an hour, per address | `note_auth_email_send()` | Burying one person's inbox |
| 100 an hour, project-wide | `[auth.rate_limit].email_sent` | A runaway loop, whoever it is aimed at |

The minute is enforced twice on purpose: GoTrue's copy runs first and costs
nothing, but it is one dashboard toggle away from being gone, and an hourly
ceiling alone would still permit six emails in six seconds. The app keeps
the same window (`SignInSendWindow` in RibbonCore) purely so the resend
button can say why it is waiting — it is never the enforcement.

A refusal deliberately changes nothing, so hammering cannot push the next
legitimate code further out, and GoTrue's automatic retries of a 429 re-read
the same answer rather than digging the hole deeper.

The counters are keyed by an HMAC of the address, never the address, so the
table cannot be read back into a list of who uses Ribbon.

### Deploying it

```sh
supabase functions deploy send-email --no-verify-jwt   # the hook fires before any JWT exists
supabase secrets set --env-file supabase/functions/.env
```

`supabase/functions/.env` (never committed):

```ini
SEND_EMAIL_HOOK_SECRET="v1,whsec_<base64>"   # required — dashboard → Authentication → Hooks
RESEND_API_KEY=...                           # required
SEND_EMAIL_FROM="Ribbon <hello@example.com>" # required
EMAIL_THROTTLE_PEPPER=<random>               # recommended, see index.ts
EMAIL_MAX_PER_HOUR=6                         # optional
EMAIL_MIN_INTERVAL_SECONDS=60                # optional
```

`SEND_EMAIL_HOOK_SECRET` is the whole of the caller's authentication —
`verify_jwt` is off because the hook runs before a JWT exists, so the
Standard Webhooks signature is what proves the request came from GoTrue.
The function refuses to start without it rather than degrading, because
unverified this endpoint is a mail cannon.

Then point the hook at the function (dashboard → Authentication → Hooks, or
`[auth.hook.send_email]` in `config.toml`) and raise the rate limits under
Authentication → Rate Limits to match `config.toml`. Note that
`config.toml` is deliberately partial — see its header before running
`supabase config push`.

The mail itself is rendered by `functions/send-email/_templates/`. That is
the same design as `email-templates/otp.html`, which is what the dashboard
would render **if the hook were switched off** — two copies of one design,
only one of them live at a time.
