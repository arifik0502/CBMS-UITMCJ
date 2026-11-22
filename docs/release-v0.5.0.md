# CBMS Admin v0.5.0

## What's new

- Added customer support tickets across the customer website and CBMS Admin Android app.
- Added the Admin **Tickets** tab with open-ticket visibility, ticket details, optional photo viewing, customer call action, and resolve/reopen controls.
- Added Supabase Realtime updates and Firebase Cloud Messaging data notifications for new support tickets.
- Added customer order cancellation for eligible orders before dispatch and before payment is completed; inventory reservations are released and the cancellation is recorded in the audit log.
- Preserved the existing order, payment, inventory, and reporting push events.

## Backend migrations

Apply these migrations to a Supabase project in the documented order:

1. `20261119000001_customer_cancel_order.sql`
2. `20261121000001_support_tickets.sql`
3. `20261122000001_support_ticket_push_webhook.sql`

The push migration requires the Edge Function secrets and the matching Vault secret to be configured first. See [docs/support-tickets.md](https://github.com/arifik0502/CBMS-UITMCJ/blob/v0.5.0/docs/support-tickets.md) for setup details. Do not configure a second Dashboard Database Webhook for `support_tickets`; the migration creates the database trigger.

## Android APK

- Package: `com.crispyburger.admin`
- Version: `0.5.0`
- Version code: `14`
- Signed release APK; uses the existing CBMS Admin signing certificate.
- SHA-256: `401291B261B03BA0CA57233502FAABB527BE540C88FBFB03E8C78C00311E9A46`

The built-in GitHub updater can detect this release from v0.4.0. Android will still ask the user to approve installation.
