-- 0004_multiplayer.sql
-- Realtime multiplayer: private rooms with codes, presence via Realtime, and
-- server-validated match results. Scores and winners are decided here, never by
-- the client. Matchmaking is left to the client (it lists open rooms and joins),
-- which keeps everything inside the free tier.

alter table public.room_players add column if not exists submitted_at timestamptz;

-- ---------------------------------------------------------------- create room
create or replace function public.create_room(p_mode text default 'MATH_SPRINT')
returns public.rooms
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_code text;
    v_room public.rooms;
    i int;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;
    if p_mode not in ('MATH_SPRINT','CROSS_MATH','MAZE','PATTERN','MEMORY','LOGIC','CLASSIC','REFLEX') then
        raise exception 'unknown mode %', p_mode;
    end if;

    for i in 1..20 loop
        v_code := upper(substr(encode(gen_random_bytes(6), 'hex'), 1, 6));
        begin
            insert into public.rooms (code, host_id, mode, status)
            values (v_code, v_uid, p_mode, 'open')
            returning * into v_room;
            exit;
        exception when unique_violation then
            v_room := null;
        end;
    end loop;

    if v_room is null then raise exception 'could not allocate a room code'; end if;

    insert into public.room_players (room_id, user_id, ready)
    values (v_room.id, v_uid, true)
    on conflict do nothing;

    return v_room;
end;
$$;

-- ----------------------------------------------------------------- join room
create or replace function public.join_room(p_code text)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_room public.rooms;
    v_count int;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    select * into v_room from public.rooms where code = upper(trim(p_code));
    if v_room is null then raise exception 'room not found'; end if;
    if v_room.status in ('finished', 'abandoned') then raise exception 'room is closed'; end if;

    select count(*) into v_count from public.room_players where room_id = v_room.id;
    if not exists (select 1 from public.room_players where room_id = v_room.id and user_id = v_uid)
       and v_count >= 2 then
        raise exception 'room is full';
    end if;

    insert into public.room_players (room_id, user_id)
    values (v_room.id, v_uid)
    on conflict do nothing;

    return jsonb_build_object('room_id', v_room.id, 'code', v_room.code, 'mode', v_room.mode, 'status', v_room.status);
end;
$$;

