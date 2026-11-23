import type { CartLine } from "./cartLogic";

export interface CatalogueEntry {
  id: string;
  name: string;
  availableStock?: number;
}

export interface ReorderResult {
  lines: CartLine[];
  /** Names that are not on the menu any more or have no stock. */
  skipped: string[];
  /** True when a quantity was reduced to fit current stock or the total order limit. */
  capped: boolean;
}

const norm = (s: string) => s.trim().toLowerCase();

/**
 * Turns a past order's items into cart lines for today's menu.
 * Product names are used because the customer order-history snapshot stores names.
 * Quantities are limited by live stock and by the global maximum items per order.
 */
export function buildReorder(
  items: { name: string; quantity: number }[],
  catalogue: CatalogueEntry[],
  maxQty: number
): ReorderResult {
  const totals = new Map<string, number>();
  const skipped: string[] = [];
  for (const item of items) {
    const match = catalogue.find((p) => norm(p.name) === norm(item.name));
    const stock = match?.availableStock ?? Number.MAX_SAFE_INTEGER;
    if (!match || stock <= 0 || !Number.isFinite(item.quantity) || item.quantity < 1) {
      if (!skipped.includes(item.name)) skipped.push(item.name);
      continue;
    }
    totals.set(match.id, (totals.get(match.id) ?? 0) + Math.trunc(item.quantity));
  }

  let remaining = Math.max(1, maxQty);
  let capped = false;
  const lines: CartLine[] = [];

  for (const [productId, requested] of totals) {
    if (remaining <= 0) {
      capped = true;
      break;
    }
    const product = catalogue.find((p) => p.id === productId);
    const stock = Math.max(0, product?.availableStock ?? requested);
    const quantity = Math.min(requested, stock, remaining);
    if (quantity < requested) capped = true;
    if (quantity > 0) {
      lines.push({ productId, quantity });
      remaining -= quantity;
    }
  }
  return { lines, skipped, capped };
}
