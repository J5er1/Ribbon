-- Migration: 20260923030000_ribbon_unblock_room_creation.sql
--
-- Applied to the live project on 2026-09-23. Three faults which between them
-- meant that, for an Auth0 account, every write after `profiles` failed and
-- `rooms`, `memberships` and `invites` were all empty — so every invite link
-- resolved to nothing and said "That invite isn't there any more."
--
-- 1. rooms_select was `is_member(id)`. Every PostgREST write carries a
--    RETURNING clause, and Postgres applies the SELECT policy to the row a
--    write returns — so creating a room meant being refused sight of the row
--    you had just written. The membership that would satisfy `is_member` is
--    pushed *after* the room, so this never resolved: 42501, for good. A
--    plain INSERT passes; `INSERT ... RETURNING` does not.
-- 2. memberships_insert referenced `memberships` inline, so the policy
--    re-entered its own table: 42P17 infinite recursion on every attempt to
--    seat the first member. Introduced by
--    20260909220000_fix_invite_expiry_and_memberships_rls.sql.
-- 3. ribbons_insert/ribbons_update still called auth.uid(), which casts the
--    raw subject to uuid and throws 22P02 on an Auth0 sub
--    ("google-oauth2|…"). Every ribbon push 400'd.

-- A room nobody has joined yet. SECURITY DEFINER so a policy on memberships
-- can ask the question without re-entering memberships' own policies — the
-- same reason is_member() is one.
create or replace function public.room_has_no_members(target_room uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select not exists (select 1 from memberships where room_id = target_room);
$$;

grant execute on function public.room_has_no_members(uuid) to authenticated, anon;

-- 1. A room with no members holds nobody's content, and no invite can point
--    at it (invites_insert requires is_member) — so it is safe to see, and it
--    is exactly the room you are in the middle of creating. This is the rule
--    memberships_insert already uses for "you may seat yourself as a room's
--    first member".
drop policy if exists rooms_select on public.rooms;
create policy rooms_select on public.rooms for select
  using (
    public.is_member(id)
    or public.room_has_no_members(id)
  );

-- The same window, for the UPDATE half of an upsert: a room nobody has joined
-- is the creator's to write. Once it has a member it is members-only again.
drop policy if exists rooms_update on public.rooms;
create policy rooms_update on public.rooms for update
  using (
    public.is_member(id)
    or public.room_has_no_members(id)
  )
  with check (
    public.is_member(id)
    or public.room_has_no_members(id)
  );

-- 2. Same rule as before — you may seat yourself in a room that has no
--    members, or one you are already in — asked without the self-reference.
drop policy if exists memberships_insert on public.memberships;
create policy memberships_insert on public.memberships for insert
  with check (
    person_id = (select public.current_user_id())
    and (
      public.room_has_no_members(room_id)
      or public.is_member(room_id)
    )
  );

-- 3. auth.uid() cannot read an Auth0 subject; current_user_id() can, and is
--    what every other table already uses.
drop policy if exists ribbons_insert on public.ribbons;
create policy ribbons_insert on public.ribbons for insert
  with check (
    person_id = (select public.current_user_id())
    and public.is_member(public.reading_room(reading_id))
  );

drop policy if exists ribbons_update on public.ribbons;
create policy ribbons_update on public.ribbons for update
  using (public.is_member(public.reading_room(reading_id)))
  with check (person_id = (select public.current_user_id()));
