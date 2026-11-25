import type { OrderView } from "./types";

export type OrderTab = "TO_PAY" | "PREPARING" | "READY" | "ON_THE_WAY" | "COMPLETED" | "CANCELLED";

export const TAB_LABEL: Record<OrderTab, string> = {
  TO_PAY: "To pay",
  PREPARING: "Preparing",
  READY: "Ready",
  ON_THE_WAY: "On the way",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled"
};

/** Which "My Orders" tab an order belongs to (Shopee-style groups). */
export function orderTab(o: OrderView): OrderTab {
  if (o.order_status === "CANCELLED") return "CANCELLED";
  if (o.order_status === "DELIVERED") return "COMPLETED";
  if (o.order_status === "OUT_FOR_DELIVERY") return "ON_THE_WAY";
  if (o.order_status === "READY" || o.order_status === "ASSIGNED") return "READY";
  if (o.payment_method === "DUITNOW" && (o.payment_status === "UNPAID" || o.payment_status === "REJECTED")) {
    return "TO_PAY";
  }
  return "PREPARING";
}

export function isFinished(o: OrderView): boolean {
  return o.order_status === "DELIVERED" || o.order_status === "CANCELLED";
}

const onTheWayOrDone = (o: OrderView) =>
  o.order_status === "OUT_FOR_DELIVERY" || o.order_status === "DELIVERED" || o.order_status === "CANCELLED";

/** Customer may cancel only unpaid orders that are not out for delivery (the server enforces this too). */
export function canCancel(o: OrderView): boolean {
  return !onTheWayOrDone(o) &&
    (o.payment_status === "UNPAID" || o.payment_status === "CASH_PENDING" || o.payment_status === "REJECTED");
}

/** Paid, or receipt waiting for the team: must go through the team so a refund is handled. */
export function isPaidLocked(o: OrderView): boolean {
  return !onTheWayOrDone(o) &&
    (o.payment_status === "PENDING_VERIFICATION" || o.payment_status === "VERIFIED");
}

/** Cancelled orders that were never paid should not show a misleading payment status. */
export function showPaymentStatus(o: OrderView): boolean {
  if (o.order_status !== "CANCELLED") return true;
  return o.payment_status === "REFUND_PENDING" || o.payment_status === "REFUNDED";
}

export function totalBurgers(o: OrderView): number {
  return o.items.reduce((n, i) => n + i.quantity, 0);
}

/** Editing is more restrictive than cancellation: only before preparation and payment. */
export function canEdit(o: OrderView): boolean {
  return (o.order_status === "NEW" || o.order_status === "CONFIRMED") &&
    (o.payment_status === "UNPAID" || o.payment_status === "CASH_PENDING") && !o.receipt_submitted;
}
