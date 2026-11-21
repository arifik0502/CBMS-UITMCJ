import { safeGet, safeSet } from "./storage";
import type { TicketStatus, TicketView } from "./types";

/** Tickets this device filed. Customers have no login, so this browser is the only place they live. */
export interface StoredTicket {
  token: string;
  ticketNo: string;
  /** Tracking token of the order the ticket is about, so it can be shown under that order. */
  orderToken: string | null;
  orderNo: string | null;
  message: string;
  status: TicketStatus;
  createdAt: string;
  resolvedAt: string | null;
}

export const TICKETS_KEY = "cbms:tickets:v1";
export const CONTACT_KEY = "cbms:contact:v1";
export const TICKETS_MAX = 40;

const TOKEN_RE = /^[\w.~-]{6,200}$/;

export function parseTickets(raw: string | null): StoredTicket[] {
  if (!raw) return [];
  try {
    const data: unknown = JSON.parse(raw);
    if (!Array.isArray(data)) return [];
    const seen = new Set<string>();
    const out: StoredTicket[] = [];
    for (const item of data) {
      if (typeof item !== "object" || item === null) continue;
      const o = item as Record<string, unknown>;
      if (typeof o.token !== "string" || !TOKEN_RE.test(o.token) || seen.has(o.token)) continue;
      if (typeof o.ticketNo !== "string" || !o.ticketNo) continue;
      seen.add(o.token);
      out.push({
        token: o.token,
        ticketNo: o.ticketNo.slice(0, 20),
        orderToken: typeof o.orderToken === "string" && TOKEN_RE.test(o.orderToken) ? o.orderToken : null,
        orderNo: typeof o.orderNo === "string" ? o.orderNo.slice(0, 40) : null,
        message: typeof o.message === "string" ? o.message.slice(0, 160) : "",
        status: o.status === "RESOLVED" ? "RESOLVED" : "OPEN",
        createdAt: typeof o.createdAt === "string" ? o.createdAt : "",
        resolvedAt: typeof o.resolvedAt === "string" ? o.resolvedAt : null
      });
      if (out.length >= TICKETS_MAX) break;
    }
    return out;
  } catch {
    return [];
  }
}

/** Newest first, capped. Re-inserting a token replaces it. */
export function upsertTicket(list: StoredTicket[], entry: StoredTicket): StoredTicket[] {
  return [entry, ...list.filter((t) => t.token !== entry.token)].slice(0, TICKETS_MAX);
}

export function fromView(view: TicketView, orderToken: string | null): StoredTicket {
  return {
    token: view.token,
    ticketNo: view.ticket_no,
    orderToken,
    orderNo: view.order_no,
    message: view.message.slice(0, 160),
    status: view.status,
    createdAt: view.created_at,
    resolvedAt: view.resolved_at
  };
}

/** Applies fresh server data to what is stored. Tickets the server did not return are left alone. */
export function applyViews(list: StoredTicket[], views: TicketView[]): StoredTicket[] {
  const byToken = new Map(views.map((v) => [v.token, v]));
  return list.map((t) => {
    const v = byToken.get(t.token);
    return v ? { ...fromView(v, t.orderToken), orderNo: v.order_no ?? t.orderNo } : t;
  });
}

export function ticketsForOrder(list: StoredTicket[], orderToken: string): StoredTicket[] {
  return list.filter((t) => t.orderToken === orderToken);
}

export function openCount(list: StoredTicket[]): number {
  return list.filter((t) => t.status === "OPEN").length;
}

export function readTickets(): StoredTicket[] {
  return parseTickets(safeGet(TICKETS_KEY));
}

function save(list: StoredTicket[]): void {
  safeSet(TICKETS_KEY, JSON.stringify(list));
}

export function rememberTicket(view: TicketView, orderToken: string | null): void {
  save(upsertTicket(readTickets(), fromView(view, orderToken)));
}

export function refreshStoredTickets(views: TicketView[]): StoredTicket[] {
  const next = applyViews(readTickets(), views);
  save(next);
  return next;
}

export function forgetTicket(token: string): void {
  save(readTickets().filter((t) => t.token !== token));
}

// ---- contact details, so a returning customer does not retype them ----

export interface Contact { name: string; phone: string }

export function readContact(): Contact {
  try {
    const raw: unknown = JSON.parse(safeGet(CONTACT_KEY) ?? "null");
    if (typeof raw === "object" && raw !== null) {
      const { name, phone } = raw as Record<string, unknown>;
      return { name: typeof name === "string" ? name.slice(0, 80) : "", phone: typeof phone === "string" ? phone.slice(0, 20) : "" };
    }
  } catch { /* fall through */ }
  return { name: "", phone: "" };
}

export function saveContact(c: Contact): void {
  safeSet(CONTACT_KEY, JSON.stringify({ name: c.name.trim().slice(0, 80), phone: c.phone.trim().slice(0, 20) }));
}
