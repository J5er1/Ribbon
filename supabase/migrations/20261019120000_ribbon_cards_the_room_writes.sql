-- Cards the room writes (docs/deviations.md A74; build book §4.6, §16.4).
--
-- Two things, the first of which the second needs.
--
-- **A card opens on the server.** §4.6: "When the last answer lands, the
-- card turns over … and everyone's answers are there together." Until now
-- a phone decided that, from the answers it could see — and
-- card_answers_select shows nobody a sealed answer but their own, as it
-- must. So no phone could ever see everyone's answers, and in a room of
-- two or more no card ever opened. The moment the last answer lands is now
-- the server's to see: a trigger on card_answers turns the card over, and
-- the push that says "The cards are open" follows from that as before.
--
-- **The room may write a card.** §16.4 left open who writes the cards:
-- Ribbon, the room, or a mix. The owner chose the room. Ribbon still
-- writes one card a chapter; anyone in the room may ask one more, of their
-- own, beside it — "one or two per chapter", as §4.6 has it. It is sealed
-- and opens as Ribbon's does, and says whose question it is. It is never
-- edited, so nobody's answer is ever under a question that changed; it
-- can be set down like any card, and then someone may ask another.
--
-- Nothing here is removed, and every function is created or replaced.

-- ----------------------------------------------------------- the columns

-- Who asked it, as an id and no more: the card keeps its writer's name
-- only as long as the profile does, and says "Someone" after, as every
-- answer from a deleted account already does.
alter table public.cards
  add column if not exists by_the_room boolean not null default false,
  add column if not exists written_by uuid;

-- A question is a sentence or two. Ribbon's are well inside this.
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'cards_question_is_a_question') then
    alter table public.cards add constraint cards_question_is_a_question
      check (char_length(btrim(question)) between 1 and 280);
  end if;
end $$;

-- One card from Ribbon a chapter. Phones that know this make it with the
-- same id (ReflectionCard.ribbonCardID), so two of them reaching the end
-- of a chapter at once make one card, not two whose answers never meet.
create unique index if not exists cards_one_from_ribbon
  on public.cards (reading_id, chapter) where not by_the_room;

-- And one of the room's own a chapter, while it stands.
create unique index if not exists cards_one_from_the_room
  on public.cards (reading_id, chapter) where by_the_room and state <> 'set_down';

-- --------------------------------------------------------- who may write

-- A card is Ribbon's (nobody's name on it) or the room's (the writer's own
-- name on it, and nobody else's).
alter policy cards_insert on public.cards
  with check (
    public.is_member(public.reading_room(reading_id))
    and not public.room_is_paused(public.reading_room(reading_id))
    and (
      (not by_the_room and written_by is null)
      or (by_the_room and written_by = (select public.current_user_id()))
    )
  );

-- A card keeps its word. A signed-in request may set a sealed card down
-- and do nothing else to it: not open it, not seal it again, not change
-- its question or whose it is. Phones push the whole card when they push
-- an answer, and a shipped build pushes it sealed — so this keeps what was
-- there rather than refusing, or the answer behind it would be refused
-- too. A card arrives sealed. The server opens it (open_card_if_answered).
create or replace function public.cards_keep_their_word() returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user not in ('authenticated', 'anon') then
    return new;
  end if;
  if tg_op = 'INSERT' then
    new.state := 'sealed';
    new.opened_at := null;
    return new;
  end if;
  new.reading_id := old.reading_id;
  new.chapter := old.chapter;
  new.question := old.question;
  new.by_the_room := old.by_the_room;
  new.written_by := old.written_by;
  new.opened_at := old.opened_at;
  if not (old.state = 'sealed' and new.state = 'set_down') then
    new.state := old.state;
  end if;
  return new;
end;
$$;

create or replace trigger cards_keep_their_word before insert or update on public.cards
  for each row execute function public.cards_keep_their_word();

-- An answer can be given and changed "right up until the card opens"
-- (§4.6), and not after; nor to a card that has been set down. As above,
-- quietly: a phone saying an answer again after its card opened keeps the
-- answer that opened it, and a new answer to a closed card is not kept.
create or replace function public.answers_until_it_opens() returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  if exists (select 1 from cards where id = new.card_id and state = 'sealed') then
    return new;
  end if;
  if tg_op = 'INSERT' then
    return null;
  end if;
  new.body := old.body;
  new.answered_at := old.answered_at;
  return new;
end;
$$;

revoke execute on function public.answers_until_it_opens() from public, anon, authenticated;

create or replace trigger card_answers_until_it_opens before insert or update on public.card_answers
  for each row execute function public.answers_until_it_opens();

-- ------------------------------------------------------------- opening

-- Turns a sealed card over once everyone in its room has answered it.
-- Runs as its owner: it reads answers nobody signed in may read yet, and
-- says nothing about them but whether the card is open.
create or replace function public.open_card_if_answered(target_card uuid) returns void
language plpgsql security definer
set search_path = public
as $$
declare
  room uuid;
begin
  select public.reading_room(c.reading_id) into room
  from cards c where c.id = target_card and c.state = 'sealed'
  for update;
  if room is null then
    return;
  end if;
  if exists (select 1 from memberships m where m.room_id = room)
     and not exists (
       select 1 from memberships m
       where m.room_id = room
         and not exists (
           select 1 from card_answers a where a.card_id = target_card and a.person_id = m.person_id))
  then
    update cards set state = 'open', opened_at = now()
    where id = target_card and state = 'sealed';
  end if;
end;
$$;

revoke execute on function public.open_card_if_answered(uuid) from public, anon, authenticated;

create or replace function public.card_answered() returns trigger
language plpgsql security definer
set search_path = public
as $$
begin
  perform public.open_card_if_answered(new.card_id);
  return null;
end;
$$;

revoke execute on function public.card_answered() from public, anon, authenticated;

create or replace trigger card_answers_open_the_card after insert or update on public.card_answers
  for each row execute function public.card_answered();

-- --------------------------------------------- when someone leaves

-- Someone leaving can leave everyone else answered: the cards they were
-- waiting on open then, as if the last answer had just landed.
create or replace function public.cards_after_someone_left() returns trigger
language plpgsql security definer
set search_path = public
as $$
declare
  waiting uuid;
begin
  for waiting in
    select c.id from cards c join readings r on r.id = c.reading_id
    where r.room_id = old.room_id and c.state = 'sealed'
  loop
    perform public.open_card_if_answered(waiting);
  end loop;
  return null;
end;
$$;

revoke execute on function public.cards_after_someone_left() from public, anon, authenticated;

create or replace trigger memberships_cards_after_someone_left after delete on public.memberships
  for each row execute function public.cards_after_someone_left();
