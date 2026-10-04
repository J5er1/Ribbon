-- A mark follows its words.
--
-- A mark on a phrase (A41g) was stored as two offsets into one version's
-- text, and an offset into one version is nonsense in another. A42 answered
-- that by giving each room one version, which reversed §2.6 — "Translation is
-- a personal setting, not a room setting". This migration is the other half
-- of reversing A42 (deviation A60): a mark now also names the *original*
-- words it covers — the Hebrew, Aramaic or Greek under the English — and
-- every reader sees it on whatever their own version says for those words.
-- So everybody can read their own version again, and a mark still lands on
-- the same words in each person's hands.
--
-- Three nullable columns, and the nulls are the point. A mark on whole verses
-- writes none of them. A phrase mark made before this migration writes none
-- of them either, and still follows its words: a client derives them from
-- its offsets and the version they were measured in. Nothing that does not
-- know about original words has to learn.
--
--   start_words   positions of the original words the mark covers in
--                 start_verse, 0-based, in original order; null when the
--                 start is the whole verse or nothing could be matched
--   end_words     the same for end_verse; always null for a mark inside one
--                 verse, which keeps its whole set in start_words
--   words_source  which numbering those positions count in — a key the
--                 bundled original text carries, so a re-numbered text can
--                 tell an old anchor from a new one and fall back honestly
--
-- **A set, not a range.** An English phrase can render original words out of
-- their order — "In the beginning God created" is Hebrew words 0, 2, 1 — so
-- a contiguous English selection is a scattering of positions underneath.
--
-- **The offsets stay.** start_char, end_char and char_translation keep
-- meaning exactly what they meant: the author's phrase in the author's
-- version. A client that predates this migration still shows the phrase to a
-- reader on that version and the whole verse to anyone else, which is the
-- honest fallback it always had.
--
-- **rooms.translation and readings.translation stay too.** Nothing on a
-- current client reads them for display any more, but shipped clients write
-- them, and PostgREST rejects a write that names a column that is gone.
-- Dropping them would break a client in someone's pocket to tidy a schema.
--
-- **No policy changes.** Insert and select already cover new columns, and
-- there is no update policy: the words are worked out at the moment the mark
-- is made and never added later.

alter table public.highlights
  add column if not exists start_words int[],
  add column if not exists end_words int[],
  add column if not exists words_source text;

-- Positions without their source are numbers nobody can resolve, the same
-- reasoning that ties char offsets to their translation.
alter table public.highlights
  drop constraint if exists highlights_words_name_their_source;
alter table public.highlights
  add constraint highlights_words_name_their_source
  check (
    (start_words is null and end_words is null)
    or words_source is not null
  );

-- A position is an index into a verse's list of words, so it is a whole
-- number at least zero. An empty set is written as null rather than as an
-- empty array, so there is one way to say "no words". The ceiling is far above
-- the longest verse (Esther 8:9) and only there to refuse nonsense.
alter table public.highlights
  drop constraint if exists highlights_words_are_positions;
alter table public.highlights
  add constraint highlights_words_are_positions
  check (
    (start_words is null or (
      cardinality(start_words) between 1 and 512
      and 0 <= all(start_words)
      and array_position(start_words, null) is null
    ))
    and (end_words is null or (
      cardinality(end_words) between 1 and 512
      and 0 <= all(end_words)
      and array_position(end_words, null) is null
    ))
  );

-- Inside one verse there is one set of words. Two would be two answers to the
-- same question, so the client folds them together before it writes.
alter table public.highlights
  drop constraint if exists highlights_a_verse_keeps_one_set;
alter table public.highlights
  add constraint highlights_a_verse_keeps_one_set
  check (start_verse <> end_verse or end_words is null);

-- The source is a key, not prose. The registry of sources is the bundled
-- text, not this table, so there is deliberately no list here; what is worth
-- rejecting is the empty string and anything too long to be a key.
alter table public.highlights
  drop constraint if exists highlights_words_source_is_a_key;
alter table public.highlights
  add constraint highlights_words_source_is_a_key
  check (words_source is null or length(words_source) between 1 and 64);
