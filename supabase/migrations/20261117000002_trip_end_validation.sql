-- No pre-trip order cutoff: a trip closes only after its 15-minute
-- delivery window has ended. Run after 20261117000001.
begin;

create or replace function public._trip_closed_reason(t public.delivery_trips) returns text
language plpgsql stable security definer set search_path = public as $$
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

  v_end := v_now::date + t.end_time;
  if v_now >= v_end then
    return 'ENDED';
  end if;

  return null;
end $$;

commit;
