-- Customer can cancel their own order from the website (tracking token = proof of ownership). No reason asked.
-- Allowed only while the order is NOT out for delivery AND nothing has been paid or sent for checking.
-- Paid / receipt-pending orders must go through the team so a refund is handled.
-- Needs the base CBMS schema and functions (_release_reservation, _set_order_status). Safe to re-run.
create or replace function public.cancel_order_by_token(p_token text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare o public.orders;
begin
  if p_token is null or p_token !~ '^[0-9a-f]{64}$' then raise exception 'ORDER_NOT_FOUND'; end if;
  select * into o from public.orders where tracking_token = p_token for update;
  if not found then raise exception 'ORDER_NOT_FOUND'; end if;

  if o.order_status = 'CANCELLED' then
    return jsonb_build_object('order_status', 'CANCELLED');          -- double tap / retry is harmless
  end if;
  if o.order_status in ('OUT_FOR_DELIVERY', 'DELIVERED') then raise exception 'CANNOT_CANCEL_ON_THE_WAY'; end if;
  if o.payment_status not in ('UNPAID', 'CASH_PENDING', 'REJECTED') then raise exception 'CANNOT_CANCEL_PAID'; end if;

  perform public._release_reservation(o.id, 'Cancelled by customer');   -- releases stock exactly once
  update public.orders set cancelled_reason = 'Cancelled by customer' where id = o.id;
  perform public._set_order_status(o.id, 'CANCELLED', 'Cancelled by customer');
  insert into public.audit_logs (actor_id, action, entity, entity_id, details)
  values (null, 'ORDER_CANCELLED_BY_CUSTOMER', 'order', o.id::text, jsonb_build_object('order_no', o.order_no));
  return jsonb_build_object('order_status', 'CANCELLED');
end $$;

revoke execute on function public.cancel_order_by_token(text) from public;
grant execute on function public.cancel_order_by_token(text) to anon, authenticated;
