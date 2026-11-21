# Customer order cancellation

Apply supabase/migrations/20261119000001_customer_cancel_order.sql in the Supabase SQL Editor.

Customers can cancel their own orders using the private 64-character tracking token. The server permits cancellation only before the order is out for delivery and while payment is unpaid, cash-pending or rejected. Paid or receipt-pending orders must be handled by the team so refunds can be arranged. The migration releases the inventory reservation, records the cancellation reason and adds an audit log entry.
