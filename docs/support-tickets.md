# Support tickets

Customers report a problem on the website (`/support`); the owner sees it in the admin app's **Tickets** tab and sets it
**Open / Resolved**. One-way: there is no reply thread. Customers see the status under **My orders** (on the device that
filed it; there are no customer accounts).

## 1. Apply the migration

Run `supabase/migrations/20261121000001_support_tickets.sql` in the Supabase SQL editor (safe to re-run). It creates:

| Object | Purpose |
| --- | --- |
| `support_tickets` | the tickets (RLS on; no direct access for customers) |
| `create_support_ticket(...)` | website → file a ticket (anon). Idempotent, validates input, max 5 tickets per phone per 24 h |
| `get_support_tickets_by_tokens(text[])` | website → status of the tickets this device holds a secret token for |
| `set_support_ticket_status(uuid, text)` | admin app → active OWNER only |
| bucket `ticket-attachments` | private; one JPEG (≤ 2 MB) per ticket; only the OWNER can read |

It assumes `orders.id` is a `uuid`. If `orders.id` has another type, change `order_id uuid` accordingly.

## 2. Push notification (background)

The repository Edge Function at supabase/functions/send-admin-push/index.ts handles new support-ticket inserts and
sends an FCM data message with type = "ticket", ticket_id, title and body. The Android app deduplicates the event and
opens the Tickets tab when the notification is tapped.

Configure these Edge Function secrets (never commit their values):

- SUPABASE_URL
- CBMS_SUPABASE_SERVICE_ROLE_KEY (or SUPABASE_SERVICE_ROLE_KEY)
- FIREBASE_SERVICE_ACCOUNT_JSON
- CBMS_WEBHOOK_SECRET
- CBMS_OWNER_EMAIL (the active OWNER account email used to select device tokens)

Deploy the function using:
supabase functions deploy send-admin-push --no-verify-jwt

In Supabase Dashboard, create a Database Webhook:

- Schema: public
- Table: support_tickets
- Event: Insert
- Target: Supabase Edge Function send-admin-push
- Header: x-cbms-webhook-secret = the same value as CBMS_WEBHOOK_SECRET

The Edge Function looks up active OWNER device tokens and sends only a data message. Realtime notifications cover the
case where the admin app is in the foreground; FCM covers background delivery.

## 3. Quick test

1. Website → *My orders* → **Report a problem** → fill in, optionally add a photo → *Send report*.
2. Admin app → **Tickets**: the ticket appears (with a 🎫 badge); open it, tap **View photo**, **Call**, **Mark as resolved**.
3. Website → *My orders*: the ticket shows **Resolved** within ~30 seconds.
