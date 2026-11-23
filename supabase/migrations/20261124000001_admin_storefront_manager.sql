-- No-code storefront management for CBMS Admin.
-- Adds multi-product storefront/order support, product photos, product creation,
-- delivery trip/building management and simple order controls.
--
-- Active OWNER and ADMIN profiles may use these management RPCs.
-- Existing create_order(...) is left untouched for backwards compatibility.

begin;

create or replace function public._require_active_admin()
returns void
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null
     or not exists (
       select 1
       from public.profiles p
       where p.id = auth.uid()
         and p.is_active is true
         and p.role::text in ('OWNER', 'ADMIN')
     )
  then
    raise exception 'FORBIDDEN';
  end if;
end;
$$;
revoke all on function public._require_active_admin() from public, anon, authenticated;

create or replace function public._product_image(p_value text)
returns text
language plpgsql
immutable
as $$
begin
  if p_value is null or btrim(p_value) = '' then return null; end if;
  if p_value !~ '^https://[a-z0-9.-]+/storage/v1/object/public/product-images/[0-9]{10,16}-[0-9a-f]{8}\.jpg$' then
    raise exception 'INVALID_PRODUCT_IMAGE';
  end if;
  return p_value;
end;
$$;
revoke all on function public._product_image(text) from public, anon, authenticated;

create or replace function public.admin_list_products()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public._require_active_admin();
  return coalesce((
    select jsonb_agg(
      jsonb_build_object(
        'id', p.id,
        'sku', p.sku,
        'name', p.name,
        'description', p.description,
        'price_cents', p.price_cents,
        'image_url', p.image_url,
        'is_active', p.is_active
      )
      order by p.name, p.created_at
    )
    from public.products p
  ), '[]'::jsonb);
end;
$$;

create or replace function public.admin_create_product(
  p_sku text,
  p_name text,
  p_description text,
  p_price_cents integer,
  p_image_url text default null,
  p_initial_stock integer default 0
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
  v_sku text := upper(btrim(coalesce(p_sku, '')));
  v_name text := btrim(coalesce(p_name, ''));
  v_description text := btrim(coalesce(p_description, ''));
  v_image text;
begin
  perform public._require_active_admin();

  if v_sku !~ '^[A-Z0-9][A-Z0-9_-]{1,19}$' then raise exception 'INVALID_SKU'; end if;
  if char_length(v_name) < 2 or char_length(v_name) > 80 then raise exception 'INVALID_PRODUCT_NAME'; end if;
  if char_length(v_description) > 500 then raise exception 'INVALID_PRODUCT_DESCRIPTION'; end if;
  if p_price_cents is null or p_price_cents < 1 or p_price_cents > 1000000 then raise exception 'INVALID_PRODUCT_PRICE'; end if;
  if p_initial_stock is null or p_initial_stock < 0 or p_initial_stock > 100000 then raise exception 'INVALID_QUANTITY'; end if;

  v_image := public._product_image(p_image_url);

  if exists (select 1 from public.products where upper(sku) = v_sku) then
    raise exception 'SKU_EXISTS';
  end if;

  insert into public.products (sku, name, description, price_cents, image_url, is_active)
  values (v_sku, v_name, v_description, p_price_cents, v_image, true)
  returning id into v_id;

  insert into public.inventory (product_id, physical_stock, reserved_stock)
  values (v_id, p_initial_stock, 0);

  if p_initial_stock > 0 then
    insert into public.inventory_logs (
      product_id, log_type, physical_delta, reserved_delta,
      physical_after, reserved_after, reason, performed_by
    )
    values (
      v_id, 'RESTOCK', p_initial_stock, 0,
      p_initial_stock, 0, 'Initial stock for new product', auth.uid()
    );
  end if;

  perform public._audit(
    'PRODUCT_CREATED',
    'product',
    v_id::text,
    jsonb_build_object(
      'sku', v_sku,
      'name', v_name,
      'price_cents', p_price_cents,
      'initial_stock', p_initial_stock
    )
  );

  return v_id;
end;
$$;

create or replace function public.admin_update_product(
  p_product_id uuid,
  p_sku text,
  p_name text,
  p_description text,
  p_price_cents integer,
  p_image_url text,
  p_is_active boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_old public.products;
  v_sku text := upper(btrim(coalesce(p_sku, '')));
  v_name text := btrim(coalesce(p_name, ''));
  v_description text := btrim(coalesce(p_description, ''));
  v_image text;
begin
  perform public._require_active_admin();

  if p_product_id is null then raise exception 'INVALID_PRODUCT'; end if;
  if v_sku !~ '^[A-Z0-9][A-Z0-9_-]{1,19}$' then raise exception 'INVALID_SKU'; end if;
  if char_length(v_name) < 2 or char_length(v_name) > 80 then raise exception 'INVALID_PRODUCT_NAME'; end if;
  if char_length(v_description) > 500 then raise exception 'INVALID_PRODUCT_DESCRIPTION'; end if;
  if p_price_cents is null or p_price_cents < 1 or p_price_cents > 1000000 then raise exception 'INVALID_PRODUCT_PRICE'; end if;
  if p_is_active is null then raise exception 'INVALID_PRODUCT'; end if;

  v_image := public._product_image(p_image_url);

  select * into v_old
  from public.products
  where id = p_product_id
  for update;

  if not found then raise exception 'PRODUCT_NOT_FOUND'; end if;

  if exists (
    select 1 from public.products
    where upper(sku) = v_sku and id <> p_product_id
  ) then
    raise exception 'SKU_EXISTS';
  end if;

  update public.products
  set sku = v_sku,
      name = v_name,
      description = v_description,
      price_cents = p_price_cents,
      image_url = v_image,
      is_active = p_is_active,
      updated_at = now()
  where id = p_product_id;

  perform public._audit(
    'PRODUCT_UPDATED',
    'product',
    p_product_id::text,
    jsonb_build_object(
      'old', jsonb_build_object(
        'sku', v_old.sku,
        'name', v_old.name,
        'description', v_old.description,
        'price_cents', v_old.price_cents,
        'image_url', v_old.image_url,
        'is_active', v_old.is_active
      ),
      'new', jsonb_build_object(
        'sku', v_sku,
        'name', v_name,
        'description', v_description,
        'price_cents', p_price_cents,
        'image_url', v_image,
        'is_active', p_is_active
      )
    )
  );
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
        'floors', b.floors
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
  if p_start_time is null or p_end_time is null or p_end_time <= p_start_time then raise exception 'INVALID_TRIP_TIME'; end if;
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
      'is_open', p_is_open
    )
  );

  return v_id;