create or replace function public.leave_room(p_room_id uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not authenticated'; end if;
    delete from public.room_players where room_id = p_room_id and user_id = v_uid;
    if not exists (select 1 from public.room_players where room_id = p_room_id) then
        update public.rooms set status = 'abandoned' where id = p_room_id;
    end if;
end;
$$;

create or replace function public.set_room_ready(p_room_id uuid, p_ready boolean)
returns void
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_count int;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    update public.room_players set ready = p_ready
        where room_id = p_room_id and user_id = v_uid;

    -- Start the match once two players are present and both are ready.
    select count(*) into v_count from public.room_players where room_id = p_room_id and ready;
    if v_count >= 2 then
        update public.rooms set status = 'playing'
            where id = p_room_id and status = 'open';
    end if;
end;
$$;

-- ------------------------------------------------------ submit a duel score
-- The client sends raw counts; the score is computed here.
create or replace function public.submit_room_score(
    p_room_id      uuid,
    p_correct      int,
    p_total        int,
    p_best_streak  int
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_score int;
    v_room public.rooms;
    v_players int;
    v_submitted int;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;
    if p_total < 0 or p_total > 100 or p_correct < 0 or p_correct > p_total then
        raise exception 'invalid score payload';
    end if;
    if not exists (select 1 from public.room_players where room_id = p_room_id and user_id = v_uid) then
        raise exception 'not a member of this room';
    end if;

    v_score := greatest(0, p_correct * 100 + p_best_streak * 25 - (p_total - p_correct) * 20);

    update public.room_players
        set score = greatest(score, v_score), submitted_at = now()
        where room_id = p_room_id and user_id = v_uid;

    select * into v_room from public.rooms where id = p_room_id;
    select count(*) into v_players from public.room_players where room_id = p_room_id;
    select count(*) into v_submitted from public.room_players where room_id = p_room_id and submitted_at is not null;

    if v_room.status = 'playing' and v_players >= 2 and v_submitted >= 2 then
        perform public.finish_match(p_room_id);
    end if;

    return jsonb_build_object('ok', true, 'score', v_score);
end;
$$;

-- ------------------------------------------------------------- finish match
create or replace function public.finish_match(p_room_id uuid)
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_room public.rooms;
    v_a record;
    v_b record;
    v_winner uuid;
    v_skill text;
    v_ra int;
    v_rb int;
    v_new_a int;
    v_new_b int;
    v_match_id uuid;
begin
    select * into v_room from public.rooms where id = p_room_id;
    if v_room is null then raise exception 'room not found'; end if;
    if v_room.status = 'finished' then
        return jsonb_build_object('duplicate', true);
    end if;

    select * into v_a from public.room_players
        where room_id = p_room_id order by joined_at asc limit 1;
    select * into v_b from public.room_players
        where room_id = p_room_id order by joined_at asc offset 1 limit 1;
    if v_a is null or v_b is null then raise exception 'need two players'; end if;
    if v_a.submitted_at is null or v_b.submitted_at is null then raise exception 'both players must finish'; end if;

    v_winner := case
        when v_a.score > v_b.score then v_a.user_id
        when v_b.score > v_a.score then v_b.user_id
        else null
    end;

    insert into public.match_history (room_id, mode, player_a, player_b, score_a, score_b, winner)
    values (p_room_id, v_room.mode, v_a.user_id, v_b.user_id, v_a.score, v_b.score, v_winner)
    returning id into v_match_id;

    -- Elo between the two players for this mode's skill.
    v_skill := public.skill_for_mode(v_room.mode);
    insert into public.player_ratings (user_id, skill) values (v_a.user_id, v_skill), (v_b.user_id, v_skill)
        on conflict do nothing;
    select rating into v_ra from public.player_ratings where user_id = v_a.user_id and skill = v_skill;
    select rating into v_rb from public.player_ratings where user_id = v_b.user_id and skill = v_skill;

    v_new_a := public.elo_update(v_ra, case when v_winner = v_a.user_id then 1.0 when v_winner is null then 0.5 else 0.0 end);
    v_new_b := public.elo_update(v_rb, case when v_winner = v_b.user_id then 1.0 when v_winner is null then 0.5 else 0.0 end);

    update public.player_ratings set rating = v_new_a, peak = greatest(peak, v_new_a)
        where user_id = v_a.user_id and skill = v_skill;
    update public.player_ratings set rating = v_new_b, peak = greatest(peak, v_new_b)
        where user_id = v_b.user_id and skill = v_skill;

    update public.player_progress set wins = wins + 1 where user_id = v_winner;
    update public.player_progress set losses = losses + 1
        where user_id in (v_a.user_id, v_b.user_id) and user_id <> coalesce(v_winner, '00000000-0000-0000-0000-000000000000'::uuid);

    update public.rooms set status = 'finished' where id = p_room_id;

    return jsonb_build_object(
        'match_id', v_match_id, 'winner', v_winner,
        'score_a', v_a.score, 'score_b', v_b.score,
        'rating_a', v_new_a, 'rating_b', v_new_b
    );
end;
$$;

-- ------------------------------------------------------------ realtime feed
do $$
begin
    begin
        alter publication supabase_realtime add table public.rooms;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.room_players;
    exception when duplicate_object then null;
    end;
    begin
        alter publication supabase_realtime add table public.match_history;
    exception when duplicate_object then null;
    end;
end;
$$;

alter table public.room_players replica identity full;
alter table public.rooms replica identity full;

-- ------------------------------------------------------------------- grants
revoke all on function public.create_room(text) from public;
revoke all on function public.join_room(text) from public;
revoke all on function public.leave_room(uuid) from public;
revoke all on function public.set_room_ready(uuid, boolean) from public;
revoke all on function public.submit_room_score(uuid, int, int, int) from public;
revoke all on function public.finish_match(uuid) from public;

grant execute on function public.create_room(text) to authenticated;
grant execute on function public.join_room(text) to authenticated;
grant execute on function public.leave_room(uuid) to authenticated;
grant execute on function public.set_room_ready(uuid, boolean) to authenticated;
grant execute on function public.submit_room_score(uuid, int, int, int) to authenticated;
grant execute on function public.finish_match(uuid) to authenticated;
