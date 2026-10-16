-- 0001_schema.sql
-- Mindflow core schema: profiles, progression, results, ratings, achievements,
-- daily challenges, friendships, multiplayer rooms and leaderboard aggregates.
--
-- Conventions:
--   * UUID primary keys (gen_random_uuid) for entity tables.
--   * every user-owned table references auth.users(id) with ON DELETE CASCADE.
--   * timestamptz everywhere; CHECK constraints enforce game rules at the DB layer.
--   * game_results is append-only and idempotent via (user_id, client_id).

create extension if not exists "pgcrypto";

-- ---------------------------------------------------------------- profiles
create table if not exists public.profiles (
    id            uuid primary key references auth.users (id) on delete cascade,
    username      text not null unique,
    display_name  text not null default 'Player',
    avatar_seed   int  not null default 0,
    is_guest      boolean not null default false,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint username_format check (username ~ '^[a-z0-9_]{3,20}$'),
    constraint display_name_len check (char_length(display_name) between 1 and 24)
);

-- ----------------------------------------------------------- player_progress
create table if not exists public.player_progress (
    user_id        uuid primary key references auth.users (id) on delete cascade,
    xp             int  not null default 0 check (xp >= 0),
    player_level   int  not null default 1 check (player_level >= 1),
    streak_days    int  not null default 0 check (streak_days >= 0),
    last_active_day bigint not null default -1,
    games_played   int  not null default 0 check (games_played >= 0),
    wins           int  not null default 0 check (wins >= 0),
    losses         int  not null default 0 check (losses >= 0),
    best_streak    int  not null default 0 check (best_streak >= 0),
    updated_at     timestamptz not null default now()
);

-- ------------------------------------------------------------ level_progress
create table if not exists public.level_progress (
    user_id    uuid not null references auth.users (id) on delete cascade,
    level_id   int  not null check (level_id between 1 and 100),
    stars      int  not null default 0 check (stars between 0 and 3),
    best_score int  not null default 0,
    completed  boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (user_id, level_id)
);

-- -------------------------------------------------------------- game_results
create table if not exists public.game_results (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references auth.users (id) on delete cascade,
    client_id       uuid not null,
    mode            text not null,
    level_id        int,
    correct         int  not null check (correct >= 0),
    total           int  not null check (total >= 0),
    best_streak     int  not null default 0 check (best_streak >= 0),
    avg_response_ms int  not null default 0 check (avg_response_ms >= 0),
    score           int  not null default 0,
    stars           int  not null default 0 check (stars between 0 and 3),
    xp              int  not null default 0 check (xp >= 0),
    epoch_day       bigint not null,
    created_at      timestamptz not null default now(),
    constraint correct_le_total check (correct <= total),
    constraint unique_client_result unique (user_id, client_id)
);

-- ------------------------------------------------------------ player_ratings
create table if not exists public.player_ratings (
    user_id    uuid not null references auth.users (id) on delete cascade,
    skill      text not null check (skill in ('math', 'logic', 'memory', 'puzzle')),
    rating     int  not null default 1000 check (rating between 100 and 3000),
    peak       int  not null default 1000 check (peak between 100 and 3000),
    updated_at timestamptz not null default now(),
    primary key (user_id, skill)
);

-- -------------------------------------------------------------- achievements
create table if not exists public.achievements (
    id          text primary key,
    title       text not null,
    description text not null,
    sort        int  not null default 0
);

create table if not exists public.player_achievements (
    user_id        uuid not null references auth.users (id) on delete cascade,
    achievement_id text not null references public.achievements (id) on delete cascade,
    unlocked_at    timestamptz not null default now(),
    primary key (user_id, achievement_id)
);

-- ----------------------------------------------------------- daily challenges
create table if not exists public.daily_challenges (
    id         text   not null,
    epoch_day  bigint not null,
    mode       text   not null,
    difficulty int    not null default 2 check (difficulty between 1 and 5),
    seed       bigint not null,
    primary key (epoch_day, id)
);

create table if not exists public.daily_challenge_results (
    user_id      uuid   not null references auth.users (id) on delete cascade,
    epoch_day    bigint not null,
    challenge_id text   not null,
    score        int    not null default 0,
    completed_at timestamptz not null default now(),
    primary key (user_id, epoch_day, challenge_id)
);

-- --------------------------------------------------------------- friendships
create table if not exists public.friendships (
    id           uuid primary key default gen_random_uuid(),
    requester_id uuid not null references auth.users (id) on delete cascade,
    addressee_id uuid not null references auth.users (id) on delete cascade,
    status       text not null default 'pending' check (status in ('pending', 'accepted', 'blocked')),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint no_self_friend check (requester_id <> addressee_id),
    constraint unique_friend_pair unique (requester_id, addressee_id)
);

