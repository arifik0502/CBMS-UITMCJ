-- CBMS customer support tickets
--
-- Customers (no login) report a problem from the website. The admin Android app lists tickets
-- and sets their status (OPEN / RESOLVED). One-way: there is no reply thread.
--
--   * public.support_tickets                    the tickets (RLS on, no direct customer access)
--   * create_support_ticket(...)                anon + authenticated: file a ticket (optionally about an order)
--   * get_support_tickets_by_tokens(text[])     anon + authenticated: status of tickets this device filed
--   * set_support_ticket_status(uuid, text)     active OWNER only
--   * storage bucket `ticket-attachments`       PRIVATE, one JPEG up to 2 MB per ticket, OWNER can read
--
-- Requires: public.orders (id uuid, order_no), public.profiles (id, role, is_active) and the existing
-- public.get_order_by_token(p_token). Safe to re-run.

begin;

create sequence if not exists public.support_ticket_seq;

create table if not exists public.support_tickets (
  id              uuid primary key default gen_random_uuid(),
  ticket_no       text not null unique
                    default ('TK-' || lpad(nextval('public.support_ticket_seq')::text, 4, '0')),
  -- Secret the customer's device keeps to look up its own tickets.
  token           text not null unique default replace(gen_random_uuid()::text, '-', ''),
  order_id        uuid references public.orders (id) on delete set null,
  message         text not null check (char_length(message) between 5 and 1000),
  customer_name   text not null check (char_length(customer_name) between 2 and 80),
  customer_phone  text not null,
  attachment_path text,
  status          text not null default 'OPEN' check (status in ('OPEN', 'RESOLVED')),
  idempotency_key text unique,
  created_at      timestamptz not null default now(),
  resolved_at     timestamptz,
  resolved_by     uuid references auth.users (id) on delete set null
);

create index if not exists support_tickets_status_created_idx on public.support_tickets (status, created_at desc);
create index if not exists support_tickets_order_idx on public.support_tickets (order_id);
create index if not exists support_tickets_phone_created_idx on public.support_tickets (customer_phone, created_at desc);
create unique index if not exists support_tickets_attachment_uidx
  on public.support_tickets (attachment_path) where attachment_path is not null;

alter table public.support_tickets enable row level security;
alter table public.support_tickets replica identity full;

revoke all on public.support_tickets from anon, authenticated;
grant select on public.support_tickets to authenticated;

-- Shared guard (identical to the reporting migration; safe to redefine).
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
       select 1 from public.profiles p
       where p.id = auth.uid() and p.role::text = 'OWNER' and p.is_active is true
     )
  then
    raise exception 'FORBIDDEN';
  end if;
end;
$$;
revoke all on function public._require_active_owner() from public, anon, authenticated;

drop policy if exists cbms_owner_reads_support_tickets on public.support_tickets;
create policy cbms_owner_reads_support_tickets
on public.support_tickets
for select
to authenticated
using (
  exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role::text = 'OWNER' and p.is_active is true
  )
);

-- Live updates for the admin app (RLS still applies to realtime).
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (
       select 1 from pg_publication_tables
       where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'support_tickets'
     )
  then
    alter publication supabase_realtime add table public.support_tickets;
  end if;
end;
$$;

-- What a customer is allowed to see about their own ticket.
create or replace function public._support_ticket_json(t public.support_tickets)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'ticket_no',   t.ticket_no,
    'token',       t.token,
    'status',      t.status,
    'message',     t.message,
    'created_at',  t.created_at,
    'resolved_at', t.resolved_at,
    'order_no',    (select o.order_no from public.orders o where o.id = t.order_id),
    'has_photo',   t.attachment_path is not null
  );
$$;
revoke all on function public._support_ticket_json(public.support_tickets) from public, anon, authenticated;

