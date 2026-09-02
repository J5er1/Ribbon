-- Per-address cooldown for sign-in emails, enforced server-side.
--
-- Why this table exists: Supabase's built-in cap of two auth emails an hour
-- is a guard on *their* shared mail relay, not on us. The moment the
-- send-email hook (supabase/functions/send-email) takes delivery over, that
-- cap stops applying and the project's own limit is whatever config.toml
-- says. GoTrue's limit is also per *project*, not per recipient: one
-- address being hammered burns the allowance for everyone. So the real
-- protection — you cannot bomb one person's inbox — has to live here.
--
-- Two limits, both per address, both enforced in this function:
--
--   * A minute between codes. GoTrue has its own version of this
--     (auth.email.max_frequency), and it runs first, before the hook is
--     ever called. This one is not a duplicate of it but the load-bearing
--     copy: max_frequency is one dashboard toggle away from being gone,
--     and if it went, an hourly ceiling alone would still permit six
--     emails in six seconds. Belt and braces, where the braces are ours.
--
--   * Six an hour. The ceiling. Nothing above this exists per-address
--     anywhere else in the stack.
--
-- The hook calls note_auth_email_send() before it sends anything. Deno
-- functions are stateless and horizontally scaled, so counting in memory
-- would count nothing; the window has to be shared, durable, and atomic.
--
-- What is stored: a digest, never an address. The hook HMACs the
-- normalized address with a secret it holds and passes only the hex digest,
-- so this table cannot be read back into a list of who uses Ribbon. It is
-- an index of opaque keys with counters. (auth.users holds the addresses in
-- the clear and always will — this is not pretending otherwise. It means
-- that a leak of *this* table, through a stray grant or a backup, hands
-- over nothing on its own.)

create schema if not exists auth_guard;

-- Not in PostgREST's exposed schemas, so there is no REST route to any of
-- this. Belt and braces on top of that: no usage for the API roles.
revoke all on schema auth_guard from anon, authenticated, public;
grant usage on schema auth_guard to service_role;

create table if not exists auth_guard.email_sends (
  recipient_hash text primary key,
  window_started_at timestamptz not null default now(),
  sent_in_window int not null default 0,
  -- Null until a send is actually taken, so that staking a fresh row does
  -- not read as "a code just went out" and refuse the first one.
  last_sent_at timestamptz
);

-- RLS with no policies at all: the table is service-role only, and the
-- service role bypasses RLS. Anything else that ever reaches it reads zero
-- rows rather than the whole table.
alter table auth_guard.email_sends enable row level security;
revoke all on table auth_guard.email_sends from anon, authenticated, public;

-- Pruning: same posture as fuel_events (§13, "no history"). A window that
-- closed a day ago is not evidence of anything and should not be kept.
create index if not exists email_sends_last_sent_at
  on auth_guard.email_sends (last_sent_at);

do $$
begin
  create extension if not exists pg_cron;
  perform cron.schedule(
    'prune-email-sends',
    '43 * * * *',
    $job$
      delete from auth_guard.email_sends
      where coalesce(last_sent_at, window_started_at) < now() - interval '24 hours'
    $job$);
exception when others then
  -- pg_cron unavailable in this environment; the rows are small and the
  -- window logic is correct without pruning — this is hygiene, not
  -- correctness.
  raise notice 'pg_cron not scheduled: %', sqlerrm;
end;
$$;

