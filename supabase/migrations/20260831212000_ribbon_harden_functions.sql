-- Hardening per security advisor: SECURITY DEFINER functions should not be
-- callable more broadly than intended.
--
-- * The RLS helpers (is_member, reading_room, room_is_paused) exist for
--   policy evaluation, which runs them as the querying (authenticated)
--   user — so authenticated keeps EXECUTE, anon loses it.
-- * accept_invite requires a signed-in caller; anon loses EXECUTE.
-- * invite_preview stays anon-callable BY DESIGN: the invite screen (S16)
--   shows who is inviting before any account exists, and the token is an
--   unguessable capability that reveals only the inviter's name, the
--   room's name, and whether the invite still stands.

revoke execute on function public.is_member(uuid) from anon;
revoke execute on function public.reading_room(uuid) from anon;
revoke execute on function public.room_is_paused(uuid) from anon;
revoke execute on function public.accept_invite(uuid) from anon;

-- Belt and braces: strip PUBLIC grants so future roles don't inherit.
revoke execute on function public.is_member(uuid) from public;
revoke execute on function public.reading_room(uuid) from public;
revoke execute on function public.room_is_paused(uuid) from public;
revoke execute on function public.accept_invite(uuid) from public;
revoke execute on function public.invite_preview(uuid) from public;
grant execute on function public.invite_preview(uuid) to anon, authenticated;