create or replace function public.create_support_ticket(
  p_idempotency_key text,
  p_order_token     text,
  p_message         text,
  p_name            text,
  p_phone           text,
  p_attachment_path text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_message  text := btrim(coalesce(p_message, ''));
  v_name     text := btrim(coalesce(p_name, ''));
  v_phone    text := regexp_replace(coalesce(p_phone, ''), '[\s-]', '', 'g');
  v_order_no text;
  v_order_id uuid;
  v_ticket   public.support_tickets;
begin
  if p_idempotency_key is null or char_length(p_idempotency_key) not between 8 and 100 then
    raise exception 'INVALID_REQUEST';
  end if;

  -- A retry of the same submission returns the ticket that was already created.
  select * into v_ticket from public.support_tickets where idempotency_key = p_idempotency_key;
  if found then
    return public._support_ticket_json(v_ticket);
  end if;

  if char_length(v_message) not between 5 and 1000 then raise exception 'INVALID_MESSAGE'; end if;
  if char_length(v_name) not between 2 and 80 then raise exception 'INVALID_NAME'; end if;
  if v_phone !~ '^(\+?60|0)1[0-9]{8,9}$' then raise exception 'INVALID_PHONE'; end if;

  if p_attachment_path is not null then
    if p_attachment_path !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9]{10,16}\.jpg$'
       or not exists (
         select 1 from storage.objects
         where bucket_id = 'ticket-attachments' and name = p_attachment_path
       )
    then
      raise exception 'INVALID_ATTACHMENT';
    end if;
    -- One photo belongs to exactly one ticket.
    if exists (select 1 from public.support_tickets where attachment_path = p_attachment_path) then
      raise exception 'INVALID_ATTACHMENT';
    end if;
  end if;

  if p_order_token is not null and btrim(p_order_token) <> '' then
    -- Reuses the existing token lookup, so an invalid token raises the same ORDER_NOT_FOUND.
    v_order_no := (public.get_order_by_token(btrim(p_order_token)))::jsonb ->> 'order_no';
    if v_order_no is null then raise exception 'ORDER_NOT_FOUND'; end if;
    select o.id into v_order_id from public.orders o where o.order_no = v_order_no;
  end if;

  -- Simple abuse guard: no more than 5 tickets per phone number per day.
  if (select count(*) from public.support_tickets
      where customer_phone = v_phone and created_at > now() - interval '24 hours') >= 5
  then
    raise exception 'TOO_MANY_TICKETS';
  end if;

  insert into public.support_tickets (
    order_id, message, customer_name, customer_phone, attachment_path, idempotency_key
  )
  values (v_order_id, v_message, v_name, v_phone, p_attachment_path, p_idempotency_key)
  returning * into v_ticket;

  return public._support_ticket_json(v_ticket);
end;
$$;

create or replace function public.get_support_tickets_by_tokens(p_tokens text[])
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select coalesce(jsonb_agg(public._support_ticket_json(t) order by t.created_at desc), '[]'::jsonb)
  from public.support_tickets t
  where t.token = any (coalesce(p_tokens[1:40], '{}'::text[]));
$$;

create or replace function public.set_support_ticket_status(p_ticket_id uuid, p_status text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public._require_active_owner();

  if p_status is null or p_status not in ('OPEN', 'RESOLVED') then
    raise exception 'INVALID_STATUS';
  end if;

  update public.support_tickets
     set status      = p_status,
         resolved_at = case when p_status = 'RESOLVED' then now() else null end,
         resolved_by = case when p_status = 'RESOLVED' then auth.uid() else null end
   where id = p_ticket_id;

  if not found then raise exception 'TICKET_NOT_FOUND'; end if;
end;
$$;

revoke all on function public.create_support_ticket(text, text, text, text, text, text) from public;
revoke all on function public.get_support_tickets_by_tokens(text[]) from public;
revoke all on function public.set_support_ticket_status(uuid, text) from public, anon;
grant execute on function public.create_support_ticket(text, text, text, text, text, text) to anon, authenticated;
grant execute on function public.get_support_tickets_by_tokens(text[]) to anon, authenticated;
grant execute on function public.set_support_ticket_status(uuid, text) to authenticated;

-- Private bucket for the optional photo. The website compresses to a JPEG well under 2 MB.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('ticket-attachments', 'ticket-attachments', false, 2097152, array['image/jpeg'])
on conflict (id) do update
  set public = false,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "cbms_customers_upload_ticket_photos" on storage.objects;
create policy "cbms_customers_upload_ticket_photos"
on storage.objects
for insert
to anon, authenticated
with check (
  bucket_id = 'ticket-attachments'
  and name ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9]{10,16}\.jpg$'
);

drop policy if exists "cbms_active_owner_can_read_ticket_photos" on storage.objects;
create policy "cbms_active_owner_can_read_ticket_photos"
on storage.objects
for select
to authenticated
using (
  bucket_id = 'ticket-attachments'
  and exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role::text = 'OWNER' and p.is_active is true
  )
);

commit;

notify pgrst, 'reload schema';
