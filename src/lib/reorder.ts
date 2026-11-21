import type { CartLine } from "./cartLogic";

export interface CatalogueEntry {
  id: string;
  name: string;
}

export interface ReorderResult {
  lines: CartLine[];
  /** Names that are not on the menu any more. */
  skipped: string[];
  /** True when a quantity was reduced to fit the per-order limit or current stock. */
  capped: boolean;
}

const norm = (s: string) => s.trim().toLowerCase();

/**
 * Turns a past order's items into cart lines for today's menu.
 * Orders only store product names, so items are matched by name. When the backend
 * grows a multi-product catalogue, order items can carry a product id and this
 * function is the only place that needs to change.
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
    if (!match || !Number.isFinite(item.quantity) || item.quantity < 1) {
      if (!skipped.includes(item.name)) skipped.push(item.name);
      continue;
    }
    totals.set(match.id, (totals.get(match.id) ?? 0) + Math.trunc(item.quantity));
  }
  const limit = Math.max(1, maxQty);
  let capped = false;
  const lines: CartLine[] = [];
  for (const [productId, quantity] of totals) {
    if (quantity > limit) capped = true;
    lines.push({ productId, quantity: Math.min(quantity, limit) });
  }
  return { lines, skipped, capped };
}
