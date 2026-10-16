-- 0003_functions.sql
-- Server-side, trusted logic. Clients never write competitive data directly:
-- every function below re-derives XP, stars, ratings, achievements and leaderboard
-- entries from the raw session payload, using auth.uid() as the only identity.

-- ------------------------------------------------------------------ helpers
create or replace function public.current_week_key() returns text
language sql stable as $$
    select to_char(now(), 'IYYY-IW');
$$;

create or replace function public.tier_for(p_rating int) returns text
language sql immutable as $$
    select case
        when p_rating >= 1700 then 'Master'
        when p_rating >= 1550 then 'Diamond'
        when p_rating >= 1400 then 'Platinum'
        when p_rating >= 1250 then 'Gold'
        when p_rating >= 1100 then 'Silver'
        else 'Bronze'
    end;
$$;

-- Elo update against a fixed 1000 baseline; p_actual is 0..1 (accuracy or win/loss).
create or replace function public.elo_update(p_current int, p_actual double precision) returns int
language sql immutable as $$
    select greatest(100, least(3000,
        round(p_current + 32 * (p_actual - (1.0 / (1.0 + power(10, (1000 - p_current) / 400.0)))))::int));
$$;

create or replace function public.skill_for_mode(p_mode text) returns text
language sql immutable as $$
    select case p_mode
        when 'MATH_SPRINT' then 'math'
        when 'CROSS_MATH'  then 'math'
        when 'MAZE'        then 'logic'
        when 'PATTERN'     then 'logic'
        when 'LOGIC'       then 'logic'
        when 'MEMORY'      then 'memory'
        when 'CLASSIC'     then 'puzzle'
        when 'REFLEX'      then 'puzzle'
        else 'puzzle'
    end;
$$;

-- ------------------------------------------------------- award achievements
create or replace function public.award_achievements(p_user uuid)
returns void
language plpgsql security definer set search_path = public
as $$
declare
    v_games int; v_best_streak int; v_stars int; v_levels int;
    v_overall int; v_streak_days int;
begin
    select games_played, best_streak, streak_days into v_games, v_best_streak, v_streak_days
        from public.player_progress where user_id = p_user;
    select coalesce(sum(stars), 0), count(*) filter (where completed) into v_stars, v_levels
        from public.level_progress where user_id = p_user;
    select coalesce(round(avg(rating)), 1000) into v_overall
        from public.player_ratings where user_id = p_user;

    insert into public.player_achievements (user_id, achievement_id)
    select p_user, a.id from (
        values
            ('first_game',     v_games >= 1),
            ('ten_games',      v_games >= 10),
            ('fifty_games',    v_games >= 50),
            ('streak_10',      v_best_streak >= 10),
            ('streak_25',      v_best_streak >= 25),
            ('stars_30',       v_stars >= 30),
            ('level_10',       v_levels >= 10),
            ('level_50',       v_levels >= 50),
            ('level_100',      v_levels >= 100),
            ('gold_tier',      v_overall >= 1250),
            ('diamond_tier',   v_overall >= 1550),
            ('daily_7',        coalesce(v_streak_days, 0) >= 7)
    ) as a(id, earned)
    where a.earned
    on conflict do nothing;
end;
$$;

