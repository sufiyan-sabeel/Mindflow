-- 0005_seed.sql
-- Static catalogue data and the daily-challenge provisioner.

-- Achievement catalogue. The unlock conditions live in award_achievements().
insert into public.achievements (id, title, description, sort) values
    ('first_game',   'First Steps',    'Finish your first game',              1),
    ('ten_games',    'Getting Warm',   'Play 10 games',                       2),
    ('fifty_games',  'Dedicated',      'Play 50 games',                       3),
    ('streak_10',    'On Fire',        'Reach a 10-answer streak',            4),
    ('streak_25',    'Unstoppable',    'Reach a 25-answer streak',            5),
    ('stars_30',     'Star Collector', 'Earn 30 stars in the campaign',       6),
    ('level_10',     'Climber',        'Complete 10 campaign levels',         7),
    ('level_50',     'Halfway',        'Complete 50 campaign levels',         8),
    ('level_100',    'Completionist',  'Complete all 100 levels',             9),
    ('gold_tier',    'Gold Standard',  'Reach Gold tier',                    10),
    ('diamond_tier', 'Diamond Mind',   'Reach Diamond tier',                 11),
    ('daily_7',      'Daily Habit',    'Reach a 7-day streak',               12)
on conflict (id) do update set
    title = excluded.title,
    description = excluded.description,
    sort = excluded.sort;

-- Deterministic daily challenge set for a given epoch day. The seed mirrors the
-- client formula (epoch_day * 2654435761) so both sides agree on the puzzles.
create or replace function public.ensure_daily_challenges(p_epoch_day bigint)
returns setof public.daily_challenges
language plpgsql security definer set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    insert into public.daily_challenges (id, epoch_day, mode, difficulty, seed) values
        ('math',   p_epoch_day, 'MATH_SPRINT', 2, p_epoch_day * 2654435761),
        ('logic',  p_epoch_day, 'LOGIC',       2, p_epoch_day * 2654435761 + 1),
        ('memory', p_epoch_day, 'MEMORY',      2, p_epoch_day * 2654435761 + 2),
        ('speed',  p_epoch_day, 'REFLEX',      2, p_epoch_day * 2654435761 + 3),
        ('mixed',  p_epoch_day, 'PATTERN',     3, p_epoch_day * 2654435761 + 4)
    on conflict (epoch_day, id) do nothing;

    return query select * from public.daily_challenges where epoch_day = p_epoch_day order by id;
end;
$$;

revoke all on function public.ensure_daily_challenges(bigint) from public;
grant execute on function public.ensure_daily_challenges(bigint) to authenticated;
