import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Button, ErrorScreen, Field, Header, inputClass, LinkButton, LoadingScreen, Notice, Shell } from "../components/ui";
import { createOrder } from "../lib/api";
import { storefrontProducts } from "../lib/availability";
import { useCart } from "../lib/cart";
import { subtotalCents } from "../lib/cartLogic";
import { friendlyError } from "../lib/errors";
import { formatRM, roomLabel } from "../lib/format";
import { rememberOrder } from "../lib/orderHistory";
import {
  IDEMPOTENCY_KEY, LAST_ORDER_KEY, safeSessionGet, safeSessionRemove, safeSessionSet, safeSet
} from "../lib/storage";
import { useStorefront } from "../lib/useStorefront";
import { tripDurationMinutes, tripHasEndedToday, tripIsSelectable } from "../lib/trips";
import {
  normalizePhone, normalizeRoom, NOTE_MAX, validateOrder, type FormErrors, type OrderFormValues
} from "../lib/validation";

function idempotencyKey(): string {
  let key = safeSessionGet(IDEMPOTENCY_KEY);
  if (!key) {
    key = crypto.randomUUID();
    safeSessionSet(IDEMPOTENCY_KEY, key);
  }
  return key;
}

export default function OrderPage() {
  const { data, error, loading, reload } = useStorefront();
  const navigate = useNavigate();
  const [values, setValues] = useState<OrderFormValues>({
    name: "", phone: "", buildingId: "", floor: "", room: "", quantity: 1, method: "",
    tripId: "", custom: false, note: ""
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [clock, setClock] = useState(() => new Date());
  const cart = useCart();

  useEffect(() => {
    const timer = window.setInterval(() => setClock(new Date()), 15000);
    return () => window.clearInterval(timer);
  }, []);

  const building = useMemo(() => data?.buildings.find((b) => b.id === values.buildingId), [data, values.buildingId]);

  if (loading) return <Shell><Header back="/" /><LoadingScreen label="Loading" /></Shell>;
  if (error || !data) return <Shell><Header back="/" /><ErrorScreen message={error ?? "No data."} onRetry={reload} /></Shell>;

  const products = storefrontProducts(data);
  const maxQty = Math.max(1, data.max_qty_per_order);
  const cartItems = cart.lines
    .map((line) => ({ line, product: products.find((p) => p.id === line.productId) }))
    .filter((x): x is { line: typeof cart.lines[number]; product: (typeof products)[number] } => Boolean(x.product));
  const cartHasMissingItem = cartItems.length !== cart.lines.length;
  const totalQty = cartItems.reduce((sum, x) => sum + x.line.quantity, 0);
  const stockProblem = cartItems.some((x) => x.line.quantity > x.product.available_stock);
  const cartHasItem = totalQty > 0;
  const form: OrderFormValues = { ...values, quantity: totalQty };
  const total = subtotalCents(cart.lines, (id) => products.find((p) => p.id === id)?.price_cents);
  const anyStock = products.some((p) => p.available_stock > 0);

  const anyTripOpen = data.trips.some(
    (trip) => trip.closed_reason === null && !tripHasEndedToday(trip, clock)
  );

  if (products.length === 0 || !data.accepting_orders || !anyStock || !anyTripOpen) {
    return (
      <Shell>
        <Header back="/" />
        <div className="space-y-4 py-12 text-center">
          <p className="font-display text-3xl font-extrabold">
            {!anyStock ? "Sold out" : "Ordering is closed"}
          </p>
          <p className="text-sm font-medium">
            {anyStock && data.accepting_orders
              ? "All of today's trips have closed. See you at the next session."
              : "Check back at the next selling session."}
          </p>
          <LinkButton to="/" variant="secondary">Back to menu</LinkButton>
        </div>
      </Shell>
    );
  }

  function set<K extends keyof OrderFormValues>(key: K, value: OrderFormValues[K]) {
    setValues((v) => ({ ...v, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
    setSubmitError(null);
  }

  function cancelCart() {
    if (!window.confirm("Cancel this cart and return to the menu? Your selections will be cleared.")) return;
    cart.clear();
    setValues({
      name: "", phone: "", buildingId: "", floor: "", room: "", quantity: 1, method: "",
      tripId: "", custom: false, note: ""
    });
    setErrors({});
    setSubmitError(null);
    navigate("/", { replace: true });
  }

  async function onSubmit(ev: FormEvent) {
    ev.preventDefault();
    if (submitting || !data) return;
    if (!cartHasItem) {
      setSubmitError("Your cart is empty. Add an item before checking out.");
      return;
    }
    if (cartHasMissingItem) {
      setSubmitError("One item is no longer on the menu. Remove it from your cart and try again.");
      return;
    }
    if (stockProblem) {
      setSubmitError("One or more quantities exceed current stock. Reduce them and try again.");
      return;
    }
    if (totalQty > maxQty) {
      setSubmitError(`This order can contain at most ${maxQty} items. Remove ${totalQty - maxQty} item${totalQty - maxQty === 1 ? "" : "s"}.`);
      return;
    }
    const selectedTrip = data.trips.find((trip) => trip.id === values.tripId);
    if (values.tripId && (
      !selectedTrip ||
      !tripIsSelectable(selectedTrip, new Date())
    )) {
      const ended = selectedTrip ? tripHasEndedToday(selectedTrip, new Date()) : false;
      const message = ended
        ? "This trip has already ended for today. Choose another trip."
        : "This trip is no longer available. Choose another trip.";
      setClock(new Date());
      setValues((current) => ({ ...current, tripId: "" }));
      setErrors((current) => ({ ...current, tripId: message }));
      setSubmitError(message);
      return;
    }

    const found = validateOrder(form, maxQty);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      const first = Object.keys(found)[0];
      document.getElementById(first === "buildingId" ? "building" : first)?.focus();
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    try {
      const ticket = await createOrder({
        name: values.name.trim(),
        phone: normalizePhone(values.phone),
        buildingId: values.buildingId,
        floor: Number(values.floor),
        room: normalizeRoom(values.room),
        items: cartItems.map(({ line }) => ({ productId: line.productId, quantity: line.quantity })),
        method: values.method as "CASH" | "DUITNOW",
        idempotencyKey: idempotencyKey(),
        tripId: values.tripId,
        note: values.custom ? values.note.trim() : null
      });
      safeSessionRemove(IDEMPOTENCY_KEY);
      safeSet(LAST_ORDER_KEY, ticket.tracking_token);
      rememberOrder({
        token: ticket.tracking_token,
        orderNo: ticket.order_no,
        totalCents: ticket.total_cents,
        createdAt: new Date().toISOString(),
        status: ticket.order_status,
        paymentStatus: ticket.payment_status,
        items: cartItems.map(({ line, product }) => ({ name: product.name, quantity: line.quantity }))
      });
      cart.clear();
      navigate(
        ticket.payment_method === "DUITNOW"
          ? `/pay/${ticket.tracking_token}`
          : `/track/${ticket.tracking_token}?placed=1`,
        { replace: true }
      );
    } catch (e) {
      setSubmitError(friendlyError(e));
      void reload();
    } finally {
      setSubmitting(false);
    }
  }

  const err = (k: keyof OrderFormValues) => errors[k];
  const aria = (k: keyof OrderFormValues) => ({
    "aria-invalid": err(k) ? true : undefined,
    "aria-describedby": err(k) ? `${k === "buildingId" ? "building" : k}-error` : undefined
  });

  return (
    <Shell>
      <Header back="/" />
      <h1 className="font-display text-3xl font-extrabold tracking-tight">Your cart</h1>
      <p className="mt-1 text-sm text-muted">Review your items, choose a delivery trip and complete checkout.</p>

      <form onSubmit={onSubmit} noValidate className="mt-4 space-y-5">
        <section className="ticket space-y-4 p-5">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="font-display text-xl font-extrabold">Shopping cart</h2>
              <p className="text-sm text-muted">{cartHasItem ? totalQty + " item" + (totalQty === 1 ? "" : "s") + " in your cart" : "No items added yet"}</p>
            </div>
            <span className="rounded-full border-2 border-ink bg-bun px-3 py-1 text-sm font-bold">{totalQty}/{maxQty}</span>
          </div>

          {cartHasItem ? (
            <>
              <ul className="space-y-3">
                {cartItems.map(({ line, product }) => {
                  const otherQty = totalQty - line.quantity;
                  const lineMax = Math.max(1, Math.min(product.available_stock, maxQty - otherQty));
                  return (
                    <li key={product.id} className="rounded-xl border-2 border-ink bg-white p-4">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex min-w-0 items-start gap-3">
                          {product.image_url && <img src={product.image_url} alt="" className="size-16 shrink-0 rounded-lg border-2 border-ink object-cover" />}
                          <div>
                            <p className="font-display text-lg font-extrabold">{product.name}</p>
                            <p className="text-sm text-muted">{formatRM(product.price_cents)} each · {product.available_stock} in stock</p>
                          </div>
                        </div>
                        <button type="button" onClick={() => { cart.remove(product.id); setSubmitError(null); }}
                          className="min-h-10 shrink-0 rounded-lg px-2 text-sm font-bold text-ketchup underline underline-offset-2">
                          Remove
                        </button>
                      </div>
                      <div className="mt-4 flex items-center justify-between gap-3">
                        <span className="text-sm font-semibold">Quantity</span>
                        <div className="flex items-center gap-2" role="group" aria-label={product.name + " quantity"}>
                          <button type="button" aria-label={"Decrease " + product.name} disabled={line.quantity <= 1}
                            onClick={() => cart.setQuantity(product.id, line.quantity - 1, lineMax)}
                            className="grid size-10 place-items-center rounded-full border-2 border-ink bg-paper text-xl font-bold disabled:opacity-40">−</button>
                          <output aria-live="polite" className="w-8 text-center font-display text-xl font-extrabold">{line.quantity}</output>
                          <button type="button" aria-label={"Increase " + product.name} disabled={line.quantity >= lineMax}
                            onClick={() => cart.setQuantity(product.id, line.quantity + 1, lineMax)}
                            className="grid size-10 place-items-center rounded-full border-2 border-ink bg-paper text-xl font-bold disabled:opacity-40">+</button>
                        </div>
                      </div>
                      {line.quantity > product.available_stock && (
                        <p className="mt-3 text-sm font-semibold text-ketchup">Only {product.available_stock} left in stock.</p>
                      )}
                      <p className="mt-2 text-right font-bold">{formatRM(product.price_cents * line.quantity)}</p>
                    </li>
                  );
                })}
              </ul>

              {cartHasMissingItem && <Notice tone="error">An item in your saved cart is no longer on the menu. Return to the menu and remove unavailable items.</Notice>}
              {stockProblem && <Notice tone="error">One or more items exceed current stock. Reduce the quantities before checkout.</Notice>}
              {totalQty > maxQty && <Notice tone="error">This order can contain at most {maxQty} items.</Notice>}

              <div className="space-y-2 text-sm">
                <div className="flex justify-between"><span>Subtotal</span><span className="font-semibold">{formatRM(total)}</span></div>
                <div className="flex justify-between"><span>Delivery</span><span className="font-semibold">Included</span></div>
                <div className="flex justify-between border-t-2 border-ink pt-3 text-base">
                  <span className="font-bold">Total</span><span className="font-display text-2xl font-extrabold">{formatRM(total)}</span>
                </div>
              </div>
              <a href="#checkout-details" className="inline-flex min-h-12 w-full items-center justify-center rounded-xl border-2 border-ink bg-ink px-5 py-3 text-base font-bold text-bun shadow-[3px_3px_0_0_var(--color-ketchup)]">
                Continue to checkout
              </a>
            </>
          ) : (
            <div className="space-y-3 rounded-xl border-2 border-dashed border-ink p-4 text-center">
              <p className="font-bold">Your cart is empty</p>
              <p className="text-sm text-muted">Choose products from the menu before checking out.</p>
              <LinkButton to="/" variant="secondary">Browse menu</LinkButton>
            </div>
          )}
          <button type="button" onClick={cancelCart}
            className="min-h-11 w-full rounded-xl border-2 border-ketchup bg-paper px-4 py-2.5 text-sm font-bold text-ketchup">
            Clear cart
          </button>
        </section>

        {cartHasItem && (
          <>
        <section id="checkout-details" className="ticket space-y-4 p-5">
          <h2 className="font-display text-xl font-extrabold">Delivery details</h2>

          <Field label="Your name" htmlFor="name" error={err("name")}>
            <input id="name" className={inputClass} autoComplete="name" value={values.name}
              onChange={(e) => set("name", e.target.value)} maxLength={80} {...aria("name")} />
          </Field>

          <Field label="Phone number" htmlFor="phone" error={err("phone")} hint="The seller may call when they arrive.">
            <input id="phone" className={inputClass} type="tel" inputMode="tel" autoComplete="tel"
              placeholder="012-345 6789" value={values.phone}
              onChange={(e) => set("phone", e.target.value)} {...aria("phone")} />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Building" htmlFor="building" error={err("buildingId")}>
              <select id="building" className={inputClass} value={values.buildingId}
                onChange={(e) => { set("buildingId", e.target.value); set("floor", ""); }} {...aria("buildingId")}>
                <option value="">Choose</option>
                {data.buildings.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            </Field>
            <Field label="Floor" htmlFor="floor" error={err("floor")}>
              <select id="floor" className={inputClass} value={values.floor} disabled={!building}
                onChange={(e) => set("floor", e.target.value)} {...aria("floor")}>
                <option value="">{building ? "Choose" : "Pick building"}</option>
                {building?.floors.map((f) => <option key={f} value={f}>Floor {f}</option>)}
              </select>
            </Field>
          </div>

          <Field
            label="Room number"
            htmlFor="room"
            error={err("room")}
            hint={building && values.room.trim() ? `Delivering to ${roomLabel(building.code, normalizeRoom(values.room))}` : "Type your room number."}
          >
            <div className="flex">
              <span className="grid min-w-14 place-items-center rounded-l-xl border-2 border-r-0 border-ink bg-bun-deep px-3 font-bold">
                {building?.code ?? "—"}
              </span>
              <input id="room" className={`${inputClass} rounded-l-none`} autoCapitalize="characters"
                autoComplete="off" maxLength={10} placeholder="205" value={values.room}
                onChange={(e) => set("room", e.target.value)} {...aria("room")} />
            </div>
          </Field>
        </section>

        <section className="ticket p-5">
          <fieldset aria-describedby={err("tripId") ? "tripId-error" : undefined}>
            <legend className="font-display text-xl font-extrabold">Pick your delivery trip</legend>
            <p className="mt-1 text-sm text-muted">Choose your preferred delivery time. Orders remain open until the selected trip ends. All times use Malaysia time.</p>
            <div className="mt-3 space-y-3">
              {data.trips.map((t) => {
                const ended = tripHasEndedToday(t, clock);
                const closed = t.closed_reason !== null || ended;
                return (
                  <label key={t.id}
                    className={`flex min-h-14 items-start gap-3 rounded-xl border-2 border-ink p-3 ${closed ? "cursor-not-allowed bg-line/50 opacity-60" : "cursor-pointer"} ${values.tripId === t.id ? "bg-bun" : closed ? "" : "bg-white"} has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-ketchup`}>
                    <input type="radio" name="trip" value={t.id} disabled={closed} checked={values.tripId === t.id}
                      onChange={() => set("tripId", t.id)} className="mt-1 size-5 accent-ink" />
                    <span>
                      <span className="block font-bold">{t.label}: {t.start_label} to {t.end_label}</span>
                      <span className="block text-sm text-muted">
                        {ended || t.closed_reason === "ENDED"
                          ? "This trip has ended for today"
                          : closed
                            ? t.closed_reason === "TEAM" ? "Closed by the team" : `Orders closed at ${t.closes_label}`
                            : `Order until ${t.end_label} · ${tripDurationMinutes(t)}-minute delivery window`}
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
            {err("tripId") && <p id="tripId-error" role="alert" className="mt-2 text-sm font-semibold text-ketchup">{err("tripId")}</p>}
          </fieldset>
        </section>

        <section className="ticket p-5">
          <fieldset>
            <legend className="font-display text-xl font-extrabold">Kitchen note</legend>
            <div className="mt-3 space-y-3">
              {([
                { custom: false, title: "No special request", body: "Prepare the order as listed on the menu." },
                { custom: true, title: "Add a kitchen note", body: "Use one note for the whole order, e.g. no sauce." }
              ] as const).map((o) => (
                <label key={o.title}
                  className={`flex min-h-14 cursor-pointer items-start gap-3 rounded-xl border-2 border-ink p-3 ${values.custom === o.custom ? "bg-bun" : "bg-white"} has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-ketchup`}>
                  <input type="radio" name="orderNoteType" checked={values.custom === o.custom}
                    onChange={() => set("custom", o.custom)} className="mt-1 size-5 accent-ink" />
                  <span>
                    <span className="block font-bold">{o.title}</span>
                    <span className="block text-sm text-muted">{o.body}</span>
                  </span>
                </label>
              ))}
            </div>
            {values.custom && (
              <div className="mt-4">
                <Field label="Note for the kitchen" htmlFor="note" error={err("note")}
                  hint={`${values.note.trim().length}/${NOTE_MAX}. The note applies to the whole order. For allergies, tell the seller directly.`}>
                  <textarea id="note" rows={3} maxLength={NOTE_MAX} className={inputClass} value={values.note}
                    placeholder="e.g. no sauce, extra spicy" onChange={(e) => set("note", e.target.value)} {...aria("note")} />
                </Field>
              </div>
            )}
          </fieldset>
        </section>

        <section className="ticket p-5">
          <fieldset aria-describedby={err("method") ? "method-error" : undefined}>
            <legend className="font-display text-xl font-extrabold">How will you pay?</legend>
            <div className="mt-3 space-y-3">
              {([
                { id: "CASH", title: "Cash on delivery", body: "Pay the seller at your door." },
                { id: "DUITNOW", title: "DuitNow QR", body: "Scan the QR on the next step and upload your receipt." }
              ] as const).map((o) => (
                <label key={o.id}
                  className={`flex min-h-14 cursor-pointer items-start gap-3 rounded-xl border-2 border-ink p-3 ${values.method === o.id ? "bg-bun" : "bg-white"} has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-ketchup`}>
                  <input type="radio" name="method" value={o.id} checked={values.method === o.id}
                    onChange={() => set("method", o.id)} className="mt-1 size-5 accent-ink" />
                  <span>
                    <span className="block font-bold">{o.title}</span>
                    <span className="block text-sm text-muted">{o.body}</span>
                  </span>
                </label>
              ))}
            </div>
            {err("method") && <p id="method-error" role="alert" className="mt-2 text-sm font-semibold text-ketchup">{err("method")}</p>}
          </fieldset>
        </section>

        {submitError && <Notice tone="error">{submitError}</Notice>}

        <Button
          type="submit"
          busy={submitting}
          disabled={!cartHasItem || cartHasMissingItem || stockProblem || totalQty > maxQty}
        >
          {submitting ? "Placing order" : `Place order · ${formatRM(total)}`}
        </Button>
          </>
        )}
      </form>
    </Shell>
  );
}
