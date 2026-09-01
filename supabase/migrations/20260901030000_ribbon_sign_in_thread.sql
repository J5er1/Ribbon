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
