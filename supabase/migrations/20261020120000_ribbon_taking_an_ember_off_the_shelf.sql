-- Taking an ember off the shelf (iOS ledger I47).
--
-- The owner asked for "a way to remove embers because the old ones were
-- messed up", for the whole room, with a confirmation. A finished book can
-- now be deleted by any member of its room; the cascade already on every
-- table that names a reading takes its fire, fuel, notes, marks, places,
-- ribbon and cards with it. An open book cannot be deleted: only one that
-- has been put on the shelf.
--
-- Voice notes' recordings in storage are not removed by the cascade; they
-- are no longer reachable from any row.

create policy readings_delete on public.readings for delete
  using (public.is_member(room_id) and finished_at is not null);
