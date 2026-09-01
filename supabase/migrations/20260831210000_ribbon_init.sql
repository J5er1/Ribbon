-- Ribbon — initial schema.
--
-- The object model from the build book §03, with the privacy posture of
-- §13 enforced structurally:
--   * No table stores reading duration, session counts, or any per-person
--     reading record older than the rolling fuel window. If the data
--     doesn't exist, nobody can be asked for it and no future product
--     manager can build a streak from it.
--   * note_founds is writable and readable only by the finder — the
--     author of a note can never learn it was found (no read receipts).
--   * A paused room accepts no new content, but everything already left
--     stays readable forever (Scripture is never locked).

-- ---------------------------------------------------------------- people

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  name text not null,
  portrait_path text,
  translation text not null default 'bsb' check (translation in ('bsb', 'web')),
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- rooms

create table public.rooms (
  id uuid primary key default gen_random_uuid(),
  name text,
  is_paused boolean not null default false,
  created_at timestamptz not null default now()
);

create table public.memberships (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  -- Ink lives on membership, not on the person: you're teal in one room
  -- and ochre in another. Null while the room is two (free palette).
  ink text check (ink in ('crimson','clay','ochre','moss','teal','indigo','plum','rose')),
  joined_at timestamptz not null default now(),
  unique (room_id, person_id)
);

create index memberships_person_idx on public.memberships (person_id);
create index memberships_room_idx on public.memberships (room_id);

-- -------------------------------------------------------------- readings

create table public.readings (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms (id) on delete cascade,
  book_id text not null,
  -- Size is set once from the book's word count and never changes during
  -- the read (§2.9).
  scale text not null check (scale in ('small', 'medium', 'large')),
  started_at timestamptz not null default now(),
  finished_at timestamptz
);

create index readings_room_idx on public.readings (room_id);

-- The campfire's persistent state. State is derived client-side by the
-- fire engine; what's stored is exactly what the engine remembers.
create table public.fires (
  reading_id uuid primary key references public.readings (id) on delete cascade,
  coal_depth double precision not null default 0,
  last_fuel_at timestamptz,
  restart_at timestamptz,
  state_at_last_fuel text not null default 'catching'
    check (state_at_last_fuel in ('catching', 'burning', 'steady'))
);

