-- Being found (docs/deviations.md A72).
--
-- The owner's call: public profiles and discovery, built as Ribbon would
-- build them. Until now a person was visible only to the people they share
-- a room with, and a room only to its members ("No strangers", init.sql).
-- This opens exactly two doors, each from the inside:
--
-- 1. A person may be findable: their name, their face and one line in
--    their own words, seen by anyone signed in. Off unless they turn it on,
--    and only once they have said they are thirteen or older.
-- 2. A room may be open: found by anyone signed in, and asked to join. A
--    room is open only while every member has said yes (would_open), the
--    way closing a room asks everyone (§6.8): one person's switch never
--    exposes the others.
--
-- Nobody walks in. A stranger asks (a knock) with a line if they like, and
-- anyone in the room lets them in or leaves it; nobody is told no. A knock
-- lasts fourteen days, a person holds at most five at once, and asking
-- needs a findable profile, so the room can see who is asking.
--
-- Safety comes with it: blocks (two people who block each other stop seeing
-- each other in every list, and cannot ask into each other's rooms) and
-- reports (written, and read only by the people who run Ribbon).
--
-- Nothing here is removed: policies that already exist are altered in place
-- and triggers are created or replaced, so it runs the same way from the
-- dashboard, the CLI or a tool that asks before anything is removed.
--
-- And one repair, because this hands room ids to strangers: memberships and
-- invites could be updated into another room (their update policies check
-- whose row it is, not where it points). Both now keep their place.

-- --------------------------------------------------------- keeping place

create or replace function public.keep_in_place() returns trigger
language plpgsql
set search_path = public
as $$
declare
  c text;
begin
  foreach c in array tg_argv loop
    if (to_jsonb(new) -> c) is distinct from (to_jsonb(old) -> c) then
      raise exception 'keep_in_place: % cannot change', c using errcode = '42501';
    end if;
  end loop;
  return new;
end;
$$;

create or replace trigger memberships_keep_in_place before update on public.memberships
  for each row execute function public.keep_in_place('room_id', 'person_id');

create or replace trigger invites_keep_in_place before update on public.invites
  for each row execute function public.keep_in_place('room_id', 'created_by');

-- ------------------------------------------------------- people, findable

alter table public.profiles
  add column if not exists findable boolean not null default false,
  add column if not exists line text,
  add column if not exists thirteen_or_older_at timestamptz;

alter table public.profiles add constraint profiles_line_is_a_line
  check (line is null or char_length(line) between 1 and 140);
alter table public.profiles add constraint profiles_findable_said_thirteen
  check (not findable or thirteen_or_older_at is not null);

-- ------------------------------------------------------------------ blocks

create table if not exists public.blocks (
  blocker uuid not null references public.profiles (id) on delete cascade,
  blocked uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker, blocked),
  constraint blocks_not_yourself check (blocker <> blocked)
);

alter table public.blocks enable row level security;

-- Your own blocks, and nobody else's: who has blocked you is never said.
create policy blocks_own on public.blocks for all
  using (blocker = (select public.current_user_id()))
  with check (blocker = (select public.current_user_id()));

grant select, insert, delete on public.blocks to authenticated;

create index if not exists blocks_blocked_idx on public.blocks (blocked);

-- Whether the caller and `other` have blocked each other, either way. Asks
-- only about the caller, so it says nothing about anyone else's blocks.
create or replace function public.blocked_with_me(other uuid) returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from blocks
    where (blocker = (select public.current_user_id()) and blocked = other)
       or (blocker = other and blocked = (select public.current_user_id()))
  );
$$;

revoke execute on function public.blocked_with_me(uuid) from public, anon;
grant execute on function public.blocked_with_me(uuid) to authenticated;

-- ------------------------------------------------------- rooms, and asking

alter table public.rooms
  add column if not exists line text;
alter table public.rooms add constraint rooms_line_is_a_line
  check (line is null or char_length(line) between 1 and 140);

alter table public.memberships
  add column if not exists would_open boolean not null default false;

-- Open: not paused, somebody in it, and nobody in it who has not said yes.
create or replace function public.room_is_open(target_room uuid) returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (select 1 from rooms where id = target_room and not is_paused)
     and exists (select 1 from memberships where room_id = target_room)
     and not exists (
       select 1 from memberships where room_id = target_room and not would_open);
$$;

revoke execute on function public.room_is_open(uuid) from public, anon;
grant execute on function public.room_is_open(uuid) to authenticated;

-- Whether anyone in the room and the caller have blocked each other.
create or replace function public.room_blocked_with_me(target_room uuid) returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from memberships m
    where m.room_id = target_room and public.blocked_with_me(m.person_id));
$$;

revoke execute on function public.room_blocked_with_me(uuid) from public, anon;
grant execute on function public.room_blocked_with_me(uuid) to authenticated;

