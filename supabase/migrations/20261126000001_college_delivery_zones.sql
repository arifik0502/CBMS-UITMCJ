-- Campus delivery zones. Floor 0 and room LOBBY are the canonical lobby
-- address understood by the existing order, payment and tracking APIs.
begin;

alter table public.buildings add column if not exists college_zone text
  check (college_zone in ('KKA', 'KKB', 'KKC'));

insert into public.buildings as existing (code, name, college_zone, sort_order, is_active)
values
  ('TS', 'Kolej Taming Sari', 'KKA', 1, true),
  ('SR', 'Kolej Sempana Riau', 'KKA', 2, true),
  ('SB', 'Kolej Sulok Belingkung', 'KKA', 3, true),
  ('MUTIARA', 'Kolej Mutiara', 'KKB', 4, true),
  ('BAIDURI', 'Kolej Baiduri', 'KKB', 5, true),
  ('NILAM', 'Kolej Nilam', 'KKB', 6, true),
  ('INTAN', 'Kolej Intan', 'KKC', 7, true),
  ('ZAMRUD', 'Kolej Zamrud', 'KKC', 8, true),
  ('SMK', 'Kolej Si Manja Kini', 'KKC', 9, true),
  ('SAB', 'Kolej Sempana Alam Buana', 'KKC', 10, true)
on conflict (code) do update set
  college_zone = excluded.college_zone,
  name = case when existing.name = existing.code then excluded.name else existing.name end;

-- Preserve configured room-delivery floors, including SB's fourth floor.
insert into public.floors (building_id, floor_number)
select b.id, f
from public.buildings b
cross join lateral generate_series(1, case when b.code = 'SB' then 4 else 3 end) f
where b.college_zone = 'KKA' and b.code in ('TS', 'SR', 'SB')
  and not exists (select 1 from public.floors existing where existing.building_id = b.id);

insert into public.floors (building_id, floor_number)
select b.id, 0 from public.buildings b
where b.college_zone in ('KKB', 'KKC')
  and not exists (select 1 from public.floors f where f.building_id = b.id and f.floor_number = 0);

-- Every order writer must respect the same destination, including older RPCs.
create or replace function public._require_college_lobby()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  if (new.floor_number is distinct from 0 or new.room_number is distinct from 'LOBBY')
     and exists (select 1 from public.buildings where id = new.building_id and college_zone in ('KKB', 'KKC')) then
    raise exception 'LOBBY_DELIVERY_ONLY';
  end if;
  return new;
end;
$$;

revoke all on function public._require_college_lobby() from public, anon, authenticated;
drop trigger if exists require_college_lobby on public.orders;
create trigger require_college_lobby before insert or update of building_id, floor_number, room_number
on public.orders for each row execute function public._require_college_lobby();

