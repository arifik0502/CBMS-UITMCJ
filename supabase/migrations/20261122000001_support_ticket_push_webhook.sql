-- Deliver support-ticket inserts to the CBMS Admin app through the existing Edge Function.
-- Before applying this migration:
--   1. Deploy send-admin-push and configure CBMS_WEBHOOK_SECRET as an Edge Function secret.
--   2. Store the same value in Supabase Vault as cbms_webhook_secret.
-- The secret value must never be committed to source control.

begin;

create extension if not exists pg_net;

do $$
begin
  if not exists (
    select 1
    from vault.decrypted_secrets
    where name = 'cbms_webhook_secret'
  ) then
    raise exception
      'Missing Vault secret cbms_webhook_secret. Configure the shared webhook secret before applying this migration.';
  end if;
end;
$$;

create or replace function public.cbms_support_ticket_push_webhook()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, vault, net
as $function$
declare
  v_secret text;
begin
  select decrypted_secret
    into v_secret
    from vault.decrypted_secrets
   where name = 'cbms_webhook_secret';

  if v_secret is null then
    raise exception 'CBMS webhook secret is not configured in Vault';
  end if;

  perform net.http_post(
    url := 'https://kflvdozjchirckoorlog.supabase.co/functions/v1/send-admin-push',
    body := jsonb_build_object(
      'type', 'INSERT',
      'schema', TG_TABLE_SCHEMA,
      'table', TG_TABLE_NAME,
      'record', to_jsonb(NEW),
      'old_record', null
    ),
    params := '{}'::jsonb,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cbms-webhook-secret', v_secret
    ),
    timeout_milliseconds := 5000
  );

  return NEW;
end;
$function$;

drop trigger if exists cbms_support_ticket_push_after_insert
  on public.support_tickets;

create trigger cbms_support_ticket_push_after_insert
after insert on public.support_tickets
for each row
execute function public.cbms_support_ticket_push_webhook();

comment on function public.cbms_support_ticket_push_webhook() is
  'Sends new support-ticket inserts to the CBMS Admin push Edge Function using a Vault-managed webhook secret.';

commit;
