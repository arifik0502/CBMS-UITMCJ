import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { CartBar } from "../components/CartBar";
import { CartDrawer } from "../components/CartDrawer";
import { Header, LoadingScreen, Notice, Pill, Shell } from "../components/ui";
import { getOrder } from "../lib/api";
import { mapLimit } from "../lib/async";
import { getOrderability } from "../lib/availability";
import { useCart } from "../lib/cart";
import { subtotalCents } from "../lib/cartLogic";
import { formatRM } from "../lib/format";
import {
  countByTab, forgetOrder, markUnavailable, orderTab, readHistory, sortNewestFirst, updateFromView,
  type OrderTab, type StoredOrder
} from "../lib/orderHistory";
import { buildReorder } from "../lib/reorder";
import { ORDER_LABEL, PAYMENT_LABEL } from "../lib/statusText";
import { useStorefront } from "../lib/useStorefront";

const TABS: { id: OrderTab; label: string }[] = [
  { id: "active", label: "Active" },
  { id: "completed", label: "Completed" },
  { id: "cancelled", label: "Cancelled" }
];

const EMPTY_TEXT: Record<OrderTab, string> = {
  active: "No active orders. Anything you place shows up here while it is on its way.",
  completed: "No completed orders yet.",
  cancelled: "No cancelled orders."
};

function formatPlaced(iso: string): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return d.toLocaleString("en-MY", {
    timeZone: "Asia/Kuala_Lumpur", day: "numeric", month: "short", hour: "numeric", minute: "2-digit"
  });
}

function statusTone(o: StoredOrder): "good" | "warn" | "bad" | "neutral" {
  if (o.status === "DELIVERED") return "good";
  if (o.status === "CANCELLED") return "bad";
  return "warn";
}

