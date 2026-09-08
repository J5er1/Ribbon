-- Ribbon — Support for Auth0 as Third-Party Auth Provider.
--
-- Background:
-- When using Auth0 as an external identity provider:
-- 1. Users authenticate via Auth0 and send an Auth0-signed ID token to Supabase.
-- 2. Auth0 users do not pre-exist in Supabase's local auth.users table.
--    Dropping the foreign key constraint profiles_id_fkey allows profiles
--    to be created for Auth0 users while preserving all foreign keys within
--    the public schema (memberships, readings, notes, etc.).
-- 3. Auth0 'sub' claims are strings (e.g. "auth0|64f2..." or "google-oauth2|...").
--    The default auth.uid() in Supabase casts the sub claim directly to uuid,
--    which fails on non-UUID strings.
--    This migration ensures auth.uid() safely reads the 'user_uuid' claim
--    injected by our Auth0 Post-Login Action, or falls back to a deterministic
--    UUIDv5 generated from the Auth0 subject string.

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

-- 3. Override auth.uid() so all existing policies (is_member, profiles, storage, etc.)
-- work seamlessly with Auth0 tokens without needing to rewrite every single policy.
create or replace function auth.uid()
returns uuid
language sql stable
as $$
  select public.current_user_id();
$$;
