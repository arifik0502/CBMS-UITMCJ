import { LAST_ORDER_KEY, safeGet, safeSet } from "./storage";
import type { OrderStatus, OrderView, PaymentStatus } from "./types";

/** What this device remembers about an order it placed. No login: it is only this browser. */
export interface StoredOrder {
  token: string;
  orderNo: string;
  totalCents: number;
  createdAt: string;
  status?: OrderStatus;
  paymentStatus?: PaymentStatus;
  items?: { name: string; quantity: number }[];
  /** The server no longer knows this token (ORDER_NOT_FOUND). Listed under Cancelled so it can be removed. */
  unavailable?: boolean;
}

export type OrderTab = "active" | "completed" | "cancelled";

export const HISTORY_KEY = "cbms:orders:v1";
export const HISTORY_MAX = 40;

const TOKEN_RE = /^[\w.~-]{6,200}$/;

export function orderTab(o: StoredOrder): OrderTab {
  return o.unavailable ? "cancelled" : tabOf(o.status);
}

export function tabOf(status: OrderStatus | undefined): OrderTab {
  if (status === "DELIVERED") return "completed";
  if (status === "CANCELLED") return "cancelled";
  return "active"; // unknown (not refreshed yet) counts as active so it gets refreshed
}

function cleanItems(value: unknown): { name: string; quantity: number }[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const out: { name: string; quantity: number }[] = [];
  for (const it of value) {
    if (typeof it !== "object" || it === null) continue;
    const { name, quantity } = it as Record<string, unknown>;
    if (typeof name === "string" && name && typeof quantity === "number" && Number.isInteger(quantity) && quantity > 0) {
      out.push({ name: name.slice(0, 120), quantity });
    }
  }
  return out;
}

/** Parses untrusted JSON from localStorage. Malformed entries are dropped, never thrown. */
export function parseHistory(raw: string | null): StoredOrder[] {
  if (!raw) return [];
  try {
    const data: unknown = JSON.parse(raw);
    if (!Array.isArray(data)) return [];
    const seen = new Set<string>();
    const out: StoredOrder[] = [];
    for (const item of data) {
      if (typeof item !== "object" || item === null) continue;
      const o = item as Record<string, unknown>;
      if (typeof o.token !== "string" || !TOKEN_RE.test(o.token) || seen.has(o.token)) continue;
      seen.add(o.token);
      out.push({
        token: o.token,
        orderNo: typeof o.orderNo === "string" ? o.orderNo.slice(0, 40) : "",
        totalCents: typeof o.totalCents === "number" && Number.isFinite(o.totalCents) ? Math.trunc(o.totalCents) : 0,
        createdAt: typeof o.createdAt === "string" ? o.createdAt : "",
        status: typeof o.status === "string" ? (o.status as OrderStatus) : undefined,
        paymentStatus: typeof o.paymentStatus === "string" ? (o.paymentStatus as PaymentStatus) : undefined,
        items: cleanItems(o.items),
        unavailable: o.unavailable === true ? true : undefined
      });
      if (out.length >= HISTORY_MAX) break;
    }
    return out;
  } catch {
    return [];
  }
}

/** Newest first, capped. Inserting an existing token replaces it. */
export function upsertOrder(list: StoredOrder[], entry: StoredOrder): StoredOrder[] {
  const rest = list.filter((o) => o.token !== entry.token);
  const next = [entry, ...rest];
  return next.slice(0, HISTORY_MAX);
}

export function mergeView(entry: StoredOrder, view: OrderView): StoredOrder {
  return {
    ...entry,
    orderNo: view.order_no,
    totalCents: view.total_cents,
    createdAt: view.created_at,
    status: view.order_status,
    paymentStatus: view.payment_status,
    items: view.items.map((i) => ({ name: i.name, quantity: i.quantity })),
    unavailable: undefined
  };
}

export function sortNewestFirst(list: StoredOrder[]): StoredOrder[] {
  return [...list].sort((a, b) => (b.createdAt || "").localeCompare(a.createdAt || ""));
}

export function countByTab(list: StoredOrder[]): Record<OrderTab, number> {
  const counts: Record<OrderTab, number> = { active: 0, completed: 0, cancelled: 0 };
  for (const o of list) counts[orderTab(o)]++;
  return counts;
}

// ---- storage wrappers ----

function save(list: StoredOrder[]): void {
  safeSet(HISTORY_KEY, JSON.stringify(list));
}

/** Reads history, folding in the single "last order" token older versions saved. */
export function readHistory(): StoredOrder[] {
  const list = parseHistory(safeGet(HISTORY_KEY));
  const last = safeGet(LAST_ORDER_KEY);
  if (last && TOKEN_RE.test(last) && !list.some((o) => o.token === last)) {
    const withLast = [...list, { token: last, orderNo: "", totalCents: 0, createdAt: "" }];
    save(withLast);
    return withLast;
  }
  return list;
}

export function rememberOrder(entry: StoredOrder): void {
  save(upsertOrder(readHistory(), entry));
}

/** Refreshes a stored order from a live view. Orders this device never saved are ignored. */
export function updateFromView(token: string, view: OrderView): void {
  const list = readHistory();
  const existing = list.find((o) => o.token === token);
  if (!existing) return;
  save(list.map((o) => (o.token === token ? mergeView(o, view) : o)));
}

export function markUnavailable(token: string): void {
  save(readHistory().map((o) => (o.token === token ? { ...o, unavailable: true } : o)));
}

export function forgetOrder(token: string): void {
  save(readHistory().filter((o) => o.token !== token));
}
