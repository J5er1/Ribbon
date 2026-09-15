-- Ribbon — the room channel, and the invites a room can see.
--
-- Two things this build needed and did not have:
--
-- 1. A *private* Realtime channel per room. Both clients already speak
--    Phoenix to `realtime:room:<room id>`, but they joined it with nothing
--    but the publishable key — which means the channel was open to anyone
--    holding a key that ships inside the app, and a room's presence and its
--    "thinking of you" taps were readable by a stranger who guessed a room
--    id. Presence is the most intimate signal the product has (§4.2); it
--    cannot be the one thing RLS does not cover.
--
--    Supabase's Realtime Authorization checks `realtime.messages` with the
--    caller's own JWT, so the same membership rule every table already uses
--    governs the socket too. The clients now join with `private: true` and
--    the account's access token; a channel whose room they are not in is
--    refused by the server, not by politeness.
--
-- 2. Invites in the pull. `invites` was written and never read back, so a
--    person handing out the link from a second device minted a second row
--    rather than re-offering the live one. The link is the whole mechanism
--    (S15) and there should be one of it per room.

-- ----------------------------------------------------- the topic's room

-- `realtime:room:<uuid>` reaches the policy below as `room:<uuid>`. Anything
-- else — another topic shape, a malformed id — is null, and `is_member(null)`
-- is false, which is the refusal we want.
create or replace function public.topic_room_id(topic text)
returns uuid
language sql immutable
as $$
  select case
    when topic ~ '^room:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'
    then substring(topic from 6)::uuid
  end;
$$;

-- Its body is pg_catalog only (a regex, a substring, a cast), so the empty
-- path is safe and a caller's search_path cannot sway it.
alter function public.topic_room_id(text) set search_path = '';

revoke execute on function public.topic_room_id(text) from public, anon;
grant execute on function public.topic_room_id(text) to authenticated;

-- --------------------------------------------- the channel's own policies

-- Guarded: `realtime.messages` belongs to the Realtime extension, and a
-- local or older project may not have it yet. A project without it keeps
-- working — the clients fall back to a public join (see RoomChannel on both
-- platforms) — and the day the extension is there, this migration is what
-- closes the channel.
do $$
begin
  if not exists (
    select 1 from pg_tables
    where schemaname = 'realtime' and tablename = 'messages'
  ) then
    raise notice 'realtime.messages not present; room channels stay public until it is';
    return;
  end if;

  execute 'alter table realtime.messages enable row level security';

  execute 'drop policy if exists ribbon_room_channel_read on realtime.messages';
  execute $p$
    create policy ribbon_room_channel_read on realtime.messages
      for select to authenticated
      using (public.is_member(public.topic_room_id(realtime.topic())))
  $p$;

  execute 'drop policy if exists ribbon_room_channel_write on realtime.messages';
  execute $p$
    create policy ribbon_room_channel_write on realtime.messages
      for insert to authenticated
      with check (public.is_member(public.topic_room_id(realtime.topic())))
  $p$;
exception when others then
  -- Never fail the migration over the socket's policies: every table's own
  -- RLS is what protects the content, and a channel that cannot be closed
  -- here is a channel the clients decline to trust (they join public and
  -- carry no more than a name and an address, exactly as before).
  raise notice 'realtime.messages policies not applied: %', sqlerrm;
end;
$$;

-- --------------------------------------------------------------- invites

-- The pull reads a room's live invites, so `invites_select` (members only,
-- from the init migration) is already the right rule. It needs the index
-- the other room-scoped tables have.
create index if not exists invites_room_idx on public.invites (room_id, expires_at);
