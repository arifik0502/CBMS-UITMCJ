-- CBMS admin reporting RPCs
--
-- Used by the Android admin app (android-admin) so that dashboard counts and sales totals are
-- computed in the database instead of from a capped client-side download:
--   * get_order_counts()                      whole-table order counts (Overview cards)
--   * get_sales_summary(p_from, p_to)         delivered / settled / cash totals for a date range
--   * get_cash_collections(p_from, p_to, n)   latest cash collections, joined to order numbers
--
-- All three are SECURITY DEFINER, restricted to an active OWNER profile, and only granted to
-- the `authenticated` role. The app falls back to paged client queries if this migration has
-- not been applied yet, so applying it is safe at any time.

begin;

-- Shared guard: raises FORBIDDEN unless the caller is an active OWNER.
create or replace function public._require_active_owner()
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
         and p.role::text = 'OWNER'
         and p.is_active is true
     )
  then
    raise exception 'FORBIDDEN';
  end if;
end;
$$;

revoke all on function public._require_active_owner() from public, anon, authenticated;

-- Whole-table order counts for the Overview cards.
create or replace function public.get_order_counts()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_today_start timestamptz :=
    (date_trunc('day', now() at time zone 'Asia/Kuala_Lumpur') at time zone 'Asia/Kuala_Lumpur');
  v_result jsonb;
begin
  perform public._require_active_owner();

  select jsonb_build_object(
    'payment_review',  count(*) filter (where o.payment_status::text = 'PENDING_VERIFICATION'),
    'new_orders',      count(*) filter (where o.order_status::text in ('NEW', 'CONFIRMED')),
    'in_progress',     count(*) filter (where o.order_status::text in ('PREPARING', 'READY', 'ASSIGNED', 'OUT_FOR_DELIVERY')),
    'delivered_today', count(*) filter (where o.delivered_at >= v_today_start)
  )
  into v_result
  from public.orders o;

  return v_result;
end;
$$;

-- Delivered/settled totals (by delivery date) and cash totals (by collection date).
create or replace function public.get_sales_summary(p_from timestamptz, p_to timestamptz)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_orders jsonb;
  v_cash jsonb;
begin
  perform public._require_active_owner();

  if p_from is null or p_to is null or p_to <= p_from then
    raise exception 'invalid date range';
  end if;

  select jsonb_build_object(
    'delivered_count', count(*),
    'delivered_cents', coalesce(sum(o.total_cents), 0),
    'settled_count',   count(*) filter (where o.payment_status::text in ('VERIFIED', 'CASH_COLLECTED')),
    'settled_cents',   coalesce(sum(o.total_cents) filter (where o.payment_status::text in ('VERIFIED', 'CASH_COLLECTED')), 0)
  )
  into v_orders
  from public.orders o
  where o.delivered_at >= p_from
    and o.delivered_at <  p_to;

  select jsonb_build_object(
    'cash_count', count(*),
    'cash_cents', coalesce(sum(p.amount_cents), 0)
  )
  into v_cash
  from public.payments p
  join public.orders o on o.id = p.order_id
  where p.collected_at >= p_from
    and p.collected_at <  p_to
    and o.payment_status::text = 'CASH_COLLECTED';

  return v_orders || v_cash;
end;
$$;

-- Latest cash collections with their order numbers (replaces the old "load 1000 orders and join in the app").
create or replace function public.get_cash_collections(
  p_from timestamptz,
  p_to timestamptz,
  p_limit integer default 50
)
returns table (order_no text, amount_cents bigint, collected_at timestamptz)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public._require_active_owner();

  return query
  select o.order_no::text, p.amount_cents::bigint, p.collected_at
  from public.payments p
  join public.orders o on o.id = p.order_id
  where p.collected_at >= p_from
    and p.collected_at <  p_to
    and o.payment_status::text = 'CASH_COLLECTED'
  order by p.collected_at desc
  limit greatest(least(coalesce(p_limit, 50), 500), 1);
end;
$$;

revoke all on function public.get_order_counts() from public, anon;
revoke all on function public.get_sales_summary(timestamptz, timestamptz) from public, anon;
revoke all on function public.get_cash_collections(timestamptz, timestamptz, integer) from public, anon;
grant execute on function public.get_order_counts() to authenticated;
grant execute on function public.get_sales_summary(timestamptz, timestamptz) to authenticated;
grant execute on function public.get_cash_collections(timestamptz, timestamptz, integer) to authenticated;

-- Supporting indexes for the range / status queries above.
create index if not exists orders_delivered_at_idx on public.orders (delivered_at) where delivered_at is not null;
create index if not exists orders_order_status_idx on public.orders (order_status);
create index if not exists orders_payment_status_idx on public.orders (payment_status);
create index if not exists payments_collected_at_idx on public.payments (collected_at) where collected_at is not null;

commit;

-- Make PostgREST pick up the new functions immediately.
notify pgrst, 'reload schema';