create or replace function public.admin_save_building(
  p_id uuid,
  p_code text,
  p_name text,
  p_floors integer[],
  p_sort_order integer,
  p_is_active boolean
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_code text := upper(btrim(coalesce(p_code, '')));
  v_name text := btrim(coalesce(p_name, ''));
begin
  perform public._require_active_admin();

  if v_code !~ '^[A-Z0-9][A-Z0-9_-]{0,7}$' then raise exception 'INVALID_BUILDING_CODE'; end if;
  if char_length(v_name) < 1 or char_length(v_name) > 80 then raise exception 'INVALID_BUILDING_NAME'; end if;
  if p_sort_order is null or p_sort_order < 0 or p_sort_order > 1000 then raise exception 'INVALID_SORT_ORDER'; end if;
  if p_is_active is null then raise exception 'INVALID_BUILDING'; end if;
  if p_floors is distinct from array[0] and exists (
    select 1 from public.buildings where id = p_id and college_zone in ('KKB', 'KKC')
  ) then raise exception 'LOBBY_DELIVERY_ONLY'; end if;
  if p_floors is null or cardinality(p_floors) < 1 or cardinality(p_floors) > 50 then raise exception 'INVALID_FLOORS'; end if;
  if exists (select 1 from unnest(p_floors) f where f < 0 or f > 100) then raise exception 'INVALID_FLOORS'; end if;
  if (select count(*) from unnest(p_floors)) <> (select count(distinct f) from unnest(p_floors) f) then
    raise exception 'DUPLICATE_FLOORS';
  end if;

  if exists (
    select 1 from public.buildings
    where upper(code) = v_code
      and (p_id is null or id <> p_id)
  ) then
    raise exception 'BUILDING_CODE_EXISTS';
  end if;

  if p_id is null then
    insert into public.buildings (code, name, sort_order, is_active)
    values (v_code, v_name, p_sort_order, p_is_active)
    returning id into v_id;
  else
    update public.buildings
       set code = v_code,
           name = v_name,
           sort_order = p_sort_order,
           is_active = p_is_active
     where id = p_id
     returning id into v_id;
    if v_id is null then raise exception 'BUILDING_NOT_FOUND'; end if;
  end if;

  delete from public.floors where building_id = v_id;
  insert into public.floors (building_id, floor_number)
  select v_id, f
  from unnest(p_floors) f
  order by f;

  perform public._audit(
    case when p_id is null then 'BUILDING_CREATED' else 'BUILDING_UPDATED' end,
    'building',
    v_id::text,
    jsonb_build_object(
      'code', v_code,
      'name', v_name,
      'floors', to_jsonb(p_floors),
      'sort_order', p_sort_order,
      'is_active', p_is_active
    )
  );

  return v_id;
end;
$$;

create or replace function public.admin_get_delivery_settings()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_trips jsonb;
  v_buildings jsonb;
begin
  perform public._require_active_admin();

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', t.id,
        'trip_no', t.trip_no,
        'label', t.label,
        'start_time', to_char(t.start_time, 'HH24:MI:SS'),
        'end_time', to_char(t.end_time, 'HH24:MI:SS'),
        'cutoff_minutes', t.cutoff_minutes,
        'is_open', t.is_open
      )
      order by t.trip_no
    ),
    '[]'::jsonb
  )
  into v_trips
  from public.delivery_trips t;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', b.id,
        'code', b.code,
        'name', b.name,
        'sort_order', b.sort_order,
        'is_active', b.is_active,
        'college_zone', b.college_zone,
        'floors', case when b.college_zone in ('KKB', 'KKC') then '[0]'::jsonb else b.floors end
      )
      order by b.sort_order, b.code
    ),
    '[]'::jsonb
  )
  into v_buildings
  from (
    select
      bu.id,
      bu.code,
      bu.name,
      bu.college_zone,
      bu.sort_order,
      bu.is_active,
      (
        select coalesce(jsonb_agg(f.floor_number order by f.floor_number), '[]'::jsonb)
        from public.floors f
        where f.building_id = bu.id
      ) as floors
    from public.buildings bu
  ) b;

  return jsonb_build_object(
    'accepting_orders', public._setting('accepting_orders', 'true')::boolean,
    'max_qty_per_order', public._setting('max_qty_per_order', '10')::int,
    'trips', v_trips,
    'buildings', v_buildings
  );
end;
$$;

create or replace function public.get_storefront()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_products jsonb;
  v_buildings jsonb;
  v_trips jsonb;
  v_first jsonb;
begin
  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', p.id,
        'name', p.name,
        'description', p.description,
        'price_cents', p.price_cents,
        'image_url', p.image_url,
        'available_stock', greatest(coalesce(i.physical_stock - i.reserved_stock, 0), 0)
      )
      order by p.created_at, p.name
    ),
    '[]'::jsonb
  )
  into v_products
  from public.products p
  left join public.inventory i on i.product_id = p.id
  where p.is_active;

  v_first := v_products -> 0;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', b.id,
        'code', b.code,
        'name', b.name,
        'college_zone', b.college_zone,
        'floors', case when b.college_zone in ('KKB', 'KKC') then '[0]'::jsonb else b.floors end
      ) order by b.sort_order
    ),
    '[]'::jsonb
  )
  into v_buildings
  from (
    select
      bu.id,
      bu.code,
      bu.name,
      bu.college_zone,
      bu.sort_order,
      (
        select coalesce(jsonb_agg(f.floor_number order by f.floor_number), '[]'::jsonb)
        from public.floors f
        where f.building_id = bu.id
      ) as floors
    from public.buildings bu
    where bu.is_active
  ) b;

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', t.id,
        'trip_no', t.trip_no,
        'label', t.label,
        'start_time', to_char(t.start_time, 'HH24:MI:SS'),
        'end_time', to_char(t.end_time, 'HH24:MI:SS'),
        'start_label', public._t12(t.start_time),
        'end_label', public._t12(t.end_time),
        'closes_label', public._t12(t.end_time),
        'closed_reason', public._trip_closed_reason(t)
      ) order by t.trip_no
    ),
    '[]'::jsonb
  )
  into v_trips
  from public.delivery_trips t;

  return jsonb_build_object(
    'business_name', public._setting('business_name', 'Crispy Burger'),
    'accepting_orders', public._setting('accepting_orders', 'true')::boolean,
    'max_qty_per_order', public._setting('max_qty_per_order', '10')::int,
    'payment_expiry_minutes', public._setting('payment_expiry_minutes', '30')::int,
    'duitnow_account_name', public._setting('duitnow_account_name', ''),
    'products', v_products,
    -- Legacy fields are retained so older website builds still render the first product.
    'product', v_first,
    'available_stock', coalesce((v_first ->> 'available_stock')::integer, 0),
    'buildings', v_buildings,
    'trips', v_trips
  );
end;
$$;

commit;
