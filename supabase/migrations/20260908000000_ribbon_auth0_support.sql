-- Ribbon — Support for Auth0 as Third-Party Auth Provider.
--
-- Background:
-- When using Auth0 as an external identity provider:
-- 1. Users authenticate via Auth0 and send an Auth0-signed ID token to Supabase.
-- 2. Auth0 users do not pre-exist in Supabase's local auth.users table.
--    Dropping the foreign key constraint profiles_id_fkey allows profiles
--    to be created for Auth0 users while preserving all relational integrity within
--    the public schema (memberships, readings, notes, etc.).
-- 3. Auth0 'sub' claims are strings (e.g. "auth0|64f2..." or "google-oauth2|...").
--    Supabase's built-in auth.uid() function casts request.jwt.claim.sub directly to uuid,
--    which causes PostgreSQL to throw an error on string subjects.
--    Furthermore, Supabase protects the auth schema so auth.uid() cannot be replaced.
-- 4. This migration provides public.current_user_id(), which safely extracts the user UUID
--    from either the injected 'user_uuid' claim, a native Supabase UUID sub, or a
--    deterministic RFC 4122 UUIDv5 hash of the Auth0 string subject.
--    It then updates existing RLS policies and functions to use public.current_user_id().

create extension if not exists "uuid-ossp" with schema extensions;

-- 1. Detach profiles.id from auth.users(id) so third-party auth users can write profiles
alter table public.profiles drop constraint if exists profiles_id_fkey;

-- 2. Safe, universal user ID resolver
create or replace function public.current_user_id()
returns uuid
language sql stable security definer
set search_path = public, extensions
as $$
  select coalesce(
    -- Priority 1: 'user_uuid' custom claim explicitly injected by our Auth0 Action
    nullif(current_setting('request.jwt.claim.user_uuid', true), '')::uuid,
    nullif(current_setting('request.jwt.claims', true)::jsonb->>'user_uuid', '')::uuid,
    -- Priority 2: native Supabase auth where sub is already a UUID
    case
      when current_setting('request.jwt.claim.sub', true) ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        then current_setting('request.jwt.claim.sub', true)::uuid
      when current_setting('request.jwt.claims', true)::jsonb->>'sub' ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
        then (current_setting('request.jwt.claims', true)::jsonb->>'sub')::uuid
      -- Priority 3: deterministic UUIDv5 generated from string sub (e.g. auth0|...)
      when current_setting('request.jwt.claim.sub', true) is not null and current_setting('request.jwt.claim.sub', true) != ''
        then extensions.uuid_generate_v5('6ba7b810-9dad-11d1-80b4-00c04fd430c8'::uuid, current_setting('request.jwt.claim.sub', true))
      when current_setting('request.jwt.claims', true)::jsonb->>'sub' is not null
        then extensions.uuid_generate_v5('6ba7b810-9dad-11d1-80b4-00c04fd430c8'::uuid, current_setting('request.jwt.claims', true)::jsonb->>'sub')
      else null
    end
  );
$$;

grant execute on function public.current_user_id() to authenticated, anon;

-- 3. Update helper functions to use public.current_user_id()
create or replace function public.is_member(target_room uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from memberships
    where room_id = target_room and person_id = (select public.current_user_id())
  );
$$;

grant execute on function public.is_member(uuid) to authenticated, anon;

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
  if inv.id is null or inv.expires_at < now() then
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

-- 4. Update RLS policies to use (select public.current_user_id())

-- profiles
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select
  using (
    id = (select public.current_user_id())
    or exists (
      select 1
      from memberships mine
      join memberships theirs on mine.room_id = theirs.room_id
      where mine.person_id = (select public.current_user_id()) and theirs.person_id = profiles.id
    )
  );

drop policy if exists profiles_insert on public.profiles;
create policy profiles_insert on public.profiles for insert
  with check (id = (select public.current_user_id()));

drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles for update
  using (id = (select public.current_user_id()));

drop policy if exists profiles_delete on public.profiles;
create policy profiles_delete on public.profiles for delete
  using (id = (select public.current_user_id()));

-- rooms
drop policy if exists rooms_insert on public.rooms;
create policy rooms_insert on public.rooms for insert
  with check ((select public.current_user_id()) is not null);

-- memberships
drop policy if exists memberships_select on public.memberships;
create policy memberships_select on public.memberships for select
  using (person_id = (select public.current_user_id()) or public.is_member(room_id));

drop policy if exists memberships_insert on public.memberships;
create policy memberships_insert on public.memberships for insert
  with check (
    person_id = (select public.current_user_id())
    and not exists (select 1 from memberships m where m.room_id = memberships.room_id)
  );

drop policy if exists memberships_update on public.memberships;
create policy memberships_update on public.memberships for update
  using (person_id = (select public.current_user_id()));

drop policy if exists memberships_delete on public.memberships;
create policy memberships_delete on public.memberships for delete
  using (person_id = (select public.current_user_id()));

-- fuel_events
drop policy if exists fuel_insert on public.fuel_events;
create policy fuel_insert on public.fuel_events for insert
  with check (
    person_id = (select public.current_user_id())
    and public.is_member(public.reading_room(reading_id))
    and not public.room_is_paused(public.reading_room(reading_id))
  );

