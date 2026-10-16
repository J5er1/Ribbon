-- A room of twelve (docs/deviations.md A70).
--
-- The owner's call: a room holds twelve, not six, and everyone in it has an
-- ink of their own, so the eight inks become twelve — bronze, jade, sky and
-- iris join them.
--
-- Two things change here:
--
-- 1. Every ink check accepts the twelve names. The checks were written
--    inline, so their names are whatever Postgres gave them; each is found
--    by what it says rather than by name, dropped, and written again under
--    a name of its own. Older builds read an ink they do not know as no ink
--    (a membership) or ochre (a highlight), and never write one.
-- 2. accept_invite refuses the thirteenth, not the seventh, and
--    invite_preview says a room is full at twelve. Both are otherwise the
--    versions in 20260909220000, unchanged: the row lock on the room still
--    keeps two simultaneous joins from seating one too many.

do $$
declare
  t text;
  c record;
begin
  foreach t in array array['memberships', 'highlights', 'room_inks'] loop
    for c in
      select con.conname
      from pg_constraint con
      join pg_class rel on rel.oid = con.conrelid
      join pg_namespace ns on ns.oid = rel.relnamespace
      where ns.nspname = 'public'
        and rel.relname = t
        and con.contype = 'c'
        and pg_get_constraintdef(con.oid) like '%crimson%'
    loop
      execute format('alter table public.%I drop constraint %I', t, c.conname);
    end loop;
  end loop;
end;
$$;

alter table public.memberships add constraint memberships_ink_is_an_ink check (
  ink in ('crimson','clay','ochre','bronze','moss','jade',
          'teal','sky','indigo','iris','plum','rose'));

alter table public.highlights add constraint highlights_ink_is_an_ink check (
  ink in ('crimson','clay','ochre','bronze','moss','jade',
          'teal','sky','indigo','iris','plum','rose'));

alter table public.room_inks add constraint room_inks_ink_is_an_ink check (
  ink in ('crimson','clay','ochre','bronze','moss','jade',
          'teal','sky','indigo','iris','plum','rose'));

create or replace function public.invite_preview(invite_token uuid)
returns table (inviter_name text, room_name text, expired boolean, full boolean)
language sql stable security definer
set search_path = public
as $$
  select
    coalesce(p.name, 'Someone'),
    r.name,
    i.expires_at < now(),
    (select count(*) from memberships m where m.room_id = i.room_id) >= 12
  from invites i
  left join profiles p on p.id = i.created_by
  left join rooms r on r.id = i.room_id
  where i.id = invite_token;
$$;

revoke execute on function public.invite_preview(uuid) from public;
grant execute on function public.invite_preview(uuid) to anon, authenticated;

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

revoke execute on function public.accept_invite(uuid) from public;
grant execute on function public.accept_invite(uuid) to authenticated;