-- The rolling fuel window (§13): who fed the fire recently, discarded
-- after. Pruned by cron below. This cap is what makes a consistency
-- report impossible — not the absence of data, but the absence of history.
create table public.fuel_events (
  id uuid primary key default gen_random_uuid(),
  reading_id uuid not null references public.readings (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  at timestamptz not null default now()
);

create index fuel_events_reading_idx on public.fuel_events (reading_id, at);

-- ----------------------------------------------------------------- notes

create table public.notes (
  id uuid primary key default gen_random_uuid(),
  reading_id uuid not null references public.readings (id) on delete cascade,
  author_id uuid not null references public.profiles (id) on delete cascade,
  book_id text not null,
  chapter int not null,
  verse int not null,
  kind text not null check (kind in ('voice', 'written')),
  body text,
  audio_path text,
  waveform real[],
  transcript text,
  created_at timestamptz not null default now()
);

create index notes_reading_idx on public.notes (reading_id, chapter, verse);

-- Who found a note. The record stays; the author is never told — enforced
-- below by RLS, not by convention.
create table public.note_founds (
  note_id uuid not null references public.notes (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  found_at timestamptz not null default now(),
  primary key (note_id, person_id)
);

-- ------------------------------------------------------------ highlights

create table public.highlights (
  id uuid primary key default gen_random_uuid(),
  reading_id uuid not null references public.readings (id) on delete cascade,
  author_id uuid not null references public.profiles (id) on delete cascade,
  book_id text not null,
  chapter int not null,
  start_verse int not null,
  end_verse int not null,
  ink text not null check (ink in ('crimson','clay','ochre','moss','teal','indigo','plum','rose')),
  created_at timestamptz not null default now()
);

create index highlights_reading_idx on public.highlights (reading_id, chapter);

-- ----------------------------------------------------------------- cards
-- Phase two ships the UI; the schema exists now so the object model
-- doesn't churn (§03, §4.6).

create table public.cards (
  id uuid primary key default gen_random_uuid(),
  reading_id uuid not null references public.readings (id) on delete cascade,
  chapter int not null,
  question text not null,
  state text not null default 'sealed' check (state in ('sealed', 'open', 'set_down')),
  opened_at timestamptz
);

create table public.card_answers (
  card_id uuid not null references public.cards (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  body text not null,
  answered_at timestamptz not null default now(),
  primary key (card_id, person_id)
);

-- ------------------------------------------------------------ quiet days

create table public.quiet_days (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  -- The marker's local day and zone: there is no midnight, anywhere, for
  -- anyone (§4.9). No limit, no ledger — a counted grace is not grace.
  local_date date not null,
  time_zone text not null,
  marked_at timestamptz not null default now(),
  unique (room_id, person_id, local_date)
);

-- --------------------------------------------------------------- invites

create table public.invites (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.rooms (id) on delete cascade,
  created_by uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '30 days'
);

-- ------------------------------------------------------------- positions
-- Per-person, per-reading. There is no shared "where we are." Addresses,
-- never percentages.

create table public.positions (
  reading_id uuid not null references public.readings (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  chapter int not null,
  verse int not null,
  updated_at timestamptz not null default now(),
  primary key (reading_id, person_id)
);

-- One last-read stamp per person per room (§13) — renders as "this
-- morning", never as a clock time. Overwritten in place: no history.
create table public.last_read (
  room_id uuid not null references public.rooms (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  at timestamptz not null default now(),
  primary key (room_id, person_id)
);

-- ------------------------------------------------------------------ RLS

alter table public.profiles enable row level security;
alter table public.rooms enable row level security;
alter table public.memberships enable row level security;
alter table public.readings enable row level security;
alter table public.fires enable row level security;
alter table public.fuel_events enable row level security;
alter table public.notes enable row level security;
alter table public.note_founds enable row level security;
alter table public.highlights enable row level security;
alter table public.cards enable row level security;
alter table public.card_answers enable row level security;
alter table public.quiet_days enable row level security;
alter table public.invites enable row level security;
alter table public.positions enable row level security;
alter table public.last_read enable row level security;

create or replace function public.is_member(target_room uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from memberships
    where room_id = target_room and person_id = auth.uid()
  );
$$;

create or replace function public.reading_room(target_reading uuid)
returns uuid
language sql stable security definer
set search_path = public
as $$
  select room_id from readings where id = target_reading;
$$;

create or replace function public.room_is_paused(target_room uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select coalesce((select is_paused from rooms where id = target_room), false);
$$;

-- profiles: yourself, and the people you share a room with. No strangers.
create policy profiles_select on public.profiles for select
  using (
    id = auth.uid()
    or exists (
      select 1
      from memberships mine
      join memberships theirs on mine.room_id = theirs.room_id
      where mine.person_id = auth.uid() and theirs.person_id = profiles.id
    )
  );
create policy profiles_insert on public.profiles for insert
  with check (id = auth.uid());
create policy profiles_update on public.profiles for update
  using (id = auth.uid());

-- rooms: members read and tend; anyone signed in may start one.
create policy rooms_select on public.rooms for select
  using (public.is_member(id));
create policy rooms_insert on public.rooms for insert
  with check (auth.uid() is not null);
create policy rooms_update on public.rooms for update
  using (public.is_member(id));

-- memberships: visible to fellow members; you may add yourself only as a
-- room's first member (its creator) — every later join goes through
-- accept_invite below. You may update or remove only your own.
create policy memberships_select on public.memberships for select
  using (person_id = auth.uid() or public.is_member(room_id));
create policy memberships_insert on public.memberships for insert
  with check (
    person_id = auth.uid()
    and not exists (select 1 from memberships m where m.room_id = memberships.room_id)
  );
create policy memberships_update on public.memberships for update
  using (person_id = auth.uid());
create policy memberships_delete on public.memberships for delete
  using (person_id = auth.uid());

-- readings and the fire: the room's.
create policy readings_select on public.readings for select
  using (public.is_member(room_id));
create policy readings_insert on public.readings for insert
  with check (public.is_member(room_id) and not public.room_is_paused(room_id));
create policy readings_update on public.readings for update
  using (public.is_member(room_id));

create policy fires_select on public.fires for select
  using (public.is_member(public.reading_room(reading_id)));
create policy fires_insert on public.fires for insert
  with check (public.is_member(public.reading_room(reading_id)));
create policy fires_update on public.fires for update
  using (public.is_member(public.reading_room(reading_id)));

create policy fuel_select on public.fuel_events for select
  using (public.is_member(public.reading_room(reading_id)));
create policy fuel_insert on public.fuel_events for insert
  with check (
    person_id = auth.uid()
    and public.is_member(public.reading_room(reading_id))
    and not public.room_is_paused(public.reading_room(reading_id))
  );

-- notes: the reading's. New notes need the room started again (paused
-- rooms read, never write). Only the author edits or takes back.
create policy notes_select on public.notes for select
  using (public.is_member(public.reading_room(reading_id)));
create policy notes_insert on public.notes for insert
  with check (
    author_id = auth.uid()
    and public.is_member(public.reading_room(reading_id))
    and not public.room_is_paused(public.reading_room(reading_id))
  );
create policy notes_update on public.notes for update
  using (author_id = auth.uid());
create policy notes_delete on public.notes for delete
  using (author_id = auth.uid());

-- note_founds: the finder's record, and no one else's — including the
-- note's author. This is "no read receipts" as a database constraint.
create policy note_founds_select on public.note_founds for select
  using (person_id = auth.uid());
create policy note_founds_insert on public.note_founds for insert
  with check (person_id = auth.uid());

-- highlights: a mark on a shared page. They stay when someone leaves, so
-- there is no delete-by-room-member policy beyond the author's own.
create policy highlights_select on public.highlights for select
  using (public.is_member(public.reading_room(reading_id)));
create policy highlights_insert on public.highlights for insert
  with check (
    author_id = auth.uid()
    and public.is_member(public.reading_room(reading_id))
    and not public.room_is_paused(public.reading_room(reading_id))
  );
create policy highlights_delete on public.highlights for delete
  using (author_id = auth.uid());

-- cards: sealed answers are visible only to their author; every answer is
-- visible once the card opens. Never who hasn't answered — that's a query
-- the client must not run, and the sealed-answer policy makes the answers
-- themselves safe.
create policy cards_select on public.cards for select
  using (public.is_member(public.reading_room(reading_id)));
create policy cards_update on public.cards for update
  using (public.is_member(public.reading_room(reading_id)));
create policy card_answers_select on public.card_answers for select
  using (
    person_id = auth.uid()
    or exists (select 1 from cards c where c.id = card_id and c.state = 'open')
  );
create policy card_answers_insert on public.card_answers for insert
  with check (person_id = auth.uid());
create policy card_answers_update on public.card_answers for update
  using (person_id = auth.uid());

-- quiet days: marked by a person, for a room, in the open.
create policy quiet_days_select on public.quiet_days for select
  using (public.is_member(room_id));
create policy quiet_days_insert on public.quiet_days for insert
  with check (person_id = auth.uid() and public.is_member(room_id));

-- invites: members mint them; accepting goes through accept_invite, and
-- previewing through invite_preview, so the token alone never exposes the
-- room.
create policy invites_select on public.invites for select
  using (public.is_member(room_id));
create policy invites_insert on public.invites for insert
  with check (created_by = auth.uid() and public.is_member(room_id));

-- positions and the last-read stamp: yours to write, the room's to see.
create policy positions_select on public.positions for select
  using (public.is_member(public.reading_room(reading_id)));
create policy positions_upsert on public.positions for insert
  with check (person_id = auth.uid());
create policy positions_update on public.positions for update
  using (person_id = auth.uid());

create policy last_read_select on public.last_read for select
  using (public.is_member(room_id));
create policy last_read_insert on public.last_read for insert
  with check (person_id = auth.uid() and public.is_member(room_id));
create policy last_read_update on public.last_read for update
  using (person_id = auth.uid());

-- ------------------------------------------------------------ functions

-- Accepting an invite (S16). Checks expiry and the six-person ceiling,
-- then joins. Security definer: the joiner isn't a member yet, so RLS
-- alone can't let them in.
create or replace function public.accept_invite(invite_token uuid)
returns uuid
language plpgsql security definer
set search_path = public
as $$
declare
  inv invites%rowtype;
  member_count int;
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  select * into inv from invites where id = invite_token;
  if inv.id is null or inv.expires_at < now() then
    raise exception 'invite_expired';
  end if;
  select count(*) into member_count from memberships where room_id = inv.room_id;
  if member_count >= 6 then
    raise exception 'room_full';
  end if;
  insert into memberships (room_id, person_id)
  values (inv.room_id, auth.uid())
  on conflict (room_id, person_id) do nothing;
  return inv.room_id;
end;
$$;

-- What the invite screen shows before joining: a person, not a product.
create or replace function public.invite_preview(invite_token uuid)
returns table (inviter_name text, room_name text, expired boolean, full boolean)
language sql stable security definer
set search_path = public
as $$
  select
    p.name,
    r.name,
    i.expires_at < now(),
    (select count(*) from memberships m where m.room_id = i.room_id) >= 6
  from invites i
  join profiles p on p.id = i.created_by
  join rooms r on r.id = i.room_id
  where i.id = invite_token;
$$;

-- ----------------------------------------------------- pruning (pg_cron)

-- The rolling window: fuel events older than 48 hours are discarded (the
-- engine needs at most ~36; 48 leaves slack for clock skew). Nothing
-- older than this exists anywhere.
do $$
begin
  create extension if not exists pg_cron;
  perform cron.schedule(
    'prune-fuel-events',
    '17 * * * *',
    $job$ delete from public.fuel_events where at < now() - interval '48 hours' $job$);
exception when others then
  -- pg_cron unavailable in this environment; prune from an edge function
  -- or scheduled task instead.
  raise notice 'pg_cron not scheduled: %', sqlerrm;
end;
$$;

-- --------------------------------------------------------------- storage

-- Voice notes: private bucket, paths are <reading_id>/<note_id>.m4a,
-- readable and writable only by the reading's room.
insert into storage.buckets (id, name, public)
values ('voice-notes', 'voice-notes', false)
on conflict (id) do nothing;

create policy voice_notes_read on storage.objects for select
  using (
    bucket_id = 'voice-notes'
    and public.is_member(public.reading_room((split_part(name, '/', 1))::uuid))
  );

create policy voice_notes_write on storage.objects for insert
  with check (
    bucket_id = 'voice-notes'
    and public.is_member(public.reading_room((split_part(name, '/', 1))::uuid))
    and not public.room_is_paused(public.reading_room((split_part(name, '/', 1))::uuid))
  );
