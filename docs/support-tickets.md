# Support tickets

Customers report a problem on the website (`/support`); the owner sees it in the admin app's **Tickets** tab and sets it
**Open / Resolved**. One-way: there is no reply thread. Customers see the status under **My orders** (on the device that
filed it; there are no customer accounts).

## 1. Apply the support-ticket migration

Run `supabase/migrations/20261121000001_support_tickets.sql` in the Supabase SQL Editor (safe to re-run). It creates:

| Object | Purpose |
| --- | --- |
| `support_tickets` | tickets (RLS on; no direct access for customers) |
| `create_support_ticket(...)` | website → file a ticket (anon), idempotent validation, max 5 tickets per phone per 24 h |
| `get_support_tickets_by_tokens(text[])` | website → status of tickets this device holds a secret token for |
| `set_support_ticket_status(uuid, text)` | admin app → active OWNER only |
| bucket `ticket-attachments` | private; one JPEG (≤ 2 MB) per ticket; only the OWNER can read |

It assumes `orders.id` is a `uuid`. If `orders.id` has another type, change `order_id uuid` accordingly.

## 2. Configure push credentials

The repository Edge Function `supabase/functions/send-admin-push/index.ts` handles new support-ticket inserts and sends
an FCM data message with `type=ticket`, `ticket_id`, title and body. The Android app deduplicates the event and opens
the Tickets tab when the notification is tapped.

Configure these Edge Function secrets in the Supabase project (never commit their values):

- `SUPABASE_URL` (normally supplied automatically by Supabase)
- `SUPABASE_SERVICE_ROLE_KEY` or `CBMS_SUPABASE_SERVICE_ROLE_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON` (Firebase Admin SDK service-account JSON for the Android app's Firebase project)
- `CBMS_WEBHOOK_SECRET` (random shared secret)
- `CBMS_OWNER_EMAIL` (email of the active OWNER account)

Deploy the function:

```powershell
npx supabase functions deploy send-admin-push --no-verify-jwt
```

Store the exact same random webhook secret in Supabase Vault. Run this statement in the Supabase SQL Editor, replacing the
placeholder locally (do not commit the secret):

```sql
select vault.create_secret(
  '<same value as CBMS_WEBHOOK_SECRET>',
  'cbms_webhook_secret',
  'Shared secret for the CBMS support-ticket Edge Function webhook'
);
```

## 3. Install the database push trigger

After the Edge Function secrets and Vault secret are configured, run
`supabase/migrations/20261122000001_support_ticket_push_webhook.sql` in the Supabase SQL Editor.

This migration installs `pg_net` and an `AFTER INSERT` trigger on `public.support_tickets`. The trigger reads the shared
secret from Vault and posts the inserted row to `send-admin-push`. Do not also create a Supabase Dashboard Database
Webhook for the same table; that would send duplicate notifications.

The function looks up active OWNER Android device tokens and sends only data messages. Realtime notifications cover
foreground updates; FCM is used for background delivery.

## 4. Quick test

1. Website → *My orders* → **Report a problem** → fill in, optionally add a photo → *Send report*.
2. Admin app → **Tickets**: the ticket appears; open it, tap **View photo**, **Call**, or **Mark as resolved**.
3. Website → *My orders*: the ticket shows **Resolved** within ~30 seconds.

For an existing project where the SQL was already applied manually, verify the migration's database objects first, then
mark the migration version as applied with `supabase migration repair --status applied 20261122000001 --remote`.
Do not use `supabase db push` until all older migrations in the repository have been reconciled with the remote migration
history.
