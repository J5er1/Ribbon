-- Translations are an app-side registry now (bsb/web bundled; NKJV and two
-- undecided versions arriving via API.Bible — build book §16.8). The
-- database stores the raw key and stops enumerating: adding a translation
-- must never take a migration.

alter table public.profiles drop constraint if exists profiles_translation_check;
alter table public.profiles
  add constraint profiles_translation_shape check (translation ~ '^[a-z0-9_-]{2,24}$');
