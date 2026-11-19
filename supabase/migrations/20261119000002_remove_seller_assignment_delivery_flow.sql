-- CBMS delivery flow without seller assignment.
-- New flow: READY -> OUT_FOR_DELIVERY -> DELIVERED.
-- Legacy ASSIGNED orders are treated as READY for the next transition.

begin;

create or replace function public.update_order_status(
  p_order_id uuid,
  p_new_status public.order_status,
  p_note text default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_role public.user_role := public.app_role();
  o public.orders;
  v_chain text[] := array[
    'NEW',
    'CONFIRMED',
    'PREPARING',
    'READY',
    'OUT_FOR_DELIVERY',
    'DELIVERED'
  ];
  v_current_status text;
  v_from integer;
  v_to integer;
begin
  if v_role is null then
    raise exception 'FORBIDDEN';
  end if;

  select *
    into o
    from public.orders
   where id = p_order_id
   for update;

  if not found then
    raise exception 'ORDER_NOT_FOUND';
  end if;

  if p_new_status = 'CANCELLED' then
    raise exception 'USE_CANCEL_ORDER';
  end if;

  if p_new_status = 'ASSIGNED' then
    raise exception 'SELLER_ASSIGNMENT_DISABLED';
  end if;

  if o.order_status in ('DELIVERED', 'CANCELLED') then
    raise exception 'ORDER_CLOSED';
  end if;

  -- Old ASSIGNED rows can continue directly to Out for delivery.
  v_current_status := case
    when o.order_status = 'ASSIGNED' then 'READY'
    else o.order_status::text
  end;

  v_from := array_position(v_chain, v_current_status);
  v_to := array_position(v_chain, p_new_status::text);

  if v_from is null or v_to is null or v_to <= v_from then
    raise exception 'INVALID_TRANSITION';
  end if;

  if v_role = 'SELLER' then
    if o.assigned_seller_id is distinct from v_uid then
      raise exception 'FORBIDDEN';
    end if;

    if not (
      (o.order_status = 'ASSIGNED' and p_new_status = 'OUT_FOR_DELIVERY')
      or (o.order_status = 'OUT_FOR_DELIVERY' and p_new_status = 'DELIVERED')
    ) then
      raise exception 'FORBIDDEN';
    end if;
  end if;

  if p_new_status = 'OUT_FOR_DELIVERY' then
    if o.order_status not in ('READY', 'ASSIGNED') then
      raise exception 'INVALID_TRANSITION';
    end if;

    -- Legacy delivery rows are updated when present; new unassigned deliveries
    -- do not require a seller/deliveries row.
    update public.deliveries
       set started_at = now()
     where order_id = o.id
       and is_current;
  end if;

  if p_new_status = 'DELIVERED' then
    if o.order_status <> 'OUT_FOR_DELIVERY' then
      raise exception 'INVALID_TRANSITION';
    end if;

    if o.payment_method = 'DUITNOW'
       and o.payment_status <> 'VERIFIED'
       and public._setting('require_verified_before_delivery', 'true')::boolean then
      raise exception 'PAYMENT_NOT_VERIFIED';
    end if;

    perform public._fulfil_reservation(o.id);

    update public.orders
       set delivered_at = now()
     where id = o.id;

    update public.deliveries
       set delivered_at = now()
     where order_id = o.id
       and is_current;
  end if;

  perform public._set_order_status(o.id, p_new_status, p_note);

  perform public._audit(
    'ORDER_STATUS',
    'order',
    o.id::text,
    jsonb_build_object(
      'order_no', o.order_no,
      'from', o.order_status,
      'to', p_new_status
    )
  );
end;
$$;

-- Prevent the retired seller-assignment action from being called through RPC.
revoke execute on function public.assign_order(uuid, uuid)
  from public, anon, authenticated;

comment on function public.update_order_status(uuid, public.order_status, text)
  is 'Order workflow: READY -> OUT_FOR_DELIVERY -> DELIVERED; seller assignment is not required.';

notify pgrst, 'reload schema';

commit;