-- ----------------------------------------------------- submit a game result
-- The single entry point for recording a finished session. XP, stars, score,
-- rating change and leaderboard entries are all recomputed here; client-supplied
-- values for those fields are ignored.
create or replace function public.submit_game_result(
    p_client_id       uuid,
    p_mode            text,
    p_level_id        int,
    p_correct         int,
    p_total           int,
    p_best_streak     int,
    p_avg_response_ms int
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_accuracy double precision;
    v_stars int;
    v_xp int;
    v_score int;
    v_skill text;
    v_rating int;
    v_new_rating int;
    v_today bigint := floor(extract(epoch from now()) / 86400)::bigint;
    v_streak int;
    v_last bigint;
    v_week text := public.current_week_key();
    v_existing uuid;
begin
    if v_uid is null then
        raise exception 'not authenticated';
    end if;

    -- Payload sanity: reject impossible or implausible sessions.
    if p_total < 0 or p_total > 100 or p_correct < 0 or p_correct > p_total then
        raise exception 'invalid result payload';
    end if;
    if p_mode not in ('MATH_SPRINT','CROSS_MATH','MAZE','PATTERN','MEMORY','LOGIC','CLASSIC','REFLEX') then
        raise exception 'unknown mode %', p_mode;
    end if;
    if p_level_id is not null and (p_level_id < 1 or p_level_id > 100) then
        raise exception 'invalid level';
    end if;

    -- Idempotency: the same client session may be retried after a reconnect.
    select id into v_existing from public.game_results
        where user_id = v_uid and client_id = p_client_id;
    if v_existing is not null then
        return jsonb_build_object('duplicate', true, 'result_id', v_existing);
    end if;

    v_accuracy := case when p_total = 0 then 0 else p_correct::double precision / p_total end;
    v_stars := case when v_accuracy >= 0.9 then 3 when v_accuracy >= 0.7 then 2 when v_accuracy >= 0.5 then 1 else 0 end;
    v_xp := greatest(5, p_correct * 10 + p_best_streak * 5 + v_stars * 20);
    v_score := greatest(0, p_correct * 100 + p_best_streak * 25 - (p_total - p_correct) * 20);

    insert into public.game_results
        (user_id, client_id, mode, level_id, correct, total, best_streak,
         avg_response_ms, score, stars, xp, epoch_day)
    values
        (v_uid, p_client_id, p_mode, p_level_id, p_correct, p_total, p_best_streak,
         greatest(0, p_avg_response_ms), v_score, v_stars, v_xp, v_today);

    -- Progression (streak days, XP, win/loss).
    select last_active_day, streak_days into v_last, v_streak
        from public.player_progress where user_id = v_uid for update;
    if v_streak is null then
        insert into public.player_progress (user_id) values (v_uid) on conflict do nothing;
        v_streak := 0; v_last := -1;
    end if;
    v_streak := case
        when v_last = v_today then greatest(v_streak, 1)
        when v_last = v_today - 1 then v_streak + 1
        else 1
    end;

    update public.player_progress set
        xp = xp + v_xp,
        player_level = 1 + (xp + v_xp) / 500,
        games_played = games_played + 1,
        wins = wins + case when v_accuracy >= 0.7 then 1 else 0 end,
        losses = losses + case when v_accuracy < 0.7 then 1 else 0 end,
        best_streak = greatest(best_streak, p_best_streak),
        streak_days = v_streak,
        last_active_day = v_today
    where user_id = v_uid;

    -- Level progress (never downgrades an existing score).
    if p_level_id is not null then
        insert into public.level_progress (user_id, level_id, stars, best_score, completed)
        values (v_uid, p_level_id, v_stars, v_score, v_stars >= 1)
        on conflict (user_id, level_id) do update set
            stars = greatest(public.level_progress.stars, excluded.stars),
            best_score = greatest(public.level_progress.best_score, excluded.best_score),
            completed = public.level_progress.completed or excluded.completed,
            updated_at = now();
    end if;

    -- Rating for the mode's skill.
    v_skill := public.skill_for_mode(p_mode);
    insert into public.player_ratings (user_id, skill) values (v_uid, v_skill) on conflict do nothing;
    select rating into v_rating from public.player_ratings where user_id = v_uid and skill = v_skill for update;
    v_new_rating := public.elo_update(v_rating, v_accuracy);
    update public.player_ratings set
        rating = v_new_rating,
        peak = greatest(peak, v_new_rating)
    where user_id = v_uid and skill = v_skill;

    -- Leaderboard aggregates: per-mode and overall, weekly and all-time.
    insert into public.leaderboard_scores (user_id, mode, period, period_key, score)
    values
        (v_uid, p_mode, 'weekly', v_week, v_score),
        (v_uid, p_mode, 'all_time', 'all', v_score),
        (v_uid, 'overall', 'weekly', v_week, v_score),
        (v_uid, 'overall', 'all_time', 'all', v_score)
    on conflict (user_id, mode, period, period_key) do update set
        score = public.leaderboard_scores.score + excluded.score,
        updated_at = now();

    perform public.award_achievements(v_uid);

    return jsonb_build_object(
        'duplicate', false,
        'stars', v_stars,
        'xp', v_xp,
        'score', v_score,
        'rating', v_new_rating,
        'rating_delta', v_new_rating - v_rating
    );
end;
$$;

-- --------------------------------------------------- claim a daily challenge
create or replace function public.claim_daily_challenge(
    p_epoch_day    bigint,
    p_challenge_id text,
    p_score        int
) returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_today bigint := floor(extract(epoch from now()) / 86400)::bigint;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    -- Only the current day can be claimed (allows a small clock-skew window).
    if p_epoch_day > v_today + 1 or p_epoch_day < v_today - 1 then
        raise exception 'daily challenge is not claimable for that day';
    end if;
    if not exists (select 1 from public.daily_challenges where epoch_day = p_epoch_day and id = p_challenge_id) then
        raise exception 'unknown daily challenge';
    end if;

    insert into public.daily_challenge_results (user_id, epoch_day, challenge_id, score)
    values (v_uid, p_epoch_day, p_challenge_id, greatest(0, p_score))
    on conflict (user_id, epoch_day, challenge_id) do nothing;

    perform public.award_achievements(v_uid);
    return jsonb_build_object('ok', true);
end;
$$;

-- ------------------------------------------------------------- read snapshot
-- Everything the client needs to restore state after signing in on a new device.
create or replace function public.get_my_snapshot()
returns jsonb
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_result jsonb;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    select jsonb_build_object(
        'profile', to_jsonb(p),
        'progress', to_jsonb(pr),
        'ratings', coalesce((select jsonb_object_agg(skill, rating) from public.player_ratings where user_id = v_uid), '{}'::jsonb),
        'levels', coalesce((select jsonb_agg(jsonb_build_object('level_id', level_id, 'stars', stars, 'best_score', best_score, 'completed', completed)) from public.level_progress where user_id = v_uid), '[]'::jsonb),
        'achievements', coalesce((select jsonb_agg(achievement_id) from public.player_achievements where user_id = v_uid), '[]'::jsonb),
        'daily', coalesce((select jsonb_agg(jsonb_build_object('epoch_day', epoch_day, 'challenge_id', challenge_id, 'score', score)) from public.daily_challenge_results where user_id = v_uid), '[]'::jsonb)
    )
    into v_result
    from public.profiles p
    left join public.player_progress pr on pr.user_id = p.id
    where p.id = v_uid;

    return coalesce(v_result, '{}'::jsonb);
end;
$$;

-- --------------------------------------------------------------- leaderboard
-- p_scope  : 'global' | 'community' | 'friends'
-- p_kind   : 'score' | 'rating' | 'best_time'
-- p_mode   : a game mode, or 'overall'
-- p_period : 'weekly' | 'all_time'   (score board only)
create or replace function public.get_leaderboard(
    p_scope  text default 'global',
    p_kind   text default 'score',
    p_mode   text default 'overall',
    p_period text default 'all_time',
    p_limit  int  default 50,
    p_offset int  default 0
) returns table (
    rank         bigint,
    user_id      uuid,
    username     text,
    display_name text,
    avatar_seed  int,
    score        bigint,
    tier         text
)
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_community text;
    v_week text := public.current_week_key();
    v_limit int := least(greatest(coalesce(p_limit, 50), 1), 200);
    v_offset int := greatest(coalesce(p_offset, 0), 0);
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    select community into v_community from public.profiles where id = v_uid;

    return query
    with base as (
        select s.user_id as uid, s.score::bigint as val
        from public.leaderboard_scores s
        where p_kind = 'score'
          and s.mode = coalesce(p_mode, 'overall')
          and s.period = coalesce(p_period, 'all_time')
          and s.period_key = case when p_period = 'weekly' then v_week else 'all' end
        union all
        select r.user_id, r.rating::bigint
        from public.player_ratings r
        where p_kind = 'rating'
          and (case when p_mode = 'overall' then true else r.skill = public.skill_for_mode(p_mode) end)
        union all
        select g.user_id, min(g.avg_response_ms)::bigint
        from public.game_results g
        where p_kind = 'best_time' and g.avg_response_ms > 0
          and (p_mode is null or p_mode = 'overall' or g.mode = p_mode)
        group by g.user_id
    ),
    scoped as (
        select b.uid, b.val
        from base b
        join public.profiles pr on pr.id = b.uid
        where case p_scope
            when 'community' then v_community is not null and pr.community = v_community
            when 'friends' then exists (
                select 1 from public.friendships f
                where f.status = 'accepted'
                  and ((f.requester_id = v_uid and f.addressee_id = b.uid)
                    or (f.addressee_id = v_uid and f.requester_id = b.uid))
            )
            else true
        end
    ),
    ranked as (
        select s.uid, s.val,
               case when p_kind = 'best_time' then rank() over (order by s.val asc)
                    else rank() over (order by s.val desc) end as rk
        from scoped s
    )
    select r.rk, r.uid, pr.username, pr.display_name, pr.avatar_seed, r.val,
           case when p_kind = 'rating' then public.tier_for(r.val::int) else null end
    from ranked r
    join public.profiles pr on pr.id = r.uid
    order by r.rk asc, pr.username asc
    limit v_limit offset v_offset;
end;
$$;

-- ------------------------------------------------------------------- grants
revoke all on function public.submit_game_result(uuid, text, int, int, int, int, int) from public;
revoke all on function public.claim_daily_challenge(bigint, text, int) from public;
revoke all on function public.get_my_snapshot() from public;
revoke all on function public.get_leaderboard(text, text, text, text, int, int) from public;

grant execute on function public.submit_game_result(uuid, text, int, int, int, int, int) to authenticated;
grant execute on function public.claim_daily_challenge(bigint, text, int) to authenticated;
grant execute on function public.get_my_snapshot() to authenticated;
grant execute on function public.get_leaderboard(text, text, text, text, int, int) to authenticated;
