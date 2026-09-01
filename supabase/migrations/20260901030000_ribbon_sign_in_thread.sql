-- The sign-in thread: what the schema needs the day accounts go live.
--
-- 1. profiles.translation stops enumerating. The launch check named only
--    the two bundled translations; the registry has since grown licensed
--    editions (nkjv, niv, nasb), and a translation is a client-side
--    registry entry, not a migration (docs/deviations.md). A profile's
--    translation is a personal preference string; the client validates it
--    against its registry, and an unknown value renders as its own key.
--
-- 2. A portraits bucket. Presence is faces (§2.7): when a person joins a
--    room from another phone, the room should see them. Paths are
--    <person_id>.jpg — a person writes only their own; anyone who shares
--    a room with them may read it. Same posture as voice-notes: private
--    bucket, capability comes from membership, never from the URL.

alter table public.profiles
  drop constraint if exists profiles_translation_check;
alter table public.profiles
  add constraint profiles_translation_check
  check (char_length(translation) between 1 and 24);

insert into storage.buckets (id, name, public)
values ('portraits', 'portraits', false)
on conflict (id) do nothing;

create policy portraits_read on storage.objects for select
  using (
    bucket_id = 'portraits'
    and (
      (split_part(name, '.', 1))::uuid = auth.uid()
      or exists (
        select 1
        from public.memberships mine
        join public.memberships theirs on mine.room_id = theirs.room_id
        where mine.person_id = auth.uid()
          and theirs.person_id = (split_part(name, '.', 1))::uuid
      )
    )
  );

create policy portraits_write on storage.objects for insert
  with check (
    bucket_id = 'portraits'
    and (split_part(name, '.', 1))::uuid = auth.uid()
  );

-- A portrait can be replaced (storage upsert issues an update).
create policy portraits_update on storage.objects for update
  using (
    bucket_id = 'portraits'
    and (split_part(name, '.', 1))::uuid = auth.uid()
  );
