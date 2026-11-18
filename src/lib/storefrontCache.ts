import type { Storefront } from "./types";
import { safeGet, safeSet } from "./storage";

const CACHE_KEY = "cbms:publicMenu:v1";
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;

export interface CachedMenu {
  business_name: string;
  product: NonNullable<Storefront["product"]>;
  saved_at: number;
}

/** Persist only public menu details. Never store stock, orders, customer data or receipts. */
export function savePublicMenu(data: Storefront): void {
  if (!data.product) {
    safeSet(CACHE_KEY, "");
    return;
  }
  const snapshot: CachedMenu = {
    business_name: data.business_name,
    product: {
      id: data.product.id,
      name: data.product.name,
      description: data.product.description,
      price_cents: data.product.price_cents
    },
    saved_at: Date.now()
  };
  safeSet(CACHE_KEY, JSON.stringify(snapshot));
}

export function readPublicMenu(): CachedMenu | null {
  const raw = safeGet(CACHE_KEY);
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<CachedMenu>;
    const product = value.product;
    if (
      typeof value.business_name !== "string" ||
      !product ||
      typeof product.id !== "string" ||
      typeof product.name !== "string" ||
      typeof product.description !== "string" ||
      !Number.isInteger(product.price_cents) ||
      typeof value.saved_at !== "number" ||
      Date.now() - value.saved_at > MAX_AGE_MS ||
      value.saved_at > Date.now() + 60_000
    ) return null;
    return value as CachedMenu;
  } catch {
    return null;
  }
}

/** Convert the saved public menu into a deliberately read-only storefront. */
export function toReadOnlyStorefront(menu: CachedMenu): Storefront {
  return {
    business_name: menu.business_name,
    accepting_orders: false,
    max_qty_per_order: 0,
    payment_expiry_minutes: 0,
    duitnow_account_name: "",
    product: menu.product,
    available_stock: 0,
    buildings: [],
    trips: []
  };
}
