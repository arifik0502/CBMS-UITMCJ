-- Send new orders to the existing owner-only Firebase push service.
-- Do not let a push delivery problem abort a customer's order.
begin;

create or replace function public.cbms_order_push_webhook()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, vault, net
as $function$
declare
  v_secret text;
begin
  select decrypted_secret into v_secret
  from vault.decrypted_secrets
  where name = 'cbms_webhook_secret'
  limit 1;

  if v_secret is null or v_secret = '' then
    raise warning 'CBMS order push secret unavailable';
    return new;
  end if;

  perform net.http_post(
    url := 'https://kflvdozjchirckoorlog.supabase.co/functions/v1/send-admin-push',
    body := jsonb_build_object(
      'type', 'INSERT',
      'schema', 'public',
      'table', 'orders',
      'record', jsonb_build_object(
        'id', new.id,
        'order_no', new.order_no,
        'trip_id', new.trip_id
      ),
      'old_record', null
    ),
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cbms-webhook-secret', v_secret
    ),
    timeout_milliseconds := 15000
  );
  return new;
exception when others then
  raise warning 'CBMS order push enqueue failed: %', SQLSTATE;
  return new;
end;
$function$;

revoke all on function public.cbms_order_push_webhook() from public, anon, authenticated;

drop trigger if exists cbms_order_push_after_insert on public.orders;
create trigger cbms_order_push_after_insert
after insert on public.orders
for each row execute function public.cbms_order_push_webhook();

commit;
