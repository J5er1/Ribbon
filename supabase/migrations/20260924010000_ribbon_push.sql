-- Ribbon — push (S19), and the Live Activity it carries (S24).
--
-- Until this migration every notification Ribbon could give was the phone's
-- own work: a background pull, fifteen minutes apart at the very best
-- (RoomWatch on both platforms), and for the two things with no row behind
-- them — "Ruth is reading Mark" and thinking of you — nothing at all once
-- the app was closed. This is the server half: a phone that is not open
-- hears about a note when the note is left.
--
-- The shape, and why it is this shape:
--
-- **Every push is a fact in the database first.** A trigger on notes, cards
-- and readings — or a call from the phone for the two live things — writes a
-- row into `push_outbox`, and that insert wakes the `push` function over
-- pg_net. The function asks the database what to send (`push_claim`), and
-- the database answers from its own tables, not from anything the caller
-- said. So the function needs no secret to be safe to call: a stranger who
-- finds its URL can only make it deliver what was going to be delivered
-- anyway, once, because claiming a row is what consumes it.
--
-- **The switches are the phone's, as the phone holds them.** Each device
-- registers its per-room switches, its quiet hours and its time zone with
-- its token (`register_push_device`). S19's switches have always lived on
-- the device, and a push that obeyed some other copy of them would be the
-- server overruling the setting a person just changed. Quiet hours are
-- judged here, in the device's own zone, because a phone that is asleep
-- cannot judge anything.
--
-- **Nothing becomes history.** §13 lists what may persist behind presence —
-- the rolling fuel window, one last-read stamp per person per room, quiet
-- days — and says nothing older than that is kept anywhere. So: "reading",
-- "left" and thinking-of-you rows go five minutes after they are sent;
-- note, card and book rows (which point at rows that exist anyway) go after
-- a day; "Ruth is reading Mark" is judged against `last_read`, the one
-- stamp §13 already allows, overwritten in place. Nobody can assemble from
-- these tables when anyone read, or who touched whom.
--
-- **No badge, ever.** Nothing here sends one, and there is no column that
-- could count one.

create extension if not exists pg_net with schema extensions;

-- ------------------------------------------------------------ the phones

