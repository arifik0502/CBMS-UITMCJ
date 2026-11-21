// Pure cart helpers (no React, no storage) so they are easy to reason about and test.

export interface CartLine {
  productId: string;
  quantity: number;
}

export const CART_MAX_LINES = 20;
const HARD_MAX_QTY = 99;

function cleanQty(q: unknown): number | null {
  return typeof q === "number" && Number.isInteger(q) && q >= 1 && q <= HARD_MAX_QTY ? q : null;
}

/** Parses untrusted JSON from localStorage. Anything malformed is dropped, never thrown. */
export function parseCart(raw: string | null): CartLine[] {
  if (!raw) return [];
  try {
    const data: unknown = JSON.parse(raw);
    if (!Array.isArray(data)) return [];
    const seen = new Set<string>();
    const lines: CartLine[] = [];
    for (const item of data) {
      if (typeof item !== "object" || item === null) continue;
      const { productId, quantity } = item as Record<string, unknown>;
      const q = cleanQty(quantity);
      if (typeof productId !== "string" || !productId || q === null || seen.has(productId)) continue;
      seen.add(productId);
      lines.push({ productId, quantity: q });
      if (lines.length >= CART_MAX_LINES) break;
    }
    return lines;
  } catch {
    return [];
  }
}

/** Adds `qty` of a product, never exceeding `max`. */
export function addLine(lines: CartLine[], productId: string, qty: number, max: number): CartLine[] {
  const limit = Math.max(1, Math.min(max, HARD_MAX_QTY));
  const existing = lines.find((l) => l.productId === productId);
  if (existing) {
    return lines.map((l) =>
      l.productId === productId ? { ...l, quantity: Math.min(limit, l.quantity + qty) } : l
    );
  }
  if (lines.length >= CART_MAX_LINES) return lines;
  return [...lines, { productId, quantity: Math.min(limit, Math.max(1, qty)) }];
}

/** Sets an exact quantity. Quantity below 1 removes the line. */
export function setLineQuantity(lines: CartLine[], productId: string, qty: number, max: number): CartLine[] {
  if (qty < 1) return removeLine(lines, productId);
  const limit = Math.max(1, Math.min(max, HARD_MAX_QTY));
  const next = Math.min(limit, qty);
  if (!lines.some((l) => l.productId === productId)) return addLine(lines, productId, next, max);
  return lines.map((l) => (l.productId === productId ? { ...l, quantity: next } : l));
}

export function removeLine(lines: CartLine[], productId: string): CartLine[] {
  return lines.filter((l) => l.productId !== productId);
}

export function itemCount(lines: CartLine[]): number {
  return lines.reduce((sum, l) => sum + l.quantity, 0);
}

export function subtotalCents(lines: CartLine[], priceOf: (productId: string) => number | undefined): number {
  return lines.reduce((sum, l) => sum + (priceOf(l.productId) ?? 0) * l.quantity, 0);
}
