-- The ribbon: one place in the book, kept by the room.
--
-- §03 says position is per-person and there is no shared "where we are",
-- and that stays true: public.positions is untouched and the reader still
-- opens the book at their own. What this adds is the object the app is named
-- after — the ribbon in a shared Bible, left where somebody stopped, which
-- the next person can take or flip straight past.
--
-- One row per reading, not one per person. That is the whole design: a
-- per-person ribbon would be a leaderboard with the numbers taken out, and
-- a single shared address has nothing to compare.

create table public.ribbons (
  reading_id uuid primary key references public.readings (id) on delete cascade,
  person_id uuid not null references public.profiles (id) on delete cascade,
  chapter int not null,
  verse int not null,
  placed_at timestamptz not null default now()
);

alter table public.ribbons enable row level security;

-- Anybody in the room may move it, which is the difference from positions:
-- a position is yours to write, the ribbon is the room's. Leaving it
-- somewhere is an act of care performed in public (§4.7's shape), so the
-- room sees who left it and when.
create policy ribbons_select on public.ribbons for select
  using (public.is_member(public.reading_room(reading_id)));
create policy ribbons_insert on public.ribbons for insert
  with check (
    person_id = auth.uid()
    and public.is_member(public.reading_room(reading_id))
  );
create policy ribbons_update on public.ribbons for update
  using (public.is_member(public.reading_room(reading_id)))
  with check (person_id = auth.uid());