-- Take one send against a recipient's cooldown, or refuse it.
--
-- Returns {allowed, reason, sent_in_window, remaining, retry_after_seconds}.
--
-- A refusal changes nothing — not the window, not last_sent_at. Hammering
-- the button cannot push the next legitimate send further away, which is
-- the bug that makes naive "reset the timer on every attempt" throttles
-- hostile to the person holding the phone rather than to the attacker. It
-- also means GoTrue's automatic retries of a 429 (three, two seconds apart)
-- re-read the same refusal instead of digging the hole deeper.
--
-- Atomicity: the insert stakes the row, the SELECT ... FOR UPDATE holds it
-- for the rest of the transaction, so two hook invocations for the same
-- address serialize instead of both reading "one sent" and both sending.
--
-- It lives in public, alone, while the table it guards does not: PostgREST
-- only routes to exposed schemas, and the hook has to be able to call this
-- over REST with the service-role key. So the callable surface is this one
-- function, execute granted to nobody else, and the counters themselves
-- stay somewhere with no route to them at all.
--
-- The parameters carry a p_ prefix so that none of them can collide with a
-- column of the table they act on: plpgsql resolves a bare name against
-- both, and `on conflict (recipient_hash)` against a parameter of the same
-- name is ambiguous at runtime rather than at creation — which is to say it
-- fails in production, not here. The hook posts these names as its JSON
-- keys.
create or replace function public.note_auth_email_send(
  p_recipient_hash text,
  p_window_seconds int default 3600,
  p_max_in_window int default 6,
  p_min_interval_seconds int default 60
)
returns jsonb
language plpgsql volatile security definer
set search_path = auth_guard, pg_temp
as $$
declare
  window_start timestamptz;
  prior_count int;
  last_sent timestamptz;
  window_ends timestamptz;
  next_allowed timestamptz;
begin
  if p_recipient_hash is null or p_recipient_hash = '' then
    raise exception 'recipient_hash required';
  end if;
  if p_window_seconds <= 0 or p_max_in_window <= 0 or p_min_interval_seconds < 0 then
    raise exception 'window and ceiling must be positive';
  end if;

  insert into auth_guard.email_sends (recipient_hash, window_started_at, sent_in_window, last_sent_at)
  values (p_recipient_hash, now(), 0, null)
  on conflict (recipient_hash) do nothing;

  select e.window_started_at, e.sent_in_window, e.last_sent_at
    into window_start, prior_count, last_sent
    from auth_guard.email_sends e
   where e.recipient_hash = p_recipient_hash
     for update;

  -- The window has closed; this send opens a fresh one. (last_sent stands:
  -- anything older than the window is necessarily older than the interval
  -- too, so the check below passes on its own.)
  if window_start < now() - make_interval(secs => p_window_seconds) then
    window_start := now();
    prior_count := 0;
  end if;

  window_ends := window_start + make_interval(secs => p_window_seconds);

  -- The minute between codes.
  next_allowed := last_sent + make_interval(secs => p_min_interval_seconds);
  if last_sent is not null and next_allowed > now() then
    return jsonb_build_object(
      'allowed', false,
      'reason', 'too_soon',
      'sent_in_window', prior_count,
      'remaining', greatest(0, p_max_in_window - prior_count),
      'retry_after_seconds', greatest(1, ceil(extract(epoch from next_allowed - now()))::int));
  end if;

  -- The hourly ceiling.
  if prior_count >= p_max_in_window then
    return jsonb_build_object(
      'allowed', false,
      'reason', 'hourly_limit',
      'sent_in_window', prior_count,
      'remaining', 0,
      'retry_after_seconds', greatest(1, ceil(extract(epoch from window_ends - now()))::int));
  end if;

  update auth_guard.email_sends e
     set window_started_at = window_start,
         sent_in_window = prior_count + 1,
         last_sent_at = now()
   where e.recipient_hash = p_recipient_hash;

  return jsonb_build_object(
    'allowed', true,
    'reason', 'ok',
    'sent_in_window', prior_count + 1,
    'remaining', p_max_in_window - (prior_count + 1),
    'retry_after_seconds', 0);
end;
$$;

-- Only the hook may call this, and the hook holds the service-role key.
-- Without these two lines PostgREST would happily let anon reset anyone's
-- cooldown, which would undo the whole file. (SECURITY DEFINER with a
-- pinned search_path, per the advisor hygiene the rest of the schema
-- already follows — see 20260901050000.)
revoke all on function public.note_auth_email_send(text, int, int, int) from public, anon, authenticated;
grant execute on function public.note_auth_email_send(text, int, int, int) to service_role;
