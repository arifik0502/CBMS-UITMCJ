import { tripIsSelectable } from "./trips";
import type { Storefront } from "./types";

export interface Orderability {
  canOrder: boolean;
  /** Why ordering is unavailable (empty when it is available). */
  message: string;
  maxQty: number;
}

export function getOrderability(data: Storefront, cachedOnly: boolean, now: Date): Orderability {
  const available = data.available_stock;
  const maxQty = Math.max(1, Math.min(data.max_qty_per_order, available));
  const anyTripOpen = data.trips.some((t) => tripIsSelectable(t, now));
  const canOrder = !cachedOnly && data.accepting_orders && data.product !== null && available > 0 && anyTripOpen;
  if (canOrder) return { canOrder, message: "", maxQty };
  const message = cachedOnly
    ? "You're viewing a saved menu. Live stock and delivery times cannot be verified, so ordering is disabled until the website reconnects."
    : available <= 0
      ? "We have sold out this session. Check back next time."
      : data.accepting_orders && !anyTripOpen
        ? "All of today's trips have closed. See you next session."
        : "Ordering is closed right now.";
  return { canOrder, message, maxQty };
}