create table if not exists public.knocks (
  room_id uuid not null references public.rooms (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  line text,
  created_at timestamptz not null default now(),
  primary key (room_id, person_id),
  constraint knocks_line_is_a_line check (line is null or char_length(line) between 1 and 280)
);

create index if not exists knocks_person_idx on public.knocks (person_id);

alter table public.knocks enable row level security;

-- Yours, and the ones on your rooms from anyone you have not blocked.
-- Written only through ask_to_join; taken back by the asker, or left by
-- anyone in the room — silently, either way.
create policy knocks_select on public.knocks for select
  using (
    person_id = (select public.current_user_id())
    or (public.is_member(room_id) and not public.blocked_with_me(person_id))
  );

create policy knocks_delete on public.knocks for delete
  using (person_id = (select public.current_user_id()) or public.is_member(room_id));

grant select, delete on public.knocks to authenticated;

-- Whether `person` is asking into a room the caller is in, freshly.
create or replace function public.asks_into_my_room(person uuid) returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from knocks k
    join memberships m on m.room_id = k.room_id
    where k.person_id = person
      and m.person_id = (select public.current_user_id())
      and k.created_at > now() - interval '14 days');
$$;

revoke execute on function public.asks_into_my_room(uuid) from public, anon;
grant execute on function public.asks_into_my_room(uuid) to authenticated;

-- --------------------------------------------------- who can see a person

-- Yourself; anyone you share a room with (as before); and, if neither of
-- you has blocked the other, anyone findable, and anyone asking into one of
-- your rooms.
create or replace function public.can_see_person(person uuid) returns boolean
language sql stable security definer
set search_path = public
as $$
  select person = (select public.current_user_id())
    or exists (
      select 1
      from memberships mine
      join memberships theirs on mine.room_id = theirs.room_id
      where mine.person_id = (select public.current_user_id())
        and theirs.person_id = person)
    or (
      (select public.current_user_id()) is not null
      and not public.blocked_with_me(person)
      and (
        exists (select 1 from profiles where id = person and findable)
        or public.asks_into_my_room(person)));
$$;

-- Signed out it answers no, but it must be callable: the policies below
-- run it for whoever is asking, and a refusal there would be an error
-- rather than an empty answer.
revoke execute on function public.can_see_person(uuid) from public;
grant execute on function public.can_see_person(uuid) to anon, authenticated;

alter policy profiles_select on public.profiles
  using (public.can_see_person(id));

-- A portrait follows its person: whoever can see someone can see their face.
alter policy portraits_read on storage.objects
  using (
    bucket_id = 'portraits'
    and split_part(name, '.', 1) ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and public.can_see_person(split_part(name, '.', 1)::uuid)
  );

-- ------------------------------------------------------------- finding

