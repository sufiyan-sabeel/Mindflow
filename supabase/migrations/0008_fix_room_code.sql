-- 0008_fix_room_code.sql
-- Fix: gen_random_bytes() lives in the `extensions` schema, which the function's
-- pinned search_path (public, pg_catalog) does not include, so create_room failed
-- with "function gen_random_bytes(integer) does not exist".
-- Replace it with a code derived from gen_random_uuid(), which is in pg_catalog
-- on PostgreSQL 13+ and therefore always resolvable.

create or replace function public.create_room(p_mode text default 'MATH_SPRINT')
returns public.rooms
language plpgsql security definer set search_path = public, pg_catalog
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
        v_code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
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

revoke all on function public.create_room(text) from public, anon;
grant execute on function public.create_room(text) to authenticated;
