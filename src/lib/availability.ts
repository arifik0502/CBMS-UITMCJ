import { tripIsSelectable } from "./trips";
import type { Storefront, StorefrontProduct } from "./types";

export interface Orderability {
  canOrder: boolean;
  /** Why ordering is unavailable (empty when it is available). */
  message: string;
  /** Maximum total number of items allowed in one order. */
  maxQty: number;
}

export function storefrontProducts(data: Storefront): StorefrontProduct[] {
  if (Array.isArray(data.products) && data.products.length > 0) return data.products;
  return data.product ? [data.product] : [];
}

export function getOrderability(data: Storefront, cachedOnly: boolean, now: Date): Orderability {
  const products = storefrontProducts(data);
  const anyStock = products.some((p) => p.available_stock > 0);
  const maxQty = Math.max(1, data.max_qty_per_order);
  const anyTripOpen = data.trips.some((t) => tripIsSelectable(t, now));
  const canOrder = !cachedOnly && data.accepting_orders && products.length > 0 && anyStock && anyTripOpen;
  if (canOrder) return { canOrder, message: "", maxQty };

  const message = cachedOnly
    ? "You're viewing a saved menu. Live stock and delivery times cannot be verified, so ordering is disabled until the website reconnects."
    : !anyStock
      ? "We have sold out this session. Check back next time."
      : data.accepting_orders && !anyTripOpen
        ? "All of today's trips have closed. See you next session."
        : "Ordering is closed right now.";

  return { canOrder, message, maxQty };
}
