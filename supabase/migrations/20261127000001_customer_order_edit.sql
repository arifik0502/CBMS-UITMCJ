-- Customer can revise an unpaid booking before preparation; cancellations remain soft deletes.
begin;
alter table public.orders add column if not exists customer_edit_revision integer not null default 0;

create or replace function public._check_customer_edit(o public.orders)
returns void language plpgsql stable set search_path = public as $$
declare t public.delivery_trips;
begin
  if o.order_status not in ('NEW','CONFIRMED') or o.reservation_settled
     or o.payment_status not in ('UNPAID','CASH_PENDING')
     or exists(select 1 from public.payments p where p.order_id=o.id and (p.receipt_path is not null or p.verified_at is not null or p.collected_at is not null)) then
    raise exception 'ORDER_EDIT_LOCKED';
  end if;
  if exists(select 1 from public.order_items where order_id=o.id group by product_id having count(*)>1) then raise exception 'ORDER_EDIT_LOCKED'; end if;
  if o.payment_method='DUITNOW' and now() >= o.created_at + make_interval(mins=>public._setting('payment_expiry_minutes','30')::int) then
    raise exception 'ORDER_EDIT_LOCKED';
  end if;
  select * into t from public.delivery_trips where id=o.trip_id;
  if not found or o.trip_date is null or public._trip_now() >= (o.trip_date+t.end_time+(case when t.end_time<t.start_time then interval '1 day' else interval '0 day' end)) then
    raise exception 'TRIP_CLOSED';
  end if;
end $$;
revoke all on function public._check_customer_edit(public.orders) from public,anon,authenticated;

create or replace function public.get_order_edit_options(p_token text)
returns jsonb language plpgsql stable security definer set search_path=public as $$
declare o public.orders;
begin
  if p_token is null or p_token !~ '^[0-9a-f]{64}$' then raise exception 'ORDER_NOT_FOUND'; end if;
  select * into o from public.orders where tracking_token=p_token;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  perform public._check_customer_edit(o);
  return jsonb_build_object('revision',o.customer_edit_revision,'note',o.note,
    'max_qty',public._setting('max_qty_per_order','10')::int,
    'products', (select coalesce(jsonb_agg(jsonb_build_object(
      'id',p.id,'name',coalesce(x.product_name,p.name),'price_cents',coalesce(x.unit_price_cents,p.price_cents),
      'quantity',coalesce(x.quantity,0),
      'max_quantity',coalesce(x.quantity,0)+case when p.is_active then greatest(coalesce(i.physical_stock-i.reserved_stock,0),0) else 0 end
    ) order by p.name,p.id),'[]'::jsonb)
    from public.products p left join public.inventory i on i.product_id=p.id
    left join public.order_items x on x.order_id=o.id and x.product_id=p.id
    where p.is_active or x.id is not null));
end $$;

create or replace function public.edit_order_by_token(p_token text,p_revision integer,p_items jsonb,p_note text,p_expected_total integer)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  o public.orders;
  item record;
  p public.products;
  inv public.inventory;
  old_item public.order_items;
  q integer;
  old_q integer;
  delta integer;
  total integer:=0;
  qty integer:=0;
  v_note text:=nullif(btrim(coalesce(p_note,'')),'');
  old_items jsonb;
