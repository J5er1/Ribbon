-- The sign-in thread: what the storage layer needs the day accounts go
-- live. (profiles.translation was already opened up by
-- 20260901001000_ribbon_open_translation_registry.)
--
-- 1. A portraits bucket. Presence is faces (§2.7): when a person joins a
--    room from another phone, the room should see them. Paths are
--    <person_id>.jpg — a person writes only their own; anyone who shares
--    a room with them may read it. Same posture as voice-notes: private
--    bucket, capability comes from membership, never from the URL.
--
-- 2. The voice-notes policies get cast-safe. Policies on storage.objects
--    are evaluated per row across every bucket, and SQL AND does not
--    promise evaluation order — so a bare ::uuid cast in one bucket's
--    policy can raise on another bucket's object names. With a second
--    bucket arriving, neither bucket's policy may cast text that might
--    not be a uuid: the guard function returns null instead of raising,
--    and the portraits policies compare as text and never cast at all.

insert into storage.buckets (id, name, public)
values ('portraits', 'portraits', false)
on conflict (id) do nothing;

create policy portraits_read on storage.objects for select
  using (
    bucket_id = 'portraits'
    and (
      split_part(name, '.', 1) = auth.uid()::text
      or exists (
        select 1
        from public.memberships mine
        join public.memberships theirs on mine.room_id = theirs.room_id
        where mine.person_id = auth.uid()
          and theirs.person_id::text = split_part(name, '.', 1)
      )
    )
  );

create policy portraits_write on storage.objects for insert
  with check (
    bucket_id = 'portraits'
    and split_part(name, '.', 1) = auth.uid()::text
  );

-- A portrait can be replaced (storage upsert issues an update).
create policy portraits_update on storage.objects for update
  using (
    bucket_id = 'portraits'
    and split_part(name, '.', 1) = auth.uid()::text
  );

-- Recreate the voice-notes policies with the guarded cast.
drop policy if exists voice_notes_read on storage.objects;
drop policy if exists voice_notes_write on storage.objects;

create or replace function public.uuid_or_null(candidate text)
returns uuid
language sql immutable
as $$
  select case
    when candidate ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
    then candidate::uuid
  end;
$$;

revoke execute on function public.uuid_or_null(text) from anon, public;
grant execute on function public.uuid_or_null(text) to authenticated;

create policy voice_notes_read on storage.objects for select
  using (
    bucket_id = 'voice-notes'
    and public.is_member(public.reading_room(public.uuid_or_null(split_part(name, '/', 1))))
  );

create policy voice_notes_write on storage.objects for insert
  with check (
    bucket_id = 'voice-notes'
    and public.is_member(public.reading_room(public.uuid_or_null(split_part(name, '/', 1))))
    and not public.room_is_paused(public.reading_room(public.uuid_or_null(split_part(name, '/', 1))))
  );

-- 3. Re-upserting what already exists must be a no-op, not an error.
--    PostgREST's merge-duplicates takes the ON CONFLICT DO UPDATE path,
--    and Postgres then requires an UPDATE policy: an invite is re-pushed
--    whenever it's handed out, quiet days on every sign-in.
create policy invites_update on public.invites for update
  using (created_by = auth.uid());
create policy quiet_days_update on public.quiet_days for update
  using (person_id = auth.uid());

-- 4. accept_invite takes the room's lock before counting, so two
--    simultaneous joins can't both read five and seat a seventh. Same
--    body as the original otherwise; create-or-replace keeps the
--    hardening migration's grants.
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
  perform 1 from rooms where id = inv.room_id for update;
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
