-- 0007_queries.sql
-- Read/query RPCs so the Android client only ever touches `auth` + `rpc` (no raw
-- table access from the app). All are SECURITY DEFINER, authenticated-only, and
-- scope their results to auth.uid().

-- ------------------------------------------------------------------ profile
create or replace function public.upsert_profile(
    p_display_name text,
    p_avatar_seed  int,
    p_community    text default null
) returns jsonb
language plpgsql security definer set search_path = public, pg_catalog
as $$
declare
    v_uid uuid := auth.uid();
    v_name text;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;
    v_name := left(coalesce(nullif(trim(p_display_name), ''), 'Player'), 24);

    update public.profiles
        set display_name = v_name,
            avatar_seed = greatest(0, p_avatar_seed),
            community = nullif(left(coalesce(p_community, ''), 40), '')
        where id = v_uid;

    return (select to_jsonb(p) from public.profiles p where p.id = v_uid);
end;
$$;

-- ---------------------------------------------------------------- room state
create or replace function public.get_room_state(p_room_id uuid)
returns jsonb
language plpgsql security definer set search_path = public, pg_catalog
as $$
declare
    v_uid uuid := auth.uid();
    v_room public.rooms;
    v_players jsonb;
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    select * into v_room from public.rooms where id = p_room_id;
    if v_room is null then return null; end if;

    select coalesce(jsonb_agg(jsonb_build_object(
                'user_id', rp.user_id,
                'username', pr.username,
                'display_name', pr.display_name,
                'avatar_seed', pr.avatar_seed,
                'score', rp.score,
                'ready', rp.ready,
                'submitted', rp.submitted_at is not null,
                'is_host', rp.user_id = v_room.host_id,
                'is_me', rp.user_id = v_uid
            ) order by rp.joined_at), '[]'::jsonb)
        into v_players
        from public.room_players rp
        join public.profiles pr on pr.id = rp.user_id
        where rp.room_id = p_room_id;

    return jsonb_build_object(
        'id', v_room.id,
        'code', v_room.code,
        'mode', v_room.mode,
        'status', v_room.status,
        'host_id', v_room.host_id,
        'is_host', v_room.host_id = v_uid,
        'players', v_players
    );
end;
$$;

-- ------------------------------------------------------------- matchmaking
create or replace function public.list_open_rooms(
    p_mode  text default null,
    p_limit int  default 20
) returns table (
    id         uuid,
    code       text,
    mode       text,
    host_name  text,
    players    int,
    created_at timestamptz
)
language plpgsql security definer set search_path = public, pg_catalog
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    return query
    select r.id, r.code, r.mode, pr.display_name,
           (select count(*)::int from public.room_players rp where rp.room_id = r.id),
           r.created_at
    from public.rooms r
    join public.profiles pr on pr.id = r.host_id
    where r.status = 'open'
      and (p_mode is null or r.mode = p_mode)
      and (select count(*) from public.room_players rp where rp.room_id = r.id) < 2
      and r.created_at > now() - interval '30 minutes'
    order by r.created_at desc
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end;
$$;

-- ----------------------------------------------------------- match history
create or replace function public.get_match_history(
    p_limit  int default 20,
    p_offset int default 0
) returns table (
    id          uuid,
    mode        text,
    opponent    text,
    my_score    int,
    their_score int,
    won         boolean,
    drawn       boolean,
    created_at  timestamptz
)
language plpgsql security definer set search_path = public, pg_catalog
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'not authenticated'; end if;

    return query
    select m.id, m.mode,
           coalesce(op.display_name, 'Player'),
           case when m.player_a = v_uid then m.score_a else m.score_b end,
           case when m.player_a = v_uid then m.score_b else m.score_a end,
           (m.winner = v_uid),
           (m.winner is null),
           m.created_at
    from public.match_history m
    left join public.profiles op
        on op.id = case when m.player_a = v_uid then m.player_b else m.player_a end
    where m.player_a = v_uid or m.player_b = v_uid
    order by m.created_at desc
    limit least(greatest(coalesce(p_limit, 20), 1), 100)
    offset greatest(coalesce(p_offset, 0), 0);
end;
$$;

-- ------------------------------------------------------------------- grants
do $$
declare
    f text;
    fns text[] := array[
        'upsert_profile(text,int,text)',
        'get_room_state(uuid)',
        'list_open_rooms(text,int)',
        'get_match_history(int,int)'
    ];
begin
    foreach f in array fns loop
        execute format('revoke all on function public.%s from public, anon', f);
        execute format('grant execute on function public.%s to authenticated', f);
    end loop;
end;
$$;