export default function OrdersPage() {
  const cart = useCart();
  const { data: storefront, cachedOnly } = useStorefront();
  const [orders, setOrders] = useState<StoredOrder[]>(() => sortNewestFirst(readHistory()));
  const [tab, setTab] = useState<OrderTab>(() => {
    const counts = countByTab(readHistory());
    return counts.active > 0 || counts.completed === 0 ? "active" : "completed";
  });
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState<{ tone: "info" | "error" | "success"; text: string } | null>(null);
  const [clock, setClock] = useState(() => new Date());
  const [reorderNote, setReorderNote] = useState<string | undefined>(undefined);
  const busy = useRef(false);

  const refresh = useCallback(async (onlyActive: boolean) => {
    if (busy.current) return;
    const current = readHistory();
    const targets = current.filter((o) =>
      !o.unavailable && (onlyActive ? orderTab(o) === "active" : orderTab(o) === "active" || !o.items || !o.orderNo)
    );
    if (targets.length === 0) { setLoading(false); return; }
    busy.current = true;
    const results = await mapLimit(targets, 4, async (o) => {
      try {
        return { token: o.token, view: await getOrder(o.token), notFound: false };
      } catch (e) {
        return { token: o.token, view: null, notFound: (e as { message?: string })?.message === "ORDER_NOT_FOUND" };
      }
    });
    for (const r of results) {
      if (r.view) updateFromView(r.token, r.view);
      else if (r.notFound) markUnavailable(r.token);
    }
    setOrders(sortNewestFirst(readHistory()));
    setLoading(false);
    busy.current = false;
  }, []);

  useEffect(() => {
    void refresh(false);
    const id = window.setInterval(() => { if (!document.hidden) void refresh(true); }, 15000);
    const clockId = window.setInterval(() => setClock(new Date()), 15000);
    return () => { window.clearInterval(id); window.clearInterval(clockId); };
  }, [refresh]);

  const counts = useMemo(() => countByTab(orders), [orders]);
  const shown = useMemo(() => orders.filter((o) => orderTab(o) === tab), [orders, tab]);

  const products = useMemo(() => (storefront?.product ? [storefront.product] : []), [storefront]);
  const orderability = storefront ? getOrderability(storefront, cachedOnly, clock) : null;
  const maxQty = orderability?.maxQty ?? 1;
  const cartTotal = subtotalCents(cart.lines, (id) => products.find((p) => p.id === id)?.price_cents);

  function reorder(o: StoredOrder) {
    if (!storefront?.product) {
      setNotice({ tone: "error", text: "The menu has not loaded yet. Try again in a moment." });
      return;
    }
    if (cart.count > 0 && !window.confirm("Replace the items in your cart with this order?")) return;
    const result = buildReorder(
      o.items ?? [],
      [{ id: storefront.product.id, name: storefront.product.name }],
      maxQty
    );
    if (result.lines.length === 0) {
      setNotice({ tone: "error", text: "None of the items in that order are on the menu any more." });
      return;
    }
    cart.replace(result.lines);
    setNotice(null);
    const parts: string[] = [];
    if (result.skipped.length > 0) parts.push(`Not on the menu any more: ${result.skipped.join(", ")}.`);
    if (result.capped) parts.push(`Quantity reduced to ${maxQty}, the most you can order right now.`);
    setReorderNote(parts.length > 0 ? parts.join(" ") : undefined);
    cart.openDrawer();
  }

  function remove(o: StoredOrder) {
    if (!window.confirm(`Remove ${o.orderNo || "this order"} from this device? The order itself is not affected.`)) return;
    forgetOrder(o.token);
    setOrders(sortNewestFirst(readHistory()));
  }

  return (
    <Shell>
      <Header back="/" />
      <h1 className="font-display text-3xl font-extrabold tracking-tight">My orders</h1>
      <p className="mt-1 text-sm text-muted">Orders placed on this device. They are not tied to an account.</p>

      <div role="tablist" aria-label="Order status" className="mt-4 grid grid-cols-3 gap-2">
        {TABS.map((t) => (
          <button
            key={t.id}
            role="tab"
            id={`tab-${t.id}`}
            aria-selected={tab === t.id}
            aria-controls="orders-panel"
            onClick={() => setTab(t.id)}
            className={`min-h-12 rounded-xl border-2 border-ink px-2 py-2 text-sm font-bold ${tab === t.id ? "bg-ink text-bun" : "bg-paper"}`}
          >
            {t.label}
            <span className="ml-1 opacity-70">({counts[t.id]})</span>
          </button>
        ))}
      </div>

      {notice && <div className="mt-4"><Notice tone={notice.tone}>{notice.text}</Notice></div>}

      <section id="orders-panel" role="tabpanel" aria-labelledby={`tab-${tab}`} className="mt-4 space-y-4">
        {loading && orders.length > 0 && shown.length === 0 && <LoadingScreen label="Updating your orders" />}

        {shown.length === 0 && !loading && (
          <div className="ticket space-y-3 p-5 text-center">
            <p className="text-sm font-semibold">{EMPTY_TEXT[tab]}</p>
            <Link to="/" className="inline-flex min-h-11 items-center rounded-full border-2 border-ink bg-bun px-5 py-2 text-sm font-bold">
              Browse the menu
            </Link>
          </div>
        )}

        {shown.map((o) => {
          const unavailable = o.unavailable === true;
          const pay = o.paymentStatus ? PAYMENT_LABEL[o.paymentStatus] : null;
          const needsPayment = orderTab(o) === "active" && (o.paymentStatus === "UNPAID" || o.paymentStatus === "REJECTED");
          const terminal = orderTab(o) !== "active";
          return (
            <article key={o.token} className="ticket p-4" aria-label={`Order ${o.orderNo || "loading"}`}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-display text-lg font-extrabold">{o.orderNo || "Loading…"}</p>
                  {o.createdAt && <p className="text-xs text-muted">{formatPlaced(o.createdAt)}</p>}
                </div>
                {unavailable ? (
                  <Pill tone="neutral">Unavailable</Pill>
                ) : o.status ? (
                  <Pill tone={statusTone(o)}>{ORDER_LABEL[o.status] ?? o.status}</Pill>
                ) : null}
              </div>

              {unavailable ? (
                <p className="mt-3 text-sm text-muted">We could not find this order any more.</p>
              ) : (
                <>
                  {o.items && o.items.length > 0 && (
                    <ul className="mt-3 space-y-1 text-sm">
                      {o.items.map((i) => <li key={i.name}>{i.quantity} × {i.name}</li>)}
                    </ul>
                  )}
                  <div className="mt-3 flex items-center justify-between gap-3 text-sm">
                    <span className="text-muted">{pay?.text ?? ""}</span>
                    {o.totalCents > 0 && <span className="font-display text-lg font-extrabold">{formatRM(o.totalCents)}</span>}
                  </div>
                </>
              )}

              <div className="mt-4 grid grid-cols-2 gap-2">
                {!unavailable && (
                  <Link to={`/track/${o.token}`}
                    className="inline-flex min-h-11 items-center justify-center rounded-xl border-2 border-ink bg-paper px-3 py-2 text-sm font-bold shadow-[2px_2px_0_0_var(--color-ink)]">
                    Track order
                  </Link>
                )}
                {!unavailable && needsPayment && (
                  <Link to={`/pay/${o.token}`}
                    className="inline-flex min-h-11 items-center justify-center rounded-xl border-2 border-ink bg-ink px-3 py-2 text-sm font-bold text-bun shadow-[2px_2px_0_0_var(--color-ketchup)]">
                    Pay now
                  </Link>
                )}
                {!unavailable && terminal && (
                  <button type="button" onClick={() => reorder(o)} disabled={!o.items || o.items.length === 0}
                    className="min-h-11 rounded-xl border-2 border-ink bg-ink px-3 py-2 text-sm font-bold text-bun shadow-[2px_2px_0_0_var(--color-ketchup)] disabled:opacity-50">
                    Reorder
                  </button>
                )}
                {(terminal || unavailable) && (
                  <button type="button" onClick={() => remove(o)}
                    className="min-h-11 rounded-xl border-2 border-ketchup bg-paper px-3 py-2 text-sm font-bold text-ketchup">
                    Remove
                  </button>
                )}
              </div>
            </article>
          );
        })}
      </section>

      <CartBar count={cart.count} totalCents={cartTotal} onOpen={cart.openDrawer} />
      <CartDrawer
        products={products}
        maxQty={maxQty}
        canCheckout={orderability?.canOrder ?? false}
        notice={[reorderNote, orderability && !orderability.canOrder ? orderability.message : undefined]
          .filter(Boolean).join(" ") || undefined}
      />
    </Shell>
  );
}