-- One row per install. The token is the key because it is what identifies
-- the install: a second person signing in on the same phone moves the row
-- to them rather than leaving the first person's notes arriving on it.
create table if not exists public.push_devices (
  token text primary key,
  person_id uuid not null references public.profiles (id) on delete cascade,
  platform text not null check (platform in ('ios', 'android')),
  -- APNs has two worlds: a build run from Xcode talks to the sandbox, and
  -- TestFlight and the store to production. Meaningless on Android.
  environment text not null default 'production'
    check (environment in ('sandbox', 'production')),
  -- iOS only: the token that lets the server start the Live Activity (S24)
  -- on this phone. Null when Live Activities are off in Settings — the
  -- phone then gets "Ruth is reading Mark" as an ordinary notification.
  live_start_token text,
  time_zone text not null default 'UTC',
  -- Minutes after local midnight. Equal ends mean no quiet hours at all
  -- rather than all of them, the same rule both apps use.
  quiet_start smallint not null default 1320 check (quiet_start between 0 and 1439),
  quiet_end smallint not null default 360 check (quiet_end between 0 and 1439),
  -- The four switches per room (S19), keyed by room id:
  --   {"<room>": {"notesLeft": true, "cardsOpen": true,
  --               "inTheBook": false, "thinkingOfYou": true}}
  -- A room missing from the map takes S19's defaults.
  rooms jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create index if not exists push_devices_person on public.push_devices (person_id);

-- No policies: nobody reads a token, not even their own. The phone writes
-- through the functions below, and the sender reads with the service role.
alter table public.push_devices enable row level security;

-- A Live Activity running on one phone about one reader (S24). The row is
-- written when the server starts the activity and the activity's own token
-- arrives when the phone wakes to hand it over. It exists exactly as long as
-- the activity does: "left" ends the activity and deletes the row, and
-- twelve hours — eight running, four ended, the most iOS ever shows one — is
-- the outer bound for a row nothing ever came back for.
create table if not exists public.live_activities (
  device_token text not null references public.push_devices (token) on delete cascade,
  room_id uuid not null references public.rooms (id) on delete cascade,
  reader_id uuid not null references public.profiles (id) on delete cascade,
  activity_token text,
  started_at timestamptz not null default now(),
  primary key (device_token, room_id, reader_id)
);

create index if not exists live_activities_reader on public.live_activities (room_id, reader_id);

alter table public.live_activities enable row level security;

-- ------------------------------------------------------------ the outbox

create table if not exists public.push_outbox (
  id bigint generated always as identity primary key,
  kind text not null
    check (kind in ('note', 'cards', 'finished', 'reading', 'left', 'thinking')),
  room_id uuid not null references public.rooms (id) on delete cascade,
  reading_id uuid references public.readings (id) on delete cascade,
  -- The note or the card, for the two kinds that are about one.
  ref_id uuid,
  -- Whoever did the thing. Never told about their own act.
  actor_id uuid references public.profiles (id) on delete cascade,
  -- Thinking of you: the one person it is for.
  target_id uuid references public.profiles (id) on delete cascade,
  -- "reading": an arrival, rather than the heartbeat of someone already
  -- reading. Only an arrival says anything aloud.
  fresh boolean not null default true,
  created_at timestamptz not null default now(),
  claimed_at timestamptz
);

create index if not exists push_outbox_unclaimed on public.push_outbox (id) where claimed_at is null;
create index if not exists push_outbox_actor on public.push_outbox (room_id, actor_id, kind, created_at);

alter table public.push_outbox enable row level security;

-- The sender's address. This project's own function; a project restored
-- somewhere else changes it here and nowhere else.
create or replace function public.push_endpoint()
returns text
language sql immutable
set search_path = ''
as $$
  select 'https://noyccfkaotuvhhaoccck.supabase.co/functions/v1/push'::text;
$$;

-- Wake the sender. Once per statement rather than per row: a phone that
-- comes back online and pushes six notes wakes it once, and the sender
-- takes all six in one claim. pg_net queues the request inside this
-- transaction and sends it after commit, so a write that rolls back wakes
-- nothing.
create or replace function public.push_wake()
returns trigger
language plpgsql security definer
set search_path = public, extensions
as $$
begin
  perform net.http_post(
    url := public.push_endpoint(),
    body := '{}'::jsonb,
    headers := '{"Content-Type": "application/json"}'::jsonb,
    timeout_milliseconds := 10000);
  return null;
exception when others then
  -- A notification is never worth a failed write. The note is saved
  -- whether or not the sender could be woken; the next wake takes it.
  return null;
end;
$$;

drop trigger if exists push_outbox_wake on public.push_outbox;
create trigger push_outbox_wake
  after insert on public.push_outbox
  for each statement execute function public.push_wake();

-- ------------------------------------------------- facts that become pushes

-- A note left (§10.3). The first insert only: a phone re-sending a note it
-- already sent goes down the upsert's UPDATE path and says nothing twice.
create or replace function public.push_note_left()
returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  insert into push_outbox (kind, room_id, reading_id, ref_id, actor_id)
  select 'note', r.room_id, new.reading_id, new.id, new.author_id
  from readings r where r.id = new.reading_id;
  return null;
end;
$$;

drop trigger if exists push_note_left on public.notes;
create trigger push_note_left
  after insert on public.notes
  for each row execute function public.push_note_left();

-- The cards open (§4.6): the moment the last answer is in, on whichever
-- phone noticed first. Every later phone pushes the same card as open and
-- finds it open already.
create or replace function public.push_cards_open()
returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  if new.state <> 'open' then
    return null;
  end if;
  -- `old` is only read on an update: an insert has none to compare.
  if tg_op = 'UPDATE' then
    if old.state <> 'sealed' then
      return null;
    end if;
  end if;
  insert into push_outbox (kind, room_id, reading_id, ref_id, actor_id)
  select 'cards', r.room_id, new.reading_id, new.id, public.current_user_id()
  from readings r where r.id = new.reading_id;
  return null;
end;
$$;

drop trigger if exists push_cards_open on public.cards;
create trigger push_cards_open
  after insert or update of state on public.cards
  for each row execute function public.push_cards_open();

-- A book finished (§6.5) — "once per book, to everyone in the room
-- including whoever wasn't there when it happened". Everyone but the phone
-- that did it, which is showing the ember already.
create or replace function public.push_book_finished()
returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  if old.finished_at is null and new.finished_at is not null then
    insert into push_outbox (kind, room_id, reading_id, actor_id)
    values ('finished', new.room_id, new.id, public.current_user_id());
  end if;
  return null;
end;
$$;

drop trigger if exists push_book_finished on public.readings;
create trigger push_book_finished
  after update of finished_at on public.readings
  for each row execute function public.push_book_finished();

-- ----------------------------------------------------- what the phone says

-- Its token, switches, quiet hours and zone — all of them, every time,
-- because they are small and a partial update is a second way to be wrong.
create or replace function public.register_push_device(
  device_token text,
  device_platform text,
  apns_environment text,
  zone text,
  quiet_from int,
  quiet_until int,
  room_prefs jsonb,
  live_start text default null
)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  uid uuid := public.current_user_id();
  checked_zone text;
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if device_token is null or device_token !~ '^[A-Za-z0-9_:-]{32,4096}$' then
    raise exception 'bad_token';
  end if;
  if live_start is not null and live_start !~ '^[A-Za-z0-9]{32,512}$' then
    live_start := null;
  end if;
  -- An unknown zone would make every quiet-hours check throw; a known one
  -- is the only kind worth keeping, and UTC is the honest fallback.
  select name into checked_zone from pg_timezone_names where name = zone limit 1;
  insert into push_devices (
    token, person_id, platform, environment, live_start_token,
    time_zone, quiet_start, quiet_end, rooms, updated_at)
  values (
    device_token, uid,
    case when device_platform = 'ios' then 'ios' else 'android' end,
    case when apns_environment = 'sandbox' then 'sandbox' else 'production' end,
    live_start,
    coalesce(checked_zone, 'UTC'),
    greatest(0, least(1439, coalesce(quiet_from, 1320))),
    greatest(0, least(1439, coalesce(quiet_until, 360))),
    case when jsonb_typeof(room_prefs) = 'object' then room_prefs else '{}'::jsonb end,
    now())
  on conflict (token) do update set
    person_id = excluded.person_id,
    platform = excluded.platform,
    environment = excluded.environment,
    live_start_token = excluded.live_start_token,
    time_zone = excluded.time_zone,
    quiet_start = excluded.quiet_start,
    quiet_end = excluded.quiet_end,
    rooms = excluded.rooms,
    updated_at = now();
end;
$$;

-- Signing out takes the phone off the list. Only your own phone.
create or replace function public.forget_push_device(device_token text)
returns void
language sql security definer
set search_path = public
as $$
  delete from push_devices
  where token = device_token and person_id = public.current_user_id();
$$;

-- A Live Activity the server started has woken its phone, and the phone
-- hands back the token that can update and end it.
create or replace function public.register_live_activity(
  device_token text, room uuid, reader uuid, activity_token text)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  uid uuid := public.current_user_id();
begin
  if uid is null or activity_token is null or activity_token !~ '^[A-Za-z0-9]{32,512}$' then
    return;
  end if;
  if not exists (select 1 from push_devices where token = device_token and person_id = uid) then
    return;
  end if;
  if not public.is_member(room) then
    return;
  end if;
  insert into live_activities (device_token, room_id, reader_id, activity_token)
  values (device_token, room, reader, register_live_activity.activity_token)
  on conflict (device_token, room_id, reader_id)
  do update set activity_token = excluded.activity_token;
end;
$$;

-- The phone ended one itself (the person swiped it away, or the app found
-- it stale), so the server stops trying to update it.
create or replace function public.forget_live_activity(device_token text, room uuid, reader uuid)
returns void
language sql security definer
set search_path = public
as $$
  delete from live_activities a
  using push_devices d
  where a.device_token = forget_live_activity.device_token
    and a.room_id = room and a.reader_id = reader
    and d.token = a.device_token and d.person_id = public.current_user_id();
$$;

-- "When they open the book" (S19), and the Live Activity (S24). Called when
-- the book opens — never while reading quietly, which is the whole of what
-- reading quietly means — and again every ten minutes while it stays open,
-- which keeps the Live Activity from going stale. `last_read` is the one
-- stamp per person per room that §13 keeps; an arrival is a stamp older than
-- half an hour, and only an arrival is said aloud.
create or replace function public.i_am_reading(room uuid, reading uuid)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  uid uuid := public.current_user_id();
  was timestamptz;
begin
  if uid is null or not public.is_member(room) then
    return;
  end if;
  if not exists (
    select 1 from readings
    where id = reading and room_id = room and finished_at is null
  ) then
    return;
  end if;
  select at into was from last_read where room_id = room and person_id = uid;
  insert into last_read (room_id, person_id, at) values (room, uid, now())
  on conflict (room_id, person_id) do update set at = now();
  -- A double call (the screen appearing twice) is one arrival.
  if was is not null and was > now() - interval '20 seconds' then
    return;
  end if;
  insert into push_outbox (kind, room_id, reading_id, actor_id, fresh)
  values ('reading', room, reading, uid,
          was is null or was < now() - interval '30 minutes');
end;
$$;

-- The book closed. Ends the Live Activity on every phone showing it.
create or replace function public.i_have_left(room uuid)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  uid uuid := public.current_user_id();
begin
  if uid is null or not public.is_member(room) then
    return;
  end if;
  insert into push_outbox (kind, room_id, actor_id) values ('left', room, uid);
end;
$$;

-- Thinking of you (§4.3), for a phone that is not in the room. The socket
-- still carries the touch to a phone that is; this carries the name to one
-- that isn't. Repeats inside three minutes are one touch, as on the socket.
create or replace function public.think_of(room uuid, person uuid)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  uid uuid := public.current_user_id();
begin
  if uid is null or person is null or person = uid or not public.is_member(room) then
    return;
  end if;
  if not exists (select 1 from memberships where room_id = room and person_id = person) then
    return;
  end if;
  if exists (
    select 1 from push_outbox
    where kind = 'thinking' and room_id = room and actor_id = uid and target_id = person
      and created_at > now() - interval '3 minutes'
  ) then
    return;
  end if;
  insert into push_outbox (kind, room_id, actor_id, target_id)
  values ('thinking', room, uid, person);
end;
$$;

-- ------------------------------------------------------------ the sender

-- Quiet hours (S19) in the device's own zone, wrap-aware: 22:00–06:00 spans
-- midnight.
create or replace function public.push_is_quiet(
  zone text, quiet_start int, quiet_end int, moment timestamptz default now())
returns boolean
language sql stable
set search_path = ''
as $$
  select case
    when quiet_start = quiet_end then false
    else (
      select case
        when quiet_start < quiet_end then m >= quiet_start and m < quiet_end
        else m >= quiet_start or m < quiet_end
      end
      from (
        select extract(hour from (moment at time zone zone))::int * 60
             + extract(minute from (moment at time zone zone))::int as m
      ) local_minute
    )
  end;
$$;

-- The phones in a room that want a kind of thing, right now. `switch` is
-- the key in the device's room map; null means the thing has no switch
-- (a finished book). Quiet hours hold for everything they are asked to.
create or replace function public.push_audience(
  room uuid, excluding uuid, switch text, default_on boolean, honour_quiet boolean)
returns setof public.push_devices
language sql stable security definer
set search_path = public
as $$
  select d.*
  from push_devices d
  join memberships m on m.person_id = d.person_id and m.room_id = room
  where d.person_id is distinct from excluding
    and (switch is null
         or coalesce((d.rooms -> room::text ->> switch)::boolean, default_on))
    and (not honour_quiet
         or not public.push_is_quiet(d.time_zone, d.quiet_start, d.quiet_end));
$$;

-- Take what is waiting, and say exactly who should hear what. The function
-- only formats and delivers; every decision about recipients, switches,
-- quiet hours and running Live Activities is made here, against the tables.
create or replace function public.push_claim()
returns jsonb
language plpgsql security definer
set search_path = public
as $$
declare
  o push_outbox%rowtype;
  messages jsonb := '[]'::jsonb;
  message jsonb;
  devices jsonb;
  actor text;
  book text;
begin
  -- The rolling part. Nothing here outlives its purpose.
  delete from push_outbox
  where (kind in ('reading', 'left', 'thinking') and created_at < now() - interval '5 minutes')
     or created_at < now() - interval '1 day';
  delete from live_activities where started_at < now() - interval '12 hours';

  for o in
    update push_outbox set claimed_at = now()
    where id in (
      select id from push_outbox
      where claimed_at is null
      order by id
      limit 100
      for update skip locked)
    returning *
  loop
    select name into actor from profiles where id = o.actor_id;
    select book_id into book from readings where id = o.reading_id;
    message := jsonb_build_object(
      'kind', o.kind, 'room', o.room_id, 'reading', o.reading_id,
      'actor', o.actor_id, 'actorName', actor, 'book', book, 'fresh', o.fresh);
    devices := null;

    if o.kind = 'note' then
      -- Taken back before it was sent: nothing to say.
      continue when not exists (select 1 from notes where id = o.ref_id);
      select message || jsonb_build_object(
               'book', n.book_id, 'chapter', n.chapter, 'verse', n.verse,
               -- A second note from the same person inside half an hour
               -- names only the person (§10.3): listing them would be a
               -- count in prose.
               'several', exists (
                 select 1 from push_outbox p
                 where p.kind = 'note' and p.room_id = o.room_id
                   and p.actor_id = o.actor_id and p.id <> o.id
                   and p.created_at > now() - interval '30 minutes'))
        into message
        from notes n where n.id = o.ref_id;
      select jsonb_agg(jsonb_build_object(
               'token', a.token, 'platform', a.platform, 'environment', a.environment))
        into devices
        from push_audience(o.room_id, o.actor_id, 'notesLeft', true, true) a
        where not exists (
          select 1 from note_founds f where f.note_id = o.ref_id and f.person_id = a.person_id);

    elsif o.kind = 'cards' then
      continue when not exists (select 1 from cards where id = o.ref_id and state = 'open');
      select message || jsonb_build_object('chapter', c.chapter)
        into message from cards c where c.id = o.ref_id;
      select jsonb_agg(jsonb_build_object(
               'token', a.token, 'platform', a.platform, 'environment', a.environment))
        into devices
        from push_audience(o.room_id, o.actor_id, 'cardsOpen', true, true) a;

    elsif o.kind = 'finished' then
      select jsonb_agg(jsonb_build_object(
               'token', a.token, 'platform', a.platform, 'environment', a.environment))
        into devices
        from push_audience(o.room_id, o.actor_id, null, true, true) a;

    elsif o.kind = 'reading' then
      continue when not exists (
        select 1 from readings where id = o.reading_id and finished_at is null);
      -- A phone already showing this reader's Live Activity gets it kept
      -- current; one that isn't gets it started, and the row that says so
      -- is written now, so a heartbeat ten minutes later updates rather
      -- than starting a second one.
      with audience as (
        select a.*, l.activity_token, (l.device_token is not null) as running
        from push_audience(o.room_id, o.actor_id, 'inTheBook', false, true) a
        left join live_activities l
          on l.device_token = a.token and l.room_id = o.room_id and l.reader_id = o.actor_id
      ), started as (
        insert into live_activities (device_token, room_id, reader_id)
        select token, o.room_id, o.actor_id from audience
        where platform = 'ios' and live_start_token is not null and not running
        on conflict do nothing
        returning device_token
      )
      select jsonb_agg(jsonb_build_object(
               'token', token, 'platform', platform, 'environment', environment,
               'liveStart', live_start_token, 'activity', activity_token,
               'running', running))
        into devices
        from audience;

    elsif o.kind = 'left' then
      -- Every phone showing this reader's Live Activity, whatever its
      -- switches say now — ending a thing is never an interruption — and
      -- every Android phone whose "reading" notification stands in for one.
      with ended as (
        delete from live_activities l
        using push_devices d
        where l.room_id = o.room_id and l.reader_id = o.actor_id
          and d.token = l.device_token
        returning d.token, d.platform, d.environment, l.activity_token
      )
      select jsonb_agg(x) into devices from (
        select jsonb_build_object(
                 'token', token, 'platform', platform, 'environment', environment,
                 'activity', activity_token) as x
        from ended where activity_token is not null
        union all
        select jsonb_build_object('token', a.token, 'platform', a.platform)
        from push_audience(o.room_id, o.actor_id, 'inTheBook', false, false) a
        where a.platform = 'android'
      ) everyone;

    elsif o.kind = 'thinking' then
      continue when not exists (
        select 1 from memberships where room_id = o.room_id and person_id = o.target_id);
      select jsonb_agg(jsonb_build_object(
               'token', a.token, 'platform', a.platform, 'environment', a.environment))
        into devices
        from push_audience(o.room_id, o.actor_id, 'thinkingOfYou', true, true) a
        where a.person_id = o.target_id;
    end if;

    if devices is not null and jsonb_array_length(devices) > 0 then
      messages := messages || jsonb_build_array(message || jsonb_build_object('devices', devices));
    end if;
  end loop;

  return messages;
end;
$$;

-- What APNs and FCM said no to. A token they call dead is dead: the phone
-- registers a fresh one the next time it opens.
create or replace function public.push_forget(
  dead_devices text[], dead_live_starts text[], dead_activities text[])
returns void
language sql security definer
set search_path = public
as $$
  delete from push_devices where token = any(coalesce(dead_devices, '{}'));
  update push_devices set live_start_token = null
  where live_start_token = any(coalesce(dead_live_starts, '{}'));
  delete from live_activities where activity_token = any(coalesce(dead_activities, '{}'));
$$;

-- ------------------------------------------------------------ who may call

-- Supabase grants every new function in public to anon and authenticated by
-- default. The phone's calls are for signed-in people; the sender's are for
-- the service role alone; the triggers' are for nobody.
revoke execute on function public.push_endpoint() from public, anon, authenticated;
revoke execute on function public.push_wake() from public, anon, authenticated;
revoke execute on function public.push_note_left() from public, anon, authenticated;
revoke execute on function public.push_cards_open() from public, anon, authenticated;
revoke execute on function public.push_book_finished() from public, anon, authenticated;
revoke execute on function public.push_is_quiet(text, int, int, timestamptz) from public, anon, authenticated;
revoke execute on function public.push_audience(uuid, uuid, text, boolean, boolean) from public, anon, authenticated;
revoke execute on function public.push_claim() from public, anon, authenticated;
revoke execute on function public.push_forget(text[], text[], text[]) from public, anon, authenticated;
grant execute on function public.push_claim() to service_role;
grant execute on function public.push_forget(text[], text[], text[]) to service_role;

revoke execute on function public.register_push_device(text, text, text, text, int, int, jsonb, text) from public, anon;
revoke execute on function public.forget_push_device(text) from public, anon;
revoke execute on function public.register_live_activity(text, uuid, uuid, text) from public, anon;
revoke execute on function public.forget_live_activity(text, uuid, uuid) from public, anon;
revoke execute on function public.i_am_reading(uuid, uuid) from public, anon;
revoke execute on function public.i_have_left(uuid) from public, anon;
revoke execute on function public.think_of(uuid, uuid) from public, anon;
grant execute on function public.register_push_device(text, text, text, text, int, int, jsonb, text) to authenticated;
grant execute on function public.forget_push_device(text) to authenticated;
grant execute on function public.register_live_activity(text, uuid, uuid, text) to authenticated;
grant execute on function public.forget_live_activity(text, uuid, uuid) to authenticated;
grant execute on function public.i_am_reading(uuid, uuid) to authenticated;
grant execute on function public.i_have_left(uuid) to authenticated;
grant execute on function public.think_of(uuid, uuid) to authenticated;

revoke all on public.push_devices from anon, authenticated;
revoke all on public.live_activities from anon, authenticated;
revoke all on public.push_outbox from anon, authenticated;
