-- Migration: 20260909220000_fix_invite_expiry_and_memberships_rls.sql
-- Fixes:
-- 1. memberships_insert RLS: allow existing members to upsert/update their row without RLS 42501 error
-- 2. invite_preview RPC: use LEFT JOIN on profiles and rooms so invites remain visible even if metadata is syncing
-- 3. accept_invite RPC: raise 'invite_not_found' if the token does not exist, reserving 'invite_expired' for truly expired tokens

-- 1. memberships_insert RLS
drop policy if exists memberships_insert on public.memberships;
create policy memberships_insert on public.memberships for insert
  with check (
    person_id = (select public.current_user_id())
    and (
      not exists (select 1 from memberships m where m.room_id = memberships.room_id)
      or public.is_member(room_id)
    )
  );

-- 2. invite_preview RPC
create or replace function public.invite_preview(invite_token uuid)
returns table (inviter_name text, room_name text, expired boolean, full boolean)
language sql stable security definer
set search_path = public
as $$
  select
    coalesce(p.name, 'Someone'),
    r.name,
    i.expires_at < now(),
    (select count(*) from memberships m where m.room_id = i.room_id) >= 6
  from invites i
  left join profiles p on p.id = i.created_by
  left join rooms r on r.id = i.room_id
  where i.id = invite_token;
$$;

revoke execute on function public.invite_preview(uuid) from public;
grant execute on function public.invite_preview(uuid) to anon, authenticated;

-- 3. accept_invite RPC
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
  if member_count >= 6 then
    raise exception 'room_full';
  end if;
  insert into memberships (room_id, person_id)
  values (inv.room_id, uid)
  on conflict (room_id, person_id) do nothing;
  return inv.room_id;
end;
$$;

grant execute on function public.accept_invite(uuid) to authenticated;
