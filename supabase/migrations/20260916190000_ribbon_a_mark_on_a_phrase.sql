-- A highlight can name part of a verse.
--
-- S06's *Extending* asks for handles that snap "to verse boundaries by default
-- and to word boundaries when dragged slowly", and the second half had nowhere
-- to live: `highlights` carried a start verse and an end verse and nothing
-- finer, so a mark on a phrase could be drawn on a phone and then had to be
-- stored as a mark on the whole verse. Which is to say it could not be made at
-- all.
--
-- Three nullable columns, and the nulls are the point. A mark on whole verses
-- — every mark made before this migration, and every mark iOS makes today —
-- writes none of them and reads back exactly as it always did. Nothing that
-- does not know about phrases has to learn.
--
--   start_char        offset into start_verse's own text, or null for its
--                     first letter
--   end_char          offset into end_verse's own text, or null for its last
--   char_translation  the translation those offsets were measured in
--
-- **Why the translation travels with the numbers.** Translation is a property
-- of a *person* (S20 puts it in Text settings), so two people in one room can
-- be reading different words for the same verse, and an offset into one is
-- nonsense in the other. A reader whose translation does not match is shown
-- the whole verse marked: it says truthfully that somebody marked something
-- here, which beats pointing at words that are not on their page and beats
-- hiding the mark. Without the key there is no way to tell the two cases
-- apart, so the offsets are only honoured when it is present.

alter table public.highlights
  add column if not exists start_char int,
  add column if not exists end_char int,
  add column if not exists char_translation text;

-- Offsets are positions in a string, so they cannot be negative, and a mark
-- that names a phrase has to say which words it means: an offset without its
-- translation is a number nobody can resolve.
alter table public.highlights
  drop constraint if exists highlights_chars_are_positions;
alter table public.highlights
  add constraint highlights_chars_are_positions
  check (
    (start_char is null or start_char >= 0)
    and (end_char is null or end_char >= 0)
  );

alter table public.highlights
  drop constraint if exists highlights_chars_name_their_translation;
alter table public.highlights
  add constraint highlights_chars_name_their_translation
  check (
    (start_char is null and end_char is null)
    or char_translation is not null
  );

-- A mark inside one verse cannot end before it starts. Across verses the two
-- offsets belong to different strings and have no order between them, so there
-- is nothing to compare — the check is deliberately limited to the one case
-- where a comparison means something.
alter table public.highlights
  drop constraint if exists highlights_chars_run_forwards;
alter table public.highlights
  add constraint highlights_chars_run_forwards
  check (
    start_verse <> end_verse
    or start_char is null
    or end_char is null
    or end_char >= start_char
  );
