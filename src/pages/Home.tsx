import { Fragment, useEffect, useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { CartBar } from "../components/CartBar";
import { CartDrawer } from "../components/CartDrawer";
import { Stepper } from "../components/Stepper";
import { Button, ErrorScreen, LoadingScreen, Pill, Shell } from "../components/ui";
import { useCart } from "../lib/cart";
import { subtotalCents } from "../lib/cartLogic";
import { formatRM } from "../lib/format";
import { countByTab, readHistory } from "../lib/orderHistory";
import { getOrderability, storefrontProducts } from "../lib/availability";
import { tripHasEndedToday } from "../lib/trips";
import { useStorefront } from "../lib/useStorefront";
import { useSiteConfig } from "../lib/SiteConfigContext";
import type { SiteSection } from "../lib/siteConfig";

const TICKET_TYPES = new Set(["steps", "trips", "buildings"]);

function SmartLink({ to, className, children }: { to: string; className?: string; children: ReactNode }) {
  return /^https:\/\//.test(to)
    ? <a href={to} target="_blank" rel="noopener noreferrer" className={className}>{children}</a>
    : <Link to={to} className={className}>{children}</Link>;
}

export default function Home() {
  const { data, error, loading, reload, cachedOnly } = useStorefront();
  const cart = useCart();
  const site = useSiteConfig();
  const [clock, setClock] = useState(() => new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setClock(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, []);

  const activeOrders = useMemo(() => countByTab(readHistory()).active, []);

  const products = useMemo(() => data ? storefrontProducts(data) : [], [data]);
  const maxQty = Math.max(1, data?.max_qty_per_order ?? 1);

  // If a product's live stock dropped below the cart quantity, trim that line safely.
  useEffect(() => {
    if (!data || cachedOnly) return;
    for (const line of cart.lines) {
      const product = products.find((p) => p.id === line.productId);
      if (!product) continue;
      const lineMax = Math.max(0, Math.min(maxQty, product.available_stock));
      if (line.quantity > lineMax) cart.setQuantity(line.productId, lineMax, Math.max(1, lineMax));
    }
  }, [data, cachedOnly, products, maxQty, cart]);

  if (loading) return <Shell><LoadingScreen label="Loading menu" /></Shell>;
  if (error || !data) return <Shell><ErrorScreen message={error ?? "No data."} onRetry={reload} /></Shell>;

  const soldOut = products.length === 0 || products.every((p) => p.available_stock <= 0);
  const { canOrder, message: closedMessage } = getOrderability(data, cachedOnly, clock);
  const cartTotal = subtotalCents(cart.lines, (id) => products.find((p) => p.id === id)?.price_cents);

  const renderHero = (s: SiteSection) => (
    <section key={s.id} className="pt-4 text-center">
      <h1 className="font-display text-[2.6rem] font-extrabold leading-[1.02] tracking-tight">{s.headline}</h1>
      <div className="relative mx-auto mt-6 max-w-xs">
        {s.image_url
          ? <img src={s.image_url} alt="" width={400} height={360} className="mx-auto max-h-80 w-full rounded-2xl border-2 border-ink object-cover" />
          : <img src="/burger.svg" alt="Crispy chicken burger" width={400} height={360} className="burger-drop w-full" />}
      </div>
    </section>
  );

  const renderMenu = (s: SiteSection) => (
    <section key={s.id} className="mt-6 space-y-3" aria-labelledby="menu-title">
      <div className="flex items-center justify-between gap-3">
        <h2 id="menu-title" className="font-display text-2xl font-extrabold">{s.title || "Menu"}</h2>
        {cachedOnly ? (
          <Pill tone="warn">Saved menu · read-only</Pill>
        ) : !data.accepting_orders ? (
          <Pill tone="bad">Not taking orders</Pill>
        ) : soldOut ? (
          <Pill tone="bad">Sold out</Pill>
        ) : (
          <Pill tone="good">{products.filter((p) => p.available_stock > 0).length} available</Pill>
        )}
      </div>

      {products.length === 0 && (
        <p className="rounded-xl border-2 border-dashed border-ink p-4 text-center text-sm font-semibold">The menu is empty right now.</p>
      )}

      <ul className="space-y-3">
        {products.map((p) => {
          const qty = cart.quantityOf(p.id);
          const productMax = Math.max(0, Math.min(maxQty, p.available_stock));
          const lineMax = Math.max(0, Math.min(productMax, maxQty - (cart.count - qty)));
          const itemSoldOut = p.available_stock <= 0;
          const lowStock = p.available_stock > 0 && p.available_stock <= 10;
          const canAdd = canOrder && !itemSoldOut && cart.count < maxQty;
          return (
            <li key={p.id} className="ticket overflow-hidden">
              {p.image_url && (
                <img
                  src={p.image_url}
                  alt={p.name}
                  loading="lazy"
                  className="h-48 w-full border-b-2 border-ink object-cover"
                />
              )}
              <div className="p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-display text-xl font-extrabold">{p.name}</h3>
                    {p.description && <p className="mt-1 text-sm text-muted">{p.description}</p>}
                    <div className="mt-2">
                      {itemSoldOut ? <Pill tone="bad">Sold out</Pill>
                        : lowStock ? <Pill tone="warn">Only {p.available_stock} left</Pill>
                          : <Pill tone="good">In stock</Pill>}
                    </div>
                  </div>
                  <span className="shrink-0 rounded-full border-2 border-ink bg-ketchup px-3 py-1 font-display text-lg font-extrabold text-white">
                    {formatRM(p.price_cents)}
                  </span>
                </div>
                <div className="mt-4">
                  {qty > 0 ? (
                    <div className="flex items-center justify-between gap-3">
                      <Stepper value={qty} min={0} max={Math.max(1, lineMax)} label={`${p.name} quantity`}
                        onChange={(q) => cart.setQuantity(p.id, q, Math.max(1, lineMax))} />
                      <button type="button" onClick={cart.openDrawer}
                        className="min-h-11 rounded-full border-2 border-ink bg-paper px-4 py-2 text-sm font-bold">View cart</button>
                    </div>
                  ) : (
                    <Button disabled={!canAdd} onClick={() => cart.add(p.id, Math.max(1, lineMax))}>
                      {itemSoldOut ? "Sold out" : cart.count >= maxQty ? "Order limit reached" : canOrder ? "Add to cart" : "Unavailable"}
                    </Button>
                  )}
                </div>
              </div>
            </li>
          );
        })}
      </ul>

      {!canOrder && <p className="text-center text-sm font-semibold">{closedMessage}</p>}
    </section>
  );

  /** steps, trips and buildings share one tear-off ticket, as before; hidden or empty parts are skipped. */
  const renderTicketPart = (s: SiteSection): ReactNode => {
    if (s.type === "steps") {
      const items = s.items ?? [];
      if (items.length === 0) return null;
      return (
        <div key={s.id}>
          {s.title && <h3 className="px-5 pt-5 font-display text-lg font-extrabold">{s.title}</h3>}
          <ol className="space-y-3 p-5 text-sm font-medium">
            {items.map((t, i) => <li key={i}><strong>{i + 1}.</strong> {t}</li>)}
          </ol>
        </div>
      );
    }
    if (s.type === "trips") {
      if (data.trips.length === 0) return null;
      return (
        <div key={s.id} className="p-5">
          <h3 className="font-display text-lg font-extrabold">{s.title || "Delivery trips"}</h3>
          {s.body && <p className="mt-1 text-sm text-muted">{s.body}</p>}
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
      );
    }
    if (data.buildings.length === 0) return null;
    return (
      <p key={s.id} className="p-5 text-sm">
        {s.body || "We deliver to"} <strong>{data.buildings.map((b) => b.code).join(", ")}</strong>.
      </p>
    );
  };

  const renderCustom = (s: SiteSection) => {
    if (s.type === "text") {
      if (!s.title && !s.body) return null;
      return (
        <section key={s.id} className="ticket mt-6 p-5">
          {s.title && <h3 className="font-display text-lg font-extrabold">{s.title}</h3>}
          {s.body && <p className="mt-1 whitespace-pre-line text-sm font-medium">{s.body}</p>}
        </section>
      );
    }
    if (s.type === "image") {
      if (!s.image_url) return null;
      return (
        <figure key={s.id} className="mt-6">
          <img src={s.image_url} alt={s.caption ?? ""} loading="lazy" className="w-full rounded-2xl border-2 border-ink" />
          {s.caption && <figcaption className="mt-2 text-center text-sm font-medium">{s.caption}</figcaption>}
        </figure>
      );
    }
    if (s.type === "button") {
      if (!s.label || !s.to) return null;
      return (
        <div key={s.id} className="mt-6">
          <SmartLink to={s.to} className="inline-flex min-h-12 w-full items-center justify-center rounded-xl border-2 border-ink bg-paper px-5 py-3 text-base font-bold text-ink shadow-[3px_3px_0_0_var(--color-ink)]">
            {s.label}
          </SmartLink>
        </div>
      );
    }
    return null;
  };

  const blocks: ReactNode[] = [];
  const visible = site.sections.filter((s) => s.visible);
  for (let i = 0; i < visible.length; i++) {
    const s = visible[i];
    if (TICKET_TYPES.has(s.type)) {
      const group: SiteSection[] = [s];
      while (i + 1 < visible.length && TICKET_TYPES.has(visible[i + 1].type)) group.push(visible[++i]);
      const parts = group.map(renderTicketPart).filter(Boolean);
      if (parts.length > 0) {
        blocks.push(
          <section key={`t-${s.id}`} className="ticket mt-8">
            {parts.map((part, idx) => (
              <Fragment key={idx}>{idx > 0 && <div className="tear" />}{part}</Fragment>
            ))}
          </section>
        );
      }
    } else if (s.type === "hero") blocks.push(renderHero(s));
    else if (s.type === "menu") blocks.push(renderMenu(s));
    else if (s.type === "support") {
      blocks.push(
        <p key={s.id} className="mt-6 text-center text-sm font-medium">
          {s.body || "Problem with an order?"} <Link to="/support" className="font-bold underline">{s.label || "Report it"}</Link>
        </p>
      );
    } else blocks.push(renderCustom(s));
  }

  return (
    <Shell>
      <header className="flex items-center justify-between gap-2 py-3">
        <span className="font-display text-xl font-extrabold tracking-tight">{site.brand_name || data.business_name}</span>
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

      {blocks}

      {site.footer_note && <p className="mt-6 text-center text-sm text-muted">{site.footer_note}</p>}

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
