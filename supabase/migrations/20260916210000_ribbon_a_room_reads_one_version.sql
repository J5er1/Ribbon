-- A room reads one version.
--
-- This reverses §2.6 — "Translation is a personal setting, not a room
-- setting" — on the owner's instruction. Deviation A42 carries the argument;
-- the short of it is that §2.6's own reasoning is "notes pin to verse
-- addresses, not to text offsets", and the mark on a *phrase* added by A41g
-- is a text offset. One version per room is what makes that mark mean the
-- same words in both people's hands.
--
--   rooms.translation      what everyone in this room reads
--   readings.translation   what this book was read in
--
-- **Two columns rather than one**, because S11 calls a finished reading
-- immutable and "the source of the printed keepsake". An open book follows
-- the room; a finished one keeps the words it was read in, so a room that
-- changes version next year does not silently re-word a book it has already
-- read, under notes that were left about those exact words.
--
-- **`profiles.translation` is deliberately left alone.** iOS still reads it
-- and §2.6 is still true over there until somebody takes that pass — this is
-- an Android-only change to a shared backend, and the honest shape of that is
-- an added column, not a moved one. Both platforms keep working on one
-- account, each right about itself. When iOS follows, the profile column can
-- go; until then, dropping it would break a shipped client to tidy a schema.
--
-- Nullable, with no default, for the usual reason: a row written by a client
-- that predates this migration says nothing about version, and "says nothing"
-- has to be distinguishable from "says Berean". The app reads a null as the
-- launch translation, which is what those rooms have always been reading.

alter table public.rooms
  add column if not exists translation text;

alter table public.readings
  add column if not exists translation text;

-- The registry stores a raw key and never enumerates (§16.8, and the
-- open-translation-registry migration), so there is deliberately no check
-- constraint listing the versions: adding NKJV is a registry edit, not a
-- migration. What is worth rejecting is the empty string, which is a bug
-- rather than a choice — a translation nobody can look up, that is also not
-- null and so does not fall back.
alter table public.rooms
  drop constraint if exists rooms_translation_is_a_key;
alter table public.rooms
  add constraint rooms_translation_is_a_key
  check (translation is null or length(translation) > 0);

alter table public.readings
  drop constraint if exists readings_translation_is_a_key;
alter table public.readings
  add constraint readings_translation_is_a_key
  check (translation is null or length(translation) > 0);