-- -------------------------------------------------- multiplayer rooms/matches
create table if not exists public.rooms (
    id         uuid primary key default gen_random_uuid(),
    code       text not null unique check (code ~ '^[A-Z0-9]{6}$'),
    host_id    uuid not null references auth.users (id) on delete cascade,
    mode       text not null,
    status     text not null default 'open' check (status in ('open', 'playing', 'finished', 'abandoned')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.room_players (
    room_id   uuid not null references public.rooms (id) on delete cascade,
    user_id   uuid not null references auth.users (id) on delete cascade,
    score     int  not null default 0 check (score >= 0),
    ready     boolean not null default false,
    joined_at timestamptz not null default now(),
    primary key (room_id, user_id)
);

create table if not exists public.match_history (
    id         uuid primary key default gen_random_uuid(),
    room_id    uuid references public.rooms (id) on delete set null,
    mode       text not null,
    player_a   uuid not null references auth.users (id) on delete cascade,
    player_b   uuid not null references auth.users (id) on delete cascade,
    score_a    int  not null default 0,
    score_b    int  not null default 0,
    winner     uuid,
    created_at timestamptz not null default now(),
    constraint different_players check (player_a <> player_b)
);

-- -------------------------------------------------------- leaderboard_scores
create table if not exists public.leaderboard_scores (
    user_id    uuid not null references auth.users (id) on delete cascade,
    mode       text not null,
    period     text not null check (period in ('weekly', 'all_time')),
    period_key text not null,
    score      int  not null default 0,
    updated_at timestamptz not null default now(),
    primary key (user_id, mode, period, period_key)
);

-- ------------------------------------------------------------------- indexes
create index if not exists idx_game_results_user_created on public.game_results (user_id, created_at desc);
create index if not exists idx_game_results_mode_score   on public.game_results (mode, score desc);
create index if not exists idx_game_results_mode_time    on public.game_results (mode, avg_response_ms asc);
create index if not exists idx_leaderboard_rank          on public.leaderboard_scores (mode, period, period_key, score desc);
create index if not exists idx_friendships_requester     on public.friendships (requester_id);
create index if not exists idx_friendships_addressee     on public.friendships (addressee_id);
create index if not exists idx_match_history_player_a    on public.match_history (player_a, created_at desc);
create index if not exists idx_match_history_player_b    on public.match_history (player_b, created_at desc);
create index if not exists idx_daily_results_day         on public.daily_challenge_results (epoch_day);
create index if not exists idx_room_players_user         on public.room_players (user_id);

-- ------------------------------------------------------- updated_at trigger
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_profiles_touch on public.profiles;
create trigger trg_profiles_touch before update on public.profiles
    for each row execute function public.touch_updated_at();

drop trigger if exists trg_progress_touch on public.player_progress;
create trigger trg_progress_touch before update on public.player_progress
    for each row execute function public.touch_updated_at();

drop trigger if exists trg_friendships_touch on public.friendships;
create trigger trg_friendships_touch before update on public.friendships
    for each row execute function public.touch_updated_at();

drop trigger if exists trg_rooms_touch on public.rooms;
create trigger trg_rooms_touch before update on public.rooms
    for each row execute function public.touch_updated_at();

-- --------------------------------------------- auto-provision new accounts
-- When a user signs up, create their profile + progress + rating rows so the
-- client always has a complete record to sync against.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    base text;
    candidate text;
    n int := 0;
begin
    base := lower(regexp_replace(coalesce(split_part(new.email, '@', 1), 'player'), '[^a-z0-9_]', '', 'g'));
    if char_length(base) < 3 then
        base := 'player' || substr(replace(new.id::text, '-', ''), 1, 6);
    end if;
    base := substr(base, 1, 16);
    candidate := base;
    while exists (select 1 from public.profiles p where p.username = candidate) loop
        n := n + 1;
        candidate := base || n::text;
    end loop;

    insert into public.profiles (id, username, display_name, is_guest)
    values (new.id, candidate, coalesce(new.raw_user_meta_data ->> 'display_name', 'Player'),
            coalesce((new.raw_user_meta_data ->> 'is_guest')::boolean, false))
    on conflict (id) do nothing;

    insert into public.player_progress (user_id) values (new.id) on conflict do nothing;
    insert into public.player_ratings (user_id, skill)
        select new.id, s from unnest(array['math', 'logic', 'memory', 'puzzle']) as s
    on conflict do nothing;

    return new;
end;
$$;

drop trigger if exists trg_on_auth_user_created on auth.users;
create trigger trg_on_auth_user_created after insert on auth.users
    for each row execute function public.handle_new_user();