end;
$$;

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

create or replace function public.admin_set_order_controls(
  p_accepting_orders boolean,
  p_max_qty_per_order integer
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public._require_active_admin();

  if p_accepting_orders is null then raise exception 'INVALID_SETTING'; end if;
  if p_max_qty_per_order is null or p_max_qty_per_order < 1 or p_max_qty_per_order > 99 then
    raise exception 'INVALID_MAX_QUANTITY';
  end if;

  insert into public.business_settings (key, value, updated_by, updated_at)
  values ('accepting_orders', to_jsonb(p_accepting_orders), auth.uid(), now())
  on conflict (key) do update
    set value = excluded.value,
        updated_by = excluded.updated_by,
        updated_at = excluded.updated_at;

  insert into public.business_settings (key, value, updated_by, updated_at)
  values ('max_qty_per_order', to_jsonb(p_max_qty_per_order), auth.uid(), now())
  on conflict (key) do update
    set value = excluded.value,
        updated_by = excluded.updated_by,
        updated_at = excluded.updated_at;

  perform public._audit(
    'ORDER_CONTROLS_UPDATED',
    'business_settings',
    'storefront',
    jsonb_build_object(
      'accepting_orders', p_accepting_orders,
      'max_qty_per_order', p_max_qty_per_order
    )
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
        'floors', b.floors
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

create or replace function public.create_order_cart(
  p_customer_name text,
  p_phone text,
  p_building_id uuid,
  p_floor_number integer,
  p_room_number text,
  p_items jsonb,
  p_payment_method public.payment_method,
  p_idempotency_key uuid default null,
  p_trip_id uuid default null,
  p_note text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_name text := btrim(coalesce(p_customer_name, ''));
  v_phone text := regexp_replace(coalesce(p_phone, ''), '[\s\-]', '', 'g');
  v_room text := upper(btrim(coalesce(p_room_number, '')));
  v_note text := nullif(btrim(regexp_replace(coalesce(p_note, ''), '\s+', ' ', 'g')), '');
  v_trip public.delivery_trips;
  v_prod public.products;
  v_inv public.inventory;
  v_order public.orders;
  v_item jsonb;
  v_product_id uuid;
  v_qty integer;
  v_ids uuid[] := '{}'::uuid[];
  v_total_qty integer := 0;
  v_total integer := 0;
  v_avail integer;
begin
  if char_length(v_name) < 2 or char_length(v_name) > 80 then raise exception 'INVALID_NAME'; end if;
  if v_phone !~ '^(\+?60|0)1[0-9]{8,9}$' then raise exception 'INVALID_PHONE'; end if;
  if v_room !~ '^[A-Z0-9][A-Z0-9 /\-]{0,9}$' then raise exception 'INVALID_ROOM'; end if;
  if v_note is not null and char_length(v_note) > 120 then raise exception 'INVALID_NOTE'; end if;
  if p_payment_method is null then raise exception 'INVALID_PAYMENT_METHOD'; end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array'
     or jsonb_array_length(p_items) < 1 or jsonb_array_length(p_items) > 20 then
    raise exception 'INVALID_CART';
  end if;

  for v_item in select value from jsonb_array_elements(p_items) loop
    if jsonb_typeof(v_item) <> 'object' then raise exception 'INVALID_CART'; end if;
    begin
      v_product_id := (v_item ->> 'product_id')::uuid;
      v_qty := (v_item ->> 'quantity')::integer;
    exception when others then
      raise exception 'INVALID_CART';
    end;
    if v_qty is null or v_qty < 1 or v_qty > 99 then raise exception 'INVALID_QUANTITY'; end if;
    if v_product_id = any(v_ids) then raise exception 'DUPLICATE_PRODUCT'; end if;
    v_ids := array_append(v_ids, v_product_id);
    v_total_qty := v_total_qty + v_qty;
  end loop;

  if v_total_qty > public._setting('max_qty_per_order', '10')::int then
    raise exception 'INVALID_QUANTITY';
  end if;
  if not public._setting('accepting_orders', 'true')::boolean then
    raise exception 'NOT_ACCEPTING_ORDERS';
  end if;

  if not exists (
    select 1
    from public.floors f
    join public.buildings b on b.id = f.building_id
    where f.building_id = p_building_id
      and f.floor_number = p_floor_number
      and b.is_active
  ) then
    raise exception 'INVALID_LOCATION';
  end if;

  if p_trip_id is null then raise exception 'TRIP_REQUIRED'; end if;
  select * into v_trip from public.delivery_trips where id = p_trip_id;
  if not found then raise exception 'INVALID_TRIP'; end if;
  if public._trip_closed_reason(v_trip) is not null then raise exception 'TRIP_CLOSED'; end if;

  if p_idempotency_key is not null then
    select * into v_order from public.orders where idempotency_key = p_idempotency_key;
    if found then return public._order_ticket(v_order); end if;
  end if;

  if (
    select count(*)
    from public.orders
    where customer_phone = v_phone
      and order_status not in ('DELIVERED', 'CANCELLED')
  ) >= public._setting('max_active_orders_per_phone', '3')::int then
    raise exception 'TOO_MANY_ACTIVE_ORDERS';
  end if;

  -- Lock product and inventory rows in UUID order to avoid deadlocks between concurrent carts.
  for v_item in
    select value from jsonb_array_elements(p_items)
    order by value ->> 'product_id'
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::integer;

    select * into v_prod
    from public.products
    where id = v_product_id and is_active
    for share;
    if not found then raise exception 'PRODUCT_NOT_FOUND'; end if;

    select * into v_inv
    from public.inventory
    where product_id = v_product_id
    for update;
    if not found then raise exception 'SOLD_OUT'; end if;

    v_avail := v_inv.physical_stock - v_inv.reserved_stock;
    if v_avail <= 0 then raise exception 'SOLD_OUT'; end if;
    if v_avail < v_qty then
      raise exception 'INSUFFICIENT_STOCK' using hint = v_avail::text;
    end if;

    v_total := v_total + (v_prod.price_cents * v_qty);
  end loop;

  insert into public.orders (
    idempotency_key, customer_name, customer_phone, building_id, floor_number,
    room_number, payment_method, order_status, payment_status, total_cents,
    trip_id, trip_date, note, commission_per_burger_cents
  )
  values (
    p_idempotency_key, v_name, v_phone, p_building_id, p_floor_number,
    v_room, p_payment_method, 'CONFIRMED',
    case p_payment_method
      when 'CASH' then 'CASH_PENDING'::public.payment_status
      else 'UNPAID'::public.payment_status
    end,
    v_total, v_trip.id, public._trip_now()::date, v_note,
    public._setting('commission_per_burger_cents', '50')::int
  )
  returning * into v_order;

  for v_item in
    select value from jsonb_array_elements(p_items)
    order by value ->> 'product_id'
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::integer;

    select * into v_prod from public.products where id = v_product_id;

    insert into public.order_items (
      order_id, product_id, product_name, quantity, unit_price_cents
    )
    values (
      v_order.id, v_prod.id, v_prod.name, v_qty, v_prod.price_cents
    );

    update public.inventory
       set reserved_stock = reserved_stock + v_qty
     where product_id = v_product_id
     returning * into v_inv;

    insert into public.inventory_logs (
      product_id, log_type, physical_delta, reserved_delta,
      physical_after, reserved_after, reason, order_id
    )
    values (
      v_product_id, 'RESERVE', 0, v_qty,
      v_inv.physical_stock, v_inv.reserved_stock,
      'Online order ' || v_order.order_no, v_order.id
    );
  end loop;

  insert into public.payments (order_id, method, amount_cents)
  values (v_order.id, p_payment_method, v_total);

  insert into public.order_status_history (order_id, from_status, to_status, note)
  values
    (v_order.id, null, 'NEW', 'Order placed'),
    (v_order.id, 'NEW', 'CONFIRMED', 'Auto-accepted: stock reserved');

  return public._order_ticket(v_order);
end;
$$;

revoke all on function public.admin_list_products() from public, anon;
revoke all on function public.admin_create_product(text,text,text,integer,text,integer) from public, anon;
revoke all on function public.admin_update_product(uuid,text,text,text,integer,text,boolean) from public, anon;
revoke all on function public.admin_get_delivery_settings() from public, anon;
revoke all on function public.admin_save_trip(uuid,integer,text,time,time,boolean) from public, anon;
revoke all on function public.admin_save_building(uuid,text,text,integer[],integer,boolean) from public, anon;
revoke all on function public.admin_set_order_controls(boolean,integer) from public, anon;

grant execute on function public.admin_list_products() to authenticated;
grant execute on function public.admin_create_product(text,text,text,integer,text,integer) to authenticated;
grant execute on function public.admin_update_product(uuid,text,text,text,integer,text,boolean) to authenticated;
grant execute on function public.admin_get_delivery_settings() to authenticated;
grant execute on function public.admin_save_trip(uuid,integer,text,time,time,boolean) to authenticated;
grant execute on function public.admin_save_building(uuid,text,text,integer[],integer,boolean) to authenticated;
grant execute on function public.admin_set_order_controls(boolean,integer) to authenticated;

revoke all on function public.create_order_cart(text,text,uuid,integer,text,jsonb,public.payment_method,uuid,uuid,text) from public;
grant execute on function public.create_order_cart(text,text,uuid,integer,text,jsonb,public.payment_method,uuid,uuid,text) to anon, authenticated;
revoke all on function public.get_storefront() from public;
grant execute on function public.get_storefront() to anon, authenticated;

-- Product images are public menu assets. Only active OWNER/ADMIN users may upload/delete.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('product-images', 'product-images', true, 1048576, array['image/jpeg'])
on conflict (id) do update
  set public = true,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "cbms_admin_uploads_product_images" on storage.objects;
create policy "cbms_admin_uploads_product_images"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'product-images'
  and name ~ '^[0-9]{10,16}-[0-9a-f]{8}\.jpg$'
  and exists (
    select 1 from public.profiles p
    where p.id = auth.uid()
      and p.is_active is true
      and p.role::text in ('OWNER', 'ADMIN')
  )
);

drop policy if exists "cbms_admin_deletes_product_images" on storage.objects;
create policy "cbms_admin_deletes_product_images"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'product-images'
  and exists (
    select 1 from public.profiles p
    where p.id = auth.uid()
      and p.is_active is true
      and p.role::text in ('OWNER', 'ADMIN')
  )
);

commit;
