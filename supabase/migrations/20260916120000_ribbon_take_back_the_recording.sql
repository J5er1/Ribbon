-- Taking back a voice note takes the recording with it.
--
-- S04 says a taken-back note "vanishes with no tombstone", and until now
-- that was only ever half true for a voice note. `takeBack` deleted the
-- local file and the `notes` row; the uploaded object at
-- `voice-notes/<reading_id>/<note_id>.m4a` stayed where it was, and the
-- voice-notes bucket had exactly two policies — `voice_notes_read` (select,
-- to any member of the room) and `voice_notes_write` (insert). There was no
-- delete policy at all, so the app could not have removed the object even
-- if it had asked.
--
-- What that meant in practice: a person records a thought, thinks better of
-- it, takes it back — and a recording of their voice stays on the server,
-- fetchable by everyone else in the room, permanently. It is the one place
-- in the product where undoing something leaves the most personal version
-- of it behind.
--
-- The portraits bucket already had its `portraits_delete` (deviation A-auth0,
-- 20260908000000); this is the same shape for the other bucket, with the
-- author check that a portrait does not need because a portrait's path *is*
-- the person.

-- The path is `<reading_id>/<note_id>.m4a`, which is what `voice_notes_read`
-- already parses with `split_part(name, '/', 1)`. The second segment is the
-- note, minus its extension.
create or replace function public.voice_note_id(object_name text)
returns uuid
language sql immutable
set search_path = public, extensions
as $$
  select public.uuid_or_null(split_part(split_part(object_name, '/', 2), '.', 1));
$$;

revoke execute on function public.voice_note_id(text) from anon, public;
grant execute on function public.voice_note_id(text) to authenticated;

-- Only the author, and only for a note they still own — which is the same
-- rule `notes_delete` keeps on the row itself. Deliberately *not* "any
-- member of the room": reading a note somebody left you does not entitle you
-- to erase their voice.
--
-- The object is deleted before the row (the app does them in that order), so
-- the note is still there to be checked against at the moment this policy
-- runs. If the row has already gone the object is orphaned rather than
-- unreachable, and the app's own prune sweeps the local copy either way.
drop policy if exists voice_notes_delete on storage.objects;
create policy voice_notes_delete on storage.objects for delete
  using (
    bucket_id = 'voice-notes'
    and exists (
      select 1
      from public.notes n
      where n.id = public.voice_note_id(name)
        and n.author_id = (select public.current_user_id())
    )
  );
