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

While the admin app is open it is notified live (Supabase realtime). For notifications when the app is closed, add a
**Database Webhook** that calls your existing push Edge Function:

- Dashboard → Database → Webhooks → *Create*: table `public.support_tickets`, event **Insert**, type *Supabase Edge Functions*,
  pick your existing push function.
- In that function, handle the new table. The Android app already understands this FCM **data** message
  (`CbmsFirebaseMessagingService`): `type = "ticket"`, plus `title` / `body`; `ticket_id` is used to de-duplicate.

```ts
// inside your existing handler, where you branch on payload.table
if (payload.table === "support_tickets" && payload.type === "INSERT") {
  const t = payload.record;
  const preview = String(t.message ?? "").split("\n")[0].slice(0, 100);
  return sendToAllOwnerDevices({            // your existing helper that reads device_tokens + calls FCM
    data: {
      type: "ticket",
      event_id: t.id,
      ticket_id: t.id,
      title: "New support ticket",
      body: `${t.ticket_no} from ${t.customer_name}: ${preview}`,
    },
  });
}
```

Send it as a **data** message (not only a `notification` payload) so the app can build the notification and open the Tickets tab when tapped.

## 3. Quick test

1. Website → *My orders* → **Report a problem** → fill in, optionally add a photo → *Send report*.
2. Admin app → **Tickets**: the ticket appears (with a 🎫 badge); open it, tap **View photo**, **Call**, **Mark as resolved**.
3. Website → *My orders*: the ticket shows **Resolved** within ~30 seconds.
