import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { CartBar } from "../components/CartBar";
import { CartDrawer } from "../components/CartDrawer";
import { Stepper } from "../components/Stepper";
import { Button, ErrorScreen, LoadingScreen, Pill, Shell } from "../components/ui";
import { useCart } from "../lib/cart";
import { subtotalCents } from "../lib/cartLogic";
import { formatRM } from "../lib/format";
import { countByTab, readHistory } from "../lib/orderHistory";
import { getOrderability } from "../lib/availability";
import { tripHasEndedToday } from "../lib/trips";
import { useStorefront } from "../lib/useStorefront";

export default function Home() {
  const { data, error, loading, reload, cachedOnly } = useStorefront();
  const cart = useCart();
  const [clock, setClock] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setClock(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, []);

  const activeOrders = useMemo(() => countByTab(readHistory()).active, []);

  // Catalogue-ready: today the backend sells one product, but the UI renders a list.
  const products = useMemo(() => (data?.product ? [data.product] : []), [data]);
  const available = data?.available_stock ?? 0;
  const maxQty = Math.max(1, Math.min(data?.max_qty_per_order ?? 1, available));

  // If stock or the per-order limit dropped below what is in the cart, trim it quietly.
  useEffect(() => {
    if (!data || cachedOnly) return;
    for (const l of cart.lines) {
      if (products.some((p) => p.id === l.productId) && l.quantity > maxQty && available > 0) {
        cart.setQuantity(l.productId, maxQty, maxQty);
      }
    }
  }, [data, cachedOnly, products, maxQty, available, cart]);

  if (loading) return <Shell><LoadingScreen label="Loading menu" /></Shell>;
  if (error || !data) return <Shell><ErrorScreen message={error ?? "No data."} onRetry={reload} /></Shell>;

  const soldOut = available <= 0;
  const { canOrder, message: closedMessage } = getOrderability(data, cachedOnly, clock);
  const low = available > 0 && available <= 10;
  const cartTotal = subtotalCents(cart.lines, (id) => products.find((p) => p.id === id)?.price_cents);

  return (
    <Shell>
      <header className="flex items-center justify-between gap-2 py-3">
        <span className="font-display text-xl font-extrabold tracking-tight">{data.business_name}</span>
        <div className="flex items-center gap-2">
          <Link to="/orders" className="relative min-h-11 rounded-full border-2 border-ink bg-paper px-4 py-2 text-sm font-bold">
            My orders
            {activeOrders > 0 && (
              <span className="absolute -right-1.5 -top-1.5 grid size-5 place-items-center rounded-full bg-ketchup text-[0.7rem] font-extrabold text-white"
                aria-label={`${activeOrders} active order${activeOrders === 1 ? "" : "s"}`}>{activeOrders}</span>
            )}
          </Link>
        </div>
      </header>

      <section className="pt-4 text-center">
        <h1 className="font-display text-[2.6rem] font-extrabold leading-[1.02] tracking-tight">
          Hot crispy burgers at your dorm door.
        </h1>
        <div className="relative mx-auto mt-6 max-w-xs">
          <img src="/burger.svg" alt="Crispy chicken burger" width={400} height={360} className="burger-drop w-full" />
        </div>
      </section>

      <section className="mt-6 space-y-3" aria-labelledby="menu-title">
        <div className="flex items-center justify-between gap-3">
          <h2 id="menu-title" className="font-display text-2xl font-extrabold">Menu</h2>
          {cachedOnly ? (
            <Pill tone="warn">Saved menu · read-only</Pill>
          ) : !data.accepting_orders ? (
            <Pill tone="bad">Not taking orders</Pill>
          ) : soldOut ? (
            <Pill tone="bad">Sold out</Pill>
          ) : low ? (
            <Pill tone="warn">Only {available} left</Pill>
          ) : (
            <Pill tone="good">In stock</Pill>
          )}
        </div>

        {products.length === 0 && (
          <p className="rounded-xl border-2 border-dashed border-ink p-4 text-center text-sm font-semibold">The menu is empty right now.</p>
        )}

        <ul className="space-y-3">
          {products.map((p) => {
            const qty = cart.quantityOf(p.id);
            return (
              <li key={p.id} className="ticket p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-display text-xl font-extrabold">{p.name}</h3>
                    {p.description && <p className="mt-1 text-sm text-muted">{p.description}</p>}
                  </div>
                  <span className="shrink-0 rounded-full border-2 border-ink bg-ketchup px-3 py-1 font-display text-lg font-extrabold text-white">
                    {formatRM(p.price_cents)}
                  </span>
                </div>
                <div className="mt-4">
                  {qty > 0 ? (
                    <div className="flex items-center justify-between gap-3">
                      <Stepper value={qty} min={0} max={maxQty} label={`${p.name} quantity`}
                        onChange={(q) => cart.setQuantity(p.id, q, maxQty)} />
                      <button type="button" onClick={cart.openDrawer}
                        className="min-h-11 rounded-full border-2 border-ink bg-paper px-4 py-2 text-sm font-bold">View cart</button>
                    </div>
                  ) : (
                    <Button disabled={!canOrder} onClick={() => cart.add(p.id, maxQty)}>
                      {canOrder ? "Add to cart" : soldOut ? "Sold out" : "Unavailable"}
                    </Button>
                  )}
                </div>
              </li>
            );
          })}
        </ul>

        {!canOrder && <p className="text-center text-sm font-semibold">{closedMessage}</p>}
      </section>

      <section className="ticket mt-8">
        <ol className="space-y-3 p-5 text-sm font-medium">
          <li><strong>1.</strong> Add burgers to your cart.</li>
          <li><strong>2.</strong> Pick your delivery trip at checkout.</li>
          <li><strong>3.</strong> Tell us your building, floor and room. Add a note if you want it custom.</li>
          <li><strong>4.</strong> Pay cash on delivery or by DuitNow QR.</li>
          <li><strong>5.</strong> We bring it to your room. Find it later under <Link to="/orders" className="font-bold underline">My orders</Link>.</li>
        </ol>
        {data.trips.length > 0 && (
          <>
            <div className="tear" />
            <div className="p-5">
              <h3 className="font-display text-lg font-extrabold">Delivery trips</h3>
              <p className="mt-1 text-sm text-muted">Choose the delivery window that suits you. Orders stay open until that window ends.</p>
              <ul className="mt-3 space-y-2 text-sm">
                {data.trips.map((t) => (
                  <li key={t.id} className="flex items-center justify-between gap-3">
                    <span className="font-semibold">{t.label}: {t.start_label} to {t.end_label}</span>
                    <span className={`shrink-0 rounded-full border-2 border-ink px-2.5 py-0.5 text-xs font-bold ${t.closed_reason || tripHasEndedToday(t, clock) ? "bg-line" : "bg-pickle text-white"}`}>
                      {tripHasEndedToday(t, clock) || t.closed_reason === "ENDED" ? "Ended today" : t.closed_reason ? "Closed" : `Order until ${t.end_label}`}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </>
        )}
        {data.buildings.length > 0 && (
          <>
            <div className="tear" />
            <p className="p-5 text-sm">
              We deliver to <strong>{data.buildings.map((b) => b.code).join(", ")}</strong>.
            </p>
          </>
        )}
      </section>

      <CartBar count={cart.count} totalCents={cartTotal} onOpen={cart.openDrawer} />
      <CartDrawer
        products={products}
        maxQty={maxQty}
        canCheckout={canOrder}
        notice={canOrder ? undefined : closedMessage}
      />
    </Shell>
  );
}