-- The open rooms the caller could ask into, most recently read first.
-- Never a room they are in, a paused one, or one where anybody and the
-- caller have blocked each other. `words` matches the room's name and line;
-- `books` the book it is reading (the phone turns "Mark" into "MRK");
-- `person` the rooms a findable person is in; `room` one room. With none of
-- them, every open room.
--
-- What it says of a room: its name and line, whether it is full and
-- whether you have asked, the book it is reading and its fire's stored
-- fields (the phone's fire engine turns those into a state), and the faces
-- of its findable members. Never how many people are in it.
create or replace function public.find_rooms(
  words text default null,
  books text[] default null,
  person uuid default null,
  room uuid default null
)
returns table (
  room_id uuid,
  name text,
  line text,
  full boolean,
  asked boolean,
  book_id text,
  scale text,
  coal_depth double precision,
  last_fuel_at timestamptz,
  restart_at timestamptz,
  state_at_last_fuel text,
  faces jsonb
)
language sql stable security definer
set search_path = public
as $$
  with me as (
    select (select public.current_user_id()) as id
  ),
  found as (
    select r.id, r.name, r.line
    from rooms r, me
    where me.id is not null
      and public.room_is_open(r.id)
      and not exists (
        select 1 from memberships m where m.room_id = r.id and m.person_id = me.id)
      and not public.room_blocked_with_me(r.id)
      and (find_rooms.room is null or r.id = find_rooms.room)
      and (find_rooms.person is null or exists (
        select 1 from memberships m join profiles p on p.id = m.person_id
        where m.room_id = r.id and m.person_id = find_rooms.person and p.findable))
  ),
  reading as (
    select distinct on (rd.room_id) rd.room_id, rd.id, rd.book_id, rd.scale
    from readings rd
    join found on found.id = rd.room_id
    where rd.finished_at is null
    order by rd.room_id, rd.started_at desc
  )
  select
    found.id,
    found.name,
    found.line,
    (select count(*) from memberships m where m.room_id = found.id) >= 12,
    exists (
      select 1 from knocks k, me
      where k.room_id = found.id and k.person_id = me.id
        and k.created_at > now() - interval '14 days'),
    reading.book_id,
    reading.scale,
    f.coal_depth,
    f.last_fuel_at,
    f.restart_at,
    f.state_at_last_fuel,
    coalesce((
      select jsonb_agg(
        jsonb_build_object('id', p.id, 'name', p.name, 'portrait_path', p.portrait_path)
        order by m.joined_at)
      from memberships m join profiles p on p.id = m.person_id
      where m.room_id = found.id and p.findable), '[]'::jsonb)
  from found
  left join reading on reading.room_id = found.id
  left join fires f on f.reading_id = reading.id
  where (find_rooms.words is null and find_rooms.books is null)
     or (find_rooms.words is not null and (
          position(lower(find_rooms.words) in lower(coalesce(found.name, ''))) > 0
          or position(lower(find_rooms.words) in lower(coalesce(found.line, ''))) > 0))
     or (find_rooms.books is not null and reading.book_id = any (find_rooms.books))
  order by f.last_fuel_at desc nulls last, found.id
  limit 50;
$$;

revoke execute on function public.find_rooms(text, text[], uuid, uuid) from public, anon;
grant execute on function public.find_rooms(text, text[], uuid, uuid) to authenticated;

-- Findable people by name, for finding someone you already know of. Never
-- anyone either of you has blocked, and never yourself.
create or replace function public.find_people(words text)
returns table (id uuid, name text, line text, portrait_path text)
language sql stable security definer
set search_path = public
as $$
  select p.id, p.name, p.line, p.portrait_path
  from profiles p
  where (select public.current_user_id()) is not null
    and p.findable
    and p.id <> (select public.current_user_id())
    and not public.blocked_with_me(p.id)
    and char_length(btrim(coalesce(words, ''))) >= 2
    and position(lower(btrim(words)) in lower(p.name)) > 0
  order by p.name, p.id
  limit 30;
$$;

revoke execute on function public.find_people(text) from public, anon;
grant execute on function public.find_people(text) to authenticated;

-- ------------------------------------------------------- asking to join

create or replace function public.ask_to_join(target_room uuid, said text default null)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  uid uuid := (select public.current_user_id());
  words text := nullif(btrim(coalesce(said, '')), '');
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not exists (select 1 from profiles where id = uid and findable) then
    raise exception 'not_findable';
  end if;
  if exists (select 1 from memberships where room_id = target_room and person_id = uid) then
    return;
  end if;
  -- A room that has blocked you reads exactly as one that is not open.
  if not public.room_is_open(target_room) or public.room_blocked_with_me(target_room) then
    raise exception 'room_not_open';
  end if;
  if (select count(*) from memberships where room_id = target_room) >= 12 then
    raise exception 'room_full';
  end if;
  if (select count(*) from knocks
      where person_id = uid and room_id <> target_room
        and created_at > now() - interval '14 days') >= 5 then
    raise exception 'asked_enough';
  end if;
  insert into knocks (room_id, person_id, line)
  values (target_room, uid, left(words, 280))
  on conflict (room_id, person_id)
  do update set line = excluded.line, created_at = now();
end;
$$;

revoke execute on function public.ask_to_join(uuid, text) from public, anon;
grant execute on function public.ask_to_join(uuid, text) to authenticated;

-- Anyone in the room lets an asker in. They arrive having already said yes
-- to the room being found, because that is how they found it.
create or replace function public.let_in(target_room uuid, person uuid)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  uid uuid := (select public.current_user_id());
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  if not public.is_member(target_room) then
    raise exception 'not_a_member';
  end if;
  perform 1 from rooms where id = target_room for update;
  if exists (select 1 from memberships where room_id = target_room and person_id = person) then
    delete from knocks where room_id = target_room and person_id = person;
    return;
  end if;
  if not exists (
    select 1 from knocks
    where room_id = target_room and person_id = person
      and created_at > now() - interval '14 days')
     or public.blocked_with_me(person) then
    raise exception 'no_ask';
  end if;
  if (select count(*) from memberships where room_id = target_room) >= 12 then
    raise exception 'room_full';
  end if;
  insert into memberships (room_id, person_id, would_open)
  values (target_room, person, true)
  on conflict (room_id, person_id) do nothing;
  delete from knocks where room_id = target_room and person_id = person;
end;
$$;

revoke execute on function public.let_in(uuid, uuid) from public, anon;
grant execute on function public.let_in(uuid, uuid) to authenticated;

-- ------------------------------------------------------------- reports

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter uuid references public.profiles (id) on delete set null,
  person_id uuid references public.profiles (id) on delete set null,
  room_id uuid references public.rooms (id) on delete set null,
  reason text,
  created_at timestamptz not null default now(),
  constraint reports_reason_is_short check (reason is null or char_length(reason) <= 1000)
);

alter table public.reports enable row level security;

-- Written by the reporter, read by nobody signed in: reports are for the
-- people who run Ribbon, through the dashboard.
create policy reports_insert on public.reports for insert
  with check (
    reporter = (select public.current_user_id())
    and (person_id is not null or room_id is not null));

grant insert on public.reports to authenticated;