begin
  if p_token is null or p_token !~ '^[0-9a-f]{64}$' then raise exception 'ORDER_NOT_FOUND'; end if;
  select * into o from public.orders where tracking_token=p_token for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;
  perform public._check_customer_edit(o);
  if p_revision is distinct from o.customer_edit_revision then raise exception 'ORDER_EDIT_STALE'; end if;
  if char_length(v_note)>120 then raise exception 'INVALID_NOTE'; end if;
  if p_items is null or jsonb_typeof(p_items)<>'array' then raise exception 'INVALID_CART'; end if;
  if jsonb_array_length(p_items) not between 1 and 20 then raise exception 'INVALID_CART'; end if;
  if exists(select 1 from jsonb_array_elements(p_items) a where jsonb_typeof(a)<>'object' or
      coalesce(a->>'product_id','') !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' or
      coalesce(a->>'quantity','') !~ '^[1-9][0-9]?$') then raise exception 'INVALID_CART'; end if;
  if (select count(distinct (a->>'product_id')::uuid) from jsonb_array_elements(p_items) a) <> jsonb_array_length(p_items) then raise exception 'DUPLICATE_PRODUCT'; end if;
  select sum((a->>'quantity')::int) into qty from jsonb_array_elements(p_items) a;
  if qty>public._setting('max_qty_per_order','10')::int then raise exception 'INVALID_QUANTITY'; end if;
  -- Lock and confirm payment record before touching reservation.
  perform 1 from public.payments where order_id=o.id for update;
  if not found then raise exception 'ORDER_EDIT_LOCKED'; end if;
  select coalesce(jsonb_agg(jsonb_build_object('product_id',x.product_id,'quantity',x.quantity,'price_cents',x.unit_price_cents)),'[]') into old_items from public.order_items x where x.order_id=o.id;
  -- Lock affected product and inventory rows in UUID order, including removed items.
  for item in select product_id from (
    select x.product_id from public.order_items x where x.order_id=o.id
    union select (a->>'product_id')::uuid from jsonb_array_elements(p_items) a
  ) s order by product_id loop
    select * into p from public.products where id=item.product_id for share;
    if not found then raise exception 'PRODUCT_NOT_FOUND'; end if;
    select * into inv from public.inventory where product_id=item.product_id for update;
    if not found then raise exception 'SOLD_OUT'; end if;
    select * into old_item from public.order_items where order_id=o.id and product_id=item.product_id;
    old_q:=coalesce(old_item.quantity,0);
    select coalesce(max((a->>'quantity')::int),0) into q from jsonb_array_elements(p_items) a where (a->>'product_id')::uuid=item.product_id;
    delta:=q-old_q;
    if delta>0 and not p.is_active then raise exception 'PRODUCT_NOT_FOUND'; end if;
    if delta>inv.physical_stock-inv.reserved_stock then raise exception 'INSUFFICIENT_STOCK' using hint=(old_q+greatest(inv.physical_stock-inv.reserved_stock,0))::text; end if;
    if inv.reserved_stock+delta<0 then raise exception 'ORDER_EDIT_LOCKED'; end if;
    if delta<>0 then
      update public.inventory set reserved_stock=reserved_stock+delta where product_id=item.product_id returning * into inv;
      if delta>0 then
        insert into public.inventory_logs(product_id,log_type,physical_delta,reserved_delta,physical_after,reserved_after,reason,order_id)
        values(item.product_id,'RESERVE',0,delta,inv.physical_stock,inv.reserved_stock,'Customer edited order',o.id);
      else
        insert into public.inventory_logs(product_id,log_type,physical_delta,reserved_delta,physical_after,reserved_after,reason,order_id)
        values(item.product_id,'RELEASE',0,delta,inv.physical_stock,inv.reserved_stock,'Customer edited order',o.id);
      end if;
    end if;
    if q=0 then
      delete from public.order_items where order_id=o.id and product_id=item.product_id;
    elsif old_q>0 then
      update public.order_items set quantity=q where order_id=o.id and product_id=item.product_id;
      total:=total+q*old_item.unit_price_cents;
    else
      insert into public.order_items(order_id,product_id,product_name,quantity,unit_price_cents) values(o.id,p.id,p.name,q,p.price_cents);
      total:=total+q*p.price_cents;
    end if;
  end loop;
  if total is distinct from p_expected_total then raise exception 'ORDER_EDIT_STALE'; end if;
  update public.payments set amount_cents=total where order_id=o.id;
  update public.orders set total_cents=total,note=v_note,customer_edit_revision=customer_edit_revision+1 where id=o.id;
  insert into public.audit_logs(actor_id,action,entity,entity_id,details)
    values(null,'ORDER_EDITED_BY_CUSTOMER','order',o.id::text,jsonb_build_object('order_no',o.order_no,'old_items',old_items,'new_items',p_items,'old_total',o.total_cents,'new_total',total,'old_note',o.note,'new_note',v_note));
  return public.get_order_by_token(p_token);
end $$;

revoke all on function public.get_order_edit_options(text) from public,anon,authenticated;
revoke all on function public.edit_order_by_token(text,integer,jsonb,text,integer) from public,anon,authenticated;
grant execute on function public.get_order_edit_options(text) to anon,authenticated;
grant execute on function public.edit_order_by_token(text,integer,jsonb,text,integer) to anon,authenticated;
commit;
