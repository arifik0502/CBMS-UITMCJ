-- Allow delivery windows to cross midnight, for example 11:00 PM -> 2:15 AM.
-- Overnight trips are recurring by their start date. Orders placed after midnight
-- but before the overnight end time keep the previous date as trip_date.

begin;

alter table public.delivery_trips
  drop constraint if exists trip_window;

alter table public.delivery_trips
  add constraint trip_window check (end_time <> start_time);

alter table public.delivery_trips
  drop constraint if exists delivery_trips_trip_no_check;

alter table public.delivery_trips
  add constraint delivery_trips_trip_no_check check (trip_no between 1 and 50);

create or replace function public._trip_closed_reason(t public.delivery_trips)
returns text
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_now timestamp := public._trip_now();
  v_end timestamp;
begin
  if not public._setting('accepting_orders', 'true')::boolean then
    return 'TEAM';
  end if;

  if not t.is_open then
    return 'TEAM';
  end if;

  if t.end_time = t.start_time then
    return 'ENDED';
  end if;

  if t.end_time > t.start_time then
    v_end := v_now::date + t.end_time;
  elsif v_now::time < t.end_time then
    -- We are after midnight inside the occurrence that started yesterday.
    v_end := v_now::date + t.end_time;
  else
    -- The next occurrence starts tonight and ends tomorrow.
    v_end := v_now::date + interval '1 day' + t.end_time;
  end if;

  if v_now >= v_end then
    return 'ENDED';
  end if;

  return null;
end;
$$;

create or replace function public.admin_save_trip(
  p_id uuid,
  p_trip_no integer,
  p_label text,
  p_start_time time,
  p_end_time time,
  p_is_open boolean
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_label text := btrim(coalesce(p_label, ''));
begin
  perform public._require_active_admin();

  if p_trip_no is null or p_trip_no < 1 or p_trip_no > 50 then raise exception 'INVALID_TRIP_NO'; end if;
  if char_length(v_label) < 1 or char_length(v_label) > 40 then raise exception 'INVALID_TRIP_LABEL'; end if;
  if p_start_time is null or p_end_time is null or p_end_time = p_start_time then raise exception 'INVALID_TRIP_TIME'; end if;
  if p_is_open is null then raise exception 'INVALID_TRIP'; end if;

  if exists (
    select 1 from public.delivery_trips
    where trip_no = p_trip_no
      and (p_id is null or id <> p_id)
  ) then
    raise exception 'TRIP_NO_EXISTS';
  end if;

  if p_id is null then
    insert into public.delivery_trips (trip_no, label, start_time, end_time, cutoff_minutes, is_open)
    values (p_trip_no, v_label, p_start_time, p_end_time, 0, p_is_open)
    returning id into v_id;
  else
    update public.delivery_trips
       set trip_no = p_trip_no,
           label = v_label,
           start_time = p_start_time,
           end_time = p_end_time,
           cutoff_minutes = 0,
           is_open = p_is_open
     where id = p_id
     returning id into v_id;
    if v_id is null then raise exception 'TRIP_NOT_FOUND'; end if;
  end if;

  perform public._audit(
    case when p_id is null then 'TRIP_CREATED' else 'TRIP_UPDATED' end,
    'delivery_trip',
    v_id::text,
    jsonb_build_object(
      'trip_no', p_trip_no,
      'label', v_label,
      'start_time', p_start_time,
      'end_time', p_end_time,
      'crosses_midnight', p_end_time < p_start_time,
      'is_open', p_is_open
    )
  );

  return v_id;
end;
$$;

create or replace function public._set_overnight_trip_date()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_trip public.delivery_trips;
  v_now timestamp := public._trip_now();
begin
  if new.trip_id is null then
    return new;
  end if;

  select * into v_trip
  from public.delivery_trips
  where id = new.trip_id;

  if found then
    if v_trip.end_time < v_trip.start_time and v_now::time < v_trip.end_time then
      new.trip_date := v_now::date - 1;
    elsif new.trip_date is null then
      new.trip_date := v_now::date;
    end if;
  end if;

  return new;
end;
$$;

drop trigger if exists cbms_set_overnight_trip_date on public.orders;
create trigger cbms_set_overnight_trip_date
before insert on public.orders
for each row execute function public._set_overnight_trip_date();

revoke all on function public._set_overnight_trip_date() from public, anon, authenticated;

commit;
