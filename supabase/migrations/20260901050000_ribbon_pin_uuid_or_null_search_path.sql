-- Advisor hygiene: pin the guard function's search_path. Its body uses
-- only pg_catalog (a regex match and a cast), so the empty path is safe
-- and the function can't be swayed by a caller's search_path.
alter function public.uuid_or_null(text) set search_path = '';
