import { storefrontProducts } from "./availability";
import type { Storefront, StorefrontProduct } from "./types";
import { safeGet, safeSet } from "./storage";

const CACHE_KEY = "cbms:publicMenu:v2";
const OLD_CACHE_KEY = "cbms:publicMenu:v1";
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;

export interface CachedMenuProduct {
  id: string;
  name: string;
  description: string;
  price_cents: number;
  image_url: string | null;
}

export interface CachedMenu {
  business_name: string;
  products: CachedMenuProduct[];
  saved_at: number;
}

function publicProduct(product: StorefrontProduct): CachedMenuProduct {
  return {
    id: product.id,
    name: product.name,
    description: product.description,
    price_cents: product.price_cents,
    image_url: product.image_url ?? null
  };
}

/** Persist only public menu details. Never store stock, orders, customer data or receipts. */
export function savePublicMenu(data: Storefront): void {
  const products = storefrontProducts(data);
  if (products.length === 0) {
    safeSet(CACHE_KEY, "");
    return;
  }
  const snapshot: CachedMenu = {
    business_name: data.business_name,
    products: products.map(publicProduct),
    saved_at: Date.now()
  };
  safeSet(CACHE_KEY, JSON.stringify(snapshot));
}

function validProduct(product: unknown): product is CachedMenuProduct {
  if (!product || typeof product !== "object") return false;
  const p = product as Partial<CachedMenuProduct>;
  return typeof p.id === "string" &&
    typeof p.name === "string" &&
    typeof p.description === "string" &&
    Number.isInteger(p.price_cents) &&
    (typeof p.image_url === "string" || p.image_url === null || p.image_url === undefined);
}

function parseNew(raw: string): CachedMenu | null {
  try {
    const value = JSON.parse(raw) as Partial<CachedMenu>;
    if (
      typeof value.business_name !== "string" ||
      !Array.isArray(value.products) ||
      value.products.length === 0 ||
      !value.products.every(validProduct) ||
      typeof value.saved_at !== "number" ||
      Date.now() - value.saved_at > MAX_AGE_MS ||
      value.saved_at > Date.now() + 60_000
    ) return null;
    return {
      business_name: value.business_name,
      products: value.products.map((p) => ({ ...p, image_url: p.image_url ?? null })),
      saved_at: value.saved_at
    };
  } catch {
    return null;
  }
}

/** Reads v2, with a one-time compatibility path for the old single-product cache. */
export function readPublicMenu(): CachedMenu | null {
  const current = safeGet(CACHE_KEY);
  if (current) {
    const parsed = parseNew(current);
    if (parsed) return parsed;
  }

  const old = safeGet(OLD_CACHE_KEY);
  if (!old) return null;
  try {
    const value = JSON.parse(old) as {
      business_name?: unknown;
      product?: unknown;
      saved_at?: unknown;
    };
    if (
      typeof value.business_name !== "string" ||
      !validProduct(value.product) ||
      typeof value.saved_at !== "number" ||
      Date.now() - value.saved_at > MAX_AGE_MS
    ) return null;
    return {
      business_name: value.business_name,
      products: [{ ...value.product, image_url: value.product.image_url ?? null }],
      saved_at: value.saved_at
    };
  } catch {
    return null;
  }
}

/** Convert the saved public menu into a deliberately read-only storefront. */
export function toReadOnlyStorefront(menu: CachedMenu): Storefront {
  const products: StorefrontProduct[] = menu.products.map((p) => ({
    ...p,
    available_stock: 0
  }));
  return {
    business_name: menu.business_name,
    accepting_orders: false,
    max_qty_per_order: 0,
    payment_expiry_minutes: 0,
    duitnow_account_name: "",
    products,
    product: products[0] ?? null,
    available_stock: 0,
    buildings: [],
    trips: []
  };
}
