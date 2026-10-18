-- Closing a room (docs/deviations.md A73; build book §6.8).
--
-- "Requires everyone to agree, or 30 days of silence plus one member's
-- request. On close, every member gets an export of the whole shelf —
-- notes, highlights, cards — as a readable file. No dark pattern, no
-- retention offer."
--
-- Each member says yes (memberships.would_close), the way opening a room
-- asks everyone (A72). close_room records the caller's yes and closes the
-- room when nobody is left who has not said it — or, after thirty days in
-- which nobody has read, written or marked anything, on the one request.
-- A closed room keeps every row: the shelf is exported from it on each
-- phone, and nothing is deleted. It can no longer be found, joined by an
-- invite or asked into.
--
-- Nothing here is removed, and every function is created or replaced, so
-- it runs the same from the dashboard, the CLI or a tool that asks before
-- anything is removed.

-- ----------------------------------------------------------- the columns

alter table public.rooms
  add column if not exists closed_at timestamptz;

alter table public.memberships
  add column if not exists would_close boolean not null default false;

-- Only close_room closes a room. rooms_update lets any member update the
-- row, which would let one person close it for everyone; a signed-in
-- request may not set or clear closed_at itself. close_room runs as its
-- owner, so it is not caught here.
create or replace function public.closed_only_by_closing() returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user in ('authenticated', 'anon')
     and (tg_op = 'INSERT' and new.closed_at is not null
          or tg_op = 'UPDATE' and new.closed_at is distinct from old.closed_at) then
    raise exception 'closed_only_by_closing' using errcode = '42501';
  end if;
  return new;
end;
$$;

create or replace trigger rooms_closed_only_by_closing before insert or update on public.rooms
  for each row execute function public.closed_only_by_closing();

-- ------------------------------------------------------------- silence

-- When anybody last did anything in the room: read (the fire's last fuel),
-- wrote a note, laid a mark, answered a card, or arrived.
create or replace function public.room_last_heard(target_room uuid) returns timestamptz
language sql stable security definer
set search_path = public
as $$
  select greatest(
    (select created_at from rooms where id = target_room),
    (select max(joined_at) from memberships where room_id = target_room),
    (select max(f.last_fuel_at) from fires f join readings r on r.id = f.reading_id
       where r.room_id = target_room),
    (select max(n.created_at) from notes n join readings r on r.id = n.reading_id
       where r.room_id = target_room),
    (select max(h.created_at) from highlights h join readings r on r.id = h.reading_id
       where r.room_id = target_room),
    (select max(a.answered_at) from card_answers a
       join cards c on c.id = a.card_id join readings r on r.id = c.reading_id
       where r.room_id = target_room));
$$;

revoke execute on function public.room_last_heard(uuid) from public, anon;
grant execute on function public.room_last_heard(uuid) to authenticated;

-- ------------------------------------------------------------- closing

-- Your yes to closing. Answers 'closed' when the room is closed — now, or
-- already — and 'waiting' while somebody has not said yes and the room
-- has not been quiet for thirty days. Who has not said yes is never named.
create or replace function public.close_room(target_room uuid) returns text
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
  if exists (select 1 from rooms where id = target_room and closed_at is not null) then
    return 'closed';
  end if;
  update memberships set would_close = true
  where room_id = target_room and person_id = uid;
  if not exists (
       select 1 from memberships where room_id = target_room and not would_close)
     or public.room_last_heard(target_room) < now() - interval '30 days' then
    update rooms set closed_at = now() where id = target_room;
    return 'closed';
  end if;
  return 'waiting';
end;
$$;

revoke execute on function public.close_room(uuid) from public, anon;
grant execute on function public.close_room(uuid) to authenticated;

-- ------------------------------------------- a closed room stays closed

-- Not found, not asked into (A72).
create or replace function public.room_is_open(target_room uuid) returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (select 1 from rooms where id = target_room and not is_paused and closed_at is null)
     and exists (select 1 from memberships where room_id = target_room)
     and not exists (
       select 1 from memberships where room_id = target_room and not would_open);
$$;

-- Not joined by an invite. Shipped builds read the preview, and a closed
-- room's invite reads to them as an expired one, which they already say
-- plainly ("This invite has expired. Ask for a new one.").
create or replace function public.invite_preview(invite_token uuid)
returns table (inviter_name text, room_name text, expired boolean, "full" boolean)
language sql stable security definer
set search_path = public
as $$
  select
    coalesce(p.name, 'Someone'),
    r.name,
    i.expires_at < now() or r.closed_at is not null,
    (select count(*) from memberships m where m.room_id = i.room_id) >= 12
  from invites i
  left join profiles p on p.id = i.created_by
  left join rooms r on r.id = i.room_id
  where i.id = invite_token;
$$;

create or replace function public.accept_invite(invite_token uuid)
returns uuid
language plpgsql security definer
set search_path = public
as $$
declare
  inv invites%rowtype;
  member_count int;
  uid uuid := public.current_user_id();
begin
  if uid is null then
    raise exception 'not_signed_in';
  end if;
  select * into inv from invites where id = invite_token;
  if inv.id is null then
    raise exception 'invite_not_found';
  end if;
  if inv.expires_at < now() then
    raise exception 'invite_expired';
  end if;
  if exists (
    select 1 from memberships
    where room_id = inv.room_id and person_id = uid
  ) then
    return inv.room_id;
  end if;
  perform 1 from rooms where id = inv.room_id for update;
  -- A closed room's invite is spent, and says so as an expired one does.
  if exists (select 1 from rooms where id = inv.room_id and closed_at is not null) then
    raise exception 'invite_expired';
  end if;
  select count(*) into member_count from memberships where room_id = inv.room_id;
  if member_count >= 12 then
    raise exception 'room_full';
  end if;
  insert into memberships (room_id, person_id)
  values (inv.room_id, uid)
  on conflict (room_id, person_id) do nothing;
  return inv.room_id;
end;
$$;

-- Nobody arrives in a closed room, by any door: an invite, an ask let in
-- (let_in), or a row written by hand.
create or replace function public.no_one_into_a_closed_room() returns trigger
language plpgsql
set search_path = public
as $$
begin
  if exists (select 1 from rooms where id = new.room_id and closed_at is not null) then
    raise exception 'room_closed' using errcode = '42501';
  end if;
  return new;
end;
$$;

create or replace trigger memberships_not_into_a_closed_room before insert on public.memberships
  for each row execute function public.no_one_into_a_closed_room();
