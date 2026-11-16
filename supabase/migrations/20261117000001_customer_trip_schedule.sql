-- Customer delivery schedule: three 15-minute windows spread evenly
-- from 7:15 PM to 11:00 PM Malaysia time.
-- Run once after 20261116000001_trips_notes_commission.sql.
begin;

update public.delivery_trips
set
  start_time = case trip_no
    when 1 then time '19:15'
    when 2 then time '21:00'
    when 3 then time '22:45'
  end,
  end_time = case trip_no
    when 1 then time '19:30'
    when 2 then time '21:15'
    when 3 then time '23:00'
  end,
  cutoff_minutes = 0
where trip_no in (1, 2, 3);

create or replace function public.get_storefront() returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
  p public.products;
  v_avail int;
  v_buildings jsonb;
  v_trips jsonb;
begin
  select * into p from public.products where is_active order by created_at limit 1;

  select coalesce(physical_stock - reserved_stock, 0)
    into v_avail from public.inventory where product_id = p.id;

  select coalesce(
    jsonb_agg(jsonb_build_object('id', b.id, 'code', b.code, 'name', b.name, 'floors', b.floors)
              order by b.sort_order), '[]'::jsonb)
    into v_buildings
  from (
    select bu.id, bu.code, bu.name, bu.sort_order,
           (select coalesce(jsonb_agg(f.floor_number order by f.floor_number), '[]'::jsonb)
              from public.floors f where f.building_id = bu.id) as floors
      from public.buildings bu where bu.is_active
  ) b;

  select coalesce(
    jsonb_agg(jsonb_build_object(
      'id', t.id,
      'trip_no', t.trip_no,
      'label', t.label,
      'start_time', to_char(t.start_time, 'HH24:MI:SS'),
      'end_time', to_char(t.end_time, 'HH24:MI:SS'),
      'start_label', public._t12(t.start_time),
      'end_label', public._t12(t.end_time),
      'closes_label', public._t12(t.end_time),
      'closed_reason', public._trip_closed_reason(t)
    ) order by t.trip_no), '[]'::jsonb)
    into v_trips
  from public.delivery_trips t;

  return jsonb_build_object(
    'business_name', public._setting('business_name', 'Crispy Burger'),
    'accepting_orders', public._setting('accepting_orders', 'true')::boolean,
    'max_qty_per_order', public._setting('max_qty_per_order', '10')::int,
    'payment_expiry_minutes', public._setting('payment_expiry_minutes', '30')::int,
    'duitnow_account_name', public._setting('duitnow_account_name', ''),
    'product', case when p.id is null then null else jsonb_build_object(
      'id', p.id, 'name', p.name, 'description', p.description, 'price_cents', p.price_cents) end,
    'available_stock', coalesce(v_avail, 0),
    'buildings', v_buildings,
    'trips', v_trips
  );
end $$;

commit;
