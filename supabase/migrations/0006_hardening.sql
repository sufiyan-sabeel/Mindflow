-- 0006_hardening.sql
-- Follow-up fixes from the Supabase security advisor:
--   1. pin search_path on helper functions (lint: function_search_path_mutable)
--   2. remove the default anon EXECUTE grant from SECURITY DEFINER functions
--      (lint: anon_security_definer_function_executable)
--   3. keep purely-internal functions out of the REST surface entirely.

-- 1. Pin search_path on the SQL helpers.
alter function public.current_week_key() set search_path = public, pg_catalog;
alter function public.tier_for(int) set search_path = public, pg_catalog;
alter function public.elo_update(int, double precision) set search_path = public, pg_catalog;
alter function public.skill_for_mode(text) set search_path = public, pg_catalog;
alter function public.touch_updated_at() set search_path = public, pg_catalog;

-- 2. Internal-only functions: no client role may call them directly.
--    (They are still invoked internally by other SECURITY DEFINER functions,
--     which run with the owner's privileges.)
revoke all on function public.award_achievements(uuid) from public, anon, authenticated;
revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.touch_updated_at() from public, anon, authenticated;
revoke all on function public.finish_match(uuid) from public, anon, authenticated;

-- 3. Client-facing RPCs: signed-in users only, never the anon role.
do $$
declare
    f text;
    client_fns text[] := array[
        'submit_game_result(uuid,text,int,int,int,int,int)',
        'claim_daily_challenge(bigint,text,int)',
        'get_my_snapshot()',
        'get_leaderboard(text,text,text,text,int,int)',
        'ensure_daily_challenges(bigint)',
        'create_room(text)',
        'join_room(text)',
        'leave_room(uuid)',
        'set_room_ready(uuid,boolean)',
        'submit_room_score(uuid,int,int,int)'
    ];
begin
    foreach f in array client_fns loop
        execute format('revoke all on function public.%s from public, anon', f);
        execute format('grant execute on function public.%s to authenticated', f);
    end loop;
end;
$$;
