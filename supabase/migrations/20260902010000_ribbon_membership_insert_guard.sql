-- The "first member only" guard on memberships_insert counted the room's
-- members through a subquery that row-level security filters: a person who
-- is not yet a member sees no membership rows, so the count was always zero
-- for exactly the person the guard exists to stop. Any signed-in user who
-- learned a room's id could seat themselves in it without an invite.
--
-- The count now runs as a security-definer function, which sees every row.
-- Joining still goes through accept_invite (its own definer function); the
-- only self-insert this policy permits is a room's creator taking the first
-- seat in a room nobody is in yet.

create or replace function public.room_has_members(target_room uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (select 1 from memberships m where m.room_id = target_room);
$$;

revoke execute on function public.room_has_members(uuid) from anon, public;
grant execute on function public.room_has_members(uuid) to authenticated;

drop policy if exists memberships_insert on public.memberships;
create policy memberships_insert on public.memberships for insert
  with check (
    person_id = auth.uid()
    and not public.room_has_members(room_id)
  );
