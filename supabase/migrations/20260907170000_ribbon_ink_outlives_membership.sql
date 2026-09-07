-- §6.10, "lost access entirely": the room's other members can re-invite you
-- "into the same membership so your ink and your notes reattach rather than
-- duplicating."
--
-- Notes already do. They are keyed by the author's id, which is the account's
-- id, so a note written before you left is still yours when you come back.
--
-- Ink did not, and could not. Ink lives on the membership row on purpose —
-- you are teal in one room and ochre in another (§4.5) — and leaving a room
-- deletes that row. Re-invited, you were seated with no colour, and in a room
-- of three or more, where ink *is* identity, that is a stranger arriving
-- rather than the same person coming back.
--
-- So the choice outlives the membership. One row per person per room, written
-- when an ink is picked, read when a membership is made again. It is not a
-- second source of truth for the *current* ink: the membership stays the
-- truth, and every screen keeps reading it. This is only the memory of what
-- you chose, so that coming back can put it on again.

create table public.room_inks (
  room_id uuid not null references public.rooms (id) on delete cascade,
  -- On profiles, not on memberships: the row's whole purpose is to be here
  -- when the membership is not. It goes when the account does.
  person_id uuid not null references public.profiles (id) on delete cascade,
  ink text not null check (
    ink in ('crimson','clay','ochre','moss','teal','indigo','plum','rose')),
  chosen_at timestamptz not null default now(),
  primary key (room_id, person_id)
);

alter table public.room_inks enable row level security;

-- Your own, and nobody else's — in either direction. Which ink a person once
-- chose in a room they have left is not a thing the room gets to know, and
-- the current ink of everyone present is already on their membership, where
-- the room can see it. No count, no history, no "was".
create policy room_inks_own on public.room_inks for all
  using (person_id = auth.uid())
  with check (person_id = auth.uid());

grant select, insert, update, delete on public.room_inks to authenticated;

create index room_inks_person_idx on public.room_inks (person_id);
