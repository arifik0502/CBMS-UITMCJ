# Trips, custom notes, open/close, chef sheet, commission

Migrations: run `20261116000001_trips_notes_commission.sql` after migrations 1-3. Then run `20261117000003_trip_15_minute_windows.sql` once to apply the final schedule and server-side end-time validation. The final migration supersedes the earlier customer schedule migrations.

| Feature | How it works |
|---|---|
| Trips | Three 15-minute delivery windows in Malaysia time: Trip 1 7:15-7:30 PM, Trip 2 9:00-9:15 PM, Trip 3 10:45-11:00 PM. The windows are evenly spread from 7:15 PM to 11:00 PM. Customers can order until the selected window actually ends; there is no pre-trip cutoff. The website disables a trip after its end time and the database independently rejects orders after that time. Every order must pick a trip. Stock is one shared pool. |
| Custom orders | `orders.note` is empty for a standard burger. A custom order carries a note up to 120 characters; it applies to every burger in that order. |
| Open / close | `set_ordering_open(bool)` closes the whole shop; `set_trip_open(trip, bool)` closes one trip. OWNER and ADMIN only, audited. Existing orders are not affected. |
| Chef sheet | `get_chef_sheet(date, trip)` returns plain text: burgers to make per trip, plain vs custom, custom notes grouped, order numbers for packing, day total. OWNER and ADMIN only. |
| Commission | RM0.50 per burger (`business_settings.commission_per_burger_cents`, snapshotted on each order). `get_commission(from, to)`: burger counts when the order is DELIVERED and payment is VERIFIED or CASH_COLLECTED. Delivered but unpaid burgers show as pending. Sellers see only their own. |

Edit trip times: update `public.delivery_trips.start_time` and `end_time` for the relevant `trip_no`. Set `cutoff_minutes = 0`; the final server function closes a trip only at its end time (unless manually closed by the team).

The Android screens for open/close, chef sheet (copy button) and commission are built in Phase 5.
