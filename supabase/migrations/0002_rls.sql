-- 0002_rls.sql
-- Row Level Security for every exposed table.
--
-- Model:
--   * A user may read and (where allowed) write only rows tied to their own id.
--   * Public catalogue tables (achievements, daily_challenges) are read-only to clients.
--   * Competitive and progression writes (results, ratings, XP, achievements, scores,
--     match outcomes) are NOT permitted directly: those tables have no INSERT/UPDATE
--     policy, so they can only be written through SECURITY DEFINER functions in 0003,
--     which validate the payload against auth.uid().
--   * profiles and player_ratings are readable by signed-in users so leaderboards and
--     friend search work; nothing private is stored in them.

-- Optional "community / college" grouping used by the community leaderboard tab.
alter table public.profiles add column if not exists community text;
create index if not exists idx_profiles_community on public.profiles (community) where community is not null;

-- ------------------------------------------------------------ enable RLS
alter table public.profiles                 enable row level security;
alter table public.player_progress          enable row level security;
alter table public.level_progress           enable row level security;
alter table public.game_results             enable row level security;
alter table public.player_ratings           enable row level security;
alter table public.achievements             enable row level security;
alter table public.player_achievements      enable row level security;
alter table public.daily_challenges         enable row level security;
alter table public.daily_challenge_results  enable row level security;
alter table public.friendships              enable row level security;
alter table public.rooms                    enable row level security;
alter table public.room_players             enable row level security;
alter table public.match_history            enable row level security;
alter table public.leaderboard_scores       enable row level security;

-- ---------------------------------------------------------------- profiles
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
    for select to authenticated using (true);

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self on public.profiles
    for insert to authenticated with check (id = auth.uid());

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles
    for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

-- ----------------------------------------------------------- player_progress
drop policy if exists progress_select_self on public.player_progress;
create policy progress_select_self on public.player_progress
    for select to authenticated using (user_id = auth.uid());

-- ------------------------------------------------------------ level_progress
drop policy if exists level_select_self on public.level_progress;
create policy level_select_self on public.level_progress
    for select to authenticated using (user_id = auth.uid());

-- -------------------------------------------------------------- game_results
drop policy if exists results_select_self on public.game_results;
create policy results_select_self on public.game_results
    for select to authenticated using (user_id = auth.uid());
-- No INSERT policy: results are written only by submit_game_result().

-- ------------------------------------------------------------ player_ratings
drop policy if exists ratings_select_all on public.player_ratings;
create policy ratings_select_all on public.player_ratings
    for select to authenticated using (true);
-- No write policy: ratings are updated only by server functions.

-- ------------------------------------------------------- achievement catalogue
drop policy if exists achievements_select on public.achievements;
create policy achievements_select on public.achievements
    for select to authenticated using (true);

drop policy if exists player_achievements_select on public.player_achievements;
create policy player_achievements_select on public.player_achievements
    for select to authenticated using (true);
-- No INSERT policy: awarded only by the server.

-- ---------------------------------------------------------- daily challenges
drop policy if exists daily_challenges_select on public.daily_challenges;
create policy daily_challenges_select on public.daily_challenges
    for select to authenticated using (true);

drop policy if exists daily_results_select_self on public.daily_challenge_results;
create policy daily_results_select_self on public.daily_challenge_results
    for select to authenticated using (user_id = auth.uid());
-- No INSERT policy: written only by claim_daily_challenge().

-- --------------------------------------------------------------- friendships
drop policy if exists friendships_select_involved on public.friendships;
create policy friendships_select_involved on public.friendships
    for select to authenticated
    using (requester_id = auth.uid() or addressee_id = auth.uid());

drop policy if exists friendships_insert_self on public.friendships;
create policy friendships_insert_self on public.friendships
    for insert to authenticated with check (requester_id = auth.uid());

drop policy if exists friendships_update_involved on public.friendships;
create policy friendships_update_involved on public.friendships
    for update to authenticated
    using (requester_id = auth.uid() or addressee_id = auth.uid())
    with check (requester_id = auth.uid() or addressee_id = auth.uid());

drop policy if exists friendships_delete_involved on public.friendships;
create policy friendships_delete_involved on public.friendships
    for delete to authenticated
    using (requester_id = auth.uid() or addressee_id = auth.uid());

-- -------------------------------------------------------- rooms (multiplayer)
-- Rooms are readable by signed-in users so a room code can be resolved before joining.
drop policy if exists rooms_select on public.rooms;
create policy rooms_select on public.rooms
    for select to authenticated using (true);

drop policy if exists rooms_insert_host on public.rooms;
create policy rooms_insert_host on public.rooms
    for insert to authenticated with check (host_id = auth.uid());

drop policy if exists rooms_update_host on public.rooms;
create policy rooms_update_host on public.rooms
    for update to authenticated using (host_id = auth.uid()) with check (host_id = auth.uid());

drop policy if exists room_players_select on public.room_players;
create policy room_players_select on public.room_players
    for select to authenticated using (true);

drop policy if exists room_players_insert_self on public.room_players;
create policy room_players_insert_self on public.room_players
    for insert to authenticated with check (user_id = auth.uid());

-- Players may toggle their own ready flag; scores are set only by the server.
drop policy if exists room_players_update_ready on public.room_players;
create policy room_players_update_ready on public.room_players
    for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists room_players_delete_self on public.room_players;
create policy room_players_delete_self on public.room_players
    for delete to authenticated using (user_id = auth.uid());

-- ------------------------------------------------------------ match_history
drop policy if exists match_history_select_involved on public.match_history;
create policy match_history_select_involved on public.match_history
    for select to authenticated
    using (player_a = auth.uid() or player_b = auth.uid());
-- No INSERT policy: matches are recorded only by finish_match().

-- -------------------------------------------------------- leaderboard_scores
drop policy if exists leaderboard_select on public.leaderboard_scores;
create policy leaderboard_select on public.leaderboard_scores
    for select to authenticated using (true);
-- No write policy: aggregates are maintained only by the server.