-- notes
drop policy if exists notes_insert on public.notes;
create policy notes_insert on public.notes for insert
  with check (
    author_id = (select public.current_user_id())
    and public.is_member(public.reading_room(reading_id))
    and not public.room_is_paused(public.reading_room(reading_id))
  );

drop policy if exists notes_update on public.notes;
create policy notes_update on public.notes for update
  using (author_id = (select public.current_user_id()));

drop policy if exists notes_delete on public.notes;
create policy notes_delete on public.notes for delete
  using (author_id = (select public.current_user_id()));

-- note_founds
drop policy if exists note_founds_select on public.note_founds;
create policy note_founds_select on public.note_founds for select
  using (person_id = (select public.current_user_id()));

drop policy if exists note_founds_insert on public.note_founds;
create policy note_founds_insert on public.note_founds for insert
  with check (person_id = (select public.current_user_id()));

-- highlights
drop policy if exists highlights_insert on public.highlights;
create policy highlights_insert on public.highlights for insert
  with check (
    author_id = (select public.current_user_id())
    and public.is_member(public.reading_room(reading_id))
    and not public.room_is_paused(public.reading_room(reading_id))
  );

drop policy if exists highlights_delete on public.highlights;
create policy highlights_delete on public.highlights for delete
  using (author_id = (select public.current_user_id()));

-- card_answers
drop policy if exists card_answers_select on public.card_answers;
create policy card_answers_select on public.card_answers for select
  using (
    person_id = (select public.current_user_id())
    or exists (select 1 from cards c where c.id = card_id and c.state = 'open')
  );

drop policy if exists card_answers_insert on public.card_answers;
create policy card_answers_insert on public.card_answers for insert
  with check (person_id = (select public.current_user_id()));

drop policy if exists card_answers_update on public.card_answers;
create policy card_answers_update on public.card_answers for update
  using (person_id = (select public.current_user_id()));

-- quiet_days
drop policy if exists quiet_days_insert on public.quiet_days;
create policy quiet_days_insert on public.quiet_days for insert
  with check (person_id = (select public.current_user_id()) and public.is_member(room_id));

drop policy if exists quiet_days_update on public.quiet_days;
create policy quiet_days_update on public.quiet_days for update
  using (person_id = (select public.current_user_id()));

-- invites
drop policy if exists invites_insert on public.invites;
create policy invites_insert on public.invites for insert
  with check (created_by = (select public.current_user_id()) and public.is_member(room_id));

drop policy if exists invites_update on public.invites;
create policy invites_update on public.invites for update
  using (created_by = (select public.current_user_id()));

-- positions
drop policy if exists positions_upsert on public.positions;
create policy positions_upsert on public.positions for insert
  with check (person_id = (select public.current_user_id()));

drop policy if exists positions_update on public.positions;
create policy positions_update on public.positions for update
  using (person_id = (select public.current_user_id()));

-- last_read
drop policy if exists last_read_insert on public.last_read;
create policy last_read_insert on public.last_read for insert
  with check (person_id = (select public.current_user_id()) and public.is_member(room_id));

drop policy if exists last_read_update on public.last_read;
create policy last_read_update on public.last_read for update
  using (person_id = (select public.current_user_id()));

-- room_inks (ink memory that outlives membership; created if not already present)
create table if not exists public.room_inks (
  room_id uuid not null references public.rooms (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  ink text not null check (
    ink in ('crimson','clay','ochre','moss','teal','indigo','plum','rose')),
  chosen_at timestamptz not null default now(),
  primary key (room_id, person_id)
);

alter table public.room_inks enable row level security;
grant select, insert, update, delete on public.room_inks to authenticated;
create index if not exists room_inks_person_idx on public.room_inks (person_id);

drop policy if exists room_inks_own on public.room_inks;
create policy room_inks_own on public.room_inks for all
  using (person_id = (select public.current_user_id()))
  with check (person_id = (select public.current_user_id()));

-- portraits (storage.objects)
do $$
begin
  if exists (select 1 from pg_tables where schemaname = 'storage' and tablename = 'objects') then
    drop policy if exists portraits_read on storage.objects;
    create policy portraits_read on storage.objects for select
      using (
        bucket_id = 'portraits'
        and (
          split_part(name, '.', 1) = (select public.current_user_id())::text
          or exists (
            select 1
            from public.memberships mine
            join public.memberships theirs on mine.room_id = theirs.room_id
            where mine.person_id = (select public.current_user_id())
              and theirs.person_id::text = split_part(name, '.', 1)
          )
        )
      );

    drop policy if exists portraits_write on storage.objects;
    create policy portraits_write on storage.objects for insert
      with check (
        bucket_id = 'portraits'
        and split_part(name, '.', 1) = (select public.current_user_id())::text
      );

    drop policy if exists portraits_update on storage.objects;
    create policy portraits_update on storage.objects for update
      using (
        bucket_id = 'portraits'
        and split_part(name, '.', 1) = (select public.current_user_id())::text
      );

    drop policy if exists portraits_delete on storage.objects;
    create policy portraits_delete on storage.objects for delete
      using (
        bucket_id = 'portraits'
        and split_part(name, '.', 1) = (select public.current_user_id())::text
      );
  end if;
end $$;
