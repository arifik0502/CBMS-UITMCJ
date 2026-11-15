import { useMemo, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Button, ErrorScreen, Field, Header, inputClass, LinkButton, LoadingScreen, Notice, Shell } from "../components/ui";
import { createOrder } from "../lib/api";
import { friendlyError } from "../lib/errors";
import { formatRM, roomLabel } from "../lib/format";
import {
  IDEMPOTENCY_KEY, LAST_ORDER_KEY, safeSessionGet, safeSessionRemove, safeSessionSet, safeSet
} from "../lib/storage";
import { useStorefront } from "../lib/useStorefront";
import {
  normalizePhone, normalizeRoom, validateOrder, type FormErrors, type OrderFormValues
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
    name: "", phone: "", buildingId: "", floor: "", room: "", quantity: 1, method: ""
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const building = useMemo(() => data?.buildings.find((b) => b.id === values.buildingId), [data, values.buildingId]);

  if (loading) return <Shell><Header back="/" /><LoadingScreen label="Loading" /></Shell>;
  if (error || !data) return <Shell><Header back="/" /><ErrorScreen message={error ?? "No data."} onRetry={reload} /></Shell>;

  const product = data.product;
  const available = data.available_stock;
  const maxQty = Math.max(1, Math.min(data.max_qty_per_order, available));

  if (!product || !data.accepting_orders || available <= 0) {
    return (
      <Shell>
        <Header back="/" />
        <div className="space-y-4 py-12 text-center">
          <p className="font-display text-3xl font-extrabold">
            {available <= 0 ? "Sold out" : "Ordering is closed"}
          </p>
          <p className="text-sm font-medium">Check back at the next selling session.</p>
          <LinkButton to="/" variant="secondary">Back to menu</LinkButton>
        </div>
      </Shell>
    );
  }

  const total = product.price_cents * values.quantity;

  function set<K extends keyof OrderFormValues>(key: K, value: OrderFormValues[K]) {
    setValues((v) => ({ ...v, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
    setSubmitError(null);
  }

  async function onSubmit(ev: FormEvent) {
    ev.preventDefault();
    if (submitting) return;
    const found = validateOrder(values, maxQty);
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
        quantity: values.quantity,
        method: values.method as "CASH" | "DUITNOW",
        idempotencyKey: idempotencyKey()
      });
      safeSessionRemove(IDEMPOTENCY_KEY);
      safeSet(LAST_ORDER_KEY, ticket.tracking_token);
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
      <h1 className="font-display text-3xl font-extrabold tracking-tight">Your order</h1>

      <form onSubmit={onSubmit} noValidate className="mt-4 space-y-5">
        <section className="ticket">
          <div className="flex items-center justify-between gap-3 p-5">
            <div>
              <p className="font-display text-lg font-extrabold leading-tight">{product.name}</p>
              <p className="text-sm text-muted">{formatRM(product.price_cents)} each</p>
            </div>
            <div className="flex items-center gap-1" role="group" aria-label="Quantity">
              <button
                type="button"
                aria-label="Fewer burgers"
                disabled={values.quantity <= 1}
                onClick={() => set("quantity", values.quantity - 1)}
                className="grid size-11 place-items-center rounded-full border-2 border-ink bg-white text-2xl font-bold disabled:opacity-40"
              >
                −
              </button>
              <output aria-live="polite" className="w-10 text-center font-display text-2xl font-extrabold">
                {values.quantity}
              </output>
              <button
                type="button"
                aria-label="More burgers"
                disabled={values.quantity >= maxQty}
                onClick={() => set("quantity", values.quantity + 1)}
                className="grid size-11 place-items-center rounded-full border-2 border-ink bg-white text-2xl font-bold disabled:opacity-40"
              >
                +
              </button>
            </div>
          </div>
          <div className="tear" />
          <div className="flex items-baseline justify-between p-5">
            <span className="text-sm font-bold">Total</span>
            <span className="font-display text-4xl font-extrabold">{formatRM(total)}</span>
          </div>
          {available <= 10 && (
            <p className="px-5 pb-4 text-sm font-semibold text-ketchup">Only {available} left.</p>
          )}
        </section>

        <section className="ticket space-y-4 p-5">
          <h2 className="font-display text-xl font-extrabold">Where to deliver</h2>

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

        <Button type="submit" busy={submitting}>
          {submitting ? "Placing order" : `Place order · ${formatRM(total)}`}
        </Button>
      </form>
    </Shell>
  );
}
