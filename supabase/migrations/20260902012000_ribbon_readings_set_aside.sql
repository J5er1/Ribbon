-- A room has one open reading at a time (§03). Picking another book while
-- one is still going sets the first aside (§6.6): it keeps its notes and
-- comes back from the chooser; it is not an ember. The moment rides the
-- reading row so both phones show the same open book.

alter table public.readings
  add column if not exists set_aside_at timestamptz;
