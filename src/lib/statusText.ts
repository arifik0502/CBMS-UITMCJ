import type { OrderStatus, PaymentStatus } from "./types";

export const STEPS: { status: OrderStatus; label: string }[] = [
  { status: "NEW", label: "Order received" },
  { status: "CONFIRMED", label: "Order confirmed" },
  { status: "PREPARING", label: "Preparing" },
  { status: "READY", label: "Ready" },
  { status: "ASSIGNED", label: "Assigned to seller" },
  { status: "OUT_FOR_DELIVERY", label: "Out for delivery" },
  { status: "DELIVERED", label: "Delivered" }
];

export function stepIndex(s: OrderStatus): number {
  return STEPS.findIndex((x) => x.status === s);
}

export const ORDER_LABEL: Record<OrderStatus, string> = {
  NEW: "Received", CONFIRMED: "Confirmed", PREPARING: "Preparing", READY: "Ready",
  ASSIGNED: "Assigned", OUT_FOR_DELIVERY: "On the way", DELIVERED: "Delivered", CANCELLED: "Cancelled"
};

export const PAYMENT_LABEL: Record<PaymentStatus, { text: string; tone: "good" | "warn" | "bad" | "neutral" }> = {
  UNPAID: { text: "Waiting for your payment", tone: "warn" },
  PENDING_VERIFICATION: { text: "Checking your payment", tone: "warn" },
  VERIFIED: { text: "Paid", tone: "good" },
  REJECTED: { text: "Receipt not accepted", tone: "bad" },
  CASH_PENDING: { text: "Pay cash on delivery", tone: "neutral" },
  CASH_COLLECTED: { text: "Paid in cash", tone: "good" },
  REFUND_PENDING: { text: "Refund in progress", tone: "warn" },
  REFUNDED: { text: "Refunded", tone: "neutral" }
};
